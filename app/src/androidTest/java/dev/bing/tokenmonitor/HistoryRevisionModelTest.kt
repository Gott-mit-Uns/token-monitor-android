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
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Real ViewModel, SSE merge and encrypted cache with in-memory public responses only. */
class HistoryRevisionModelTest {
    @Test fun legacyPreviewInvalidatesHistoryAndRejectsOldRepliesWhileFreshnessAndAuthStayIndependent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val store = HubStore(app, "test-history-revision-${UUID.randomUUID()}.bin")
        val owner = ViewModelStore()
        val counter = AtomicInteger(100)
        val historyReads = AtomicInteger()
        val historyStatus = AtomicInteger(200)
        val badMethods = AtomicInteger()
        val hold = AtomicBoolean()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val input = PipedInputStream(65536)
        val output = PipedOutputStream(input)
        fun stats() = """{"updatedAt":"2026-10-09T08:00:00Z","periods":{"today":{"totalTokens":${counter.get() + 50}}},"historyPreview":{"daily":[{"date":"2026-10-08","tokens":${counter.get()},"cost":0.1},{"date":"2026-10-09","tokens":50,"cost":0.05}],"monthly":[{"month":"2026-10","tokens":${counter.get() + 50},"cost":0.15}]}}"""
        fun pushStats() { output.write(("event: stats\ndata: {\"stats\":" + stats() + "}\n\n").toByteArray()); output.flush() }
        val client = HubViewModel.defaultClient().newBuilder().addInterceptor { chain ->
            if (chain.request().method != "GET") badMethods.incrementAndGet()
            val path = chain.request().url.encodedPath
            var status = 200
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
                    "/api/history" -> {
                        val observed = counter.get()
                        historyReads.incrementAndGet()
                        status = historyStatus.get()
                        val payload = """{"daily":[{"date":"2026-10-08","tokens":$observed,"cost":0.1},{"date":"2026-10-09","tokens":50,"cost":0.05}],"monthly":[{"month":"2026-10","tokens":${observed + 50},"cost":0.15}]}"""
                        if (hold.getAndSet(false)) { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
                        payload
                    }
                    else -> error("Unexpected public synthetic read")
                }
                body.toResponseBody("application/json".toMediaType())
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Public fixture")
                .header("Content-Type", response.contentType().toString()).body(response).build()
        }.build()
        lateinit var model: HubViewModel
        fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
        fun await(condition: () -> Boolean) {
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(7)
            while (!condition() && System.nanoTime() < until) Thread.sleep(10)
            assertTrue("Public history revision condition unavailable", condition())
        }
        try {
            main { model = HubViewModel(app, store, client); owner.put("history", model) }
            await { !model.state.value.loading }
            main { model.setForeground(true); model.connect("https://example.invalid", UUID.randomUUID().toString(), "") }
            await { model.state.value.live }
            assertNull(model.state.value.snapshot!!.historyRevision)
            main { model.loadHistory(manual = true) }
            await { model.state.value.history != null && store.load()?.history != null && !model.state.value.loading }
            assertEquals(100L, model.state.value.history!!.daily.last().tokens)
            assertEquals(1L,model.state.value.historyRenderRevision)
            val sameHistory=model.state.value.history
            main {model.loadHistory(manual = true)}
            await {!model.state.value.loading && model.state.value.historyRenderRevision==2L}
            assertEquals("Equal payload is still one accepted Dashboard render",sameHistory,model.state.value.history)
            assertEquals(2,historyReads.get())
            val firstSignature = homeHistorySignature(model.state.value.snapshot)
            output.write("event: freshness\ndata: {\"stats\":{\"updatedAt\":\"2026-10-09T08:01:00Z\",\"devices\":[]}}\n\n".toByteArray()); output.flush()
            await { model.state.value.snapshot!!.updated == "2026-10-09T08:01:00Z" }
            assertEquals(firstSignature, homeHistorySignature(model.state.value.snapshot))
            assertEquals(100L, model.state.value.history!!.daily.last().tokens)

            hold.set(true)
            main { model.loadHistory(manual = true) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            counter.set(200); pushStats()
            await { homeHistorySignature(model.state.value.snapshot) != firstSignature && model.state.value.history == null && store.load()?.history == null }
            release.countDown()
            await { !model.state.value.loading && model.state.value.error == R.string.error_revision_changed }
            assertNull(model.state.value.history)
            assertNull(store.load()!!.history)
            assertEquals(3, historyReads.get())
            assertEquals("Stale response is not a render event",2L,model.state.value.historyRenderRevision)
            main { model.loadHistory(manual = true) }
            await { model.state.value.history?.daily?.last()?.tokens == 200L && store.load()?.history != null && !model.state.value.loading }
            assertEquals(200L, HistoryParser.parse(store.load()!!.history!!).daily.last().tokens)
            assertEquals(4, historyReads.get())
            assertEquals(3L,model.state.value.historyRenderRevision)

            historyStatus.set(401)
            main { model.loadHistory(manual = true) }
            await { !model.state.value.loading && !model.state.value.live && model.state.value.error == R.string.error_auth }
            assertFalse("Auth failures revoke the policy's live gate", model.state.value.live)
            assertEquals(200L, model.state.value.history!!.daily.last().tokens)
            main { model.setForeground(false); model.loadHistory(manual = true) }
            assertEquals(5, historyReads.get())
            assertEquals("Auth and rejected foreground reads cannot advance the render event",3L,model.state.value.historyRenderRevision)
            assertEquals(0, badMethods.get())
        } finally {
            release.countDown()
            main { owner.clear() }
            runCatching { output.close() }; runCatching { input.close() }
            store.clear(); client.dispatcher.cancelAll(); client.connectionPool.evictAll()
        }
    }
}
