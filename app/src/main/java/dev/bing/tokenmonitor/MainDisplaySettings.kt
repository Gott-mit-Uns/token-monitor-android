package dev.bing.tokenmonitor

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset

internal val LocalMainDisplay=staticCompositionLocalOf {MainDisplayPreferences()}

@Composable internal fun MainDisplaySettings(preferences:DisplayPreferences,devices:List<Device>,change:(DisplayChange)->Unit,embedded:Boolean=false,viewsOnly:Boolean=false,appearanceOnly:Boolean=false,homeContent:(@Composable ()->Unit)?=null) {
    val strings=LocalResources.current;val main=preferences.main
    val effectiveHidden=MainDisplay.hidden(main.hiddenViews);var expanded by rememberSaveable {mutableStateOf(false)}
    var sessionExpanded by rememberSaveable {mutableStateOf(false)}
    var homeExpanded by rememberSaveable {mutableStateOf(false)}
    var deviceMenu by remember {mutableStateOf(false)};var views by rememberSaveable {mutableStateOf(false)}
    if(!embedded) DesktopTextButton(onClick={expanded=!expanded},modifier=Modifier.heightIn(min=LocalDesktopSettingsRowHeight.current).testTag("main-display-toggle")) {Text(strings.getString(R.string.main_display_title),style=quotaTextStyle(12,true))}
    DesktopAccordion(expanded || embedded,"main-display-motion",kind=DesktopDisclosureKind.MAIN_SETTINGS) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        if(!viewsOnly) {
        MainDisplayOption(strings.getString(R.string.main_compact_total),main.compactTotal,"main-compact-total") {change(DisplayChange.CompactTotal(it))}
        MainDisplayOption(strings.getString(R.string.main_live_rate),main.showLiveRate,"main-live-rate") {change(DisplayChange.ShowLiveRate(it))}
        if(main.showLiveRate) Text(strings.getString(R.string.main_live_rate_note),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(main.compactTotal || main.showLiveRate) {
            DesktopSettingInlineChoices(strings.getString(R.string.main_compact_units),listOf("western" to strings.getString(R.string.main_units_western),"localized" to strings.getString(R.string.main_units_localized)),main.compactUnits,"main-units") {change(DisplayChange.CompactUnits(it))}
        }
        if(main.showLiveRate) {
            Box {
                val name=devices.firstOrNull {it.id==main.rateDevice}?.name
                DesktopTextButton(onClick={deviceMenu=true},modifier=Modifier.heightIn(min=LocalDesktopSettingsRowHeight.current).testTag("main-rate-device")) {
                    Text(if(main.rateDevice.isEmpty()) strings.getString(R.string.main_rate_all) else name?.let {strings.getString(R.string.main_rate_selected,it)} ?: strings.getString(R.string.main_rate_missing),style=quotaTextStyle(10))
                }
                DesktopOptionMenu(deviceMenu,listOf(DesktopMenuOption("",strings.getString(R.string.main_rate_all),"main-rate-all"))+devices.distinctBy {it.id}.filter {it.id.isNotBlank()}.mapIndexed {index,device->DesktopMenuOption(device.id,device.name,"main-rate-device-$index")},main.rateDevice,"main-rate-device-menu",compactSettings=true,dismiss={deviceMenu=false}) {id->change(DisplayChange.LiveRateDevice(id));deviceMenu=false}
            }
        }
        MainDisplayOption(strings.getString(R.string.main_swap_actions),main.swapActions,"main-swap-actions") {change(DisplayChange.SwapMainActions(it))}
        }
        if(!appearanceOnly) {
        if(!embedded) DesktopTextButton(onClick={views=!views},modifier=Modifier.heightIn(min=LocalDesktopSettingsRowHeight.current).testTag("main-views-toggle")) {Text(strings.getString(R.string.main_views_title),style=quotaTextStyle(11))}
        DesktopAccordion(views || embedded,"main-views-motion",kind=DesktopDisclosureKind.MAIN_SETTINGS) {
            Row(Modifier.fillMaxWidth().testTag("main-views-header"),verticalAlignment=Alignment.CenterVertically) {
                Text(strings.getString(R.string.main_views_note),Modifier.weight(1f),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
                DesktopListIcon(strings.getString(R.string.main_views_reset),"main-views-reset",glyph="↺",visualSize=24,enabled=main.viewOrder.isNotEmpty()) {change(DisplayChange.ResetMainViewOrder)}
                DesktopListIcon(strings.getString(R.string.main_views_show_all),"main-views-show-all",drawable=R.drawable.desktop_eye,visualSize=24,enabled=main.hiddenViews.isNotEmpty()) {change(DisplayChange.ShowAllMainViews)}
            }
            MainDisplay.order(main.viewOrder).forEachIndexed {index,id->val page=HubPage.entries.first {it.name.lowercase()==id}
                var ordering by remember(id) {mutableStateOf(false)}
                Row(Modifier.fillMaxWidth().heightIn(min=LocalDesktopSettingsRowHeight.current),verticalAlignment=Alignment.CenterVertically) {
                    Text(strings.getString(page.title),Modifier.weight(1f),style=quotaTextStyle(11).copy(fontWeight=FontWeight.Medium),color=MaterialTheme.colorScheme.onSurface.copy(alpha=if(id in effectiveHidden) .58f else 1f),maxLines=1,overflow=TextOverflow.Ellipsis)
                    if(id=="home" && homeContent!=null) IconButton(onClick={homeExpanded=!homeExpanded},modifier=Modifier.sizeIn(minWidth=LocalDesktopSettingsRowHeight.current,minHeight=LocalDesktopSettingsRowHeight.current).testTag("main-view-home-settings")) {Icon(androidx.compose.ui.res.painterResource(R.drawable.desktop_settings_general),strings.getString(R.string.desktop_home_modules),Modifier.size(12.dp),tint=if(homeExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
                    if(id=="sessions") IconButton(onClick={sessionExpanded=!sessionExpanded},modifier=Modifier.sizeIn(minWidth=LocalDesktopSettingsRowHeight.current,minHeight=LocalDesktopSettingsRowHeight.current).testTag("main-view-sessions-settings").semantics {stateDescription=strings.getString(if(sessionExpanded) R.string.settings_session_expanded else R.string.settings_session_collapsed)}) {Icon(androidx.compose.ui.res.painterResource(R.drawable.desktop_settings_general),strings.getString(R.string.settings_session_configure),Modifier.size(12.dp),tint=if(sessionExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
                    IconToggleButton(checked=id !in effectiveHidden,enabled=id in effectiveHidden || MainDisplay.visible(main).size>1,onCheckedChange={change(DisplayChange.MainViewVisible(id,it))},modifier=Modifier.testTag("main-view-visible-$id")) {
                        Icon(androidx.compose.ui.res.painterResource(if(id in effectiveHidden) R.drawable.desktop_eye_off else R.drawable.desktop_eye),strings.getString(if(id in effectiveHidden) R.string.tool_list_show else R.string.tool_list_hide,strings.getString(page.title)),Modifier.size(12.dp))
                    }
                    Box {
                        DesktopListIcon(strings.getString(R.string.tool_list_reorder,strings.getString(page.title)),"main-view-order-$id",glyph="↕") {ordering=true}
                        DesktopOptionMenu(ordering,listOf(-1,1).map {direction->
                    DesktopMenuOption(direction.toString(),strings.getString(if(direction<0) R.string.desktop_move_up else R.string.desktop_move_down),"main-view-${if(direction<0) "up" else "down"}-$id",enabled=index+direction in MainDisplay.ids.indices)
                },tag="main-view-order-menu-$id",width=132.dp,dismiss={ordering=false}) {value->ordering=false;change(DisplayChange.MainViewMove(id,value.toInt()))}
                    }
                }
                if(id=="home" && homeContent!=null) DesktopAccordion(homeExpanded,"main-home-details-motion",kind=DesktopDisclosureKind.MAIN_HOME) {
                    Column(Modifier.fillMaxWidth().padding(start=12.dp).testTag("main-view-home-details"),verticalArrangement=Arrangement.spacedBy(4.dp)) {homeContent()}
                }
                val nestedLine=LocalDesktopLineColor.current.copy(alpha=.14f)
                if(id=="sessions") DesktopAccordion(sessionExpanded,"main-sessions-details-motion",kind=DesktopDisclosureKind.MAIN_SESSIONS) {
                Column(Modifier.fillMaxWidth().padding(start=12.dp).drawBehind {drawLine(nestedLine,Offset.Zero,Offset(0f,size.height),1.dp.toPx())}.padding(start=8.dp).testTag("main-view-sessions-details"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    DesktopSettingSwitch(strings.getString(R.string.settings_session_titles),preferences.sessionTitlesEnabled,"session-titles") {change(DisplayChange.SessionTitles(it))}
                    Text(strings.getString(if(preferences.hideTitles) R.string.settings_session_privacy_note else R.string.settings_session_titles_note),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
                    DesktopSettingInlineChoices(strings.getString(R.string.session_context_metric),listOf("used" to strings.getString(R.string.session_context_used),"remaining" to strings.getString(R.string.session_context_remaining)),main.sessionContextMetric,"session-context") {change(DisplayChange.SessionContextMetric(it))}
                }
                }
            }
        }
        }
    }
    }
}

@Composable private fun MainDisplayOption(label:String,checked:Boolean,tag:String,onChange:(Boolean)->Unit) {
    DesktopSettingSwitch(label,checked,tag,change=onChange)
}
