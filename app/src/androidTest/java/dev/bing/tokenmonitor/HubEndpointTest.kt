package dev.bing.tokenmonitor

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class HubEndpointTest {
    @Test fun unavailableAlternatePreservesSameHubSnapshotAndCachedDetails() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val store = HubStore(app, "test-endpoint-${UUID.randomUUID()}.bin")
        val address = "http://192.168.1.1"
        val alternate = "https://hub.example.invalid"
        val credential = UUID.randomUUID().toString()
        store.save(SavedHub(address, credential, """{"periods":{"today":{"totalTokens":123}},"devices":[]}""", 1,
            alternate, """{"daily":[{"date":"2026-10-09","tokens":123}]}""", """{"subscriptions":[]}"""))
        val client = HubViewModel.defaultClient().newBuilder().addInterceptor { throw IOException("Synthetic unavailable endpoint") }.build()
        lateinit var model: HubViewModel
        instrumentation.runOnMainSync { model = HubViewModel(app, store, client) }
        fun waitUntilReady() {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (model.state.value.loading && System.nanoTime() < deadline) Thread.sleep(10)
            assertFalse(model.state.value.loading)
        }
        try {
            waitUntilReady()
            assertEquals("http", model.endpointType())
            assertFalse(model.endpointsAssociated())
            val saved = store.load()!!
            instrumentation.runOnMainSync { model.setForeground(true); model.switchEndpoint() }
            waitUntilReady()
            assertNotNull(model.state.value.connectionFailure)
            assertEquals("http", model.endpointType()) // Failed manual candidate never replaces the authority.
            assertEquals(123L, model.state.value.snapshot?.periods?.get("today")?.tokens)
            assertEquals(123L, model.state.value.history?.daily?.first()?.tokens)
            assertEquals(saved, store.load())
            assertFalse(model.state.value.live)
            val home = fixtureLocalScope()
            instrumentation.runOnMainSync { model.updateNetworkScope(home) }
            waitUntilReady()
            instrumentation.runOnMainSync { model.switchEndpoint() }
            waitUntilReady()
            assertEquals("http", model.endpointType())
            assertEquals(saved, store.load())
            instrumentation.runOnMainSync {
                model.updateNetworkScope(home.copy(networkId = 2L,localTransport = false))
                model.setForeground(false); model.setForeground(true)
            }
            waitUntilReady()
            assertEquals("http", model.endpointType())
            assertEquals(saved, store.load())
            assertEquals(123L, model.state.value.history?.daily?.first()?.tokens)
        } finally {
            instrumentation.runOnMainSync { model.setForeground(false) }
            client.dispatcher.cancelAll()
            store.clear()
        }
    }
}
