package dev.bing.tokenmonitor

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.res.Configuration
import android.os.LocaleList
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Two separate processes; the host revokes permission between them through the real Settings UI. */
@SdkSuppress(minSdkVersion = 37)
class LocalRevocationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun savedConnectionSurvivesExternalRevocationAndSettingsGrant() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val context = instrumentation.targetContext
        val store = HubStore(context)
        var model = ViewModelProvider(compose.activity)[HubViewModel::class.java]
        val automation = instrumentation.uiAutomation
        val service = automation.serviceInfo
        val previousFlags = service.flags
        service.flags = previousFlags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = service
        val strings = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags("zh"))
        }).resources
        fun findText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.text?.toString() == text) return node
            for (index in 0 until node.childCount) findText(node.getChild(index), text)?.let { return it }
            return null
        }
        fun settingsClick(text: String) {
            var node: AccessibilityNodeInfo? = null
            compose.waitUntil(10000) { node = findText(automation.rootInActiveWindow, text); node != null }
            automation.waitForIdle(500, 5000)
            node = findText(automation.rootInActiveWindow, text)
            val bounds = android.graphics.Rect()
            node?.getBoundsInScreen(bounds)
            assertFalse("Settings control has no visible bounds", bounds.isEmpty)
            val now = android.os.SystemClock.uptimeMillis()
            for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                val event = android.view.MotionEvent.obtain(now, android.os.SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
                try { assertTrue("System settings tap rejected", automation.injectInputEvent(event, true)) } finally { event.recycle() }
            }
        }
        val seed = args.getString("permissionPhase") == "seed"
        try {
            compose.waitUntil(10000) { !model.state.value.loading }
            if (seed) {
                assertTrue("Seed requires an empty app cache", store.load() == null)
                assertTrue("Seed requires local permission", HubLocalPermission.granted(context))
                val port = args.getString("fixturePort")?.toIntOrNull() ?: error("Synthetic fixture port required")
                require(port in 1024..65535)
                instrumentation.runOnMainSync { model.connect("http://10.0.2.2:$port", UUID.randomUUID().toString(), "") }
                compose.waitUntil(10000) { model.state.value.live && store.load() != null }
                assertEquals(123L, model.state.value.snapshot?.periods?.get("today")?.tokens)
            } else {
                assertFalse("Recovery must start with permission revoked", HubLocalPermission.granted(context))
                compose.waitUntil(5000) { model.state.value.error == R.string.error_local_permission }
                assertEquals(123L, model.state.value.snapshot?.periods?.get("today")?.tokens)
                assertFalse(model.state.value.live)
                assertNotNull(store.load())
                // The host observes zero HTTP/SSE requests during this denied interval.
                Thread.sleep(1000)
                compose.onNodeWithText(strings.getString(R.string.nav_settings)).performClick()
                compose.onNodeWithTag("hub-content").performScrollToNode(hasText(strings.getString(R.string.open_app_settings)))
                compose.onNodeWithText(strings.getString(R.string.open_app_settings)).performClick()
                compose.waitUntil(10000) { automation.rootInActiveWindow?.packageName?.toString() == "com.android.settings" }
                settingsClick("Permissions")
                settingsClick("Nearby devices")
                settingsClick("Allow")
                compose.waitUntil(10000) { HubLocalPermission.granted(context) }
                context.startActivity(Intent(Intent.ACTION_MAIN).setClass(context, MainActivity::class.java)
                    .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
                compose.waitUntil(10000) {
                    instrumentation.runOnMainSync {
                        androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
                            ?.let { model = ViewModelProvider(it)[HubViewModel::class.java] }
                    }
                    model.state.value.live
                }
                assertEquals(123L, model.state.value.snapshot?.periods?.get("today")?.tokens)
                assertNull(model.state.value.error)
            }
        } finally {
            instrumentation.runOnMainSync { model.setForeground(false); if (!seed) model.forget() }
            compose.waitUntil(10000) { !model.state.value.loading }
            service.flags = previousFlags
            automation.serviceInfo = service
        }
    }
}
