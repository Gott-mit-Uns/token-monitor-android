package dev.bing.tokenmonitor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
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

internal val BreakdownRowAlpha=SemanticsPropertyKey<Float>("BreakdownRowAlpha")
internal val BreakdownRowTranslationY=SemanticsPropertyKey<Float>("BreakdownRowTranslationY")
internal val BreakdownRowAnimating=SemanticsPropertyKey<Boolean>("BreakdownRowAnimating")

@Stable internal class BreakdownRowPlacementHandle internal constructor(
    internal val model:BreakdownRowPlacementMotion,
    internal val frame:MutableState<BreakdownRowPlacementFrame>
) {
    internal var positioned:(String,Float,Any)->Unit={_,_,_->}
    internal fun detached(key:String) {model.detached(key);frame.value=model.frame}
}
private class RowPlacementRunner(var job:Job?=null) {fun cancel() {job?.cancel();job=null}}

/** Same owner lifetime/causes as bars; no row/table subscribes to per-frame values. */
@Composable internal fun rememberBreakdownRowPlacementMotion(rows:List<BreakdownRowSpec>?,connectionKey:Long,
    mainViewKey:String,periodKey:String,event:BreakdownPeriodEvent,renderToken:Any,visible:Boolean,
    preference:String="system"):BreakdownRowPlacementHandle {
    val model=remember {BreakdownRowPlacementMotion()}
    val frame=remember {mutableStateOf(model.frame)}
    val handle=remember {BreakdownRowPlacementHandle(model,frame)}
    val runner=remember {RowPlacementRunner()}
    val clock=LocalDesktopAnimationClock.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    val scale=LocalHeadlineMotionScale.current ?: DesktopMotionPreference.scale(preference,LocalDesktopSystemMotionScale.current)
    val latestScale=rememberUpdatedState(scale)
    val offset=with(LocalDensity.current) {7.dp.toPx()}
    val scope=rememberCoroutineScope()
    fun startIfNeeded() {
        if(model.running && latestScale.value>0f && runner.job?.isActive!=true)
            runner.job=scope.launch(start=CoroutineStart.UNDISPATCHED) {
                while(model.running) withFrameNanos {now->model.advance(now,latestScale.value);frame.value=model.frame}
            }
    }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->
            foreground=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if(!foreground) {runner.cancel();model.stop();frame.value=model.frame}
        }
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);runner.cancel();model.stop()}
    }
    // Resume must acknowledge the settled state before a later real row change.
    val shown=visible && foreground
    SideEffect {
        val replaced=model.render(rows,connectionKey,mainViewKey,periodKey,event,renderToken,clock.nowNanos(),
            shown,shown && scale>0f,scale,offset)
        frame.value=model.frame
        if(replaced) runner.cancel()
        if(shown && scale>0f && model.running) startIfNeeded() else runner.cancel()
        handle.positioned={key,y,token->
            if(model.positioned(key,y,token,clock.nowNanos(),latestScale.value)) {
                frame.value=model.frame
                if(shown && latestScale.value>0f && model.running) startIfNeeded() else runner.cancel()
            }
        }
    }
    return handle
}

/** Outer box keeps unanimated layout geometry; inner layer moves only the painted/hit-tested row. */
@Composable internal fun DesktopBreakdownMotionRow(key:String,renderToken:Any,motion:BreakdownRowPlacementHandle,
    content:@Composable ()->Unit) {
    DisposableEffect(motion,key) {onDispose {motion.detached(key)}}
    Box(Modifier.fillMaxWidth().testTag("breakdown-row-layout-$key").onGloballyPositioned {coordinates->
        if(coordinates.isAttached) motion.positioned(key,coordinates.positionInWindow().y,renderToken)
    }) {
        // Semantics live inside the layer so their native bounds follow the
        // painted row, while the outer box still records untransformed layout.
        Box(Modifier.fillMaxWidth().graphicsLayer {
            val row=motion.frame.value.row(key)
            alpha=row.alpha;translationY=row.translationY
        }.testTag("breakdown-row-layer-$key").semantics {
            val f=motion.frame.value
            this[BreakdownRowAlpha]=f.row(key).alpha
            this[BreakdownRowTranslationY]=f.row(key).translationY
            this[BreakdownRowAnimating]=f.running && key in f.layers
        }) {content()}
    }
}
