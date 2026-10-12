package dev.bing.tokenmonitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor

/** Independent Overview body. The root owns tabs, navigation, preferences and selected-day details. */
@Composable internal fun DesktopDashboardOverview(
    history:HubHistory,today:LocalDate,metricCost:Boolean,onMetric:(Boolean)->Unit,
    onRowSelect:(HistoryRow)->Unit,availableWidth:Dp?=null,modifier:Modifier=Modifier,
    motion:DashboardOverviewMotionState?=null
) {
    val model=remember(history,today) {DashboardOverviewProjection.project(history,today)}
    val strings=LocalResources.current
    BoxWithConstraints(Modifier.fillMaxWidth().then(modifier).testTag("dashboard-overview")) {
        val width=availableWidth?.coerceAtMost(maxWidth) ?: maxWidth
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            DashboardStatStrip(model.summary)
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                    verticalArrangement=Arrangement.spacedBy(4.dp),itemVerticalAlignment=Alignment.CenterVertically) {
                    Text(strings.getString(R.string.dashboard_heatmap_title),style=quotaTextStyle(13,true),
                        color=MaterialTheme.colorScheme.onSurface.copy(alpha=.9f))
                    DesktopDashboardSegment(listOf("tokens" to strings.getString(R.string.home_heatmap_tokens),
                        "cost" to strings.getString(R.string.home_heatmap_cost)),if(metricCost) "cost" else "tokens",
                        "dashboard-heatmap-metric",{onMetric(it=="cost")},small=true)
                }
                DashboardOverviewHeatmap(model,width,metricCost,onRowSelect,motion)
            }
            val wide=width>=680.dp*LocalDesktopTextScale.current
            if(wide) Row(Modifier.fillMaxWidth().padding(top=6.dp),horizontalArrangement=Arrangement.spacedBy(32.dp)) {
                DashboardRanking(model.models,true,Modifier.weight(1f),motion)
                DashboardRanking(model.tools,false,Modifier.weight(1f),motion)
            } else Column(Modifier.fillMaxWidth().padding(top=6.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
                DashboardRanking(model.models,true,motion=motion)
                DashboardRanking(model.tools,false,motion=motion)
            }
        }
    }
}

private data class DashboardStat(val key:String,val label:String,val value:String)

