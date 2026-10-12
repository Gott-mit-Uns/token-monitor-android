package dev.bing.tokenmonitor

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Call
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Actual emulator Wi-Fi/cellular callbacks and production client. HTTPS is deliberately unavailable. */
class LiveNetworkTest {
    @get:Rule val activity = ActivityScenarioRule(MainActivity::class.java)

    @Test fun actualNetworkChangeRequiresHttpsAndOnlyManualLocalReturn() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val port = InstrumentationRegistry.getArguments().getString("liveHubPort")?.toIntOrNull() ?: error("Bridge port required")
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val store = HubStore(context)
        assertNull("Network check requires an empty emulator app profile", store.load())
        lateinit var model: HubViewModel
        activity.scenario.onActivity { model = ViewModelProvider(it)[HubViewModel::class.java] }
        fun transport(type: Int) = connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasTransport(type) == true
        fun await(condition: () -> Boolean) {
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(25)
            while (!condition() && System.nanoTime() < until) Thread.sleep(20)
            assertTrue("Network policy condition unavailable; private diagnostics omitted", condition())
        }
        fun focused() = if (activity.scenario.state != Lifecycle.State.RESUMED) false else {
            var value = false
            activity.scenario.onActivity { value = it.hasWindowFocus() }
            value
        }
        fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes(); Unit }
        fun stage(value: String) = instrumentation.sendStatus(2, Bundle().apply { putString("nativeLiveStage", value) })
        fun cached() = assertTrue("Known Hub snapshot retained", model.state.value.snapshot != null && store.load()?.snapshot != null)
        try {
            await { focused() && !model.state.value.loading && transport(NetworkCapabilities.TRANSPORT_WIFI) }
            val initial = connectivity.activeNetwork
            activity.scenario.onActivity { model.connect("http://10.0.2.2:$port", UUID.randomUUID().toString(), "https://hub.example.invalid") }
            await { model.state.value.live && store.load() != null }
            await { model.state.value.history != null && store.load()?.history != null && model.state.value.modelNames != null && store.load()?.modelNames != null && !model.state.value.loading }
            // Read the actual call/client only; no factory, network scope or client override.
            lateinit var oldStream: Call
            lateinit var actualClient: OkHttpClient
            activity.scenario.onActivity {
                oldStream = HubViewModel::class.java.getDeclaredField("streamCall").apply { isAccessible = true }.get(model) as Call
                actualClient = HubViewModel::class.java.getDeclaredField("client").apply { isAccessible = true }.get(model) as OkHttpClient
            }
            stage("sustain")
            shell("svc wifi disable")
            await { transport(NetworkCapabilities.TRANSPORT_CELLULAR) && connectivity.activeNetwork != initial }
            await { model.endpointType() == "https" && !model.state.value.loading && !model.state.value.live && model.state.value.error != null }
            instrumentation.sendStatus(2, Bundle().apply {
                putString("nativeOldStreamProbe", "cancelled:${oldStream.isCanceled()},running:${actualClient.dispatcher.runningCalls().any { it === oldStream }},queued:${actualClient.dispatcher.queuedCalls().any { it === oldStream }}")
            })
            await { oldStream.isCanceled() && actualClient.dispatcher.runningCalls().none { it === oldStream } && actualClient.dispatcher.queuedCalls().none { it === oldStream } }
            cached()
            stage("cell_https")
            Thread.sleep(3000)
            activity.scenario.onActivity { model.switchEndpoint() }
            await { !model.state.value.loading && model.state.value.error != null }
            assertEquals("Cellular must reject explicit local HTTP selection", "https", model.endpointType())
            assertFalse(model.state.value.live)
            cached()
            stage("cell_http_blocked")
            val cellular = connectivity.activeNetwork
            shell("svc wifi enable")
            await { transport(NetworkCapabilities.TRANSPORT_WIFI) && connectivity.activeNetwork != cellular }
            await { model.endpointType() == "https" && !model.state.value.loading && model.state.value.error != null }
            cached()
            stage("wifi_secure")
            // Observe that Wi-Fi returning does not downgrade; allow the real link callbacks to settle.
            Thread.sleep(1000)
            activity.scenario.onActivity { model.switchEndpoint() }
            await { model.state.value.live && model.endpointType() == "http" }
            stage("http_reconnected")
            assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
            await { !model.state.value.live && activity.scenario.state != Lifecycle.State.RESUMED }
            stage("background")
            Thread.sleep(6000)
            context.startActivity(Intent(Intent.ACTION_MAIN).setClass(context, MainActivity::class.java)
                .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            await { focused() && model.endpointType() == "https" && !model.state.value.loading && model.state.value.error != null }
            assertFalse(model.state.value.live)
            cached()
            stage("resumed")
        } finally {
            instrumentation.runOnMainSync { model.setForeground(false); model.forget() }
            shell("svc wifi enable")
            await { !model.state.value.loading }
            store.clear()
        }
    }
}
