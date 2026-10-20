package dev.bing.tokenmonitor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import java.math.BigInteger
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** The desktop's independent Dashboard window, adapted to Android navigation and touch. */
@Composable internal fun DesktopDashboardScreen(
    history:HubHistory?,loading:Boolean,error:Int?,ui:DashboardUiState,
    trend:DashboardTrendModel?,today:LocalDate,preferences:DisplayPreferences,
    updateDisplay:((DisplayChange)->Unit)?,onBack:()->Unit,onRefresh:()->Unit,onSettings:()->Unit,
    chartMotion:State<DashboardChartFrame>?=null,overviewMotion:DashboardOverviewMotionState?=null,chartEventKey:Any?=history,
    recordsHistory:HubHistory?=history,historyRead:HistoryReadPresentation=HistoryReadPresentation(),
    refreshFeedback:Int=0
) {
    val strings=LocalResources.current
    val text=MaterialTheme.colorScheme.onSurface
    val overlay=LocalDesktopOverlayColor.current
    val density=LocalDensity.current
    val visibleLoading=if(ui.tab==DashboardTab.RECORDS) historyRead.loading else loading
    val visibleError=if(ui.tab==DashboardTab.RECORDS) historyRead.error else error
    var selectedRow by remember(history) {mutableStateOf<HistoryRow?>(null)}
    var selectedBar by remember(history,ui.range,ui.byModel,ui.mode) {mutableStateOf<DashboardTrendBar?>(null)}
    var selectedCandle by remember(history,ui.range,ui.mode) {mutableStateOf<DashboardTrendCandle?>(null)}
    ChartInspectionHost {
    Scaffold(Modifier.testTag("trends-history-detail"),containerColor=MaterialTheme.colorScheme.background,
        contentWindowInsets=WindowInsets.navigationBars,
        topBar={Column(Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=10.dp),
                verticalAlignment=Alignment.CenterVertically) {
                DashboardHeaderAction(R.drawable.desktop_arrow_left,strings.getString(R.string.dashboard_close),
                    "trends-history-back",true,onBack)
                Text(strings.getString(R.string.dashboard_title),Modifier.weight(1f).padding(start=4.dp),
                    style=quotaTextStyle(13,true).copy(fontWeight=FontWeight.SemiBold,
                        letterSpacing=(.26f*LocalDesktopTextScale.current).sp),color=text)
                DashboardHeaderAction(R.drawable.desktop_refresh,strings.getString(R.string.refresh),"hub-refresh",!loading,onRefresh,refreshFeedback)
                DashboardHeaderAction(R.drawable.desktop_settings,strings.getString(R.string.nav_settings),"hub-settings-toggle",true,onSettings)
            }
            HorizontalDivider(color=overlay.copy(alpha=.14f))
            Row(Modifier.fillMaxWidth().padding(horizontal=14.dp).testTag("dashboard-tabs"),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                listOf(DashboardTab.OVERVIEW to R.string.dashboard_overview,DashboardTab.TRENDS to R.string.dashboard_trends,DashboardTab.RECORDS to R.string.dashboard_tab_records).forEach {(tab,label)->
                    val active=ui.tab==tab
                    Box(Modifier.heightIn(min=48.dp).selectableDashboard(active,"dashboard-tab-${tab.name.lowercase()}") {ui.selectTab(tab)},contentAlignment=Alignment.BottomCenter) {
                        Text(strings.getString(label),Modifier.background(if(active) overlay.copy(alpha=.06f) else Color.Transparent,
                            RoundedCornerShape(topStart=7.dp,topEnd=7.dp)).padding(horizontal=12.dp,vertical=6.dp),
                            style=quotaTextStyle(12),color=text.copy(alpha=if(active) 1f else .55f))
                    }
                }
            }
            HorizontalDivider(color=overlay.copy(alpha=.08f))
            if(visibleLoading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp).testTag("dashboard-history-loading"))
            visibleError?.let {Text(strings.getString(it),Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=6.dp)
                .testTag("dashboard-history-error").semantics {liveRegion=LiveRegionMode.Polite},style=quotaTextStyle(11),color=MaterialTheme.colorScheme.error)}
        }}) {padding->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),contentAlignment=Alignment.TopCenter) {
            Column(Modifier.widthIn(max=1200.dp).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("dashboard-scroll")
                .padding(14.dp).onSizeChanged {ui.resizeOverview(with(density) {it.width.toDp().value.toDouble()})},verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(ui.tab==DashboardTab.RECORDS) {
                    Row(Modifier.fillMaxWidth().testTag("dashboard-records-modes"),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        DesktopFilterChip(!ui.recordsAnalysis,{ui.openRecords(false)},modifier=Modifier.testTag("dashboard-records-details")) {Text(strings.getString(R.string.records_mode_details))}
                        DesktopFilterChip(ui.recordsAnalysis,{ui.openRecords(true)},modifier=Modifier.testTag("dashboard-records-analysis")) {Text(strings.getString(R.string.records_mode_analysis))}
                    }
                    Box(Modifier.testTag("dashboard-records")) {
                        if(ui.recordsAnalysis) HistoryAnalysis(recordsHistory,historyRead.loading,historyRead.error)
                        else HistoryTrend(recordsHistory,historyRead.loading,historyRead.error,today)
                    }
                } else if(history==null) {
                    DesktopActionButton(onRefresh,enabled=!loading,modifier=Modifier.testTag("history-load")) {Text(strings.getString(R.string.desktop_load_charts))}
                } else if(history.daily.isEmpty() && history.monthly.isEmpty()) {
                    Text(strings.getString(R.string.dashboard_empty),Modifier.testTag("dashboard-empty"),style=quotaTextStyle(12),color=text.copy(alpha=.5f))
                } else when(ui.tab) {
                    DashboardTab.OVERVIEW->DesktopDashboardOverview(history,today,ui.metricCost,{ui.selectMetric(it);updateDisplay?.invoke(DisplayChange.HeatmapMetric(if(it) "cost" else "tokens"))},
                        {selectedRow=it},motion=overviewMotion)
                    DashboardTab.TRENDS->trend?.let {model->
                        DashboardTrendsBody(history,model,ui,{bar,row->selectedBar=bar;selectedRow=row},{selectedCandle=it},chartMotion,chartEventKey)
                    }
                    DashboardTab.RECORDS->Unit
                }
            }
        }
    }
    }
    selectedRow?.let {row->DashboardDayDialog(row,selectedBar) {selectedRow=null;selectedBar=null}}
    selectedCandle?.let {candle->DashboardCandleDialog(candle) {selectedCandle=null}}
}

