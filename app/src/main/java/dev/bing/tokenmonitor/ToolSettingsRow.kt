package dev.bing.tokenmonitor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Source single-line name/eye/pin chrome; the mobile order menu replaces dragging. */
@Composable internal fun ToolSettingsRow(id:String,preferences:DisplayPreferences,filtering:Boolean,update:(DisplayChange)->Unit) {
    val strings=LocalResources.current;val label=toolLabel(id)
    val hidden=id in preferences.hiddenTools;val pinned=id in preferences.pinnedTools
    var ordering by remember {mutableStateOf(false)}
    Row(Modifier.fillMaxWidth().heightIn(min=LocalDesktopSettingsRowHeight.current).testTag("tool-setting-$id").semantics {
        stateDescription=strings.getString(if(hidden) R.string.tool_list_hidden else if(pinned) R.string.tool_list_pinned else R.string.tool_list_visible)
    },verticalAlignment=Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f),style=quotaTextStyle(11).copy(fontWeight=androidx.compose.ui.text.font.FontWeight.Medium),color=MaterialTheme.colorScheme.onSurface.copy(alpha=if(hidden) .58f else 1f),maxLines=1,overflow=TextOverflow.Ellipsis)
        DesktopListIcon(strings.getString(if(hidden) R.string.tool_list_show else R.string.tool_list_hide,label),"tool-visible-$id",checked=!hidden,
            drawable=if(hidden) R.drawable.desktop_eye_off else R.drawable.desktop_eye,accent=!hidden) {update(DisplayChange.ToolVisibility(id))}
        DesktopListIcon(strings.getString(if(pinned) R.string.tool_list_unpin else R.string.tool_list_pin,label),"tool-pin-$id",checked=pinned,drawable=R.drawable.desktop_pin,accent=pinned,outlined=pinned) {update(DisplayChange.ToolPin(id))}
        Box {
            DesktopListIcon(strings.getString(R.string.tool_list_reorder,label),"tool-order-$id",glyph="↕",enabled=!filtering) {ordering=true}
            DesktopOptionMenu(ordering,listOf(-1,1).map {direction->
                    DesktopMenuOption(direction.toString(),strings.getString(if(direction<0) R.string.desktop_move_up else R.string.desktop_move_down),"tool-${if(direction<0) "up" else "down"}-$id",enabled=!filtering && ToolDisplay.canMove(preferences,id,direction))
                },tag="tool-order-menu-$id",width=132.dp,dismiss={ordering=false}) {value->ordering=false;update(DisplayChange.ToolMove(id,value.toInt()))}
        }
    }
}
