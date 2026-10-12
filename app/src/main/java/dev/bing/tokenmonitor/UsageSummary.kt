package dev.bing.tokenmonitor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalResources
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.text.NumberFormat

/** Summary panels grow only when both retain at least 300dp of reading space. */
@Composable
fun UsageSummary(period: Period?, rangeLabel: String?) {
    val strings = LocalResources.current
    val numbers = NumberFormat.getIntegerInstance(strings.configuration.locales[0])
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val twoColumns = maxWidth >= 640.dp
        @Composable fun Total(modifier: Modifier = Modifier) {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${rangeLabel} Token", style = MaterialTheme.typography.labelLarge)
                Text(period?.tokens?.let { numbers.format(it) } ?: strings.getString(R.string.not_provided), style = MaterialTheme.typography.displaySmall)
                Text(strings.getString(R.string.estimated_cost_formatted, LocalUsageCurrency.current.headline(period?.cost, period?.unpriced)))
                period?.unpriced?.takeIf { it > 0 }?.let { Text(strings.getString(R.string.unpriced_note, numbers.format(it)), style = MaterialTheme.typography.bodySmall) }
            }
        }
        @Composable fun Components(modifier: Modifier = Modifier) {
            period?.components?.let { parts ->
                Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.getString(R.string.token_components), style = MaterialTheme.typography.titleMedium)
                    listOf(strings.getString(R.string.token_cache_read) to parts.cacheRead, strings.getString(R.string.token_output) to parts.output,
                        strings.getString(R.string.token_input_miss) to parts.inputMiss, strings.getString(R.string.token_unclassified) to parts.unclassified).forEach { (label, value) ->
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(label)
                            Text(value?.let { numbers.format(it) } ?: strings.getString(R.string.not_provided))
                        }
                    }
                }
            }
        }
        if (twoColumns) Row(Modifier.fillMaxWidth().testTag("usage-two-columns"), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Total(Modifier.weight(1f)); Components(Modifier.weight(1f))
        } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Total(); Components() }
    }
}
