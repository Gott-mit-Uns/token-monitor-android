package dev.bing.tokenmonitor

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private class TrendsBarRunner(var job: Job? = null) {
    fun cancel() { job?.cancel(); job = null }
}

/**
 * Keep this owner above conditional pages. Only Canvas draws read the returned State.
 * Settings/details preserve mainViewKey; renderEventKey changes for actual data renders.
 */
@Composable internal fun rememberTrendsBarMotion(
    points: List<TrendsMainPoint>?,
    renderEventKey: Any?,
    mainViewKey: String,
    visible: Boolean,
    preference: String = "system"
): State<TrendsBarFrame> {
    val motion = remember { TrendsBarMotion() }
    val output = remember { mutableStateOf(motion.frame) }
    val targets = remember(points) { points?.let { TrendsBarMotion.targets(it) } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    // Lifecycle changes must invalidate this owner; reading only in SideEffect would not subscribe.
    val shown = visible && foreground
    val animationClock = LocalDesktopAnimationClock.current
    val scale = DesktopMotionPreference.scale(preference, LocalDesktopSystemMotionScale.current)
    val latestScale = rememberUpdatedState(scale)
    val runner = remember { TrendsBarRunner() }
    val scope = rememberCoroutineScope()

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (!foreground) {
                runner.cancel()
                motion.settle()
                output.value = motion.frame
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            runner.cancel()
            motion.settle()
        }
    }

    // Applying targets at commit avoids briefly painting a new target before its entry scale.
    // The SideEffect reads no per-frame State and does not make its owner subscribe to frames.
    SideEffect {
        motion.mainView(mainViewKey)
        val replaced = motion.render(targets, renderEventKey, animationClock.nowNanos(),
            animate = shown && scale > 0f, consumeEntry = shown, durationScale = scale)
        if (!shown || scale <= 0f) motion.settle()
        output.value = motion.frame
        if (replaced) runner.cancel()
        if (motion.running && motion.hasVisibleMotion && shown && scale > 0f) {
            if (runner.job?.isActive != true) {
                runner.job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    while (motion.running && motion.hasVisibleMotion) {
                        withFrameNanos { now ->
                            motion.advance(now, latestScale.value)
                            output.value = motion.frame
                        }
                    }
                }
            }
        } else runner.cancel()
    }
    return output
}
