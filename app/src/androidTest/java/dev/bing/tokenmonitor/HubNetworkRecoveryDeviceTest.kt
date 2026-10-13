package dev.bing.tokenmonitor

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Real Android main/IO threads, Keystore cache, validated loopback TLS and SSE.
 * Transient NETWORK failures and network scopes are synthetic; this is not a physical radio test.
 * Production's real 2/4/8-second recovery delays are retained, without virtual clocks or reflection.
 */
class HubNetworkRecoveryDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun await(message: String, timeoutSeconds: Long = 12, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(20)
        assertTrue(message, condition())
    }

    private inner class Scenario : AutoCloseable {
        private val generated = UUID.randomUUID().toString()
        private val certificate = HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build()
        val server = NetworkFixtureHub(generated, HandshakeCertificates.Builder().heldCertificate(certificate).build(),
            """{"periods":{"today":{"totalTokens":456}},"devices":[]}""")
        private val trusted = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val failures = AtomicInteger()
        val failingPath = AtomicReference("/api/health")
        val healthAttempts = AtomicInteger()
        val statsAttempts = AtomicInteger()
        val connection: OkHttpClient = HubViewModel.defaultClient().newBuilder().proxy(Proxy.NO_PROXY)
            .sslSocketFactory(trusted.sslSocketFactory(), trusted.trustManager)
            .addInterceptor { chain ->
                val path = chain.request().url.encodedPath
                if (path == "/api/health") healthAttempts.incrementAndGet()
                if (path == "/api/stats") statsAttempts.incrementAndGet()
                if (path == failingPath.get() && failures.getAndUpdate { (it - 1).coerceAtLeast(0) } > 0)
                    throw IOException("Synthetic transient network failure")
                chain.proceed(chain.request())
            }.build()
        private val storeName = "test-android-network-recovery-${UUID.randomUUID()}.bin"
        val store = HubStore(app, storeName)
        private val owner = ViewModelStore()
        lateinit var model: HubViewModel
            private set

        init {
            store.save(SavedHub("https://127.0.0.1:${server.port}", generated,
                """{"periods":{"today":{"totalTokens":123}},"devices":[]}""", 1))
            main { model = HubViewModel(app, store, connection); owner.put("device-recovery", model) }
            await("Real encrypted cache must finish loading") { !model.state.value.loading }
        }

        fun scope(id: Long, ready: Boolean = true) = HubNetworkScope(false, false, emptyList(),
            networkId = id, available = true, ready = ready)

        fun online() {
            main { model.updateNetworkScope(scope(1)); model.setForeground(true) }
            await("Initial validated TLS snapshot and SSE must be live") {
                model.state.value.live && server.streams.get() == 1
            }
            await("Initial network snapshot must be durable") { cacheTokens() == 456L }
            assertEquals(HubStartup.HOME, model.startup.value)
        }

        fun cacheTokens(): Long? = store.load()?.parsedSnapshot?.periods?.get("today")?.tokens

        fun assertOneReplacementStream() {
            await("Retired SSE must close and exactly one replacement SSE may remain") {
                server.streams.get() == 2 && server.closedStreams.get() == 1 && model.state.value.live
            }
            assertEquals(1, server.streams.get() - server.closedStreams.get())
            assertEquals(0, server.badAuthentication.get())
        }

        fun record(scene: String) {
            val report = JSONObject().put("scenario", scene).put("sdkInt", Build.VERSION.SDK_INT)
                .put("realAndroidMainThread", true).put("realEncryptedStore", true)
                .put("validatedLoopbackTls", true).put("productionRecoveryDelays", true)
                .put("healthAttempts", healthAttempts.get()).put("statsAttempts", statsAttempts.get())
                .put("openedStreams", server.streams.get()).put("closedStreams", server.closedStreams.get())
                .put("live", model.state.value.live).put("retainedCacheTokens", cacheTokens())
                .put("scope", "Synthetic network scopes/failures on Android; no physical cellular or Wi-Fi claim")
            File(app.getExternalFilesDir(null), "android-network-recovery-$scene-public.json").writeText(report.toString(2))
        }

        override fun close() {
            main { owner.clear() }
            connection.dispatcher.cancelAll()
            connection.connectionPool.evictAll()
            server.close()
            // Only this UUID-named test cache is removed; never the production default store.
            store.clear()
        }
    }

    @Test fun pendingColdNetworkWaitsForReadyThenFirstHealthFailureRecoversAutomatically() = Scenario().use { s ->
        s.failures.set(1)
        main { s.model.updateNetworkScope(s.scope(1, ready = false)); s.model.setForeground(true) }
        Thread.sleep(600)
        assertEquals(HubStartup.LOADING, s.model.startup.value)
        assertEquals("Pending network details must not trigger HTTPS verification", 0, s.healthAttempts.get())
        assertEquals(0, s.server.requests.get())
        assertEquals(123L, s.cacheTokens())

        main { s.model.updateNetworkScope(s.scope(1)) }
        await("First health NETWORK failure must settle before automatic retry") {
            s.healthAttempts.get() == 1 && !s.model.state.value.loading && s.model.state.value.error != null
        }
        assertFalse(s.model.state.value.live)
        assertEquals(123L, s.model.state.value.snapshot?.periods?.get("today")?.tokens)
        assertEquals(123L, s.cacheTokens())
        await("Same ready network must recover without manual refresh") { s.model.state.value.live }
        await("Accepted retry must be durable") { s.cacheTokens() == 456L }
        assertEquals(HubStartup.HOME, s.model.startup.value)
        assertEquals(2, s.healthAttempts.get())
        assertEquals(1, s.statsAttempts.get())
        assertEquals(1, s.server.streams.get())
        assertEquals(0, s.server.closedStreams.get())
        assertEquals(0, s.server.badAuthentication.get())
        main { repeat(3) { s.model.updateNetworkScope(s.scope(1)) } }
        Thread.sleep(600)
        assertEquals("Repeated ready callback must not duplicate validation", 2, s.healthAttempts.get())
        assertEquals(1, s.server.streams.get())
        s.record("pending-cold-health-retry")
    }

    @Test fun firstStatsNetworkFailureRetainsEncryptedCacheThenRecoversOneReplacementSse() = Scenario().use { s ->
        s.online()
        s.failingPath.set("/api/stats")
        s.failures.set(1)
        val statsBefore = s.statsAttempts.get()
        s.server.statsBody.set("""{"periods":{"today":{"totalTokens":789}},"devices":[]}""")
        main { s.model.updateNetworkScope(s.scope(2)) }
        await("First stats NETWORK failure must settle") {
            s.statsAttempts.get() == statsBefore + 1 && !s.model.state.value.loading && s.model.state.value.error != null
        }
        assertFalse(s.model.state.value.live)
        assertEquals(456L, s.model.state.value.snapshot?.periods?.get("today")?.tokens)
        assertEquals(456L, s.cacheTokens())
        await("Same network's stats read must recover without manual refresh") {
            s.model.state.value.live && s.model.state.value.snapshot?.periods?.get("today")?.tokens == 789L
        }
        await("Recovered stats cache must be durable") { s.cacheTokens() == 789L }
        s.assertOneReplacementStream()
        assertEquals(statsBefore + 2, s.statsAttempts.get())
        main { repeat(3) { s.model.updateNetworkScope(s.scope(2)) } }
        Thread.sleep(600)
        assertEquals(statsBefore + 2, s.statsAttempts.get())
        s.assertOneReplacementStream()
        s.record("stats-retry")
    }

    @Test fun persistentNetworkFailureExhaustsThreeRetriesAndRepeatedReadyDoesNotRearmBudget() = Scenario().use { s ->
        s.online()
        s.failures.set(100)
        val healthBefore = s.healthAttempts.get()
        main { s.model.updateNetworkScope(s.scope(2)) }
        await("Initial attempt plus three production-delay retries must finish", 25) {
            s.healthAttempts.get() == healthBefore + 4 && !s.model.state.value.loading
        }
        await("Retired SSE must stay closed after exhausting the retry budget") { s.server.closedStreams.get() == 1 }
        assertFalse(s.model.state.value.live)
        assertEquals(456L, s.model.state.value.snapshot?.periods?.get("today")?.tokens)
        assertEquals(456L, s.cacheTokens())
        // Observe longer than the largest 8s+jitter backoff. Repeated ready must not extend it.
        repeat(5) {
            main { s.model.updateNetworkScope(s.scope(2)) }
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (System.nanoTime() < until) {
                assertEquals("Repeated ready callback must not rearm an exhausted budget", healthBefore + 4, s.healthAttempts.get())
                Thread.sleep(50)
            }
        }
        assertEquals(1, s.server.streams.get())
        assertEquals(1, s.server.closedStreams.get())
        assertEquals(0, s.server.badAuthentication.get())
        s.record("finite-budget")
    }
}