private fun Modifier.selectableDashboard(active:Boolean,tag:String,click:()->Unit)=
    this.clickable(role=Role.Tab,onClick=click).testTag(tag).semantics {selected=active}

@Composable private fun DashboardHeaderAction(icon:Int,label:String,tag:String,enabled:Boolean,click:()->Unit,feedback:Int=0) {
    val text=MaterialTheme.colorScheme.onSurface
    Box(Modifier.size(48.dp).clickable(enabled,role=Role.Button,onClick=click).testTag(tag)
        .semantics {contentDescription=label},contentAlignment=Alignment.Center) {
        Box(Modifier.size(26.dp).alpha(if(enabled) 1f else .4f).background(LocalDesktopOverlayColor.current.copy(alpha=.05f),RoundedCornerShape(6.dp)),
            contentAlignment=Alignment.Center) {
            when {
                icon==R.drawable.desktop_refresh && feedback==1 -> Text("✓",style=quotaTextStyle(12,true),color=LocalDesktopSuccessColor.current)
                icon==R.drawable.desktop_refresh && feedback==2 -> Text("!",style=quotaTextStyle(12,true),color=MaterialTheme.colorScheme.error)
                else -> Icon(painterResource(icon),null,if(icon==R.drawable.desktop_refresh) Modifier.size(8.3.dp,8.dp) else Modifier.size(12.dp),tint=text)
            }
        }
    }
}

