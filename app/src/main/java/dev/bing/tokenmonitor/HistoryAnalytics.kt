package dev.bing.tokenmonitor

import java.math.BigInteger
import java.time.LocalDate

data class HistoryCandle(val start: String, val end: String, val recorded: Int, val calendarDays: Long,
    val open: Long?, val close: Long?, val high: Long, val low: Long)

object HistoryAnalytics {
    const val UNATTRIBUTED = "__unattributed__"

    /** Source desktop convention: calendar buckets anchored backwards from the latest day. */
    fun candles(rows: List<HistoryRow>, bucketDays: Int): List<HistoryCandle> {
        require(bucketDays in 1..366)
        val unique = rows.groupBy { it.date }.filterValues { it.size == 1 }.mapValues { it.value.single() }
        val end = unique.keys.maxOrNull()?.let(LocalDate::parse) ?: return emptyList()
        val start = unique.keys.minOrNull()?.let(LocalDate::parse) ?: return emptyList()
        return unique.values.filter { it.tokens != null }.groupBy { (end.toEpochDay() - LocalDate.parse(it.date).toEpochDay()) / bucketDays }
            .toSortedMap(compareByDescending { it }).map { (index, group) ->
                val bucketEnd = end.minusDays(index * bucketDays)
                val bucketStart = maxOf(start, bucketEnd.minusDays(bucketDays.toLong() - 1))
                HistoryCandle(bucketStart.toString(), bucketEnd.toString(), group.size, bucketEnd.toEpochDay() - bucketStart.toEpochDay() + 1,
                    unique[bucketStart.toString()]?.tokens, unique[bucketEnd.toString()]?.tokens, group.maxOf { it.tokens!! }, group.minOf { it.tokens!! })
            }
    }

    /** Missing attribution stays visible as an unassigned portion; it is never invented per tool. */
    fun stacks(row: HistoryRow, byModel: Boolean): Map<String, BigInteger> {
        val raw = if (byModel) row.perModel else row.perClient
        val folded = linkedMapOf<String, BigInteger>()
        raw.forEach { (name, value) -> value.tokens?.let { count ->
            val key = if (!byModel && name == "antigravity-cli") "antigravity" else name
            folded[key] = (folded[key] ?: BigInteger.ZERO) + BigInteger.valueOf(count)
        } }
        val total = row.tokens?.let(BigInteger::valueOf)
        val assigned = folded.values.fold(BigInteger.ZERO, BigInteger::add)
        if (total != null && assigned > total) return mapOf(UNATTRIBUTED to total)
        if (total != null && total > assigned) folded[UNATTRIBUTED] = total - assigned
        return folded
    }
}
