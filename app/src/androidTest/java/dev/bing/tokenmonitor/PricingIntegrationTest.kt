package dev.bing.tokenmonitor

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PricingIntegrationTest {
    @Test fun readOnlyPricingCacheInvalidatesAndRejectsStaleVersions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val store = HubStore(app, "test-pricing-${UUID.randomUUID()}.bin")
        val owner = ViewModelStore()
        val statsRevision = AtomicInteger(1)
        val priceRevision = AtomicInteger(1)
        val badMethods = AtomicInteger()
        val priceReads = AtomicInteger()
        val client = HubViewModel.defaultClient().newBuilder().addInterceptor { chain ->
            if (chain.request().method != "GET") badMethods.incrementAndGet()
            val path = chain.request().url.encodedPath
            val body = when (path) {
                "/api/health" -> """{"ok":true,"role":"hub"}"""
                "/api/stats" -> """{"periods":{"today":{"totalTokens":123,"costUsd":"0.75"}},"syncSettingsRevisions":{"customPricing":${statsRevision.get()}}}"""
                "/api/sync/settings/customPricing" -> {
                    priceReads.incrementAndGet()
                    """{"ok":true,"version":1,"revision":${priceRevision.get()},"value":[{"modelId":"demo-model","inputPerM":0,"cacheWrite1hPerM":2.5}]}"""
                }
                else -> ""
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if (path.endsWith("/stream")) 503 else 200)
                .message("Synthetic response").header("Content-Type", "application/json")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        lateinit var model: HubViewModel
        instrumentation.runOnMainSync { model = HubViewModel(app, store, client); owner.put("pricing", model) }
        fun await(condition: () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!condition() && System.nanoTime() < deadline) Thread.sleep(10)
            assertTrue("Pricing condition was not reached", condition())
        }
        try {
            await { !model.state.value.loading }
            instrumentation.runOnMainSync { model.setForeground(true); model.connect("https://example.invalid", UUID.randomUUID().toString(), "") }
            await { model.state.value.snapshot != null && !model.state.value.loading }
            instrumentation.runOnMainSync { model.loadPricing() }
            await { model.state.value.pricing != null && store.load()?.pricing != null }
            assertEquals(1L, model.state.value.pricing!!.revision)
            assertEquals("0.75", model.state.value.snapshot!!.periods.getValue("today").cost)
            assertEquals("0", PricingParser.parse(store.load()!!.pricing!!).models.single().rates.getValue("inputPerM").toPlainString())
            statsRevision.set(2)
            instrumentation.runOnMainSync { model.refreshSaved() }
            await { model.state.value.snapshot?.pricingRevision == "2" && !model.state.value.loading && store.load()?.pricing == null }
            assertNull(model.state.value.pricing)
            instrumentation.runOnMainSync { model.loadPricing() }
            await { !model.state.value.loading }
            assertNull(model.state.value.pricing)
            assertEquals(R.string.error_revision_changed, model.state.value.error)
            priceRevision.set(2)
            instrumentation.runOnMainSync { model.loadPricing() }
            await { model.state.value.pricing?.revision == 2L && store.load()?.pricing != null }
            val beforeBackground = priceReads.get()
            instrumentation.runOnMainSync { model.setForeground(false); model.loadPricing() }
            assertEquals(beforeBackground, priceReads.get())
            assertEquals(0, badMethods.get())
            assertEquals(123L, model.state.value.snapshot!!.periods.getValue("today").tokens)
        } finally {
            instrumentation.runOnMainSync { model.setForeground(false); owner.clear() }
            store.clear(); client.dispatcher.cancelAll(); client.connectionPool.evictAll()
        }
    }
}
