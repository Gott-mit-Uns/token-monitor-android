package dev.bing.tokenmonitor

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.InputDevice
import android.view.MotionEvent
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.Lifecycle
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
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * Same optional perfTest real ActivityScenario/FrameMetrics method as DisclosureWindowTest.
 * No Compose rule, test frame clock, duration override, fake draw or virtual advance.
 * Public fixture only. A pass concerns this Window/device run, never physical-phone FPS.
 */
class HomeHistoryEntryWindowTest {
    private class Actions : HubUiActions {
        val calls = AtomicInteger()
        override fun connect(address: String, credential: String, alternate: String) { calls.incrementAndGet() }
        override fun refreshSaved() { calls.incrementAndGet() }
        override fun switchEndpoint() { calls.incrementAndGet() }
        override fun hasAlternate() = false
        override fun endpointType() = "https"
        override fun loadHistory() { calls.incrementAndGet() }
        override fun loadDeviceHistory() { calls.incrementAndGet() }
        override fun loadModelNames() { calls.incrementAndGet() }
        override fun loadSubscriptions() { calls.incrementAndGet() }
        override fun forget() { calls.incrementAndGet() }
        override fun renameDevice(id: String, input: String) { calls.incrementAndGet() }
    }
    private data class WindowSample(val vsync: Long, val total: Long, val layout: Long, val draw: Long, val deadline: Long)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val fixtureSavedAt = LocalDate.of(2026, 10, 11).atTime(12, 0)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private inline fun withReportedViewIds(block: () -> Unit) {
        val before = requireNotNull(automation.serviceInfo) { "Instrumentation accessibility service must exist" }
        val previousFlags = before.flags
        try {
            before.flags = previousFlags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = before
            block()
        } finally {
            // Includes setup/ActivityScenario.launch failures, not only test-body failures.
            val restore = requireNotNull(automation.serviceInfo) { "Instrumentation accessibility service must remain available" }
            restore.flags = previousFlags
            automation.serviceInfo = restore
        }
    }
    private var primaryWindowId = -1
    private var hostView: android.view.View? = null
    private val nativeTouchRecords=Collections.synchronizedList(mutableListOf<JSONObject>())
    private val nativeTouchOverflow=AtomicInteger()
    private var basicObtainProbe:JSONObject?=null
    private fun touchRecords()=JSONArray(synchronized(nativeTouchRecords) {nativeTouchRecords.toList()})
    private val selectorReadyRecords=mutableListOf<JSONObject>()
    private var selectorReadySequence=0
    private var selectorReadyOverflow=0
    private fun selectorReadySamples()=JSONArray(selectorReadyRecords.toList())
    private fun visibleScreenBounds():Rect {
        val result=Rect()
        instrumentation.runOnMainSync {
            val view=hostView ?: return@runOnMainSync
            if(!view.isAttachedToWindow) return@runOnMainSync
            view.getWindowVisibleDisplayFrame(result)
            val screen=if(Build.VERSION.SDK_INT>=30) Rect(requireNotNull(view.context.getSystemService(WindowManager::class.java)).maximumWindowMetrics.bounds)
                else Rect(0,0,view.resources.displayMetrics.widthPixels,view.resources.displayMetrics.heightPixels)
            if(!result.intersect(screen)) result.setEmpty()
        }
        return result
    }
    private fun nextNativeVsync(timeoutMs:Long):Long {
        val ready=CountDownLatch(1);val at=AtomicLong()
        lateinit var choreographer:Choreographer
        val callback=Choreographer.FrameCallback {time->at.set(time);ready.countDown()}
        instrumentation.runOnMainSync {choreographer=Choreographer.getInstance();choreographer.postFrameCallback(callback)}
        try {assertTrue("A real Choreographer callback must precede selector sampling",ready.await(timeoutMs.coerceAtLeast(1),TimeUnit.MILLISECONDS))}
        finally {instrumentation.runOnMainSync {choreographer.removeFrameCallback(callback)}}
        return at.get()
    }
    private fun stableSelectorBounds():Rect {
        val expires=SystemClock.uptimeMillis()+5000
        val sequence=++selectorReadySequence
        var previous:Rect?=null;var previousVisible:Rect?=null;var previousWindow=-1;var previousVsync=0L
        while(SystemClock.uptimeMillis()<expires) {
            val vsync=nextNativeVsync(expires-SystemClock.uptimeMillis())
            val selector=node("hub-view-selector")
            val bounds=Rect().also {selector?.getBoundsInScreen(it)}
            val visible=visibleScreenBounds()
            val attached=hostView?.isAttachedToWindow==true;val focused=hostView?.hasWindowFocus()==true
            val enabled=selector?.isEnabled==true
            val valid=attached && focused && enabled && !bounds.isEmpty && !visible.isEmpty && visible.contains(bounds)
            val record=JSONObject().put("sequence",sequence).put("vsyncNanos",vsync).put("sampleNanos",System.nanoTime())
                .put("sampleUptimeMs",SystemClock.uptimeMillis()).put("windowId",selector?.windowId ?: -1)
                .put("attached",if(attached) 1 else 0).put("focused",if(focused) 1 else 0).put("enabled",if(enabled) 1 else 0)
                .put("fullyOnscreen",if(valid) 1 else 0).put("bounds",JSONArray(listOf(bounds.left,bounds.top,bounds.right,bounds.bottom)))
                .put("visibleScreen",JSONArray(listOf(visible.left,visible.top,visible.right,visible.bottom)))
            if(selectorReadyRecords.size<512) selectorReadyRecords.add(record) else selectorReadyOverflow++
            val stable=valid && previous==bounds && previousVisible==visible && previousWindow==selector!!.windowId && vsync>previousVsync
            if(stable) {record.put("selected",1);return Rect(bounds)}
            record.put("selected",0)
            previous=if(valid) Rect(bounds) else null;previousVisible=if(valid) Rect(visible) else null
            previousWindow=selector?.windowId ?: -1;previousVsync=vsync
        }
        saveKnownControlWindows()
        throw AssertionError("The complete selector must have stable onscreen coordinates across two real vsync samples")
    }
    private fun saveKnownControlWindows() {
        val rows=JSONArray()
        val packageName=instrumentation.targetContext.packageName
        automation.windows.take(16).filter {it.type==AccessibilityWindowInfo.TYPE_APPLICATION}.forEach {window ->
            val root=window.root?.takeIf {it.packageName?.toString()==packageName} ?: return@forEach
            val controls=JSONArray()
            boundedNodes(root) { candidate ->
                val tag=candidate.viewIdResourceName
                val known=when {
                    tag in setOf("hub-view-selector","hub-view-menu","home-trend-chart","view-home","view-tools") -> tag
                    root.windowId!=primaryWindowId && candidate.text?.toString()=="Home" -> "fixed-home-label"
                    root.windowId!=primaryWindowId && candidate.text?.toString()=="Tools" -> "fixed-tools-label"
                    else -> null
                }
                if(known!=null) {
                    val bounds=Rect();candidate.getBoundsInScreen(bounds)
                    controls.put(JSONObject().put("control",known).put("visible",candidate.isVisibleToUser)
                        .put("enabled",candidate.isEnabled).put("clickable",candidate.isClickable)
                        .put("selectorExpanded",if(Build.VERSION.SDK_INT>=30 && tag=="hub-view-selector") candidate.stateDescription?.toString()=="Collapse details" else JSONObject.NULL)
                        .put("bounds",JSONArray(listOf(bounds.left,bounds.top,bounds.right,bounds.bottom))))
                }
            }
            rows.put(JSONObject().put("id",window.id).put("rootId",root.windowId).put("active",window.isActive)
                .put("focused",window.isFocused).put("controls",controls))
        }
        val report=JSONObject().put("primaryWindowId",primaryWindowId).put("hostHasWindowFocus",hostView?.hasWindowFocus())
            .put("hostAttached",hostView?.isAttachedToWindow).put("windows",rows)
            .put("basicObtainProbe",basicObtainProbe ?: JSONObject.NULL)
            .put("nativeTouchRecords",touchRecords()).put("nativeTouchOverflow",nativeTouchOverflow.get())
            .put("selectorReadySamples",selectorReadySamples()).put("selectorReadyOverflow",selectorReadyOverflow)
        File(instrumentation.targetContext.getExternalFilesDir(null),"home-history-window-controls-public.json").writeText(report.toString(2))
    }
    private fun boundedNodes(root: AccessibilityNodeInfo, visit: (AccessibilityNodeInfo) -> Unit) {
        val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var enqueued = 1
        var inspected = 0
        while (pending.isNotEmpty() && inspected < 1024) {
            val candidate = pending.removeFirst(); inspected++
            visit(candidate)
            // Bound both visited nodes and retained queue, including broad trees.
            repeat(candidate.childCount.coerceAtMost(1024 - enqueued)) { index ->
                candidate.getChild(index)?.let { pending.add(it); enqueued++ }
            }
        }
    }
    private fun node(tag: String): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= 34) automation.clearCache()
        val targetPackage=instrumentation.targetContext.packageName
        val visibleScreen=visibleScreenBounds()
        val roots=automation.windows.take(16).filter {it.type==AccessibilityWindowInfo.TYPE_APPLICATION}
            .mapNotNull {window->window.root?.takeIf {it.packageName?.toString()==targetPackage}}
        val popup=tag=="view-home" || tag=="view-tools"
        val candidates=if(popup) roots.filter {it.windowId!=primaryWindowId}
            else roots.sortedBy {if(it.windowId==primaryWindowId) 0 else 1}
        for(root in candidates) {
        // Bare Compose testTags are exact viewIdResourceName values, not Android
        // package:id/name resources. Native resource/text search can miss them.
        val matching = mutableListOf<AccessibilityNodeInfo>()
        boundedNodes(root) { candidate -> if (candidate.viewIdResourceName == tag) matching.add(candidate) }
        // A native Popup has a separate semantics owner and cannot inherit host
        // testTagsAsResourceId. Only its two fixed public fixture labels may match.
        val publicLabel = if (popup && primaryWindowId >= 0 && root.windowId != primaryWindowId) when (tag) {
            "view-home" -> "Home"
            "view-tools" -> "Tools"
            else -> null
        } else null
        val choices = if (matching.isNotEmpty() || publicLabel == null) matching else {
            val fixedMatches = mutableListOf<AccessibilityNodeInfo>()
            boundedNodes(root) { candidate ->
                if (candidate.text?.toString() == publicLabel || candidate.contentDescription?.toString() == publicLabel)
                    fixedMatches.add(candidate)
            }
            fixedMatches
        }
        // No node labels, descriptions or unknown IDs are emitted to diagnostics.
        val found=choices.firstOrNull { val bounds = Rect(); it.getBoundsInScreen(bounds); it.isVisibleToUser && !bounds.isEmpty && !visibleScreen.isEmpty && Rect.intersects(bounds,visibleScreen) }
        if(found!=null) {
            if(!popup) primaryWindowId=root.windowId
            return found
        }
        }
        return null
    }
    private fun waitUntil(message: String, timeoutMs: Long = 5000, condition: () -> Boolean) {
        val expires = SystemClock.uptimeMillis() + timeoutMs
        while (!condition() && SystemClock.uptimeMillis() < expires) SystemClock.sleep(8)
        assertTrue(message, condition())
    }
    private fun controlBounds(tag: String): Rect {
        val expires=SystemClock.uptimeMillis()+5000
        while(SystemClock.uptimeMillis()<expires) {
            val target=node(tag)
            val bounds=Rect().also {target?.getBoundsInScreen(it)}
            if(target!=null && !bounds.isEmpty && bounds.intersect(visibleScreenBounds()) && !bounds.isEmpty) return bounds
            SystemClock.sleep(8)
        }
        saveKnownControlWindows()
        throw AssertionError("Public tagged control $tag must intersect the actual visible screen")
    }
    private fun tap(tag: String): Long = tap(controlBounds(tag))
    private fun tap(bounds: Rect): Long {
        val down = SystemClock.uptimeMillis(); val began = System.nanoTime()
        // Read the original overload's actual runtime defaults once without injecting it.
        if(basicObtainProbe==null) {
            val probe=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,bounds.exactCenterX(),bounds.exactCenterY(),0)
            try {basicObtainProbe=JSONObject().put("source",probe.source).put("toolType",probe.getToolType(0))}
            finally {probe.recycle()}
        }
        val properties=arrayOf(MotionEvent.PointerProperties().apply {id=0;toolType=MotionEvent.TOOL_TYPE_FINGER})
        val coordinates=arrayOf(MotionEvent.PointerCoords().apply {x=bounds.exactCenterX();y=bounds.exactCenterY();pressure=1f;size=1f})
        fun event(action:Int,time:Long)=MotionEvent.obtain(down,time,action,1,properties,coordinates,0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0)
        val first=event(MotionEvent.ACTION_DOWN,down)
        val last=event(MotionEvent.ACTION_UP,down+8)
        fun injectRecorded(event:MotionEvent):Boolean {
            val beforeNanos=System.nanoTime();val beforeUptime=SystemClock.uptimeMillis()
            var accepted=false
            try {accepted=automation.injectInputEvent(event,true);return accepted}
            finally {
                val record=JSONObject().put("action",event.actionMasked).put("source",event.source).put("toolType",event.getToolType(0))
                    .put("downTimeMs",event.downTime).put("eventTimeMs",event.eventTime)
                    .put("x",event.x).put("y",event.y).put("bounds",JSONArray(listOf(bounds.left,bounds.top,bounds.right,bounds.bottom)))
                    .put("accepted",if(accepted) 1 else 0).put("beforeUptimeMs",beforeUptime).put("afterUptimeMs",SystemClock.uptimeMillis())
                    .put("beforeNanos",beforeNanos).put("afterNanos",System.nanoTime())
                synchronized(nativeTouchRecords) {if(nativeTouchRecords.size<256) nativeTouchRecords.add(record) else nativeTouchOverflow.incrementAndGet()}
            }
        }
        try { assertTrue(injectRecorded(first)); assertTrue(injectRecorded(last)) }
        finally { first.recycle(); last.recycle() }
        return began
    }
    private fun select(view: String): Long {
        // Visibility/focus alone can precede the Activity window's actual onscreen pose.
        // Use the qualifying fixed rectangle; never query another possibly moving pose before injecting.
        val selectorBounds=stableSelectorBounds()
        tap(selectorBounds)
        return tap("view-$view")
    }
    private fun publicState(): HubState {
        val today = LocalDate.of(2026, 10, 11)
        val history = HubHistory(List(365) { index -> HistoryRow(today.minusDays(364L - index).toString(), 100, "1") }, emptyList())
        val snapshot = HubParser.parse("""{"updatedAt":"2026-10-11T08:00:00Z","periods":{"today":{"totalTokens":100,"costUsd":1},"month":{"totalTokens":100,"costUsd":1},"allTime":{"totalTokens":100,"costUsd":1}}}""")
        return HubState(snapshot = snapshot, savedAt = fixtureSavedAt, history = history, modelNames = ModelNames(emptyMap(), "off"), subscriptions = emptyList())
    }

    @Test fun actualHomeDrawsStaggerRevealCancelAndReuseCacheWithProductionClock() = withReportedViewIds {
        val target = instrumentation.targetContext
        val source = mutableStateOf(publicState())
        val prefs = mutableStateOf(DisplayPreferences(theme = ThemeChoice.DARK, language = LanguageChoice.ENGLISH,
            homeModules = listOf("activity"), heatmapMetric = "tokens", appearance = AppearancePreferences(reduceMotion = "off")))
        val actions = Actions()
        val appliedMotionPreference = AtomicReference<String?>(null)
        val samples = Collections.synchronizedList(mutableListOf<HomeHistoryEntryObservation>())
        val metrics = Collections.synchronizedList(mutableListOf<WindowSample>())
        val observationOverflow = AtomicInteger(); val metricsOverflow = AtomicInteger(); val lostMetrics = AtomicInteger()
        val thread = HandlerThread("public-home-entry-window-observer").apply { start() }
        val handler = Handler(thread.looper)
        val listener = Window.OnFrameMetricsAvailableListener { _, frame, lost ->
            synchronized(metrics) {
                if (metrics.size < 4096) metrics.add(WindowSample(frame.getMetric(FrameMetrics.VSYNC_TIMESTAMP),
                    frame.getMetric(FrameMetrics.TOTAL_DURATION), frame.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),
                    frame.getMetric(FrameMetrics.DRAW_DURATION), if (Build.VERSION.SDK_INT >= 31) frame.getMetric(FrameMetrics.DEADLINE) else 0))
                else metricsOverflow.incrementAndGet()
            }
            lostMetrics.addAndGet(lost)
        }
        val scenario = try { ActivityScenario.launch(MainActivity::class.java) } catch (failure: Throwable) {
            thread.quitSafely(); thread.join(5000)
            throw failure
        }
        val runs = JSONArray()
        var windowWidth = 0; var windowHeight = 0
        var lastEntryActionAtNanos = 0L
        fun observations() = synchronized(samples) { samples.toList() }
        fun entries() = observations().filter { it.phase == HomeHistoryEntryObservationPhase.ENTRY_START }
        fun entry() = entries().last()
        fun waitComplete(sequence: Int) = waitUntil("Finite Home controller must finish with real Choreographer") {
            observations().any { it.entrySequence == sequence && it.phase == HomeHistoryEntryObservationPhase.STATE && !it.frame.running && !it.frame.preparing }
        }
        fun enter(): HomeHistoryEntryObservation {
            val oldCount = entries().size
            select("tools"); lastEntryActionAtNanos = select("home")
            waitUntil("A true main-view return consumes one new Home entry") { entries().size == oldCount + 1 }
            return entry()
        }
        fun intermediate(sequence: Int): HomeHistoryEntryObservation {
            waitUntil("Actual Window draw must expose an intermediate line and area") {
                observations().any { it.entrySequence == sequence && it.phase == HomeHistoryEntryObservationPhase.TREND_DRAW &&
                    it.frame.running && it.frame.trendReveal > 0f && it.frame.trendReveal < 1f }
            }
            return observations().last { it.entrySequence == sequence && it.phase == HomeHistoryEntryObservationPhase.TREND_DRAW &&
                it.frame.running && it.frame.trendReveal > 0f && it.frame.trendReveal < 1f }
        }
        fun measure(animated: Boolean, label: String) {
            val started = enter(); val sequence = started.entrySequence
            waitComplete(sequence)
            waitUntil("Final actual chart draw must be fully revealed") {
                observations().any { it.atNanos >= lastEntryActionAtNanos && (!animated || it.entrySequence == sequence) &&
                    it.phase == HomeHistoryEntryObservationPhase.TREND_DRAW && it.frame.trendReveal == 1f }
            }
            val ended = System.nanoTime()
            // Reduced motion can paint its final bitmap before the ready callback
            // consumes the pending entry. Never cause a diagnostic-only redraw.
            val collected = observations().filter { it.atNanos in lastEntryActionAtNanos..ended &&
                (it.entrySequence == sequence || it.phase in listOf(HomeHistoryEntryObservationPhase.HEAT_DRAW,
                    HomeHistoryEntryObservationPhase.TREND_DRAW, HomeHistoryEntryObservationPhase.TREND_CACHE_BUILD)) }
            val trend = collected.filter { it.phase == HomeHistoryEntryObservationPhase.TREND_DRAW }
            val middle = trend.filter { it.frame.trendReveal > 0f && it.frame.trendReveal < 1f }
            if (animated) assertTrue("Production Window has intermediate draws", middle.isNotEmpty()) else assertTrue(middle.isEmpty())
            assertTrue(trend.isNotEmpty())
            trend.groupBy { it.cacheIdentity }.values.forEach { sameCache ->
                assertEquals("Static path cache is not rebuilt on animation frames", 1, sameCache.map { it.cacheGeneration }.distinct().size)
            }
            assertTrue(trend.all { it.chartWidthPx <= windowWidth + 1 && it.chartHeightPx <= windowHeight + 1 &&
                abs(it.drawnStrokeLength - it.sourceStrokeLength * it.frame.trendReveal) < .01f &&
                abs(it.areaClipRightPx - it.chartWidthPx * it.frame.trendReveal) < .01f })
            val heat = collected.filter { it.phase == HomeHistoryEntryObservationPhase.HEAT_DRAW && !it.frame.preparing }
            assertTrue(heat.isNotEmpty())
            heat.forEach { draw ->
                // Independent brute-force rectangle intersection, not the production visibleColumns helper.
                val visible = (0 until draw.totalColumns).filter { column ->
                    val left = column * draw.pitchPx - draw.scrollPx
                    left + draw.cellPx > 0f && left < draw.viewportWidthPx
                }
                assertEquals(visible.first(), draw.visibleFirst); assertEquals(visible.last(), draw.visibleLast)
                assertEquals(1f, draw.outsideOpacity, 0f)
            }
            if (animated) {
                assertEquals(heat.first().visibleFirst, started.frame.columns!!.first)
                assertEquals(heat.first().visibleLast, started.frame.columns!!.last)
                assertTrue("Real heat draws show ordered column stagger", heat.any { it.firstOpacity > 0f && it.lastOpacity == 0f })
            }
            val actualViewport = Rect(); node("home-activity-viewport")!!.getBoundsInScreen(actualViewport)
            assertEquals("Recorded viewport agrees with real accessibility window bounds", actualViewport.width().toDouble(), heat.last().viewportWidthPx.toDouble(), 1.0)
            val window = synchronized(metrics) { metrics.filter { it.vsync in lastEntryActionAtNanos..ended } }
            assertTrue("Window frame callbacks accompany actual drawing", window.isNotEmpty())
            runs.put(JSONObject().put("operation", label).put("entrySequence", sequence).put("animated", animated)
                .put("controllerDurationMs", (collected.last { it.phase == HomeHistoryEntryObservationPhase.STATE }.atNanos - started.atNanos) / 1e6)
                .put("firstIntermediateDrawMs", middle.firstOrNull()?.let { (it.atNanos - started.atNanos) / 1e6 })
                .put("intermediateDraws", middle.size).put("heatDraws", heat.size).put("visibleFirst", heat.last().visibleFirst)
                .put("visibleLast", heat.last().visibleLast).put("viewportWidthPx", heat.last().viewportWidthPx)
                .put("scrollPx", heat.last().scrollPx).put("pathCacheGenerations", JSONArray(trend.map { it.cacheGeneration }.distinct()))
                .put("windowFrames", window.size).put("maxTotalMs", window.maxOfOrNull { it.total / 1e6 })
                .put("maxLayoutMs", window.maxOfOrNull { it.layout / 1e6 }).put("maxDrawMs", window.maxOfOrNull { it.draw / 1e6 }))
        }
        try {
            scenario.onActivity { activity ->
                hostView=activity.window.decorView
                activity.window.addOnFrameMetricsAvailableListener(listener, handler)
                activity.setContent {
                    CompositionLocalProvider(LocalHubCalendarClock provides Clock.fixed(Instant.parse("2026-10-11T08:00:00Z"), ZoneId.of("Asia/Shanghai")),
                        LocalHomeHistoryEntryObserver provides { observed -> synchronized(samples) {
                            if (samples.size < 8192) samples.add(observed) else observationOverflow.incrementAndGet()
                        } }) {
                        TokenTheme(true, prefs.value.language, prefs.value.appearance) {
                            val appliedPreference=prefs.value.appearance.reduceMotion
                            SideEffect { appliedMotionPreference.set(appliedPreference) }
                            Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                                HubScreen(source.value, actions, preferences = prefs.value, updateDisplay = { prefs.value = it.apply(prefs.value) })
                            }
                        }
                    }
                }
            }
            waitUntil("Public real Home charts must be displayed") { node("home-trend-chart") != null && node("home-activity-viewport") != null }
            primaryWindowId = node("home-trend-chart")!!.windowId
            scenario.onActivity { windowWidth = it.window.decorView.width; windowHeight = it.window.decorView.height }
            waitUntil("Initial public entry is consumed") { entries().isNotEmpty() }; waitComplete(entry().entrySequence)
            repeat(5) { measure(animated = true, label = "true-main-view-return-$it") }
            val count = entries().size
            select("home"); SystemClock.sleep(120)
            assertEquals("Selecting the existing Home view does not replay", count, entries().size)
            var started = enter(); val beforeFreshness = intermediate(started.entrySequence)
            scenario.onActivity { source.value = source.value.copy(savedAt = fixtureSavedAt + 1_000L, live = true) }
            waitUntil("Same-business freshness must retain an intermediate entry draw") {
                observations().any { it.atNanos > beforeFreshness.atNanos && it.entrySequence == started.entrySequence &&
                    it.phase == HomeHistoryEntryObservationPhase.TREND_DRAW && it.frame.running &&
                    it.frame.trendReveal > beforeFreshness.frame.trendReveal && it.contentIdentity == beforeFreshness.contentIdentity }
            }
            waitComplete(started.entrySequence)
            val settingsBounds=controlBounds("hub-settings-toggle")
            started = enter(); intermediate(started.entrySequence)
            val oldEntries = entries().size
            val settingsAt = tap(settingsBounds)
            assertTrue("The input must interrupt a live entry", observations().last { it.phase == HomeHistoryEntryObservationPhase.STATE && it.atNanos <= settingsAt }.frame.running)
            waitUntil("Settings hides and cancels the entry immediately") { observations().any { it.entrySequence == started.entrySequence &&
                it.atNanos >= settingsAt && !it.visible && it.phase == HomeHistoryEntryObservationPhase.STATE && !it.frame.running } }
            val settingsCancelled = observations().first { it.entrySequence == started.entrySequence && it.atNanos >= settingsAt &&
                !it.visible && it.phase == HomeHistoryEntryObservationPhase.STATE && !it.frame.running }
            assertTrue("Settings cancellation happens before the old 920ms entrance can finish", settingsCancelled.atNanos - settingsAt < 240_000_000L)
            tap("hub-settings-toggle")
            waitUntil("Settings return shows settled Home") { node("home-trend-chart") != null && observations().last { it.phase == HomeHistoryEntryObservationPhase.TREND_DRAW }.frame.trendReveal == 1f }
            assertEquals(oldEntries, entries().size)
            started = enter(); val replaced = intermediate(started.entrySequence)
            scenario.onActivity {
                val snapshot = source.value.snapshot!!
                source.value = source.value.copy(snapshot = snapshot.copy(periods = snapshot.periods + ("today" to snapshot.periods.getValue("today").copy(tokens = 200))))
            }
            waitUntil("Real content replacement cancels and paints its new cached chart completely") {
                observations().any { it.phase == HomeHistoryEntryObservationPhase.TREND_DRAW && it.entrySequence == started.entrySequence &&
                    it.contentIdentity != replaced.contentIdentity && !it.frame.running && it.frame.trendReveal == 1f }
            }
            assertEquals(oldEntries + 1, entries().size)
            // Query the stable native header bounds before starting the finite
            // entry. Accessibility traversal must not consume the interrupt window.
            val periodBounds=controlBounds("hub-period-month")
            started = enter(); val beforePeriod = intermediate(started.entrySequence)
            val periodAt = tap(periodBounds)
            assertTrue("Period input interrupts a live entrance", observations().last { it.phase == HomeHistoryEntryObservationPhase.STATE && it.atNanos <= periodAt }.frame.running)
            waitUntil("A real period render cancels the old entry without replay") {
                observations().any { it.atNanos > beforePeriod.atNanos && it.entrySequence == started.entrySequence &&
                    it.phase == HomeHistoryEntryObservationPhase.TREND_DRAW && !it.frame.running && it.frame.trendReveal == 1f }
            }
            assertEquals(oldEntries + 2, entries().size)
            started = enter(); intermediate(started.entrySequence)
            val beforeStop = entries().size; val stoppedAt = System.nanoTime()
            assertTrue("STOP is exercised during a live entrance", observations().last { it.phase == HomeHistoryEntryObservationPhase.STATE }.frame.running)
            scenario.moveToState(Lifecycle.State.CREATED)
            waitUntil("STOP cancels the active controller") { observations().any { it.atNanos >= stoppedAt &&
                it.phase == HomeHistoryEntryObservationPhase.STOP && !it.frame.running } }
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitUntil("Resume displays fully revealed current chart") { node("home-trend-chart") != null &&
                observations().last { it.phase == HomeHistoryEntryObservationPhase.TREND_DRAW }.frame.trendReveal == 1f }
            SystemClock.sleep(120); assertEquals(beforeStop, entries().size)
            scenario.onActivity { prefs.value = prefs.value.copy(appearance = prefs.value.appearance.copy(reduceMotion = "on")) }
            waitUntil("The fixture must commit preference-on before starting its native menu action") { appliedMotionPreference.get()=="on" }
            instrumentation.waitForIdleSync()
            measure(animated = false, label = "preference-on")
            val androidScale = android.provider.Settings.Global.getFloat(target.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            scenario.onActivity { prefs.value = prefs.value.copy(appearance = prefs.value.appearance.copy(reduceMotion = "system")) }
            waitUntil("The fixture must commit preference-system before starting its native menu action") { appliedMotionPreference.get()=="system" }
            instrumentation.waitForIdleSync()
            measure(animated = androidScale > 0f && androidScale.isFinite(), label = "preference-system")
            scenario.onActivity { prefs.value = prefs.value.copy(appearance = prefs.value.appearance.copy(reduceMotion = "off")) }
            waitUntil("The fixture must commit preference-off before starting its native menu action") { appliedMotionPreference.get()=="off" }
            instrumentation.waitForIdleSync()
            measure(animated = true, label = "preference-off")
            assertEquals(0, actions.calls.get()); assertEquals(200L, source.value.snapshot!!.periods.getValue("today").tokens)
            assertEquals(365, source.value.history!!.daily.size)
            val barrier = CountDownLatch(1); handler.post { barrier.countDown() }; assertTrue(barrier.await(5, TimeUnit.SECONDS))
            assertEquals(0, observationOverflow.get()); assertEquals(0, metricsOverflow.get()); assertEquals(0,nativeTouchOverflow.get()); assertEquals(0,selectorReadyOverflow)
            val hash = MessageDigest.getInstance("SHA-256").digest(File(target.applicationInfo.sourceDir).readBytes()).joinToString("") { "%02x".format(it) }
            val report = JSONObject().put("apkSha256", hash).put("clock", "production System.nanoTime and real Activity Choreographer")
                .put("composeTestClockUsed", false).put("windowWidthPx", windowWidth).put("windowHeightPx", windowHeight)
                .put("actualAnimatorDurationScale", androidScale).put("networkActionCalls", actions.calls.get())
                .put("metricsCallbacksDropped", lostMetrics.get()).put("metricsComplete", lostMetrics.get() == 0)
                .put("observations", observations().size).put("runs", runs).put("businessTokens", 200)
                .put("basicObtainProbe",basicObtainProbe ?: JSONObject.NULL).put("nativeTouchRecords",touchRecords())
                .put("nativeTouchOverflow",nativeTouchOverflow.get()).put("selectorReadySamples",selectorReadySamples())
                .put("selectorReadyOverflow",selectorReadyOverflow)
                .put("businessHistoryDays", 365).put("scope", "Public actual HubScreen Window draw/cancellation/cache events; no screenshot pixel or physical presentation FPS claim")
            File(target.getExternalFilesDir(null), "home-history-entry-window-public.json").writeText(report.toString(2))
        } finally {
            try { scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) } }
            finally {
                try { scenario.close() }
                finally { thread.quitSafely(); thread.join(5000) }
            }
        }
    }
}
