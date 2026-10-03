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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
            DesktopDetailRow(point.label, "", formatTokens(point.tokens), formatMoney(point.costUsd), Blue)
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
        "TOTAL TOKENS" to formatCompactTokens(totalTokens),
        "TOTAL COST" to formatMoney(totalCost),
        "ACTIVE DAYS" to active.size.toString(),
        "CURRENT STREAK" to currentStreak(history).toString(),
        "ACTIVE TIME" to formatActiveDuration(activeTime),
        "PEAK DAY" to formatCompactTokens(peak),
        "TOP MODEL" to topModel.displayName().ifBlank { "—" },
        "MESSAGES" to formatCompactTokens(messages),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.chunked(2).forEach { rowStats ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowStats.forEach { (label, value) -> OverviewStat(label, value, Modifier.weight(1f)) }
                if (rowStats.size == 1) Spacer(Modifier.weight(1f))
            }
        }
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
            Text(value, color = Ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    if (modelRows) ModelMark(name, accentFor(name), size = 8.dp)
                    else UpstreamToolMark(name, accentFor(name), size = 8.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(name.displayName(), color = Ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatCompactTokens(tokens), color = Ink, style = MaterialTheme.typography.bodySmall)
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
    val points = io.github.theminionooo.tokenmonitor.domain.historyInRange(history, range.days?.let { LocalDate.now().minusDays(it - 1L) } ?: LocalDate.MIN, LocalDate.now())
    val series = points
        .flatMap { point -> (if (group == TrendGroup.Tool) point.perClient else point.perModel).keys }
        .distinct()
        .sortedByDescending { key -> points.sumOf { point -> (if (group == TrendGroup.Tool) point.perClient else point.perModel)[key]?.tokens ?: 0L } }
    val total = points.sumOf { it.tokens }.coerceAtLeast(1L)

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
                Text(formatCompactTokens(points.sumOf { it.tokens }), color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(tr("${points.size} days · ${formatMoney(points.sumOf { it.costUsd })}"), color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            Text(tr(if (group == TrendGroup.Tool) "BY TOOL" else "BY MODEL"), color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        if (style == TrendStyle.Bars) {
            StackedTrendChart(points = points, group = group, series = series, height = 178.dp)
        } else {
            CandleTrendChart(points = points, height = 178.dp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(points.firstOrNull()?.label.orEmpty(), color = Muted, style = MaterialTheme.typography.labelSmall)
            Text(points.lastOrNull()?.label.orEmpty(), color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        if (style == TrendStyle.Bars) {
            val legend = if (series.isEmpty()) listOf("All usage") else series.take(8)
            legend.forEachIndexed { index, key ->
                val value = if (series.isEmpty()) points.sumOf { it.tokens } else points.sumOf { point ->
                    (if (group == TrendGroup.Tool) point.perClient else point.perModel)[key]?.tokens ?: 0L
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(trendSeriesColor(LocalPalette.current, index), size = 8.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(key.displayName(), color = Ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatCompactTokens(value), color = Ink, style = MaterialTheme.typography.bodySmall)
                    Text(tr(String.format(Locale.US, "  %.1f%%", value.toDouble() / total * 100.0)), color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun StackedTrendChart(points: List<HistoryPoint>, group: TrendGroup, series: List<String>, height: androidx.compose.ui.unit.Dp) {
    val reveal = rememberChartReveal(Triple(points.size, group, points.firstOrNull()?.label))
    val palette = LocalPalette.current
    Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
        if (points.isEmpty()) return@Canvas
        val maximum = max(1f, points.maxOf { it.tokens }.toFloat()) / reveal.coerceAtLeast(0.001f)
        val slot = size.width / points.size
        val barWidth = (slot * 0.66f).coerceAtLeast(1.dp.toPx())
        repeat(4) { tick ->
            val y = size.height * tick / 3f
            drawLine(palette.line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        points.forEachIndexed { pointIndex, point ->
            val entries = if (group == TrendGroup.Tool) point.perClient else point.perModel
            val values = if (series.isEmpty()) listOf(point.tokens) else series.map { entries[it]?.tokens ?: 0L }
            var bottom = size.height
            values.forEachIndexed { seriesIndex, value ->
                if (value <= 0) return@forEachIndexed
                val segmentHeight = size.height * value.toFloat() / maximum
                val left = pointIndex * slot + (slot - barWidth) / 2f
                drawRect(trendSeriesColor(palette, seriesIndex), topLeft = Offset(left, bottom - segmentHeight), size = Size(barWidth, segmentHeight))
                bottom -= segmentHeight
            }
        }
    }
}

@Composable
private fun CandleTrendChart(points: List<HistoryPoint>, height: androidx.compose.ui.unit.Dp) {
    val reveal = rememberChartReveal(points.size to points.firstOrNull()?.label)
    val palette = LocalPalette.current
    Canvas(modifier = Modifier.fillMaxWidth().height(height).alpha(reveal)) {
        if (points.isEmpty()) return@Canvas
        val bucketSize = when {
            points.size <= 10 -> 2
            points.size <= 30 -> 3
            points.size <= 90 -> 7
            else -> 14
        }
        val candles = points.chunked(bucketSize)
        val maximum = max(1f, points.maxOf { it.tokens }.toFloat())
        val slot = size.width / candles.size
        val bodyWidth = (slot * 0.45f).coerceAtLeast(2.dp.toPx())
        repeat(4) { tick ->
            val y = size.height * tick / 3f
            drawLine(palette.line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        fun y(value: Long) = size.height - size.height * value.toFloat() / maximum
        candles.forEachIndexed { index, bucket ->
            val open = bucket.first().tokens
            val close = bucket.last().tokens
            val high = bucket.maxOf { it.tokens }
            val low = bucket.minOf { it.tokens }
            val x = index * slot + slot / 2f
            val color = if (close >= open) palette.blue else palette.orange
            drawLine(color, Offset(x, y(high)), Offset(x, y(low)), strokeWidth = 1.dp.toPx())
            val top = minOf(y(open), y(close))
            val bodyHeight = max(2.dp.toPx(), kotlin.math.abs(y(open) - y(close)))
            drawRect(color, topLeft = Offset(x - bodyWidth / 2f, top), size = Size(bodyWidth, bodyHeight))
        }
    }
}

private fun trendSeriesColor(palette: Palette, index: Int): Color =
    listOf(Color(0xFF4FA6B5), Color(0xFFCF7C5C), Color(0xFF8B8B87), palette.purple, palette.yellow, palette.blue, palette.success, palette.danger)[index % 8]
