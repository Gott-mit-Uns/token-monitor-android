package dev.bing.tokenmonitor

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.buffer
import okio.source
import org.junit.Assert.*
import org.junit.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

/** Public synthetic SSE and ordinary reads exercise revision races, cache and cancellation. */
class DeviceHistoryIntegrationTest {
    @Test fun deviceHistoryFollowsSseRevisionDiscardsOldReadAndRestoresOnlyProjectedCache() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val store = HubStore(app, "test-fixed-read-${UUID.randomUUID()}.bin")
        val owner = ViewModelStore()
        val revision = AtomicInteger(1); val reads = AtomicInteger(); val badMethods = AtomicInteger()
        val hold = AtomicBoolean(); var entered = CountDownLatch(1); var release = CountDownLatch(1)
        val input = PipedInputStream(65536); val output = PipedOutputStream(input)
        fun stats() = """{"deviceHistoryRevision":"${revision.get()}","periods":{"today":{"totalTokens":10}},"devices":[{"deviceId":"public","periods":{"today":{"totalTokens":10},"allTime":{"totalTokens":10}},"periodWindows":{"timeZone":"Etc/UTC","today":{"key":"2026-10-09","endsAt":"2999-10-10T00:00:00Z"}}}]}"""
        val client = HubViewModel.defaultClient().newBuilder().addInterceptor { chain ->
            if (chain.request().method != "GET") badMethods.incrementAndGet()
            val path = chain.request().url.encodedPath
            val response = if (path == "/api/stats/stream") {
                output.write(("event: snapshot\ndata: {\"stats\":" + stats() + "}\n\n").toByteArray()); output.flush()
                object : ResponseBody() {
                    private val buffered = input.source().buffer()
                    override fun contentType() = "text/event-stream".toMediaType()
                    override fun contentLength() = -1L
                    override fun source() = buffered
                }
            } else {
                val body = when (path) {
                    "/api/health" -> """{"ok":true,"role":"hub"}"""
                    "/api/stats" -> stats()
                    "/api/devices" -> {
                        val observed = revision.get(); reads.incrementAndGet()
                        val payload = """{"devices":[{"deviceId":"public","historyAvailable":true,"sessions":{"public":"discarded-fixture"},"history":{"daily":[{"date":"2026-10-08","tokens":${observed * 100},"cost":1,"tokenComponentsAvailable":true,"unclassifiedTokens":0}]}}]}"""
                        if (hold.getAndSet(false)) { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
                        payload
                    }
                    else -> error("Unexpected synthetic read")
                }
                body.toResponseBody("application/json".toMediaType())
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Public fixture")
                .header("Content-Type", response.contentType().toString()).body(response).build()
        }.build()
        lateinit var model: HubViewModel
        fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
        fun await(condition: () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(7)
            while (!condition() && System.nanoTime() < deadline) Thread.sleep(10)
            assertTrue("Device-history condition unavailable", condition())
        }
        try {
            main { model = HubViewModel(app, store, client); owner.put("first", model) }
            await { !model.state.value.loading }
            main { model.setForeground(true); model.connect("https://example.invalid", UUID.randomUUID().toString(), "") }
            await { model.state.value.live }
            main { model.loadDeviceHistory() }
            await { model.state.value.deviceHistories != null && store.load()?.deviceHistory != null }
            assertEquals(110L, FixedPeriods.evaluate("last7", model.state.value.snapshot!!, model.state.value.deviceHistories, Instant.parse("2026-10-09T01:00:00Z")).period!!.tokens)
            assertFalse(store.load()!!.deviceHistory!!.contains("discarded-fixture"))
            hold.set(true)
            main { model.loadDeviceHistory() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            revision.set(2)
            output.write(("event: snapshot\ndata: {\"stats\":" + stats() + "}\n\n").toByteArray()); output.flush()
            await { model.state.value.snapshot?.deviceHistoryRevision == "2" && store.load()?.deviceHistory == null }
            release.countDown()
            await { model.state.value.deviceHistories?.signature?.startsWith("2|") == true && store.load()?.deviceHistory != null }
            assertEquals(3, reads.get())
            assertEquals(210L, FixedPeriods.evaluate("last7", model.state.value.snapshot!!, model.state.value.deviceHistories, Instant.parse("2026-10-09T01:00:00Z")).period!!.tokens)
            val saved = store.load()!!.deviceHistory
            entered = CountDownLatch(1); release = CountDownLatch(1); hold.set(true)
            main { model.loadDeviceHistory() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            main { model.setForeground(false) }
            release.countDown()
            await { !model.state.value.loading && !model.state.value.live }
            val count = reads.get()
            main { model.loadDeviceHistory() }
            assertEquals(count, reads.get()); assertEquals(saved, store.load()!!.deviceHistory)
            lateinit var restored: HubViewModel
            main { restored = HubViewModel(app, store, client); owner.put("restored", restored) }
            await { !restored.state.value.loading }
            assertEquals(model.state.value.deviceHistories, restored.state.value.deviceHistories)
            assertFalse(restored.state.value.live)
            assertEquals(0, badMethods.get())
        } finally {
            release.countDown()
            main { owner.clear() }
            runCatching { output.close() }; runCatching { input.close() }
            store.clear(); client.dispatcher.cancelAll(); client.connectionPool.evictAll()
        }
    }
}