@Composable private fun DashboardTrendsBody(
    history:HubHistory,model:DashboardTrendModel,ui:DashboardUiState,
    onBar:(DashboardTrendBar,HistoryRow)->Unit,onCandle:(DashboardTrendCandle)->Unit,motion:State<DashboardChartFrame>?,eventKey:Any?
) {
    val strings=LocalResources.current
    val density=LocalDensity.current
    val text=MaterialTheme.colorScheme.onSurface
    val overlay=LocalDesktopOverlayColor.current
    var controlsMeasured by remember {mutableStateOf(false)}
    var legendMeasured by remember {mutableStateOf(false)}
    var chartSize by remember {mutableStateOf(IntSize.Zero)}
    val requestRevision=ui.motionRevision
    val requestReason=ui.motionReason
    val locale=strings.configuration.locales[0].toLanguageTag()
    val palette=LocalDesktopVendorPalette.current
    val axisScale=effectiveTextScale()
    val axisInset=52.0+((axisScale-1f).coerceAtLeast(0f)*22.0)
    LaunchedEffect(eventKey,requestRevision,chartSize,controlsMeasured,legendMeasured,model,density,locale,palette,axisInset) {
        if(controlsMeasured && (model.mode==DashboardTrendMode.KLINE || legendMeasured) && chartSize.width>0 && chartSize.height>0) {
            // Publishing measured dimensions feeds the owner's projection once.
            // That identical geometry must reuse the completed target, rather than
            // start another raster job after the first animation has already begun.
            if(ui.committedChartEvent==eventKey && ui.committedChartRevision==requestRevision &&
                ui.committedChartPreparation?.matches(model,palette,chartSize)==true) return@LaunchedEffect
            // Compose commits and measures asynchronously. Keep the previous painted model
            // until this render's controls/legend have completed layout, then animate once.
            withFrameNanos {}
            withFrameNanos {}
            if(ui.motionRevision==requestRevision) {
                val width=with(density) {chartSize.width.toDp().value.toDouble()}
                val height=with(density) {chartSize.height.toDp().value.toDouble()}
                val finalModel=DashboardTrendProjection.project(history,model.range,model.byModel,model.mode,width,height,locale,axisInset)
                val prepared=prepareDashboardChart(finalModel,palette,chartSize)
                // withContext's prompt cancellation plus the revision guard prevent
                // obsolete history/size/palette work from publishing after a newer request.
                currentCoroutineContext().ensureActive()
                ui.resizeChart(width,height)
                ui.commitChart(finalModel,eventKey,requestRevision,requestReason,prepared)
            }
        }
    }
    Column(Modifier.fillMaxWidth().testTag("dashboard-trends"),verticalArrangement=Arrangement.spacedBy(CompactUiTokens.SectionSpacing)) {
        FlowRow(Modifier.fillMaxWidth().onSizeChanged {controlsMeasured=true},
            horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(2.dp),itemVerticalAlignment=Alignment.CenterVertically) {
            if(model.mode==DashboardTrendMode.BARS) DesktopDashboardSegment(
                listOf("tool" to strings.getString(R.string.dashboard_by_tool),"model" to strings.getString(R.string.dashboard_by_model)),
                if(ui.byModel) "model" else "tool","dashboard-dimension",{ui.selectDimension(it=="model")})
            DesktopDashboardSegment(listOf("bars" to strings.getString(R.string.chart_bars),"kline" to strings.getString(R.string.chart_kline)),
                if(ui.mode==DashboardTrendMode.BARS) "bars" else "kline","dashboard-mode",{ui.selectMode(if(it=="bars") DashboardTrendMode.BARS else DashboardTrendMode.KLINE)})
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically) {
                DashboardTrendProjection.ranges.forEach {range->
                    val label=strings.getString(when(range) {"7"->R.string.range_7_days;"30"->R.string.range_30_days;"90"->R.string.range_90_days;"365"->R.string.dashboard_range_year;else->R.string.dashboard_range_all})
                    val active=ui.range==range
                    Box(Modifier.heightIn(min=48.dp).widthIn(min=48.dp).clickable(role=Role.RadioButton) {ui.selectRange(range)}
                        .testTag("dashboard-range-$range").semantics {selected=active},contentAlignment=Alignment.Center) {
                        Text(label,Modifier.background(if(active) overlay.copy(alpha=.12f) else Color.Transparent,RoundedCornerShape(6.dp))
                            .padding(horizontal=10.dp,vertical=3.dp),style=quotaTextStyle(12),color=text.copy(alpha=if(active) 1f else .6f))
                    }
                }
            }
        }
        val targetHeight=(LocalConfiguration.current.screenHeightDp.dp-360.dp).coerceIn(160.dp,240.dp)
        val h=maxOf(CompactUiTokens.chartHeight(),targetHeight)
        val chartModifier=Modifier.fillMaxWidth().height(h).onSizeChanged {chartSize=it}
        val committedChart=ui.committedChart
        if(committedChart!=null) DesktopDashboardChart(committedChart,chartModifier,onBar,onCandle,motion,
            prepared=ui.committedChartPreparation)
        else Spacer(chartModifier.testTag("dashboard-chart"))
        if (model.mode == DashboardTrendMode.BARS && model.daily.isNotEmpty()) Text(
            if(model.bucketDays>1) strings.getString(R.string.dashboard_bars_bucket,model.bucketDays)
            else strings.getString(R.string.daily_chart_inspect_hint),
            style = quotaTextStyle(11), color = text.copy(alpha = .55f))
        if(model.mode==DashboardTrendMode.KLINE && model.candles.isNotEmpty()) KlineSummary(model)
        if(model.daily.isEmpty()) Text(strings.getString(R.string.dashboard_no_daily),style=quotaTextStyle(11),color=text.copy(alpha=.55f))
        if(model.hasUnknownData) Text(strings.getString(R.string.dashboard_chart_unknown),Modifier.testTag("dashboard-trend-unknown"),style=quotaTextStyle(11),color=text.copy(alpha=.55f))
        if(model.mode==DashboardTrendMode.BARS) DashboardLegend(model,Modifier.onSizeChanged {legendMeasured=true})
    }
}

