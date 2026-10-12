package dev.bing.tokenmonitor

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private class DashboardChartRunner(var job: Job? = null) {
    fun cancel() { job?.cancel(); job = null }
}

/** Hold above conditional panes/settings; ONLY Canvas draw reads the returned per-frame State. */
@Composable internal fun rememberDashboardChartMotion(
    model: DashboardTrendModel?, renderEventKey: Any?, motionRevision: Int,
    reason: DashboardMotionReason, visible: Boolean, preference: String = "system"
): State<DashboardChartFrame> {
    val motion = remember { DashboardChartMotion() }
    val output = remember { mutableStateOf(motion.frame) }
    val targets = remember(model) { model?.let { DashboardChartMotion.targets(it) } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val shown = visible && foreground
    val clock = LocalDesktopAnimationClock.current
    val scale = DesktopMotionPreference.scale(preference, LocalDesktopSystemMotionScale.current)
    val latestScale = rememberUpdatedState(scale)
    val runner = remember { DashboardChartRunner() }
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (!foreground) {
                runner.cancel(); motion.settle(); output.value = motion.frame
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); runner.cancel(); motion.settle() }
    }
    SideEffect {
        val replaced = motion.render(targets, renderEventKey, motionRevision, reason, clock.nowNanos(),
            animate = shown && scale > 0f, consumeReason = shown, durationScale = scale)
        if (!shown || scale <= 0f) motion.settle()
        output.value = motion.frame
        if (replaced) runner.cancel()
        if (motion.running && motion.hasVisibleMotion && shown && scale > 0f) {
            if (runner.job?.isActive != true) runner.job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                while (motion.running && motion.hasVisibleMotion) withFrameNanos { now ->
                    motion.advance(now, latestScale.value); output.value = motion.frame
                }
            }
        } else runner.cancel()
    }
    return output
}
