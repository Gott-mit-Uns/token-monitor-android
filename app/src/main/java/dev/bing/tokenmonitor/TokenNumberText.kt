package dev.bing.tokenmonitor

import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal val LocalTokenNumberAnimation=staticCompositionLocalOf {"count"}
internal val LocalTokenRollFrameObserver=staticCompositionLocalOf<((Boolean,Int)->Unit)?> {null}

/** The real Text retains selection/accessibility. Frame state is read only by drawing, never by its owner/list. */
@Composable internal fun TokenNumberText(
    text:String,modifier:Modifier=Modifier,style:TextStyle,color:Color=Color.Unspecified,
    softWrap:Boolean=true,maxLines:Int=Int.MAX_VALUE
) {
    val mode=LocalTokenNumberAnimation.current
    val observer=LocalTokenRollFrameObserver.current
    if(mode=="count") {SideEffect {observer?.invoke(false,0)};Text(text,modifier,style=style,color=color,softWrap=softWrap,maxLines=maxLines);return}
    val ink=if(color!=Color.Unspecified) color else if(style.color!=Color.Unspecified) style.color else LocalContentColor.current
    val motion=remember(mode) {TokenDigitRoll(text)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    var onScreen by remember {mutableStateOf(false)}
    val system=LocalDesktopSystemMotionScale.current
    val scale=LocalHeadlineMotionScale.current ?: LocalDesktopMotionScale.current
    val enabled=mode=="roll" && foreground && onScreen && system>0f && scale>0f && text.all {it in "0123456789, .\u202f\u00a0−-—"}
    // Token numbers can span >1 line on large-font narrow windows: keep the native wrapped Text there.
    // Breakdown's natural wrapping is also retained when its measured reading cannot fit.
    val measurer=rememberTextMeasurer()
    val density=LocalDensity.current
    val glyphs=remember(style,ink,measurer) {("0123456789, .\u202f\u00a0−-—").toSet().associateWith {measurer.measure(AnnotatedString(it.toString()),style.copy(color=ink))}}
    val digitWidth=remember(glyphs) {('0'..'9').maxOf {glyphs.getValue(it).size.width}.toFloat()}
    val columnCount=maxOf(motion.length,text.length)
    val padded=text.padEnd(columnCount,' ')
    val widths=remember(padded,glyphs,digitWidth) {padded.map {if(it.isDigit() || it==' ') digitWidth else (glyphs[it]?.size?.width ?: digitWidth.toInt()).toFloat()}}
    val reserveWidth=widths.sum()
    val textLayout=remember {arrayOfNulls<androidx.compose.ui.text.TextLayoutResult>(1)}
    var tick by remember {mutableFloatStateOf(1f)}
    val clock=LocalDesktopAnimationClock.current
    DisposableEffect(lifecycle,motion) {
        val observer=LifecycleEventObserver {_,_->
            foreground=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if(!foreground) {motion.settle();tick=1f}
        }
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);motion.settle()}
    }
    LaunchedEffect(text,enabled,mode,scale,clock) {
        val previous=motion.target.filter(Char::isDigit).toBigIntegerOrNull()
        val next=text.filter(Char::isDigit).toBigIntegerOrNull()
        val started=motion.retarget(text,next!=null && previous!=null && next>=previous,enabled && previous!=null && next!=null)
        // A duration-scale/clock change cancels the old effect even when its target is unchanged.
        // Settle its retained columns rather than leaving a stale running timeline behind.
        if(!started && motion.running) motion.settle()
        tick=if(started) 0f else 1f
        if(started) {
            val start=clock.nowNanos()
            while(motion.running) withFrameNanos {now->
                motion.frame(((now-start).toDouble()/(650_000_000.0*scale)).toFloat())
                tick=motion.progress
            }
        }
    }
    // Reserve endpoint/maximum-digit width for the whole transition, including proportional custom fonts.
    // Parent constraints win. A narrow/wrapped reading uses Text's native shaping and clipping rules.
    val draw=if(mode=="roll") Modifier.widthIn(min=(reserveWidth/density.density).dp).drawWithContent {
        val fraction=tick
        observer?.invoke(enabled && motion.running,motion.columns.sumOf {it.size})
        if(reserveWidth>size.width+.5f || textLayout[0]?.lineCount!=1 || !text.all {it in glyphs}) drawContent()
        else clipRect {
            var x=0f
            val lineHeight=glyphs.getValue('0').size.height.toFloat()
            val columns=if(enabled && motion.target==text && motion.columns.size==widths.size) motion.columns else padded.map {listOf(RollingGlyph(it,0f,0f))}
            for((i,column) in columns.withIndex()) {
                for(layer in column) glyphs[layer.char]?.let {layout->
                    val y=(textLayout[0]?.firstBaseline ?: layout.firstBaseline)-layout.firstBaseline
                    drawText(layout,topLeft=Offset(x+(widths[i]-layout.size.width)/2f,y+layer.position(fraction)*lineHeight))
                }
                x+=widths[i]
            }
        }
    } else Modifier
    Text(text,modifier.onGloballyPositioned {coordinates->
        val bounds=coordinates.boundsInWindow()
        onScreen=bounds.width>0f && bounds.height>0f
    }.then(draw),style=style,color=color,softWrap=softWrap,maxLines=maxLines,onTextLayout={textLayout[0]=it})
}
