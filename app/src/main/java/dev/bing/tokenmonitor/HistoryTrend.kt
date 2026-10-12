package dev.bing.tokenmonitor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalResources
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.text.NumberFormat

/** Desktop history controls with exact date details and explicit gaps. */
@Composable
fun HistoryTrend(history: HubHistory) {
    val strings = LocalResources.current
    val numbers = NumberFormat.getIntegerInstance(strings.configuration.locales[0])
    var monthly by rememberSaveable { mutableStateOf(false) }
    var days by rememberSaveable { mutableStateOf<Int?>(30) }
    val summary = remember(history, days) { HistoryRanges.summarize(history, days) }
    var page by rememberSaveable(history, monthly, days) { mutableIntStateOf(0) }
    var selected by remember(history) { mutableStateOf<HistoryRow?>(null) }
    val rows = if (monthly) history.monthly else summary?.rows.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(strings.getString(R.string.history_title), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7 to strings.getString(R.string.range_7_days), 30 to strings.getString(R.string.range_30_days), 90 to strings.getString(R.string.range_90_days), 365 to strings.getString(R.string.range_365_days), null to strings.getString(R.string.range_all)).forEach { (count, label) ->
                FilterChip(days == count, { days = count }, label = { Text(label) })
            }
        }
        summary?.let { range ->
            Text(strings.getString(R.string.date_range, range.start, range.end), style = MaterialTheme.typography.bodySmall)
            Text(strings.getString(R.string.known_tokens, if (range.knownTokenDays == 0) strings.getString(R.string.not_provided) else numbers.format(range.tokens)), style = MaterialTheme.typography.titleMedium)
        }
        summary?.let { range -> DesktopHistoryChart(range.rows, range.start, range.end) { selected = it } }
        summary?.let { range ->
            Text(strings.getString(R.string.history_date_note), style = MaterialTheme.typography.bodySmall)
            Text(strings.getString(R.string.history_coverage, numbers.format(range.recordedDays), numbers.format(range.calendarDays), numbers.format(range.knownTokenDays)), style = MaterialTheme.typography.bodySmall)
            Text(strings.getString(R.string.history_known_cost_formatted, usageCostText(if (range.knownCostDays == 0) null else range.cost.toPlainString(), range.unpricedTokens), numbers.format(range.knownCostDays)), style = MaterialTheme.typography.bodySmall)
            if (range.unpricedTokens.signum() > 0) Text(strings.getString(R.string.known_unpriced, numbers.format(range.unpricedTokens)), style = MaterialTheme.typography.bodySmall)
            if (range.knownTokenDays.toLong() < range.calendarDays) Text(strings.getString(R.string.history_incomplete), style = MaterialTheme.typography.bodySmall)
        }
        history.summary?.let { DesktopHistorySummary(it) }
        var costActivity by rememberSaveable { mutableStateOf(false) }
        var calendarExpanded by rememberSaveable { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!costActivity, { costActivity = false }, label = { Text(strings.getString(R.string.desktop_total)) })
            FilterChip(costActivity, { costActivity = true }, label = { Text(strings.getString(R.string.activity_cost)) })
        }
        DesktopActivityPreview(history, metricCost = costActivity)
        DesktopTextButton(onClick = { calendarExpanded = !calendarExpanded }) { Text(strings.getString(if (calendarExpanded) R.string.desktop_collapse else R.string.activity_select_day)) }
        if (calendarExpanded) HistoryActivity(history, metricCost = costActivity) { selected = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!monthly, { monthly = false }, label = { Text(strings.getString(R.string.history_daily)) })
            FilterChip(monthly, { monthly = true }, label = { Text(strings.getString(R.string.history_monthly)) })
        }
        if (monthly) Text(strings.getString(R.string.monthly_note), style = MaterialTheme.typography.bodySmall)
        if (rows.isEmpty()) Text(strings.getString(R.string.history_missing, if (monthly) strings.getString(R.string.history_monthly) else strings.getString(R.string.history_daily)))
        rows.drop(page * 20).take(20).forEach { row ->
            Column(Modifier.fillMaxWidth().heightIn(min = CompactUiTokens.InteractiveMinHeight).testTag("history-record-${row.date}")
                .clickable { selected = row }.padding(vertical = CompactUiTokens.DetailRowPadding), verticalArrangement = Arrangement.spacedBy(CompactUiTokens.ChartTitleSpacing)) {
                Text(row.date, style = MaterialTheme.typography.bodySmall)
                Text("${row.tokens?.let { numbers.format(it) } ?: strings.getString(R.string.not_provided)} Token")
            }
        }
        if (rows.size > 20) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DesktopTextButton(onClick = { page-- }, enabled = page > 0) { Text(strings.getString(R.string.history_newer)) }
            Text("${page + 1} / ${(rows.size + 19) / 20}", Modifier.padding(top = 12.dp))
            DesktopTextButton(onClick = { page++ }, enabled = (page + 1) * 20 < rows.size) { Text(strings.getString(R.string.history_older)) }
        }
    }
    selected?.let { row ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(row.date) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Token ${row.tokens?.let { numbers.format(it) } ?: strings.getString(R.string.not_provided)}")
                Text(strings.getString(R.string.known_cost_formatted, usageCostText(row.cost, row.unpricedTokens)))
                row.unpricedTokens?.takeIf { it > 0 }?.let { Text(strings.getString(R.string.unpriced_tokens, numbers.format(it))) }
                listOf(R.string.dimension_tools to row.perClient, R.string.dimension_models to row.perModel).forEach { (title, entries) ->
                    if (entries.isNotEmpty()) {
                        HorizontalDivider()
                        Text(strings.getString(title), style = MaterialTheme.typography.titleMedium)
                        entries.toList().sortedByDescending { it.second.tokens }.forEach { (name, value) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (title == R.string.dimension_tools) toolLabel(name) else name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                    Text(value.tokens?.let(numbers::format) ?: "—", style = MaterialTheme.typography.bodySmall)
                                    Text(usageCostText(value.cost, value.unpricedTokens), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }, confirmButton = { DesktopTextButton(onClick = { selected = null }) { Text(strings.getString(R.string.close)) } })
    }
}
