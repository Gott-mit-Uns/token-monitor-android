package dev.bing.tokenmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

internal data class DesktopMenuOption(val id:String,val label:String,val tag:String,val icon:Int?=null,val enabled:Boolean=true)

private data class DesktopMenuSpace(val visibleBounds:IntRect,val heightPx:Int)

/** Visible display-frame edges converted to the anchor's containing-window coordinates. */
private fun desktopMenuVisibleBounds(view:android.view.View,fallback:IntSize):IntRect {
    val frame=android.graphics.Rect()
    val onScreen=IntArray(2);val inWindow=IntArray(2)
    view.getWindowVisibleDisplayFrame(frame)
    view.getLocationOnScreen(onScreen);view.getLocationInWindow(inWindow)
    val originX=onScreen[0]-inWindow[0];val originY=onScreen[1]-inWindow[1]
    return if(frame.width()>0 && frame.height()>0)
        IntRect(frame.left-originX,frame.top-originY,frame.right-originX,frame.bottom-originY)
    else IntRect(0,0,fallback.width,fallback.height)
}
private data class DesktopMenuGeometry(val widthPx:Int,val maxHeightPx:Int)

/** Measure the complete labels using the same resolved family, size and density as Text. */
@Composable internal fun rememberDesktopLabelWidths(labels:List<String>,style:TextStyle):List<Int> {
    val density=LocalDensity.current
    val textMeasurer=rememberTextMeasurer()
    return remember(labels,style,density,textMeasurer) {
        labels.map {label->
            val layout=textMeasurer.measure(label,style,softWrap=false)
            maxOf(layout.size.width,kotlin.math.ceil(layout.multiParagraph.maxIntrinsicWidth.toDouble()).toInt())
        }
    }
}

