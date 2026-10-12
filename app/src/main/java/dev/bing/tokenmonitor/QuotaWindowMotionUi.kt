package dev.bing.tokenmonitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal val QuotaMotionFill=SemanticsPropertyKey<Float>("QuotaMotionFill")
internal val QuotaMotionGlow=SemanticsPropertyKey<Float>("QuotaMotionGlow")
internal val QuotaMotionRunning=SemanticsPropertyKey<Boolean>("QuotaMotionRunning")
private class QuotaMotionRunner(var job:Job?=null) {fun cancel() {job?.cancel();job=null}}

/** Stable above route branches; frames are read only by Canvas/one native percentage leaf. */
@Composable internal fun rememberQuotaWindowMotion(targets:List<QuotaWindowTarget>?,connectionKey:Long,
    mainViewKey:String,renderToken:Any,visible:Boolean,preference:String="system"):State<QuotaWindowMotionFrame> {
    val model=remember {QuotaWindowMotion()}
    val output=remember {mutableStateOf(model.frame)}
    val runner=remember {QuotaMotionRunner()}
    val clock=LocalDesktopAnimationClock.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    val scale=LocalHeadlineMotionScale.current ?: DesktopMotionPreference.scale(preference,LocalDesktopSystemMotionScale.current)
    val latestScale=rememberUpdatedState(scale)
    val scope=rememberCoroutineScope()
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->
            foreground=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if(!foreground) {runner.cancel();model.stop();output.value=model.frame}
        }
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);runner.cancel();model.stop()}
    }
    // Read this boundary during composition so STOP/resume subscribes this
    // owner even when the route and payload stay unchanged. No frame read.
    val shown=visible && foreground
    SideEffect {
        val replaced=model.render(targets,connectionKey,mainViewKey,renderToken,clock.nowNanos(),shown,shown && scale>0f,scale)
        output.value=model.frame
        if(replaced) runner.cancel()
        if(shown && scale>0f && model.running) {
            if(runner.job?.isActive!=true) runner.job=scope.launch(start=CoroutineStart.UNDISPATCHED) {
                while(model.running) withFrameNanos {now->model.advance(now,latestScale.value);output.value=model.frame}
            }
        } else runner.cancel()
    }
    return output
}

@Composable internal fun QuotaMotionMeter(target:QuotaWindowTarget?,percent:Double,color:Color,tone:Float,
    motion:State<QuotaWindowMotionFrame>?,modifier:Modifier=Modifier) {
    Canvas(modifier.fillMaxSize().semantics {
        val frame=motion?.value
        this[QuotaMotionFill]=if(target!=null && frame!=null) frame.fill(target) else (percent/100).coerceIn(0.0,1.0).toFloat()
        this[QuotaMotionGlow]=if(target!=null) frame?.meters?.get(target.key)?.glow ?: 0f else 0f
        this[QuotaMotionRunning]=frame?.running==true
    }) {
        val frame=motion?.value
        val fraction=(if(target!=null && frame!=null) frame.fill(target) else (percent/100).toFloat()).coerceIn(0f,1f)
        val width=size.width*fraction
        if(width>0f) {
            val glow=if(target!=null) frame?.meters?.get(target.key)?.glow ?: 0f else 0f
            // Source highlight is a child of the toned fill: mix before applying its opacity.
            val painted=if(glow>0f) lerp(color,Color.White,.58f*glow) else color
            drawRoundRect(painted.copy(alpha=tone),size=Size(width,size.height),cornerRadius=CornerRadius(3.dp.toPx()))
        }
    }
}

/** Rounded strings isolate layout invalidations; money/raw numbers retain the existing natural Text. */
@Composable internal fun QuotaMotionValue(row:ProviderQuotaRow,motion:State<QuotaWindowMotionFrame>?,
    modifier:Modifier=Modifier,style:TextStyle) {
    val masked=LocalLimitDisplay.current.maskEmails
    val static=LimitDisplay.text(row.value,masked)
    val target=row.motion
    val text by remember(target,static,motion) {derivedStateOf {
        val percent=if(target?.percentSuffix!=null) motion?.value?.meters?.get(target.key)?.percent else null
        if(percent==null) static else "${Math.round(percent)}% ${target!!.percentSuffix}"
    }}
    Text(text,modifier=modifier,style=style)
}
