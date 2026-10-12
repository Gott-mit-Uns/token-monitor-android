package dev.bing.tokenmonitor

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal val MenuMotionFraction=SemanticsPropertyKey<Float>("MenuMotionFraction")
internal val MenuMotionPlaying=SemanticsPropertyKey<Boolean>("MenuMotionPlaying")
internal val MenuAcceptsInput=SemanticsPropertyKey<Boolean>("MenuAcceptsInput")
internal data class MenuMotionFrame(val value:Float,val running:Boolean)

/** Finite display-only frames. Leaving the foreground dismisses rather than replays. */
@Composable internal fun rememberMenuMotion(open:Boolean,enabled:Boolean,dismiss:()->Unit):MenuMotionFrame {
    val motion=remember {DesktopMenuMotion()}
    var frame by remember {mutableStateOf(MenuMotionFrame(0f,false))}
    val scale=LocalHeadlineMotionScale.current ?: LocalDesktopMotionScale.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    val latestOpen by rememberUpdatedState(open);val latestDismiss by rememberUpdatedState(dismiss)
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->
            foreground=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if(!foreground) {motion.settle(false);frame=MenuMotionFrame(0f,false);if(latestOpen) latestDismiss()}
        }
        lifecycle.addObserver(observer);onDispose {lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(open,enabled,scale,foreground) {
        motion.retarget(open && foreground,enabled && foreground && scale>0f)
        frame=MenuMotionFrame(motion.value,motion.running)
        if(motion.running) {
            val start=withFrameNanos {it}
            val duration=motion.durationMs*1_000_000.0*scale
            while(motion.running) withFrameNanos {now->
                motion.frame(((now-start)/duration).toFloat())
                frame=MenuMotionFrame(motion.value,motion.running)
            }
        }
    }
    return frame
}
