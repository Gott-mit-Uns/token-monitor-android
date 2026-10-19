package dev.bing.tokenmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Hub calendar keys and totals are projected as supplied; selection never starts a network read. */
@Composable
fun HistoryTrend(history: HubHistory?, loading: Boolean = false, error: Int? = null,
    today: LocalDate = LocalDate.now()) {
    val strings = LocalResources.current
    val locale = strings.configuration.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    var monthly by rememberSaveable { mutableStateOf(false) }
    val latestDay = history?.daily?.maxOfOrNull { it.date } ?: today.toString()
    val latestMonth = history?.monthly?.maxOfOrNull { it.date } ?: latestDay.take(7)
    var monthKey by rememberSaveable { mutableStateOf(latestDay.take(7)) }
    var year by rememberSaveable { mutableIntStateOf(latestMonth.take(4).toInt()) }
    var selectedDay by rememberSaveable { mutableStateOf(latestDay) }
    var selectedMonth by rememberSaveable { mutableStateOf(latestMonth) }
    val month = YearMonth.parse(monthKey)
    val dayGrid = remember(history, month, today, loading, error) {
        RecordCalendarProjection.days(history, month, today, null, loading, error != null)
    }
    val monthGrid = remember(history, year, today, loading, error) {
        RecordCalendarProjection.months(history, year, today, loading, error != null)
    }
    val lastDayMonth = remember(history, today) { RecordCalendarProjection.dailyNavigationEnd(history, today) }
    val lastMonthYear = remember(history, today) { RecordCalendarProjection.monthlyNavigationEndYear(history, today) }
    val selected = if (monthly) monthGrid.firstOrNull { it.key == selectedMonth }
        else dayGrid.flatten().filterNotNull().firstOrNull { it.key == selectedDay }
    val text = MaterialTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(CompactUiTokens.SectionSpacing)) {
        Text(strings.getString(R.string.history_title), style = quotaTextStyle(13, true))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!monthly, { monthly = false }, label = { Text(strings.getString(R.string.history_daily), style = quotaTextStyle(12)) }, modifier = Modifier.testTag("records-daily"))
            FilterChip(monthly, { monthly = true }, label = { Text(strings.getString(R.string.history_monthly), style = quotaTextStyle(12)) }, modifier = Modifier.testTag("records-monthly"))
        }
        if (monthly) Text(strings.getString(R.string.record_month_scope), style = quotaTextStyle(11), color = text.copy(alpha = .6f))
        RecordCalendarHeader(
            title = if (monthly) year.toString() else month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
            previous = strings.getString(if (monthly) R.string.record_previous_year else R.string.record_previous_month),
            next = strings.getString(if (monthly) R.string.record_next_year else R.string.record_next_month),
            canPrevious = if (monthly) year > 1900 else month.year > 1900,
            canNext = if (monthly) year < lastMonthYear else month < lastDayMonth,
            onPrevious = { if (monthly) year-- else monthKey = month.minusMonths(1).toString() },
            onNext = { if (monthly) year++ else monthKey = month.plusMonths(1).toString() })
        if (monthly) {
            Column(Modifier.testTag("records-month-grid"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                monthGrid.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { cell ->
                            RecordCalendarTile(cell, YearMonth.parse(cell.key).format(DateTimeFormatter.ofPattern("MMM", locale)),
                                selectedMonth == cell.key, false, Modifier.weight(1f).fillMaxHeight()) { selectedMonth = cell.key }
                        }
                    }
                }
            }
        } else {
            // Seven accessible targets keep their width on narrow phones; the small overflow scrolls.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gridWidth = maxOf(maxWidth, 336.dp)
                Column(Modifier.horizontalScroll(rememberScrollState()).width(gridWidth).testTag("records-day-grid"),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf(R.string.weekday_mon, R.string.weekday_tue, R.string.weekday_wed, R.string.weekday_thu,
                            R.string.weekday_fri, R.string.weekday_sat, R.string.weekday_sun).forEach {
                            Text(strings.getString(it), Modifier.weight(1f), textAlign = TextAlign.Center,
                                style = quotaTextStyle(10), color = text.copy(alpha = .55f))
                        }
                    }
                    dayGrid.forEach { row ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                            row.forEach { cell ->
                                if (cell == null) Spacer(Modifier.weight(1f)) else RecordCalendarTile(cell,
                                    LocalDate.parse(cell.key).dayOfMonth.toString(), selectedDay == cell.key,
                                    cell.key == today.toString(), Modifier.weight(1f).fillMaxHeight()) { selectedDay = cell.key }
                            }
                        }
                    }
                }
            }
        }
        Text(strings.getString(R.string.record_calendar_legend), style = quotaTextStyle(10), color = text.copy(alpha = .6f))
        if (loading) Text(strings.getString(R.string.record_loading), Modifier.testTag("records-loading"), style = quotaTextStyle(11), color = text.copy(alpha = .6f))
        error?.let { Text(strings.getString(it), Modifier.testTag("records-error"), style = quotaTextStyle(11), color = MaterialTheme.colorScheme.error) }
        selected?.let { RecordInlineDetail(it, numbers, error) }
            ?: Text(strings.getString(R.string.record_select), style = quotaTextStyle(11), color = text.copy(alpha = .6f))
    }
}

