package dev.bing.tokenmonitor

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt
import kotlin.math.ceil

internal enum class DesktopDisclosureObservationPhase { STATE, DRAW, RELEASE }
internal val DisclosureHeightFraction = SemanticsPropertyKey<Float>("DisclosureHeightFraction")
internal val DisclosureOpacity = SemanticsPropertyKey<Float>("DisclosureOpacity")
internal val DisclosureChevronFraction = SemanticsPropertyKey<Float>("DisclosureChevronFraction")
internal val DisclosureMotionPlaying = SemanticsPropertyKey<Boolean>("DisclosureMotionPlaying")
internal val DisclosureAcceptsInput = SemanticsPropertyKey<Boolean>("DisclosureAcceptsInput")
internal val DisclosureRetainsPixels = SemanticsPropertyKey<Boolean>("DisclosureRetainsPixels")
/** Fixed kind and numeric fields only. No row id, model label, URL or payload is exposed. */
internal data class DesktopDisclosureObservation(
    val kind: DesktopDisclosureKind,
    val identity: Int,
    val phase: DesktopDisclosureObservationPhase,
    val atNanos: Long,
    val open: Boolean,
    val acceptsInput: Boolean,
    val retained: Boolean,
    val pose: DesktopDisclosurePose,
    val drawnHeightPx: Float = 0f,
    val cacheWidthPx: Int = 0,
    val cacheHeightPx: Int = 0
)
// Null in the application. Optional Window tests use bounded storage and real clocks.
internal val LocalDesktopDisclosureObserver = staticCompositionLocalOf<((DesktopDisclosureObservation) -> Unit)?> { null }

@Stable
internal class DesktopDisclosureMotionState internal constructor(
    initiallyOpen: Boolean,
    internal val kind: DesktopDisclosureKind,
    style: DesktopDisclosureStyle
) {
    internal val controller = DesktopDisclosureMotion(initiallyOpen, style)
    private val frames = mutableStateOf(controller.pose)
    val pose: State<DesktopDisclosurePose> get() = frames
    var retained by mutableStateOf(initiallyOpen); private set
    var foreground by mutableStateOf(true); internal set
    internal val identity = System.identityHashCode(this)
    internal fun publish() {
        frames.value = controller.pose
        retained = controller.retained
    }
}

@Composable
internal fun rememberDesktopDisclosureMotion(
    open: Boolean,
    kind: DesktopDisclosureKind = DesktopDisclosureKind.GENERIC,
    style: DesktopDisclosureStyle = DesktopDisclosureStyle.ACCORDION
): DesktopDisclosureMotionState {
    val state = remember(kind, style) { DesktopDisclosureMotionState(open, kind, style) }
    val scale = LocalHeadlineMotionScale.current ?: LocalDesktopMotionScale.current
    val clock = LocalDesktopAnimationClock.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestOpen by rememberUpdatedState(open)
    val observer by rememberUpdatedState(LocalDesktopDisclosureObserver.current)
    fun observe(now: Long) {
        observer?.let { callback -> runCatching {
            callback(DesktopDisclosureObservation(kind, state.identity, DesktopDisclosureObservationPhase.STATE,
                now, latestOpen, latestOpen && state.foreground, state.retained, state.pose.value))
        } }
    }
    DisposableEffect(lifecycle, state) {
        state.foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val listener = LifecycleEventObserver { _, _ ->
            state.foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (!state.foreground) {
                state.controller.settle(latestOpen)
                state.publish()
                observe(clock.nowNanos())
            }
        }
        lifecycle.addObserver(listener)
        onDispose {
            lifecycle.removeObserver(listener)
            state.controller.settle(latestOpen)
        }
    }
    LaunchedEffect(open, scale, state.foreground, clock, state) {
        val began = clock.nowNanos()
        state.controller.retarget(open, state.foreground && scale > 0f, began, scale)
        state.publish()
        observe(began)
        while (state.controller.pose.running && state.foreground) {
            withFrameNanos { now ->
                state.controller.frame(now)
                state.publish()
                observe(now)
            }
        }
    }
    return state
}

