package dev.bing.tokenmonitor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalResources
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun LimitWindow(limit: Limit) {
    val strings = LocalResources.current
    val display=LocalLimitDisplay.current
    val currency = limit.currency?.takeIf { it.isNotBlank() } ?: strings.getString(R.string.currency_unknown)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(limit.displayProvider(strings), style = MaterialTheme.typography.titleMedium)
        
        Text(strings.getString(R.string.limit_window, limit.label?.let {LimitDisplay.text(it,display.maskEmails)} ?: when (limit.kind) { "session" -> strings.getString(R.string.limit_session); "daily" -> strings.getString(R.string.limit_daily); "weekly" -> strings.getString(R.string.limit_weekly); "monthly" -> strings.getString(R.string.limit_monthly); "billing" -> strings.getString(R.string.limit_billing); else -> limit.kind ?: strings.getString(R.string.not_provided) }), style = MaterialTheme.typography.bodySmall)
        when (limit.metric) {
            "credits" -> Text(strings.getString(R.string.limit_balance, limit.amount ?: strings.getString(R.string.not_provided), currency))
            "spend" -> {
                Text(strings.getString(R.string.limit_used, limit.usedAmount ?: strings.getString(R.string.not_provided), currency))
                Text(limit.limitAmount?.let { strings.getString(R.string.limit_budget, it, currency) } ?: strings.getString(R.string.limit_no_budget), style = MaterialTheme.typography.bodySmall)
            }
            else -> Text(strings.getString(if(display.showUsed) R.string.desktop_used else R.string.desktop_remaining, LimitDisplay.percent(limit,display.showUsed)?.let { Math.round(it).toString()+"%" } ?: "—"))
        }
        Text("${when (limit.boundaryKind) { "expiry" -> strings.getString(R.string.boundary_expiry); "mixed" -> strings.getString(R.string.boundary_mixed); else -> strings.getString(R.string.boundary_reset) }} ${limit.resets ?: strings.getString(R.string.not_provided)}", style = MaterialTheme.typography.bodySmall)
        limit.detail?.let { Text(LimitDisplay.text(it,display.maskEmails), style = MaterialTheme.typography.bodySmall) }
        if(display.showSource) when (limit.componentSource) { "local" -> Text(strings.getString(R.string.limit_source_local), style = MaterialTheme.typography.bodySmall); "web" -> Text(strings.getString(R.string.limit_source_web), style = MaterialTheme.typography.bodySmall) }
        limit.status?.let { Text(strings.getString(R.string.limit_status, it), style = MaterialTheme.typography.bodySmall) }
        if (limit.stale == true) Text(strings.getString(R.string.limit_stale), color = MaterialTheme.colorScheme.error)
        limit.updated?.let { Text(strings.getString(R.string.limit_updated, it), style = MaterialTheme.typography.bodySmall) }
    }
}
