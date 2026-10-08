package io.github.theminionooo.tokenmonitor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/** Identity and full statistics share a row only while both fit. */
@Composable
internal fun AlignedUsageIdentity(tokens: String, cost: String, identity: @Composable RowScope.() -> Unit) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodySmall
    val metricWidth = with(density) { maxOf(measurer.measure(tokens, style).size.width, measurer.measure(cost, style).size.width).toDp() + 4.dp }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stack = density.fontScale >= 1.5f || metricWidth + LocalContentIconSize.current + 8.dp + 120.dp > maxWidth
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically, content = identity) }
                if (!stack) { Spacer(Modifier.width(8.dp)); CompleteMetrics(tokens, cost, Modifier.width(metricWidth)) }
            }
            if (stack) CompleteMetrics(tokens, cost, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CompleteMetrics(tokens: String, cost: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        CompleteValue(tokens, true)
        if (cost.isNotBlank()) CompleteValue(cost, false)
    }
}

@Composable
internal fun CompleteValue(value: String, prominent: Boolean = true, styleOverride: androidx.compose.ui.text.TextStyle? = null) {
    val style = styleOverride ?: if (prominent) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints {
        val available = with(density) { maxWidth.toPx() }
        val measured = measurer.measure(value, style).size.width.toFloat()
        val fitting = if (measured > available && available > 0) style.copy(fontSize = style.fontSize * (available / measured)) else style
        Text(value, color = if (prominent) Ink else Muted, style = fitting, maxLines = 1, softWrap = false)
    }
}
