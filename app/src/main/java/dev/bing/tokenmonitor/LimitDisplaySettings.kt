package dev.bing.tokenmonitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal val LocalLimitDisplay = staticCompositionLocalOf { LimitDisplayPreferences() }

@Composable internal fun LimitDisplaySettings(preferences:DisplayPreferences,update:(DisplayChange)->Unit,limits:List<Limit> = emptyList(),embedded:Boolean=false) {
    val strings=LocalResources.current
    var expanded by rememberSaveable {mutableStateOf(false)}
    if(!embedded) DesktopTextButton({expanded=!expanded},modifier=Modifier.heightIn(min=LocalDesktopSettingsRowHeight.current).testTag("limit-settings-toggle")) {Text(strings.getString(R.string.limit_display_title),style=quotaTextStyle(11))}
    if(expanded || embedded) Column(Modifier.fillMaxWidth().testTag("limit-settings-controls"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        QuotaProviderSettings(preferences,update,limits)
        DesktopSettingSwitch(strings.getString(R.string.limit_show_source),preferences.limits.showSource,"limit-show-source") {update(DisplayChange.LimitSource(it))}
        DesktopSettingSwitch(strings.getString(R.string.limit_mask_emails),preferences.limits.maskEmails,"limit-mask-emails",description=strings.getString(R.string.limit_mask_note)) {update(DisplayChange.MaskLimitEmails(it))}
        DesktopSettingInlineChoices(strings.getString(R.string.limit_bars_show),listOf("remaining" to strings.getString(R.string.limit_mode_remaining),"used" to strings.getString(R.string.limit_mode_used)),if(preferences.limits.showUsed) "used" else "remaining","limit-mode") {update(DisplayChange.LimitUsed(it=="used"))}
    }
}

/** Original usage-item checkbox at the left; entire row remains one touch target. */
@Composable internal fun LimitDisplayToggle(label:String,tag:String,value:Boolean,enabled:Boolean=true,change:(Boolean)->Unit) {
    val colors=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag(tag).toggleable(value,enabled=enabled,role=Role.Checkbox,onValueChange=change),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        val ink=if(value) colors.primary else colors.onSurfaceVariant.copy(alpha=.75f)
        Canvas(Modifier.size(14.dp).testTag("$tag-visual")) {
            val tint=ink.copy(alpha=if(enabled) ink.alpha else .38f)
            if(value) drawRoundRect(tint,cornerRadius=CornerRadius(2.dp.toPx()))
            else drawRoundRect(tint,cornerRadius=CornerRadius(2.dp.toPx()),style=Stroke(1.dp.toPx()))
            if(value) {
                val path=Path();path.moveTo(size.width*.23f,size.height*.51f);path.lineTo(size.width*.43f,size.height*.71f);path.lineTo(size.width*.79f,size.height*.30f)
                drawPath(path,colors.onPrimary,style=Stroke(1.5.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round,join=androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
        Text(label,Modifier.weight(1f),style=quotaTextStyle(11),color=colors.onSurface.copy(alpha=if(enabled) 1f else .58f))
    }
}

@Composable private fun QuotaProviderSettings(preferences:DisplayPreferences,update:(DisplayChange)->Unit,limits:List<Limit>) {
    val strings=LocalResources.current;val prefs=preferences.limits
    var query by rememberSaveable {mutableStateOf("")}
    var expanded by rememberSaveable {mutableStateOf<String?>(null)}
    val filtering=query.isNotBlank();val locale=strings.configuration.locales[0]
    val ordered=LimitPreferences.order(prefs.providerOrder)
    val groups=remember(limits) {LimitDisplay.groups(limits).groupBy {it.first().provider}}
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(strings.getString(R.string.limit_local_display_note),Modifier.weight(1f),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
        DesktopListIcon(strings.getString(R.string.limit_order_restore),"limit-order-restore",glyph="↺",visualSize=24,enabled=prefs.providerOrder.isNotEmpty()) {update(DisplayChange.RestoreLimitOrder())}
    }
    DesktopListSearch(query,{query=it},strings.getString(R.string.limit_provider_search),"limit-provider-search")
    if(filtering) Text(strings.getString(R.string.tool_list_filter_note),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
    val matches=ordered.filter {LimitPreferences.matches(it,query,locale)}
    if(matches.isEmpty()) Text(strings.getString(R.string.settings_list_empty),Modifier.testTag("limit-provider-empty"),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
    matches.forEach {id->
        val label=LimitPreferences.settingsLabel(id);val open=expanded==id
        val reading=LimitSettingsPresentation.record(groups[id].orEmpty())
        val detectedStatus=reading?.takeIf(LimitSettingsPresentation::detected)?.let(LimitSettingsPresentation::status)?.let {LimitSettingsPresentation.text(locale,it.label)}
        val index=ordered.indexOf(id)
        Column(Modifier.fillMaxWidth().testTag("limit-provider-row-$id")) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                DesktopTextButton({expanded=if(open) null else id},modifier=Modifier.weight(1f).heightIn(min=LocalDesktopSettingsRowHeight.current).testTag("limit-items-$id").semantics {stateDescription=listOfNotNull(strings.getString(if(open) R.string.settings_options_expanded else R.string.settings_options_collapsed),detectedStatus).joinToString(" · ")},shape=RoundedCornerShape(4.dp),contentPadding=PaddingValues(0.dp)) {
                    Box(Modifier.weight(1f)) {LimitSettingsProviderCopy(id,label,groups[id].orEmpty(),prefs.maskEmails)}
                    val accounts=LimitSettingsPresentation.accounts(groups[id].orEmpty())
                    if(accounts.isNotEmpty()) Text(strings.getQuantityString(R.plurals.limit_accounts,LimitSettingsPresentation.accountCount(accounts),LimitSettingsPresentation.accountCount(accounts)),Modifier.padding(horizontal=4.dp).testTag("limit-provider-account-count-$id"),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if(open) "⌃" else "⌄",style=quotaTextStyle(11),color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                QuotaOrderMenu(id,label,index,ordered.size,false,filtering,update)
            }
            if(open) Column(Modifier.fillMaxWidth().padding(start=12.dp).testTag("limit-items-details-$id"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                LimitSettingsAccounts(id,groups[id].orEmpty(),prefs.maskEmails)
                val items=remember(groups[id],prefs.hiddenItems[id],locale) {
                    val drawn=linkedMapOf<String,String>()
                    groups[id].orEmpty().flatMap {LimitProviderLayout.project(LimitProviderLayout.record(it),false,locale)}.forEach {row->row.item?.let {key->drawn.putIfAbsent(key,row.label.ifBlank {LimitPreferences.fallback(id,key)})}}
                    drawn.map {LimitSettingItem(it.key,it.value,true)}+prefs.hiddenItems[id].orEmpty().filter {it !in drawn}.map {LimitSettingItem(it,LimitPreferences.fallback(id,it),false)}
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(strings.getString(R.string.limit_visible_items),Modifier.weight(1f),style=quotaTextStyle(10))
                    if(!prefs.hiddenItems[id].isNullOrEmpty()) DesktopListIcon(strings.getString(R.string.limit_items_restore),"limit-items-restore-$id",glyph="↺",visualSize=18) {update(DisplayChange.RestoreLimitItems(id))}
                }
                if(items.isEmpty()) Text(strings.getString(R.string.limit_no_current_items),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
                items.forEachIndexed {itemIndex,item->
                    val raw=if(item.available) item.label else strings.getString(R.string.limit_item_unavailable,item.label)
                    LimitDisplayToggle(LimitDisplay.text(raw,prefs.maskEmails),"limit-item-$id-$itemIndex",item.key !in prefs.hiddenItems[id].orEmpty()) {visible->update(DisplayChange.LimitItemVisibility(id,item.key,visible))}
                }
            }
        }
    }
}
private data class LimitSettingItem(val key:String,val label:String,val available:Boolean)

/** Original Home -> quotas subgroup. Does not configure collection or enabled providers. */
@Composable internal fun HomeQuotaSettings(preferences:DisplayPreferences,update:(DisplayChange)->Unit,limits:List<Limit>) {
    val strings=LocalResources.current;val prefs=preferences.limits
    DesktopSettingSelect(strings.getString(R.string.limit_home_display_mode),listOf("text" to strings.getString(R.string.limit_home_text),"bars" to strings.getString(R.string.limit_home_bars)),if(prefs.homeBars) "bars" else "text","home-quota-mode") {update(DisplayChange.HomeLimitBars(it=="bars"))}
    DesktopSettingSwitch(strings.getString(R.string.limit_home_highlight),prefs.highlightLowHomeLimits,"home-quota-highlight") {update(DisplayChange.HighlightLowHomeLimits(it))}
    val namesRequired=!preferences.appearance.showToolIcons
    DesktopSettingSwitch(strings.getString(R.string.limit_home_names),namesRequired || prefs.showHomeProviderNames,"home-quota-names",enabled=!namesRequired,description=if(namesRequired) strings.getString(R.string.limit_home_names_required) else null) {update(DisplayChange.HomeLimitNames(it))}
    var count by remember(prefs.homeAccountCount) {mutableStateOf(prefs.homeAccountCount.toString())}
    fun commitCount() {
        val value=count.toIntOrNull()?.takeIf {it in 1..12}
        if(value!=null && value!=prefs.homeAccountCount) update(DisplayChange.HomeLimitCount(value))
        else count=prefs.homeAccountCount.toString()
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(strings.getString(R.string.limit_home_count),Modifier.weight(1f),style=quotaTextStyle(11))
        DesktopEditField(count,{count=it},strings.getString(R.string.limit_home_count),"home-quota-count",Modifier.width(54.dp),keyboardType=KeyboardType.Number,onFocus={if(!it) commitCount()},done=::commitCount)
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(strings.getString(R.string.limit_home_urgency_note),Modifier.weight(1f),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
        DesktopListIcon(strings.getString(R.string.limit_home_order_restore),"home-quota-order-restore",glyph="↺",visualSize=24,enabled=prefs.homeProviderOrder.isNotEmpty()) {update(DisplayChange.RestoreLimitOrder(home=true))}
        DesktopListIcon(strings.getString(R.string.limit_home_show_all),"home-quota-show-all",drawable=R.drawable.desktop_eye,visualSize=24,enabled=prefs.hiddenHomeProviders.isNotEmpty()) {update(DisplayChange.ShowAllHomeLimitProviders)}
    }
    val ordered=LimitPreferences.order(prefs.homeProviderOrder.ifEmpty {prefs.providerOrder})
    val available=limits.map {it.provider}.toSet()+prefs.hiddenHomeProviders
    ordered.filter {it in available}.forEach {id->
        val label=LimitPreferences.settingsLabel(id);val visible=id !in prefs.hiddenHomeProviders
        Row(Modifier.fillMaxWidth().heightIn(min=LocalDesktopSettingsRowHeight.current),verticalAlignment=Alignment.CenterVertically) {
            Text(label,Modifier.weight(1f),style=quotaTextStyle(11).copy(fontWeight=FontWeight.Medium),color=MaterialTheme.colorScheme.onSurface.copy(alpha=if(visible) 1f else .58f),maxLines=1,overflow=TextOverflow.Ellipsis)
            DesktopListIcon(strings.getString(if(visible) R.string.tool_list_hide else R.string.tool_list_show,label),"home-quota-provider-$id",checked=visible,drawable=if(visible) R.drawable.desktop_eye else R.drawable.desktop_eye_off,accent=visible) {update(DisplayChange.HomeLimitProviderVisibility(id))}
            QuotaOrderMenu(id,label,ordered.indexOf(id),ordered.size,true,false,update)
        }
    }
}

@Composable private fun QuotaOrderMenu(id:String,label:String,index:Int,size:Int,home:Boolean,filtering:Boolean,update:(DisplayChange)->Unit) {
    val strings=LocalResources.current;var ordering by remember {mutableStateOf(false)}
    val prefix=if(home) "home-quota" else "limit"
    Box {
        DesktopListIcon(strings.getString(R.string.tool_list_reorder,label),"$prefix-provider-order-$id",glyph="↕",enabled=!filtering) {ordering=true}
        DesktopOptionMenu(ordering,listOf(-1,1).map {direction->
                    DesktopMenuOption(direction.toString(),strings.getString(if(direction<0) R.string.desktop_move_up else R.string.desktop_move_down),"$prefix-provider-${if(direction<0) "up" else "down"}-$id",enabled=!filtering && index+direction in 0 until size)
                },tag="$prefix-provider-order-menu-$id",width=132.dp,dismiss={ordering=false}) {value->ordering=false;update(DisplayChange.LimitProviderMove(id,value.toInt(),home))}
    }
}
