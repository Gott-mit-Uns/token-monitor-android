package dev.bing.tokenmonitor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalResources
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.text.NumberFormat

/** Calendar cells preserve unknown days and ambiguous duplicate rows. */
object HistoryCalendar {
    fun weeks(history: HubHistory, count: Int = 12): List<List<HistoryRow?>> {
        require(count in 1..53)
        val grouped = history.daily.groupBy { it.date }
        val end = grouped.keys.maxOrNull()?.let(LocalDate::parse) ?: return emptyList()
        val start = end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks((count - 1).toLong())
        return (0 until count).map { week -> (0 until 7).map { day ->
            val date = start.plusDays((week * 7 + day).toLong())
            if (date > end || (count == 53 && date < end.minusDays(364))) null else {
                val rows = grouped[date.toString()].orEmpty()
                if (rows.size == 1) rows.single() else HistoryRow(date.toString(), null, null)
            }
        } }
    }
}

@Composable
fun HistoryActivity(history: HubHistory, metricCost: Boolean = false, select: (HistoryRow) -> Unit) {
    val strings = LocalResources.current
    val numbers = NumberFormat.getIntegerInstance(strings.configuration.locales[0])
    val weeks = HistoryCalendar.weeks(history, 53)
    if (weeks.isEmpty()) return
    fun metric(row: HistoryRow?) = if (metricCost) row?.cost?.let { java.math.BigDecimal(it).toDouble() } else row?.tokens?.toDouble()
    val max = weeks.flatten().filterNotNull().mapNotNull { metric(it) }.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val colors = MaterialTheme.colorScheme
    val chartColor=LocalDesktopChartColor.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val canMoveEarlier by remember { derivedStateOf { scroll.value > 0 } }
    val canMoveLater by remember { derivedStateOf { scroll.value < scroll.maxValue } }
    val step = with(LocalDensity.current) { 208.dp.roundToPx() }
    LaunchedEffect(weeks.last().filterNotNull().lastOrNull()?.date, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(strings.getString(R.string.activity_title), style = MaterialTheme.typography.titleMedium)
        Text(strings.getString(R.string.activity_legend), style = MaterialTheme.typography.bodySmall)
        if (scroll.maxValue > 0) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DesktopTextButton(onClick = { scope.launch { scroll.animateScrollTo((scroll.value - step).coerceAtLeast(0)) } }, enabled = canMoveEarlier) { Text(strings.getString(R.string.activity_earlier)) }
            DesktopTextButton(onClick = { scope.launch { scroll.animateScrollTo((scroll.value + step).coerceAtMost(scroll.maxValue)) } }, enabled = canMoveLater) { Text(strings.getString(R.string.activity_later)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column {
                Text(strings.getString(R.string.week_label), Modifier.height(28.dp), style = MaterialTheme.typography.labelSmall)
                listOf(strings.getString(R.string.weekday_mon), strings.getString(R.string.weekday_tue), strings.getString(R.string.weekday_wed), strings.getString(R.string.weekday_thu), strings.getString(R.string.weekday_fri), strings.getString(R.string.weekday_sat), strings.getString(R.string.weekday_sun)).forEach { Text(it, Modifier.height(52.dp).padding(top = 16.dp), style = MaterialTheme.typography.labelMedium) }
            }
            Row(Modifier.weight(1f).horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            weeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(week.first()?.date?.substring(5).orEmpty(), Modifier.height(24.dp), style = MaterialTheme.typography.labelSmall)
                    week.forEach { row ->
                        val tokens = row?.tokens
                        val value = metric(row)
                        val knownPositive = value != null && value > 0
                        val intensity = if (value != null && knownPositive) (value / max).coerceIn(0.0, 1.0) else 0.0
                        val background = if (knownPositive) chartColor.copy(alpha = (0.24 + intensity * 0.76).toFloat()) else colors.surfaceContainer
                        val foreground = colors.onSurface
                        DesktopTextButton(
                            onClick = { row?.let(select) }, enabled = row != null,
                            modifier = Modifier.size(48.dp).semantics {
                                contentDescription = row?.let { strings.getString(R.string.activity_date_description, it.date, it.tokens?.let { count -> "${numbers.format(count)} Token" } ?: strings.getString(R.string.usage_unknown)) } ?: strings.getString(R.string.history_future)
                            },
                            colors = ButtonDefaults.textButtonColors(containerColor = background, contentColor = foreground),
                            shape = MaterialTheme.shapes.small, contentPadding = PaddingValues(0.dp)
                        ) { Text(if (row == null) "" else if (tokens == null) "—" else if (tokens == 0L) "0" else row.date.takeLast(2), style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            }
        }
        Text(strings.getString(R.string.activity_color_note), style = MaterialTheme.typography.bodySmall)
    }
}
