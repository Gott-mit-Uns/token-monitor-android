package dev.bing.tokenmonitor

import android.app.Application
import android.os.Looper
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
    private class Scenario : AutoCloseable {
        val app: Application = RuntimeEnvironment.getApplication()
        val secret = UUID.randomUUID().toString()
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build()
        val server = RecoveryFixtureHub(secret, HandshakeCertificates.Builder().heldCertificate(certificate).build())
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
    @Test fun lateStatsFromRetiredNetworkCannotOverwriteReplacementSnapshot() = Scenario().use { s ->
        s.online(); val reads = s.statsAttempts.get(); val serverReads = s.server.statsRequests.get(); val gate = CountDownLatch(1)
        s.server.statsBody.set("""{"periods":{"today":{"totalTokens":456}},"devices":[]}""")
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
            gate.countDown(); s.advance(15)
            assertEquals(789L, s.model.state.value.snapshot?.periods?.get("today")?.tokens)
            assertEquals(reads + 2, s.statsAttempts.get())
        } finally { gate.countDown() }
    }
    @Test fun replacingConnectionCancelsRecoveryForTheOldAuthority() = Scenario().use { s ->
        s.online(); s.failures.set(1); val reads = s.healthAttempts.get()
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
        }
    }
}
