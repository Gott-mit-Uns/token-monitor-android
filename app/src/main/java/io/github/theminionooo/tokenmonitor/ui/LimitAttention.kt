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

@Composable
internal fun LimitAttentionPanel(snapshot: HubSnapshot) {
    val notes = limitAttention(snapshot, LocalNow.current)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Text(tr("QUOTA AT A GLANCE"), color = Ink, style = MaterialTheme.typography.labelMedium)
        notes.take(4).forEach { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
        if (notes.size > 4) Text(tr("${notes.size - 4} more quota notices; see the accounts below."), color = Muted, style = MaterialTheme.typography.labelSmall)
        if (notes.isEmpty()) Text(tr(if (snapshot.stats.limits.providers.none { it.windows.isNotEmpty() }) "No quota windows were reported." else "No low quota or imminent reset in the reported windows."), color = Muted, style = MaterialTheme.typography.bodySmall)
        Text(tr("Quota is provider-reported. Usage costs are estimates; subscriptions are recorded payments."), color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}