@Composable private fun KlineSummary(model:DashboardTrendModel) {
    val strings=LocalResources.current
    val display=LocalMainDisplay.current
    val tag=DesktopCompactTokens.localeTag(strings.configuration.locales[0])
    val text=MaterialTheme.colorScheme.onSurface
    fun compact(value:BigInteger?)=value?.let{DesktopCompactTokens.format(it.toDouble(),display.compactUnits,tag)} ?: "—"
    val candles=model.candles
    val high=candles.mapNotNull{it.high}.maxOrNull()
    val low=candles.mapNotNull{it.low}.minOrNull()
    FlowRow(Modifier.fillMaxWidth().testTag("dashboard-kline-summary"),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        if(model.bucketDays>1) Text(strings.getString(R.string.dashboard_bucket_days,model.bucketDays),style=quotaTextStyle(10),color=text.copy(alpha=.55f))
        listOf(R.string.chart_open to candles.first().open,R.string.chart_high to high,R.string.chart_low to low,R.string.chart_close to candles.last().close).forEach { (label,value) ->
            Text("${strings.getString(label)} ${compact(value)}",style=quotaDataTextStyle(10),color=text.copy(alpha=.55f))
        }
    }
}

@Composable private fun DashboardLegend(model:DashboardTrendModel,modifier:Modifier) {
    val display=LocalMainDisplay.current
    val locale=LocalResources.current.configuration.locales[0]
    val palette=LocalDesktopVendorPalette.current
    val overlay=LocalDesktopOverlayColor.current
    val text=MaterialTheme.colorScheme.onSurface
    Column(modifier.fillMaxWidth().heightIn(max=CompactUiTokens.ChartHeightSecondary).verticalScroll(rememberScrollState()).testTag("dashboard-legend"),
        verticalArrangement=Arrangement.spacedBy(1.dp)) {
        model.legend.forEach {row->
            Row(Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=CompactUiTokens.ChartTitleSpacing).testTag("dashboard-legend-${row.key}"),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(dashboardColor(row.key,model.byModel,palette))
                    .border(1.dp,overlay.copy(alpha=.18f),RoundedCornerShape(3.dp)))
                Text(row.key,Modifier.weight(1f),style=quotaTextStyle(12),color=text.copy(alpha=.9f))
                }
                Text(DesktopCompactTokens.format(row.value.toDouble(),display.compactUnits,DesktopCompactTokens.localeTag(locale)),style=quotaTextStyle(12,true),color=text.copy(alpha=.85f))
                Text(row.percent.toPlainString()+"%",Modifier.widthIn(min=50.dp),style=quotaTextStyle(12),textAlign=TextAlign.End,color=text.copy(alpha=.55f))
            }
        }
    }
}

