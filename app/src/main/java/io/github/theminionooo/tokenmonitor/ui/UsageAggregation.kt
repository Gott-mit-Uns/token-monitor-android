package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.DeviceUsage
import io.github.theminionooo.tokenmonitor.domain.HistoryAttribution
import io.github.theminionooo.tokenmonitor.domain.HistoryPoint
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.domain.UsagePeriod
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

internal enum class DashboardPeriod(val label: String, val statsKey: String? = null) {
    Today("DAY", "today"),
    Month("MONTH", "month"),
    Week("WEEK"),
    Last7("7 DAYS"),
    Last30("30 DAYS"),
    AllTime("TOTAL", "allTime");

    fun usage(snapshot: HubSnapshot): UsagePeriod = when (this) {
        Today -> snapshot.today
        Month -> snapshot.month
        Week -> aggregateHistory(snapshot, startOfCurrentWeek())
        Last7 -> aggregateHistory(snapshot, LocalDate.now().minusDays(6))
        Last30 -> aggregateHistory(snapshot, LocalDate.now().minusDays(29))
        AllTime -> snapshot.allTime
    }

    fun usage(device: DeviceUsage): UsagePeriod = when (this) {
        Today -> device.periods["today"] ?: UsagePeriod()
        Month -> device.periods["month"] ?: UsagePeriod()
        Week -> aggregateDeviceHistory(device, startOfCurrentWeek())
        Last7 -> aggregateDeviceHistory(device, LocalDate.now().minusDays(6))
        Last30 -> aggregateDeviceHistory(device, LocalDate.now().minusDays(29))
        AllTime -> device.periods["allTime"] ?: UsagePeriod()
    }

    companion object {
        val rangeChoices = listOf(Month, Week, Last7, Last30)
    }
}