@Composable private fun DashboardStatStrip(summary:HistorySummary?) {
    val strings=LocalResources.current
    val locale=strings.configuration.locales[0]
    val tag=DesktopCompactTokens.localeTag(locale)
    val units=LocalMainDisplay.current.compactUnits
    val currency=LocalUsageCurrency.current
    val stats=listOf(
        DashboardStat("tokens",strings.getString(R.string.dashboard_stat_total_tokens),DashboardOverviewProjection.count(summary?.totalTokens,units,tag)),
        DashboardStat("cost",strings.getString(R.string.dashboard_stat_total_cost),DashboardOverviewProjection.compactCost(summary?.totalCost,summary?.unpricedTokens,currency,units,tag)),
        DashboardStat("active-days",strings.getString(R.string.trends_active_days),DashboardOverviewProjection.count(summary?.activeDays,units,tag)),
        DashboardStat("streak",strings.getString(R.string.trends_current_streak),DashboardOverviewProjection.count(summary?.currentStreak,units,tag)),
        DashboardStat("time",strings.getString(R.string.trends_active_time),DashboardOverviewProjection.duration(summary?.activeTimeMs)),
        DashboardStat("peak",strings.getString(R.string.trends_peak_day),DashboardOverviewProjection.count(summary?.peakDayTokens,units,tag)),
        DashboardStat("favorite",strings.getString(R.string.dashboard_stat_favorite_model),summary?.favoriteModel ?: "—"),
        DashboardStat("messages",strings.getString(R.string.activity_messages),DashboardOverviewProjection.count(summary?.messages,units,tag))
    )
    val missing=summary?.unpricedTokens?.takeIf {it>0}
    val note=missing?.let {strings.getString(R.string.known_unpriced,NumberFormat.getIntegerInstance(locale).format(it))}
    var showNote by remember(missing) {mutableStateOf(false)}
    val text=MaterialTheme.colorScheme.onSurface
    val overlay=LocalDesktopOverlayColor.current
    val scale=LocalDesktopTextScale.current
    val valueStyle=quotaTextStyle(19,true).copy(fontFamily=LocalDesktopDisplayFont.current,
        fontWeight=FontWeight.SemiBold,fontFeatureSettings="tnum")
    val captionStyle=quotaTextStyle(10).copy(letterSpacing=(.3f*scale).sp)
    val measurer=rememberTextMeasurer()
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density=LocalDensity.current
            val padding=(maxWidth.value*.0135f).coerceIn(12f,18f).dp
            val requirements=stats.map {stat->with(density) {maxOf(
                measurer.measure(AnnotatedString(stat.value),valueStyle,maxLines=1,softWrap=false).size.width,
                measurer.measure(AnnotatedString(stat.label.uppercase(Locale.ROOT)),captionStyle,maxLines=1,softWrap=false).size.width
            ).toDp().value.toDouble()+padding.value*2}}
            val width=maxWidth.value.toDouble()
            val bounded=requirements.map {it.coerceAtMost(width/2)}
            val columns=when {
                width>=8*92 && bounded.sum()+80<=width ->8
                width>=4*92 && bounded.chunked(4).all {it.sum()+40<=width}->4
                else->2
            }
            val border=overlay.copy(alpha=.08f)
            Layout(modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp,border,RoundedCornerShape(12.dp))
                .background(overlay.copy(alpha=.03f)).testTag("dashboard-summary"),content={
                stats.forEachIndexed {index,stat->
                    val clickable=stat.key=="cost" && note!=null
                    Column(Modifier.semantics(mergeDescendants=true) {
                        if(clickable) contentDescription=stat.value+", "+note
                    }.testTag("dashboard-card-${stat.key}")
                        .then(if(clickable) Modifier.clickable(role=Role.Button,onClickLabel=note) {showNote=!showNote} else Modifier)
                        .drawBehind {
                            if(index%columns!=columns-1) drawLine(overlay.copy(alpha=.06f),Offset(size.width,0f),Offset(size.width,size.height),1.dp.toPx())
                            if(index>=columns) drawLine(overlay.copy(alpha=.06f),Offset.Zero,Offset(size.width,0f),1.dp.toPx())
                        }.padding(horizontal=padding,vertical=14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(stat.value,Modifier.fillMaxWidth().testTag("dashboard-card-${stat.key}-value"),style=valueStyle,color=text,
                            maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(stat.label.uppercase(Locale.ROOT),Modifier.fillMaxWidth(),style=captionStyle,color=text.copy(alpha=.55f),
                            maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                }
            }) {children,constraints->
                val chunks=children.chunked(columns)
                val rows=chunks.mapIndexed {rowIndex,row->
                    val widths=DashboardOverviewProjection.statWidths(bounded.drop(rowIndex*columns).take(row.size),width)
                    var used=0
                    row.mapIndexed {index,child->
                        val px=if(index==row.lastIndex) constraints.maxWidth-used else
                            with(density) {widths[index].dp.roundToPx()}.coerceIn(0,constraints.maxWidth-used)
                        used+=px
                        child.measure(Constraints(minWidth=px,maxWidth=px))
                    }
                }
                val heights=rows.map {row->row.maxOf {it.height}}
                layout(constraints.maxWidth,heights.sum()) {
                    var y=0
                    rows.forEachIndexed {rowIndex,row->
                        var x=0
                        row.forEach {child->child.placeRelative(x,y);x+=child.width}
                        y+=heights[rowIndex]
                    }
                }
            }
        }
        if(showNote && note!=null) Text(note,Modifier.testTag("dashboard-summary-unpriced"),style=quotaTextStyle(10),color=text.copy(alpha=.55f))
    }
}

@Composable private fun DashboardOverviewHeatmap(model:DashboardOverviewModel,width:Dp,metricCost:Boolean,onRowSelect:(HistoryRow)->Unit,motion:DashboardOverviewMotionState?) {
    val geometry=remember(model.weeks,width) {DashboardOverviewProjection.heatGeometry(model.weeks,width.value.toDouble())}
    val strings=LocalResources.current
    val density=LocalDensity.current
    val overlay=LocalDesktopOverlayColor.current
    val colors=listOf(overlay.copy(alpha=.03f))+LocalDesktopHeatColors.current.mapIndexed {index,color->color.copy(alpha=listOf(.18f,.45f,.8f,1f)[index])}
    val line=LocalDesktopLineColor.current
    val locale=strings.configuration.locales[0]
    val formatter=remember(locale) {DateTimeFormatter.ofPattern("MMM",locale)}
    val months=model.months.map {it to if(locale.language=="zh") "${YearMonth.parse(it.month).monthValue}月" else YearMonth.parse(it.month).format(formatter)}
    val latestModel by rememberUpdatedState(model)
    val latestSelect by rememberUpdatedState(onRowSelect)
    fun select(cell:HomeHeatCell) {latestSelect(cell.value ?: HistoryRow(cell.date,null,null))}
    val description=strings.getString(R.string.dashboard_heatmap_description,model.cells.first().date,model.today.toString())
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column(Modifier.width(geometry.width.dp)) {
            Canvas(Modifier.fillMaxWidth().height(geometry.height.dp).testTag("dashboard-heatmap")
                .pointerInput(model.today,geometry,density.density) {detectTapGestures {position->
                    DashboardOverviewProjection.hitCell(latestModel,geometry,
                        (position.x/density.density).toDouble(),(position.y/density.density).toDouble())?.let(::select)
                }}.semantics {
                    contentDescription=description
                    role=Role.Button
                    onClick(strings.getString(R.string.home_activity_day)) {select(latestModel.cells.last());true}
                }) {
                fun coordinate(value:Double)=(floor(value*100+.5)/100).dp.toPx()
                val frame=motion?.frame?.value
                model.cells.forEach {cell->drawRoundRect(colors[if(metricCost) cell.costLevel else cell.tokenLevel].let {
                    it.copy(alpha=it.alpha*(frame?.heatAlpha(cell.column,model.weeks-1) ?: 1f))},
                    Offset(coordinate(cell.column*geometry.pitch),coordinate(cell.weekday*geometry.pitch)),
                    Size(coordinate(geometry.cell),coordinate(geometry.cell)),CornerRadius(3.dp.toPx()))}
            }
            Box(Modifier.fillMaxWidth().height((16*LocalDesktopTextScale.current).dp)) {
                months.forEach {(month,label)->Text(label,Modifier.offset(x=(month.column*geometry.pitch).dp)
                    .testTag("dashboard-heatmap-month-${month.month}"),style=quotaTextStyle(10),color=line.copy(alpha=.5f))}
            }
        }
    }
}

