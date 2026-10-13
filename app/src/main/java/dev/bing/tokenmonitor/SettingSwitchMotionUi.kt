package dev.bing.tokenmonitor

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal val SettingSwitchPosition=SemanticsPropertyKey<Float>("SettingSwitchPosition")
internal val SettingSwitchPlaying=SemanticsPropertyKey<Boolean>("SettingSwitchPlaying")

@Composable internal fun rememberSettingSwitchMotion(checked:Boolean,enabled:Boolean,on:Color,off:Color,onBorder:Color,offBorder:Color,onThumb:Color,offThumb:Color):SettingSwitchPose {
    val target=SettingSwitchPose(if(checked) 1f else 0f,if(checked) on else off,if(checked) onBorder else offBorder,if(checked) onThumb else offThumb)
    val motion=remember {SettingSwitchMotion(target)}
    var pose by remember {mutableStateOf(target)}
    val scale=LocalHeadlineMotionScale.current ?: LocalDesktopMotionScale.current
    val systemScale=LocalDesktopSystemMotionScale.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    val latestTarget by rememberUpdatedState(target)
    DisposableEffect(lifecycle,motion) {
        val listener=LifecycleEventObserver {_,_->
            foreground=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if(!foreground) {motion.settle(latestTarget);pose=motion.pose}
        }
        lifecycle.addObserver(listener)
        onDispose {lifecycle.removeObserver(listener);motion.settle(latestTarget)}
    }
    LaunchedEffect(target,enabled,scale,systemScale,foreground) {
        motion.retarget(target,enabled && foreground && scale>0f && systemScale>0f)
        pose=motion.pose
        if(motion.pose.running) {
            val began=withFrameNanos {it}
            val duration=180.0*1_000_000.0*scale
            while(motion.pose.running && foreground) withFrameNanos {now->
                motion.frame(((now-began)/duration).toFloat());pose=motion.pose
            }
        }
    }
    return pose
}
