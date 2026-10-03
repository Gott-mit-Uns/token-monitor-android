package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.localization.tr
import io.github.theminionooo.tokenmonitor.localization.localizedText
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import java.time.Instant

internal fun limitAttention(snapshot: HubSnapshot, now: Long): List<String> = snapshot.stats.limits.providers.flatMap { account ->
    val updated = runCatching { Instant.parse(account.updatedAt.ifBlank { snapshot.stats.limits.updatedAt }).toEpochMilli() }.getOrNull()
    val staleAfter = snapshot.stats.staleAfterMs?.takeIf { it > 0 } ?: 600_000L
    val provider = account.provider.providerLabel()
    if (updated == null || snapshot.fromCache || now - updated > staleAfter) {
        listOf("$provider: ${if (updated == null) localizedText("freshness unavailable") else localizedText("saved quota; refresh to confirm")}")
    } else account.windows.mapNotNull { window ->
        val remaining = window.remainingPercent ?: window.usedPercent?.let { 100.0 - it }
        val reset = runCatching { Instant.parse(window.resetsAt).toEpochMilli() }.getOrNull()
        val label = window.label.ifBlank { window.kind }
        when {
            reset != null && reset <= now -> "$provider $label: ${formatBoundary(window.resetsAt, window.boundaryKind, now)}; ${localizedText("awaiting updated quota")}"
            remaining != null && remaining <= 20 -> "$provider $label: ${localizedText("${formatPercent(remaining.coerceAtLeast(0.0))} left")}"
            reset != null && reset - now <= 3_600_000L -> "$provider $label: ${formatBoundary(window.resetsAt, window.boundaryKind, now)}"
            else -> null
        }
    }
}.distinct()

@Composable
internal fun LimitAttentionPanel(snapshot: HubSnapshot) {
    val notes = limitAttention(snapshot, LocalNow.current)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Text(tr("QUOTA AT A GLANCE"), color = Ink, style = MaterialTheme.typography.labelMedium)
        notes.take(4).forEach { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
        if (notes.isEmpty()) Text(tr(if (snapshot.stats.limits.providers.none { it.windows.isNotEmpty() }) "No quota windows were reported." else "No low quota or imminent reset in the reported windows."), color = Muted, style = MaterialTheme.typography.bodySmall)
        Text(tr("Quota is provider-reported. Usage costs are estimates; subscriptions are recorded payments."), color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}
