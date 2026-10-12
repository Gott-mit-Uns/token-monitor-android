package dev.bing.tokenmonitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.math.BigInteger
import java.text.NumberFormat
import java.time.LocalDate
import kotlin.math.roundToInt

private enum class ChartMode { BARS, KLINE, LINE }
private fun seriesColor(key: String, byModel: Boolean, palette:Map<String,Long>) = when {
    key == HistoryAnalytics.UNATTRIBUTED -> Color(0xFF8C98AA)
    byModel -> modelColor(key,palette)
    else -> toolColor(key,palette)
}

@Composable internal fun DesktopHistoryChart(rows: List<HistoryRow>, start: LocalDate, end: LocalDate, select: (HistoryRow) -> Unit) {
    val strings = LocalResources.current; val colors = MaterialTheme.colorScheme; val chartColor=LocalDesktopChartColor.current;val palette=LocalDesktopVendorPalette.current
    val numbers = NumberFormat.getIntegerInstance(strings.configuration.locales[0])
    var mode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(ChartMode.BARS) }
    var byModel by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var candleDetail by remember { mutableStateOf<HistoryCandle?>(null) }
    val calendar = remember(rows, start, end) {
        val grouped = rows.groupBy { it.date }
        (0..(end.toEpochDay() - start.toEpochDay()).coerceAtMost(3660)).map { delta ->
            val date = start.plusDays(delta).toString()
            grouped[date]?.singleOrNull() ?: HistoryRow(date, null, null)
        }
    }
    val stacks = remember(calendar, byModel) { calendar.map { HistoryAnalytics.stacks(it, byModel) } }
    val totals = remember(stacks) {
        val totals = linkedMapOf<String, BigInteger>()
        stacks.forEach { stack -> stack.forEach { (key, value) -> totals[key] = (totals[key] ?: BigInteger.ZERO) + value } }
        totals.toList().filter { it.second.signum() > 0 }.sortedByDescending { it.second }
    }
    val peak = calendar.mapNotNull { it.tokens }.maxOrNull()
    val maximum = peak?.coerceAtLeast(1) ?: 1L
    val scroll = rememberScrollState()
    LaunchedEffect(rows, mode, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ChartMode.BARS to R.string.chart_bars, ChartMode.KLINE to R.string.chart_kline, ChartMode.LINE to R.string.chart_line).forEach { (choice, label) ->
                FilterChip(mode == choice, { mode = choice }, label = { Text(strings.getString(label)) })
            }
        }
        if (mode == ChartMode.BARS) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!byModel, { byModel = false }, label = { Text(strings.getString(R.string.chart_by_tool)) },modifier=Modifier.testTag("history-by-tool"))
            FilterChip(byModel, { byModel = true }, label = { Text(strings.getString(R.string.chart_by_model)) },modifier=Modifier.testTag("history-by-model"))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(strings.getString(R.string.history_peak, peak?.let(numbers::format) ?: "—"), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Text(strings.getString(R.string.chart_tap_detail), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = if (mode == ChartMode.BARS) maxOf(maxWidth, (calendar.size * 7).dp) else maxWidth
            val bucketDays = if (calendar.size <= 10) 2 else maxOf(3, (calendar.size / maxOf(8, (maxWidth.value / 24).roundToInt()).toFloat()).roundToInt())
            val candles = remember(rows, bucketDays) { HistoryAnalytics.candles(rows, bucketDays) }
            val chartMax = if (mode == ChartMode.KLINE) candles.maxOfOrNull { it.high }?.coerceAtLeast(1) ?: 1L else maximum
            Row(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
                Canvas(Modifier.width(width).height(180.dp).testTag("desktop-history-chart").semantics {
                    contentDescription = strings.getString(R.string.history_chart_description, start, end)
                }.pointerInput(calendar, candles, mode, byModel) {
                    detectTapGestures { point ->
                        if (mode == ChartMode.KLINE) {
                            candles.getOrNull((point.x / size.width * candles.size).toInt().coerceIn(0, (candles.size - 1).coerceAtLeast(0)))?.let { candleDetail = it }
                        } else calendar.getOrNull((point.x / size.width * calendar.size).toInt().coerceIn(0, calendar.lastIndex))?.let(select)
                    }
                }) {
                    val top = 8.dp.toPx(); val bottom = size.height - 8.dp.toPx(); val height = bottom - top
                    repeat(4) { tick -> val y = bottom - tick * height / 3
                        drawLine(colors.outlineVariant.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx()) }
                    fun y(value: Double) = bottom - (value / chartMax * height).toFloat()
                    when (mode) {
                        ChartMode.BARS -> {
                            val slot = size.width / calendar.size
                            stacks.forEachIndexed { index, stack ->
                                var sum = 0.0
                                totals.forEach { (key, _) -> stack[key]?.let { count ->
                                    val value = count.toDouble(); val segment = (value / chartMax * height).toFloat()
                                    sum += value
                                    if (segment > 0) drawRect(seriesColor(key, byModel,palette), Offset(index * slot + slot * 0.15f, y(sum)), Size(slot * 0.7f, segment))
                                } }
                                if (calendar[index].tokens == 0L) drawCircle(colors.onSurfaceVariant, 1.5.dp.toPx(), Offset((index + 0.5f) * slot, bottom))
                            }
                        }
                        ChartMode.KLINE -> {
                            val slot = size.width / candles.size.coerceAtLeast(1)
                            candles.forEachIndexed { index, candle ->
                                val open = candle.open; val close = candle.close
                                val color = if (open == null || close == null) Color(0xFF8C98AA) else if (close >= open) Color(0xFF82C6AE) else Color(0xFFE18C8F)
                                val x = (index + 0.5f) * slot
                                drawLine(color, Offset(x, y(candle.high.toDouble())), Offset(x, y(candle.low.toDouble())), 1.5.dp.toPx())
                                if (open != null && close != null) {
                                    val highBody = y(maxOf(open, close).toDouble()); val lowBody = y(minOf(open, close).toDouble())
                                    drawRect(color, Offset(x - slot * 0.28f, highBody), Size(slot * 0.56f, maxOf(2.dp.toPx(), lowBody - highBody)))
                                }
                            }
                        }
                        ChartMode.LINE -> {
                            var previous: Offset? = null
                            calendar.forEachIndexed { index, row ->
                                if (row.tokens == null) previous = null else {
                                    val point = Offset((index + 0.5f) / calendar.size * size.width, y(row.tokens.toDouble()))
                                    previous?.let { drawLine(chartColor, it, point, 2.dp.toPx()) }
                                    drawCircle(chartColor, 2.dp.toPx(), point); previous = point
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(start.toString(), style = MaterialTheme.typography.labelSmall); Text(end.toString(), style = MaterialTheme.typography.labelSmall)
        }
        if (mode == ChartMode.BARS) {
            val grand = totals.fold(BigInteger.ZERO) { sum, entry -> sum + entry.second }
            totals.forEach { (key, value) -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(seriesColor(key, byModel,palette)).testTag("history-color-$key")); Spacer(Modifier.width(8.dp))
                Text(if (key == HistoryAnalytics.UNATTRIBUTED) strings.getString(R.string.chart_unattributed) else if (byModel) key else toolLabel(key), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(numbers.format(value), style = MaterialTheme.typography.bodySmall)
                Text(if (grand.signum() > 0) " ${(value.toDouble() / grand.toDouble() * 100).let { String.format(strings.configuration.locales[0], "%.1f", it) }}%" else " —", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            } }
        }
        Text(strings.getString(if (mode == ChartMode.KLINE) R.string.chart_kline_note else R.string.history_gap_note), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
    candleDetail?.let { candle -> AlertDialog(onDismissRequest = { candleDetail = null }, title = { Text("${candle.start} – ${candle.end}") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.chart_open to candle.open, R.string.chart_close to candle.close, R.string.chart_high to candle.high, R.string.chart_low to candle.low).forEach { (name, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(strings.getString(name)); Text(value?.let(numbers::format) ?: "—") }
            }
            Text(strings.getString(R.string.chart_candle_coverage, candle.recorded, candle.calendarDays))
        }
    }, confirmButton = { DesktopTextButton(onClick = { candleDetail = null }) { Text(strings.getString(R.string.close)) } }) }
}
