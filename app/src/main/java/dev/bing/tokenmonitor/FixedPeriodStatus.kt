package dev.bing.tokenmonitor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable internal fun FixedPeriodStatus(view: FixedPeriodView, historyLoaded: Boolean, loading: Boolean, read: () -> Unit) {
    val strings = LocalResources.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        view.range?.let { dates -> Text(strings.getString(R.string.fixed_period_dates, dates.start.toString(), dates.end.toString()),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("fixed-period-range")) }
        view.issue?.let { issue ->
            val message = when (issue) {
                FixedPeriodIssue.HISTORY -> if (historyLoaded) R.string.fixed_period_history else R.string.fixed_period_pending
                FixedPeriodIssue.WINDOW -> R.string.fixed_period_window
                FixedPeriodIssue.INVENTORY -> R.string.fixed_period_inventory
                FixedPeriodIssue.SIZE -> R.string.fixed_period_size
            }
            Text(strings.getString(message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("fixed-period-unavailable"))
            if (issue in listOf(FixedPeriodIssue.HISTORY, FixedPeriodIssue.INVENTORY)) DesktopActionButton(read, enabled = !loading, modifier = Modifier.testTag("fixed-period-read"),visualTag="fixed-period-read-visual") { Text(strings.getString(R.string.fixed_period_read)) }
        }
    }
}

@Composable internal fun FixedPeriodActivitySummary(view: FixedPeriodView) {
    val activity = view.activity ?: return
    val strings = LocalResources.current
    val numbers = java.text.NumberFormat.getIntegerInstance(strings.configuration.locales[0])
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("fixed-period-activity")) {
        Text(strings.getString(R.string.fixed_period_activity), style = MaterialTheme.typography.titleMedium)
        listOf(R.string.activity_active_days to numbers.format(activity.activeDays), R.string.activity_current_streak to numbers.format(activity.currentStreak),
            R.string.activity_active_time to "${activity.activeTimeMs / 3_600_000}h ${(activity.activeTimeMs / 60_000) % 60}m", R.string.activity_peak_day to numbers.format(activity.peakDayTokens)).forEach { (label, value) ->
            Row(Modifier.fillMaxWidth()) { Text(strings.getString(label), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text(value, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
