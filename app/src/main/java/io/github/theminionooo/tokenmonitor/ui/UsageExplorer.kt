package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.localization.tr
import io.github.theminionooo.tokenmonitor.localization.localizedText
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.domain.*
import java.time.LocalDate

@Composable
internal fun UsageComparisonPanel(history: List<HistoryPoint>) {
    var days by rememberSaveable { mutableIntStateOf(7) }
    val today = LocalDate.now()
    val comparison = remember(history, days, today) { compareUsage(history, today, days) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
        Text(tr("PERIOD COMPARISON"), color = Ink, style = MaterialTheme.typography.labelMedium)
        ChoiceGroup(listOf("7 DAYS" to "7", "30 DAYS" to "30"), days.toString(), { days = it.toInt() })
        Text(tr("Last $days completed days vs preceding $days"), color = Muted, style = MaterialTheme.typography.labelSmall)
        Text(tr("${formatTokens(comparison.current.sumOf { it.tokens })} vs ${formatTokens(comparison.previous.sumOf { it.tokens })} tokens"), color = Ink)
        if (comparison.complete) {
            Text(tr("${signedTokens(comparison.tokenDelta)} tokens${comparison.percentChange?.let { " · %+.1f%%".format(java.util.Locale.US, it) }.orEmpty()}"), color = Accent)
            Text(tr("Estimated cost: ${formatMoney(comparison.current.sumOf { it.costUsd })} vs ${formatMoney(comparison.previous.sumOf { it.costUsd })}"), color = Muted, style = MaterialTheme.typography.bodySmall)
            comparison.drivers(byModel = true).filter { it.second != 0L }.take(3).forEach { (name, delta) ->
                Text(tr("$name: ${signedTokens(delta)} tokens"), color = Ink, style = MaterialTheme.typography.bodySmall)
            }
            Text(tr("Model changes use reported attribution; missing breakdowns are not estimated."), color = Muted, style = MaterialTheme.typography.labelSmall)
        } else {
            Text(tr("Incomplete history: ${comparison.current.size}/$days and ${comparison.previous.size}/$days days recorded. Missing days are not treated as zero; percentage change is unavailable."), color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider(color = Line)
    }
}

private fun signedTokens(value: Long) = (if (value >= 0) "+" else "−") + formatTokens(kotlin.math.abs(value))

@Composable
internal fun UsageExplorer(history: List<HistoryPoint>) {
    val focus = LocalFocusManager.current
    var models by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val values = remember(history, models, query) {
        history.flatMap { (if (models) it.perModel else it.perClient).entries }
            .groupBy { it.key }.mapValues { (_, entries) -> entries.sumOf { it.value.tokens } }
            .filterKeys { it.contains(query, ignoreCase = true) }.entries.sortedByDescending { it.value }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("EXPLORE USAGE"), color = Ink, style = MaterialTheme.typography.labelMedium)
        Text(tr("Tap a tool or model to see its recorded daily usage."), color = Muted, style = MaterialTheme.typography.bodySmall)
        ChoiceGroup(listOf("BY MODEL" to "model", "BY TOOL" to "tool"), if (models) "model" else "tool", { models = it == "model" })
        CompactSearchField(query, { query = it }, "Find a tool or model")
        Column { values.take(8).forEach { (name, value) ->
            ExplorerRow(name, formatTokens(value)) { focus.clearFocus(); selected = name }
        } }
        if (values.size > 8) Text(tr("${values.size - 8} more matches. Refine the search to find them."), color = Muted, style = MaterialTheme.typography.labelSmall)
        if (values.isEmpty()) Text(tr("No matching attribution in the available history."), color = Muted)
    }
    selected?.let { SeriesUsageDialog(it, models, history) { selected = null } }
}

@Composable
internal fun DayUsageDialog(date: String, history: List<HistoryPoint>, onDismiss: () -> Unit) {
    val point = history.firstOrNull { it.label.take(10) == date }
    var models by rememberSaveable { mutableStateOf(true) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    if (selected == null) UsageDetailDialog(date, onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr(if (point == null) "No observation was supplied for this date." else "${formatTokens(point.tokens)} tokens · ${formatMoney(point.costUsd)} estimated"), color = Ink)
            ChoiceGroup(listOf("MODELS" to "model", "TOOLS" to "tool"), if (models) "model" else "tool", { models = it == "model" })
            val entries = (if (models) point?.perModel else point?.perClient).orEmpty().entries.sortedByDescending { it.value.tokens }
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(entries, key = { it.key }) { (name, value) -> ExplorerRow(name, formatTokens(value.tokens)) { selected = name } }
                if (entries.isEmpty()) item { Text(tr("The Hub has no breakdown for this day."), color = Muted) }
            }
            Text(tr("Sessions and projects are available for the Hub's Day, Month, and Total periods."), color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
    selected?.let { SeriesUsageDialog(it, models, history) { selected = null } }
}

@Composable
private fun SeriesUsageDialog(name: String, models: Boolean, history: List<HistoryPoint>, onDismiss: () -> Unit) {
    val points = history.mapNotNull { point -> (if (models) point.perModel else point.perClient)[name]?.let { point.label to it } }
    UsageDetailDialog(name, onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("${formatTokens(points.sumOf { it.second.tokens })} tokens across ${points.size} recorded ${if (points.size == 1) "day" else "days"}"), color = Ink)
            Text(tr("Daily usage, newest first. Costs are estimates from the desktop Hub."), color = Muted, style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(points.reversed(), key = { it.first }) { (date, value) ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(date, color = Ink, fontWeight = FontWeight.SemiBold)
                        Text(tr("${formatTokens(value.tokens)} tokens · ${formatMoney(value.costUsd)}"), color = Muted)
                        UsageBar(value.tokens.toFloat() / points.maxOf { it.second.tokens }.coerceAtLeast(1), Blue)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplorerRow(name: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClickLabel = localizedText("Explore $name"), onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(name, color = Ink, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(value, color = Accent, style = MaterialTheme.typography.bodySmall)
    }
}

/** Tool-specific attribution must never borrow a model's global totals or breakdown. */
internal fun UsagePeriod.modelsForTool(tool: String): UsagePeriod = UsagePeriod(
    totalTokens = clients[tool] ?: 0,
    costUsd = clientCosts[tool] ?: 0.0,
    models = clientModels[tool].orEmpty(),
    modelCosts = clientModelCosts[tool].orEmpty(),
    unpricedTokens = clientUnpricedTokens[tool] ?: 0,
    modelUnpricedTokens = clientModelUnpricedTokens[tool].orEmpty(),
)

@Composable
internal fun CompactSearchField(value: String, onValueChange: (String) -> Unit, hint: String) {
    var focused by remember { mutableStateOf(false) }
    val textStyle = MaterialTheme.typography.bodySmall.copy(color = Ink)
    val border = if (focused) Accent else Line
    Surface(color = Overlay, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, border)) {
        BasicTextField(value, onValueChange, singleLine = true, textStyle = textStyle, cursorBrush = SolidColor(Accent),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).onFocusChanged { focused = it.isFocused }.semantics { contentDescription = localizedText(hint) },
            decorationBox = { inner ->
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).padding(vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) Text(tr(hint), color = Muted, style = MaterialTheme.typography.bodySmall)
                        inner()
                    }
                    if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = localizedText("Clear search"), tint = Muted, modifier = Modifier.size(18.dp))
                    } else Spacer(Modifier.width(12.dp))
                }
            })
    }
}

@Composable
private fun UsageDetailDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val containerHeight = LocalWindowInfo.current.containerSize.height
    val maxHeight = with(LocalDensity.current) { containerHeight.toDp() * 0.85f }
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Shell, contentColor = Ink, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, StrongLine)) {
            Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr(title), color = Ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                HorizontalDivider(color = Line)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { content() }
                HorizontalDivider(color = Line)
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(tr("CLOSE"), color = Accent, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}
