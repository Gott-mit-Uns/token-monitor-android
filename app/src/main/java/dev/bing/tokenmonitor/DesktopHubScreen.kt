package dev.bing.tokenmonitor

import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity

import androidx.compose.foundation.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.*

enum class HubPage(val title: Int, val icon: Int) {
    HOME(R.string.desktop_home, R.drawable.desktop_view_home), TOOLS(R.string.dimension_tools, R.drawable.desktop_view_tool),
    MODELS(R.string.dimension_models, R.drawable.desktop_view_model), PROJECTS(R.string.dimension_projects, R.drawable.desktop_view_project),
    SESSIONS(R.string.dimension_sessions, R.drawable.desktop_view_session), DEVICES(R.string.devices, R.drawable.desktop_view_device),
    LIMITS(R.string.nav_limits, R.drawable.desktop_view_limits), TRENDS(R.string.desktop_trends, R.drawable.desktop_view_trends),
    SUBSCRIPTIONS(R.string.desktop_subscriptions, R.drawable.nav_limits), PRICING(R.string.dimension_pricing, R.drawable.nav_analysis),
    SETTINGS(R.string.nav_settings, R.drawable.desktop_settings)
}

@Composable internal fun BrandMark(name: String, modifier: Modifier = Modifier, respectToolIcons:Boolean=true, providerMark:Boolean=false,size:androidx.compose.ui.unit.Dp=18.dp) {
    val id=toolId(name)
    val resource=if(providerMark && id=="grok") R.drawable.brand_grok else brandResource(name)
    if((respectToolIcons && !LocalDesktopToolIcons.current) || (providerMark && resource==null)) Box(modifier.size(size).testTag("vendor-dot-$id"),contentAlignment=Alignment.Center) {
        Box(Modifier.size(6.dp).background(toolColor(name,LocalDesktopVendorPalette.current),androidx.compose.foundation.shape.CircleShape))
    } else Icon(painterResource(resource ?: R.drawable.nav_analysis), null, modifier.size(size).testTag("vendor-icon-$id"), tint = MaterialTheme.colorScheme.onSurface)
}
internal fun amountText(value: String?) = value?.let { runCatching { BigDecimal(it).stripTrailingZeros().toPlainString() }.getOrNull() } ?: "—"
private fun clock(value: String?) = value?.let { runCatching { DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.parse(it)) }.getOrNull() } ?: "—"

internal val LocalHubCalendarClock=staticCompositionLocalOf<java.time.Clock> {java.time.Clock.systemDefaultZone()}

