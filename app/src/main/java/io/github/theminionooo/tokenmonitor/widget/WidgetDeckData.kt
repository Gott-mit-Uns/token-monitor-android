package io.github.theminionooo.tokenmonitor.widget

import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.domain.HistoryPoint
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.domain.snapshotDate
import io.github.theminionooo.tokenmonitor.domain.usageHistory
import io.github.theminionooo.tokenmonitor.ui.buildActivityHeatmap
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class WidgetDeckPage(val subtitle: Int) {
    Overview(R.string.widget_deck_overview),
    Limits(R.string.widget_deck_limits),
    Breakdown(R.string.widget_deck_breakdown),
    Activity(R.string.widget_deck_activity),
}

internal data class WidgetDeckUsageRow(
    val name: String,
    val tokens: Long,
    val costUsd: Double,
    val share: Double,
    val unpricedTokens: Long = 0,
)

internal data class WidgetDeckLimitRow(
    val provider: String,
    val title: String,
    val remainingPercent: Double,
    val reset: String,
)

/** One provider and its two tightest windows, tightest first; a Limits row. */
internal data class WidgetDeckLimitGroup(
    val provider: String,
    val windows: List<WidgetDeckLimitRow>,
)

internal data class WidgetDeckData(
    val snapshot: HubSnapshot?,
    val liveEnabled: Boolean,
    val status: String,
    val statusDescription: String,
    val emptyMessage: String,
    val date: LocalDate,
    val history: List<HistoryPoint>,
    val stats: List<Pair<String, String>>,
    val tools: List<WidgetDeckUsageRow>,
    val models: List<WidgetDeckUsageRow>,
    val limits: List<WidgetDeckLimitRow>,
    val limitGroups: List<WidgetDeckLimitGroup>,
    val week: List<HistoryPoint>,
    val activeDays: Int,
    val messagesToday: Long,
) {
    val hasData: Boolean get() = snapshot != null
}

internal fun prepareWidgetDeck(
    snapshot: HubSnapshot?,
    session: WidgetSession,
    now: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): WidgetDeckData {
    val liveEnabled = session.enabled && session.expiresAt > now
    val age = snapshot?.capturedAt?.takeIf { it > 0 }?.let { (now - it).coerceAtLeast(0) }
    val staleAfter = snapshot?.stats?.staleAfterMs ?: 5 * 60_000L
    val stale = age != null && age > staleAfter
    val status = when {
        session.note != null -> "OFFLINE"
        liveEnabled && session.connected -> "LIVE"
        liveEnabled -> "CONNECTING"
        session.refreshing -> "UPDATING"
        stale -> "STALE"
        snapshot == null -> "NO DATA"
        else -> "SAVED"
    }
    val clock = DateTimeFormatter.ofPattern("HH:mm", locale).withZone(zoneId)
    val saved = snapshot?.capturedAt?.takeIf { it > 0 }?.let { clock.format(Instant.ofEpochMilli(it)) }
    val statusDescription = buildString {
        append(status.lowercase(locale).replaceFirstChar { it.titlecase(locale) })
        saved?.let { append(". Snapshot saved at $it") }
        session.note?.let { append(". $it") }
    }
    val emptyMessage = when {
        liveEnabled -> "Connecting to your Hub…"
        session.refreshing -> "Refreshing your saved usage…"
        session.note != null -> "Hub offline. Open Token Monitor to check the connection."
        else -> "Open Token Monitor to connect or refresh your Hub."
    }
    val date = snapshot?.let { snapshotDate(it, zoneId) } ?: Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
    val history = snapshot?.let(::usageHistory).orEmpty()
    val weekStart = date.minusDays(6)
    val week = history.filter { point ->
        runCatching { LocalDate.parse(point.label.take(10)) }.getOrNull()?.let { !it.isBefore(weekStart) && !it.isAfter(date) } == true
    }
    val today = history.lastOrNull { it.label.take(10) == date.toString() }
    val period = snapshot?.today
    fun rows(tokens: Map<String, Long>, costs: Map<String, Double>, unpriced: Map<String, Long>, limit: Int): List<WidgetDeckUsageRow> {
        val total = tokens.values.sum().toDouble()
        if (total <= 0) return emptyList()
        return tokens.entries.sortedByDescending { it.value }.take(limit).map { (name, value) ->
            WidgetDeckUsageRow(name, value, costs[name] ?: 0.0, value / total, unpriced[name] ?: 0)
        }
    }
    val limitsByProvider = snapshot?.stats?.limits?.providers.orEmpty().map { account ->
        account.provider to account.windows.mapNotNull { window ->
            remainingPercent(window)?.let { remaining ->
                WidgetDeckLimitRow(
                    provider = account.provider,
                    title = io.github.theminionooo.tokenmonitor.ui.windowTitle(window, account.windows),
                    remainingPercent = remaining,
                    reset = io.github.theminionooo.tokenmonitor.ui.formatBoundary(window.resetsAt, window.boundaryKind, now),
                )
            }
        }.sortedBy { it.remainingPercent }
    }.filter { it.second.isNotEmpty() }
    val limits = limitsByProvider.flatMap { it.second }.sortedBy { it.remainingPercent }.take(4)
    val limitGroups = limitsByProvider
        .map { (provider, windows) -> WidgetDeckLimitGroup(provider, windows.take(2)) }
        .sortedBy { it.windows.first().remainingPercent }
        .distinctBy { it.provider }
        .take(2)
    return WidgetDeckData(
        snapshot = snapshot,
        liveEnabled = liveEnabled,
        status = status,
        statusDescription = statusDescription,
        emptyMessage = emptyMessage,
        date = date,
        history = history,
        stats = widgetStats(history, date).take(3),
        tools = period?.let { rows(it.clients, it.clientCosts, it.clientUnpricedTokens, 3) }.orEmpty(),
        models = period?.let { rows(it.models, it.modelCosts, it.modelUnpricedTokens, 4) }.orEmpty(),
        limits = limits,
        limitGroups = limitGroups,
        week = week,
        activeDays = buildActivityHeatmap(history, date).activeDays,
        messagesToday = today?.messages ?: 0,
    )
}