/**
 * A clipped natural-height child. Only this layout and its graphics layer read
 * the frame. Open content draws live; a viewport-bounded display list retains
 * the last actual draw only for exit. The active subtree is removed immediately,
 * which also disposes child Popup/Dialog windows and their input/IME sessions. Saveable holders at the call
 * sites preserve ordinary local choices; plain credential drafts are never saved.
 */
@Composable
internal fun DesktopDisclosureContent(
    open: Boolean,
    state: DesktopDisclosureMotionState,
    modifier: Modifier = Modifier,
    tag: String,
    panelTranslation: Boolean = false,
    content: @Composable () -> Unit
) {
    val inputEnabled = open && state.foreground
    val focus = LocalFocusManager.current
    var containsFocus by remember { mutableStateOf(false) }
    val observer = LocalDesktopDisclosureObserver.current
    val clock = LocalDesktopAnimationClock.current
    val context = LocalGraphicsContext.current
    val view = LocalView.current
    val latestObserver by rememberUpdatedState(observer)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val cache = remember(context, state, lifecycle) { DisclosureExitLayer(context) {
        latestObserver?.let { callback -> runCatching {
            callback(DesktopDisclosureObservation(state.kind, state.identity,
                DesktopDisclosureObservationPhase.RELEASE, clock.nowNanos(), state.controller.targetOpen,
                // ON_STOP registry state is authoritative even if this later
                // listener releases before the controller flips foreground.
                state.controller.targetOpen && state.foreground &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED), state.retained, state.pose.value))
        } }
    } }
    val measured = remember { DisclosureMeasuredSize() }
    DisposableEffect(lifecycle, cache) {
        val listener = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) cache.release() }
        lifecycle.addObserver(listener)
        onDispose { lifecycle.removeObserver(listener); cache.release() }
    }
    // The finite controller's completion is the exit deadline. No retained layer
    // survives its final frame, a zero-motion close, disposal or Activity STOP.
    LaunchedEffect(open, state.retained, state.foreground) {
        if (!state.foreground || (!open && !state.retained)) cache.release()
    }
    LaunchedEffect(inputEnabled) {
        if (!inputEnabled && containsFocus) focus.clearFocus(force = true)
    }
    if (!open && !state.retained) return
    val blocked = if (inputEnabled) Modifier else Modifier.onPreviewKeyEvent { true }.pointerInput(Unit) {
            awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
        }
    val metadata: SemanticsPropertyReceiver.() -> Unit = {
        val pose = state.pose.value
        testTag = tag
        this[DisclosureHeightFraction] = pose.heightFraction
        this[DisclosureOpacity] = pose.opacity
        this[DisclosureChevronFraction] = pose.chevronFraction
        this[DisclosureMotionPlaying] = pose.running
        this[DisclosureAcceptsInput] = inputEnabled
        this[DisclosureRetainsPixels] = state.retained
        if (!inputEnabled) hideFromAccessibility()
    }
    val semantics = if (inputEnabled) Modifier.semantics(properties = metadata)
        else Modifier.clearAndSetSemantics(properties = metadata)
    Layout(modifier = modifier.fillMaxWidth().clipToBounds().testTag(tag)
        .then(semantics).then(blocked)
        .onGloballyPositioned { coordinates ->
            // boundsInWindow clips to ancestor clips, including the Lazy viewport.
            // Intersect once more with the actual window surface; never use a long
            // accordion's natural height as a GPU layer allocation size.
            val visible = coordinates.boundsInWindow().intersect(Rect(0f, 0f, view.width.toFloat(), view.height.toFloat()))
            if (visible.width > 0f && visible.height > 0f) {
                val local = coordinates.windowToLocal(visible.topLeft)
                cache.visible = Rect(local, visible.size)
            } else {
                cache.visible = Rect.Zero
                cache.release()
            }
        }
        .graphicsLayer {
            val pose = state.pose.value
            alpha = pose.opacity
            translationY = if (panelTranslation) -4.dp.toPx() * (1f - pose.heightFraction) else 0f
        }
        .drawWithContent {
            if (inputEnabled) {
                val visible = cache.visible
                if (state.foreground && visible.width > 0f && visible.height > 0f) {
                    cache.record(visible, this) { this@drawWithContent.drawContent() }
                }
                // Lazy children can move or invalidate their own layers without
                // redrawing this ancestor. Replaying its clipped exit display
                // list while open freezes that earlier viewport and can leave
                // newly exposed settings blank. Only closing uses the snapshot;
                // active content always follows Compose's live drawing tree.
                drawContent()
            } else cache.draw(this)
            observer?.let { callback -> runCatching {
                callback(DesktopDisclosureObservation(state.kind, state.identity,
                    DesktopDisclosureObservationPhase.DRAW, clock.nowNanos(), open, inputEnabled,
                    state.retained, state.pose.value, size.height, cache.recordedSize.width, cache.recordedSize.height))
            } }
        },
        content = {
            // Removing this branch closes independently hosted child windows now;
            // clearAndSetSemantics alone would not disable a Popup's own Window.
            if (inputEnabled) Column(Modifier.fillMaxWidth().onFocusChanged { containsFocus = it.hasFocus }.focusGroup()) { content() }
        }) { measurables, constraints ->
        val child = measurables.singleOrNull()?.measure(constraints.copy(minHeight = 0))
        if (child != null) measured.natural = IntSize(child.width, child.height)
        val fraction = state.pose.value.heightFraction
        val height = constraints.constrainHeight((measured.natural.height * fraction).roundToInt().coerceAtLeast(0))
        layout(constraints.constrainWidth(measured.natural.width), height) { child?.placeRelative(0, 0) }
    }
}

