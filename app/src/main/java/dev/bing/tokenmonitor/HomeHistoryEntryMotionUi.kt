package dev.bing.tokenmonitor

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class HomeHistoryEntryDisplay internal constructor(
    val frame: State<HomeHistoryEntryFrame>,
    val identity: Int,
    private val sequence: () -> Int,
    private val ready: (HomeChartProjection, HomeHeatColumns) -> Unit
) {
    val entrySequence: Int get() = sequence()
    fun heatViewport(projection: HomeChartProjection, columns: HomeHeatColumns) = ready(projection, columns)
}

internal val LocalHomeHistoryEntryMotion = staticCompositionLocalOf<HomeHistoryEntryDisplay?> { null }

private class HomeHistoryEntryRunner(var job: Job? = null) {
    var entrySequence = 0
    fun cancel() { job?.cancel(); job = null }
}

/** Keep above conditional pages. Only heat/line draws may read the returned frame State. */
@Composable internal fun rememberHomeHistoryEntryMotion(
    projection: HomeChartProjection?,
    mainViewKey: String,
    visible: Boolean,
    renderEventKey: Any? = projection,
    preference: String = "system"
): HomeHistoryEntryDisplay {
    val motion = remember { HomeHistoryEntryMotion() }
    val output = remember { mutableStateOf(motion.frame) }
    val runner = remember { HomeHistoryEntryRunner() }
    val scope = rememberCoroutineScope()
    val clock = LocalDesktopAnimationClock.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val shown = visible && foreground
    val scale = DesktopMotionPreference.scale(preference, LocalDesktopSystemMotionScale.current)
    val latestScale = rememberUpdatedState(scale)
    val latestProjection = rememberUpdatedState(projection)
    val latestShown = rememberUpdatedState(shown)
    val observer by rememberUpdatedState(LocalHomeHistoryEntryObserver.current)
    fun observe(phase: HomeHistoryEntryObservationPhase, now: Long) {
        observer.homeEntryObserve { HomeHistoryEntryObservation(phase, now, System.identityHashCode(motion),
            runner.entrySequence, System.identityHashCode(latestProjection.value), motion.frame,
            visible = phase != HomeHistoryEntryObservationPhase.STOP && latestShown.value) }
    }
    val hasTrendStroke = remember(projection?.trend) {
        projection?.trend?.zipWithNext()?.any { (before, after) -> before.tokens != null && after.tokens != null } == true
    }
    fun runFrames() {
        if (motion.running && runner.job?.isActive != true) {
            runner.job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                while (motion.running) withFrameNanos { now ->
                    motion.advance(now, latestScale.value)
                    output.value = motion.frame
                    observe(HomeHistoryEntryObservationPhase.STATE, now)
                }
            }
        } else if (!motion.running) runner.cancel()
    }

    val onViewport = rememberUpdatedState<(HomeChartProjection, HomeHeatColumns) -> Unit> { content, columns ->
        if (motion.heatViewport(content, columns, clock.nowNanos(), scale)) {
            runner.entrySequence++
            output.value = motion.frame
            observe(HomeHistoryEntryObservationPhase.ENTRY_START, clock.nowNanos())
            observe(HomeHistoryEntryObservationPhase.STATE, clock.nowNanos())
            runner.cancel()
            runFrames()
        }
    }
    val display = remember { HomeHistoryEntryDisplay(output, System.identityHashCode(motion), { runner.entrySequence }) {
        content, columns -> onViewport.value(content, columns)
    } }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (!foreground) {
                runner.cancel()
                motion.stop()
                output.value = motion.frame
                observe(HomeHistoryEntryObservationPhase.STOP, clock.nowNanos())
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            runner.cancel()
            motion.stop()
        }
    }

    SideEffect {
        val changedView = motion.mainView(mainViewKey)
        val replaced = motion.render(projection, projection?.cells?.isNotEmpty() == true, shown, hasTrendStroke, scale, renderEventKey)
        if (changedView || replaced || !shown || scale <= 0f) runner.cancel()
        if (!foreground) motion.stop()
        output.value = motion.frame
        observe(HomeHistoryEntryObservationPhase.STATE, clock.nowNanos())
        runFrames()
    }
    return display
}
