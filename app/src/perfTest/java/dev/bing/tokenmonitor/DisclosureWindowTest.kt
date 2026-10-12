package dev.bing.tokenmonitor

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * ActivityScenario's real Activity recomposer/Choreographer. No Compose test rule,
 * TestMonotonicFrameClock, LocalDesktopAnimationClock override or virtual advances.
 * ROOT runs this optional test only on the cleared owned AVD, never a live Hub.
 */
class DisclosureWindowTest {
    private class Actions : HubUiActions {
        val calls = AtomicInteger()
        override fun connect(address: String, credential: String, alternate: String) { calls.incrementAndGet() }
        override fun refreshSaved() { calls.incrementAndGet() }
        override fun switchEndpoint() { calls.incrementAndGet() }
        override fun hasAlternate() = false
        override fun endpointType() = "https"
        override fun loadHistory() { calls.incrementAndGet() }
        override fun loadModelNames() { calls.incrementAndGet() }
        override fun loadSubscriptions() { calls.incrementAndGet() }
        override fun forget() { calls.incrementAndGet() }
        override fun renameDevice(id: String, input: String) { calls.incrementAndGet() }
    }
    private data class WindowSample(val vsync: Long, val total: Long, val layout: Long, val draw: Long, val deadline: Long)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private inline fun withReportedViewIds(block: () -> Unit) {
        val before = requireNotNull(automation.serviceInfo) { "Instrumentation accessibility service must exist" }
        val previousFlags = before.flags
        try {
            before.flags = previousFlags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = before
            block()
        } finally {
            // Restore the exact flags even if setup, locating or an assertion fails.
            // Other service fields are not changed by this diagnostic fixture.
            val restore = requireNotNull(automation.serviceInfo) { "Instrumentation accessibility service must remain available" }
            restore.flags = previousFlags
            automation.serviceInfo = restore
        }
    }
    private fun writeSafeLocatorFailure() {
        runCatching {
            val allowedTags = setOf("hub-settings-toggle", "hub-content", "hub-view-selector", "view-projects",
                "view-devices", "main-display-toggle", "settings-general-toggle", "settings-language-select",
                "settings-language-menu", "project-row-public project", "project-tool-public project-0",
                "device-row-public-device")
            val rows = JSONArray()
            val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
            automation.rootInActiveWindow?.let(pending::add)
            var inspected = 0
            while (pending.isNotEmpty() && inspected < 256) {
                val candidate = pending.removeFirst(); inspected++
                val rawTag = candidate.viewIdResourceName
                val rawClass = candidate.className?.toString().orEmpty()
                val fixedClass = rawClass.takeIf { it in setOf("android.view.View", "android.widget.Button",
                    "android.widget.TextView", "android.widget.FrameLayout", "android.widget.ScrollView",
                    "androidx.compose.ui.platform.AndroidComposeView", "androidx.compose.ui.platform.ComposeView") } ?: "unlisted"
                val bounds = Rect(); candidate.getBoundsInScreen(bounds)
                // Never query or serialize node text/contentDescription, unknown
                // resource IDs, app payload, addresses, credentials or cookies.
                rows.put(JSONObject().put("testTag", rawTag?.takeIf { it in allowedTags })
                    .put("class", fixedClass).put("bounds", JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom))))
                repeat(candidate.childCount.coerceAtMost(256 - inspected)) { index -> candidate.getChild(index)?.let(pending::add) }
            }
            File(instrumentation.targetContext.getExternalFilesDir(null), "disclosure-window-locator-failure-public.json")
                .writeText(rows.toString(2))
        }
    }
    private var primaryWindowId = -1
    private fun node(tag: String): AccessibilityNodeInfo? {
        // Each native tap uses newly reported layout geometry, not the cached
        // expanded row bounds left by Android accessibility after a close.
        if (Build.VERSION.SDK_INT >= 34) automation.clearCache()
        val root = automation.rootInActiveWindow ?: return null
        // Compose exports the exact testTag as viewIdResourceName. Android's
        // resource lookup expects package:id/name and may not find these bare IDs.
        // Traverse the actual public tree, keeping the same visibility gate below.
        val matching = mutableListOf<AccessibilityNodeInfo>()
        val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var inspected = 0
        while (pending.isNotEmpty() && inspected < 1024) {
            val candidate = pending.removeFirst(); inspected++
            if (candidate.viewIdResourceName == tag) matching.add(candidate)
            repeat(candidate.childCount.coerceAtMost(1024 - inspected)) { index ->
                candidate.getChild(index)?.let(pending::add)
            }
        }
        // Popup is a separate semantic owner. Its inherited public text remains
        // selectable even when the outer diagnostic testTagsAsResourceId does not
        // propagate across that Window; no production test switch is required.
        val publicLabel = when (tag) {
            "view-projects" -> "Projects"
            "view-devices" -> "Devices"
            "settings-language-menu" -> if (root.windowId != primaryWindowId && primaryWindowId >= 0) "English" else null
            else -> null
        }
        // Popup's separate semantics owner cannot inherit the host ID flag.
        // Only this cleared public fixture's three fixed labels are compared;
        // labels/text/descriptions are never emitted to diagnostics or reports.
        val choices = if (matching.isNotEmpty() || publicLabel == null) matching else {
            val fixedMatches = mutableListOf<AccessibilityNodeInfo>()
            val popupNodes = java.util.ArrayDeque<AccessibilityNodeInfo>()
            popupNodes.add(root)
            var popupInspected = 0
            while (popupNodes.isNotEmpty() && popupInspected < 1024) {
                val candidate = popupNodes.removeFirst(); popupInspected++
                if (candidate.text?.toString() == publicLabel || candidate.contentDescription?.toString() == publicLabel)
                    fixedMatches.add(candidate)
                repeat(candidate.childCount.coerceAtMost(1024 - popupInspected)) { index ->
                    candidate.getChild(index)?.let(popupNodes::add)
                }
            }
            fixedMatches
        }
        return choices.firstOrNull { candidate ->
            val bounds = Rect(); candidate.getBoundsInScreen(bounds)
            candidate.isVisibleToUser && !bounds.isEmpty
        }
    }
    private fun waitUntil(message: String, timeoutMs: Long = 5000, condition: () -> Boolean) {
        val expires = SystemClock.uptimeMillis() + timeoutMs
        while (!condition() && SystemClock.uptimeMillis() < expires) SystemClock.sleep(8)
        val satisfied = condition()
        if (!satisfied) writeSafeLocatorFailure()
        assertTrue(message, satisfied)
    }
    private fun find(tag: String, scroll: Boolean = false): AccessibilityNodeInfo {
        repeat(if (scroll) 24 else 1) {
            node(tag)?.let { return it }
            if (scroll) {
                node("hub-content")?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                SystemClock.sleep(80)
            }
        }
        waitUntil("Public tagged control must be visible: $tag") { node(tag) != null }
        return node(tag)!!
    }
    private fun tap(tag: String, scroll: Boolean = false): Long {
        val bounds = Rect(); find(tag, scroll).getBoundsInScreen(bounds)
        val down = SystemClock.uptimeMillis(); val began = System.nanoTime()
        val first = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY(), 0)
        val last = MotionEvent.obtain(down, down + 8, MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY(), 0)
        try {
            assertTrue(automation.injectInputEvent(first, true))
            assertTrue(automation.injectInputEvent(last, true))
        } finally { first.recycle(); last.recycle() }
        return began
    }
    private fun publicState(): HubState {
        val snapshot = HubParser.parse("""{"updatedAt":"2026-10-11T08:00:00Z","periods":{"today":{"totalTokens":1000,"costUsd":1,"clients":{"codex":600,"claude":400},"projects":{"public":{"label":"Public project","tokens":1000,"costUsd":1,"clients":{"codex":600,"claude":400}}}},"month":{"totalTokens":1000},"allTime":{"totalTokens":1000}},"devices":[{"deviceId":"public-device","displayName":"Public device","platform":"linux","updatedAt":"2026-10-11T08:00:00Z","periods":{"today":{"totalTokens":1000,"clients":{"codex":600,"claude":400}}}}]}""")
        return HubState(snapshot = snapshot, history = HubHistory(emptyList(), emptyList()),
            modelNames = ModelNames(emptyMap(), "off"), subscriptions = emptyList())
    }

    @Test fun actualHubPanelSectionsRowsReductionAndStopUseRealWindowFrames() = withReportedViewIds {
        val target = instrumentation.targetContext
        val original = publicState()
        val preferences = mutableStateOf(DisplayPreferences(theme = ThemeChoice.DARK, language = LanguageChoice.ENGLISH,
            appearance = AppearancePreferences(reduceMotion = "off"), homeModules = emptyList()))
        val actions = Actions()
        val samples = Collections.synchronizedList(mutableListOf<DesktopDisclosureObservation>())
        val metrics = Collections.synchronizedList(mutableListOf<WindowSample>())
        val observationOverflow = AtomicInteger(); val metricsOverflow = AtomicInteger(); val dropped = AtomicInteger()
        val thread = HandlerThread("public-disclosure-window").apply { start() }
        val handler = Handler(thread.looper)
        val listener = Window.OnFrameMetricsAvailableListener { _, frame, lost ->
            synchronized(metrics) {
                if (metrics.size < 4096) metrics.add(WindowSample(frame.getMetric(FrameMetrics.VSYNC_TIMESTAMP),
                    frame.getMetric(FrameMetrics.TOTAL_DURATION), frame.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),
                    frame.getMetric(FrameMetrics.DRAW_DURATION), if (Build.VERSION.SDK_INT >= 31) frame.getMetric(FrameMetrics.DEADLINE) else 0))
                else metricsOverflow.incrementAndGet()
            }
            dropped.addAndGet(lost)
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val runs = JSONArray()
        var windowWidth = 0; var windowHeight = 0
        val stopEventAt = AtomicLong(0)
        var stopReturnedAt = 0L
        val stopObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) stopEventAt.set(System.nanoTime())
        }
        fun observations() = synchronized(samples) { samples.toList() }
        fun measure(kind: DesktopDisclosureKind, open: Boolean, control: String, scroll: Boolean = false,
            animated: Boolean = true) {
            val began = tap(control, scroll)
            waitUntil("The finite real-time transition must finish", 5000) {
                observations().any { it.atNanos >= began && it.kind == kind && it.phase == DesktopDisclosureObservationPhase.STATE &&
                    it.open == open && !it.pose.running && it.pose.heightFraction == (if (open) 1f else 0f) }
            }
            val end = System.nanoTime()
            val collected = observations().filter { it.atNanos in began..end && it.kind == kind }
            val draws = collected.filter { it.phase == DesktopDisclosureObservationPhase.DRAW }
            val intermediate = draws.filter { it.pose.heightFraction > 0f && it.pose.heightFraction < 1f }
            if (animated) assertTrue("An actual Window draw must contain an intermediate pose", intermediate.isNotEmpty())
            else assertTrue("Reduced motion must have no intermediate draw", intermediate.isEmpty())
            assertTrue("Close/STOP must not expose old actions", collected.filter { !it.open }.all { !it.acceptsInput })
            assertTrue("Each retained display list is bounded by the actual window surface", draws.all {
                it.cacheWidthPx <= windowWidth + 1 && it.cacheHeightPx <= windowHeight + 1 })
            val first = intermediate.firstOrNull()
            val final = collected.firstOrNull { it.phase == DesktopDisclosureObservationPhase.STATE && !it.pose.running &&
                it.open == open && it.pose.heightFraction == (if (open) 1f else 0f) }
            val window = synchronized(metrics) { metrics.filter { it.vsync in began..end } }
            runs.put(JSONObject().put("kind", kind.name).put("open", open).put("animated", animated)
                .put("actionMs", (end - began) / 1e6).put("firstIntermediateDrawMs", first?.let { (it.atNanos - began) / 1e6 })
                .put("lastControllerMs", final?.let { (it.atNanos - began) / 1e6 }).put("intermediateDraws", intermediate.size)
                .put("windowFrames", window.size).put("maxTotalMs", window.maxOfOrNull { it.total / 1e6 })
                .put("maxDrawMs", window.maxOfOrNull { it.draw / 1e6 }).put("maxLayoutMs", window.maxOfOrNull { it.layout / 1e6 })
                .put("maxCacheWidthPx", draws.maxOfOrNull { it.cacheWidthPx }).put("maxCacheHeightPx", draws.maxOfOrNull { it.cacheHeightPx }))
        }
        try {
            scenario.onActivity { activity ->
                activity.window.addOnFrameMetricsAvailableListener(listener, handler)
                activity.setContent {
                    CompositionLocalProvider(LocalHubCalendarClock provides Clock.fixed(Instant.parse("2026-10-11T08:00:00Z"), ZoneId.of("Asia/Shanghai")),
                        LocalDesktopDisclosureObserver provides { observed -> synchronized(samples) {
                            if (samples.size < 8192) samples.add(observed) else observationOverflow.incrementAndGet()
                        } }) {
                        TokenTheme(true, preferences.value.language, preferences.value.appearance) {
                            Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                                HubScreen(original, actions, preferences = preferences.value, updateDisplay = { preferences.value = it.apply(preferences.value) })
                            }
                        }
                    }
                }
            }
            waitUntil("The real public Home must be ready") { node("hub-settings-toggle") != null }
            primaryWindowId = node("hub-settings-toggle")!!.windowId
            scenario.onActivity { windowWidth = it.window.decorView.width; windowHeight = it.window.decorView.height }
            SystemClock.sleep(1200) // Let the unrelated cold headline finish before recording disclosure actions.
            repeat(5) {
                measure(DesktopDisclosureKind.SETTINGS_PANEL, true, "hub-settings-toggle")
                measure(DesktopDisclosureKind.SETTINGS_SECTION, true, "main-display-toggle")
                measure(DesktopDisclosureKind.SETTINGS_SECTION, false, "main-display-toggle")
                measure(DesktopDisclosureKind.SETTINGS_PANEL, false, "hub-settings-toggle")
            }
            tap("hub-view-selector"); tap("view-projects")
            repeat(5) {
                measure(DesktopDisclosureKind.PROJECT_DETAILS, true, "project-row-public project", scroll = true)
                measure(DesktopDisclosureKind.PROJECT_DETAILS, false, "project-row-public project")
                waitUntil("Closing removes the real source-detail semantics now") { node("project-tool-public project-0") == null }
            }
            tap("hub-view-selector"); tap("view-devices")
            repeat(5) {
                measure(DesktopDisclosureKind.DEVICE_DETAILS, true, "device-row-public-device", scroll = true)
                measure(DesktopDisclosureKind.DEVICE_DETAILS, false, "device-row-public-device")
            }
            // Real app preference, not a test-local scale. ROOT additionally runs
            // explicit off with actual system animator scale 0 and 1 separately.
            scenario.onActivity { preferences.value = preferences.value.copy(appearance = preferences.value.appearance.copy(reduceMotion = "on")) }
            measure(DesktopDisclosureKind.SETTINGS_PANEL, true, "hub-settings-toggle", animated = false)
            measure(DesktopDisclosureKind.SETTINGS_SECTION, true, "settings-general-toggle", animated = false)
            // Open the actual native child popup, then STOP: it must be disposed
            // immediately along with active subtree input; ordinary choices persist.
            tap("settings-language-select")
            waitUntil("Public language child window opens") { node("settings-language-menu") != null }
            // Downward lifecycle dispatch is reverse registration order. Install
            // this marker last, after real UI effects, to observe ON_STOP first.
            scenario.onActivity { it.lifecycle.addObserver(stopObserver) }
            scenario.moveToState(Lifecycle.State.CREATED)
            stopReturnedAt = System.nanoTime()
            assertTrue("The real lifecycle must have dispatched ON_STOP", stopEventAt.get() > 0)
            waitUntil("STOP releases all recorded viewport layers") {
                val active = mutableSetOf<Int>()
                observations().forEach { sample ->
                    if (sample.phase == DesktopDisclosureObservationPhase.DRAW && sample.cacheWidthPx > 0 && sample.cacheHeightPx > 0) active.add(sample.identity)
                    if (sample.phase == DesktopDisclosureObservationPhase.RELEASE) active.remove(sample.identity)
                }
                active.isEmpty()
            }
            // moveToState's request also includes ON_PAUSE, where STARTED input
            // is still valid. Check completed ON_STOP dispatch, not request time.
            assertTrue("Completed STOP must publish a blocked controller state", observations().any {
                it.atNanos >= stopEventAt.get() && it.phase == DesktopDisclosureObservationPhase.STATE && !it.acceptsInput })
            assertTrue("No actions remain after completed STOP", observations().filter { it.atNanos >= stopReturnedAt }.none { it.acceptsInput })
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitUntil("Settings return retains the real ordinary choice") { node("settings-language-select") != null }
            assertNull("STOP does not replay the old child Popup", node("settings-language-menu"))
            assertEquals(LanguageChoice.ENGLISH, preferences.value.language)
            assertEquals(0, actions.calls.get())
            val today = original.snapshot!!.periods.getValue("today")
            assertEquals(1000L, today.tokens)
            assertEquals(mapOf("codex" to 600L, "claude" to 400L), today.tools)
            assertEquals(1, original.snapshot.devices.size)
            val barrier = CountDownLatch(1); handler.post { barrier.countDown() }
            assertTrue(barrier.await(5, TimeUnit.SECONDS))
            assertEquals(0, observationOverflow.get()); assertEquals(0, metricsOverflow.get())
            val hash = MessageDigest.getInstance("SHA-256").digest(File(target.applicationInfo.sourceDir).readBytes()).joinToString("") { "%02x".format(it) }
            val activeLayers = mutableSetOf<Int>()
            var maximumRetainedLayers = 0
            observations().forEach { sample ->
                if (sample.phase == DesktopDisclosureObservationPhase.DRAW && sample.cacheWidthPx > 0 && sample.cacheHeightPx > 0) activeLayers.add(sample.identity)
                if (sample.phase == DesktopDisclosureObservationPhase.RELEASE) activeLayers.remove(sample.identity)
                maximumRetainedLayers = maxOf(maximumRetainedLayers, activeLayers.size)
            }
            val report = JSONObject().put("apkSha256", hash).put("clock", "production System.nanoTime and real Activity Choreographer")
                .put("composeTestClockUsed", false).put("windowWidthPx", windowWidth).put("windowHeightPx", windowHeight)
                .put("actualAnimatorDurationScale", android.provider.Settings.Global.getFloat(target.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
                .put("stopEventAtNanos", stopEventAt.get()).put("stopDispatchReturnedAtNanos", stopReturnedAt)
                .put("networkActionCalls", actions.calls.get()).put("metricsCallbacksDropped", dropped.get()).put("metricsComplete", dropped.get() == 0)
                .put("observations", samples.size).put("maximumRetainedLayersObserved", maximumRetainedLayers).put("retainedLayersAfterResume", activeLayers.size).put("businessTokens", today.tokens).put("businessDevices", original.snapshot.devices.size).put("runs", runs).put("scope", "Public real HubScreen Window disclosure actions on owned emulator; no physical presentation FPS claim")
            File(target.getExternalFilesDir(null), "disclosure-window-public.json").writeText(report.toString(2))
        } finally {
            File(target.getExternalFilesDir(null), "disclosure-window-completed-runs-public.json")
                .writeText(JSONObject().put("completedRuns", runs)
                    .put("stopEventAtNanos", stopEventAt.get()).put("stopDispatchReturnedAtNanos", stopReturnedAt)
                    .put("postStopSamples", JSONArray(observations().filter {stopEventAt.get()>0 && it.atNanos>=stopEventAt.get()}.map {
                        JSONObject().put("kind",it.kind.name).put("phase",it.phase.name).put("atNanos",it.atNanos)
                            .put("acceptsInput",it.acceptsInput).put("open",it.open).put("running",it.pose.running)
                    })).put("scope", "Partial completed actions only; final whole-test result is separate").toString(2))
            scenario.onActivity { it.lifecycle.removeObserver(stopObserver); it.window.removeOnFrameMetricsAvailableListener(listener) }
            scenario.close(); thread.quitSafely(); thread.join(5000)
        }
    }
}