private fun aggregateDeviceHistory(device: DeviceUsage, startDate: LocalDate): UsagePeriod {
    val sourceDate = runCatching { java.time.Instant.parse(device.updatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }.getOrNull()
    val current = device.periods["today"]
    if (sourceDate == null || current == null || sourceDate.isBefore(startDate) || sourceDate.isAfter(LocalDate.now())) {
        return aggregateHistoryPoints(device.history.daily, startDate)
    }
    val retained = aggregateHistoryPoints(device.history.daily.filterNot { it.label.take(10) == sourceDate.toString() }, startDate)
    return retained + current
}

internal fun periodRangeLabel(period: DashboardPeriod): String = when (period) {
    DashboardPeriod.Month -> "This month"
    DashboardPeriod.Week -> "This week"
    DashboardPeriod.Last7 -> "Last 7 days"
    DashboardPeriod.Last30 -> "Last 30 days"
    else -> period.label
}

private fun startOfCurrentWeek(today: LocalDate = LocalDate.now()): LocalDate {
    val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    var cursor = today
    while (cursor.dayOfWeek != firstDay) cursor = cursor.minusDays(1)
    return cursor
}

internal fun aggregateHistory(snapshot: HubSnapshot, startDate: LocalDate): UsagePeriod {
    return aggregateHistoryPoints(io.github.theminionooo.tokenmonitor.domain.usageHistory(snapshot), startDate)
}

private fun aggregateHistoryPoints(points: List<HistoryPoint>, startDate: LocalDate): UsagePeriod {
    val endDate = LocalDate.now()
    val selected = points.filter { point ->
        val date = point.label.take(10).let { runCatching { LocalDate.parse(it) }.getOrNull() }
        date != null && !date.isBefore(startDate) && !date.isAfter(endDate)
    }
    val clientTotals = mutableMapOf<String, HistoryAttribution>()
    val modelTotals = mutableMapOf<String, HistoryAttribution>()
    selected.forEach { point ->
        point.perClient.forEach { (key, value) -> clientTotals.mergeAttribution(key, value) }
        point.perModel.forEach { (key, value) -> modelTotals.mergeAttribution(key, value) }
    }
    return UsagePeriod(
        totalTokens = selected.sumOf { it.tokens },
        costUsd = selected.sumOf { it.costUsd },
        unpricedTokens = selected.sumOf { it.unpricedTokens },
        clientUnpricedTokens = clientTotals.mapValues { it.value.unpricedTokens },
        modelUnpricedTokens = modelTotals.mapValues { it.value.unpricedTokens },
        clients = clientTotals.mapValues { it.value.tokens },
        clientCosts = clientTotals.mapValues { it.value.costUsd },
        models = modelTotals.mapValues { it.value.tokens },
        modelCosts = modelTotals.mapValues { it.value.costUsd },
        clientCacheReads = clientTotals.mapValues { it.value.cacheReadTokens },
        clientCacheWrites = clientTotals.mapValues { it.value.cacheWriteTokens },
        clientOutputs = clientTotals.mapValues { it.value.outputTokens },
        clientUnclassifiedTokens = clientTotals.mapValues { it.value.unclassifiedTokens },
        modelCacheReads = modelTotals.mapValues { it.value.cacheReadTokens },
        modelCacheWrites = modelTotals.mapValues { it.value.cacheWriteTokens },
        modelOutputs = modelTotals.mapValues { it.value.outputTokens },
        modelUnclassifiedTokens = modelTotals.mapValues { it.value.unclassifiedTokens },
    )
}

private fun MutableMap<String, HistoryAttribution>.mergeAttribution(key: String, value: HistoryAttribution) {
    val current = get(key) ?: HistoryAttribution()
    put(
        key,
        HistoryAttribution(
            tokens = current.tokens + value.tokens,
            costUsd = current.costUsd + value.costUsd,
            cacheReadTokens = current.cacheReadTokens + value.cacheReadTokens,
            cacheWriteTokens = current.cacheWriteTokens + value.cacheWriteTokens,
            outputTokens = current.outputTokens + value.outputTokens,
            unclassifiedTokens = current.unclassifiedTokens + value.unclassifiedTokens,
            unpricedTokens = current.unpricedTokens + value.unpricedTokens,
        ),
    )
}

private operator fun UsagePeriod.plus(other: UsagePeriod): UsagePeriod = UsagePeriod(
    throughputAvailable = throughputAvailable && other.throughputAvailable,
    timedTokens = timedTokens + other.timedTokens,
    timedOutputTokens = timedOutputTokens + other.timedOutputTokens,
    timedDurationMs = timedDurationMs + other.timedDurationMs,
    totalTokens = totalTokens + other.totalTokens,
    costUsd = costUsd + other.costUsd,
    unpricedTokens = unpricedTokens + other.unpricedTokens,
    clientUnpricedTokens = clientUnpricedTokens.plusCounts(other.clientUnpricedTokens),
    modelUnpricedTokens = modelUnpricedTokens.plusCounts(other.modelUnpricedTokens),
    clients = clients.plusCounts(other.clients),
    clientCosts = clientCosts.plusCosts(other.clientCosts),
    models = models.plusCounts(other.models),
    modelCosts = modelCosts.plusCosts(other.modelCosts),
    clientCacheReads = clientCacheReads.plusCounts(other.clientCacheReads),
    clientCacheWrites = clientCacheWrites.plusCounts(other.clientCacheWrites),
    clientOutputs = clientOutputs.plusCounts(other.clientOutputs),
    clientUnclassifiedTokens = clientUnclassifiedTokens.plusCounts(other.clientUnclassifiedTokens),
    modelCacheReads = modelCacheReads.plusCounts(other.modelCacheReads),
    modelCacheWrites = modelCacheWrites.plusCounts(other.modelCacheWrites),
    modelOutputs = modelOutputs.plusCounts(other.modelOutputs),
    modelUnclassifiedTokens = modelUnclassifiedTokens.plusCounts(other.modelUnclassifiedTokens),
)

private fun Map<String, Long>.plusCounts(other: Map<String, Long>): Map<String, Long> =
    (keys + other.keys).associateWith { key -> (this[key] ?: 0L) + (other[key] ?: 0L) }

private fun Map<String, Double>.plusCosts(other: Map<String, Double>): Map<String, Double> =
    (keys + other.keys).associateWith { key -> (this[key] ?: 0.0) + (other[key] ?: 0.0) }

/** Consecutive active days ending today or yesterday, the number the Trends overview shows as the streak. */
internal fun currentStreak(history: List<HistoryPoint>, today: LocalDate = LocalDate.now()): Int {
    val activeDates = history.filter { it.tokens > 0 }.mapNotNull { point -> runCatching { LocalDate.parse(point.label.take(10)) }.getOrNull() }.toSet()
    var cursor = if (today in activeDates) today else today.minusDays(1)
    var streak = 0
    while (cursor in activeDates) {
        streak += 1
        cursor = cursor.minusDays(1)
    }
    return streak
}
