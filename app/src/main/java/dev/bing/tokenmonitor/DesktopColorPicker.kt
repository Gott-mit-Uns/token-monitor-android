package dev.bing.tokenmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

/** Mobile counterpart of the source's native color-input popup. Only Apply commits. */
@Composable internal fun DesktopColorPicker(label:String,value:String,tag:String,dismiss:()->Unit,preview:(String)->Unit={},apply:(String)->Unit) {
    val strings=LocalResources.current
    var draft by rememberSaveable(value) {mutableStateOf(value)}
    val valid=DesktopAppearance.hex(draft)
    var lastValid by rememberSaveable(value) {mutableStateOf(value)}
    val shown=valid ?: lastValid
    LaunchedEffect(valid) {valid?.let {lastValid=it;preview(it)}}
    AppearancePickerTheme {
    Dialog(onDismissRequest=dismiss) {
        val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
        fun close() {focus.clearFocus();keyboard?.hide();dismiss()}
        fun commit() {valid?.let {focus.clearFocus();keyboard?.hide();apply(it)}}
        Column(Modifier.fillMaxWidth().heightIn(max=560.dp).background(MaterialTheme.colorScheme.background,RoundedCornerShape(12.dp))
            .border(1.dp,LocalDesktopTooltipBorder.current,RoundedCornerShape(12.dp)).verticalScroll(rememberScrollState()).padding(16.dp).testTag("$tag-dialog"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(label,Modifier.testTag("$tag-title"),style=quotaTextStyle(12,true))
            Box(Modifier.fillMaxWidth().height(36.dp).background(hexColor(shown),RoundedCornerShape(6.dp)).testTag("$tag-preview"))
            DesktopEditField(draft,{draft=it.take(20)},strings.getString(R.string.appearance_color_hex),tag,
                error=if(valid==null) strings.getString(R.string.appearance_color_invalid) else null,done=::commit)
            if(valid==null) Text(strings.getString(R.string.appearance_color_invalid),style=quotaTextStyle(10),color=MaterialTheme.colorScheme.error)
            listOf("R","G","B").forEachIndexed {index,channel->
                val values=(0..2).map {shown.substring(1+it*2,3+it*2).toInt(16)}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(channel,style=quotaTextStyle(11))
                    Slider(values[index].toFloat(),{position->val next=values.toMutableList();next[index]=position.roundToInt().coerceIn(0,255);draft="#"+next.joinToString("") {it.toString(16).padStart(2,'0')}},
                        Modifier.weight(1f).testTag("$tag-$channel").semantics {contentDescription=label+" "+channel},valueRange=0f..255f,steps=254)
                    Text(values[index].toString(),Modifier.width(28.dp),style=quotaTextStyle(11))
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                DesktopTextButton(::close,Modifier.heightIn(min=48.dp).testTag("$tag-cancel")) {Text(strings.getString(R.string.appearance_cancel),style=quotaTextStyle(11))}
                DesktopTextButton(::commit,Modifier.heightIn(min=48.dp).testTag("$tag-apply"),enabled=valid!=null) {Text(strings.getString(R.string.appearance_apply),style=quotaTextStyle(11))}
            }
        }
    }
    }
}