/** Original popup treatment shared by navigation, periods and local option menus. */
@Composable internal fun DesktopOptionMenu(expanded:Boolean,options:List<DesktopMenuOption>,selected:String?=null,tag:String,width:Dp=200.dp,above:Boolean=false,wrapNavigation:Boolean=false,horizontalNavigation:Boolean=false,animate:Boolean=false,maxHeight:Dp?=280.dp,compactNavigation:Boolean=false,dismiss:()->Unit,choose:(String)->Unit) {
    val frame=rememberMenuMotion(expanded,animate,dismiss)
    if(!expanded && !frame.running && frame.value<=0f) return
    var lastExpandedOptions by remember {mutableStateOf(options)}
    val renderedOptions=if(expanded) options else lastExpandedOptions
    SideEffect {if(expanded && lastExpandedOptions!=options) lastExpandedOptions=options}
    val colors=MaterialTheme.colorScheme
    val density=LocalDensity.current
    val containerSize=LocalWindowInfo.current.containerSize
    val hostView=LocalView.current
    val labelStyle=quotaTextStyle(if(compactNavigation) 12 else 11)
    val textMeasurer=rememberTextMeasurer()
    val labelWidths=rememberDesktopLabelWidths(renderedOptions.map {it.label},labelStyle)
    val gutter=with(density) {8.dp.roundToPx()}
    val gap=with(density) {(if(above) 6.dp else 4.dp).roundToPx()}
    val chrome=with(density) {2*(4.dp.roundToPx()+7.dp.roundToPx())} // Match each actual padding edge rounding.
    val iconSpace=with(density) {14.dp.roundToPx()+7.dp.roundToPx()} // Match actual icon and gap rounding.
    var availableSpace by remember(density,above,maxHeight) {mutableStateOf<DesktopMenuSpace?>(null)}
    var lastExpandedGeometry by remember(density) {mutableStateOf<DesktopMenuGeometry?>(null)}
    val windowWidth=(availableSpace?.visibleBounds?.width ?: containerSize.width).coerceAtLeast(1)
    val windowHeight=(availableSpace?.visibleBounds?.height ?: containerSize.height).coerceAtLeast(1)
    val naturalWidth=renderedOptions.indices.maxOfOrNull {index->labelWidths[index]+chrome+if(renderedOptions[index].icon!=null) iconSpace else 0} ?: chrome
    val widthPx=maxOf(with(density) {width.roundToPx()},with(density) {48.dp.roundToPx()},naturalWidth)
        .coerceAtMost((windowWidth-2*gutter).coerceAtLeast(1))
    val naturalHeight=remember(renderedOptions,labelStyle,density,textMeasurer,widthPx,chrome,iconSpace,compactNavigation) {
        val rowMinimum=with(density) {48.dp.roundToPx()}
        val outerPadding=with(density) {2*4.dp.roundToPx()}
        val rowGap=0
        outerPadding+rowGap*(renderedOptions.size-1).coerceAtLeast(0)+renderedOptions.sumOf {option->
            val textWidth=(widthPx-chrome-if(option.icon!=null) iconSpace else 0).coerceAtLeast(1)
            val textHeight=textMeasurer.measure(option.label,labelStyle,softWrap=true,constraints=Constraints(maxWidth=textWidth)).size.height
            maxOf(rowMinimum,textHeight)
        }
    }
    val explicitHeight=maxHeight?.let {with(density) {it.roundToPx().coerceAtLeast(1)}}
    val desiredHeight=explicitHeight?.let {minOf(naturalHeight,it)} ?: naturalHeight
    val availableHeight=(availableSpace?.heightPx ?: (windowHeight-2*gutter)).coerceAtLeast(1)
    val calculatedGeometry=DesktopMenuGeometry(widthPx,minOf(desiredHeight,availableHeight).coerceAtLeast(1))
    // Dismissal retains its last box through the existing exit motion. In particular, a new
    // preferred page/label cannot resize an already non-interactive closing surface.
    val geometry=if(expanded) calculatedGeometry else lastExpandedGeometry ?: calculatedGeometry
    SideEffect {if(expanded && lastExpandedGeometry!=calculatedGeometry) lastExpandedGeometry=calculatedGeometry}
    val menuWidth=with(density) {geometry.widthPx.toDp()}
    val menuMaxHeight=with(density) {geometry.maxHeightPx.toDp()}
    val shape=RoundedCornerShape(7.dp)
    var search by remember {mutableStateOf("")}
    var searchAt by remember {mutableLongStateOf(0)}
    val fallback=remember {FocusRequester()}
    val focus=remember(renderedOptions) {renderedOptions.map {FocusRequester()}}
    var actualAbove by remember(above) {mutableStateOf(above)}
    val position=remember(gap,gutter,above,desiredHeight,expanded,hostView) {object:PopupPositionProvider {
        override fun calculatePosition(anchorBounds:IntRect,windowSize:IntSize,layoutDirection:LayoutDirection,popupContentSize:IntSize):IntOffset {
            // windowSize has a visible size, but an edge-to-edge anchor also needs the
            // display frame's top/left origin. Read actual bounds for system bars/IME.
            val visibleBounds=desktopMenuVisibleBounds(hostView,windowSize)
            val belowSpace=(visibleBounds.bottom-gutter-anchorBounds.bottom-gap).coerceAtLeast(0)
            val aboveSpace=(anchorBounds.top-gap-visibleBounds.top-gutter).coerceAtLeast(0)
            // Use the natural/capped height, not the last constrained height. This makes
            // the side and space decision independent of the following remeasurement.
            val preferredAbove=if(above) desiredHeight<=aboveSpace || aboveSpace>=belowSpace
                else desiredHeight>belowSpace && aboveSpace>belowSpace
            val opensAbove=if(expanded) preferredAbove else actualAbove
            if(expanded) {
                if(actualAbove!=opensAbove) actualAbove=opensAbove
                val next=DesktopMenuSpace(visibleBounds,if(opensAbove) aboveSpace else belowSpace)
                if(availableSpace!=next) availableSpace=next
            }
            val y=if(opensAbove) anchorBounds.top-popupContentSize.height-gap else anchorBounds.bottom+gap
            val x=if(layoutDirection==LayoutDirection.Rtl) anchorBounds.right-popupContentSize.width else anchorBounds.left
            val minX=visibleBounds.left+gutter;val minY=visibleBounds.top+gutter
            val maxX=(visibleBounds.right-popupContentSize.width-gutter).coerceAtLeast(minX)
            val maxY=(visibleBounds.bottom-popupContentSize.height-gutter).coerceAtLeast(minY)
            return IntOffset(x.coerceIn(minX,maxX),y.coerceIn(minY,maxY))
        }
    }}
    Popup(popupPositionProvider=position,onDismissRequest=dismiss,properties=if(expanded) PopupProperties(focusable=true) else PopupProperties(flags=android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,inheritSecurePolicy=true,dismissOnBackPress=false,dismissOnClickOutside=false)) {
        Box(Modifier.testTag("$tag-transition").semantics {
            this[MenuMotionFraction]=frame.value;this[MenuMotionPlaying]=frame.running;this[MenuAcceptsInput]=expanded
            if(!expanded) hideFromAccessibility()
        }.graphicsLayer {
            alpha=frame.value;val missing=1f-frame.value
            scaleX=1f-.015f*missing;scaleY=scaleX
            translationY=(if(actualAbove) 6.dp.toPx() else -6.dp.toPx())*missing
            transformOrigin=if(actualAbove) TransformOrigin(0f,1f) else TransformOrigin(1f,0f)
            renderEffect=if(android.os.Build.VERSION.SDK_INT>=31 && animate && missing>.001f) android.graphics.RenderEffect.createBlurEffect((2.dp.toPx()*missing).coerceAtLeast(.01f),(2.dp.toPx()*missing).coerceAtLeast(.01f),android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect() else null
        }) {
        Column((if(expanded) Modifier else Modifier.clearAndSetSemantics {}).focusRequester(fallback).onPreviewKeyEvent {event->
            if(!expanded) true else if(event.key==Key.Escape) {if(event.type==KeyEventType.KeyDown) dismiss();true} else false
        }.focusable(enabled=renderedOptions.none {it.enabled}).width(menuWidth).heightIn(max=menuMaxHeight).shadow(10.dp,shape)
            .background(Brush.verticalGradient(LocalDesktopTooltipColors.current),shape)
            .border(1.dp,LocalDesktopLineColor.current.copy(alpha=.22f),shape)
            .verticalScroll(rememberScrollState()).padding(4.dp).then(if(expanded) Modifier.testTag(tag) else Modifier),verticalArrangement=Arrangement.spacedBy(0.dp)) {
            renderedOptions.forEachIndexed {index,option->
                val active=selected!=null && option.id==selected
                var focused by remember(option.id) {mutableStateOf(false)}
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).focusRequester(focus[index])
                    .onFocusChanged {focused=it.isFocused}
                    .onPreviewKeyEvent {event->
                        if(!expanded) true else if(event.key==Key.Escape) {
                            if(event.type==KeyEventType.KeyDown) dismiss()
                            true
                        } else if(event.key in listOf(Key.Enter,Key.NumPadEnter,Key.Spacebar)) {
                            if(event.type==KeyEventType.KeyUp && option.enabled && expanded) choose(option.id)
                            true
                        } else if(event.type!=KeyEventType.KeyDown) false else {
                            val direction=when(event.key) {
                                Key.DirectionDown->"next";Key.DirectionUp->"previous"
                                Key.DirectionRight->if(horizontalNavigation) "next" else null
                                Key.DirectionLeft->if(horizontalNavigation) "previous" else null
                                Key.MoveHome->"first";Key.MoveEnd->"last"
                                Key.PageDown->if(wrapNavigation) null else "page-next"
                                Key.PageUp->if(wrapNavigation) null else "page-previous"
                                else->null
                            }
                            if(direction==null) {
                                val char=event.utf16CodePoint
                                if(wrapNavigation || event.isAltPressed || event.isCtrlPressed || char<32) false else {
                                    val now=android.os.SystemClock.uptimeMillis()
                                    search=if(now-searchAt>700) char.toChar().toString() else search+char.toChar();searchAt=now
                                    val target=DesktopMenuNavigation.typeahead(renderedOptions.map {it.label},renderedOptions.map {it.enabled},index,search)
                                    if(target>=0) focus[target].requestFocus()
                                    true
                                }
                            } else {
                                val target=DesktopMenuNavigation.navigate(renderedOptions.map {it.enabled},index,direction,wrapNavigation)
                                if(target>=0) focus[target].requestFocus()
                                true
                            }
                        }
                    }.pointerInput(option.id,option.enabled,expanded,choose) {detectTapGestures {if(option.enabled && expanded) choose(option.id)}}
                    .semantics {role=if(selected!=null) Role.RadioButton else Role.Button;if(selected!=null) this.selected=active;if(!option.enabled) disabled();onClick {if(option.enabled && expanded) {choose(option.id);true} else false}}
                    .focusable(enabled=option.enabled && expanded)
                    .testTag(option.tag),verticalAlignment=Alignment.CenterVertically) {
                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp)
                        .background(if(active) colors.primary.copy(alpha=.12f) else if(focused) LocalDesktopOverlayColor.current.copy(alpha=.07f) else androidx.compose.ui.graphics.Color.Transparent,RoundedCornerShape(5.dp))
                        .padding(horizontal=7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        option.icon?.let {Icon(painterResource(it),null,Modifier.size(14.dp),tint=if(active) colors.primary else colors.onSurfaceVariant)}
                        Text(option.label,Modifier.weight(1f).testTag("${option.tag}-label"),style=labelStyle,color=(if(active) colors.primary else colors.onSurfaceVariant).copy(alpha=if(option.enabled) 1f else .5f))
                    }
                }
            }
        }
        LaunchedEffect(renderedOptions,selected,expanded) {if(!expanded) return@LaunchedEffect;val index=renderedOptions.indexOfFirst {it.id==selected && it.enabled}.takeIf {it>=0} ?: renderedOptions.indexOfFirst {it.enabled};if(index>=0) focus[index].requestFocus() else fallback.requestFocus()}
        }
    }
}
