package dev.bing.tokenmonitor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalResources
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.platform.testTag

@Composable
fun LimitWindow(limit: Limit) {
    val strings = LocalResources.current
    val display=LocalLimitDisplay.current
    val currency = limit.currency?.takeIf { it.isNotBlank() } ?: strings.getString(R.string.currency_unknown)
    Column(Modifier.fillMaxWidth().testTag("limit-window-details"),verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(limit.displayProvider(strings), style = quotaTextStyle(11,bold=true))
        
        Text(strings.getString(R.string.limit_window, limit.label?.let {LimitDisplay.text(it,display.maskEmails)} ?: when (limit.kind) { "session" -> strings.getString(R.string.limit_session); "daily" -> strings.getString(R.string.limit_daily); "weekly" -> strings.getString(R.string.limit_weekly); "monthly" -> strings.getString(R.string.limit_monthly); "billing" -> strings.getString(R.string.limit_billing); else -> limit.kind ?: strings.getString(R.string.not_provided) }), style = quotaTextStyle(10))
        when (limit.metric) {
            "credits" -> Text(strings.getString(R.string.limit_balance, limit.amount ?: strings.getString(R.string.not_provided), currency),style=quotaTextStyle(11))
            "spend" -> {
                Text(strings.getString(R.string.limit_used, limit.usedAmount ?: strings.getString(R.string.not_provided), currency),style=quotaTextStyle(11))
                Text(limit.limitAmount?.let { strings.getString(R.string.limit_budget, it, currency) } ?: strings.getString(R.string.limit_no_budget), style = quotaTextStyle(10))
            }
            else -> Text(strings.getString(if(display.showUsed) R.string.desktop_used else R.string.desktop_remaining, LimitDisplay.percent(limit,display.showUsed)?.let { Math.round(it).toString()+"%" } ?: "—"),style=quotaTextStyle(11))
        }
        Text("${when (limit.boundaryKind) { "expiry" -> strings.getString(R.string.boundary_expiry); "mixed" -> strings.getString(R.string.boundary_mixed); else -> strings.getString(R.string.boundary_reset) }} ${LimitDetailTime.format(limit.resets) ?: strings.getString(R.string.not_provided)}", style = quotaTextStyle(10))
        limit.detail?.let { Text(LimitDisplay.text(it,display.maskEmails), style = quotaTextStyle(10)) }
        if(display.showSource) when (limit.componentSource) { "local" -> Text(strings.getString(R.string.limit_source_local), style = quotaTextStyle(10)); "web" -> Text(strings.getString(R.string.limit_source_web), style = quotaTextStyle(10)) }
        limit.status?.let { Text(strings.getString(R.string.limit_status, it), style = quotaTextStyle(10)) }
        if (limit.stale == true) Text(strings.getString(R.string.limit_stale), color = MaterialTheme.colorScheme.error,style=quotaTextStyle(10))
        limit.updated?.let { Text(strings.getString(R.string.limit_updated, LimitDetailTime.format(it) ?: strings.getString(R.string.update_time_unknown)), style = quotaTextStyle(10)) }
    }
}
