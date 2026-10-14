package dev.bing.tokenmonitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalResources
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.NumberFormat

@Composable
fun DeviceDetails(device: Device, alias: String?, onDismiss: () -> Unit, onRename: () -> Unit, initialRange: String = "today") {
    val strings = LocalResources.current
    val numbers = NumberFormat.getIntegerInstance(strings.configuration.locales[0])
    var range by remember(device.id) { mutableStateOf(initialRange) }
    val period = device.periods[range]
    AlertDialog(onDismissRequest = onDismiss, title = { Text(DeviceDisplay.name(device,alias,strings.getString(R.string.devices))) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (alias != null) Text(strings.getString(R.string.hub_original_name, device.displayName(strings)))
            Text(when (device.stale) { true -> strings.getString(R.string.device_stale); false -> strings.getString(R.string.device_not_stale); null -> strings.getString(R.string.freshness_unknown) })
            Text(strings.getString(R.string.source_updated, device.updated ?: strings.getString(R.string.not_provided)), style = MaterialTheme.typography.bodySmall)
            device.received?.let { Text(strings.getString(R.string.hub_received, it), style = MaterialTheme.typography.bodySmall) }
            device.platform?.let { Text(it) }
            listOfNotNull(device.agentRuntime?.takeIf { it.isNotBlank() }, device.agentVersion?.takeIf { it.isNotBlank() })
                .takeIf { it.isNotEmpty() }?.let { Text(strings.getString(R.string.source_version, it.joinToString(" · ")), style = MaterialTheme.typography.bodySmall) }
            Text(strings.getString(R.string.source_device_usage), style = MaterialTheme.typography.titleMedium)
            Text(strings.getString(R.string.device_usage_note), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                mapOf("today" to strings.getString(R.string.period_today), "month" to strings.getString(R.string.period_month), "allTime" to strings.getString(R.string.period_all_time)).plus(device.periods.keys.filter(FixedPeriods::derived).associateWith(FixedPeriods::label)).forEach { (key, title) ->
                    FilterChip(range == key, { range = key }, label = { Text(title) })
                }
            }
            if (period == null) Text(strings.getString(R.string.device_period_missing)) else {
                Text("${period.tokens?.let { numbers.format(it) } ?: strings.getString(R.string.not_provided)} Token")
                Text(strings.getString(R.string.known_cost_formatted, usageCostText(period.cost, period.unpriced)))
                period.window?.let { Text(strings.getString(R.string.source_window, it), style = MaterialTheme.typography.bodySmall) }
                period.zone?.let { Text(strings.getString(R.string.source_timezone, it), style = MaterialTheme.typography.bodySmall) }
                period.ends?.let { Text(strings.getString(R.string.window_end, it), style = MaterialTheme.typography.bodySmall) }
                period.tools.toList().sortedByDescending { it.second }.forEach { (tool, tokens) ->
                    Text("${tool} · ${numbers.format(tokens)} Token", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(strings.getString(R.string.collection_health), style = MaterialTheme.typography.titleMedium)
            val health = device.health
            if (health == null || health.clients.isEmpty()) Text(strings.getString(R.string.health_not_provided)) else {
                Text(strings.getString(R.string.health_observed, health.observed ?: strings.getString(R.string.not_provided)), style = MaterialTheme.typography.bodySmall)
                Text(strings.getString(R.string.health_note), style = MaterialTheme.typography.bodySmall)
                health.clients.forEach { client ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(toolLabel(client.client), style = MaterialTheme.typography.titleSmall)
                        Text(when (client.overall) { "healthy" -> strings.getString(R.string.health_healthy); "waiting" -> strings.getString(R.string.health_waiting); "attention" -> strings.getString(R.string.health_attention); "unavailable" -> strings.getString(R.string.health_unavailable); else -> strings.getString(R.string.health_unknown) },
                            color = if (client.overall == "attention") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        Text(strings.getString(R.string.data_source, when (client.source) { "detected" -> strings.getString(R.string.source_detected); "missing" -> strings.getString(R.string.source_missing); else -> strings.getString(R.string.unknown) }), style = MaterialTheme.typography.bodySmall)
                        Text(strings.getString(R.string.collection_state, when (client.collection) { "direct" -> strings.getString(R.string.collection_direct); "idle" -> strings.getString(R.string.collection_idle); "pending" -> strings.getString(R.string.collection_pending); "ok" -> strings.getString(R.string.collection_ok); "failed" -> strings.getString(R.string.collection_failed); else -> strings.getString(R.string.unknown) }), style = MaterialTheme.typography.bodySmall)
                        Text(strings.getString(R.string.scan_total, client.liveTokens?.let { numbers.format(it) } ?: strings.getString(R.string.not_provided)), style = MaterialTheme.typography.bodySmall)
                        client.lastActivityDay?.let { Text(strings.getString(R.string.recent_usage_date, it), style = MaterialTheme.typography.bodySmall) }
                        client.sourceChecks.forEach {check -> Text("${check.id}: ${strings.getString(if(check.exists) R.string.source_detected else R.string.source_missing)}",style=MaterialTheme.typography.bodySmall) }
                        client.diagnostics.forEach { code -> Text(when (code) {
                            "sync-timeout" -> strings.getString(R.string.diagnostic_timeout); "sync-spawn-failed" -> strings.getString(R.string.diagnostic_spawn_failed); "sync-exit-error" -> strings.getString(R.string.diagnostic_exit_error); "sync-failed" -> strings.getString(R.string.collection_failed)
                            "source-missing" -> strings.getString(R.string.diagnostic_source_missing); "no-usage-observed" -> strings.getString(R.string.diagnostic_no_usage); "wsl-detected-no-data" -> strings.getString(R.string.diagnostic_wsl); else -> strings.getString(R.string.diagnostic_unknown)
                        }, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }, confirmButton = { DesktopTextButton(onClick = onDismiss) { Text(strings.getString(R.string.close)) } },
        dismissButton = { DesktopTextButton(onClick = onRename) { Text(strings.getString(R.string.rename_device)) } })
}
