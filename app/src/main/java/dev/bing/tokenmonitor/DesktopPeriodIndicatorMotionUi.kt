package dev.bing.tokenmonitor

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal val DesktopPeriodIndicatorIndex=SemanticsPropertyKey<Float>("DesktopPeriodIndicatorIndex")
internal val DesktopPeriodIndicatorAnimating=SemanticsPropertyKey<Boolean>("DesktopPeriodIndicatorAnimating")
/** Fixed numeric retarget evidence; no page payload, label, id or input is exposed. */
internal data class DesktopPeriodIndicatorRenderObservation(val targetIndex:Int,val atNanos:Long,
    val frame:DesktopPeriodIndicatorFrame)
internal val LocalDesktopPeriodIndicatorRenderObserver=
    staticCompositionLocalOf<((DesktopPeriodIndicatorRenderObservation)->Unit)?> {null}

internal fun Modifier.desktopPeriodIndicator(motion:State<DesktopPeriodIndicatorFrame>,overlay:Color,line:Color):Modifier =
    testTag("hub-period-tabs").semantics {
        this[DesktopPeriodIndicatorIndex]=motion.value.index
        this[DesktopPeriodIndicatorAnimating]=motion.value.running
    }.drawBehind {
        val gap=2.dp.toPx()
        val width=((size.width-2*gap)/3f).coerceAtLeast(0f)
        val topLeft=Offset(motion.value.index*(width+gap),0f)
        val bounds=Size(width,minOf(22.dp.toPx(),size.height))
        val radius=CornerRadius(6.dp.toPx())
        drawRoundRect(overlay.copy(alpha=.06f),topLeft,bounds,radius)
        drawRoundRect(line.copy(alpha=.13f),topLeft,bounds,radius,style=Stroke(1.dp.toPx()))
    }

/** The returned State is read only while drawing the tabs' shared selection layer. */
@Composable internal fun rememberDesktopPeriodIndicator(index:Int,visible:Boolean,preference:String):State<DesktopPeriodIndicatorFrame> {
    val motion=remember {DesktopPeriodIndicatorMotion(index)}
    val output=remember {mutableStateOf(motion.frame)}
    val clock=LocalDesktopAnimationClock.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    val scale=DesktopMotionPreference.scale(preference,LocalDesktopSystemMotionScale.current)
    val scope=rememberCoroutineScope()
    val runner=remember {arrayOfNulls<Job>(1)}
    val observer=LocalDesktopPeriodIndicatorRenderObserver.current
    fun cancel() {runner[0]?.cancel();runner[0]=null}
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->
            foreground=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if(!foreground) {cancel();motion.stop();output.value=motion.frame}
        }
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);cancel();motion.stop()}
    }
    // Establish a composition subscription to lifecycle visibility. This is a
    // boundary read; moving selection frames stay in draw/semantics only.
    val shown=visible && foreground
    SideEffect {
        val now=clock.nowNanos()
        if(motion.render(index,shown,now,scale)) cancel()
        val rendered=motion.frame
        output.value=rendered
        observer?.let {callback->runCatching {callback(DesktopPeriodIndicatorRenderObservation(index,now,rendered))}}
        if(rendered.running && shown) {
            if(runner[0]?.isActive!=true) runner[0]=scope.launch(start=CoroutineStart.UNDISPATCHED) {
                while(motion.frame.running) withFrameNanos {now->motion.advance(now);output.value=motion.frame}
            }
        } else cancel()
    }
    return output
}
