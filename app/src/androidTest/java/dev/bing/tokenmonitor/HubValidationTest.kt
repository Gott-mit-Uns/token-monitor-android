package dev.bing.tokenmonitor

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class HubValidationTest {
    @Test fun invalidHealthNeverEnablesPrivateReadsOrStreamOnResume() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val store = HubStore(app, "test-validation-${UUID.randomUUID()}.bin")
        val owner = ViewModelStore()
        val healthy = AtomicBoolean()
        val privateRequests = AtomicInteger()
        val healthRequests = AtomicInteger()
        val stats = """{"periods":{"today":{"totalTokens":123}},"devices":[]}"""
        val candidateCredential = UUID.randomUUID().toString()
        val client = HubViewModel.defaultClient().newBuilder().addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            val type: String
            val body: String
            if (path == "/api/health") {
                healthRequests.incrementAndGet()
                type = "application/json"
                body = if (healthy.get()) """{"ok":true,"role":"hub"}""" else """{"ok":true,"role":"agent"}"""
            } else {
                privateRequests.incrementAndGet()
                type = if (path.endsWith("/stream")) "text/event-stream" else "application/json"
                body = if (path.endsWith("/stream")) "event: snapshot\ndata: {\"stats\":$stats}\n\n" else stats
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type", type).body(body.toResponseBody(type.toMediaType())).build()
        }.build()
        lateinit var model: HubViewModel
        instrumentation.runOnMainSync { model = HubViewModel(app, store, client); owner.put("validation", model) }
        fun await(condition: () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!condition() && System.nanoTime() < deadline) Thread.sleep(10)
            assertTrue("Validation condition was not reached", condition())
        }
        try {
            await { !model.state.value.loading }
            instrumentation.runOnMainSync { model.setForeground(true); model.connect("https://example.invalid", candidateCredential, "") }
            await { !model.state.value.loading }
            assertEquals(0, privateRequests.get())
            assertFalse(model.state.value.live)
            instrumentation.runOnMainSync { model.setForeground(false); model.setForeground(true) }
            await { !model.state.value.loading }
            // A rejected first connection is only a draft. Resume must not retry or save it.
            assertEquals(1, healthRequests.get())
            assertNull(store.load())
            assertEquals(0, privateRequests.get())
            instrumentation.runOnMainSync { model.loadHistory() }
            assertEquals(0, privateRequests.get())
            healthy.set(true)
            instrumentation.runOnMainSync { model.refreshSaved() }
            assertEquals(1, healthRequests.get())
            instrumentation.runOnMainSync { model.connect("https://example.invalid", candidateCredential, "") }
            await { model.state.value.snapshot != null && privateRequests.get() >= 2 }
            assertEquals(123L, model.state.value.snapshot?.periods?.get("today")?.tokens)
        } finally {
            instrumentation.runOnMainSync { model.setForeground(false); owner.clear() }
            store.clear(); client.dispatcher.cancelAll(); client.connectionPool.evictAll()
        }
    }
}
