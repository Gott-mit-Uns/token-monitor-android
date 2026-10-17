package dev.bing.tokenmonitor

import android.app.Application
import android.os.Looper
import kotlinx.serialization.json.*
import androidx.lifecycle.ViewModelStore
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLHandshakeException

/** Real ViewModel + HTTP/SSE, synthetic data, virtual main-loop delays; never touches the phone. */
@RunWith(RobolectricTestRunner::class)
// API 34 runs on the repository's JDK 17. This is a JVM state-machine test, not an API 36 phone claim.
@Config(sdk = [34], manifest = Config.NONE)
class HubNetworkRecoveryTest {
    private class MemoryStore(app: Application, initial: SavedHub) : HubStore(app, "public-network-recovery.bin") {
        @Volatile private var saved: SavedHub? = initial
        override fun load() = saved
        override fun save(record: SavedHub) { saved = record }
        override fun clear() { saved = null }
    }
    private class Scenario(hubDisplayName: String? = null) : AutoCloseable {
        val app: Application = RuntimeEnvironment.getApplication()
        val secret = UUID.randomUUID().toString()
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build()
        val server = RecoveryFixtureHub(secret, HandshakeCertificates.Builder().heldCertificate(certificate).build(),
            buildJsonObject {
                put("periods", buildJsonObject { put("today", buildJsonObject { put("totalTokens", 123) }) })
                put("devices", JsonArray(emptyList()))
                hubDisplayName?.let { name ->
                    require(name.codePointCount(0, name.length) in 1..80)
                    put("hub", buildJsonObject { put("displayName", name) })
                }
            }.toString())
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val failures = AtomicInteger()
        var failingPath = "/api/health"
        var failure: () -> Throwable = { IOException("Synthetic transient connection failure") }
        val healthAttempts = AtomicInteger()
        val statsAttempts = AtomicInteger()
        val client: OkHttpClient = HubViewModel.defaultClient().newBuilder()
            .proxy(java.net.Proxy.NO_PROXY)
            .sslSocketFactory(trust.sslSocketFactory(), trust.trustManager)
            .addInterceptor { chain ->
                val path = chain.request().url.encodedPath
                if (path == "/api/health") healthAttempts.incrementAndGet()
                if (path == "/api/stats") statsAttempts.incrementAndGet()
                if (path == failingPath && failures.getAndUpdate { (it - 1).coerceAtLeast(0) } > 0) throw failure()
                chain.proceed(chain.request())
            }.build()
        val base = "https://127.0.0.1:${server.port}"
        val store = MemoryStore(app, SavedHub(base, secret, """{"periods":{"today":{"totalTokens":123}},"devices":[]}""", 1))
        val owner = ViewModelStore()
        val model = HubViewModel(app, store, client).also { owner.put("recovery", it) }
        fun scope(id: Long, ready: Boolean = true) = HubNetworkScope(false, false, emptyList(), networkId = id, ready = ready)
        fun await(message: String, condition: () -> Boolean) {
            val until = System.nanoTime() + 5_000_000_000L
            while (!condition() && System.nanoTime() < until) {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
                Thread.sleep(10)
            }
            assertTrue(message, condition())
        }
        fun advance(seconds: Long) {
            repeat((seconds * 10).toInt()) {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); Thread.sleep(2)
            }
        }
        fun online() {
            await("Seeded cache must load") { !model.state.value.loading }
            model.updateNetworkScope(scope(1)); model.setForeground(true)
            await("Initial HTTPS SSE must be live") { model.state.value.live }
        }
        override fun close() {
            owner.clear(); shadowOf(Looper.getMainLooper()).idle()
            client.dispatcher.cancelAll(); client.connectionPool.evictAll(); server.close()
        }
    }
    @Test fun coldStartWaitsForNetworkDetailsThenReachesHome() = Scenario().use { s ->
        s.await("Seeded cache must load") { !s.model.state.value.loading }
        s.model.updateNetworkScope(s.scope(1, ready = false)); s.model.setForeground(true)
        s.advance(1)
        assertEquals(HubStartup.LOADING, s.model.startup.value)
        assertEquals(0, s.healthAttempts.get())
        s.model.updateNetworkScope(s.scope(1))
        s.await("Ready network must restore the initial snapshot") { s.model.state.value.live }
        assertEquals(HubStartup.HOME, s.model.startup.value)
        assertEquals(1, s.healthAttempts.get())
    }
    @Test fun pendingHttpsNetworkWaitsForReadyThenValidatesOnce() = Scenario().use { s ->
        s.online(); val reads = s.healthAttempts.get()
        s.model.updateNetworkScope(s.scope(2, ready = false)); s.advance(1)
        assertEquals("Pending HTTPS network must not start validation", reads, s.healthAttempts.get())
        assertFalse(s.model.state.value.live)
        s.model.updateNetworkScope(s.scope(2))
        s.await("Same network readiness must restore SSE") { s.model.state.value.live }
        s.model.updateNetworkScope(s.scope(2)); s.advance(1)
        assertEquals(reads + 1, s.healthAttempts.get())
    }
    @Test fun confirmedLanToPendingSecureNetworkKeepsItsTransientRecoveryBudget() = Scenario().use { s ->
        RecoveryFixtureHub(s.secret).use { local ->
            val localBase = "http://192.168.1.1:${local.port}"
            val record = SavedHub(localBase, s.secret,
                """{"periods":{"today":{"totalTokens":123}},"devices":[]}""", 1, alternate = s.base).also {
                it.endpointsAssociated = true
            }
            val store = MemoryStore(s.app, record)
            val client = s.client.newBuilder().addInterceptor { chain ->
                val request = chain.request()
                val mapped = if (request.url.scheme == "http")
                    request.newBuilder().url(request.url.newBuilder().host("127.0.0.1").build()).build() else request
                chain.proceed(mapped)
            }.build()
            val owner = ViewModelStore()
            val model = HubViewModel(s.app, store, client).also { owner.put("dual-ready", it) }
            try {
                s.await("Associated LAN cache must load") { !model.state.value.loading }
                model.updateNetworkScope(HubNetworkScope(true, false, listOf(LocalSubnet("192.168.1.2", 24)),
                    networkId = 1, socketFactory = javax.net.SocketFactory.getDefault()))
                model.setForeground(true)
                s.await("The approved LAN must initially have the only live SSE") { model.state.value.live && local.streams.get() == 1 }
                assertEquals("http", model.endpointType())
                val reads = s.healthAttempts.get()
                val localRequests = local.requests.get()
                s.failures.set(1)
                model.updateNetworkScope(s.scope(2, ready = false))
                s.advance(1)
                model.updateNetworkScope(s.scope(2, ready = false))
                s.advance(1)
                assertEquals("Pending details must not start HTTPS validation", reads, s.healthAttempts.get())
                model.updateNetworkScope(s.scope(2))
                s.await("The first secure validation must fail") {
                    s.healthAttempts.get() == reads + 1 && !model.state.value.loading && !model.state.value.live
                }
                assertEquals(123L, model.state.value.snapshot?.periods?.get("today")?.tokens)
                assertEquals(localRequests, local.requests.get())
                s.await("The same ready secure network must retry automatically") { model.state.value.live }
                assertEquals("https", model.endpointType())
                assertEquals(reads + 2, s.healthAttempts.get())
                s.await("Only the new secure stream may remain active") {
                    local.closedStreams.get() == 1 && s.server.streams.get() - s.server.closedStreams.get() == 1
                }
                repeat(3) { model.updateNetworkScope(s.scope(2)) }
                s.advance(15)
                assertEquals("Repeated ready callbacks must not add another validation", reads + 2, s.healthAttempts.get())
                assertEquals(0, local.badAuthentication.get()); assertEquals(0, s.server.badAuthentication.get())
            } finally { owner.clear(); shadowOf(Looper.getMainLooper()).idle() }
        }
    }
    @Test fun firstHealthFailureRecoversOnSameNetworkWithoutUserRefresh() = Scenario().use { s ->
        s.online(); s.failures.set(1); val reads = s.healthAttempts.get()
        s.model.updateNetworkScope(s.scope(2))
        s.await("First verification must fail") { s.healthAttempts.get() > reads && !s.model.state.value.loading && !s.model.state.value.live }
        s.await("Transient verification failure must recover automatically") { s.model.state.value.live }
        s.await("Only the replacement SSE may remain active") { s.server.streams.get() - s.server.closedStreams.get() == 1 }
        assertEquals(2, s.server.streams.get())
        assertEquals(reads + 2, s.healthAttempts.get())
        assertEquals(123L, s.model.state.value.snapshot?.periods?.get("today")?.tokens)
    }
    @Test fun firstStatsFailureAlsoRecoversWithoutLosingCache() = Scenario().use { s ->
        s.online(); s.failingPath = "/api/stats"; s.failures.set(1); val reads = s.statsAttempts.get()
        s.model.updateNetworkScope(s.scope(2))
        s.await("Stats verification must fail") { s.statsAttempts.get() > reads && !s.model.state.value.loading && !s.model.state.value.live }
        assertEquals(123L, s.model.state.value.snapshot?.periods?.get("today")?.tokens)
        s.await("Stats transient failure must recover") { s.model.state.value.live }
        assertEquals(reads + 2, s.statsAttempts.get())
    }
    @Test fun persistentFailureHasFiniteBudgetAndRepeatedReadyDoesNotRearmIt() = Scenario().use { s ->
        s.online(); s.failures.set(100); val reads = s.healthAttempts.get()
        s.model.updateNetworkScope(s.scope(2))
        s.await("Initial + three retries must finish") { s.healthAttempts.get() == reads + 4 && !s.model.state.value.loading }
        repeat(5) { s.model.updateNetworkScope(s.scope(2)) }; s.advance(65)
        assertEquals(reads + 4, s.healthAttempts.get())
        assertFalse(s.model.state.value.live)
        assertEquals(123L, s.model.state.value.snapshot?.periods?.get("today")?.tokens)
    }
    @Test fun backgroundCancelsPendingRetryAndForegroundRecoversOnce() = Scenario().use { s ->
        s.online(); s.failures.set(1); val reads = s.healthAttempts.get()
        s.model.updateNetworkScope(s.scope(2))
        s.await("Failure must finish before background") { s.healthAttempts.get() == reads + 1 && !s.model.state.value.loading }
        s.model.setForeground(false); s.advance(65)
        assertEquals(reads + 1, s.healthAttempts.get())
        s.model.setForeground(true); s.await("Foreground must recover") { s.model.state.value.live }
        s.advance(15); assertEquals(reads + 2, s.healthAttempts.get())
    }
    @Test fun userRefreshSupersedesPendingRetryWithoutDuplicateValidation() = Scenario().use { s ->
        s.online(); s.failures.set(1); val reads = s.healthAttempts.get()
        s.model.updateNetworkScope(s.scope(2))
        s.await("Failure must finish before user refresh") { s.healthAttempts.get() == reads + 1 && !s.model.state.value.loading }
        s.model.refreshSaved(); s.await("User refresh must recover") { s.model.state.value.live }
        s.advance(15); assertEquals(reads + 2, s.healthAttempts.get())
    }
    @Test fun aNewNetworkInvalidatesOldRetry() = Scenario().use { s ->
        s.online(); s.failures.set(1); val reads = s.healthAttempts.get()
        s.model.updateNetworkScope(s.scope(2))
        s.await("Old network failure must finish") { s.healthAttempts.get() == reads + 1 && !s.model.state.value.loading }
        s.model.updateNetworkScope(s.scope(3)); s.await("New network must recover") { s.model.state.value.live }
        s.advance(15); assertEquals(reads + 2, s.healthAttempts.get())
    }
    @Test fun tlsProtocolAndCancelledFailuresDoNotRetry() {
        for (failure in listOf<() -> Throwable>({ SSLHandshakeException("Synthetic certificate failure") },
            { IllegalArgumentException("Synthetic protocol failure") }, { java.util.concurrent.CancellationException() })) {
            Scenario().use { s ->
                s.online(); s.failure = failure; s.failures.set(1); val reads = s.healthAttempts.get()
                s.model.updateNetworkScope(s.scope(2))
                s.await("Terminal validation failure must finish") { s.healthAttempts.get() == reads + 1 && !s.model.state.value.loading }
                s.advance(65); assertEquals(reads + 1, s.healthAttempts.get()); assertFalse(s.model.state.value.live)
            }
        }
    }
    @Test fun authenticationFailureStaysBlockedAcrossNetworkAndForegroundChanges() = Scenario().use { s ->
        s.online(); s.server.status.set(401); val reads = s.healthAttempts.get()
        s.model.updateNetworkScope(s.scope(2))
        s.await("Authentication failure must finish") { s.healthAttempts.get() == reads + 1 && !s.model.state.value.loading }
        s.advance(65); s.model.updateNetworkScope(s.scope(3)); s.model.setForeground(false); s.model.setForeground(true); s.advance(65)
        assertEquals(reads + 1, s.healthAttempts.get()); assertFalse(s.model.state.value.live)
        assertEquals(123L, s.model.state.value.snapshot?.periods?.get("today")?.tokens)
    }
    @Test fun lateStatsFromRetiredNetworkCannotOverwriteReplacementSnapshotOrRestoreRetiredHubName() = Scenario("Synthetic Hub A").use { s ->
        s.online(); val reads = s.statsAttempts.get(); val serverReads = s.server.statsRequests.get(); val gate = CountDownLatch(1)
        assertEquals("Synthetic Hub A", s.model.state.value.snapshot?.hubDisplayName)
        s.server.statsBody.set("""{"hub":{"displayName":"Retired response name"},"periods":{"today":{"totalTokens":456}},"devices":[]}""")
        s.server.statsGate.set(gate); s.model.updateNetworkScope(s.scope(2))
        try {
            s.await("Retiring response must be captured before releasing the gate") {
                s.statsAttempts.get() == reads + 1 && s.server.statsRequests.get() == serverReads + 1
            }
            // A different payload on the new network makes stale writes observable.
            s.server.statsGate.set(null)
            s.server.statsBody.set("""{"periods":{"today":{"totalTokens":789}},"devices":[]}""")
            s.model.updateNetworkScope(s.scope(3))
            s.await("Replacement network snapshot must be live") { s.model.state.value.live && s.model.state.value.snapshot?.periods?.get("today")?.tokens == 789L }
            assertNull(s.model.state.value.snapshot?.hubDisplayName)
            assertNull(SyncSettingsPresentation.hubHostName(s.model.state.value))
            gate.countDown(); s.advance(15)
            assertEquals(789L, s.model.state.value.snapshot?.periods?.get("today")?.tokens)
            assertNull("Retired HTTP response must not restore the old name", s.model.state.value.snapshot?.hubDisplayName)
            assertNull(SyncSettingsPresentation.hubHostName(s.model.state.value))
            assertEquals(reads + 2, s.statsAttempts.get())
        } finally { gate.countDown() }
    }
    @Test fun connectionAliasPersistsAcrossRefreshAndColdRestoreButNotReplacementOrForget() = Scenario().use { s ->
        s.online()
        s.model.renameHubDisplay("DXP4800")
        s.await("Local alias must persist") {s.store.load()?.hubDisplayAlias=="DXP4800"}
        assertEquals("DXP4800",SyncSettingsPresentation.hubHostName(s.model.state.value))
        val coldOwner=ViewModelStore()
        val cold=HubViewModel(s.app,s.store,s.client).also {coldOwner.put("cold",it)}
        try {
            s.await("Alias must cold restore") {cold.state.value.hubDisplayAlias=="DXP4800"}
        } finally {coldOwner.clear()}
        s.model.refreshSaved()
        s.await("Same binding refresh must finish") {!s.model.state.value.loading && s.model.state.value.connectedEndpoint!=null}
        assertEquals("DXP4800",s.model.state.value.hubDisplayAlias)
        val otherSecret=UUID.randomUUID().toString()
        RecoveryFixtureHub(otherSecret,HandshakeCertificates.Builder().heldCertificate(s.certificate).build()).use { next ->
            val base="https://127.0.0.1:${next.port}"
            s.model.connect(base,otherSecret,"")
            s.await("Replacement connection must commit") {s.model.state.value.connectedEndpoint==base && !s.model.state.value.loading}
            assertNull(s.model.state.value.hubDisplayAlias)
            assertNull(s.store.load()?.hubDisplayAlias)
            assertNull(SyncSettingsPresentation.hubHostName(s.model.state.value))
            s.model.renameHubDisplay("Other alias")
            s.await("New alias must persist") {s.store.load()?.hubDisplayAlias=="Other alias"}
            s.model.forget()
            s.await("Forget must clear binding and alias") {s.store.load()==null && s.model.state.value.hubDisplayAlias==null}
        }
    }
    @Test fun replacingConnectionCancelsRecoveryForTheOldAuthorityAndDropsItsHubName() = Scenario("Synthetic Hub A").use { s ->
        s.online(); assertEquals("Synthetic Hub A", s.model.state.value.snapshot?.hubDisplayName); s.failures.set(1); val reads = s.healthAttempts.get()
        s.model.updateNetworkScope(s.scope(2))
        s.await("Old authority validation must fail") { s.healthAttempts.get() == reads + 1 && !s.model.state.value.loading }
        val secret = UUID.randomUUID().toString()
        RecoveryFixtureHub(secret, HandshakeCertificates.Builder().heldCertificate(s.certificate).build(),
            """{"periods":{"today":{"totalTokens":789}},"devices":[]}""").use { other ->
            val oldRequests = s.server.requests.get()
            s.model.connect("https://127.0.0.1:${other.port}", secret, "")
            s.await("New authority must replace the old connection") { s.model.state.value.live && s.model.state.value.snapshot?.periods?.get("today")?.tokens == 789L }
            s.advance(15)
            assertEquals(oldRequests, s.server.requests.get())
            assertEquals(reads + 2, s.healthAttempts.get())
            assertNull(s.model.state.value.snapshot?.hubDisplayName)
            assertNull(SyncSettingsPresentation.hubHostName(s.model.state.value))
            assertEquals("https://127.0.0.1:${other.port}", s.model.state.value.connectedEndpoint)
            assertNull(HubParser.parse(s.store.load()!!.snapshot).hubDisplayName)
        }
    }
    @Test fun replacingCredentialAtSameEndpointDropsOldHubNameAndPersistsOnlyAcceptedSnapshot() = Scenario("Account A Hub").use { s ->
        s.online()
        assertEquals("Account A Hub", s.model.state.value.snapshot?.hubDisplayName)
        val previousRevision = s.model.state.value.connectionRevision
        val nextCredential = UUID.randomUUID().toString()
        s.server.acceptedCredential.set(nextCredential)
        s.server.statsBody.set("""{"periods":{"today":{"totalTokens":987}},"devices":[]}""")
        s.model.connect(s.base, nextCredential, "")
        s.await("New credential must receive its own legacy snapshot") {
            s.model.state.value.live && s.model.state.value.snapshot?.periods?.get("today")?.tokens == 987L
        }
        assertEquals(previousRevision + 1, s.model.state.value.connectionRevision)
        assertNull(s.model.state.value.snapshot?.hubDisplayName)
        assertNull(SyncSettingsPresentation.hubHostName(s.model.state.value))
        assertEquals(s.base, s.model.state.value.connectedEndpoint)
        assertEquals(s.base, s.model.state.value.lastSuccessfulEndpoint)
        s.advance(15)
        assertEquals(987L, s.model.state.value.snapshot?.periods?.get("today")?.tokens)
        assertNull(s.model.state.value.snapshot?.hubDisplayName)
        val restored = HubParser.parse(s.store.load()!!.snapshot)
        assertEquals(987L, restored.periods.getValue("today").tokens)
        assertNull(restored.hubDisplayName)
    }

}
