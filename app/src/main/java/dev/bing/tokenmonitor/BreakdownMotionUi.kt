package dev.bing.tokenmonitor

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale

private class BreakdownRunner(var job:Job?=null) {
    fun cancel() {job?.cancel();job=null}
}

/** Keep above conditional main pages. The caller passes this State without reading its frames. */
@Composable internal fun rememberBreakdownMotion(
    targets:List<BreakdownRowSpec>?,connectionKey:Long,mainViewKey:String,periodKey:String,
    periodEvent:BreakdownPeriodEvent,visible:Boolean,preference:String="system"
):State<BreakdownMotionFrame> {
    val motion=remember {BreakdownMotion()}
    val output=remember {mutableStateOf(motion.frame)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    val clock=LocalDesktopAnimationClock.current
    val scale=LocalHeadlineMotionScale.current ?: DesktopMotionPreference.scale(preference,LocalDesktopSystemMotionScale.current)
    val latestScale=rememberUpdatedState(scale)
    val runner=remember {BreakdownRunner()}
    val scope=rememberCoroutineScope()
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->
            foreground=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if(!foreground) {runner.cancel();motion.stop();output.value=motion.frame}
        }
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);runner.cancel();motion.settle()}
    }
    // Observe lifecycle boundaries in composition; frame values stay in leaves.
    val shown=visible && foreground
    SideEffect {
        val replaced=motion.render(targets,connectionKey,mainViewKey,periodKey,periodEvent,clock.nowNanos(),
            animate=shown && scale>0f,shown=shown,durationScale=scale)
        output.value=motion.frame
        if(replaced) runner.cancel()
        if(shown && scale>0f && motion.running) {
            if(runner.job?.isActive!=true) runner.job=scope.launch(start=CoroutineStart.UNDISPATCHED) {
                while(motion.running) withFrameNanos {now->
                    motion.advance(now,latestScale.value)
                    output.value=motion.frame
                }
            }
        } else runner.cancel()
    }
    return output
}

/** Only this small native Text subscribes to integer changes; entry fractions do not recompose it. */
@Composable internal fun BreakdownAnimatedNumber(
    target:BigInteger,motion:State<BreakdownMotionFrame>?,key:String,modifier:Modifier=Modifier,
    style:TextStyle,color:Color=Color.Unspecified,locale:Locale?=null,maxLines:Int=Int.MAX_VALUE
) {
    val language=locale ?: LocalResources.current.configuration.locales[0]
    val format=remember(language) {NumberFormat.getIntegerInstance(language)}
    val displayed=remember(target,motion,key) {derivedStateOf {motion?.value?.number(key,target) ?: target}}
    val observer=LocalHeadlineRenderObserver.current
    SideEffect {observer?.invoke("breakdown-number:$key")}
    Text(format.format(displayed.value),modifier,style=style,color=color,maxLines=maxLines)
}
