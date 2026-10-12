package dev.bing.tokenmonitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Fixed renderer CSS values, independent of Android's enlarged touch bounds. */
internal object DesktopEditTokens {
    val radius=6.dp
    val touch=48.dp
    val focus=Color(0xFF73BDF5).copy(alpha=.72f)
    const val sunken=.48f
    const val control=.049f
}

@Composable internal fun desktopEditLine():Color {
    val bg=MaterialTheme.colorScheme.background
    val light=.299f*bg.red+.587f*bg.green+.114f*bg.blue>.6f
    return LocalDesktopLineColor.current.copy(alpha=if(light) .28f else .138f)
}

@Composable internal fun DesktopEditField(value:String,onChange:(String)->Unit,label:String,tag:String,
    modifier:Modifier=Modifier,textStyle:TextStyle=quotaTextStyle(11),error:String?=null,
    keyboardType:KeyboardType=KeyboardType.Ascii,visualTransformation:VisualTransformation=VisualTransformation.None,onFocus:(Boolean)->Unit={},done:()->Unit={}) {
    var focused by remember {mutableStateOf(false)}
    val colors=MaterialTheme.colorScheme
    val line=desktopEditLine()
    BasicTextField(value,onChange,modifier.heightIn(min=DesktopEditTokens.touch).testTag(tag).onFocusChanged {focused=it.isFocused;onFocus(it.isFocused)}.semantics {
        contentDescription=label
        if(error!=null) this.error(error)
    },singleLine=true,visualTransformation=visualTransformation,textStyle=textStyle.copy(color=colors.onSurface),cursorBrush=SolidColor(colors.onSurface),
        keyboardOptions=KeyboardOptions(autoCorrectEnabled=false,keyboardType=keyboardType,imeAction=ImeAction.Done),
        keyboardActions=KeyboardActions(onDone={done()}),decorationBox={inner->
            Box(Modifier.fillMaxWidth().heightIn(min=DesktopEditTokens.touch),contentAlignment=Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().border(1.dp,if(focused) DesktopEditTokens.focus else if(error!=null) colors.error else line,RoundedCornerShape(DesktopEditTokens.radius))
                    .background(LocalDesktopSunkenColor.current.copy(alpha=DesktopEditTokens.sunken),RoundedCornerShape(DesktopEditTokens.radius)).padding(horizontal=8.dp,vertical=7.dp)) {inner()}
            }
        })
}

@Composable internal fun DesktopEditAction(glyph:String,label:String,tag:String,enabled:Boolean=true,
    visualSize:Int=30,glyphSize:Int=13,chrome:Boolean=true,click:()->Unit) {
    val colors=MaterialTheme.colorScheme
    val line=desktopEditLine()
    val opacity=if(enabled) 1f else .4f
    val radius=if(visualSize==30) 7.dp else DesktopEditTokens.radius
    Box(Modifier.size(DesktopEditTokens.touch).clickable(enabled=enabled,role=Role.Button,onClick=click).testTag(tag).semantics {contentDescription=label},contentAlignment=Alignment.Center) {
        Box(Modifier.size(visualSize.dp).border(1.dp,if(chrome) line.copy(alpha=line.alpha*opacity) else Color.Transparent,RoundedCornerShape(radius))
            .background(LocalDesktopOverlayColor.current.copy(alpha=if(chrome) DesktopEditTokens.control*opacity else 0f),RoundedCornerShape(radius)),contentAlignment=Alignment.Center) {
            Text(glyph,style=quotaTextStyle(glyphSize),color=colors.onSurfaceVariant.copy(alpha=opacity))
        }
    }
}

@Composable internal fun DesktopThemePreset(label:String,accent:String,selected:Boolean,tag:String,click:()->Unit) {
    val colors=MaterialTheme.colorScheme
    val line=desktopEditLine()
    Row(Modifier.heightIn(min=DesktopEditTokens.touch).selectable(selected,role=Role.Button,onClick=click).testTag(tag),verticalAlignment=Alignment.CenterVertically) {
        Row(Modifier.border(1.dp,if(selected) colors.primary.copy(alpha=.58f) else line,RoundedCornerShape(999.dp))
            .background(if(selected) colors.primary.copy(alpha=.08f) else LocalDesktopOverlayColor.current.copy(alpha=DesktopEditTokens.control),RoundedCornerShape(999.dp))
            .padding(horizontal=9.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(10.dp)) {drawCircle(hexColor(accent));drawCircle(Color.Black.copy(alpha=.25f),style=Stroke(1.dp.toPx()))}
            Text(label,style=quotaTextStyle(11),color=if(selected) colors.primary else colors.onSurfaceVariant)
        }
    }
}
