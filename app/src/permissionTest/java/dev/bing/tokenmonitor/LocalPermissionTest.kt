package dev.bing.tokenmonitor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
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
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Opt-in, fresh API37 app profile. Actual system permission dialog; synthetic Hub only. */
@SdkSuppress(minSdkVersion = 37)
class LocalPermissionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun denialStopsHttpAndSystemGrantRestoresForegroundOnlyConnection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val permanentDenial = InstrumentationRegistry.getArguments().getString("permanentDenial") == "true"
        val context = instrumentation.targetContext
        val store = HubStore(context)
        assertTrue("Permission test requires an empty app profile", store.load() == null)
        assertFalse("Permission test must start without the local permission", HubLocalPermission.granted(context))
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val address = connectivity.getLinkProperties(connectivity.activeNetwork)?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address }?.address?.hostAddress
            ?: error("Permission test emulator requires local IPv4")
        val generated = UUID.randomUUID().toString()
        val server = ServerSocket(0)
        val sockets = ConcurrentLinkedQueue<Socket>()
        val workers = ConcurrentLinkedQueue<Thread>()
        val requests = AtomicInteger()
        val streams = AtomicInteger()
        val wrongHeaders = AtomicInteger()
        val closed = CountDownLatch(1)
        val body = """{"periods":{"today":{"totalTokens":123}},"devices":[]}"""
        val accepting = thread(name = "permission-fixture") {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                sockets += socket
                workers += thread {
                    try { socket.use {
                        socket.soTimeout = 30000
                        val input = socket.getInputStream().bufferedReader()
                        val path = input.readLine().orEmpty()
                        var authorized = false
                        while (true) {
                            val header = input.readLine() ?: break
                            if (header.isEmpty()) break
                            if (header.startsWith("Authorization:", true)) authorized = header.substringAfter(':').trim() == "Bearer $generated"
                        }
                        requests.incrementAndGet()
                        if (!path.contains("/api/health") && !authorized) wrongHeaders.incrementAndGet()
                        val output = socket.getOutputStream()
                        if (path.contains("/api/stats/stream")) {
                            val number = streams.incrementAndGet()
                            output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\nevent: snapshot\ndata: {\"stats\":$body}\n\n").toByteArray())
                            output.flush()
                            val ended = try { input.read() == -1 } catch (_: java.net.SocketException) { true }
                            if (ended && number == 1) closed.countDown()
                        } else {
                            val response = if (path.contains("/api/health")) """{"ok":true,"role":"hub"}""" else body
                            output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${response.toByteArray().size}\r\nConnection: close\r\n\r\n$response").toByteArray())
                            output.flush()
                        }
                    } } catch (_: java.io.IOException) { /* Synthetic socket cancellation; no headers or bodies logged. */ }
                }
            }
        }
        val automation = instrumentation.uiAutomation
        val service = automation.serviceInfo
        val previousFlags = service.flags
        service.flags = previousFlags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = service
        var model = ViewModelProvider(compose.activity)[HubViewModel::class.java]
        fun await(condition: () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (!condition() && System.nanoTime() < deadline) Thread.sleep(20)
            assertTrue("Permission flow condition not reached", condition())
        }
        fun find(node: AccessibilityNodeInfo?, names: Set<String>): AccessibilityNodeInfo? {
            if (node == null) return null
            if (names.any { node.viewIdResourceName?.endsWith(":id/$it") == true }) return node
            for (index in 0 until node.childCount) find(node.getChild(index), names)?.let { return it }
            return null
        }
        fun systemClick(names: Set<String>) {
            var button: AccessibilityNodeInfo? = null
            await { button = find(automation.rootInActiveWindow, names); button != null }
            assertTrue("System permission button did not accept click", button!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
        fun findText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.text?.toString() == text) return node
            for (index in 0 until node.childCount) findText(node.getChild(index), text)?.let { return it }
            return null
        }
        fun settingsClick(text: String) {
            var node: AccessibilityNodeInfo? = null
            await { node = findText(automation.rootInActiveWindow, text); node != null }
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
        try {
            compose.waitUntil(10000) { !model.state.value.loading }
            val configuration = Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags("zh")) }
            val strings = context.createConfigurationContext(configuration).resources
            // Connection keyboard/layout has its own test; keep this check focused on system permission transitions.
            instrumentation.runOnMainSync { model.connect("http://$address:${server.localPort}", generated, "") }
            runCatching { compose.waitUntil(5000) { model.state.value.error == R.string.error_local_permission } }.onFailure {
                fail("Expected local permission error; actual resource=${model.state.value.error?.let { context.resources.getResourceEntryName(it) }}, loading=${model.state.value.loading}")
            }
            assertEquals(0, requests.get())
            assertFalse(model.state.value.live)
            val permissionLabel = strings.getString(R.string.allow_local_access)
            compose.onNodeWithTag("hub-content").performScrollToNode(hasText(permissionLabel))
            compose.onNodeWithText(permissionLabel).performClick()
            systemClick(setOf("permission_deny_button"))
            await { automation.rootInActiveWindow?.packageName?.toString() == context.packageName }
            assertFalse(HubLocalPermission.granted(context))
            assertEquals(0, requests.get())
            compose.onNodeWithText(permissionLabel).performScrollTo().performClick()
            if (permanentDenial) {
                systemClick(setOf("permission_deny_button", "permission_deny_and_dont_ask_again_button"))
                await { automation.rootInActiveWindow?.packageName?.toString() == context.packageName }
                assertFalse(compose.activity.shouldShowRequestPermissionRationale(HubLocalPermission.NAME))
                compose.onNodeWithText(permissionLabel).performScrollTo().performClick()
                Thread.sleep(300)
                assertEquals(context.packageName, automation.rootInActiveWindow?.packageName?.toString())
                assertFalse(HubLocalPermission.granted(context))
                assertEquals(0, requests.get())
                compose.onNodeWithText(strings.getString(R.string.open_app_settings)).performScrollTo().performClick()
                await { automation.rootInActiveWindow?.packageName?.toString() == "com.android.settings" }
                // API37 emulator system language is English. System group names are not used by production code.
                settingsClick("Permissions")
                settingsClick("Nearby devices")
                settingsClick("Allow")
                await { HubLocalPermission.granted(context) }
                assertEquals(0, requests.get())
                context.startActivity(Intent(Intent.ACTION_MAIN).setClass(context, MainActivity::class.java)
                    .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
                await {
                    var focused = false
                    instrumentation.runOnMainSync {
                        androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
                            ?.let { model = ViewModelProvider(it)[HubViewModel::class.java]; focused = it.hasWindowFocus() }
                    }
                    focused && automation.rootInActiveWindow?.packageName?.toString() == context.packageName
                }
                compose.waitUntil(10000) { !model.state.value.loading }
            } else systemClick(setOf("permission_allow_button", "permission_allow_foreground_only_button"))
            await { HubLocalPermission.granted(context) }
            compose.onNodeWithText(permissionLabel).assertDoesNotExist()
            instrumentation.runOnMainSync { model.connect("http://$address:${server.localPort}", generated, "") }
            compose.waitUntil(10000) { model.state.value.live }
            assertEquals(123L, model.state.value.snapshot?.periods?.get("today")?.tokens)
            assertTrue(requests.get() >= 3)
            assertEquals(0, wrongHeaders.get())
            assertTrue(automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
            assertTrue("Background must close the actual API37 stream", closed.await(10, TimeUnit.SECONDS))
            await { !model.state.value.live }
            val stopped = requests.get()
            Thread.sleep(300)
            assertEquals(stopped, requests.get())
            context.startActivity(Intent(Intent.ACTION_MAIN).setClass(context, MainActivity::class.java)
                .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            await {
                instrumentation.runOnMainSync {
                    androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
                        ?.let { model = ViewModelProvider(it)[HubViewModel::class.java] }
                }
                model.state.value.live && streams.get() >= 2
            }
            assertEquals(0, wrongHeaders.get())
        } finally {
            instrumentation.runOnMainSync { model.setForeground(false); model.forget() }
            await { !model.state.value.loading }
            server.close(); sockets.forEach { runCatching { it.close() } }
            accepting.join(1000); workers.forEach { it.join(1000) }
            store.clear()
            service.flags = previousFlags
            automation.serviceInfo = service
        }
    }
}
