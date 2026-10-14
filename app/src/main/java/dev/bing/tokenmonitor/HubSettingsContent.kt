package dev.bing.tokenmonitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
internal fun HubSettingsContent(state:HubState,model:HubUiActions,localAllowed:Boolean,requestLocal:(()->Unit)?,preferences:DisplayPreferences,updateDisplay:((DisplayChange)->Unit)?,openLocalSettings:(()->Unit)?,section:String,selectSection:(String)->Unit,scroll:LazyListState,today:LocalDate,onSubscription:(String)->Unit,openShared:(HubPage)->Unit,connectionDraft:HubConnectionDraft=remember {HubConnectionDraft()},syncNow:Long=LocalHubCalendarClock.current.instant().toEpochMilli(),onForgot:()->Unit) {
    val strings=LocalResources.current
    var toolQuery by rememberSaveable {mutableStateOf("")}
    LaunchedEffect(state.connectionAttempt,state.loading,state.error,state.connectionFailure,state.snapshot,connectionDraft.submitted) {
        if(connectionDraft.submitted && state.connectionAttempt>connectionDraft.submittedAfter && !state.loading) {
            if(state.snapshot!=null && state.error==null && state.connectionFailure==null) {
                if(section!="sync") selectSection("sync")
                connectionDraft.editing=false;connectionDraft.address="";connectionDraft.alternate="";connectionDraft.secret="";connectionDraft.replacing=false
            }
            connectionDraft.submitted=false
        }
    }
    val connectionContent: @Composable () -> Unit = {
        HubConnectionSettings(state,model,localAllowed,requestLocal,openLocalSettings,onForgot,connectionDraft,syncNow)
    }

    val scrollAnchor = rememberDesktopSettingsScrollAnchor(scroll, section)
    CompositionLocalProvider(LocalDesktopSettingsScrollAnchor provides scrollAnchor,LocalDesktopSettingsRowHeight provides 44.dp,LocalMinimumInteractiveComponentSize provides 44.dp) {
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("hub-content").cancelSettingsAnchorOnInteraction(scrollAnchor),state=scroll,contentPadding=PaddingValues(horizontal=20.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(0.dp)) {
        if(state.snapshot==null) item(key="connection-wizard") {Column {connectionContent()};Spacer(Modifier.height(12.dp))}
        item(key="settings-general") {
            val language=when(preferences.language) {LanguageChoice.ENGLISH->"English";LanguageChoice.CHINESE->strings.getString(R.string.language_chinese);else->strings.getString(R.string.follow_system)}
            DesktopSettingSection("general",strings.getString(R.string.settings_general),R.drawable.desktop_settings_general,language,section=="general","settings-general-toggle",first=true,toggle={selectSection("general")}) {
                if(updateDisplay!=null) DesktopSettingSelect(strings.getString(R.string.language_label),listOf(LanguageChoice.CHINESE.name to strings.getString(R.string.language_chinese),LanguageChoice.ENGLISH.name to "English",LanguageChoice.SYSTEM.name to strings.getString(R.string.follow_system)),preferences.language.name,"settings-language") {updateDisplay(DisplayChange.Language(LanguageChoice.valueOf(it)))}
                AboutSettings(state.hubBuild)
            }
        }
        if(updateDisplay!=null) {
            item(key="settings-main") {
                DesktopSettingSection("main",strings.getString(R.string.settings_main),R.drawable.desktop_settings_main,strings.getQuantityString(R.plurals.settings_views_count,MainDisplay.visible(preferences.main).size,MainDisplay.visible(preferences.main).size),section=="main","main-display-toggle",toggle={selectSection("main")}) {
                    DesktopSettingSelect(strings.getString(R.string.settings_default_range),FixedPeriods.modes.map {it to strings.getString(when(it) {"month"->R.string.settings_range_month;"week"->R.string.settings_range_week;"last7"->R.string.settings_range_last7;else->R.string.settings_range_last30})},preferences.monthMode,"settings-period") {updateDisplay(DisplayChange.MonthMode(it))}
                    MainDisplaySettings(preferences,state.snapshot?.devices.orEmpty(),updateDisplay,embedded=true,viewsOnly=true,homeContent={HomeDisplaySettings(preferences,updateDisplay,state.snapshot?.limits.orEmpty())})
                    CurrencySettings(preferences,updateDisplay)
                    DesktopSettingInlineChoices(strings.getString(R.string.settings_model_ranking),listOf("tokens" to "Token","cost" to strings.getString(R.string.activity_cost)),preferences.modelRanking,"settings-ranking") {updateDisplay(DisplayChange.ModelRanking(it))}
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Text(strings.getString(R.string.hide_titles),Modifier.weight(1f),style=quotaTextStyle(11))
                        Switch(preferences.hideTitles,{updateDisplay(DisplayChange.HideTitles(it))},Modifier.semantics {contentDescription=strings.getString(R.string.hide_titles)})
                    }
                    Text(strings.getString(R.string.hide_titles_note),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key="settings-appearance") {
                val labels=mapOf("default" to R.string.appearance_graphite,"obsidian" to R.string.appearance_carbon,"porcelain" to R.string.appearance_paper)
                val palette=renderedDesktopPalette(preferences.appearance.colors)
                val preset=DesktopAppearance.presets.entries.firstOrNull {it.value==palette}?.key
                DesktopSettingSection("appearance",strings.getString(R.string.appearance_title),R.drawable.desktop_settings_appearance,strings.getString(labels[preset] ?: R.string.appearance_custom),section=="appearance","appearance-toggle",toggle={selectSection("appearance")}) {
                    DesktopSettingInlineChoices(strings.getString(R.string.settings_theme_mode),listOf(ThemeChoice.SYSTEM.name to strings.getString(R.string.follow_system),ThemeChoice.LIGHT.name to strings.getString(R.string.theme_light),ThemeChoice.DARK.name to strings.getString(R.string.theme_dark)),preferences.theme.name,"settings-theme") {updateDisplay(DisplayChange.Theme(ThemeChoice.valueOf(it)))}
                    MainDisplaySettings(preferences,state.snapshot?.devices.orEmpty(),updateDisplay,embedded=true,appearanceOnly=true)
                    BackgroundImageControl()
                    DesktopSettingInlineChoices(strings.getString(R.string.settings_reduce_motion),listOf("system" to strings.getString(R.string.follow_system),"on" to strings.getString(R.string.settings_motion_on),"off" to strings.getString(R.string.settings_motion_off)),preferences.appearance.reduceMotion,"settings-motion") {updateDisplay(DisplayChange.ReduceMotion(it))}
                    Text(strings.getString(R.string.settings_motion_note),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
                    DesktopSettingSelect(strings.getString(R.string.token_number_animation),listOf("roll" to strings.getString(R.string.token_animation_roll),"count" to strings.getString(R.string.token_animation_count)),preferences.appearance.tokenNumberAnimation,"token-number-animation") {updateDisplay(DisplayChange.TokenNumberAnimation(it))}
                    AppearanceSettings(preferences,updateDisplay,embedded=true)
                }
            }
        }
        item(key="settings-tools") {
            DesktopSettingSection("tools",strings.getString(R.string.dimension_tools),R.drawable.desktop_view_tool,"",section=="tools","settings-tools-toggle",toggle={selectSection("tools")}) {
                Text(strings.getString(R.string.tool_settings_mobile_note),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(updateDisplay!=null) ToolDisplaySettings(preferences,updateDisplay,toolQuery,{toolQuery=it})
                ModelRuleControl(state.modelNames,state.loading,model::loadModelNames)
                val prices=state.pricing
                val priceStatus=if(prices?.initialized!=true) "—" else if(prices.models.isEmpty()) strings.getString(R.string.settings_custom_pricing_none) else strings.getString(R.string.settings_custom_pricing_count,prices.models.size)
                DesktopSharedSettingHeader(strings.getString(R.string.settings_custom_pricing_title),priceStatus,"settings-open-pricing") {openShared(HubPage.PRICING)}
            }
        }
        if(updateDisplay!=null) {
            item(key="settings-limits") {
                DesktopSettingSection("limits",strings.getString(R.string.nav_limits),R.drawable.desktop_settings_limits,strings.getString(if(preferences.limits.showUsed) R.string.limit_mode_used else R.string.limit_mode_remaining),section=="limits","limit-settings-toggle",toggle={selectSection("limits")}) {
                    LimitDisplaySettings(preferences,updateDisplay,state.snapshot?.limits.orEmpty(),embedded=true)
                }
            }
        }
        item(key="settings-subscriptions") {
            val rows=state.subscriptions
            val active=rows?.count {it.coverageStop()?.let {stop->stop.isAfter(today)} ?: true}
            val total=rows?.let {SubscriptionDisplay.monthlyUsd(it,today,LocalUsageCurrency.current)}?.let {LocalUsageCurrency.current.format(it)} ?: "—"
            val summary=if(rows==null) "—" else if(rows.isEmpty()) strings.getString(R.string.settings_none_yet) else strings.getString(R.string.settings_subscription_summary,active,total)
            DesktopSettingSection("subscriptions",strings.getString(R.string.desktop_subscriptions),R.drawable.desktop_settings_subscriptions,summary,section=="subscriptions","settings-subscriptions-toggle",toggle={selectSection("subscriptions")}) {
                DesktopActionButton(onClick={openShared(HubPage.SUBSCRIPTIONS)},modifier=Modifier.testTag("settings-open-subscriptions"),visualTag="settings-open-subscriptions-visual") {Text(strings.getString(R.string.desktop_subscriptions),style=quotaTextStyle(11))}
                if(rows==null) {
                    DesktopActionButton(onClick=model::loadSubscriptions,enabled=!state.loading,modifier=Modifier.testTag("settings-subscriptions-load"),visualTag="settings-subscriptions-load-visual") {Text(strings.getString(R.string.load_subscriptions),style=quotaTextStyle(11))}
                    Text(strings.getString(R.string.subscriptions_note),style=quotaTextStyle(9))
                } else {
                    if(rows.isEmpty()) Text(strings.getString(R.string.subscriptions_empty),style=quotaTextStyle(11))
                    val accounts=SubscriptionAccounts.records(state.snapshot?.limits.orEmpty())
                    SubscriptionDisplay.keyed(rows).forEach {(identity,record)->SubscriptionRow(record,today,identity,accounts) {onSubscription(identity)}}
                    SubscriptionMonthlyTotal(rows,today)
                }
            }
        }
        if(state.snapshot!=null) item(key="settings-sync") {
            val connectionSummary=state.connectedEndpoint?.substringBefore(':')?.uppercase()
                ?: state.lastSuccessfulEndpoint?.substringBefore(':')?.uppercase()?.let {"$it · ${strings.getString(R.string.sync_endpoint_previous)}"}
                ?: strings.getString(if(state.loading) R.string.connection_pending else R.string.unknown)
            DesktopSettingSection("sync",strings.getString(R.string.settings_hub_connection),R.drawable.desktop_settings_sync,connectionSummary,section=="sync","settings-sync-toggle",last=true,toggle={selectSection("sync")}) {
                connectionContent()
            }
        }
    }
}

}

@Composable private fun HomeDisplaySettings(preferences:DisplayPreferences,updateDisplay:(DisplayChange)->Unit,limits:List<Limit>) {
    val strings=LocalResources.current
    var activityExpanded by rememberSaveable {mutableStateOf(false)}
    var limitsExpanded by rememberSaveable {mutableStateOf(false)}
    val labels=mapOf("limits" to R.string.home_module_limits,"tools" to R.string.home_module_tools,"models" to R.string.home_module_models,"devices" to R.string.home_module_devices,"activity" to R.string.home_module_activity,"trends" to R.string.desktop_trends,"sessions" to R.string.home_module_sessions)
    val order=HomeModulePreferences.order(preferences)
    val canonical=HomeModulePreferences.catalog(preferences)
    val visibleModules=HomeModulePreferences.visible(preferences)
    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        Text(strings.getString(R.string.home_modules_note),Modifier.weight(1f),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
        DesktopListIcon(strings.getString(R.string.home_modules_reset),"home-modules-reset",glyph="↺",visualSize=24,enabled=order!=canonical) {updateDisplay(DisplayChange.ResetHomeModuleOrder)}
        DesktopListIcon(strings.getString(R.string.main_views_show_all),"home-modules-show-all",drawable=R.drawable.desktop_eye,visualSize=24,enabled=order.any {it !in visibleModules}) {updateDisplay(DisplayChange.ShowAllHomeModules)}
    }
    order.forEach {id->
        val visible=id in visibleModules;val index=order.indexOf(id)
        Row(Modifier.fillMaxWidth().heightIn(min=LocalDesktopSettingsRowHeight.current),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text(strings.getString(labels.getValue(id)),Modifier.weight(1f),style=quotaTextStyle(11).copy(fontWeight=androidx.compose.ui.text.font.FontWeight.Medium),color=MaterialTheme.colorScheme.onSurface.copy(alpha=if(visible) 1f else .58f))
            if(id=="activity") DesktopListIcon(strings.getString(R.string.home_configure_activity),"home-activity-settings",drawable=R.drawable.desktop_settings_general,accent=activityExpanded,expanded=activityExpanded) {activityExpanded=!activityExpanded}
            if(id=="limits") DesktopListIcon(strings.getString(R.string.home_configure_limits),"home-quota-settings-toggle",drawable=R.drawable.desktop_settings_general,accent=limitsExpanded,expanded=limitsExpanded) {limitsExpanded=!limitsExpanded}
            DesktopListIcon(strings.getString(if(visible) R.string.tool_list_hide else R.string.tool_list_show,strings.getString(labels.getValue(id))),"home-module-$id",drawable=if(visible) R.drawable.desktop_eye else R.drawable.desktop_eye_off,checked=visible,accent=visible) {
                updateDisplay(DisplayChange.HomeModuleVisibility(id))
            }
            var ordering by remember(id) {mutableStateOf(false)}
            Box {
                DesktopListIcon(strings.getString(R.string.tool_list_reorder,strings.getString(labels.getValue(id))),"home-module-order-$id",glyph="↕") {ordering=true}
                DesktopOptionMenu(ordering,listOf(-1,1).map {direction->
                    DesktopMenuOption(direction.toString(),strings.getString(if(direction<0) R.string.desktop_move_up else R.string.desktop_move_down),"home-module-${if(direction<0) "up" else "down"}-$id",enabled=index+direction in order.indices)
                },tag="home-module-order-menu-$id",width=132.dp,dismiss={ordering=false}) {value->ordering=false;updateDisplay(DisplayChange.HomeModuleMove(id,value.toInt()))}
            }
        }
        if(id=="limits" && limitsExpanded) Column(Modifier.fillMaxWidth().padding(start=14.dp).testTag("home-quota-settings-details"),verticalArrangement=Arrangement.spacedBy(4.dp)) {HomeQuotaSettings(preferences,updateDisplay,limits)}
        if(id=="activity" && activityExpanded) Column(Modifier.fillMaxWidth().padding(start=14.dp).testTag("home-activity-details")) {
            DesktopInlineChoices(strings.getString(R.string.home_heatmap_metric),listOf("tokens" to strings.getString(R.string.home_heatmap_tokens),"cost" to strings.getString(R.string.home_heatmap_cost)),preferences.heatmapMetric,"home-heatmap") {updateDisplay(DisplayChange.HeatmapMetric(it))}
            DesktopInlineChoices(strings.getString(R.string.home_active_days_window),listOf("all" to strings.getString(R.string.home_active_days_all),"year" to strings.getString(R.string.home_active_days_year)),preferences.homeActiveDaysWindow,"home-active-days") {updateDisplay(DisplayChange.HomeActiveDaysWindow(it))}
        }
    }
}

@Composable private fun ToolDisplaySettings(preferences:DisplayPreferences,updateDisplay:(DisplayChange)->Unit,toolQuery:String,setQuery:(String)->Unit) {
    val strings=LocalResources.current
    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        Text(strings.getString(R.string.tool_list_visibility_note),Modifier.weight(1f),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
        DesktopListIcon(strings.getString(R.string.tool_list_reset_order),"tool-list-reset",glyph="↺",visualSize=24,enabled=preferences.toolOrder.isNotEmpty() || preferences.pinnedTools.isNotEmpty()) {updateDisplay(DisplayChange.ToolUsageOrder)}
        DesktopListIcon(strings.getString(R.string.tool_list_show_all),"tool-list-show-all",drawable=R.drawable.desktop_eye,visualSize=24,enabled=preferences.hiddenTools.isNotEmpty()) {updateDisplay(DisplayChange.ShowAllTools)}
    }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(strings.getString(if(preferences.toolOrder.isEmpty()) R.string.tool_list_usage_mode else R.string.tool_list_manual_mode),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
        DesktopListSearch(toolQuery,setQuery,strings.getString(R.string.tool_list_search),"tool-list-search")
        if(toolQuery.isNotBlank()) Text(strings.getString(R.string.tool_list_filter_note),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
        val locale=strings.configuration.locales[0]
        val query=toolQuery.trim().lowercase(locale)
        val tools=ToolDisplay.settingsOrder(preferences).filter {(toolLabel(it)+" "+it).lowercase(locale).contains(query)}
        if(tools.isEmpty()) Text(strings.getString(R.string.settings_list_empty),Modifier.testTag("tool-list-empty"),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
        tools.forEach {ToolSettingsRow(it,preferences,toolQuery.isNotBlank(),updateDisplay)}
    }
}
