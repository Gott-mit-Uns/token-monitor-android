package dev.bing.tokenmonitor

import androidx.compose.runtime.*
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.SemanticsPropertyKey

internal val LocalDesktopMotionScale=staticCompositionLocalOf {1f}
internal val StatusMotionPlaying=SemanticsPropertyKey<Boolean>("StatusMotionPlaying")
internal val StatusMotionPhase=SemanticsPropertyKey<Float>("StatusMotionPhase")

@Composable private fun rememberStatusForeground():Boolean {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->foreground=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)}
        lifecycle.addObserver(observer);onDispose {lifecycle.removeObserver(observer)}
    }
    return foreground
}

/** The running indicator alone requests frames; lazy-list disposal also cancels it. */
@Composable internal fun rememberSessionSpinner(running:Boolean):Double {
    val foreground=rememberStatusForeground();val scale=LocalHeadlineMotionScale.current ?: LocalDesktopMotionScale.current
    var elapsed by remember {mutableDoubleStateOf(0.0)}
    LaunchedEffect(running,foreground,scale) {
        elapsed=0.0
        if(!running || !foreground || scale<=0f) return@LaunchedEffect
        val start=withFrameNanos {it}
        while(true) withInfiniteAnimationFrameNanos {time->elapsed=(time-start)/1_000_000.0/scale}
    }
    return if(running && foreground && scale>0f) elapsed else 0.0
}

/** Fresh sample revisions pulse once; mode changes and equal heartbeats do not restart. */
@Composable internal fun rememberRateFlash(sample:RateSample?):RateFlashFrame {
    val foreground=rememberStatusForeground();val scale=LocalHeadlineMotionScale.current ?: LocalDesktopMotionScale.current
    var previousForeground by remember {mutableStateOf(foreground)}
    var renderedRevision by remember {mutableLongStateOf(0L)}
    var frame by remember {mutableStateOf(RateFlashFrame())}
    LaunchedEffect(sample?.revision,sample?.idle,foreground,scale) {
        val resume=foreground && !previousForeground;previousForeground=foreground
        val fresh=sample!=null && !sample.idle && sample.revision!=renderedRevision
        if(sample!=null && !sample.idle) renderedRevision=sample.revision
        frame=RateFlashFrame()
        if(!foreground || resume || !fresh || scale<=0f) return@LaunchedEffect
        val start=withFrameNanos {it};frame=DesktopRateFlash.frame(0f)
        var progress=0f
        while(progress<1f) withFrameNanos {time->
            progress=((time-start)/(DesktopRateFlash.durationMillis*1_000_000.0*scale)).toFloat().coerceIn(0f,1f)
            frame=DesktopRateFlash.frame(progress)
        }
    }
    return if(foreground && scale>0f && sample?.idle==false) frame else RateFlashFrame()
}
