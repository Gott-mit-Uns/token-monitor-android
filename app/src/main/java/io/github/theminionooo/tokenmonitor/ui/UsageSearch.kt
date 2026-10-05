package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.ProjectUsage
import io.github.theminionooo.tokenmonitor.domain.SessionUsage
import java.util.Locale

internal fun SessionUsage.matchesSearch(query: String, showTitles: Boolean): Boolean {
    val text = query.trim()
    if (text.isEmpty()) return true
    val fields = listOf(id, projectLabel, client, client.displayName()) + modelNames +
        if (showTitles) listOf(title) else emptyList()
    return fields.any { it.contains(text, ignoreCase = true) }
}

internal fun ProjectUsage.matchesSearch(query: String): Boolean {
    val text = query.trim()
    return text.isEmpty() || (listOf(id, label) + clients.keys + clients.keys.map { it.displayName() })
        .any { it.contains(text, ignoreCase = true) }
}

internal fun usageListEmptyMessage(sessions: Boolean, period: DashboardPeriod, searching: Boolean): String {
    val kind = if (sessions) "sessions" else "projects"
    return when {
        period.statsKey == null -> "The Hub reports $kind for Day, Month, and Total only."
        searching -> "No $kind match this search."
        sessions -> "No session detail is available for ${period.label.lowercase(Locale.US)}."
        else -> "No project attribution is available for ${period.label.lowercase(Locale.US)}."
    }
}