/** Independent sibling of record details, backed by the same accepted history read. */
@Composable internal fun HistoryAnalysis(history:HubHistory?,loading:Boolean=false,error:Int?=null) {
    val strings=LocalResources.current
    val numbers=remember(strings.configuration.locales[0]) {NumberFormat.getIntegerInstance(strings.configuration.locales[0])}
    // Preserve the existing chart default; the sibling calendar has no range restriction.
    val summary=remember(history) {history?.let {HistoryRanges.summarize(it,30)}}
    var costActivity by rememberSaveable {mutableStateOf(false)}
    var activitySelection by remember(history) {mutableStateOf<HistoryRow?>(null)}
    Column(Modifier.fillMaxWidth().testTag("records-analysis"),verticalArrangement=Arrangement.spacedBy(CompactUiTokens.SectionSpacing)) {
        Text(strings.getString(R.string.record_analysis_show),style=quotaTextStyle(13,true))
        if(loading) Text(strings.getString(R.string.record_loading),Modifier.testTag("records-loading"),style=quotaTextStyle(11))
        error?.let {Text(strings.getString(it),Modifier.testTag("records-error"),style=quotaTextStyle(11),color=MaterialTheme.colorScheme.error)}
        if(history==null) Text(strings.getString(R.string.record_not_loaded),style=quotaTextStyle(11))
        else {
            summary?.let {range->DesktopHistoryChart(range.rows,range.start,range.end) {activitySelection=it}}
            history.summary?.let {DesktopHistorySummary(it)}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(!costActivity,{costActivity=false},label={Text(strings.getString(R.string.desktop_total),style=quotaTextStyle(11))})
                FilterChip(costActivity,{costActivity=true},label={Text(strings.getString(R.string.activity_cost),style=quotaTextStyle(11))})
            }
            HistoryActivity(history,metricCost=costActivity) {activitySelection=it}
            activitySelection?.let {RecordInlineDetail(RecordCalendarCell(it.date,it,if(it.tokens==null) RecordCellState.UNKNOWN else RecordCellState.RECORDED),numbers)}
        }
    }
}

@Composable private fun RecordCalendarHeader(title: String, previous: String, next: String,
    canPrevious: Boolean, canNext: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DesktopTextButton(onPrevious, enabled = canPrevious, modifier = Modifier.size(48.dp).testTag("records-calendar-previous")
            .semantics { contentDescription = previous }, contentPadding = PaddingValues(0.dp)) { Text("‹", style = quotaTextStyle(20)) }
        Text(title, Modifier.weight(1f).testTag("records-calendar-title"), textAlign = TextAlign.Center, style = quotaTextStyle(13, true))
        DesktopTextButton(onNext, enabled = canNext, modifier = Modifier.size(48.dp).testTag("records-calendar-next")
            .semantics { contentDescription = next }, contentPadding = PaddingValues(0.dp)) { Text("›", style = quotaTextStyle(20)) }
    }
}

