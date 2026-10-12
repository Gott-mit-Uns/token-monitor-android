package dev.bing.tokenmonitor

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal val SessionPulsePlaying=SemanticsPropertyKey<Boolean>("SessionPulsePlaying")
internal data class SessionPulseFrame(val glow:SessionGlow=SessionGlow(),val playing:Boolean=false)

@Composable private fun rememberSessionPulse(at:Long,running:Boolean,preference:String):SessionPulseFrame {
    val memory=remember {SessionPulseMemory()};var frame by remember {mutableStateOf(SessionPulseFrame())}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    var previousForeground by remember {mutableStateOf(foreground)}
    val scale=LocalHeadlineMotionScale.current ?: DesktopMotionPreference.scale(preference,LocalDesktopSystemMotionScale.current)
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->
            foreground=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if(!foreground) {memory.clear();previousForeground=false;frame=SessionPulseFrame()}
        }
        lifecycle.addObserver(observer);onDispose {lifecycle.removeObserver(observer);memory.clear()}
    }
    LaunchedEffect(at,running,foreground,scale) {
        val grew=memory.observe(at,running);val resume=foreground && !previousForeground;previousForeground=foreground
        frame=SessionPulseFrame()
        if(!foreground || resume || !running || !grew || scale<=0f) return@LaunchedEffect
        val start=withFrameNanos {it};frame=SessionPulseFrame(SessionRowPulse.frame(0f),true)
        var progress=0f
        while(progress<1f) withFrameNanos {time->
            progress=((time-start).toDouble()/(SessionRowPulse.durationMillis*1_000_000.0*scale)).toFloat().coerceIn(0f,1f)
            frame=SessionPulseFrame(SessionRowPulse.frame(progress),progress<1f)
        }
    }
    return frame
}

/** Fixed-size dot; only its halo changes. One frame loop per genuine timestamp advance. */
@Composable internal fun SessionRunningDot(id:String,at:Long,running:Boolean,preference:String,modifier:Modifier=Modifier) {
    val frame=rememberSessionPulse(at,running,preference)
    val success=LocalDesktopSuccessColor.current;val panel=LocalDesktopPanelColor.current
    if(running) Canvas(modifier.testTag("session-running-$id").semantics {this[SessionPulsePlaying]=frame.playing}) {
        val center=Offset(size.width/2,size.height/2);val dot=2.5.dp.toPx();val radius=dot+frame.glow.blur.dp.toPx()/2
        drawCircle(Brush.radialGradient(listOf(success.copy(alpha=frame.glow.opacity*.5f),Color.Transparent),center,radius),radius,center)
        drawCircle(panel,dot+1.5.dp.toPx(),center)
        drawCircle(success,dot,center)
    }
}