@Composable private fun DashboardDayDialog(row:HistoryRow,bar:DashboardTrendBar?,close:()->Unit) {
    val strings=LocalResources.current
    val numbers=NumberFormat.getIntegerInstance(strings.configuration.locales[0])
    val display=LocalMainDisplay.current
    val locale=strings.configuration.locales[0]
    DesktopAlertDialog(onDismissRequest=close,title={Text(row.date)},text={
        Column(Modifier.verticalScroll(rememberScrollState()).testTag("dashboard-day-detail"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(bar!=null) {
                Text(strings.getString(R.string.dashboard_attributed_tokens)+": "+DesktopCompactTokens.format(bar.total.toDouble(),display.compactUnits,DesktopCompactTokens.localeTag(locale)),style=quotaTextStyle(13,true))
                bar.segments.sortedByDescending {it.value}.forEach {segment->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(segment.key,Modifier.weight(1f),style=quotaTextStyle(12))
                        Text(if(segment.known) numbers.format(segment.value) else "—",style=quotaTextStyle(12,true))
                    }
                }
                if(!bar.attributionComplete || bar.duplicate) Text(strings.getString(R.string.dashboard_chart_unknown),style=quotaTextStyle(11))
                HorizontalDivider()
            }
            Text(strings.getString(R.string.dashboard_recorded_tokens)+": "+(row.tokens?.let(numbers::format) ?: "—"))
            Text(strings.getString(R.string.known_cost_formatted,usageCostText(row.cost,row.unpricedTokens)))
            row.unpricedTokens?.takeIf {it>0}?.let {Text(strings.getString(R.string.unpriced_tokens,numbers.format(it)))}
        }
    },confirmButton={DesktopTextButton(close) {Text(strings.getString(R.string.close))}})
}

@Composable private fun DashboardCandleDialog(candle:DashboardTrendCandle,close:()->Unit) {
    val strings=LocalResources.current
    val numbers=NumberFormat.getIntegerInstance(strings.configuration.locales[0])
    DesktopAlertDialog(onDismissRequest=close,title={Text(if(candle.key==candle.endKey) candle.key else "${candle.key} – ${candle.endKey}")},text={
        Column(Modifier.verticalScroll(rememberScrollState()).testTag("dashboard-candle-detail"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            listOf(R.string.chart_open to candle.open,R.string.chart_high to candle.high,R.string.chart_low to candle.low,R.string.chart_close to candle.close).forEach {(label,value)->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(strings.getString(label),Modifier.weight(1f));Text(value?.let(numbers::format) ?: "—")
                }
            }
            Text(strings.getString(R.string.chart_candle_coverage,candle.knownDays,candle.days))
            if(!candle.complete) Text(strings.getString(R.string.dashboard_chart_unknown))
        }
    },confirmButton={DesktopTextButton(close) {Text(strings.getString(R.string.close))}})
}
