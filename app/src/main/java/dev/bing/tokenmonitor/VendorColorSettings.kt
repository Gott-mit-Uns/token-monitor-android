package dev.bing.tokenmonitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable internal fun VendorColorSettings(preferences:DisplayPreferences,change:(DisplayChange)->Unit) {
    val strings=LocalResources.current
    var expanded by rememberSaveable {mutableStateOf(false)}
    val appearance=preferences.appearance
    val palette=DesktopVendorPalette.resolve(appearance.vendorColors)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        DesktopTextButton({expanded=!expanded},Modifier.weight(1f).heightIn(min=LocalDesktopSettingsRowHeight.current).testTag("vendor-colors-toggle"),contentPadding=PaddingValues(0.dp)) {
            Text(strings.getString(R.string.appearance_vendor_colors),Modifier.weight(1f),style=quotaTextStyle(11),color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if(expanded) "⌃" else "⌄",style=quotaTextStyle(11),color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DesktopEditAction("↺",strings.getString(R.string.appearance_vendor_reset_all),"vendor-colors-reset-all",appearance.vendorColors.isNotEmpty(),visualSize=24,glyphSize=16,chrome=false) {change(DisplayChange.ResetVendorColors)}
    }
    if(expanded) {
        Text(strings.getString(R.string.appearance_vendor_note),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.fillMaxWidth().height(196.dp).padding(end=4.dp).testTag("vendor-color-list"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            items(DesktopVendorPalette.ids,key={it}) {id->
                val label=if(id=="default") strings.getString(R.string.appearance_vendor_default) else desktopVendorLabels.getValue(id)
                val value="#"+(palette.getValue(id) and 0xFFFFFF).toString(16).padStart(6,'0')
                DesktopColorRow(label,value,"vendor-color-$id",id in appearance.vendorColors,{change(DisplayChange.VendorColor(id,null))}) {change(DisplayChange.VendorColor(id,it))}
            }
        }
    }
}

@Composable internal fun AppearanceVisibilityControls(preferences:DisplayPreferences,change:(DisplayChange)->Unit) {
    val strings=LocalResources.current
    listOf(Triple("appearance-live-dot",R.string.appearance_live_dot,preferences.appearance.showLiveDot),Triple("appearance-tool-icons",R.string.appearance_tool_icons,preferences.appearance.showToolIcons)).forEach {(tag,label,checked)->
        DesktopSettingSwitch(strings.getString(label),checked,tag) {change(if(tag=="appearance-tool-icons") DisplayChange.ToolIcons(it) else DisplayChange.LiveDot(it))}
    }
}
