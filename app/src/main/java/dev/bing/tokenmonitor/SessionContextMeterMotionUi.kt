package dev.bing.tokenmonitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private class SessionContextRunner(var job:Job?=null) {fun cancel() {job?.cancel();job=null}}

/** One small mounted gauge. Only its Canvas reads frames; the percentage Text stays outside. */
@Composable internal fun DesktopSessionContextMeter(id:String,percent:Int,track:Color,fill:Color,preference:String) {
    val target=percent.coerceIn(0,100)/100f
    val motion=remember(id) {SessionContextMeterMotion(target)}
    val output=remember(motion) {mutableStateOf(motion.frame)}
    val runner=remember(motion) {SessionContextRunner()}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    var inViewport by remember(motion) {mutableStateOf(false)}
    val view=LocalView.current
    val clock=LocalDesktopAnimationClock.current
    val scale=LocalHeadlineMotionScale.current ?: DesktopMotionPreference.scale(preference,LocalDesktopSystemMotionScale.current)
    val latestScale=rememberUpdatedState(scale)
    val scope=rememberCoroutineScope()
    val observer=LocalHeadlineRenderObserver.current
    SideEffect {observer?.invoke("session-context-meter:$id")}

    DisposableEffect(lifecycle,motion) {
        val listener=LifecycleEventObserver {_,_->
            foreground=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if(!foreground) {runner.cancel();motion.stop();output.value=motion.frame}
        }
        lifecycle.addObserver(listener)
        onDispose {lifecycle.removeObserver(listener);runner.cancel();motion.stop()}
    }
    // Observe visibility/lifecycle boundaries in composition; frames remain draw-only.
    val shown=foreground && inViewport
    SideEffect {
        if(motion.render(target,shown,clock.nowNanos(),scale)) runner.cancel()
        output.value=motion.frame
        if(shown && scale>0f && motion.frame.running) {
            if(runner.job?.isActive!=true) runner.job=scope.launch(start=CoroutineStart.UNDISPATCHED) {
                while(motion.frame.running) withFrameNanos {now->
                    motion.advance(now,latestScale.value);output.value=motion.frame
                }
            }
        } else runner.cancel()
    }
    Canvas(Modifier.size(20.dp,3.dp).testTag("session-context-meter-$id").onGloballyPositioned {coordinates->
        // Same ancestor/window clipping policy as DesktopDisclosureMotionUi;
        // prefetched or scrolled-off lazy rows never keep a finite frame loop.
        val visible=if(coordinates.isAttached) coordinates.boundsInWindow()
            .intersect(Rect(0f,0f,view.width.toFloat(),view.height.toFloat())) else Rect.Zero
        val next=visible.width>.5f && visible.height>.5f
        if(inViewport!=next) inViewport=next
        if(!next) {runner.cancel();motion.stop();output.value=motion.frame}
    }) {
        drawRoundRect(track,cornerRadius=CornerRadius(2.dp.toPx()))
        drawRoundRect(fill,size=Size(size.width*output.value.scale,size.height),cornerRadius=CornerRadius(2.dp.toPx()))
    }
}
