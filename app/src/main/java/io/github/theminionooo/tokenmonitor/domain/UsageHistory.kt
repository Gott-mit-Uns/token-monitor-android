package io.github.theminionooo.tokenmonitor.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A preview may omit attribution already fetched from the history endpoint. */
fun mergeUsageHistory(saved: List<HistoryPoint>, incoming: List<HistoryPoint>): List<HistoryPoint> {
    val points = saved.associateBy { it.label }.toMutableMap()
    incoming.forEach { next ->
        val old = points[next.label]
        points[next.label] = if (old != null && old.tokens == next.tokens && old.costUsd == next.costUsd) {
            val keepComponents = old.tokenComponentsAvailable && !next.tokenComponentsAvailable
            next.copy(
                cacheReadTokens = if (keepComponents) old.cacheReadTokens else next.cacheReadTokens,
                cacheWriteTokens = if (keepComponents) old.cacheWriteTokens else next.cacheWriteTokens,
                outputTokens = if (keepComponents) old.outputTokens else next.outputTokens,
                unclassifiedTokens = if (keepComponents) old.unclassifiedTokens else next.unclassifiedTokens,
                tokenComponentsAvailable = old.tokenComponentsAvailable || next.tokenComponentsAvailable,
                perClient = next.perClient.ifEmpty { old.perClient },
                perModel = next.perModel.ifEmpty { old.perModel },
            )
        } else next
    }
    return points.values.sortedBy { it.label }
}

/** Calendar filtering does not turn absent observations into zero usage. */
fun historyInRange(points: List<HistoryPoint>, start: LocalDate, end: LocalDate): List<HistoryPoint> =
    points.mapNotNull { point ->
        val date = runCatching { LocalDate.parse(point.label.take(10)) }.getOrNull()
        if (date != null && !date.isBefore(start) && !date.isAfter(end)) date to point else null
    }.toMap().toSortedMap().values.toList()

fun snapshotDate(snapshot: HubSnapshot, zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
    runCatching { Instant.parse(snapshot.stats.updatedAt).atZone(zone).toLocalDate() }.getOrNull()
        ?: snapshot.capturedAt.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

/** Share the same current-day observation across Home, Trends, comparisons, and widgets. */
fun usageHistory(snapshot: HubSnapshot): List<HistoryPoint> {
    val history = mergeUsageHistory(snapshot.history.daily, snapshot.stats.historyPreview.daily).associateBy { it.label.take(10) }.toMutableMap()
    val day = snapshotDate(snapshot) ?: return history.values.sortedBy { it.label }
    val period = snapshot.stats.periods["today"] ?: return history.values.sortedBy { it.label }
    val existing = history[day.toString()]
    fun attribution(tokens: Map<String, Long>, costs: Map<String, Double>, reads: Map<String, Long>, outputs: Map<String, Long>, writes: Map<String, Long>, unknown: Map<String, Long>) =
        tokens.mapValues { (key, value) -> HistoryAttribution(value, costs[key] ?: 0.0, reads[key] ?: 0, writes[key] ?: 0, outputs[key] ?: 0, unknown[key] ?: 0) }
    history[day.toString()] = HistoryPoint(
        label = day.toString(), tokens = period.totalTokens, costUsd = period.costUsd,
        messages = existing?.messages ?: 0, activeTimeMs = existing?.activeTimeMs ?: 0,
        perClient = attribution(period.clients, period.clientCosts, period.clientCacheReads, period.clientOutputs, period.clientCacheWrites, period.clientUnclassifiedTokens),
        perModel = attribution(period.models, period.modelCosts, period.modelCacheReads, period.modelOutputs, period.modelCacheWrites, period.modelUnclassifiedTokens),
    )
    return history.values.sortedBy { it.label }
}

data class UsageComparison(val current: List<HistoryPoint>, val previous: List<HistoryPoint>, val days: Int) {
    val complete: Boolean get() = current.size == days && previous.size == days
    val tokenDelta: Long get() = current.sumOf { it.tokens } - previous.sumOf { it.tokens }
    val percentChange: Double? get() = previous.sumOf { it.tokens }.takeIf { complete && it > 0 }?.let { tokenDelta * 100.0 / it }
    fun drivers(byModel: Boolean): List<Pair<String, Long>> {
        if (!complete || (current + previous).any { point ->
            (if (byModel) point.perModel else point.perClient).values.sumOf { it.tokens } != point.tokens
        }) return emptyList()
        fun totals(points: List<HistoryPoint>) = points.flatMap { (if (byModel) it.perModel else it.perClient).entries }
            .groupBy { it.key }.mapValues { (_, entries) -> entries.sumOf { it.value.tokens } }
        val recent = totals(current)
        val earlier = totals(previous)
        return (recent.keys + earlier.keys).map { it to ((recent[it] ?: 0) - (earlier[it] ?: 0)) }
            .sortedByDescending { kotlin.math.abs(it.second.toDouble()) }
    }
}

/** Equal, completed calendar windows. Today is excluded to avoid comparing a partial day. */
fun compareUsage(points: List<HistoryPoint>, today: LocalDate, days: Int = 7): UsageComparison {
    require(days > 0)
    return UsageComparison(
        historyInRange(points, today.minusDays(days.toLong()), today.minusDays(1)),
        historyInRange(points, today.minusDays(days * 2L), today.minusDays(days + 1L)), days,
    )
}