// Measurement owns this value; assigning it never schedules a composition.
private class DisclosureMeasuredSize { var natural = IntSize.Zero }

/** At most one layer per open visible disclosure, clipped to its actual viewport. */
private class DisclosureExitLayer(private val context: GraphicsContext, private val released: () -> Unit) {
    var visible = Rect.Zero
    private var captured = Rect.Zero
    private var layer: GraphicsLayer? = null
    var recordedSize = IntSize.Zero; private set
    fun record(rect: Rect, scope: androidx.compose.ui.graphics.drawscope.ContentDrawScope,
        draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
        val size = IntSize(ceil(rect.width.toDouble()).toInt(), ceil(rect.height.toDouble()).toInt())
        if (size.width <= 0 || size.height <= 0) return
        val destination = layer ?: context.createGraphicsLayer().also { layer = it }
        destination.clip = true
        with(scope) { destination.record(size = size) { translate(-rect.left, -rect.top, block = draw) } }
        captured = rect
        recordedSize = size
    }
    fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope) {
        val source = layer ?: return
        with(scope) { translate(captured.left, captured.top) { drawLayer(source) } }
    }
    fun release() {
        val existed = layer != null
        layer?.let(context::releaseGraphicsLayer)
        layer = null
        captured = Rect.Zero
        recordedSize = IntSize.Zero
        if (existed) released()
    }
}

/** Saveable field/scroll choices survive a completed collapse; plain draft secrets do not enter a Saver. */
@Composable
internal fun DesktopAccordion(
    open: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    kind: DesktopDisclosureKind = DesktopDisclosureKind.GENERIC,
    state: DesktopDisclosureMotionState = rememberDesktopDisclosureMotion(open, kind),
    content: @Composable () -> Unit
) {
    val saved = rememberSaveableStateHolder()
    DesktopDisclosureContent(open, state, modifier, tag) {
        saved.SaveableStateProvider("details", content)
    }
}

/** Main views retain their original direct switching; this animates only the original settings panel. */
@Composable
internal fun DesktopSettingsPane(open: Boolean, content: @Composable () -> Unit) {
    val state = rememberDesktopDisclosureMotion(open, DesktopDisclosureKind.SETTINGS_PANEL,
        DesktopDisclosureStyle.SETTINGS_PANEL)
    val saved = rememberSaveableStateHolder()
    DesktopDisclosureContent(open, state, tag = "hub-settings-panel-motion", panelTranslation = true) {
        saved.SaveableStateProvider("settings", content)
    }
}