@Composable private fun DashboardRanking(rows:List<DashboardRankingRow>,byModel:Boolean,modifier:Modifier=Modifier,motion:DashboardOverviewMotionState?=null) {
    if(rows.isEmpty()) return
    val strings=LocalResources.current
    val text=MaterialTheme.colorScheme.onSurface
    val overlay=LocalDesktopOverlayColor.current
    val group=if(byModel) "models" else "tools"
    val units=LocalMainDisplay.current.compactUnits
    val locale=DesktopCompactTokens.localeTag(strings.configuration.locales[0])
    Column(Modifier.fillMaxWidth().then(modifier).testTag("dashboard-ranking-$group"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(strings.getString(if(byModel) R.string.dashboard_by_model else R.string.dashboard_by_tool).uppercase(Locale.ROOT),
            style=quotaTextStyle(12,true).copy(letterSpacing=(.48f*LocalDesktopTextScale.current).sp),color=text.copy(alpha=.6f))
        Spacer(Modifier.height(2.dp))
        rows.forEach {row->
            val color=dashboardColor(row.key,byModel,LocalDesktopVendorPalette.current)
            Layout(modifier=Modifier.fillMaxWidth().padding(vertical=2.dp).testTag("dashboard-ranking-$group-${row.key}")
                .semantics(mergeDescendants=true) {contentDescription="${row.key}, ${row.tokens}, ${row.percent}%"},content={
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(11.dp).border(1.dp,overlay.copy(alpha=.18f),RoundedCornerShape(3.dp)).background(color,RoundedCornerShape(3.dp)))
                    Text(row.key,style=quotaTextStyle(12),color=text.copy(alpha=.9f),maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                Canvas(Modifier.clipToBounds().testTag("dashboard-ranking-$group-${row.key}-bar")) {
                    val key=DashboardRankKey(byModel,row.key)
                    val trackWidth=size.width/density
                    val fraction=motion?.frame?.value?.rankingFraction(key,trackWidth.toDouble()) ?: row.barFraction
                    drawRoundRect(overlay.copy(alpha=.06f),cornerRadius=CornerRadius(2.dp.toPx()))
                    drawRoundRect(color,size=Size(size.width*fraction,size.height),cornerRadius=CornerRadius(2.dp.toPx()))
                    motion?.recordPaintedWidth(key,trackWidth.toDouble(),fraction)
                }
                Text(DesktopCompactTokens.format(row.tokens.toDouble(),units,locale),Modifier.testTag("dashboard-ranking-$group-${row.key}-value"),style=quotaTextStyle(12).copy(fontFeatureSettings="tnum"),color=text.copy(alpha=.8f),maxLines=1,overflow=TextOverflow.Ellipsis)
                Text("${row.percent}%",Modifier.testTag("dashboard-ranking-$group-${row.key}-percent"),style=quotaTextStyle(12).copy(fontFeatureSettings="tnum"),color=text.copy(alpha=.55f),maxLines=1,overflow=TextOverflow.Ellipsis)
            }) {children,constraints->
                val gap=12.dp.roundToPx()
                val numericSpace=(constraints.maxWidth-gap*2-24.dp.roundToPx()).coerceAtLeast(0)
                val percent=children[3].measure(Constraints(maxWidth=numericSpace/2))
                val value=children[2].measure(Constraints(maxWidth=numericSpace-percent.width))
                val nameWidth=minOf(110.dp.roundToPx(),(constraints.maxWidth-value.width-percent.width-gap*3-24.dp.roundToPx()).coerceAtLeast(24.dp.roundToPx()))
                val name=children[0].measure(Constraints(maxWidth=nameWidth))
                val remaining=constraints.maxWidth-name.width-value.width-percent.width-gap*3
                val stacked=remaining<24.dp.roundToPx()
                val barWidth=if(stacked) constraints.maxWidth else remaining
                val bar=children[1].measure(Constraints.fixed(barWidth.coerceAtLeast(0),4.dp.roundToPx()))
                val rowHeight=maxOf(name.height,value.height,percent.height)
                layout(constraints.maxWidth,rowHeight+if(stacked) bar.height+6.dp.roundToPx() else 0) {
                    name.placeRelative(0,(rowHeight-name.height)/2)
                    percent.placeRelative(constraints.maxWidth-percent.width,(rowHeight-percent.height)/2)
                    value.placeRelative((constraints.maxWidth-percent.width-gap-value.width).coerceAtLeast(name.width+gap),(rowHeight-value.height)/2)
                    if(stacked) bar.placeRelative(0,rowHeight+6.dp.roundToPx())
                    else bar.placeRelative(name.width+gap,(rowHeight-bar.height)/2)
                }
            }
        }
    }
}

@Stable internal class DashboardOverviewMotionState internal constructor(private val motion:DashboardOverviewMotion) {
    internal val output=mutableStateOf(motion.frame)
    val frame:State<DashboardOverviewFrame> get()=output
    fun recordPaintedWidth(key:DashboardRankKey,trackWidth:Double,fraction:Float)=motion.recordPaintedWidth(key,trackWidth,fraction)
}
private class DashboardOverviewRunner(var job:Job?=null) {fun cancel() {job?.cancel();job=null}}

/** Keep above conditional Dashboard/Settings bodies. Only Canvas reads the returned frame. */
@Composable internal fun rememberDashboardOverviewMotion(
    history:HubHistory?,today:LocalDate,width:Double,renderEventKey:Any?,motionRevision:Int,
    reason:DashboardMotionReason,visible:Boolean,metricCost:Boolean,preference:String="system",refreshBusy:Boolean=false
):DashboardOverviewMotionState {
    val engine=remember {DashboardOverviewMotion()}
    val state=remember {DashboardOverviewMotionState(engine)}
    val model=remember(history,today) {history?.let {DashboardOverviewProjection.project(it,today)}}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    val shown=visible && foreground
    val animationClock=LocalDesktopAnimationClock.current
    val scale=DesktopMotionPreference.scale(preference,LocalDesktopSystemMotionScale.current)
    val positiveScale=scale.isFinite() && scale>0
    val latestScale=rememberUpdatedState(scale)
    val latestBusy=rememberUpdatedState(refreshBusy)
    val runner=remember {DashboardOverviewRunner()}
    val scope=rememberCoroutineScope()
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->
            foreground=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if(!foreground) {runner.cancel();engine.settle();state.output.value=engine.frame}
        }
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);runner.cancel();engine.settle()}
    }
    SideEffect {
        val replaced=engine.render(model,renderEventKey,motionRevision,reason,width,metricCost,animationClock.nowNanos(),
            animate=shown && positiveScale,visible=shown,durationScale=scale)
        if(!shown || !positiveScale) engine.settle()
        state.output.value=engine.frame
        if(replaced) runner.cancel()
        if(shown && positiveScale && engine.needsFrame(refreshBusy)) {
            if(runner.job?.isActive!=true) runner.job=scope.launch(start=CoroutineStart.UNDISPATCHED) {
                while(engine.needsFrame(latestBusy.value)) withFrameNanos {now->
                    engine.advance(now,latestScale.value,latestBusy.value)
                    state.output.value=engine.frame
                }
            }
        } else runner.cancel()
    }
    return state
}
