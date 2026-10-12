package dev.bing.tokenmonitor

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Real loopback HTTPS, SSE, ViewModel generations and saves; all values are public synthetic data. */
class FreshnessReuseIntegrationTest {
    companion object {
        private val certificate by lazy { HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build() }
        private val serverTls by lazy { HandshakeCertificates.Builder().heldCertificate(certificate).build() }
        private val clientTls by lazy { HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build() }
        private const val FIRST = "2026-10-09T08:00:00Z"
        private const val SECOND = "2026-10-09T09:00:00Z"
        private const val FRESH_ONE = "2026-10-09T08:01:00Z"
        private const val FRESH_TWO = "2026-10-09T08:02:00Z"
        private const val PROVIDER_UPDATED = "2026-10-09T06:00:00Z"

        private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
        private fun await(message: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (System.nanoTime() < deadline) {
                if (condition()) return
                Thread.sleep(10)
            }
            assertTrue(message, condition())
        }

        private fun stats(updated: String, tokens: Long = 5_000_000_123L, model: String = "public-model-one"): String {
            val period = """{"totalTokens":$tokens,"costUsd":"0.015625","unpricedTokens":17,"cacheReadTokens":20,"cacheWriteTokens":3,"outputTokens":11,"unclassifiedTokens":0,"capabilities":{"tokenComponents":true},"clients":{"codex":$tokens},"models":{"$model":$tokens},"projects":{"public-project":{"label":"Public project","tokens":$tokens,"costUsd":"0.015625","clients":{"codex":$tokens}}},"sessions":{"public-session":{"sessionId":"public-session","title":"Public session","tokens":$tokens,"costUsd":"0.015625","client":"codex","model":"$model","lastUsedAt":"2026-10-09T05:00:00Z"}}}"""
            val history = """{"daily":[{"date":"2026-10-09","tokens":$tokens,"cost":"0.015625","perModel":{"$model":{"tokens":$tokens}}}],"monthly":[{"month":"2026-10","tokens":$tokens,"cost":"0.015625"}]}"""
            return """{"updatedAt":"$updated","staleAfterMs":60000,"historyRevision":"public-history","deviceHistoryRevision":"public-device-history","subscriptionsUpdatedAt":"public-subscriptions","syncSettingsRevisions":{"modelAliases":"public-aliases","customPricing":"public-pricing"},"periods":{"today":$period,"month":$period,"allTime":$period},"devices":[{"deviceId":"public-device-one","hostname":"Public first device","updatedAt":"$FIRST","receivedAt":"$FIRST","stale":false,"platform":"linux","periods":{"today":{"totalTokens":$tokens,"clients":{"codex":$tokens},"sessions":{"projected-detail":{"model":"public-projected-model"}}}},"clientHealth":{"version":1,"observedAt":"$FIRST","clients":{"codex":{"overall":"healthy","source":{"state":"detected"},"collection":{"state":"ok"},"data":{"liveTokens":$tokens}}}}},{"deviceId":"public-device-two","hostname":"Public second device","updatedAt":"$FIRST","receivedAt":"$FIRST","stale":false,"periods":{"today":{"totalTokens":19}}}],"limits":{"updatedAt":"$PROVIDER_UPDATED","providers":[{"provider":"codex","status":"ok","stale":false,"updatedAt":"$PROVIDER_UPDATED","windows":[{"kind":"rolling","metric":"tokens","remainingPercent":75}]}]},"historyPreview":$history}"""
        }

        // Business fields, unknown devices and provider records here must be ignored by a freshness merge.
        private fun freshness(updated: String, stale: Boolean): String = """{"updatedAt":"$updated","staleAfterMs":120000,"periods":{"today":{"totalTokens":1}},"devices":[{"deviceId":"public-device-one","updatedAt":"$updated","receivedAt":"$updated","stale":$stale,"ageMs":13,"hostname":"Ignored rename","periods":{"today":{"totalTokens":1}}},{"deviceId":"unknown-public-device","stale":true}],"limits":{"updatedAt":"$updated","providers":[{"provider":"codex","status":"failed","updatedAt":"$updated","windows":[{"remainingPercent":0}]}]}}"""

        private fun assertBusinessReferences(before: Snapshot, after: Snapshot) {
            assertSame(before.periods, after.periods)
            before.periods.forEach { (key, period) ->
                val reused = after.periods.getValue(key)
                assertSame(period, reused)
                assertSame(period.sessions, reused.sessions)
                assertSame(period.sessions.single(), reused.sessions.single())
                assertSame(period.projects, reused.projects)
                assertSame(period.projects.single(), reused.projects.single())
            }
            assertSame(before.historyPreview, after.historyPreview)
            assertSame(before.observedModelIds, after.observedModelIds)
            assertSame(before.limits, after.limits)
            assertSame(before.limits.single(), after.limits.single())
            assertEquals(PROVIDER_UPDATED, after.limits.single().updated)
            assertEquals(75.0, after.limits.single().remaining!!, 0.0)
            before.devices.zip(after.devices).forEach { (old, current) ->
                assertSame(old.periods, current.periods)
                assertSame(old.periodDetails, current.periodDetails)
                assertSame(old.health, current.health)
                assertEquals(old.name, current.name)
            }
        }
    }

