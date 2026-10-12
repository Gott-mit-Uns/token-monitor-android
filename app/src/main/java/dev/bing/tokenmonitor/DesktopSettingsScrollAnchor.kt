package dev.bing.tokenmonitor

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Remeasurement
import androidx.compose.ui.layout.RemeasurementModifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

internal val LocalDesktopSettingsScrollAnchor = staticCompositionLocalOf<DesktopSettingsScrollAnchor?> { null }

/** Original app.js:743-807. Only the seven fixed settings-header positions are kept. */
@Stable
internal class DesktopSettingsScrollAnchor(private val scroll: LazyListState,
    private val scope: CoroutineScope) {
    private val order = listOf("general", "main", "appearance", "tools", "limits", "subscriptions", "sync")
    private val positions = mutableMapOf<String, Float>()
    private var job: Job? = null
    private data class Pin(val id: String, val offset: Float, val reduced: Boolean, val deadline: Long)
    private var pin: Pin? = null
    private var heldScroll: ScrollScope? = null
    private var remeasurement: Remeasurement? = null
    private var correcting = false
    // Attached to the existing Lazy viewport; no new host, snapshot state or
    // per-frame owner read is introduced by the anchor.
    val remeasurementModifier: Modifier = object : RemeasurementModifier {
        override fun onRemeasurementAvailable(remeasurement: Remeasurement) {
            this@DesktopSettingsScrollAnchor.remeasurement = remeasurement
        }
    }
    var expandedSection = ""
    var motionScale = 1f
    var clock: DesktopAnimationClock = DesktopAnimationClock { System.nanoTime() }

    fun position(id: String, y: Float, motion: DesktopDisclosureMotionState) {
        if (id !in order) return
        positions[id] = y
        val request = pin ?: return
        val activeScroll = heldScroll ?: return
        if (request.id != id || correcting) return
        if (clock.nowNanos() >= request.deadline) { cancel(); return }
        // Reduced motion performs exactly one pass against the NEW settled
        // geometry. A pre-layout frame still has the previous section's pose.
        if (request.reduced) {
            val pose = motion.pose.value
            if (!motion.controller.targetOpen || pose.running || pose.heightFraction != 1f) return
        }
        val drift = y - request.offset
        if (abs(drift) > .5f) {
            val measure = remeasurement ?: return
            correcting = true
            try {
                val consumed = activeScroll.scrollBy(drift)
                // Lazy can optimize a small scroll into placement invalidation.
                // Force the public viewport measure before this notification
                // returns, so that the next Window draw sees the corrected y.
                if (consumed != 0f) measure.forceRemeasure()
            } finally { correcting = false }
        }
        if (request.reduced && pin === request) cancel()
    }

    fun cancel() {
        // Clear the synchronous input/geometry gate before cancelling a job.
        // Its finally block cannot erase a newer toggle's mutation scope.
        pin = null
        heldScroll = null
        val previous = job
        job = null
        previous?.cancel()
    }
    fun clear() { cancel(); positions.clear(); remeasurement = null }
    fun toggle(id: String, expanding: Boolean, mutate: () -> Unit) {
        cancel()
        val currentIndex = order.indexOf(expandedSection)
        val targetIndex = order.indexOf(id)
        val offset = positions[id]
        val shouldPin = expanding && currentIndex >= 0 && targetIndex > currentIndex && offset != null
        if (!shouldPin) { mutate(); return }
        val reduced = motionScale <= 0f
        // A reduced pass needs a finite chance to observe the settled layout;
        // multiplying its escape deadline by zero would expire before measure.
        val duration = DesktopDisclosureMotionTokens.settingsAnchorDurationMs *
            1_000_000.0 * (if (reduced) 1f else motionScale.coerceAtLeast(0f))
        val request = Pin(id, offset ?: return, reduced, clock.nowNanos() + duration.toLong())
        pin = request
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                // Request Default priority before the section mutation below.
                // Native UserInput may reject or interrupt this finite lease.
                scroll.scroll(MutatePriority.Default) {
                    if (pin !== request) return@scroll
                    heldScroll = this
                    // Frame callbacks enforce only the original finite deadline;
                    // geometric correction runs on freshly positioned headers.
                    while (pin === request && clock.nowNanos() < request.deadline) withFrameNanos { }
                }
            } finally {
                if (pin === request) { pin = null; heldScroll = null; job = null }
            }
        }
        // A higher-priority input can reject the anchor lease. The user's
        // ordinary section selection must still happen exactly once.
        mutate()
    }
}

@Composable
internal fun rememberDesktopSettingsScrollAnchor(scroll: LazyListState,
    expandedSection: String): DesktopSettingsScrollAnchor {
    val scope = rememberCoroutineScope()
    val anchor = remember(scroll, scope) { DesktopSettingsScrollAnchor(scroll, scope) }
    val clock = LocalDesktopAnimationClock.current
    val scale = LocalHeadlineMotionScale.current ?: LocalDesktopMotionScale.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    SideEffect { anchor.expandedSection = expandedSection; anchor.motionScale = scale; anchor.clock = clock }
    DisposableEffect(lifecycle, anchor) {
        val listener = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) anchor.cancel() }
        lifecycle.addObserver(listener)
        onDispose { lifecycle.removeObserver(listener); anchor.clear() }
    }
    return anchor
}

internal fun Modifier.settingsHeaderPosition(anchor: DesktopSettingsScrollAnchor?, id: String,
    motion: DesktopDisclosureMotionState) =
    if (anchor == null) this else onGloballyPositioned { anchor.position(id, it.positionInRoot().y, motion) }

internal fun Modifier.cancelSettingsAnchorOnInteraction(anchor: DesktopSettingsScrollAnchor): Modifier =
    then(anchor.remeasurementModifier).pointerInput(anchor) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press || event.type == PointerEventType.Scroll) anchor.cancel()
            }
        }
    }.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key in setOf(Key.DirectionUp, Key.DirectionDown,
                Key.PageUp, Key.PageDown, Key.MoveHome, Key.MoveEnd, Key.Spacebar, Key.Tab)) anchor.cancel()
        false
    }
