package dev.bing.tokenmonitor

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Real screen-off/lock lifecycle on an unsecured disposable emulator; production networking. */
@SdkSuppress(minSdkVersion = 28)
class LiveLockTest {
    @get:Rule val activity = ActivityScenarioRule(MainActivity::class.java)

    @Test fun actualScreenLockStopsRealHubAndWakeRestoresCachedConnection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val port = InstrumentationRegistry.getArguments().getString("liveHubPort")?.toIntOrNull() ?: error("Bridge port required")
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assertFalse("Lock check requires an unsecured disposable emulator", keyguard.isKeyguardSecure)
        assertTrue("Lock check requires the emulator screen initially awake", power.isInteractive)
        val store = HubStore(context)
        assertNull("Lock check requires an empty emulator app profile", store.load())
        lateinit var model: HubViewModel
        activity.scenario.onActivity { model = ViewModelProvider(it)[HubViewModel::class.java] }
        fun await(condition: () -> Boolean) {
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(25)
            while (!condition() && System.nanoTime() < until) Thread.sleep(20)
            assertTrue("Screen lifecycle condition unavailable; private diagnostics omitted", condition())
        }
        fun focused() = if (activity.scenario.state != Lifecycle.State.RESUMED) false else {
            var value = false
            activity.scenario.onActivity { value = it.hasWindowFocus() }
            value
        }
        fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes(); Unit }
        fun wake() {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
        }
        fun stage(value: String) = instrumentation.sendStatus(2, Bundle().apply { putString("nativeLiveStage", value) })
        try {
            await { focused() && !model.state.value.loading }
            activity.scenario.onActivity { model.connect("http://10.0.2.2:$port", UUID.randomUUID().toString(), "") }
            await { model.state.value.live && store.load() != null }
            await { model.state.value.history != null && store.load()?.history != null && model.state.value.modelNames != null && store.load()?.modelNames != null && !model.state.value.loading }
            stage("sustain")
            assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN))
            await { !power.isInteractive && !model.state.value.live && activity.scenario.state != Lifecycle.State.RESUMED }
            stage("background")
            Thread.sleep(6000)
            assertFalse(power.isInteractive)
            assertFalse(model.state.value.live)
            assertNotNull("Encrypted Hub snapshot retained with screen off", store.load()?.snapshot)
            instrumentation.sendStatus(2, Bundle().apply { putBoolean("nativeLockDestroyed", activity.scenario.state == Lifecycle.State.DESTROYED) })
            wake()
            context.startActivity(Intent(Intent.ACTION_MAIN).setClass(context, MainActivity::class.java)
                .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            await {
                var focus = false
                instrumentation.runOnMainSync {
                    androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
                        ?.let { model = ViewModelProvider(it)[HubViewModel::class.java]; focus = it.hasWindowFocus() }
                }
                power.isInteractive && !keyguard.isKeyguardLocked && model.state.value.live && focus
            }
            assertNotNull(model.state.value.snapshot)
            stage("resumed")
        } finally {
            instrumentation.runOnMainSync { model.setForeground(false); model.forget() }
            wake()
            await { !model.state.value.loading }
            store.clear()
        }
    }
}
