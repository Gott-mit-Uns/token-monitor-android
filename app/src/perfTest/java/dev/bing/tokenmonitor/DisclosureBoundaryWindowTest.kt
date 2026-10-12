package dev.bing.tokenmonitor

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.KeyCharacterMap
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inspector.WindowInspector
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Ignore
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Additional real-Activity Window acceptance, using public component fixtures.
 * No Compose test rule, synthetic frame clock, animation-scale override, global
 * settings write or network fixture. ROOT runs the same class at actual system
 * scale 0 and 1 on its cleared, owned emulator.
 *
 * Only the marker crop is exported as pixels. The marker is a constant solid
 * color, separate from the public input/menu fixture and from any app payload.
 */
class DisclosureBoundaryWindowTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val directory get() = requireNotNull(instrumentation.targetContext.getExternalFilesDir(null))
    private data class PixelSample(val energy: Long, val width: Int, val height: Int, val began: Long, val ended: Long)
    private data class HeaderSample(val at: Long, val mainY: Float, val general: Float, val main: Float)
    private val exportedRows = JSONArray()

    private inline fun withViewIds(block: () -> Unit) {
        val service = requireNotNull(automation.serviceInfo)
        val previousFlags = service.flags
        try {
            service.flags = previousFlags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = service
            block()
        } finally {
            val restore = requireNotNull(automation.serviceInfo)
            restore.flags = previousFlags
            automation.serviceInfo = restore
        }
    }

    private var primaryWindowId = -1
    private val visibleFrame = AtomicReference(Rect())
    private data class AppWindowRoot(val id: Int, val active: Boolean, val focused: Boolean,
        val root: AccessibilityNodeInfo)
    private fun appWindowRoots(): List<AppWindowRoot> {
        if (Build.VERSION.SDK_INT >= 34) automation.clearCache()
        val targetPackage = instrumentation.targetContext.packageName
        val identified = mutableListOf<AppWindowRoot>()
        automation.windows.take(16).filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.forEach { window ->
            val root = window.root
            if (root != null && root.packageName?.toString() == targetPackage)
                identified.add(AppWindowRoot(window.id, window.isActive, window.isFocused, root))
        }
        val active = automation.rootInActiveWindow
        if (active != null && active.packageName?.toString() == targetPackage && identified.none { it.id == active.windowId })
            identified.add(AppWindowRoot(active.windowId, true, false, active))
        return identified.sortedWith(compareByDescending<AppWindowRoot> { it.id == primaryWindowId }
            .thenByDescending { it.active }.thenByDescending { it.focused })
    }

    /** Match the identified application host, even when IME/Popup owns the active root. */
    private fun node(tag: String): AccessibilityNodeInfo? {
        val roots = appWindowRoots()
        val candidates = if (tag == "boundary-choice") roots.filter { it.id != primaryWindowId && (it.active || it.focused) }
            else roots.filter { primaryWindowId < 0 || it.id == primaryWindowId }
        candidates.forEach { window ->
            val queue = java.util.ArrayDeque<AccessibilityNodeInfo>(); queue.add(window.root)
            var inspected = 0
            while (queue.isNotEmpty() && inspected < 1024) {
                val candidate = queue.removeFirst(); inspected++
                val bounds = Rect(); candidate.getBoundsInScreen(bounds)
                val fixedPopupLabel = tag == "boundary-choice" &&
                    (candidate.text?.toString() == "Public choice" || candidate.contentDescription?.toString() == "Public choice")
                if ((candidate.viewIdResourceName == tag || fixedPopupLabel) && candidate.isVisibleToUser && !bounds.isEmpty) {
                    if(tag!="boundary-choice") primaryWindowId=window.id
                    return candidate
                }
                repeat(candidate.childCount.coerceAtMost((1024 - inspected - queue.size).coerceAtLeast(0))) { index -> candidate.getChild(index)?.let(queue::add) }
            }
        }
        return null
    }
    private fun writeReadyDiagnostic(probe: ComponentProbe) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) automation.clearCache()
            val fixedTags = setOf("boundary-fixture", "boundary-toggle", "boundary-motion", "boundary-marker",
                "boundary-field", "boundary-menu-open", "boundary-menu", "boundary-menu-transition", "boundary-choice",
                "anchor-viewport", "anchor-header-general", "anchor-header-main", "anchor-body-general", "anchor-body-main")
            val targetPackage = instrumentation.targetContext.packageName
            val active = automation.rootInActiveWindow
            val rows = JSONArray()
            automation.windows.take(16).forEach { window ->
                val root = window.root
                val target = root?.packageName?.toString() == targetPackage
                val rectangle = Rect(); window.getBoundsInScreen(rectangle)
                val matches = JSONArray(); var inspected = 0
                if (target && root != null) {
                    val queue = java.util.ArrayDeque<AccessibilityNodeInfo>(); queue.add(root)
                    while (queue.isNotEmpty() && inspected < 1024) {
                        val candidate = queue.removeFirst(); inspected++
                        val tag = candidate.viewIdResourceName
                        if (tag in fixedTags) {
                            val b = Rect(); candidate.getBoundsInScreen(b)
                            val intersection = Rect(b); if (!intersection.intersect(visibleFrame.get())) intersection.setEmpty()
                            matches.put(JSONObject().put("testTag", tag).put("visibleToUser", candidate.isVisibleToUser)
                                .put("bounds", JSONArray(listOf(b.left, b.top, b.right, b.bottom)))
                                .put("visibleFrameIntersection", JSONArray(listOf(intersection.left, intersection.top, intersection.right, intersection.bottom))))
                        }
                        repeat(candidate.childCount.coerceAtMost((1024 - inspected - queue.size).coerceAtLeast(0))) { index -> candidate.getChild(index)?.let(queue::add) }
                    }
                }
                rows.put(JSONObject().put("id", window.id).put("type", window.type).put("active", window.isActive)
                    .put("focused", window.isFocused).put("identifiedHost", window.id == primaryWindowId)
                    .put("rootAvailable", root != null).put("belongsToTarget", target).put("inspectedNodes", inspected)
                    .put("bounds", JSONArray(listOf(rectangle.left, rectangle.top, rectangle.right, rectangle.bottom))).put("fixedMatches", matches))
            }
            val value = probe.scale.get()
            File(directory, "disclosure-boundary-ready-failure-public.json").writeText(JSONObject()
                .put("effectiveScale", if (value.isFinite()) value.toDouble() else JSONObject.NULL)
                .put("scaleFinite", value.isFinite()).put("requiredScale", 1.0).put("scaleMatch", value == 1f)
                .put("tagMatch", node("boundary-toggle") != null).put("identifiedHostWindowId", primaryWindowId)
                .put("activeWindowId", active?.windowId ?: -1).put("activeBelongsToTarget", active?.packageName?.toString() == targetPackage)
                .put("windows", rows).toString(2))
        }
    }

    private fun waitUntil(message: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 5000
        while (!condition() && SystemClock.uptimeMillis() < end) SystemClock.sleep(4)
        assertTrue(message, condition())
    }
    private fun bounds(tag: String): Rect {
        waitUntil("Fixed public control must be visible") { node(tag) != null }
        return Rect().also { node(tag)!!.getBoundsInScreen(it) }
    }
    private fun tap(rect: Rect): Long {
        val down = SystemClock.uptimeMillis()
        val began = System.nanoTime()
        val first = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, rect.exactCenterX(), rect.exactCenterY(), 0)
        val last = MotionEvent.obtain(down, down + 8, MotionEvent.ACTION_UP, rect.exactCenterX(), rect.exactCenterY(), 0)
        try {
            assertTrue("Native down must be delivered", automation.injectInputEvent(first, true))
            assertTrue("Native up must be delivered", automation.injectInputEvent(last, true))
        } finally { first.recycle(); last.recycle() }
        return began
    }
    private fun tap(tag: String): Long = tap(bounds(tag))
    private fun childWindowIsActive(host: Int): Boolean {
        if (Build.VERSION.SDK_INT >= 34) automation.clearCache()
        val root = automation.rootInActiveWindow ?: return false
        return root.windowId != host
    }
    private fun observations(samples: MutableList<DesktopDisclosureObservation>) = synchronized(samples) { samples.toList() }
    private fun settled(samples: MutableList<DesktopDisclosureObservation>, after: Long, open: Boolean) =
        observations(samples).any { it.atNanos >= after && it.phase == DesktopDisclosureObservationPhase.STATE &&
            it.open == open && !it.pose.running && it.pose.heightFraction == (if (open) 1f else 0f) }
    private fun middle(samples: MutableList<DesktopDisclosureObservation>, after: Long, open: Boolean) =
        observations(samples).any { it.atNanos >= after && it.phase == DesktopDisclosureObservationPhase.DRAW &&
            it.open == open && it.pose.running && it.pose.heightFraction in .5f.. .8f }
    private fun retainedLayers(samples: MutableList<DesktopDisclosureObservation>): Set<Int> {
        val active = mutableSetOf<Int>()
        observations(samples).forEach {
            if (it.phase == DesktopDisclosureObservationPhase.DRAW && it.cacheWidthPx > 0 && it.cacheHeightPx > 0) active.add(it.identity)
            if (it.phase == DesktopDisclosureObservationPhase.RELEASE) active.remove(it.identity)
        }
        return active
    }
    private data class PopupWindowCounts(val roots:Int,val attached:Int)
    private fun popupWindowCounts(scenario:ActivityScenario<MainActivity>):PopupWindowCounts {
        val result=AtomicReference(PopupWindowCounts(-1,-1))
        scenario.onActivity {
            check(Build.VERSION.SDK_INT>=29) {"WindowInspector acceptance requires API29 or newer"}
            if(Build.VERSION.SDK_INT>=29) {
                // Fixed framework class only. Count ALL roots for the strict
                // absence assertion; attached roots are separate diagnostics.
                val roots=WindowInspector.getGlobalWindowViews().filter {view->
                    view.javaClass.name=="androidx.compose.ui.window.PopupLayout"
                }
                result.set(PopupWindowCounts(roots.size,roots.count {it.isAttachedToWindow}))
            }
        }
        return result.get()
    }
    private fun screenshotMarker(rect: Rect, filename: String): PixelSample {
        val began = System.nanoTime()
        val raw = requireNotNull(automation.takeScreenshot()) { "Actual display screenshot must be available" }
        val copied = requireNotNull(raw.copy(Bitmap.Config.ARGB_8888, false))
        raw.recycle()
        try {
            assertTrue("Marker crop must fit the actual display", rect.left >= 0 && rect.top >= 0 &&
                rect.right <= copied.width && rect.bottom <= copied.height && !rect.isEmpty)
            val crop = Bitmap.createBitmap(copied, rect.left, rect.top, rect.width(), rect.height())
            try {
                val pixels = IntArray(crop.width * crop.height); crop.getPixels(pixels, 0, crop.width, 0, 0, crop.width, crop.height)
                val energy = pixels.sumOf { pixel ->
                    // Magenta against the constant neutral background. Clipping and
                    // opacity both lower the energy; unrelated content is outside.
                    (minOf(android.graphics.Color.red(pixel), android.graphics.Color.blue(pixel)) -
                        android.graphics.Color.green(pixel)).coerceAtLeast(0).toLong()
                }
                File(directory, filename).outputStream().use {
                    assertTrue("The public marker PNG must encode successfully", crop.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                return PixelSample(energy, crop.width, crop.height, began, System.nanoTime())
            } finally { crop.recycle() }
        } finally { copied.recycle() }
    }

    private fun fullyPresentedMarker(scenario:ActivityScenario<MainActivity>,probe:ComponentProbe,rectangle:Rect):PixelSample {
        waitUntil("Fully open marker must first reach an actual DRAW with a viewport layer") {
            observations(probe.samples).any {it.phase==DesktopDisclosureObservationPhase.DRAW && it.open &&
                !it.pose.running && it.pose.heightFraction==1f && it.pose.opacity==1f &&
                it.cacheWidthPx>0 && it.cacheHeightPx>0}
        }
        val committed=CountDownLatch(1)
        val committedAt=AtomicLong()
        scenario.onActivity {activity->
            check(Build.VERSION.SDK_INT>=29) {"Native frame-commit acceptance requires API29 or newer"}
            val view=activity.window.decorView
            check(view.isHardwareAccelerated) {"Actual renderer frame commit requires the hardware Window"}
            if(Build.VERSION.SDK_INT>=29) view.viewTreeObserver.registerFrameCommitCallback {
                committedAt.set(System.nanoTime());committed.countDown()
            }
            view.invalidate()
        }
        assertTrue("The fixture must commit an actual native Window frame",committed.await(5,TimeUnit.SECONDS))
        // Frame commit means submitted to the swap chain, not yet presented by
        // SurfaceFlinger. Confirm the initial static surface with actual pixels
        // before starting a250ms exit; no animation time or deadline is changed.
        val deadline=SystemClock.uptimeMillis()+5000
        var attempts=0
        var capture:PixelSample
        do {
            capture=screenshotMarker(rectangle,"disclosure-boundary-marker-full.png");attempts++
            if(capture.energy<=0 && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(4)
        } while(capture.energy<=0 && SystemClock.uptimeMillis()<deadline)
        File(directory,"disclosure-boundary-marker-presentation-public.json").writeText(JSONObject()
            .put("nativeFrameCommittedAtNanos",committedAt.get()).put("presentationCaptureAttempts",attempts)
            .put("fullMarkerEnergy",capture.energy).put("captureBeganAtNanos",capture.began)
            .put("captureEndedAtNanos",capture.ended).put("markerWidthPx",capture.width)
            .put("markerHeightPx",capture.height).toString(2))
        assertTrue("Fully open marker must be painted",capture.energy>0)
        return capture
    }

    private class ComponentProbe {
        val marker = AtomicReference(Rect())
        val field = AtomicReference("")
        val mounted = AtomicInteger()
        val disposed = AtomicInteger()
        val choices = AtomicInteger()
        val scale = AtomicReference(Float.NaN)
        val overflow = AtomicInteger()
        // Test-only gate observes an actual draw; it never advances a clock or
        // modifies a production controller from inside the draw callback.
        val windowObservation = AtomicReference<((DesktopDisclosureObservation) -> Unit)?>(null)
        val samples: MutableList<DesktopDisclosureObservation> = Collections.synchronizedList(mutableListOf())
    }

    private fun componentWindow(initialOpen: Boolean, body: (ActivityScenario<MainActivity>, MutableState<Boolean>,
        MutableState<String>, ComponentProbe) -> Unit) = withViewIds {
        val open = mutableStateOf(initialOpen)
        val preference = mutableStateOf("off")
        val probe = ComponentProbe()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val location = IntArray(2); val windowLocation = IntArray(2)
                activity.window.decorView.getLocationOnScreen(location)
                activity.window.decorView.getLocationInWindow(windowLocation)
                val screenX = location[0] - windowLocation[0]; val screenY = location[1] - windowLocation[1]
                primaryWindowId = -1 // Identified from the actual public tagged app-window node below.
                activity.window.decorView.getWindowVisibleDisplayFrame(Rect().also { visibleFrame.set(it) })
                activity.setContent {
                    TokenTheme(true, LanguageChoice.ENGLISH, AppearancePreferences(reduceMotion = preference.value)) {
                        val scale = LocalDesktopMotionScale.current
                        SideEffect { probe.scale.set(scale) }
                        CompositionLocalProvider(LocalDesktopDisclosureObserver provides { sample ->
                            synchronized(probe.samples) {
                                if (probe.samples.size < 4096) probe.samples.add(sample) else probe.overflow.incrementAndGet()
                            }
                            probe.windowObservation.get()?.invoke(sample)
                        }) {
                            Column(Modifier.fillMaxSize().background(Color(0xFF101010)).windowInsetsPadding(WindowInsets.safeDrawing)
                                .testTag("boundary-fixture").semantics { testTagsAsResourceId = true }) {
                                Button(onClick = { open.value = !open.value }, modifier = Modifier.testTag("boundary-toggle")) { Text("Public toggle") }
                                DesktopAccordion(open.value, "boundary-motion") {
                                    DisposableEffect(Unit) { probe.mounted.incrementAndGet(); onDispose { probe.disposed.incrementAndGet() } }
                                    var field by rememberSaveable { mutableStateOf("Public draft") }
                                    SideEffect { probe.field.set(field) }
                                    Box(Modifier.fillMaxWidth().height(120.dp).background(Color.Magenta)
                                        .testTag("boundary-marker").onGloballyPositioned {
                                            val p = it.positionInWindow()
                                            probe.marker.set(Rect(p.x.roundToInt() + screenX, p.y.roundToInt() + screenY,
                                                p.x.roundToInt() + screenX + it.size.width, p.y.roundToInt() + screenY + it.size.height))
                                        })
                                    OutlinedTextField(field, { field = it }, modifier = Modifier.fillMaxWidth().testTag("boundary-field"))
                                    Box {
                                        var menu by remember { mutableStateOf(false) }
                                        Button(onClick = { menu = true }, modifier = Modifier.testTag("boundary-menu-open")) { Text("Public options") }
                                        DesktopOptionMenu(menu, listOf(DesktopMenuOption("one", "Public choice", "boundary-choice")),
                                            selected = "one", tag = "boundary-menu", animate = true, dismiss = { menu = false }) {
                                            probe.choices.incrementAndGet(); menu = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val readyEnd = SystemClock.uptimeMillis() + 5000
            while ((node("boundary-toggle") == null || probe.scale.get() != 1f) && SystemClock.uptimeMillis() < readyEnd) SystemClock.sleep(4)
            val tagReady = node("boundary-toggle") != null
            val scaleReady = probe.scale.get() == 1f
            if (!tagReady || !scaleReady) writeReadyDiagnostic(probe)
            assertTrue("Real component fixture must expose its fixed host toggle", tagReady)
            assertEquals("Explicit off must resolve the real production theme scale to 1", 1f, probe.scale.get(), 0f)
            body(scenario, open, preference, probe)
            assertEquals("Bounded observer must not overflow", 0, probe.overflow.get())
        } finally { scenario.close() }
    }

    @Test fun actualPixelsAndRapidReversalsKeepVisibleMotion() = componentWindow(true) { scenario, _, _, probe ->
        waitUntil("Public marker must have its natural screen rectangle") { !probe.marker.get().isEmpty }
        val rectangle = Rect(probe.marker.get())
        val full = fullyPresentedMarker(scenario,probe,rectangle)
        val toggle = bounds("boundary-toggle")
        val closing = tap(toggle)
        waitUntil("Close must reach a real intermediate Window draw") { middle(probe.samples, closing, false) }
        val mid = screenshotMarker(rectangle, "disclosure-boundary-marker-exit-middle.png")
        assertTrue("Actual exit pixels must retain the colored body", mid.energy > 0)
        assertTrue("Actual exit pixels must show partial height or opacity", mid.energy < full.energy)
        waitUntil("Close must end without retaining a layer") { settled(probe.samples, closing, false) && retainedLayers(probe.samples).isEmpty() }
        assertNull("Closed body's input semantics must be unmounted", node("boundary-field"))

        val opening = tap(toggle)
        waitUntil("Open must reach a real intermediate Window draw") { middle(probe.samples, opening, true) }
        val reverseClose = tap(toggle)
        waitUntil("Reverse close must start from the current visual position") {
            observations(probe.samples).any { it.atNanos >= reverseClose && it.phase == DesktopDisclosureObservationPhase.STATE &&
                !it.open && it.pose.running && it.pose.heightFraction > 0f && it.pose.heightFraction < 1f }
        }
        val reverseOpen = tap(toggle)
        waitUntil("Second reversal must start from the current visual position") {
            observations(probe.samples).any { it.atNanos >= reverseOpen && it.phase == DesktopDisclosureObservationPhase.STATE &&
                it.open && it.pose.running && it.pose.heightFraction > 0f && it.pose.heightFraction < 1f }
        }
        waitUntil("Reversed sequence must finish at the requested open endpoint") { settled(probe.samples, reverseOpen, true) }
        assertEquals("Parent exit and reversal must never invoke a menu choice", 0, probe.choices.get())
        exportedRows.put(JSONObject().put("case", "actual_exit_pixels_and_two_reversals")
            .put("fullMarkerEnergy", full.energy).put("middleMarkerEnergy", mid.energy)
            .put("screenCaptureBeganNanos", mid.began).put("screenCaptureEndedNanos", mid.ended)
            .put("markerWidthPx", full.width).put("markerHeightPx", full.height))
        File(directory, "disclosure-boundary-pixels-public.json").writeText(exportedRows.toString(2))
    }

    @Test fun parentCloseUnloadsChildPopupInputAndPreservesSaveableField() = componentWindow(true) { scenario, open, _, probe ->
        bounds("boundary-field")
        val argument = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Public edited") }
        assertTrue("Public editable field must accept the real accessibility edit action",
            requireNotNull(node("boundary-field")).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, argument))
        waitUntil("The ordinary field edit must commit") { probe.field.get() == "Public edited" }
        val mainWindow = requireNotNull(node("boundary-toggle")).windowId
        tap("boundary-menu-open")
        waitUntil("Real child Popup window must be active") { childWindowIsActive(mainWindow) }
        val popupWindow = requireNotNull(automation.rootInActiveWindow).windowId
        // A public, fixed fixture label locates only this Popup choice. No label
        // text, content description or unknown identifier is written to reports.
        val oldChoice = Rect()
        waitUntil("Fixed public Popup choice must be visible") { node("boundary-choice") != null }
        val option = requireNotNull(node("boundary-choice"))
        option.getBoundsInScreen(oldChoice)
        val popupBeforeClose=popupWindowCounts(scenario)
        assertEquals("The public child fixture must own exactly one native Popup root",1,popupBeforeClose.roots)
        assertEquals("The public child fixture's real Popup root must be attached before close",1,popupBeforeClose.attached)
        val closing = System.nanoTime()
        scenario.onActivity { open.value = false }
        waitUntil("Parent close must unmount the active child immediately") {
            observations(probe.samples).any { it.atNanos >= closing && it.phase == DesktopDisclosureObservationPhase.STATE &&
                !it.open && !it.acceptsInput } && probe.disposed.get() == 1
        }
        val exitBegan=requireNotNull(observations(probe.samples).firstOrNull {it.atNanos>=closing &&
            it.phase==DesktopDisclosureObservationPhase.STATE && !it.open && it.pose.running})
        val exitDeadline=exitBegan.atNanos+(DesktopDisclosureMotionTokens.accordionDurationMs*
            1_000_000.0*probe.scale.get()).toLong()
        val nativePopupAfter=AtomicInteger(-1)
        val nativeAttachedPopupAfter=AtomicInteger(-1)
        val checkedAt=AtomicLong()
        val exitStillRunning=AtomicBoolean()
        val exitStillRetainsLayer=AtomicBoolean()
        scenario.onActivity {
            check(Build.VERSION.SDK_INT>=29) {"Native Popup ownership acceptance requires API29 or newer"}
            if(Build.VERSION.SDK_INT>=29) {
                val roots=WindowInspector.getGlobalWindowViews().filter {view->
                    view.javaClass.name=="androidx.compose.ui.window.PopupLayout"
                }
                nativePopupAfter.set(roots.size)
                nativeAttachedPopupAfter.set(roots.count {it.isAttachedToWindow})
            }
            checkedAt.set(System.nanoTime())
            val latest=observations(probe.samples).lastOrNull()
            exitStillRunning.set(latest!=null && !latest.open && latest.pose.running)
            exitStillRetainsLayer.set(retainedLayers(probe.samples).isNotEmpty())
        }
        if(Build.VERSION.SDK_INT>=34) automation.clearCache()
        val accessibilityStillListsPopup=automation.windows.any {it.id==popupWindow}
        File(directory,"disclosure-boundary-parent-popup-close-public.json").writeText(JSONObject()
            .put("nativePopupRootsBefore",popupBeforeClose.roots).put("nativeAttachedPopupRootsBefore",popupBeforeClose.attached)
            .put("nativePopupRootsAfter",nativePopupAfter.get()).put("nativeAttachedPopupRootsAfter",nativeAttachedPopupAfter.get())
            .put("nativeWindowCheckedAtNanos",checkedAt.get()).put("exitBeganAtNanos",exitBegan.atNanos)
            .put("originalExitDeadlineAtNanos",exitDeadline).put("parentExitStillRunning",exitStillRunning.get())
            .put("parentExitStillRetainsLayer",exitStillRetainsLayer.get())
            .put("accessibilityStillListsOriginalPopup",accessibilityStillListsPopup).toString(2))
        // Accessibility window-change events/cache can lag native ViewRoot
        // detachment. The synchronous native roots prove actual Window/input
        // ownership; an A11y inventory read alone cannot prove that boundary.
        assertEquals("The child Popup Window must be gone while parent exit pixels still run",0,nativePopupAfter.get())
        assertTrue("Native Popup removal must precede the unchanged finite parent exit deadline",checkedAt.get()<exitDeadline)
        assertTrue("Native Popup removal must be observed during the parent's actual running exit",exitStillRunning.get())
        assertTrue("Native Popup removal must be observed while the original viewport pixels remain",exitStillRetainsLayer.get())
        assertNull("The old field must no longer expose input semantics", node("boundary-field"))
        tap(oldChoice)
        assertEquals("Tapping the former child option coordinates must not invoke its old action", 0, probe.choices.get())
        waitUntil("Exit must release its viewport display list") { settled(probe.samples, closing, false) && retainedLayers(probe.samples).isEmpty() }
        val opening = tap("boundary-toggle")
        waitUntil("Reopen must retain the ordinary field choice") { settled(probe.samples, opening, true) && probe.field.get() == "Public edited" }
        assertTrue("Reopening must not replay the unmounted child Popup", automation.windows.none { it.id == popupWindow })
        assertEquals(0, probe.choices.get())
    }

    @Test fun nativeEditorIsUnmountedBeforeExitPixelsFinishAndImeHides() = componentWindow(true) { scenario, open, _, probe ->
        tap("boundary-field")
        waitUntil("Native public editor must actually open an IME Window") {
            automation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        }
        val closing = System.nanoTime()
        scenario.onActivity { open.value = false }
        waitUntil("Close must dispose the focused input subtree while exit runs") {
            probe.disposed.get() == 1 && observations(probe.samples).any { it.atNanos >= closing &&
                it.phase == DesktopDisclosureObservationPhase.STATE && !it.open && !it.acceptsInput && it.pose.running }
        }
        assertNull("Focused old editor semantics must be removed during retained exit", node("boundary-field"))
        instrumentation.sendStringSync("x")
        assertEquals("Keys after unmount must not write to the old field", "Public draft", probe.field.get())
        waitUntil("The native IME must finish hiding after its input session is removed") {
            automation.windows.none { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        }
        waitUntil("IME close must not extend the retained display list deadline") {
            settled(probe.samples, closing, false) && retainedLayers(probe.samples).isEmpty()
        }
        val opening = tap("boundary-toggle")
        waitUntil("Reopen must retain the ordinary field without the stale key input") {
            settled(probe.samples, opening, true) && probe.field.get() == "Public draft"
        }
        assertEquals(0, probe.choices.get())
    }

    @Test fun actualSystemSettingAndAllThreePreferencesControlRealDraws() = componentWindow(false) { scenario, _, preference, probe ->
        val nativeScale = android.provider.Settings.Global.getFloat(instrumentation.targetContext.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f).coerceAtLeast(0f)
        val rows = JSONArray()
        listOf("system", "on", "off").forEach { value ->
            val expectedScale = when (value) { "on" -> 0f; "off" -> 1f; else -> nativeScale }
            scenario.onActivity { preference.value = value }
            waitUntil("Theme must observe the actual requested motion preference") { probe.scale.get() == expectedScale }
            listOf(true, false).forEach { expanding ->
                val began = tap("boundary-toggle")
                waitUntil("Preference transition must settle at its requested endpoint") { settled(probe.samples, began, expanding) }
                val frames = observations(probe.samples).filter { it.atNanos >= began && it.phase == DesktopDisclosureObservationPhase.DRAW }
                val intermediate = frames.count { it.pose.heightFraction > 0f && it.pose.heightFraction < 1f }
                if (expectedScale > 0f) assertTrue("Enabled real Window motion must have an intermediate draw", intermediate > 0)
                else assertEquals("Reduced real Window motion must have zero intermediate draws", 0, intermediate)
                // The real Window releases the retained exit layer on a later frame than the
                // settled STATE observation, exactly like the other close checks in this file
                // (lines 388, 479, 504). Wait for the release with the same bounded deadline
                // instead of sampling one frame too early.
                if (!expanding) waitUntil("All preference modes release a closed layer") { retainedLayers(probe.samples).isEmpty() }
                rows.put(JSONObject().put("preference", value).put("actualSystemScale", nativeScale)
                    .put("effectiveScale", probe.scale.get()).put("open", expanding).put("intermediateDraws", intermediate))
            }
        }
        File(directory, "disclosure-boundary-three-preferences-public.json").writeText(rows.toString(2))
    }

    @Test fun stopDuringOpeningAndClosingReleasesLayersAndDoesNotReplay() = componentWindow(true) { scenario, open, _, probe ->
        val rows = JSONArray()
        // Warm the real body/font/layout and its bounded cache before testing
        // the finite STOP boundary. Both directions still use production motion.
        waitUntil("Initial real body must have a fully open Window draw and viewport layer") {
            observations(probe.samples).any { it.phase == DesktopDisclosureObservationPhase.DRAW &&
                it.open && !it.pose.running && it.pose.heightFraction == 1f &&
                it.cacheWidthPx > 0 && it.cacheHeightPx > 0 }
        }
        listOf(false, true).forEach { target ->
            val began = AtomicLong()
            val liveDraw = AtomicReference<DesktopDisclosureObservation?>(null)
            val reachedLiveDraw = CountDownLatch(1)
            val stopEventAt = AtomicLong()
            val stopObserver = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) stopEventAt.set(System.nanoTime())
            }
            probe.windowObservation.set { sample ->
                if (sample.atNanos >= began.get() && began.get() > 0L &&
                    sample.phase == DesktopDisclosureObservationPhase.DRAW && sample.open == target &&
                    sample.pose.running && sample.pose.heightFraction in .5f.. .8f &&
                    sample.cacheWidthPx > 0 && sample.cacheHeightPx > 0 &&
                    liveDraw.compareAndSet(null, sample)) reachedLiveDraw.countDown()
            }
            // Parent controller/cache observers are already installed. Register
            // last BEFORE the action, avoiding Activity/A11y IPC inside a short
            // 250ms animation. Downward callbacks therefore retain the real STOP
            // marker ordering. This controlled state change drives the actual
            // Activity recomposer and Window; native touch is covered separately.
            scenario.onActivity {
                it.lifecycle.addObserver(stopObserver)
                began.set(System.nanoTime())
                open.value = target
            }
            val observedLiveDraw = reachedLiveDraw.await(5, TimeUnit.SECONDS)
            val requestedStopAt = System.nanoTime()
            val draw = liveDraw.get()
            val latest = observations(probe.samples).lastOrNull()
            val runningAtStopRequest = latest != null && latest.open == target && latest.pose.running &&
                latest.pose.heightFraction > 0f && latest.pose.heightFraction < 1f
            val row = JSONObject().put("open", target).put("transitionBeganAtNanos", began.get())
                .put("liveIntermediateDrawObserved", observedLiveDraw)
                .put("liveDrawAtNanos", draw?.atNanos ?: 0L)
                .put("liveDrawHeightFraction", draw?.pose?.heightFraction?.toDouble() ?: JSONObject.NULL)
                .put("liveDrawCacheWidthPx", draw?.cacheWidthPx ?: 0)
                .put("liveDrawCacheHeightPx", draw?.cacheHeightPx ?: 0)
                .put("stopRequestedAtNanos", requestedStopAt).put("runningAtStopRequest", runningAtStopRequest)
            rows.put(row)
            if (!observedLiveDraw || !runningAtStopRequest)
                File(directory, "disclosure-boundary-stop-timelines-public.json").writeText(rows.toString(2))
            try {
                assertTrue("STOP workload must actually begin while motion runs", observedLiveDraw)
                assertTrue("STOP request must occur while the actual Window transition is still running", runningAtStopRequest)
                assertTrue("Visible running body must have a recorded viewport layer", retainedLayers(probe.samples).isNotEmpty())
                scenario.moveToState(Lifecycle.State.CREATED)
            } finally {
                probe.windowObservation.set(null)
                scenario.onActivity { it.lifecycle.removeObserver(stopObserver) }
            }
            val stoppedAt = stopEventAt.get()
            val moveReturnedAt = System.nanoTime()
            assertTrue("The actual ON_STOP event must have been recorded", stoppedAt > 0L && stoppedAt <= moveReturnedAt)
            waitUntil("STOP must release every recorded viewport layer") { retainedLayers(probe.samples).isEmpty() }
            val layersAfterStop = retainedLayers(probe.samples).size
            assertTrue("Stopped Window must not expose old field input", node("boundary-field") == null)
            val stopped = observations(probe.samples).filter { it.atNanos >= stoppedAt && it.atNanos <= moveReturnedAt }
            val states = stopped.filter { it.phase == DesktopDisclosureObservationPhase.STATE }
            assertTrue("Actual STOP must emit a blocked settled state, not an empty filtered sample", states.isNotEmpty())
            assertTrue("Controllers recorded at STOP must be settled and reject input", states.all { !it.acceptsInput && !it.pose.running })
            assertTrue("Actual STOP DRAW/RELEASE observations must reject old input", stopped.all { !it.acceptsInput })
            assertTrue("No draw after completed STOP may restart its controller",
                observations(probe.samples).filter { it.atNanos >= moveReturnedAt && it.phase == DesktopDisclosureObservationPhase.DRAW }
                    .none { it.acceptsInput || it.pose.running })
            val resumedAt = System.nanoTime()
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitUntil("Resume must settle to the existing requested state") { settled(probe.samples, resumedAt, target) }
            assertTrue("Resume must not restart the canceled transition",
                observations(probe.samples).filter { it.atNanos >= resumedAt }.none { it.pose.running })
            assertEquals(0, probe.choices.get())
            row.put("stopEventAtNanos", stoppedAt).put("moveReturnedAtNanos", moveReturnedAt)
                .put("blockedSettledStates", states.size).put("layersAfterStop", layersAfterStop)
            File(directory, "disclosure-boundary-stop-timelines-public.json").writeText(rows.toString(2))
        }
    }

    @Ignore("Exploratory whole-composition ownership diagnostic; no pinned desktop parity requirement")
    @Test fun stopActuallyDisposesPopupCompositionBeforeAnyResume() = componentWindow(true) { scenario, _, _, probe ->
        val hostWindow = requireNotNull(node("boundary-toggle")).windowId
        tap("boundary-menu-open")
        waitUntil("Real Popup must open before testing STOP") { childWindowIsActive(hostWindow) }
        assertEquals("The fixture must own exactly one actual attached Popup", 1, popupWindowCounts(scenario).roots)
        scenario.moveToState(Lifecycle.State.CREATED)
        val popupWindowsAfterStop = popupWindowCounts(scenario).roots
        val mountedAfterStop = probe.mounted.get()
        val disposedAfterStop = probe.disposed.get()
        val layersAfterStop = retainedLayers(probe.samples).size
        // Write fixed numeric evidence before the strict assertions so a paused
        // parent composition is distinguishable from only stale observation flags.
        File(directory, "disclosure-boundary-stop-popup-public.json").writeText(JSONObject()
            .put("mountedSubtrees", mountedAfterStop).put("disposedSubtreesAfterStop", disposedAfterStop)
            .put("attachedPopupWindowsAfterStop", popupWindowsAfterStop).put("layersAfterStop", layersAfterStop).toString(2))
        assertEquals("STOP must dispose all active input subtrees before resume", mountedAfterStop, disposedAfterStop)
        assertEquals("STOP must detach the real child Popup Window before resume", 0, popupWindowsAfterStop)
        assertEquals("STOP must release all viewport display lists", 0, layersAfterStop)
        val resumedAt = System.nanoTime()
        scenario.moveToState(Lifecycle.State.RESUMED)
        waitUntil("Resumed parent must retain its settled open state") { settled(probe.samples, resumedAt, true) }
        assertEquals("Resume must not replay the old child Popup Window", 0, popupWindowCounts(scenario).roots)
        assertEquals(0, probe.choices.get())
    }

    private class AnchorProbe {
        val mainY = AtomicReference(Float.NaN)
        val viewport = AtomicReference(Rect())
        val samples: MutableList<HeaderSample> = Collections.synchronizedList(mutableListOf())
        val layers: MutableList<DesktopDisclosureObservation> = Collections.synchronizedList(mutableListOf())
        val overflow = AtomicInteger()
        val viewportHasFocus = AtomicBoolean()
        var requestKeyboardFocus: (() -> Boolean)? = null
    }

    private fun anchorWindow(preference: String = "off", bodyHeight: Int = 220, requireMain: Boolean = true,
        body: (ActivityScenario<MainActivity>, AnchorProbe) -> Unit) = withViewIds {
        val probe = AnchorProbe()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                primaryWindowId = -1 // Identified from the actual public tagged app-window node below.
                activity.window.decorView.getWindowVisibleDisplayFrame(Rect().also { visibleFrame.set(it) })
                activity.setContent {
                    TokenTheme(true, LanguageChoice.ENGLISH, AppearancePreferences(reduceMotion = preference)) {
                        CompositionLocalProvider(LocalDesktopDisclosureObserver provides { sample ->
                            synchronized(probe.layers) {
                                if (probe.layers.size < 2048) probe.layers.add(sample) else probe.overflow.incrementAndGet()
                            }
                        }) {
                        val density = LocalDensity.current
                        val offset = with(density) { 80.dp.roundToPx() }
                        val scroll = rememberLazyListState(1, offset)
                        var section by remember { mutableStateOf("general") }
                        val anchor = rememberDesktopSettingsScrollAnchor(scroll, section)
                        val general = rememberDesktopDisclosureMotion(section == "general", DesktopDisclosureKind.SETTINGS_SECTION)
                        val main = rememberDesktopDisclosureMotion(section == "main", DesktopDisclosureKind.SETTINGS_SECTION)
                        val focus = remember { FocusRequester() }
                        val inputMode = LocalInputModeManager.current
                        SideEffect { probe.requestKeyboardFocus = {
                            inputMode.requestInputMode(InputMode.Keyboard) && focus.requestFocus()
                        } }
                        LaunchedEffect(Unit) { focus.requestFocus() }
                        Column(Modifier.fillMaxSize().background(Color(0xFF101010)).semantics { testTagsAsResourceId = true }) {
                            Spacer(Modifier.height(90.dp))
                            LazyColumn(Modifier.fillMaxWidth().height(340.dp).testTag("anchor-viewport")
                                .cancelSettingsAnchorOnInteraction(anchor).focusRequester(focus)
                                .onFocusChanged { probe.viewportHasFocus.set(it.hasFocus) }
                                .onGloballyPositioned {
                                    val location = IntArray(2); activity.window.decorView.getLocationOnScreen(location)
                                    val root = it.positionInRoot()
                                    probe.viewport.set(Rect(root.x.roundToInt() + location[0], root.y.roundToInt() + location[1],
                                        root.x.roundToInt() + location[0] + it.size.width, root.y.roundToInt() + location[1] + it.size.height))
                                }.drawWithContent {
                                    drawContent()
                                    val y = probe.mainY.get()
                                    if (y.isFinite()) synchronized(probe.samples) {
                                        if (probe.samples.size < 2048) probe.samples.add(HeaderSample(System.nanoTime(), y,
                                            general.pose.value.heightFraction, main.pose.value.heightFraction))
                                        else probe.overflow.incrementAndGet()
                                    }
                                }, state = scroll) {
                                item(key = "public-leading-space") { Spacer(Modifier.height(600.dp)) }
                                listOf("general", "main").forEach { id ->
                                    item(key = id) {
                                        val state = if (id == "general") general else main
                                        Column {
                                            Row(Modifier.fillMaxWidth().height(48.dp).settingsHeaderPosition(anchor, id, state)
                                                .onGloballyPositioned { if (id == "main") probe.mainY.set(it.positionInRoot().y) }
                                                .clickable { anchor.toggle(id, section != id) { section = if (section == id) "" else id } }
                                                .testTag("anchor-header-" + id)) { Text("Public header " + id) }
                                            DesktopAccordion(section == id, "anchor-body-" + id, state = state) {
                                                Box(Modifier.fillMaxWidth().height(bodyHeight.dp).background(Color.Magenta))
                                            }
                                        }
                                    }
                                }
                                item(key = "public-trailing-space") { Spacer(Modifier.height(600.dp)) }
                            }
                        }
                        }
                    }
                }
            }
            waitUntil("Real anchor fixture must be scrolled and visible") {
                node("anchor-viewport") != null && (!requireMain || (node("anchor-header-main") != null && probe.mainY.get().isFinite()))
            }
            body(scenario, probe)
            assertEquals("Anchor draw probe must remain bounded", 0, probe.overflow.get())
        } finally { scenario.close() }
    }

    private fun headerSamples(probe: AnchorProbe) = synchronized(probe.samples) { probe.samples.toList() }
    private fun waitAnchorMidpoint(probe: AnchorProbe, began: Long) {
        waitUntil("Header workload must enter the actual accordion transition") {
            headerSamples(probe).any { it.at >= began && it.general in .4f.. .8f && it.main > 0f && it.main < 1f }
        }
    }
    private fun finishAnchor(probe: AnchorProbe, began: Long) {
        waitUntil("Both real section transitions must finish") {
            headerSamples(probe).any { it.at >= began && it.general == 0f && it.main == 1f }
        }
        // Give the original 360 ms anchor deadline time to expire, without
        // changing controller duration or replacing the real Window clock.
        val end = began + DesktopDisclosureMotionTokens.settingsAnchorDurationMs * 1_000_000L
        while (System.nanoTime() < end) SystemClock.sleep(4)
        instrumentation.waitForIdleSync()
    }

    @Test fun settingsHeaderRemainsPinnedInEveryActualDraw() = anchorWindow { _, probe ->
        val pinnedY = probe.mainY.get()
        val began = tap("anchor-header-main")
        waitAnchorMidpoint(probe, began)
        finishAnchor(probe, began)
        val frames = headerSamples(probe).filter { it.at >= began && (it.general > 0f || it.main < 1f) }
        assertTrue("Pin acceptance must contain real intermediate header draws", frames.isNotEmpty())
        val maximumDrift = frames.maxOf { abs(it.mainY - pinnedY) }
        // The source corrects > 0.5 CSS px. Android's integer layout/scroll
        // quantization may cost at most one additional physical pixel.
        File(directory, "disclosure-boundary-header-pin-public.json").writeText(JSONObject()
            .put("intermediateHeaderDraws", frames.size).put("maximumDriftPx", maximumDrift)
            .put("finalDriftPx", abs(probe.mainY.get() - pinnedY)).toString(2))
        assertTrue("Clicked header must not visibly jump while the upper section collapses", maximumDrift <= 1.5f)
        assertTrue("Final clicked header must stay at the same position", abs(probe.mainY.get() - pinnedY) <= 1.5f)
    }

    @Test fun reducedSettingsHeaderPinsOnItsFirstSettledDraw() = anchorWindow("on") { _, probe ->
        val pinnedY = probe.mainY.get()
        val began = tap("anchor-header-main")
        finishAnchor(probe, began)
        val frames = headerSamples(probe).filter { it.at >= began }
        assertTrue("Reduced anchor workload must produce a real draw", frames.isNotEmpty())
        assertTrue("Reduced sections must never draw an intermediate height",
            frames.none { (it.general > 0f && it.general < 1f) || (it.main > 0f && it.main < 1f) })
        val firstSettled = requireNotNull(frames.firstOrNull { it.general == 0f && it.main == 1f })
        File(directory, "disclosure-boundary-header-pin-reduced-public.json").writeText(JSONObject()
            .put("firstSettledDriftPx", abs(firstSettled.mainY - pinnedY))
            .put("finalDriftPx", abs(probe.mainY.get() - pinnedY)).toString(2))
        assertTrue("Single-pass reduced anchoring must pin the first settled Window draw",
            abs(firstSettled.mainY - pinnedY) <= 1.5f)
        assertTrue("Reduced anchoring must retain the clicked header position", abs(probe.mainY.get() - pinnedY) <= 1.5f)
    }

    @Test fun anOpenAccordionReleasesItsLayerWhenScrolledCompletelyOffscreen() = anchorWindow(bodyHeight = 700, requireMain = false) { _, probe ->
        waitUntil("Initial visible open accordion must have a recorded viewport layer") { retainedLayers(probe.layers).isNotEmpty() }
        val viewport = Rect(probe.viewport.get())
        val draws = observations(probe.layers).filter { it.phase == DesktopDisclosureObservationPhase.DRAW }
        assertTrue("A long body's display list must be clipped to its actual Lazy viewport", draws.all {
            it.cacheWidthPx <= viewport.width() + 1 && it.cacheHeightPx <= viewport.height() + 1
        })
        val before = retainedLayers(probe.layers)
        val scrolledAt = System.nanoTime()
        repeat(3) {
            if (retainedLayers(probe.layers).isNotEmpty()) {
                assertTrue("Native accessibility scroll must be accepted",
                    requireNotNull(node("anchor-viewport")).performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
                SystemClock.sleep(400)
            }
        }
        waitUntil("Fully offscreen accordion must release its old layer") { retainedLayers(probe.layers).isEmpty() }
        val releases = observations(probe.layers).filter { it.atNanos >= scrolledAt &&
            it.phase == DesktopDisclosureObservationPhase.RELEASE }.map { it.identity }.toSet()
        assertTrue("Each previously visible layer must emit a release", releases.containsAll(before))
        assertTrue("Scroll must actually remove the body from the viewport", node("anchor-body-general") == null)
        File(directory, "disclosure-boundary-offscreen-layers-public.json").writeText(JSONObject()
            .put("initialRetainedLayers", before.size).put("releasedLayerIdentities", releases.size)
            .put("maximumCacheWidthPx", draws.maxOf { it.cacheWidthPx }).put("maximumCacheHeightPx", draws.maxOf { it.cacheHeightPx })
            .put("layersAfterScroll", retainedLayers(probe.layers).size).toString(2))
    }

    private fun dragUp(rect: Rect) {
        val down = SystemClock.uptimeMillis()
        val x = rect.exactCenterX(); val y = rect.top + rect.height() * .8f
        fun deliver(action: Int, time: Long, atY: Float) {
            val event = MotionEvent.obtain(down, time, action, x, atY, 0)
            try { assertTrue("Native drag event must be accepted", automation.injectInputEvent(event, true)) }
            finally { event.recycle() }
        }
        deliver(MotionEvent.ACTION_DOWN, down, y)
        repeat(4) { step -> deliver(MotionEvent.ACTION_MOVE, down + (step + 1) * 16L, y - (step + 1) * 20f) }
        SystemClock.sleep(80)
        deliver(MotionEvent.ACTION_UP, SystemClock.uptimeMillis(), y - 80f)
    }
    private fun wheelUp(rect: Rect) {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
            x = rect.exactCenterX(); y = rect.exactCenterY(); setAxisValue(MotionEvent.AXIS_VSCROLL, -2f)
        })
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, properties, coordinates,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
        try { assertTrue("Native wheel event must be accepted", automation.injectInputEvent(event, true)) }
        finally { event.recycle() }
    }
    private fun keyPageDown() {
        // The two-argument KeyEvent constructor leaves zero timestamps. Native
        // input dispatch can reject those as stale before Compose sees the key.
        val down = SystemClock.uptimeMillis()
        val first = KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, 0, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD)
        val acceptedDown = automation.injectInputEvent(first, true)
        val last = KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PAGE_DOWN, 0, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD)
        val acceptedUp = automation.injectInputEvent(last, true)
        File(directory, "disclosure-boundary-key-delivery-public.json").writeText(JSONObject()
            .put("identifiedHostWindowId", primaryWindowId).put("downTimeMs", down)
            .put("keyCode", KeyEvent.KEYCODE_PAGE_DOWN).put("source", InputDevice.SOURCE_KEYBOARD)
            .put("downAccepted", acceptedDown).put("upAccepted", acceptedUp).toString(2))
        assertTrue("Native scroll key down must be accepted", acceptedDown)
        assertTrue("Native scroll key up must be accepted", acceptedUp)
    }

    private fun anchorCancellation(kind: String, event: (Rect) -> Unit) = anchorWindow { scenario, probe ->
        val pinnedY = probe.mainY.get()
        val began = tap("anchor-header-main")
        waitAnchorMidpoint(probe, began)
        if (kind == "key") {
            var requested = false; var windowFocused = false
            scenario.onActivity { activity ->
                windowFocused = activity.hasWindowFocus()
                requested = probe.requestKeyboardFocus?.invoke() == true
            }
            File(directory, "disclosure-boundary-key-focus-public.json").writeText(JSONObject()
                .put("identifiedHostWindowId", primaryWindowId).put("windowHasFocus", windowFocused)
                .put("focusRequested", requested).put("viewportHasFocus", probe.viewportHasFocus.get()).toString(2))
            assertTrue("Genuine native keyboard delivery requires the actual Activity Window focus", windowFocused)
            assertTrue("The public Lazy viewport must accept keyboard focus", requested)
            waitUntil("The public Lazy viewport must hold focus before native PageDown") { probe.viewportHasFocus.get() }
        }
        event(Rect(probe.viewport.get()))
        finishAnchor(probe, began)
        assertTrue("A native " + kind + " scroll must take control from the header pin", abs(probe.mainY.get() - pinnedY) > 4f)
        File(directory, "disclosure-boundary-header-cancel-" + kind + "-public.json").writeText(JSONObject()
            .put("input", kind).put("finalHeaderDisplacementPx", abs(probe.mainY.get() - pinnedY)).toString(2))
    }
    @Test fun settingsHeaderPinYieldsToNativePointerGesture() = anchorCancellation("pointer", ::dragUp)
    @Test fun settingsHeaderPinYieldsToNativeWheel() = anchorCancellation("wheel", ::wheelUp)
    @Test fun settingsHeaderPinYieldsToNativeScrollKey() = anchorCancellation("key") { keyPageDown() }
}
