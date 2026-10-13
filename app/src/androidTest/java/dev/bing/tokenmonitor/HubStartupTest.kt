package dev.bing.tokenmonitor

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HubStartupTest {
    private fun await(condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        while (!condition() && System.nanoTime() < end) Thread.sleep(10)
        assertTrue("Startup condition not reached", condition())
    }
    @Test fun configuredStartupWaitsForStatsThenNeverReopensSplash() = configured(false)
    @Test fun failedStartupEntersHomeAndKeepsCache() = configured(true)
    private fun configured(fail: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val store = HubStore(app, "startup-${UUID.randomUUID()}.bin")
        val cached = """{"periods":{"today":{"totalTokens":123}},"devices":[]}"""
        store.save(SavedHub("https://startup.example.invalid", UUID.randomUUID().toString(), cached, 1))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val client = HubViewModel.defaultClient().newBuilder().addInterceptor { chain ->
            val request = chain.request()
            val content = if (request.url.encodedPath.endsWith("/health")) """{"ok":true,"role":"hub"}""" else {
                entered.countDown()
                check(release.await(8, TimeUnit.SECONDS))
                if (fail) throw IOException("Synthetic startup failure")
                cached.replace("123", "456")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type", "application/json").body(content.toResponseBody()).build()
        }.build()
        val owner = ViewModelStore()
        lateinit var model: HubViewModel
        fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
        try {
            main { model = HubViewModel(app, store, client); owner.put("startup", model); model.setForeground(true) }
            assertTrue(entered.await(8, TimeUnit.SECONDS))
            assertEquals(HubStartup.LOADING, model.startup.value)
            assertEquals(123L, model.state.value.snapshot?.periods?.get("today")?.tokens)
            release.countDown()
            await { model.startup.value == HubStartup.HOME }
            assertEquals(if (fail) 123L else 456L, model.state.value.snapshot?.periods?.get("today")?.tokens)
            if (fail) assertNotNull(model.state.value.error)
            main { model.setForeground(false); model.setForeground(true) }
            assertEquals(HubStartup.HOME, model.startup.value)
        } finally {
            release.countDown()
            main { model.setForeground(false); owner.clear() }
            client.dispatcher.cancelAll(); store.clear()
        }
    }
    @Test fun firstInstallOpensConfigurationAfterStoreCheck() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val store = HubStore(app, "startup-empty-${UUID.randomUUID()}.bin")
        val owner = ViewModelStore()
        lateinit var model: HubViewModel
        try {
            instrumentation.runOnMainSync { model = HubViewModel(app, store); owner.put("startup", model) }
            await { model.startup.value == HubStartup.SETTINGS }
            assertNull(model.state.value.snapshot)
        } finally { instrumentation.runOnMainSync { owner.clear() }; store.clear() }
    }
    @Test fun unavailableNetworkEntersHomeWithoutWaitingForHttp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val store = HubStore(app, "startup-offline-${UUID.randomUUID()}.bin")
        store.save(SavedHub("https://startup.example.invalid", UUID.randomUUID().toString(),
            """{"periods":{"today":{"totalTokens":123}},"devices":[]}""", 1))
        val owner = ViewModelStore()
        lateinit var model: HubViewModel
        try {
            instrumentation.runOnMainSync {
                model = HubViewModel(app, store); owner.put("startup", model)
                model.updateNetworkScope(HubNetworkScope(false, false, emptyList(), available = false))
                model.setForeground(true)
            }
            await { model.startup.value == HubStartup.HOME }
            assertEquals(R.string.error_no_network, model.state.value.error)
            assertEquals(123L, model.state.value.snapshot?.periods?.get("today")?.tokens)
        } finally { instrumentation.runOnMainSync { owner.clear() }; store.clear() }
    }
}