@Composable private fun RecordCalendarTile(cell: RecordCalendarCell, label: String, active: Boolean,
    today: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val strings = LocalResources.current
    val locale = strings.configuration.locales[0]
    val tokens = cell.row?.tokens?.takeIf { cell.state == RecordCellState.RECORDED }
    val full = tokens?.let { NumberFormat.getIntegerInstance(locale).format(it) }
    val value = tokens?.let { DesktopCompactTokens.format(it.toDouble(), LocalMainDisplay.current.compactUnits, DesktopCompactTokens.localeTag(locale)) }
        ?: when (cell.state) { RecordCellState.NO_RECORD -> "·"; RecordCellState.LOADING -> "…"; RecordCellState.FUTURE -> ""; else -> "—" }
    val enabled = cell.state != RecordCellState.FUTURE && cell.state != RecordCellState.OUTSIDE_RANGE
    val colors = MaterialTheme.colorScheme
    val stateLabel = recordStateLabel(cell.state)
    val description = strings.getString(R.string.record_cell_description, cell.key,
        full?.let { "$it Token" } ?: stateLabel) + if (today) ", ${strings.getString(R.string.record_today)}" else ""
    Column(modifier.heightIn(min = 48.dp)
        .background(if (active) LocalDesktopOverlayColor.current.copy(alpha = .12f) else colors.surfaceContainer.copy(alpha = .4f), RoundedCornerShape(5.dp))
        .then(if (today) Modifier.border(1.dp, colors.primary, RoundedCornerShape(5.dp)) else Modifier)
        .testTag("history-record-${cell.key}")
        .semantics(mergeDescendants = true) { selected = active; contentDescription = description }
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 2.dp, vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text(label, style = quotaTextStyle(11, active), color = colors.onSurface.copy(alpha = if (enabled) .9f else .35f), textAlign = TextAlign.Center)
        Text(value, style = quotaDataTextStyle(10), color = colors.onSurface.copy(alpha = if (enabled) .65f else .3f), textAlign = TextAlign.Center)
    }
}

@Composable private fun recordStateLabel(state: RecordCellState): String {
    val strings = LocalResources.current
    return strings.getString(when (state) {
        RecordCellState.RECORDED -> R.string.dashboard_recorded_tokens
        RecordCellState.UNKNOWN -> R.string.usage_unknown
        RecordCellState.NO_RECORD -> R.string.record_no_record
        RecordCellState.UNAVAILABLE -> R.string.record_source_unavailable
        RecordCellState.OUTSIDE_RANGE -> R.string.record_outside_range
        RecordCellState.FUTURE -> R.string.record_future
        RecordCellState.NOT_LOADED -> R.string.record_not_loaded
        RecordCellState.LOADING -> R.string.record_loading
        RecordCellState.FAILED -> R.string.record_read_failed
    })
}

@Composable private fun RecordInlineDetail(cell: RecordCalendarCell, numbers: NumberFormat, error: Int? = null) {
    val strings = LocalResources.current
    val text = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().testTag("records-selected-detail").background(
        LocalDesktopOverlayColor.current.copy(alpha = .04f), RoundedCornerShape(7.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(cell.key, Modifier.testTag("records-selected-date"), style = quotaTextStyle(12, true))
        if (cell.state !in setOf(RecordCellState.RECORDED, RecordCellState.UNKNOWN)) {
            Text(if (cell.state == RecordCellState.FAILED && error != null) strings.getString(error) else recordStateLabel(cell.state), style = quotaTextStyle(12), color = text.copy(alpha = .65f))
        } else cell.row?.let { row ->
            Text("Token ${row.tokens?.let(numbers::format) ?: strings.getString(R.string.not_provided)}", Modifier.testTag("records-selected-tokens"), style = quotaDataTextStyle(14))
            Text(strings.getString(R.string.known_cost_formatted, usageCostText(row.cost, row.unpricedTokens)), style = quotaTextStyle(11))
            row.unpricedTokens?.takeIf { it > 0 }?.let { Text(strings.getString(R.string.unpriced_tokens, numbers.format(it)), style = quotaTextStyle(11)) }
            listOf(R.string.dimension_tools to row.perClient, R.string.dimension_models to row.perModel).forEach { (title, entries) ->
                if (entries.isNotEmpty()) {
                    HorizontalDivider(color = LocalDesktopOverlayColor.current.copy(alpha = .1f))
                    Text(strings.getString(title), style = quotaTextStyle(11, true))
                    entries.toList().sortedByDescending { it.second.tokens }.forEach { (name, value) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (title == R.string.dimension_tools) toolLabel(name) else name, Modifier.weight(1f), style = quotaTextStyle(11))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(value.presentationTokens()?.let(numbers::format) ?: "—", style = quotaDataTextStyle(11))
                                Text(usageCostText(value.cost, value.unpricedTokens), style = quotaTextStyle(10), color = text.copy(alpha = .6f))
                            }
                        }
                    }
                }
            }
        }
    }
}