@Composable
internal fun DesktopHubScreen(state: HubState, model: HubUiActions, localAllowed: Boolean, requestLocal: (() -> Unit)?, preferences: DisplayPreferences, updateDisplay: ((DisplayChange) -> Unit)?, openLocalSettings: (() -> Unit)?, initialPage: HubPage? = null) {
    val renderObserver = LocalHeadlineRenderObserver.current
    if (renderObserver != null) SideEffect { renderObserver("hub") }
    val strings = LocalResources.current
    val numbers = NumberFormat.getIntegerInstance(strings.configuration.locales[0])
    val colors = MaterialTheme.colorScheme
    var page by rememberSaveable { mutableStateOf(initialPage ?: if (state.snapshot == null) HubPage.SETTINGS else HubPage.HOME) }
    var previousPage by rememberSaveable {mutableStateOf(HubPage.HOME)}
    val settingsPage=page in listOf(HubPage.SETTINGS,HubPage.PRICING,HubPage.SUBSCRIPTIONS)
    val mainPage=if(settingsPage) previousPage else page
    LaunchedEffect(page) {if(!settingsPage) previousPage=page}
    var directPage by rememberSaveable {mutableStateOf<String?>(null)}
    // Desktop opens the separate dashboard from the main Trends spark. Keep its
    // own route and ranges without adding another main-window view.
    var trendsHistoryOpen by rememberSaveable(state.connectionRevision) {mutableStateOf(false)}
    val trendsDetail=page==HubPage.TRENDS && trendsHistoryOpen
    val landing=HubPage.entries.first {it.name.lowercase()==MainDisplay.preferred(preferences.main,"home")}
    val visiblePages=MainDisplay.visible(preferences.main,directPage?.let {setOf(it)} ?: emptySet())
    fun openDirect(target:HubPage) {trendsHistoryOpen=false;directPage=target.name.lowercase();page=target}
    LaunchedEffect(page,preferences.main.viewOrder,preferences.main.hiddenViews) {
        if(!settingsPage && page.name.lowercase() !in visiblePages) {directPage=null;page=HubPage.entries.first {it.name.lowercase()==MainDisplay.preferred(preferences.main,page.name.lowercase())}}
    }
    val liveRate=rememberLiveTokenRate(state.snapshot,state.live,preferences.main,state.deviceAliases)
    val periodVisible=page !in listOf(HubPage.SETTINGS,HubPage.PRICING,HubPage.SUBSCRIPTIONS) && !trendsDetail
    var selectedPrice by rememberSaveable {mutableStateOf<String?>(null)}
    var selectedSubscription by rememberSaveable {mutableStateOf<String?>(null)}
    var sharedInfo by rememberSaveable {mutableStateOf(false)}
    var settingsSection by rememberSaveable {mutableStateOf("")}
    // Plain in-memory draft survives STOP without ever entering a Saver. Closing
    // the settings route clears it, preserving the previous page-disposal policy.
    val settingsConnectionDraft = remember { HubConnectionDraft() }
    LaunchedEffect(page) {
        if (page != HubPage.SETTINGS) {
            settingsConnectionDraft.editing=false; settingsConnectionDraft.address=""
            settingsConnectionDraft.alternate=""; settingsConnectionDraft.secret=""
            settingsConnectionDraft.replacing=false; settingsConnectionDraft.submitted=false
        }
    }
    val subscriptionAccounts=remember(state.snapshot?.limits) {SubscriptionAccounts.records(state.snapshot?.limits.orEmpty())}
    val subscriptions=remember(state.subscriptions) {SubscriptionDisplay.keyed(state.subscriptions.orEmpty())}
    LaunchedEffect(page) {selectedPrice=null;selectedSubscription=null;sharedInfo=false}
    LaunchedEffect(state.pricing?.models?.map {it.model}) {if(selectedPrice!=null && state.pricing?.models?.none {it.model==selectedPrice}!=false) selectedPrice=null}
    LaunchedEffect(subscriptions.map {it.first}) {if(selectedSubscription!=null && subscriptions.none {it.first==selectedSubscription}) selectedSubscription=null}

    var range by rememberSaveable { mutableStateOf(preferences.lastPeriod) }
    var breakdownPeriodEvent by remember {mutableStateOf(BreakdownPeriodEvent())}
    fun recordBreakdownPeriod(tab:Boolean) {
        breakdownPeriodEvent=BreakdownPeriodEvent(breakdownPeriodEvent.revision+1,
            if(tab) BreakdownPeriodReason.TAB else BreakdownPeriodReason.STATIC)
    }
    var averageRateOpen by remember {mutableStateOf(false)}
    LaunchedEffect(state.connectionRevision,state.snapshot!=null) {averageRateOpen=false}
    var monthMode by rememberSaveable(preferences.monthMode) { mutableStateOf(preferences.monthMode) }
    LaunchedEffect(preferences.monthMode) {if(FixedPeriods.slot(range)=="month" && range!=preferences.monthMode) {
        recordBreakdownPeriod(false);range=preferences.monthMode
    }}
    var periodMenu by remember { mutableStateOf(false) }
    val calendarClock=LocalHubCalendarClock.current
    var calendarNow by remember(calendarClock) { mutableStateOf(calendarClock.instant()) }
    val limitsVisible=(page==HubPage.LIMITS || (page==HubPage.HOME && "limits" in preferences.homeModules)) && !state.snapshot?.limits.isNullOrEmpty()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val homeHistory = HomeCharts.pickHistory(state.history, state.snapshot?.historyPreview)
    val sharedCalendarVisible = page in listOf(HubPage.SUBSCRIPTIONS,HubPage.SETTINGS) && state.subscriptions!=null
    val homeCalendarVisible = page == HubPage.HOME && homeHistory?.daily?.isNotEmpty() == true && preferences.homeModules.any { it in listOf("activity", "trends") }
    val trendsCalendarVisible=page==HubPage.TRENDS && !trendsDetail
    val dashboardCalendarVisible=trendsDetail
    val deviceClockVisible=page==HubPage.DEVICES
    val sessionsVisible=page==HubPage.SESSIONS || (page==HubPage.HOME && "sessions" in preferences.homeModules)
    val sessionSource=remember(state.snapshot) {SessionDisplay.source(state.snapshot)}
    val homeSessions=remember(state.snapshot,calendarNow) {SessionDisplay.recent(state.snapshot,calendarNow.toEpochMilli())}
    LaunchedEffect(calendarClock, range, lifecycle, deviceClockVisible,state.snapshot?.updated,homeCalendarVisible, trendsCalendarVisible,dashboardCalendarVisible, sharedCalendarVisible, limitsVisible, sessionsVisible, sessionSource, state.snapshot?.devices?.map { it.periods["today"]?.ends }) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            calendarNow = calendarClock.instant()
            while ((FixedPeriods.derived(range) || homeCalendarVisible || trendsCalendarVisible || dashboardCalendarVisible || sharedCalendarVisible || limitsVisible || sessionsVisible || deviceClockVisible) && state.snapshot != null) {
                val boundaries = buildList {
                    if (FixedPeriods.derived(range)) addAll(state.snapshot.devices.mapNotNull { device -> runCatching { Instant.parse(device.periods["today"]?.ends) }.getOrNull() })
                    if (homeCalendarVisible || trendsCalendarVisible || dashboardCalendarVisible || sharedCalendarVisible) add(calendarNow.atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant())
                    if(deviceClockVisible) addAll(state.snapshot.devices.mapNotNull {DeviceDisplay.nextAgeAt(it.updated,calendarNow.toEpochMilli())?.let(Instant::ofEpochMilli)})
                    if(sessionsVisible) SessionDisplay.nextStatusAt(if(page==HubPage.SESSIONS) state.snapshot?.periods?.get(range)?.sessions.orEmpty() else sessionSource,calendarNow.toEpochMilli())?.let {add(Instant.ofEpochMilli(it))}
                }
                val next = boundaries.filter { it > calendarNow }.minOrNull()
                val wait = next?.let { java.time.Duration.between(calendarNow, it).toMillis() + 1 } ?: 30_000L
                kotlinx.coroutines.delay(wait.coerceIn(50, if (FixedPeriods.derived(range)) 30_000 else 60_000))
                calendarNow = calendarClock.instant()
            }
        }
    }
    var opened by rememberSaveable { mutableStateOf(state.snapshot != null) }
    var menu by remember { mutableStateOf(false) }
    val browse=rememberSaveable(state.connectionRevision,saver=PageBrowseState.Saver) {PageBrowseState()}
    var query by browse.query(page)
    // Resolve an open dialog from the current snapshot. SSE title revocation must also
    // remove the old title while a detail dialog is already visible.
    var deviceDetail by remember { mutableStateOf<String?>(null) }
    var renamed by remember { mutableStateOf<Device?>(null) }
    var deviceName by remember { mutableStateOf("") }
    var sourceInfo by remember { mutableStateOf(false) }
    var expandedBreakdown by rememberSaveable { mutableStateOf<String?>(null) }
    var toolDetailMode by remember { mutableStateOf("tokens") }
    fun rowKey(raw: String, isModel: Boolean = false) = "${page.name}:${if(isModel) "model" else "tool"}:$raw"
    fun toggleRow(raw: String, isModel: Boolean = false) { val key=rowKey(raw,isModel);expandedBreakdown=if(expandedBreakdown==key) null else key }
    LaunchedEffect(page,trendsDetail) { periodMenu = false; expandedBreakdown = null }
    val mainContentScroll = browse.scroll(page)
    val settingsContentScroll = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val trendsViewportHeight by remember(mainContentScroll) {derivedStateOf {mainContentScroll.layoutInfo.viewportSize.height}}
    var trendsStatusHeight by remember(page,range) {mutableIntStateOf(0)}
    val trendsHistoryScroll = rememberSaveable(state.connectionRevision,saver = LazyListState.Saver) { LazyListState() }
    val trendsHistoryUiState=key(state.connectionRevision) {rememberSaveableStateHolder()}
    val dashboardUi=rememberSaveable(state.connectionRevision,saver=DashboardUiState.Saver) {DashboardUiState()}
    LaunchedEffect(preferences.heatmapMetric) {dashboardUi.selectMetric(preferences.heatmapMetric=="cost")}
    fun openDashboard() {
        trendsHistoryUiState.removeState("dashboard")
        dashboardUi.reset(preferences.heatmapMetric=="cost")
        trendsHistoryOpen=true
    }
    val contentScroll=if(trendsDetail) trendsHistoryScroll else mainContentScroll
    var automaticReads by remember(state.connectionRevision) { mutableStateOf(emptySet<String>()) }
    val fixed = remember(range,state.snapshot,state.deviceHistories,calendarNow) {if (FixedPeriods.derived(range)) state.snapshot?.let { FixedPeriods.evaluate(range, it, state.deviceHistories, calendarNow) } else null}
    val rawPeriod = if (FixedPeriods.derived(range)) fixed?.period else state.snapshot?.periods?.get(range)
    val observedModels=remember(state.snapshot) {state.snapshot?.observedModelIds?.takeIf {it.isNotEmpty()} ?: state.snapshot?.periods?.values?.flatMap {it.models.keys+it.modelDetails.keys+it.sessions.flatMap {row->SessionDisplay.models(row).keys}}.orEmpty()}
    val currency = LocalUsageCurrency.current
    val period = remember(rawPeriod, currency) { rawPeriod?.let { UsageAttribution.period(it, currency) } }
    val headline = rememberHeadlineNumber(period?.tokens,range,page !in listOf(HubPage.SETTINGS,HubPage.SUBSCRIPTIONS,HubPage.PRICING) && !trendsDetail,preferences.appearance.reduceMotion,
        detailUnavailable=FixedPeriods.derived(range) && mainPage in listOf(HubPage.SESSIONS,HubPage.PROJECTS))
    var sessionPage by rememberSaveable {mutableStateOf(0)}
    var selectedSessionGroup by rememberSaveable(state.connectionRevision) {mutableStateOf<String?>(null)}
    val reviewGroupOpen=selectedSessionGroup!=null
    val sessionNow=calendarNow.toEpochMilli()
    val sessionRaw=remember(page,period?.sessions,sessionNow,preferences.hideSessionTitles,strings.configuration.locales[0],state.modelNames,observedModels) {
        if(page==HubPage.SESSIONS && !FixedPeriods.derived(range)) SessionListDisplay.raw(period?.sessions.orEmpty(),sessionNow,preferences.hideSessionTitles,strings.getString(R.string.session_archived),::toolLabel,names=state.modelNames,observed=observedModels) else emptyList()
    }
    val sessionGrouped=remember(sessionRaw,sessionNow,strings.configuration.locales[0],preferences.main.compactUnits) {SessionListDisplay.grouped(sessionRaw,strings.getString(R.string.session_review_group),
        {strings.getString(R.string.session_review_count,it)},{strings.getString(R.string.session_review_latest,it)},
        {DesktopCompactTokens.format(it.toDouble(),preferences.main.compactUnits,DesktopCompactTokens.localeTag(strings.configuration.locales[0]))},sessionNow,grokLabel=strings.getString(R.string.session_grok_group),subagentCount={strings.getQuantityString(R.plurals.session_subagent_count,it,it)})}
    val sessionReview=sessionGrouped.firstOrNull {it.id==selectedSessionGroup}
    val sessionMatching=HubWorkTrace.trace(HubWorkStage.SESSION_FILTER,units=(if(reviewGroupOpen) sessionReview?.reviews?.size ?: 0 else sessionGrouped.size).toLong()) {
        (if(reviewGroupOpen) sessionReview?.reviews.orEmpty() else sessionGrouped).filter {
            query.isEmpty() || it.name.contains(query,true) || it.subtitle.contains(query,true) || it.source?.tool?.contains(query,true)==true
        }
    }
    val sessionVisiblePage=SessionListDisplay.page(sessionMatching,sessionPage)
    val sessionBarMax=sessionMatching.maxOfOrNull {it.tokens} ?: BigInteger.ZERO
    LaunchedEffect(page,range,query) {sessionPage=0;if(page!=HubPage.SESSIONS) selectedSessionGroup=null}
    LaunchedEffect(sessionReview?.id) {if(sessionReview==null) selectedSessionGroup=null}
    LaunchedEffect(sessionVisiblePage.page) {sessionPage=sessionVisiblePage.page}
    LaunchedEffect(sessionPage,reviewGroupOpen) {if(page==HubPage.SESSIONS) contentScroll.scrollToItem(0)}
    val activeToolKey=expandedBreakdown?.takeIf { page==HubPage.TOOLS && it.startsWith("TOOLS:tool:") }?.removePrefix("TOOLS:tool:")
    val activeTool=activeToolKey?.let { period?.toolDetails?.get(it) }?.takeIf {
        ToolDisplay.usageRows(period,preferences).any { it.first==activeToolKey }
    }
    val activeToolDetail=remember(activeTool,state.modelNames,currency) { ToolDetails.project(activeTool,state.modelNames,currency) }
    val showToolDetailSwitch=activeToolDetail.canSwitch && page==HubPage.TOOLS
    val homeDay = calendarNow.atZone(ZoneId.systemDefault()).toLocalDate()
    val dashboardRawHistory=state.history.takeIf {trendsHistoryOpen}
    // The desktop IPC applies shared aliases after fetching raw Hub history.
    // Keep cached/wire history intact and project the complete retained history once.
    val dashboardHistory=remember(dashboardRawHistory,state.modelNames) {
        dashboardRawHistory?.let {DashboardHistoryPresentation.project(it,state.modelNames)}
    }
    LaunchedEffect(dashboardHistory) {
        if(dashboardHistory==null || (dashboardHistory.daily.isEmpty() && dashboardHistory.monthly.isEmpty())) dashboardUi.discardChart()
    }
    val dashboardTrend=remember(dashboardHistory,dashboardUi.range,dashboardUi.byModel,dashboardUi.mode,
        dashboardUi.chartWidth,dashboardUi.chartHeight,strings.configuration.locales[0]) {
        dashboardHistory?.let {DashboardTrendProjection.project(it,dashboardUi.range,dashboardUi.byModel,dashboardUi.mode,
            dashboardUi.chartWidth,dashboardUi.chartHeight,strings.configuration.locales[0].toLanguageTag())}
    }
    val dashboardEventKey=listOf(state.connectionRevision,dashboardHistory,state.modelNames,state.historyRenderRevision,homeDay)
    val dashboardChartMotion=rememberDashboardChartMotion(dashboardUi.committedChart,dashboardUi.committedChartEvent,dashboardUi.committedChartRevision,
        dashboardUi.committedChartReason,trendsDetail && dashboardUi.tab==DashboardTab.TRENDS && dashboardUi.committedChart!=null,preferences.appearance.reduceMotion)
    val dashboardOverviewMotion=rememberDashboardOverviewMotion(dashboardHistory,homeDay,dashboardUi.overviewWidth,dashboardEventKey,
        dashboardUi.motionRevision,dashboardUi.motionReason,trendsDetail && dashboardUi.tab==DashboardTab.OVERVIEW && dashboardUi.overviewWidth>0,
        dashboardUi.metricCost,preferences.appearance.reduceMotion,state.loading)
    val receivedDay = state.savedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
    val homeTodayPeriod = if (receivedDay == null || receivedDay == homeDay) state.snapshot?.periods?.get("today") else null
    // Main Trends uses the retained preview, exactly as the desktop main window;
    // a full dashboard read must not silently replace this compact series.
    val trendsMain=remember(state.snapshot?.historyPreview,range,homeTodayPeriod?.tokens,homeDay,fixed) {
        TrendsMainProjection.project(state.snapshot?.historyPreview,range,homeTodayPeriod?.tokens,homeDay,fixed)
    }
    // The desktop keeps the main breakdown while settings or its independent
    // dashboard is open. Keep this holder outside the conditional lazy content;
    // returning from either surface must not request a fresh zero-height entry.
    // No per-frame State is read here: only the Canvas consumes it while drawing.
    val trendsBarMotion=rememberTrendsBarMotion(
        points=trendsMain?.points,
        renderEventKey=listOf(state.connectionRevision,state.savedAt,state.snapshot,range,homeDay,fixed),
        mainViewKey=mainPage.name,
        visible=page==HubPage.TRENDS && !trendsDetail,
        preference=preferences.appearance.reduceMotion
    )
    val previewActiveDays = state.snapshot?.historyPreview?.summary?.activeDays
    val homeCharts = remember(homeHistory, homeTodayPeriod, homeDay, preferences.homeActiveDaysWindow, previewActiveDays) {
        homeHistory?.let { HomeCharts.project(it, homeTodayPeriod, homeDay, preferences.homeActiveDaysWindow, previewActiveDays) }
    }
    val homeHistoryEntry=rememberHomeHistoryEntryMotion(
        projection=homeCharts,
        mainViewKey=mainPage.name,
        visible=page==HubPage.HOME && !trendsDetail,
        // Source setPeriod renders replace Home content without requesting another entry.
        // Cache persistence time and header-only freshness are not chart content keys.
        renderEventKey=listOf(range,preferences.heatmapMetric),
        preference=preferences.appearance.reduceMotion
    )
    val homeQuotaVisible=page==HubPage.HOME && !trendsDetail
    val homeQuotaModuleVisible=homeQuotaVisible && "limits" in HomeModulePreferences.visible(preferences)
    val homeQuotaAccounts=remember(homeQuotaModuleVisible,state.snapshot?.limits,preferences.limits,
        preferences.appearance.showToolIcons,strings.configuration.locales[0],calendarNow) {
        if(homeQuotaModuleVisible) HomeQuotaLayout.project(state.snapshot?.limits.orEmpty(),preferences.limits,
            preferences.appearance.showToolIcons,strings.configuration.locales[0],calendarNow) else emptyList()
    }
    // Real Home content/settings/period changes replace its meters. A clock tick with equal quota output is only a recomposition.
    val homeQuotaRenderToken=remember(state.snapshot,state.history,state.modelNames,state.deviceAliases,
        preferences,range,homeQuotaAccounts,strings.configuration.locales[0]) {Any()}
    val homeQuotaEntry=rememberHomeQuotaEntryMotion(HomeQuotaEntryPolicy.hasMeters(homeQuotaAccounts,preferences.limits.homeBars),
        state.connectionRevision,mainPage.name,homeQuotaRenderToken,homeQuotaVisible,preferences.appearance.reduceMotion)
    val displayDevices = if (!FixedPeriods.derived(range)) state.snapshot?.devices.orEmpty() else state.snapshot?.devices.orEmpty().mapNotNull { device ->
        fixed?.devices?.get(device.id)?.let { usage ->
            val dates = fixed.deviceRanges.getValue(device.id)
            device.copy(periods = device.periods + (range to DeviceUsage(usage.tokens, usage.cost, usage.tools, "${dates.start} – ${dates.end}", null, device.periods["today"]?.zone, usage.unpriced)))
        }
    }
    // Share each event projection with the actual lazy rows. None of these scopes reads a motion frame.
    val actualBreakdownPage=page.takeIf {it in listOf(HubPage.TOOLS,HubPage.MODELS,HubPage.PROJECTS,HubPage.DEVICES,HubPage.SESSIONS) && !trendsDetail}
    val mainBreakdown=remember(actualBreakdownPage,period,state.modelNames,observedModels,preferences.modelRanking,
        preferences.hiddenTools,preferences.pinnedTools,preferences.toolOrder) {
        actualBreakdownPage?.let {MainBreakdownProjection.project(it,period,state.modelNames,observedModels,preferences)} ?: MainBreakdownProjection()
    }
    val mainProjects=remember(actualBreakdownPage,period,range,strings.configuration.locales[0]) {
        if(actualBreakdownPage==HubPage.PROJECTS && !FixedPeriods.derived(range))
            ProjectDisplay.rows(period,::toolLabel,strings.getString(R.string.project_unknown_tool)) else emptyList()
    }
    val mainProjectMax=remember(mainProjects) {mainProjects.maxOfOrNull {it.tokens} ?: BigInteger.ZERO}
    val mainDevices=remember(actualBreakdownPage,displayDevices,range) {
        if(actualBreakdownPage==HubPage.DEVICES) DeviceDisplay.rows(displayDevices,range) else emptyList()
    }
    val mainDeviceMax=remember(mainDevices,range) {
        mainDevices.maxOfOrNull {(it.periods[range]?.tokens ?: 0).coerceAtLeast(0)}?.let(BigInteger::valueOf) ?: BigInteger.ZERO
    }
    // Reuse the existing sorted, filtered and paged business projection. The motion
    // gate counts this rendered collection, not every retained session in the snapshot.
    val mainSessionRows=if(actualBreakdownPage==HubPage.SESSIONS && !FixedPeriods.derived(range))
        if(reviewGroupOpen) sessionMatching else sessionVisiblePage.rows else emptyList()
    val mainSessionFallback=remember(actualBreakdownPage,sessionMatching,sessionRaw,period,range,reviewGroupOpen,query,
        state.modelNames,observedModels,preferences,currency) {
        if(actualBreakdownPage==HubPage.SESSIONS && !FixedPeriods.derived(range) && sessionMatching.isEmpty() && sessionRaw.isEmpty() &&
            (period?.tokens ?: 0)>0 && !reviewGroupOpen && query.isEmpty())
            SessionFallbackDisplay.rows(period,state.modelNames,observedModels,preferences,currency) else emptyList()
    }
    val mainSessionFallbackMax=remember(mainSessionFallback) {mainSessionFallback.maxOfOrNull {it.barValue} ?: 0.0}
    val breakdownTargets=remember(actualBreakdownPage,mainBreakdown,mainProjects,mainProjectMax,mainDevices,mainDeviceMax,
        mainSessionRows,sessionBarMax,mainSessionFallback,mainSessionFallbackMax,range) {
        when(actualBreakdownPage) {
            HubPage.TOOLS,HubPage.MODELS->mainBreakdown.rows.map {(id,value)->
                val barValue=if(mainPage==HubPage.MODELS) mainBreakdown.barValues[id] ?: 0.0 else value?.toDouble() ?: 0.0
                BreakdownRowSpec("${mainPage.name}:$id",BreakdownMotion.bar(barValue,mainBreakdown.maximum),
                    breakdownNumericValue(value,mainBreakdown.details[id]))
            }
            HubPage.PROJECTS->mainProjects.map {BreakdownRowSpec("project:${it.id}",SessionListDisplay.bar(it.tokens,mainProjectMax),it.tokens)}
            HubPage.DEVICES->mainDevices.map {device->
                val value=BigInteger.valueOf((device.periods[range]?.tokens ?: 0).coerceAtLeast(0))
                BreakdownRowSpec("device:${device.id}",SessionListDisplay.bar(value,mainDeviceMax),value)
            }
            HubPage.SESSIONS->if(mainSessionRows.isNotEmpty()) SessionBreakdownMotionProjection.rows(mainSessionRows,sessionBarMax)
                else SessionBreakdownMotionProjection.fallback(mainSessionFallback,mainSessionFallbackMax)
            else->emptyList()
        }
    }
    val breakdownMotion=rememberBreakdownMotion(breakdownTargets.takeIf {state.snapshot!=null && actualBreakdownPage!=null},state.connectionRevision,
        mainPage.name,range,breakdownPeriodEvent,
        actualBreakdownPage!=null,
        preferences.appearance.reduceMotion)
    // Business/context changes get one opaque layout token; frames never change it.
    val rowPlacementToken=remember(actualBreakdownPage,mainBreakdown,mainProjects,mainDevices,mainSessionRows,mainSessionFallback,
        sessionNow.takeIf {actualBreakdownPage==HubPage.SESSIONS},preferences.main.sessionContextMetric,
        state.deviceAliases,preferences.hideTitles,preferences.hideSessionTitles,strings.configuration.locales[0]) {Any()}
    val rowPlacementMotion=rememberBreakdownRowPlacementMotion(
        breakdownTargets.takeIf {state.snapshot!=null && actualBreakdownPage!=null},state.connectionRevision,
        mainPage.name,range,breakdownPeriodEvent,rowPlacementToken,actualBreakdownPage!=null,
        preferences.appearance.reduceMotion)
    val quotaVisible=page==HubPage.LIMITS && !trendsDetail
    val quotaRows=remember(quotaVisible,state.snapshot?.limits,preferences.limits,strings.configuration.locales[0],calendarNow) {
        if(quotaVisible) QuotaMotionProjection.project(state.snapshot?.limits.orEmpty(),preferences.limits,
            strings.configuration.locales[0],calendarNow) else emptyMap()
    }
    val quotaTargets=remember(quotaRows) {quotaRows.values.flatMap {rows->rows.mapNotNull {it.motion}}}
    val quotaRenderToken=remember(quotaVisible,state.snapshot?.limits,preferences.limits,
        strings.configuration.locales[0],calendarNow.epochSecond/60) {Any()}
    val quotaMotion=rememberQuotaWindowMotion(quotaTargets.takeIf {quotaVisible && state.snapshot!=null},
        state.connectionRevision,mainPage.name,quotaRenderToken,quotaVisible,preferences.appearance.reduceMotion)
    BackHandler(enabled = (page != landing || trendsDetail) && state.snapshot != null) {
        if(trendsDetail) trendsHistoryOpen=false
        else if(reviewGroupOpen) selectedSessionGroup=null
        else {trendsHistoryOpen=false;directPage=null;page = if(page in listOf(HubPage.PRICING,HubPage.SUBSCRIPTIONS)) HubPage.SETTINGS else landing}
    }
    LaunchedEffect(state.snapshot != null) { if (state.snapshot != null && !opened) { opened = true; page = HubPage.HOME } }
    LaunchedEffect(preferences.hideTitles, preferences.hideSessionTitles, range) { query = "" }
    val historySignature=remember(state.snapshot?.historyRevision,state.snapshot?.historyPreview) {homeHistorySignature(state.snapshot)}
    val historyPolicy=rememberHomeHistoryReadPolicy(HistoryReadKey(state.connectionRevision,historySignature),
        state.historyReadReceipt,
        page==HubPage.HOME || trendsDetail,state.live,state.loading)
    // A failed preview-backed history read gets only the desktop's bounded retries,
    // dispatched through the same serial queue. Lifecycle cancellation forbids background reads.
    LaunchedEffect(state.connectionRevision,page,trendsDetail, range, state.live, state.loading, state.error, historyPolicy.ready, historySignature, state.snapshot?.deviceHistoryRevision, state.snapshot?.devices?.map { it.id }, state.snapshot?.modelNamesRevision, state.snapshot?.pricingRevision, state.snapshot?.subscriptionsRevision) {
        if (!state.live) automaticReads = emptySet()
        else if (!state.loading) {
            val regularReads=state.error == null || state.error==R.string.error_connection_change
            val needed = buildList<Pair<String, () -> Unit>> {
                if (regularReads && periodVisible && FixedPeriods.derived(range) && state.deviceHistories == null) add("devices:${state.snapshot?.let(FixedPeriods::signature)}" to model::loadDeviceHistory)
                if (historyPolicy.ready) add("history" to { if(model.loadHistory(false)) historyPolicy.requested() })
                if (regularReads && (trendsDetail || page in listOf(HubPage.TOOLS, HubPage.MODELS,HubPage.SESSIONS) || (page == HubPage.HOME && preferences.homeModules.any { it in listOf("tools", "models","sessions") })) && state.modelNames == null) add("models:${state.snapshot?.modelNamesRevision}" to model::loadModelNames)
                if (regularReads && page == HubPage.PRICING && state.pricing == null) add("pricing:${state.snapshot?.pricingRevision}" to model::loadPricing)
                if (regularReads && page in listOf(HubPage.SUBSCRIPTIONS,HubPage.LIMITS) && state.subscriptions == null) add("subscriptions:${state.snapshot?.subscriptionsRevision}" to model::loadSubscriptions)
            }
            needed.firstOrNull { it.first=="history" || it.first !in automaticReads }?.let { (key, read) ->
                if(key!="history") automaticReads = automaticReads + key
                read()
            }
        }
    }
    val periodIndicator=rememberDesktopPeriodIndicator(listOf("today","month","allTime").indexOf(FixedPeriods.slot(range)),periodVisible,preferences.appearance.reduceMotion)
    val periodOverlay=LocalDesktopOverlayColor.current
    val periodLine=LocalDesktopLineColor.current
    val subscriptionContext=SubscriptionReadContext(state.subscriptions.orEmpty(),subscriptionAccounts,state.snapshot?.periods?.get("month")?.toolDetails.orEmpty().mapValues {it.value.cost},homeDay,strings.configuration.locales[0],currency)
    if(trendsDetail) {
        trendsHistoryUiState.SaveableStateProvider("dashboard") {
            val dashboardFont=nativeFont(preferences.appearance.interfaceFont,androidx.compose.ui.text.font.FontFamily.SansSerif)
            val typography=MaterialTheme.typography
            MaterialTheme(typography=typography.copy(
                bodySmall=typography.bodySmall.copy(fontFamily=dashboardFont),bodyMedium=typography.bodyMedium.copy(fontFamily=dashboardFont),bodyLarge=typography.bodyLarge.copy(fontFamily=dashboardFont),
                titleMedium=typography.titleMedium.copy(fontFamily=dashboardFont),titleLarge=typography.titleLarge.copy(fontFamily=dashboardFont),
                labelSmall=typography.labelSmall.copy(fontFamily=dashboardFont),labelMedium=typography.labelMedium.copy(fontFamily=dashboardFont),labelLarge=typography.labelLarge.copy(fontFamily=dashboardFont))) {
            CompositionLocalProvider(LocalDesktopDisplayFont provides nativeFont(preferences.appearance.displayFont,dashboardFont)) {
            DesktopDashboardScreen(dashboardHistory,state.loading,state.error,dashboardUi,dashboardTrend,homeDay,preferences,updateDisplay,
                {trendsHistoryOpen=false},{model.loadHistory(true)},{directPage=null;page=HubPage.SETTINGS},dashboardChartMotion,dashboardOverviewMotion,dashboardEventKey,
                recordsHistory=dashboardRawHistory)
            }
            }
        }
        return
    }
    CompositionLocalProvider(LocalSubscriptionReadContext provides subscriptionContext) {
    DesktopBackgroundSurface {
        Scaffold(containerColor = Color.Transparent, topBar = {
            Column(Modifier.statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)) {
                val stackedPeriods = effectiveTextScale() > 1.15f
                @Composable fun title() {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    DesktopTitle(preferences.main.titleIconOnly,preferences.appearance.showLiveDot,state.live,Modifier.weight(1f),onAverage={averageRateOpen=true}) {openDirect(HubPage.HOME)}
                    if(trendsDetail) DesktopTextButton(onClick={trendsHistoryOpen=false},modifier=Modifier.testTag("trends-history-back")) {Text("← ${strings.getString(R.string.desktop_trends)}",style=quotaTextStyle(11))}
                    }
                }
                @Composable fun periods(modifier: Modifier) {
                    if (periodVisible) Row(modifier.heightIn(min=48.dp).padding(vertical=9.dp).border(1.dp, LocalDesktopTooltipBorder.current.copy(alpha=0.09f), RoundedCornerShape(10.dp)).background(LocalDesktopOverlayColor.current.copy(alpha=0.026f),RoundedCornerShape(10.dp)).padding(3.dp).desktopPeriodIndicator(periodIndicator,periodOverlay,periodLine),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                        listOf("today", "month", "allTime").forEach { slot ->
                            val active = FixedPeriods.slot(range) == slot
                            val mode = if (slot == "month") monthMode else slot
                            Box(Modifier.weight(1f)) {
                                DesktopTextButton(onClick = {
                                    if (slot == "month" && active) periodMenu = !periodMenu
                                    else {if(range!=mode) recordBreakdownPeriod(true);range = mode; periodMenu = false; updateDisplay?.invoke(DisplayChange.PeriodSelection(mode)) }
                                }, shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.textButtonColors(containerColor = Color.Transparent, contentColor = if (active) colors.primary else colors.onSurfaceVariant),
                                    contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth().then(if(stackedPeriods) Modifier.heightIn(min=22.dp) else Modifier.height(22.dp)).testTag("hub-period-$slot").semantics { selected = active; contentDescription = FixedPeriods.label(mode) }) {
                                    Text(FixedPeriods.label(mode), Modifier.fillMaxWidth().testTag("hub-period-label-$slot"), style = quotaTextStyle(9), textAlign=androidx.compose.ui.text.style.TextAlign.Center, maxLines=1, softWrap=false)
                                }
                                if(slot=="month") DesktopOptionMenu(periodMenu,FixedPeriods.modes.map {DesktopMenuOption(it,FixedPeriods.label(it),"hub-period-menu-$it")},monthMode,"hub-period-menu",width=132.dp,wrapNavigation=true,animate=true,dismiss={periodMenu=false}) {choice->if(range!=choice) recordBreakdownPeriod(false);monthMode=choice;range=choice;periodMenu=false;updateDisplay?.invoke(DisplayChange.PeriodSelection(choice))}
                            }
                        }
                    }
                }
                if (stackedPeriods) Column(Modifier.fillMaxWidth()) {
                    title()
                    periods(Modifier.fillMaxWidth())
                } else Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) {
                    Box(Modifier.weight(1f)) {title()}
                    periods(Modifier.width(154.dp))
                }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                state.error?.let { Text(strings.getString(it), color = colors.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite }) }
            }
        }, bottomBar = {
            BoxWithConstraints(Modifier.fillMaxWidth().background(colors.background.copy(alpha=0.9f)).navigationBarsPadding().padding(horizontal=14.dp,vertical=8.dp)) {
                val stacked=showToolDetailSwitch && (maxWidth<336.dp || effectiveTextScale()>1.15f)
                val rateVisible=preferences.main.showLiveRate && !settingsPage && !showToolDetailSwitch
                val stackedRate=rateVisible && (maxWidth<336.dp || effectiveTextScale()>1.15f)
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    if(stacked) Row(Modifier.fillMaxWidth().testTag("tool-detail-footer-stacked"),horizontalArrangement=Arrangement.Center) {
                        ToolDetailSwitch(activeToolDetail.mode(toolDetailMode)) { toolDetailMode=it }
                    }
                    if(page==HubPage.SESSIONS && !FixedPeriods.derived(range)) SessionPageControls(sessionVisiblePage) {sessionPage=it}
                    if(stackedRate) Row(Modifier.fillMaxWidth().testTag("live-rate-footer-stacked"),horizontalArrangement=Arrangement.Center) {LiveTokenRateControl(liveRate,preferences.main,updateDisplay)}
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Box {
                                val menuDensity=LocalDensity.current
                                var menuWidth by remember {mutableStateOf(126.dp)}
                                val current=if(settingsPage) HubPage.entries.first {it.name.lowercase()==MainDisplay.preferred(preferences.main,previousPage.name.lowercase())} else page
                                val at=visiblePages.indexOf(current.name.lowercase())
                                val next=HubPage.entries.first {it.name.lowercase()==visiblePages[(at+1).mod(visiblePages.size)]}
                                DesktopViewSwitcher(current,next,{trendsHistoryOpen=false;directPage=null;page=next},{menu=true},expanded=menu,modifier=Modifier.onSizeChanged {menuWidth=with(menuDensity) {it.width.toDp()-24.dp}})
                                DesktopViewMenu(menu,current,visiblePages.map {id->HubPage.entries.first {it.name.lowercase()==id}},width=menuWidth,dismiss={menu=false}) {target->
                                    trendsHistoryOpen=false;directPage=null;page=target;menu=false
                                }
                            }
                        }
                        if(showToolDetailSwitch && !stacked) ToolDetailSwitch(activeToolDetail.mode(toolDetailMode)) { toolDetailMode=it }
                        if(rateVisible && !stackedRate) LiveTokenRateControl(liveRate,preferences.main,updateDisplay)
                        Row(horizontalArrangement=Arrangement.spacedBy(0.dp)) {
                        listOf(if(preferences.main.swapActions) "settings" else "refresh",if(preferences.main.swapActions) "refresh" else "settings").forEach {action->
                            if(action=="refresh") DesktopFooterAction(true,strings.getString(R.string.refresh),"hub-refresh",!state.loading && state.snapshot!=null) {when(page) {HubPage.PRICING -> model.loadPricing();HubPage.SUBSCRIPTIONS -> model.loadSubscriptions();HubPage.HOME -> model.refreshSaved(forceHistory=true);HubPage.TRENDS -> if(trendsDetail) model.loadHistory(true) else model.refreshSaved();else -> model.refreshSaved()}}
                            else DesktopFooterAction(false,strings.getString(R.string.nav_settings),"hub-settings-toggle") {directPage=null;page=if(page==HubPage.SETTINGS && state.snapshot!=null) HubPage.entries.first {it.name.lowercase()==MainDisplay.preferred(preferences.main,previousPage.name.lowercase())} else HubPage.SETTINGS}
                        }
                        }
                    }
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                    if (page != HubPage.SETTINGS) Column(Modifier.fillMaxSize().then(if(trendsDetail) Modifier.testTag("trends-history-detail") else Modifier)) {
                    val hasHeadline=state.snapshot!=null && page !in listOf(HubPage.SUBSCRIPTIONS,HubPage.PRICING) && !trendsDetail
                    if(hasHeadline) Box(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=8.dp).testTag("hub-pinned-total")) {DesktopTotal(period,headline)}
                    val rowGap=when(page) {HubPage.HOME->12.dp;HubPage.SUBSCRIPTIONS->6.dp;HubPage.SESSIONS->7.dp;HubPage.DEVICES,HubPage.PROJECTS->12.dp;else->12.dp}
                    LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("hub-content"), state = contentScroll, contentPadding = PaddingValues(start=14.dp,end=14.dp,top=if(hasHeadline) rowGap else 8.dp,bottom=8.dp), verticalArrangement = Arrangement.spacedBy(rowGap)) {
                        if (state.snapshot == null) {
                            item { Text(strings.getString(R.string.not_connected)) }
                            item { Button(onClick = { page = HubPage.SETTINGS }) { Text(strings.getString(R.string.connect_hub)) } }
                        } else {
                            if (periodVisible && fixed != null) item {
                                Box(Modifier.onSizeChanged {if(page==HubPage.TRENDS) trendsStatusHeight=it.height}) {
                                    FixedPeriodStatus(fixed, state.deviceHistories?.signature == state.snapshot?.let(FixedPeriods::signature), state.loading, model::loadDeviceHistory)
                                }
                            }
                            when (page) {
                                HubPage.HOME -> {
                                    HomeModulePreferences.visible(preferences).forEach { module ->
                                        when (module) {
                                        "limits" -> if (state.snapshot.limits.isNotEmpty()){
                                        val accounts=homeQuotaAccounts
                                        item {DesktopHomeModule(strings.getString(R.string.desktop_limits),R.drawable.desktop_view_limits,bodyGap=12,open={openDirect(HubPage.LIMITS)}) {
                                            if(accounts.isEmpty()) DesktopHomeEmpty(strings.getString(R.string.limits_empty))
                                            accounts.forEach {account->HomeQuotaRow(account,homeQuotaEntry) {openDirect(HubPage.LIMITS)}}
                                        }}
                                    }
                                        "tools" -> {
                                            val attributed=period?.let {UsageAttribution.rows(it.tools,it.toolDetails,it.tokens,it.cost,it.unpriced,currency)}.orEmpty()
                                            val labels=attributed.keys.associateWith {if(it==UsageAttribution.UNATTRIBUTED) strings.getString(R.string.token_unclassified) else toolLabel(it)}
                                            val rows=HomeUsageDisplay.rows(attributed.map {(id,value)->id to value.tokens?.let(BigInteger::valueOf)},period?.tokens,labels,true,strings.configuration.locales[0])
                                            item {DesktopHomeModule(strings.getString(R.string.desktop_tools),R.drawable.desktop_view_tool,open={openDirect(HubPage.TOOLS)}) {
                                                if(rows.isEmpty()) DesktopHomeEmpty(strings.getString(R.string.home_tools_empty))
                                                rows.forEach {row->HomeUsageRow(row,false)}
                                            }}
                                        }
                                        "models" -> {
                                            val observed=observedModels
                                            val counts=period?.models.orEmpty()
                                            val projected=state.modelNames?.project(counts,observed) ?: counts.mapValues {BigInteger.valueOf(it.value)}
                                            val remainder=BigInteger.valueOf(period?.tokens ?: 0)-projected.values.fold(BigInteger.ZERO) {a,b->a+b}
                                            val values=(projected+if(remainder.signum()>0) mapOf(UsageAttribution.UNATTRIBUTED to remainder) else emptyMap()).toList().sortedByDescending {it.second}
                                            val labels=values.associate {it.first to if(it.first==UsageAttribution.UNATTRIBUTED) strings.getString(R.string.token_unclassified) else it.first}
                                            val rows=HomeUsageDisplay.rows(values,period?.tokens,labels,false,strings.configuration.locales[0])
                                            item {DesktopHomeModule(strings.getString(R.string.desktop_models),R.drawable.desktop_view_model,open={openDirect(HubPage.MODELS)}) {
                                                if(rows.isEmpty()) DesktopHomeEmpty(strings.getString(R.string.home_models_empty))
                                                rows.forEach {row->HomeUsageRow(row,true,period?.unknownModelSource)}
                                            }}
                                        }
                                        "sessions" -> {
                                            item {
                                                val running=homeSessions.count {SessionDisplay.activity(it,calendarNow.toEpochMilli())=="running"}
                                                DesktopHomeModule(strings.getString(R.string.dimension_sessions),R.drawable.desktop_view_session,
                                                    if(running>0) strings.getString(R.string.home_sessions_running,running) else null,"home-sessions-jump",open={openDirect(HubPage.SESSIONS)}) {
                                                    if(homeSessions.isEmpty()) DesktopHomeEmpty(strings.getString(R.string.home_sessions_empty))
                                                    homeSessions.forEach {row->HomeSessionRow(row,calendarNow.toEpochMilli(),preferences.hideSessionTitles,names=state.modelNames,observed=observedModels) {openDirect(HubPage.SESSIONS)}}
                                                }
                                            }
                                        }
                                        "devices" -> {
                                            val homeDevices=DeviceDisplay.home(displayDevices,range,state.deviceAliases)
                                            item {DesktopHomeModule(strings.getString(R.string.desktop_devices),R.drawable.desktop_view_device,open={openDirect(HubPage.DEVICES)}) {
                                                if(homeDevices.isEmpty()) DesktopHomeEmpty(strings.getString(R.string.home_devices_empty))
                                                else Column(Modifier.testTag("home-device-list"),verticalArrangement=Arrangement.spacedBy(6.dp)) {homeDevices.forEach {HomeDeviceLine(it)}}
                                            }}
                                        }
                                        "activity" -> {
                                        item {DesktopHomeModule(strings.getString(R.string.desktop_activity),R.drawable.desktop_view_trends,
                                            meta=homeCharts?.let {strings.getString(if(preferences.homeActiveDaysWindow=="year") R.string.home_active_days_year_caption else R.string.home_active_days_caption,it.activeDays.toString())},open={openDirect(HubPage.TRENDS)}) {
                                            homeCharts?.let {charts ->
                                                CompositionLocalProvider(LocalHomeHistoryEntryMotion provides homeHistoryEntry) {
                                                    DesktopActivityPreview(charts,preferences.heatmapMetric=="cost",compactHome=true)
                                                    DesktopTrendPreview(charts)
                                                }
                                            } ?: ChartLoad(model,state.loading)
                                        }}
                                    }
                                            else -> Unit
                                        }
                                    }
                                }
                                HubPage.TOOLS, HubPage.MODELS -> {
                                    val rows=mainBreakdown.rows
                                    val details=mainBreakdown.details
                                    if(rows.isEmpty()) item {Text(if(page==HubPage.TOOLS && mainBreakdown.allRowCount>0)
                                        strings.getString(R.string.tool_list_all_hidden) else strings.getString(R.string.dimension_no_data,strings.getString(page.title)))}
                                    items(rows,key={"breakdown:${page.name}:${it.first}"}) {(name,value)->
                                      DesktopBreakdownMotionRow("${mainPage.name}:$name",rowPlacementToken,rowPlacementMotion) {
                                        BreakdownLine(if(name==UsageAttribution.UNATTRIBUTED) strings.getString(R.string.token_unclassified) else if(page==HubPage.TOOLS) toolLabel(name) else name,
                                            name,value,details[name],mainBreakdown.maximum,isModel=page==HubPage.MODELS,
                                            unknownModelSource=period?.unknownModelSource,names=state.modelNames,
                                            expanded=expandedBreakdown==rowKey(name,page==HubPage.MODELS),toggle={toggleRow(name,page==HubPage.MODELS)},
                                            detailMode=if(page==HubPage.TOOLS) toolDetailMode else "tokens",
                                            barValue=if(page==HubPage.MODELS) mainBreakdown.barValues[name] else null,
                                            motion=breakdownMotion,motionKey="${mainPage.name}:$name")
                                      }
                                    }
                                }
                                HubPage.SESSIONS -> {
                                    if(FixedPeriods.derived(range)) item {Text(strings.getString(R.string.fixed_period_no_details),color=colors.onSurfaceVariant)}
                                    else {
                                        if(directPage=="sessions" && !reviewGroupOpen) item {SessionReturn(strings.getString(R.string.session_return_home),"session-return-home") {directPage=null;page=landing}}
                                        if(reviewGroupOpen) {
                                            item {SessionReturn(sessionReview?.name ?: strings.getString(R.string.session_review_group),"session-return-list") {selectedSessionGroup=null}}
                                            sessionReview?.let {summary->item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                                Text(strings.getString(R.string.session_review_count,summary.reviews.size),style=quotaTextStyle(10),color=colors.onSurfaceVariant)
                                                Text("${numbers.format(summary.tokens)} · ${usageCostText(summary.cost,summary.unpricedTotal)}",style=quotaTextStyle(10),color=colors.onSurfaceVariant)
                                            }}}
                                        }
                                        if(!reviewGroupOpen && (period?.sessionsOmitted ?: 0)>0) item {Text(strings.getString(R.string.session_details_omitted),style=quotaTextStyle(10),color=colors.onSurfaceVariant)}
                                        if(sessionMatching.isNotEmpty()) items(mainSessionRows,key={"full-session:${state.connectionRevision}:$range:${it.id}"}) {row->
                                            val motionKey=SessionBreakdownMotionProjection.key(row.id)
                                            DesktopBreakdownMotionRow(motionKey,rowPlacementToken,rowPlacementMotion) {
                                                DesktopSessionRow(row,sessionBarMax,sessionNow,reviewRun=reviewGroupOpen && sessionReview?.groupKind=="review",reduceMotion=preferences.appearance.reduceMotion,
                                                    names=state.modelNames,observed=observedModels,motion=breakdownMotion,motionKey=motionKey) {selectedSessionGroup=row.id}
                                            }
                                        }
                                        else if(sessionRaw.isEmpty() && (period?.tokens ?: 0)>0 && !reviewGroupOpen && query.isEmpty()) {
                                            items(mainSessionFallback,key={"session-fallback:${it.model}:${it.id}"}) {row->
                                                val motionKey=SessionBreakdownMotionProjection.fallbackKey(row)
                                                DesktopBreakdownMotionRow(motionKey,rowPlacementToken,rowPlacementMotion) {
                                                    BreakdownLine(if(row.id==UsageAttribution.UNATTRIBUTED) strings.getString(R.string.token_unclassified) else if(row.model) row.id else toolLabel(row.id),
                                                        row.id,row.tokens,row.detail,mainSessionFallbackMax,isModel=row.model,unknownModelSource=period?.unknownModelSource,names=state.modelNames,
                                                        expanded=expandedBreakdown==rowKey(row.id,row.model),toggle={toggleRow(row.id,row.model)},detailMode="tokens",barValue=row.barValue,
                                                        motion=breakdownMotion,motionKey=motionKey)
                                                }
                                            }
                                        } else item {Text(strings.getString(R.string.dimension_no_summary,strings.getString(page.title)),style=quotaTextStyle(10),color=colors.onSurfaceVariant)}
                                    }
                                }
                                HubPage.PROJECTS -> {
                                    if(FixedPeriods.derived(range)) item {Text(strings.getString(R.string.fixed_period_no_details),color=colors.onSurfaceVariant)}
                                    else {
                                        if(period?.projectsIncomplete==true) item {Text(strings.getString(R.string.projects_details_incomplete),style=quotaTextStyle(10),color=colors.onSurfaceVariant)}
                                        items(mainProjects,key={"project:${it.id}"}) {row->
                                            DesktopBreakdownMotionRow("project:${row.id}",rowPlacementToken,rowPlacementMotion) {
                                                DesktopProjectRow(row,mainProjectMax,preferences.hideTitles,motion=breakdownMotion)
                                            }
                                        }
                                    }
                                }
                                HubPage.DEVICES -> {
                                    items(mainDevices,key={"device:${it.id}"}) {device->
                                        DesktopBreakdownMotionRow("device:${device.id}",rowPlacementToken,rowPlacementMotion) {
                                            DesktopDeviceRow(device,state.deviceAliases[device.id],range,mainDeviceMax,calendarNow.toEpochMilli(),motion=breakdownMotion) {deviceDetail=device.id}
                                        }
                                    }
                                }
                                HubPage.LIMITS -> {
                                    if (state.snapshot.limits.isEmpty()) item { Text(strings.getString(R.string.limits_empty)) }
                                    val byProvider=LimitDisplay.groups(state.snapshot.limits).groupBy {it.first().provider}
                                    val ordered=LimitPreferences.order(preferences.limits.providerOrder)+byProvider.keys.filter {it !in LimitPreferences.providers}
                                    ordered.forEach {provider->
                                        val accounts=byProvider[provider] ?: return@forEach
                                        val peers=accounts.map {it.first()}
                                        val mimo=if(provider=="mimo") LimitPreferences.mimoGroups(accounts) else emptyList()
                                        val grouped=accounts.size>1 || (provider=="mimo" && peers.any {it.account in setOf("Console","Desktop Membership","Membership")})
                                        if(grouped) item {
                                            val marks=peers.map(LimitProviderLayout::adapterMark).distinct()
                                            Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically) {
                                                BrandMark(marks.singleOrNull() ?: peers.first().provider,respectToolIcons=false,providerMark=true,size=12.dp);Spacer(Modifier.width(8.dp))
                                                Text(peers.first().displayProvider(strings),Modifier.weight(1f),style=quotaTextStyle(12))
                                                val count=if(provider=="mimo") mimo.size else accounts.size
                                                if(provider!="mimo" || count>1) SubscriptionPlanControl(strings.getString(if(provider=="volcengine") R.string.limit_plan_count else R.string.limit_account_count,count),buildJsonObject {
                                                    put("provider",provider);put("accountGroup",true);put("groupAccounts",JsonArray(accounts.map(LimitProviderLayout::record)))
                                                },true,"limit-plan-group-$provider")
                                            }
                                        }
                                        if(provider=="mimo" && grouped) {
                                            val representatives=mimo.map {group->group.firstOrNull {it.first().presentation.identity.email?.isNotBlank()==true}?.first() ?: group.first().first()}
                                            mimo.forEachIndexed {index,group->
                                                if(mimo.size>1) item {Text(LimitDisplay.accountTitle(representatives[index].copy(provider=""),representatives.map {it.copy(provider="")},index,preferences.limits.maskEmails),style=quotaTextStyle(11),modifier=Modifier.testTag("mimo-account-$index"))}
                                                items(group) {windows->val first=windows.first();DesktopAccount(windows,showAccount=false,peers=peers,accountIndex=peers.indexOf(first),showProvider=false,now=calendarNow,titleOverride=if(first.status!=null && first.status!="ok" && first.stale!=true) first.account.orEmpty() else "",quotaRows=quotaRows[QuotaMotionProjection.accountRowsKey(windows)],quotaMotion=quotaMotion)}
                                            }
                                        } else items(accounts) { windows -> DesktopAccount(windows,showAccount=grouped,peers=peers,accountIndex=peers.indexOf(windows.first()),showProvider=!grouped,now=calendarNow,quotaRows=quotaRows[QuotaMotionProjection.accountRowsKey(windows)],quotaMotion=quotaMotion) }
                                    }
                                }
                                HubPage.TRENDS -> {
                                        trendsMain?.let {overview->item {
                                            val height=with(LocalDensity.current) {(trendsViewportHeight-trendsStatusHeight).toDp()} - rowGap - 8.dp - (if(fixed!=null) rowGap else 0.dp) - (if(overview.points.isEmpty()) 48.dp+rowGap else 0.dp)
                                            DesktopTrendsMain(overview,onOpenHistory=::openDashboard,availableHeight=height.coerceAtLeast(0.dp).takeIf {trendsViewportHeight>0},barMotion=trendsBarMotion)
                                        }}
                                        // Android has no desktop tray dashboard entry. Its independent
                                        // history must remain reachable when the main series is empty
                                        // or a derived headline is temporarily unavailable.
                                        if(trendsMain==null || trendsMain.points.isEmpty()) item {
                                            DesktopActionButton(onClick=::openDashboard,modifier=Modifier.testTag("trends-main-open-history")) {Text(strings.getString(R.string.trends_open_history))}
                                        }
                                }
                                HubPage.SUBSCRIPTIONS -> {
                                    val today=calendarNow.atZone(ZoneId.systemDefault()).toLocalDate()
                                    if(state.subscriptions==null) {
                                        item {DesktopActionButton(onClick=model::loadSubscriptions,enabled=!state.loading,modifier=Modifier.heightIn(min=48.dp).testTag("shared-subscriptions-load"),visualTag="shared-subscriptions-load-visual") {Text(strings.getString(R.string.load_subscriptions))}}
                                        item {Text(strings.getString(R.string.subscriptions_note),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}
                                    } else {
                                        item {SharedDataHeader(strings.getString(HubPage.SUBSCRIPTIONS.title),strings.getQuantityString(R.plurals.settings_subscription_count,state.subscriptions.size,state.subscriptions.size),"shared-subscriptions",{sharedInfo=true})}
                                        if(state.subscriptions.isEmpty()) item {Text(strings.getString(R.string.subscriptions_empty))}
                                        items(subscriptions,key={"subscription:${it.first}"}) { (identity,record) -> SubscriptionRow(record,today,identity,subscriptionAccounts) {selectedSubscription=identity} }
                                        if(state.subscriptions.isNotEmpty()) item {SubscriptionMonthlyTotal(state.subscriptions,today)}
                                    }
                                }
                                HubPage.PRICING -> {
                                    if(state.pricing==null) {
                                        item {DesktopActionButton(onClick=model::loadPricing,enabled=!state.loading,modifier=Modifier.heightIn(min=48.dp).testTag("shared-pricing-load"),visualTag="shared-pricing-load-visual") {Text(strings.getString(R.string.load_pricing))}}
                                        item {Text(strings.getString(R.string.pricing_not_loaded),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}
                                    } else item {SharedDataHeader(strings.getString(R.string.dimension_pricing),strings.getQuantityString(R.plurals.shared_pricing_summary,state.pricing.models.size,state.pricing.models.size),"shared-pricing",{sharedInfo=true})}
                                    item {SearchField(query,{query=it},strings.getString(R.string.dimension_pricing))}
                                    state.pricing?.let {prices ->
                                        if(!prices.initialized) item {Text(strings.getString(R.string.pricing_uninitialized))}
                                        else if(prices.models.isEmpty()) item {Text(strings.getString(R.string.pricing_empty))}
                                        if(prices.models.isNotEmpty() && prices.models.none {it.model.contains(query,true)}) item {Text(strings.getString(R.string.pricing_no_match))}
                                        items(prices.models.filter {it.model.contains(query,true)},key={"price:${it.model}"}) {price -> PricingRow(price) {selectedPrice=price.model} }
                                    }
                                }
                                else -> Unit
                            }
                            item { DesktopTextButton(onClick = { sourceInfo = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.testTag("hub-source-info")) {
                                Text("${if (state.live) strings.getString(R.string.live_foreground) else strings.getString(R.string.desktop_cache)} · ${clock(state.snapshot.updated)} · ${model.endpointType().uppercase()}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                            } }
                        }
                    }
                    }
                    DesktopSettingsPane(page == HubPage.SETTINGS) {
                        HubSettingsContent(state, model, localAllowed, requestLocal, preferences, updateDisplay, openLocalSettings,settingsSection,{settingsSection=if(settingsSection==it) "" else it},settingsContentScroll,calendarNow.atZone(ZoneId.systemDefault()).toLocalDate(),{selectedSubscription=it}, {target->directPage=null;page=target},settingsConnectionDraft) { opened = false }
                    }
                }
            }
        }
    }
    if(averageRateOpen) PeriodAverageRateDialog(rawPeriod?.averageRate,range,preferences.main,updateDisplay) {averageRateOpen=false}
    state.pricing?.models?.firstOrNull {page==HubPage.PRICING && it.model==selectedPrice}?.let {PriceDetails(it) {selectedPrice=null}}
    subscriptions.firstOrNull {page in listOf(HubPage.SUBSCRIPTIONS,HubPage.SETTINGS) && it.first==selectedSubscription}?.second?.let {SubscriptionDetails(it,calendarNow.atZone(ZoneId.systemDefault()).toLocalDate(),subscriptionAccounts) {selectedSubscription=null}}
    if(sharedInfo && page in listOf(HubPage.PRICING,HubPage.SUBSCRIPTIONS)) CompactUsageDialog(strings.getString(page.title),"shared-source-details",{sharedInfo=false}) {
        Text(strings.getString(if(page==HubPage.PRICING) R.string.pricing_intro else R.string.subscriptions_note),style=MaterialTheme.typography.bodySmall)
        if(page==HubPage.PRICING) state.pricing?.let {prices -> Text(strings.getString(R.string.shared_revision,prices.revision.toString(),prices.updated ?: strings.getString(R.string.update_time_unknown)),style=MaterialTheme.typography.bodySmall)}
        Text(strings.getString(R.string.shared_readonly_note),style=MaterialTheme.typography.bodySmall)
    }
    displayDevices.firstOrNull { it.id == deviceDetail }?.let { device -> DeviceDetails(device, state.deviceAliases[device.id], { deviceDetail = null }, {
        renamed = device; deviceName = state.deviceAliases[device.id].orEmpty(); deviceDetail = null
    }, initialRange = range) }
    renamed?.let { device -> AlertDialog(onDismissRequest = { renamed = null }, title = { Text(strings.getString(R.string.device_local_name)) }, text = {
        Column { Text(strings.getString(R.string.rename_device_note, device.name)); OutlinedTextField(deviceName, { deviceName = it }, label = { Text(strings.getString(R.string.name_label)) }, singleLine = true) }
    }, confirmButton = { DesktopTextButton(onClick = { model.renameDevice(device.id, deviceName); renamed = null }) { Text(strings.getString(R.string.save)) } }, dismissButton = { DesktopTextButton(onClick = { model.renameDevice(device.id, ""); renamed = null }) { Text(strings.getString(R.string.restore_name)) } }) }
    if (sourceInfo) AlertDialog(onDismissRequest = { sourceInfo = false }, title = { Text(strings.getString(R.string.desktop_updated)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.getString(R.string.hub_updated_at) + "\n" + (state.snapshot?.updated ?: "—"))
            Text(strings.getString(R.string.snapshot_read_at, state.savedAt?.let { Instant.ofEpochMilli(it).toString() } ?: "—"))
            Text(strings.getString(if (state.live) R.string.live_foreground else R.string.saved_snapshot))
        }
    }, confirmButton = { DesktopTextButton(onClick = { sourceInfo = false }) { Text(strings.getString(R.string.close)) } })
    }
}