    /** In-memory storage captures the exact production save request without touching an existing binding. */
    private class CaptureStore(app: Application) : HubStore(app, "test-freshness-unused-${UUID.randomUUID()}.bin") {
        val saved = AtomicReference<SavedHub?>()
        val writes = AtomicInteger()
        override fun load(): SavedHub? = saved.get()
        override fun save(record: SavedHub) { writes.incrementAndGet(); saved.set(record) }
        override fun clear() { saved.set(null) }
    }

    private class StreamConnection(private val socket: Socket, private val output: OutputStream) {
        val ready = CountDownLatch(1)
        val closed = CountDownLatch(1)
        fun send(name: String, body: String) = synchronized(output) {
            output.write("event: $name\ndata: {\"stats\":$body}\n\n".toByteArray(Charsets.UTF_8))
            output.flush()
        }
        fun finishNormally() { socket.close() }
    }

    /** Bound only to loopback. Request headers and generated credentials are discarded, never diagnosed. */
    private class Peer(private val initialStats: String) : AutoCloseable {
        private val server = serverTls.sslContext().serverSocketFactory.createServerSocket(0, 12, InetAddress.getByName("127.0.0.1"))
        val address = "https://127.0.0.1:${server.localPort}"
        val streams = CopyOnWriteArrayList<StreamConnection>()
        val statsReads = AtomicInteger()
        val badMethods = AtomicInteger()
        private val sockets = ConcurrentLinkedQueue<Socket>()
        private val workers = ConcurrentLinkedQueue<Thread>()
        private val accepting = thread(name = "public-freshness-accept", isDaemon = true) {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: IOException) { break }
                sockets += socket
                workers += thread(name = "public-freshness-response", isDaemon = true) {
                    var stream: StreamConnection? = null
                    try {
                        socket.use {
                            socket.soTimeout = 20000
                            val input = socket.getInputStream().bufferedReader()
                            val line = input.readLine().orEmpty()
                            if (!line.startsWith("GET ")) badMethods.incrementAndGet()
                            val path = line.split(' ').getOrNull(1).orEmpty()
                            while (!input.readLine().isNullOrEmpty()) { /* Discard all request headers. */ }
                            val output = socket.getOutputStream()
                            if (path == "/api/stats/stream") {
                                val connection = StreamConnection(socket, output)
                                stream = connection
                                streams += connection
                                output.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n".toByteArray())
                                output.flush()
                                connection.ready.countDown()
                                input.read() // The ViewModel owns cancellation; EOF retires this particular socket.
                            } else {
                                val body = when (path) {
                                    "/api/health" -> """{"ok":true,"role":"hub"}"""
                                    "/api/stats" -> { statsReads.incrementAndGet(); initialStats }
                                    else -> "{}"
                                }
                                val code = if (path in setOf("/api/health", "/api/stats")) 200 else 404
                                val bytes = body.toByteArray(Charsets.UTF_8)
                                output.write("HTTP/1.1 $code Public\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                output.write(bytes); output.flush()
                            }
                        }
                    } catch (_: IOException) { /* EOF and cancellation are asserted without payload diagnostics. */ }
                    finally { stream?.closed?.countDown() }
                }
            }
        }
        fun stream(number: Int): StreamConnection {
            await("Expected loopback SSE generation unavailable") { streams.size >= number }
            return streams[number - 1].also { assertTrue("Loopback SSE headers unavailable", it.ready.await(5, TimeUnit.SECONDS)) }
        }
        override fun close() {
            server.close(); sockets.forEach { runCatching { it.close() } }
            accepting.join(1000); workers.forEach { it.join(1000) }
        }
    }

    private class Fixture : AutoCloseable {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val peer = Peer(stats("2026-10-09T07:00:00Z"))
        val store = CaptureStore(app)
        private val preferences = DisplayPreferenceStore(app, "test-freshness-display-${UUID.randomUUID()}.bin")
        private val owner = ViewModelStore()
        private val client = HubViewModel.defaultClient().newBuilder()
            .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
        val merges = AtomicInteger()
        val failedParses = AtomicInteger()
        private val observation = HubWorkTrace.observe { span ->
            if (span.stage == HubWorkStage.JSON_MERGE) merges.incrementAndGet()
            if (span.stage == HubWorkStage.SNAPSHOT_PARSE && !span.success) failedParses.incrementAndGet()
        }
        lateinit var model: HubViewModel
        init {
            main { model = HubViewModel(app, store, client, preferences); owner.put("freshness", model) }
            await("Isolated ViewModel did not finish loading") { !model.state.value.loading }
            main { model.setForeground(true); model.connect(peer.address, UUID.randomUUID().toString(), "") }
            await("Public HTTP baseline did not finish validation and saving") { !model.state.value.loading && model.state.value.snapshot != null && store.writes.get() == 1 }
            peer.stream(1)
        }
        fun snapshot() = model.state.value.snapshot!!
        fun sendAndAwait(stream: StreamConnection, name: String, body: String, updated: String): Snapshot {
            stream.send(name, body)
            await("Expected public SSE revision not accepted") { model.state.value.snapshot?.updated == updated && model.state.value.live }
            awaitSaved(updated)
            return snapshot()
        }
        fun awaitSaved(updated: String) {
            await("Latest accepted public SSE body was not saved") {
                store.saved.get()?.let { Json.parseToJsonElement(it.snapshot).jsonObject["updatedAt"]?.jsonPrimitive?.content == updated } == true
            }
            val record = store.saved.get()!!
            assertEquals(snapshot(), HubParser.parse(record.snapshot, record.observedModelIds))
            assertEquals(snapshot().observedModelIds, record.observedModelIds)
        }
        fun settleRejectedFrame(stream: StreamConnection, name: String, body: String) {
            val before = merges.get()
            stream.send(name, body)
            await("Public rejected event was not merged by the stream producer") { merges.get() > before }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(150)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        fun stopAndAssertQuiet(stream: StreamConnection, quietMs: Long = 350) {
            val before = snapshot()
            val writes = store.writes.get()
            val record = store.saved.get()
            val generations = peer.streams.size
            val reads = peer.statsReads.get()
            main { model.setForeground(false) }
            assertTrue("Background did not close the loopback SSE socket", stream.closed.await(5, TimeUnit.SECONDS))
            Thread.sleep(quietMs)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertFalse(model.state.value.live)
            assertSame(before, snapshot())
            assertSame(record, store.saved.get())
            assertEquals(writes, store.writes.get())
            assertEquals(generations, peer.streams.size)
            assertEquals(reads, peer.statsReads.get())
        }
        fun restoreBackground(): HubViewModel {
            lateinit var restored: HubViewModel
            main { restored = HubViewModel(app, store, client, preferences); owner.put("restored", restored) }
            await("Saved public snapshot did not cold-parse") { !restored.state.value.loading && restored.state.value.snapshot != null }
            return restored
        }
        override fun close() {
            main { model.setForeground(false); owner.clear() }
            peer.close(); client.dispatcher.cancelAll(); client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
            observation.close(); store.clear(); preferences.clear()
            assertEquals("All public fixture requests must be read-only", 0, peer.badMethods.get())
        }
    }

    @Test fun consecutiveFreshnessReusesBusinessObjectsAndSavesEquivalentLatestBody() {
        Fixture().use { fixture ->
            val stream = fixture.peer.stream(1)
            val full = fixture.sendAndAwait(stream, "snapshot", stats(FIRST), FIRST)
            assertTrue(full.observedModelIds.contains("public-projected-model"))
            assertEquals(5_000_000_123L, full.periods.getValue("today").sessions.single().tokens)
            assertNotNull(full.historyPreview)
            val first = fixture.sendAndAwait(stream, "freshness", freshness(FRESH_ONE, true), FRESH_ONE)
            assertBusinessReferences(full, first)
            assertTrue(first.devices.first().stale!!)
            assertEquals(FRESH_ONE, first.devices.first().updated)
            assertEquals(FRESH_ONE, first.devices.first().received)
            assertFalse(first.devices.last().stale!!)
            assertEquals(FIRST, first.devices.last().updated)
            assertEquals(2, first.devices.size)
            val second = fixture.sendAndAwait(stream, "freshness", freshness(FRESH_TWO, false), FRESH_TWO)
            assertBusinessReferences(first, second)
            assertFalse(second.devices.first().stale!!)
            assertEquals(FRESH_TWO, second.devices.first().received)
            assertEquals(1, fixture.peer.statsReads.get())
            fixture.stopAndAssertQuiet(stream)
            val restored = fixture.restoreBackground()
            assertEquals(second, restored.state.value.snapshot)
            assertNotSame(second.periods, restored.state.value.snapshot!!.periods)
            assertFalse(restored.state.value.live)
            assertEquals(1, fixture.peer.streams.size)
            assertEquals(1, fixture.peer.statsReads.get())
        }
    }

    @Test fun eofReconnectIgnoresOrphanFreshnessAndUsesOnlyItsNewFullBusinessBaseline() {
        Fixture().use { fixture ->
            val firstStream = fixture.peer.stream(1)
            val oldFull = fixture.sendAndAwait(firstStream, "snapshot", stats(FIRST), FIRST)
            val oldFresh = fixture.sendAndAwait(firstStream, "freshness", freshness(FRESH_ONE, true), FRESH_ONE)
            assertBusinessReferences(oldFull, oldFresh)
            val savedBeforeEof = fixture.store.saved.get()
            val writesBeforeEof = fixture.store.writes.get()
            firstStream.finishNormally()
            assertTrue("First public stream did not reach EOF", firstStream.closed.await(5, TimeUnit.SECONDS))
            await("Ordinary EOF did not mark the ViewModel disconnected") { !fixture.model.state.value.live }
            val secondStream = fixture.peer.stream(2)
            fixture.settleRejectedFrame(secondStream, "freshness", freshness(FRESH_TWO, false))
            assertSame(oldFresh, fixture.snapshot())
            assertSame(savedBeforeEof, fixture.store.saved.get())
            assertEquals(writesBeforeEof, fixture.store.writes.get())
            assertFalse(fixture.model.state.value.live)
            assertEquals(1, fixture.peer.statsReads.get())

            val newFull = fixture.sendAndAwait(secondStream, "stats", stats(SECOND, 7_000_000_999L, "public-model-two"), SECOND)
            assertNotSame(oldFull.periods, newFull.periods)
            assertEquals(7_000_000_999L, newFull.periods.getValue("today").tokens)
            assertTrue(newFull.observedModelIds.contains("public-model-two"))
            assertFalse(newFull.observedModelIds.contains("public-model-one"))
            val newFresh = fixture.sendAndAwait(secondStream, "freshness", freshness("2026-10-09T09:01:00Z", true), "2026-10-09T09:01:00Z")
            assertBusinessReferences(newFull, newFresh)
            assertNotSame(oldFull.periods, newFresh.periods)
            assertEquals(7_000_000_999L, newFresh.periods.getValue("today").sessions.single().tokens)
            assertTrue(newFresh.devices.first().stale!!)
            fixture.stopAndAssertQuiet(secondStream, quietMs = 2800)
        }
    }

    @Test fun malformedFullInvalidatesReuseUntilAnotherValidFullIsAccepted() {
        Fixture().use { fixture ->
            val stream = fixture.peer.stream(1)
            val accepted = fixture.sendAndAwait(stream, "snapshot", stats(FIRST), FIRST)
            val saved = fixture.store.saved.get()
            val writes = fixture.store.writes.get()
            val failures = fixture.failedParses.get()
            // Valid JSON and a valid periods map pass framing/projection/merge, but typed period parsing fails.
            fixture.settleRejectedFrame(stream, "stats", """{"updatedAt":"2026-10-09T08:03:00Z","periods":{"today":23},"devices":[]}""")
            await("Malformed full frame did not reach typed parsing") { fixture.failedParses.get() > failures }
            assertSame(accepted, fixture.snapshot())
            fixture.settleRejectedFrame(stream, "freshness", freshness(FRESH_TWO, true))
            await("Freshness after a malformed full did not retain the full-parse rejection") { fixture.failedParses.get() >= failures + 2 }
            assertSame(accepted, fixture.snapshot())
            assertSame(saved, fixture.store.saved.get())
            assertEquals(writes, fixture.store.writes.get())

            val recovered = fixture.sendAndAwait(stream, "stats", stats(SECOND, 7_000_000_999L, "public-model-two"), SECOND)
            val recoveredFresh = fixture.sendAndAwait(stream, "freshness", freshness("2026-10-09T09:02:00Z", false), "2026-10-09T09:02:00Z")
            assertBusinessReferences(recovered, recoveredFresh)
            assertEquals(7_000_000_999L, recoveredFresh.periods.getValue("today").tokens)
            assertFalse(recoveredFresh.devices.first().stale!!)
            fixture.stopAndAssertQuiet(stream)
        }
    }
}
