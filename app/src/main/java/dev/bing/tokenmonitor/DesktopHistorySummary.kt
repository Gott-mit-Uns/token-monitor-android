package dev.bing.tokenmonitor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.text.NumberFormat

/** Uses the Hub lifetime summary, which can extend beyond its capped daily history. */
@Composable internal fun DesktopHistorySummary(summary: HistorySummary) {
    val strings = LocalResources.current
    val numbers = NumberFormat.getIntegerInstance(strings.configuration.locales[0])
    fun count(value: Long?) = value?.let(numbers::format) ?: "—"
    val time = summary.activeTimeMs?.let { "${it / 3_600_000}h ${(it / 60_000) % 60}m" } ?: "—"
    val metrics = listOf(R.string.desktop_total to count(summary.totalTokens), R.string.activity_cost to LocalUsageCurrency.current.headline(summary.totalCost, summary.unpricedTokens),
        R.string.activity_active_days to count(summary.activeDays), R.string.activity_current_streak to count(summary.currentStreak),
        R.string.activity_active_time to time, R.string.activity_peak_day to count(summary.peakDayTokens),
        R.string.activity_favorite_model to (summary.favoriteModel ?: "—"), R.string.activity_messages to count(summary.messages))
    Column(Modifier.testTag("history-summary"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Text(strings.getString(R.string.activity_summary), style = MaterialTheme.typography.titleMedium)
        metrics.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            row.forEach { (title, value) -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(strings.getString(title), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, if (title == R.string.activity_cost) Modifier.testTag("history-summary-cost") else Modifier, style = MaterialTheme.typography.titleMedium)
                if (title == R.string.activity_cost) summary.unpricedTokens?.takeIf { it > 0 }?.let { missing ->
                    // The desktop cost card keeps its known amount and annotates the
                    // excluded tokens. A visible caption makes the same detail readable on touch.
                    Text(strings.getString(R.string.known_unpriced, numbers.format(missing)), Modifier.testTag("history-summary-unpriced"),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } }
        } }
        Text(strings.getString(R.string.activity_summary_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
    }
}
