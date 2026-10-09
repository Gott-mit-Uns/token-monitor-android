package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.localization.tr
import io.github.theminionooo.tokenmonitor.localization.localizedText
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.domain.HistoryPoint
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import java.time.LocalDate
import java.util.Locale
import kotlin.math.max

private enum class TrendGroup { Tool, Model }
private enum class TrendStyle { Bars, KLine }
private enum class DashboardTab { Overview, Trends }
private enum class TrendRange(val label: String, val days: Int?) {
    Seven("7 DAYS", 7), Thirty("30 DAYS", 30), Ninety("90 DAYS", 90), Year("1 YEAR", 365), All("ALL", null)
}

internal fun LazyListScope.trendItems(snapshot: HubSnapshot) {
    item { UsageDashboardPanel(snapshot) }
    item { UsageComparisonPanel(homeActivityPoints(snapshot)) }
    item { UsageExplorer(homeActivityPoints(snapshot)) }
    if (snapshot.history.monthly.isNotEmpty()) {
        item { Text(tr("MONTHLY HISTORY"), color = Ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
        items(snapshot.history.monthly.takeLast(12).reversed(), key = { it.label }) { point ->
            DesktopDetailRow(point.label, "", formatTokens(point.tokens), formatUsageCost(point.costUsd, point.unpricedTokens, compact = true), Blue)
        }
    }
}

@Composable
private fun UsageDashboardPanel(snapshot: HubSnapshot) {
    var tabName by rememberSaveable { mutableStateOf(DashboardTab.Overview.name) }
    val tab = DashboardTab.entries.firstOrNull { it.name == tabName } ?: DashboardTab.Overview
    val motionEnabled = LocalInteractionMotion.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceGroup(
            options = listOf("OVERVIEW" to DashboardTab.Overview.name, "TRENDS" to DashboardTab.Trends.name),
            selected = tabName,
            onSelect = { tabName = it },
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedContent(
            targetState = tab,
            transitionSpec = { (fadeIn(tween(if (motionEnabled) 200 else 0, easing = DesktopEaseOut)) + slideInVertically(tween(if (motionEnabled) 240 else 0, easing = DesktopEaseOut)) { it / 28 }) togetherWith fadeOut(tween(if (motionEnabled) 110 else 0)) },
            label = "usage dashboard tab",
        ) { visibleTab ->
            if (visibleTab == DashboardTab.Overview) UsageOverview(snapshot) else TrendsPanel(homeActivityPoints(snapshot))
        }
    }
}

@Composable
private fun UsageOverview(snapshot: HubSnapshot) {
    var activityMetricName by rememberSaveable { mutableStateOf(ActivityMetric.Cost.name) }
    val activityMetric = ActivityMetric.entries.firstOrNull { it.name == activityMetricName } ?: ActivityMetric.Cost
    val history = homeActivityPoints(snapshot)
    if (history.isEmpty()) {
        MutedCopy("No daily history is available yet.", modifier = Modifier.padding(vertical = 12.dp))
        return
    }
    val active = history.filter { it.tokens > 0 }
    val totalTokens = history.sumOf { it.tokens }
    val totalCost = history.sumOf { it.costUsd }
    val messages = history.sumOf { it.messages }
    val activeTime = history.sumOf { it.activeTimeMs }
    val peak = history.maxOfOrNull { it.tokens } ?: 0L
    val topModel = history.flatMap { it.perModel.entries }.groupingBy { it.key }.fold(0L) { sum, entry -> sum + entry.value.tokens }.maxByOrNull { it.value }?.key.orEmpty()
    val stats = listOf(
        "TOTAL TOKENS" to formatTokens(totalTokens),
        (if (history.any { it.unpricedTokens > 0 }) desktopText("已知费用", "KNOWN COST") else "TOTAL COST") to formatUsageCost(totalCost, history.sumOf { it.unpricedTokens }, compact = true),
        "ACTIVE DAYS" to active.size.toString(),
        "CURRENT STREAK" to currentStreak(history).toString(),
        "ACTIVE TIME" to formatActiveDuration(activeTime),
        "PEAK DAY" to formatTokens(peak),
        "TOP MODEL" to topModel.displayName().ifBlank { "—" },
        "MESSAGES" to formatTokens(messages),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.chunked(if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f) 1 else 2).forEach { rowStats ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowStats.forEach { (label, value) -> OverviewStat(label, value, Modifier.weight(1f)) }
                if (rowStats.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (history.any { it.unpricedTokens > 0 }) Text(desktopText("${formatTokens(history.sumOf { it.unpricedTokens })} Token 未定价；费用色阶只表示已知小计。", "${formatTokens(history.sumOf { it.unpricedTokens })} unpriced tokens; cost shading represents known subtotals only."), color = Orange, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("TOKEN ACTIVITY"), color = Ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            ChoiceGroup(
                options = listOf("TOKENS" to ActivityMetric.Tokens.name, "COST" to ActivityMetric.Cost.name),
                selected = activityMetricName,
                onSelect = { activityMetricName = it },
                modifier = Modifier.width(150.dp),
            )
        }
        ActivityHeatmapGrid(buildActivityHeatmap(history, metric = activityMetric), history)
        OverviewBreakdown("BY MODEL · AVAILABLE HISTORY", history.flatMap { it.perModel.entries }.groupingBy { it.key }.fold(0L) { sum, entry -> sum + entry.value.tokens }, totalTokens, modelRows = true)
        OverviewBreakdown("BY TOOL · AVAILABLE HISTORY", history.flatMap { it.perClient.entries }.groupingBy { it.key }.fold(0L) { sum, entry -> sum + entry.value.tokens }, totalTokens)
    }
}

@Composable
private fun OverviewStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = Overlay, border = BorderStroke(1.dp, Line), shape = MaterialTheme.shapes.small, modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, color = Ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(tr(label), color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun OverviewBreakdown(title: String, values: Map<String, Long>, totalTokens: Long, modelRows: Boolean = false) {
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(tr(title), color = Ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        values.entries.sortedByDescending { it.value }.take(5).forEach { (name, tokens) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (modelRows) ModelMark(name, accentFor(name), size = LocalContentIconSize.current)
                    else UpstreamToolMark(name, accentFor(name), size = LocalContentIconSize.current)
                    Spacer(Modifier.width(7.dp))
                    Text(name.displayName(), color = Ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatTokens(tokens), color = Ink, style = MaterialTheme.typography.bodySmall)
                    Text(tr(String.format(Locale.US, "  %.1f%%", tokens.toDouble() / totalTokens.coerceAtLeast(1L) * 100.0)), color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                UsageBar(tokens.toFloat() / values.values.maxOrNull().orEmptyAtLeastOne(), accentFor(name))
            }
        }
    }
}

private fun Long?.orEmptyAtLeastOne(): Long = (this ?: 1L).coerceAtLeast(1L)


@Composable
private fun TrendsPanel(history: List<HistoryPoint>) {
    var groupName by rememberSaveable { mutableStateOf(TrendGroup.Tool.name) }
    var styleName by rememberSaveable { mutableStateOf(TrendStyle.Bars.name) }
    var rangeName by rememberSaveable { mutableStateOf(TrendRange.Thirty.name) }
    val group = TrendGroup.entries.firstOrNull { it.name == groupName } ?: TrendGroup.Tool
    val style = TrendStyle.entries.firstOrNull { it.name == styleName } ?: TrendStyle.Bars
    val range = TrendRange.entries.firstOrNull { it.name == rangeName } ?: TrendRange.Thirty
    val today = LocalDate.now()
    val trend = remember(history, range, group, today) {
        prepareTrends(history, group == TrendGroup.Model, range.days?.let { today.minusDays(it - 1L) }, today)
    }
    val total = trend.totalTokens.coerceAtLeast(1L)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceGroup(
                options = listOf("BY TOOL" to TrendGroup.Tool.name, "BY MODEL" to TrendGroup.Model.name),
                selected = groupName,
                onSelect = { groupName = it },
                modifier = Modifier.weight(1f),
            )
            ChoiceGroup(
                options = listOf("BARS" to TrendStyle.Bars.name, "K-LINE" to TrendStyle.KLine.name),
                selected = styleName,
                onSelect = { styleName = it },
                modifier = Modifier.weight(1f),
            )
        }
        ChoiceGroup(
            options = TrendRange.entries.map { it.label to it.name },
            selected = rangeName,
            onSelect = { rangeName = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(formatTokens(trend.totalTokens), color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(tr("${trend.days.size} recorded days · ${formatUsageCost(trend.days.sumOf { it.costUsd }, trend.days.sumOf { it.unpricedTokens }, compact = true)}"), color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            Text(tr(if (group == TrendGroup.Tool) "BY TOOL" else "BY MODEL"), color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        if (style == TrendStyle.Bars) {
            StackedTrendChart(trend = trend, height = 178.dp)
        } else {
            CandleTrendChart(trend = trend, height = 178.dp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(trend.start.toString(), color = Muted, style = MaterialTheme.typography.labelSmall)
            Text(trend.end.toString(), color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        if (trend.missingDays > 0) Text(tr("${trend.missingDays} days have no observation; gaps are left blank."), color = Muted, style = MaterialTheme.typography.labelSmall)
        if (style == TrendStyle.KLine) Text(tr("Each candle covers up to ${trendCandleDays(trend.calendarDays)} consecutive recorded days: first, last, highest and lowest daily totals."), color = Muted, style = MaterialTheme.typography.labelSmall)
        if (style == TrendStyle.Bars && trend.days.any { it.inconsistentAttribution }) Text(tr("Some reported breakdowns conflict with their daily totals. Those days are shown as unattributed."), color = Muted, style = MaterialTheme.typography.labelSmall)
        if (style == TrendStyle.Bars) {
            trend.series.forEachIndexed { index, series ->
                val value = trend.days.sumOf { it.segments[series] ?: 0L }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (series.name == null) Muted else trendSeriesColor(LocalPalette.current, index), size = 8.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(series.name?.let { if (series.other) tr(it) else it.displayName() } ?: tr("Unattributed"), color = Ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatTokens(value), color = Ink, style = MaterialTheme.typography.bodySmall)
                    Text(String.format(Locale.US, "  %.1f%%", value.toDouble() / total * 100.0), color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun StackedTrendChart(trend: TrendPresentation, height: androidx.compose.ui.unit.Dp) {
    val reveal = rememberChartReveal(Triple(trend.start, trend.end, trend.series))
    val palette = LocalPalette.current
    var selectedOffset by rememberSaveable(trend.start.toString(), trend.end.toString()) { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Canvas(modifier = Modifier.fillMaxWidth().height(height).pointerInput(trend) { detectTapGestures { point -> selectedOffset = ((point.x / size.width) * trend.calendarDays).toInt().coerceIn(0, (trend.calendarDays - 1).toInt()) } }.semantics { contentDescription = trendChartDescription(trend, false) }) {
        if (trend.days.isEmpty()) return@Canvas
        val maximum = max(1f, trend.days.maxOf { it.tokens }.toFloat()) / reveal.coerceAtLeast(0.001f)
        val slot = size.width / trend.calendarDays
        val barWidth = slot * 0.66f
        repeat(4) { tick ->
            val y = size.height * tick / 3f
            drawLine(palette.line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        trend.days.forEach { day ->
            if (day.tokens == 0L) {
                val left = trend.dayOffset(day.date) * slot + (slot - barWidth) / 2f
                drawLine(palette.muted, Offset(left, size.height - 1.dp.toPx()), Offset(left + barWidth, size.height - 1.dp.toPx()), strokeWidth = 1.dp.toPx())
            }
            var bottom = size.height
            trend.series.forEachIndexed { seriesIndex, series ->
                val value = day.segments[series] ?: 0L
                if (value <= 0) return@forEachIndexed
                val segmentHeight = size.height * value.toFloat() / maximum
                val left = trend.dayOffset(day.date) * slot + (slot - barWidth) / 2f
                drawRect(if (series.name == null) palette.muted else trendSeriesColor(palette, seriesIndex), topLeft = Offset(left, bottom - segmentHeight), size = Size(barWidth, segmentHeight))
                bottom -= segmentHeight
            }
        }
    }
    selectedOffset?.let { offset ->
        val date = trend.start.plusDays(offset.toLong())
        val day = trend.days.firstOrNull { it.date == date }
        Text(date.toString(), color = Muted, style = MaterialTheme.typography.labelSmall)
        if (day == null) Text(tr("该日期没有记录，不能视为零用量"), color = Muted, style = MaterialTheme.typography.bodySmall)
        else {
            CompleteValue(formatTokens(day.tokens) + " Token")
            CompleteValue(formatUsageCost(day.costUsd, day.unpricedTokens, compact = true), false)
            day.segments.forEach { (series, count) ->
                Text("${series.name ?: tr("Unclassified")}：${formatTokens(count)}", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    }

}

@Composable
internal fun CandleTrendChart(trend: TrendPresentation, height: androidx.compose.ui.unit.Dp) {
    val reveal = rememberChartReveal(trend.start to trend.end)
    val palette = LocalPalette.current
    val candles = remember(trend) { trendCandles(trend) }
    var selectedOffset by rememberSaveable(trend.start.toString(), trend.end.toString()) { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Canvas(modifier = Modifier.fillMaxWidth().height(height).alpha(reveal).pointerInput(trend) { detectTapGestures { point -> selectedOffset = ((point.x / size.width) * trend.calendarDays).toInt().coerceIn(0, (trend.calendarDays - 1).toInt()) } }.semantics { contentDescription = trendChartDescription(trend, true) }) {
        if (trend.days.isEmpty()) return@Canvas
        val maximum = max(1f, trend.days.maxOf { it.tokens }.toFloat())
        val dayWidth = size.width / trend.calendarDays
        repeat(4) { tick ->
            val y = size.height * tick / 3f
            drawLine(palette.line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        fun y(value: Long) = size.height - size.height * value.toFloat() / maximum
        candles.forEach { candle ->
            val first = trend.dayOffset(candle.first)
            val last = trend.dayOffset(candle.last)
            val x = (first + last + 1) * dayWidth / 2f
            val bodyWidth = (last - first + 1) * dayWidth * 0.45f
            val color = if (candle.close >= candle.open) palette.chart else palette.orange
            drawLine(color, Offset(x, y(candle.high)), Offset(x, y(candle.low)), strokeWidth = 1.dp.toPx())
            val bodyHeight = max(2.dp.toPx(), kotlin.math.abs(y(candle.open) - y(candle.close)))
            val top = minOf(y(candle.open), y(candle.close)).coerceAtMost(size.height - bodyHeight)
            drawRect(color, topLeft = Offset(x - bodyWidth / 2f, top), size = Size(bodyWidth, bodyHeight))
        }
    }
    selectedOffset?.let { offset ->
        val date = trend.start.plusDays(offset.toLong())
        val day = trend.days.firstOrNull { it.date == date }
        Text(date.toString(), color = Muted, style = MaterialTheme.typography.labelSmall)
        if (day == null) Text(tr("该日期没有记录，不能视为零用量"), color = Muted, style = MaterialTheme.typography.bodySmall)
        else {
            CompleteValue(formatTokens(day.tokens) + " Token")
            CompleteValue(formatUsageCost(day.costUsd, day.unpricedTokens, compact = true), false)
            candles.firstOrNull { date >= it.first && date <= it.last }?.let { candle ->
                Text("${candle.first} — ${candle.last}", color = Muted, style = MaterialTheme.typography.labelSmall)
                CompleteValue(desktopText("首日 ${formatTokens(candle.open)} · 末日 ${formatTokens(candle.close)}", "First ${formatTokens(candle.open)} · Last ${formatTokens(candle.close)}"))
                CompleteValue(desktopText("最高 ${formatTokens(candle.high)} · 最低 ${formatTokens(candle.low)}", "High ${formatTokens(candle.high)} · Low ${formatTokens(candle.low)}"))
            }
        }
    }
    }

}

private fun trendSeriesColor(palette: Palette, index: Int): Color =
    listOf(Color(0xFF4FA6B5), Color(0xFFCF7C5C), Color(0xFF8B8B87), palette.purple, palette.yellow, palette.blue, palette.success, palette.danger)[index % 8]
