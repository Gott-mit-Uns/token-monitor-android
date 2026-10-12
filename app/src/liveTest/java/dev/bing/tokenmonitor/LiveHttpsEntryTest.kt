package dev.bing.tokenmonitor

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Real off-site HTTPS entry (Lucky reverse proxy). Uses a synthetic credential on purpose:
 * reaching the hub's 401 proves the public certificate chain and hostname were accepted by the
 * system trust store, while a certificate or hostname failure can only surface as a transport
 * error. No credential, title or usage value is read or recorded.
 */
@RunWith(AndroidJUnit4::class)
class LiveHttpsEntryTest {
    @get:Rule val activity = ActivityScenarioRule(MainActivity::class.java)

    @Test fun realPublicHttpsOriginUsesSystemTrustAndRefusesPlaintextFallback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val url = arguments.getString("httpsEntryUrl") ?: error("Public HTTPS entry required")
        require(url.startsWith("https://"))
        val app = instrumentation.targetContext.applicationContext as Application
        val store = HubStore(app)
        assertNull("The real HTTPS check requires an empty emulator app profile", store.load())
        lateinit var model: HubViewModel
        activity.scenario.onActivity { model = ViewModelProvider(it)[HubViewModel::class.java] }

        fun await(seconds: Long = 30, condition: () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
            while (!condition() && System.nanoTime() < deadline) Thread.sleep(20)
            assertTrue("Public HTTPS entry condition not reached", condition())
        }
        try {
            await { !model.state.value.loading }
            activity.scenario.onActivity { model.connect(url, UUID.randomUUID().toString(), "") }
            await { model.state.value.error != null }
            // A rejected certificate or hostname would report a transport error here.
            assertEquals("The real public certificate must be trusted", R.string.error_auth, model.state.value.error)
            assertEquals("The selected entry stays HTTPS", "https", model.endpointType())
            assertFalse("An authorization failure never starts a live stream", model.state.value.live)
            assertFalse("No alternate entry may be silently used instead", model.hasAlternate())
            Thread.sleep(3000)
            assertEquals("Authorization failure keeps the stopped state", R.string.error_auth, model.state.value.error)
            assertFalse(model.state.value.live)
        } finally {
            instrumentation.runOnMainSync { model.setForeground(false); model.forget() }
            store.clear()
        }
    }
}
