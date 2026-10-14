package dev.bing.tokenmonitor

import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

internal data class WidgetTrendPoint(val date: String, val tokens: Long?)
internal data class WidgetBreakdown(val id: String, val label: String, val tokens: Long?, val share: Double?)

internal fun widgetSafeLabel(value: String): String? = value.trim().takeIf { name ->
    name.isNotEmpty() && name.length <= 80 && name.none(Char::isISOControl) &&
        !Regex("^(?:https?://|file:|/|~[/\\\\]|\\\\\\\\|[A-Za-z]:[/\\\\])", RegexOption.IGNORE_CASE).containsMatchIn(name) &&
        !Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+").matches(name)
}

internal fun widgetBreakdown(period: Period?, mode: String, names: ModelNames? = null, observed: Collection<String> = emptyList()): List<WidgetBreakdown> {
    val raw = if (mode == "tools") period?.tools.orEmpty() else period?.models.orEmpty()
    // Reuse cached Hub display aliases before the Mac Widget's display-name normalization.
    // ModelNames.project already counts each source once; this second grouping is idempotent.
    val projected = if (mode == "models" && names != null) names.project(raw, observed.ifEmpty { raw.keys })
        else raw.mapValues { BigInteger.valueOf(it.value) }
    val safe = projected.mapNotNull { (name, count) -> widgetSafeLabel(if (mode == "tools") toolLabel(name) else name)?.let { Triple(name, it, count) } }
    val collator = java.text.Collator.getInstance(Locale.US)
    val counts = if (mode != "models") safe else safe.groupBy {
        java.text.Normalizer.normalize(it.second, java.text.Normalizer.Form.NFKC).lowercase(Locale.US)
    }.values.map { members ->
        val representative = members.minWith { left, right -> collator.compare(left.second, right.second) }
        Triple(representative.first, representative.second, members.fold(BigInteger.ZERO) { total, member -> total + member.third })
    }
    val sum = counts.fold(BigInteger.ZERO) { total, row -> total + row.third }
    val overflow = counts.any { it.third > BigInteger.valueOf(Long.MAX_VALUE) }
    return counts.filter { it.third.signum() > 0 }.sortedWith { left, right ->
        right.third.compareTo(left.third).takeIf { it != 0 } ?: collator.compare(left.second, right.second)
    }.take(10).map { (id, label, exact) ->
        val tokens = exact.takeIf { it <= BigInteger.valueOf(Long.MAX_VALUE) }?.toLong()
        WidgetBreakdown(id, label, tokens, if (!overflow && sum.signum() > 0) exact.toDouble() / sum.toDouble() * 100 else null)
    }
}

/** Same local calendar ranges and max merge as the Mac, with unknown Hub values kept unknown. */
internal fun widgetTrend(snapshot: Snapshot, history: HubHistory?, period: String, now: Instant, zone: ZoneId = ZoneId.systemDefault()): List<WidgetTrendPoint> {
    val today = now.atZone(zone).toLocalDate()
    val daily = history?.daily.orEmpty().associateBy { it.date }
    fun maximum(left: Long?, right: Long?): Long? = when { left == null -> right; right == null -> left; else -> maxOf(left, right) }
    if (period != "allTime") {
        val start = if (period == "month") today.withDayOfMonth(1) else today.minusDays(6)
        if (history == null && snapshot.periods["today"]?.tokens == null) return emptyList()
        return generateSequence(start) { it.plusDays(1).takeIf { next -> next <= today } }.map { day ->
            WidgetTrendPoint(day.toString(), if (day == today) maximum(daily[day.toString()]?.tokens, snapshot.periods["today"]?.tokens) else daily[day.toString()]?.tokens)
        }.toList()
    }
    val byMonth = linkedMapOf<String, Long?>()
    daily.values.groupBy { it.date.take(7) }.forEach { (month, rows) ->
        val sum = if (rows.any { it.tokens == null }) null else rows.fold(BigInteger.ZERO) { total, row -> total + BigInteger.valueOf(row.tokens!!) }
        byMonth[month] = sum?.takeIf { it <= BigInteger.valueOf(Long.MAX_VALUE) }?.toLong()
    }
    history?.monthly.orEmpty().forEach { row -> byMonth[row.date] = maximum(byMonth[row.date], row.tokens) }
    val current = today.toString().take(7)
    snapshot.periods["month"]?.tokens?.let { byMonth[current] = maximum(byMonth[current], it) }
    return byMonth.toSortedMap().map { WidgetTrendPoint(it.key, it.value) }
}

internal fun widgetCost(usage: WidgetUsage, preferences: DisplayPreferences): String {
    if (usage.cost == null || (usage.unpriced && usage.cost.toDoubleOrNull()?.let { it <= 0 } == true)) return "—"
    val currency = UsageCurrency(preferences.currency, preferences.currencyRates)
    val converted = currency.converted(usage.cost) ?: return "—"
    val symbol = when (currency.code) { "CNY" -> "¥"; "TWD" -> "NT$"; "HKD" -> "HK$"; else -> "$" }
    return symbol + String.format(Locale.US, "%.2f", converted) + if (usage.unpriced) " + ?" else ""
}

internal fun widgetTrendChange(snapshot: Snapshot, points: List<WidgetTrendPoint>, period: String): String {
    val change = if (period == "allTime") {
        val total = snapshot.periods["allTime"]?.tokens ?: return "—"
        val month = snapshot.periods["month"]?.tokens ?: return "—"
        val previous = total - month.coerceIn(0, total)
        if (previous <= 0) return "—"
        month.toDouble() / previous * 100
    } else {
        val first = points.firstOrNull { (it.tokens ?: 0) > 0 }?.tokens ?: return "—"
        val last = points.lastOrNull()?.tokens ?: return "—"
        (last.toDouble() - first) / first * 100
    }
    if (!change.isFinite()) return "—"
    val rounded = java.math.BigDecimal(change).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    return when { rounded > 0 -> "+$rounded%"; rounded < 0 -> "−${-rounded}%"; else -> "0%" }
}

internal fun widgetStale(usage: WidgetUsage, now: Instant): Boolean {
    val source = usage.updated?.let { runCatching { Instant.parse(it) }.getOrNull() }
    return usage.sourceStale || usage.savedAt <= 0 || usage.savedAt > now.toEpochMilli() || now.toEpochMilli() - usage.savedAt > 20 * 60 * 1000 ||
        source == null || source > now || now.toEpochMilli() - source.toEpochMilli() > 20 * 60 * 1000
}