@Composable private fun DesktopTotal(period: Period?,headlineState:State<HeadlineFrame>) {
    val renderObserver = LocalHeadlineRenderObserver.current
    if (renderObserver != null) SideEffect { renderObserver("total") }
    val headline = headlineState.value
    val strings = LocalResources.current
    var details by remember { mutableStateOf(false) }
    val locale = strings.configuration.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val value = headline.value?.let(numbers::format) ?: "—"
    val widest = remember(headline.widest,locale) { headline.widest?.let(numbers::format) ?: "—" }
    val main=LocalMainDisplay.current;val tag=DesktopCompactTokens.localeTag(locale)
    val compact=remember(period?.tokens,main.compactTotal,main.compactUnits,tag) {
        period?.tokens?.takeIf {main.compactTotal && it.toDouble()>=DesktopCompactTokens.threshold(main.compactUnits,tag)}?.let {"≈ "+DesktopCompactTokens.format(it.toDouble(),main.compactUnits,tag)}
    }
    val measurer=rememberTextMeasurer();val density=LocalDensity.current
    val fontResolver=LocalFontFamilyResolver.current
    val base=(LocalConfiguration.current.screenWidthDp*0.11f).coerceIn(30f,46f)
    val originalNumberStyle=MaterialTheme.typography.bodySmall.copy(fontFamily=LocalDesktopDisplayFont.current,fontSize=base.sp,fontWeight=FontWeight.Medium,lineHeight=(base*1.05f).sp,fontFeatureSettings="tnum")
    val compactStyle=quotaTextStyle(15)
    val suffixWidth=remember(compact,compactStyle,density.density,density.fontScale,measurer,fontResolver) {
        compact?.let {measurer.measure(AnnotatedString(it),compactStyle).size.width/density.density+4} ?: 0f
    }
    val natural=remember(widest,originalNumberStyle,density.density,density.fontScale,measurer,fontResolver) {
        measurer.measure(AnnotatedString(widest),originalNumberStyle).size.width/density.density
    }
    Column(Modifier.fillMaxWidth().padding(top=8.dp,bottom=14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(strings.getString(R.string.desktop_total),color=MaterialTheme.colorScheme.onSurfaceVariant,style=quotaTextStyle(11))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Reserve the suffix first, then fit the exact number down to 24sp.
            // System scaling still applies to that readability floor; stack only if necessary.
            val available = (maxWidth.value - suffixWidth - 2f).coerceAtLeast(0f)
            val numberStyle = remember(widest,originalNumberStyle,available,density,fontResolver) {
                fun style(size:Float)=originalNumberStyle.copy(fontSize=size.sp,lineHeight=(size*1.05f).sp)
                fun width(size:Float)=measurer.measure(AnnotatedString(widest),style(size)).size.width/density.density
                if(natural <= available) originalNumberStyle else {
                    // Actual glyph measurements account for font hinting and sp rounding.
                    var low=24f;var high=base
                    repeat(10) {val middle=(low+high)/2f;if(width(middle)<=available) low=middle else high=middle}
                    style(low)
                }
            }
            val fittedWidth = measurer.measure(AnnotatedString(widest),numberStyle).size.width / density.density
            val stacked = fittedWidth + suffixWidth + 1f > maxWidth.value
            @Composable fun number(modifier:Modifier) {
                Text(value,color=LocalDesktopNumberColor.current,style=numberStyle,
                    softWrap=stacked,maxLines=if(stacked) Int.MAX_VALUE else 1,
                    modifier=modifier.testTag("hub-total-tokens").heightIn(min=48.dp).wrapContentHeight().clickable(interactionSource=remember {androidx.compose.foundation.interaction.MutableInteractionSource()},indication=null,enabled=period!=null,onClickLabel=strings.getString(R.string.token_components)) {details=true})
            }
            @Composable fun approximation(modifier:Modifier=Modifier) {
                compact?.let {Text(it,modifier=modifier.then(if(stacked) Modifier.fillMaxWidth() else Modifier.width((suffixWidth-4f+1f).dp)).testTag("hub-total-compact"),style=compactStyle,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            if(stacked) Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                number(Modifier.fillMaxWidth())
                approximation()
            } else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                number(Modifier.weight(1f).alignByBaseline())
                approximation(Modifier.alignByBaseline())
            }
        }
        Text(LocalUsageCurrency.current.headline(period?.cost,period?.unpriced),modifier=Modifier.testTag("hub-total-cost"),color=MaterialTheme.colorScheme.onSurfaceVariant,style=quotaTextStyle(12))
        period?.unpriced?.takeIf { it > 0 }?.let { Text(strings.getString(R.string.unpriced_note, NumberFormat.getIntegerInstance().format(it)), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) }
    }
    if(details) CompactUsageDialog(strings.getString(R.string.token_components),"hub-token-components",{ details=false }) {
        val projection=ToolDetails.project(period?.let { UsageBreakdown(it.tokens,it.cost,it.components) })
        if(projection.hasTokens) ToolTokenDetails(projection.tokens,"total-token-detail",strings)
        else Text(strings.getString(R.string.token_details_unavailable),style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun SearchField(query: String, change: (String) -> Unit, label: String) {
    OutlinedTextField(query, change, label = { Text(LocalResources.current.getString(R.string.search_dimension, label)) }, singleLine = true, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth())
}
@Composable private fun BreakdownLine(label: String, raw: String, value: BigInteger?, breakdown: UsageBreakdown?, maximum: Double, isModel: Boolean = false, unknownModelSource:String? = null, names: ModelNames? = null, expanded: Boolean, toggle: () -> Unit, detailMode: String,barValue:Double?=null,motion:State<BreakdownMotionFrame>?=null,motionKey:String="${if(isModel) "model" else "tool"}:$raw") {
    val strings = LocalResources.current; val numbers = NumberFormat.getIntegerInstance(strings.configuration.locales[0]); val colors = MaterialTheme.colorScheme
    val palette=LocalDesktopVendorPalette.current
    val costLabel = usageCostText(breakdown?.cost, breakdown?.unpriced)
    val costOnly = (value == null || value.signum() == 0) && breakdown?.cost?.let { runCatching { BigDecimal(it).signum() > 0 }.getOrDefault(false) } == true
    val primary = if (costOnly) costLabel else value?.let(numbers::format) ?: "—"
    val projection=ToolDetails.project(breakdown,if(isModel) null else names,LocalUsageCurrency.current)
    val tag="${if(isModel) "model" else "tool"}-$raw"
    val barTarget=BreakdownMotion.bar(barValue ?: value?.toDouble() ?: 0.0,maximum)
    val observer=LocalHeadlineRenderObserver.current
    SideEffect {observer?.invoke("breakdown-row:$motionKey")}
    @Composable fun primaryText() {
        if(!costOnly && value!=null && motion!=null) BreakdownAnimatedNumber(value,motion,motionKey,Modifier.testTag("breakdown-tokens-$tag"),style=quotaTextStyle(12))
        else Text(primary,Modifier.testTag("breakdown-tokens-$tag"),style=quotaTextStyle(12))
    }
    Column(Modifier.fillMaxWidth()) {
      // The complete heading and bar share one touch target; the compact heading
      // itself keeps the desktop geometry rather than acquiring a 48dp spacer.
      Column(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("breakdown-$tag").then(if(projection.canExpand) Modifier.clickable(role=Role.Button,onClick=toggle) else Modifier).semantics { contentDescription = "$label, $primary"; if(projection.canExpand) { stateDescription=strings.getString(if(expanded) R.string.desktop_collapse else R.string.desktop_expand); selected=expanded } }.padding(bottom=10.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val formatted = primary
            val scale = LocalDensity.current.fontScale
            val availableWidth = maxWidth.value
            val stacked = scale > 1.15f || availableWidth < 320f || formatted.length > 15
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BreakdownMark(raw,isModel,unknownModelSource,10.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(label, Modifier.weight(1f), style = quotaTextStyle(12), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!stacked) Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        primaryText()
                        if (!costOnly) Text(costLabel, color = colors.onSurfaceVariant, style = quotaTextStyle(10))
                    }
                }
                if (stacked) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                    primaryText()
                    if (!costOnly) Text(costLabel, color = colors.onSurfaceVariant, style = quotaTextStyle(10))
                }
            }
        }
        LinearProgressIndicator(progress = {motion?.value?.bar(motionKey,barTarget) ?: barTarget}, color = if (isModel) modelColor(raw,palette) else toolColor(raw,palette), trackColor = LocalDesktopSunkenColor.current.copy(alpha = 0.46f), modifier = Modifier.fillMaxWidth().height(6.dp).testTag("breakdown-color-$tag"), gapSize = 0.dp, drawStopIndicator = {})
      }
        DesktopAccordion(expanded && projection.canExpand,"tool-details-motion-$tag",kind=DesktopDisclosureKind.BREAKDOWN_DETAILS) {
            Box(Modifier.padding(bottom=10.dp)) {ToolDetailBody(projection,projection.mode(detailMode),"tool-detail-$tag")}
        }
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.45f))
    }
}
@Composable private fun ChartLoad(model: HubUiActions, loading: Boolean) { DesktopActionButton(onClick = { model.loadHistory(true) }, enabled = !loading, modifier=Modifier.testTag("history-load"),visualTag="history-load-visual") { Text(LocalResources.current.getString(R.string.desktop_load_charts)) } }
