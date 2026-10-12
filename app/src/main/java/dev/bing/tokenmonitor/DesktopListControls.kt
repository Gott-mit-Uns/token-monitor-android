package dev.bing.tokenmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Source 22px eye/pin controls, independently enlarged to 48dp touch targets. */
@Composable internal fun DesktopListIcon(label:String,tag:String,drawable:Int?=null,glyph:String="",enabled:Boolean=true,checked:Boolean?=null,
    accent:Boolean=false,outlined:Boolean=false,visualSize:Int=22,glyphSize:Int=if(visualSize==24) 16 else 13,expanded:Boolean?=null,click:()->Unit) {
    val strings=androidx.compose.ui.platform.LocalResources.current
    val colors=MaterialTheme.colorScheme;val ink=(if(accent) colors.primary else colors.onSurfaceVariant).copy(alpha=if(enabled) 1f else .34f)
    val action=if(checked==null) Modifier.clickable(enabled,role=Role.Button,onClick=click) else Modifier.toggleable(checked,enabled,role=Role.Checkbox,onValueChange={click()})
    Box(Modifier.size(LocalDesktopSettingsRowHeight.current).then(action).testTag(tag).semantics {contentDescription=label;if(expanded!=null) stateDescription=strings.getString(if(expanded) R.string.desktop_collapse else R.string.desktop_expand)},contentAlignment=Alignment.Center) {
        Box(Modifier.size(visualSize.dp).border(1.dp,if(outlined) colors.primary.copy(alpha=.28f) else Color.Transparent,RoundedCornerShape(6.dp))
            .background(if(outlined) colors.primary.copy(alpha=.06f) else Color.Transparent,RoundedCornerShape(6.dp)),contentAlignment=Alignment.Center) {
            if(drawable!=null) Icon(painterResource(drawable),null,Modifier.size(13.dp),tint=ink) else Text(glyph,style=quotaTextStyle(glyphSize),color=ink)
        }
    }
}

/** Source search: 11px text, 5/9px padding, 24px icon inset; stable field outside rows. */
@Composable internal fun DesktopListSearch(value:String,change:(String)->Unit,label:String,tag:String) {
    var focused by remember {mutableStateOf(false)};val colors=MaterialTheme.colorScheme
    val strings=androidx.compose.ui.platform.LocalResources.current
    val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
    BasicTextField(value,change,Modifier.fillMaxWidth().heightIn(min=LocalDesktopSettingsRowHeight.current).testTag(tag).onFocusChanged {focused=it.isFocused}.semantics {contentDescription=label},
        textStyle=quotaTextStyle(11).copy(color=colors.onSurface),singleLine=true,cursorBrush=SolidColor(colors.onSurface),
        keyboardOptions=KeyboardOptions(autoCorrectEnabled=false,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus();keyboard?.hide()}),decorationBox={inner->
            Box(Modifier.fillMaxWidth().heightIn(min=LocalDesktopSettingsRowHeight.current),contentAlignment=Alignment.Center) {
                Row(Modifier.fillMaxWidth().border(1.dp,if(focused) DesktopEditTokens.focus else desktopEditLine(),RoundedCornerShape(6.dp))
                    .background(LocalDesktopSunkenColor.current.copy(alpha=.48f),RoundedCornerShape(6.dp)).padding(top=5.dp,bottom=5.dp,start=9.dp,end=if(value.isEmpty()) 9.dp else 48.dp),verticalAlignment=Alignment.CenterVertically) {
                    val ink=colors.onSurfaceVariant
                    Canvas(Modifier.size(10.dp)) {
                        drawCircle(ink,3.dp.toPx(),Offset(3.dp.toPx(),3.dp.toPx()),style=Stroke(1.5.dp.toPx()))
                        drawLine(ink,Offset(5.dp.toPx(),5.dp.toPx()),Offset(8.dp.toPx(),8.dp.toPx()),strokeWidth=1.5.dp.toPx())
                    }
                    Spacer(Modifier.width(5.dp))
                    Box(Modifier.weight(1f)) {if(value.isEmpty()) Text(label,style=quotaTextStyle(11),color=colors.onSurfaceVariant);inner()}
                }
                if(value.isNotEmpty()) Box(Modifier.align(Alignment.CenterEnd)) {
                    DesktopListIcon(strings.getString(R.string.settings_clear_search),"$tag-clear",glyph="×",glyphSize=9) {change("")}
                }
            }
        })
}

@Composable internal fun DesktopInlineChoices(title:String,choices:List<Pair<String,String>>,selected:String,tag:String,change:(String)->Unit) {
    val colors=MaterialTheme.colorScheme;val line=LocalDesktopLineColor.current.copy(alpha=.14f)
    Row(Modifier.fillMaxWidth().heightIn(min=LocalDesktopSettingsRowHeight.current),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.width(1.dp).height(24.dp)) {drawRect(line)}
        FlowRow(Modifier.weight(1f),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(0.dp)) {
        Box(Modifier.heightIn(min=LocalDesktopSettingsRowHeight.current),contentAlignment=Alignment.CenterStart) {Text(title,style=quotaTextStyle(11))}
        Row(Modifier.selectableGroup(),horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically) {
            choices.forEach {(id,label)->DesktopChoicePill(label,selected==id,"$tag-$id") {change(id)}}
        }
        }
    }
}

/** Original inline-option CSS: 4/10 padding, pill radius, overlay .12 on selected. */
@Composable internal fun DesktopChoicePill(label:String,selected:Boolean,tag:String,click:()->Unit) {
    val colors=MaterialTheme.colorScheme
    Box(Modifier.widthIn(min=LocalDesktopSettingsRowHeight.current).heightIn(min=LocalDesktopSettingsRowHeight.current).selectable(selected,role=Role.RadioButton,onClick=click).testTag(tag),contentAlignment=Alignment.Center) {
        Text(label,Modifier.background(if(selected) LocalDesktopOverlayColor.current.copy(alpha=.12f) else Color.Transparent,RoundedCornerShape(999.dp)).padding(horizontal=10.dp,vertical=4.dp),
            style=quotaTextStyle(11),color=if(selected) colors.onSurface else colors.onSurfaceVariant)
    }
}
