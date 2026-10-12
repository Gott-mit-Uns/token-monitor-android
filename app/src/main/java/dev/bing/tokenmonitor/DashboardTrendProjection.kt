package dev.bing.tokenmonitor

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.Collator
import java.time.LocalDate
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

internal enum class DashboardTrendMode { BARS, KLINE }
internal data class DashboardTrendPlot(val x: Double, val y: Double, val width: Double, val height: Double)
internal data class DashboardTrendAxisTick(val value: BigDecimal, val lineY: Double, val labelX: Double, val labelY: Double)
internal data class DashboardTrendAxisLabel(val label: String, val x: Double, val y: Double)
internal data class DashboardTrendSegment(
    val key: String, val value: BigInteger, val known: Boolean,
    val x: Double, val y: Double, val width: Double, val height: Double
)
internal data class DashboardTrendBar(
    val label: String, val index: Int, val x: Double, val width: Double,
    val total: BigInteger, val segments: List<DashboardTrendSegment>,
    val attributionComplete: Boolean, val duplicate: Boolean
)
internal data class DashboardTrendLegend(val key: String, val value: BigInteger, val percent: BigDecimal)
internal data class DashboardTrendCandle(
    val key: String, val endKey: String, val days: Int, val knownDays: Int,
    val open: BigInteger?, val high: BigInteger?, val low: BigInteger?, val close: BigInteger?,
    val up: Boolean?, val colorArgb: Long?,
    val x: Double, val width: Double, val wickX: Double,
    val yHigh: Double?, val yLow: Double?, val bodyY: Double?, val bodyHeight: Double?,
    val complete: Boolean
)
internal data class DashboardTrendIssues(
    val duplicateDates: Set<String> = emptySet(),
    val missingTokenDates: Set<String> = emptySet(),
    val incompleteAttributionDates: Set<String> = emptySet(),
    val invalidDates: Set<String> = emptySet()
) {
    val any: Boolean get() = duplicateDates.isNotEmpty() || missingTokenDates.isNotEmpty() ||
        incompleteAttributionDates.isNotEmpty() || invalidDates.isNotEmpty()
}
internal data class DashboardTrendModel(
    val range: String, val byModel: Boolean, val mode: DashboardTrendMode,
    val width: Double, val height: Double, val plot: DashboardTrendPlot,
    val daily: List<HistoryRow>, val keys: List<String>, val keyTotals: Map<String, BigInteger>,
    val bars: List<DashboardTrendBar>, val candles: List<DashboardTrendCandle>,
    val legend: List<DashboardTrendLegend>, val maxValue: BigInteger, val bucketDays: Int,
    val axisTicks: List<DashboardTrendAxisTick>, val axisLabels: List<DashboardTrendAxisLabel>,
    val issues: DashboardTrendIssues
) {
    val hasUnknownData: Boolean get() = issues.any
}

/** Independent Dashboard rules; do not reuse this projection for the main Trends spark or record view. */
internal object DashboardTrendProjection {
    val ranges = listOf("7", "30", "90", "365", "all")
    const val CANDLE_UP_ARGB = 0xFF4EC77FL
    const val CANDLE_DOWN_ARGB = 0xFFF06A7BL
    private val zero = BigInteger.ZERO
    private val one = BigInteger.ONE
    private data class Folded(val values: Map<String, BigInteger>, val unknownKeys: Set<String>, val complete: Boolean)

    /** Coordinates are the source model's units, before SVG's final two-decimal rounding.
     * [sourceLocale] makes the original host-default localeCompare collation explicit on Android.
     * Counts remain exact; only presentation coordinates use floating point.
     */
    fun project(
        history: HubHistory, range: String = "30", byModel: Boolean = false,
        mode: DashboardTrendMode = DashboardTrendMode.BARS,
        width: Double = 800.0, height: Double = 360.0, sourceLocale: String = "en"
    ): DashboardTrendModel {
        require(range in ranges)
        val w = dimension(width, 320.0, 800.0)
        val h = dimension(height, 200.0, 360.0)
        val plot = DashboardTrendPlot(52.0, 10.0, w - 66.0, h - 34.0)
        val invalid = history.daily.filter { date(it.date) == null }.mapTo(linkedSetOf()) { it.date }
        val grouped = history.daily.filter { date(it.date) != null }.groupBy { it.date }
        // A duplicated date is ambiguous. Collapse it into an explicit unknown record rather than double count.
        val duplicates = grouped.filterValues { it.size != 1 }.keys
        val ascending = grouped.map { (key, rows) -> if (rows.size == 1) rows.single() else HistoryRow(key, null, null) }
            .sortedBy { it.date }
        val daily = if (range == "all") ascending else ascending.takeLast(range.toInt())
        val selectedDuplicates = daily.mapNotNullTo(linkedSetOf()) { it.date.takeIf(duplicates::contains) }
        val missing = daily.filter { count(it.tokens) == null }.mapTo(linkedSetOf()) { it.date }
        if (daily.isEmpty()) return DashboardTrendModel(range, byModel, mode, w, h, plot, daily,
            emptyList(), emptyMap(), emptyList(), emptyList(), emptyList(), one, 0, emptyList(), emptyList(),
            DashboardTrendIssues(invalidDates = invalid))
        return if (mode == DashboardTrendMode.BARS) bars(daily, range, byModel, w, h, plot, sourceLocale,
            DashboardTrendIssues(selectedDuplicates, missing, invalidDates = invalid))
        else candles(daily, range, byModel, w, h, plot,
            DashboardTrendIssues(selectedDuplicates, missing, invalidDates = invalid))
    }

    private fun bars(
        daily: List<HistoryRow>, range: String, byModel: Boolean, w: Double, h: Double,
        plot: DashboardTrendPlot, sourceLocale: String, issues: DashboardTrendIssues
    ): DashboardTrendModel {
        val folded = daily.map { fold(it, byModel) }
        val totals = linkedMapOf<String, BigInteger>()
        folded.forEach { row -> row.values.forEach { (key, value) -> totals[key] = (totals[key] ?: zero) + value } }
        val locale = Locale.forLanguageTag(sourceLocale).takeIf { it.language.isNotEmpty() } ?: Locale.ENGLISH
        val collator = Collator.getInstance(locale)
        val keys = totals.keys.sortedWith { a, b ->
            val order = totals.getValue(b).compareTo(totals.getValue(a))
            if (order != 0) order else collator.compare(a, b)
        }
        val rowTotals = folded.map { it.values.values.fold(zero, BigInteger::add) }
        val maximum = maxOf(one, rowTotals.maxOrNull() ?: zero)
        val slot = plot.width / daily.size
        val barWidth = slot * .7
        val bars = daily.mapIndexed { index, row ->
            val x = plot.x + index * slot + (slot - barWidth) / 2
            var cumulative = 0.0
            val segments = keys.mapNotNull { key -> folded[index].values[key]?.let { value ->
                val height = plot.height * ratio(value, maximum)
                cumulative += height
                DashboardTrendSegment(key, value, key !in folded[index].unknownKeys,
                    x, plot.y + plot.height - cumulative, barWidth, height)
            } }
            DashboardTrendBar(row.date, index, x, barWidth, rowTotals[index], segments,
                folded[index].complete, row.date in issues.duplicateDates)
        }
        val grand = totals.values.fold(zero, BigInteger::add)
        val legend = keys.filter { totals.getValue(it).signum() > 0 }.map { key ->
            val value = totals.getValue(key)
            DashboardTrendLegend(key, value, BigDecimal(value).multiply(BigDecimal(100))
                .divide(BigDecimal(grand), 1, RoundingMode.HALF_UP))
        }
        val labels = axisLabels(bars.map { it.label to (it.x + it.width / 2) }, h)
        return DashboardTrendModel(range, byModel, DashboardTrendMode.BARS, w, h, plot, daily, keys, totals,
            bars, emptyList(), legend, maximum, 0, ticks(plot, maximum), labels,
            issues.copy(incompleteAttributionDates = daily.indices.filter { !folded[it].complete }
                .mapTo(linkedSetOf()) { daily[it].date }))
    }

    private fun candles(
        daily: List<HistoryRow>, range: String, byModel: Boolean, w: Double, h: Double,
        plot: DashboardTrendPlot, issues: DashboardTrendIssues
    ): DashboardTrendModel {
        val first = LocalDate.parse(daily.first().date)
        val last = LocalDate.parse(daily.last().date)
        val span = last.toEpochDay() - first.toEpochDay() + 1
        val target = max(8, floor(plot.width / 24 + .5).toInt())
        val bucketDays = if (span <= 10) 2 else max(3, floor(span.toDouble() / target + .5).toInt())
        val groups = daily.groupBy { (last.toEpochDay() - LocalDate.parse(it.date).toEpochDay()) / bucketDays }
            .toSortedMap(compareByDescending { it })
        val maximum = maxOf(one, daily.mapNotNull { count(it.tokens) }.maxOrNull() ?: zero)
        val slot = plot.width / groups.size
        val bodyWidth = slot * .6
        fun y(value: BigInteger) = plot.y + plot.height - plot.height * ratio(value, maximum)
        val candles = groups.values.mapIndexed { index, rows ->
            val values = rows.mapNotNull { count(it.tokens) }
            val open = count(rows.first().tokens)
            val close = count(rows.last().tokens)
            val high = values.maxOrNull()
            val low = values.minOrNull()
            val up = if (open != null && close != null) close >= open else null
            val x = plot.x + index * slot + (slot - bodyWidth) / 2
            val bodyY = if (open != null && close != null) y(maxOf(open, close)) else null
            val bodyHeight = if (bodyY != null && open != null && close != null) max(1.0, y(minOf(open, close)) - bodyY) else null
            DashboardTrendCandle(rows.first().date, rows.last().date, rows.size, values.size,
                open, high, low, close, up, when (up) { true -> CANDLE_UP_ARGB; false -> CANDLE_DOWN_ARGB; null -> null },
                x, bodyWidth, x + bodyWidth / 2, high?.let(::y), low?.let(::y), bodyY, bodyHeight,
                values.size == rows.size)
        }
        return DashboardTrendModel(range, byModel, DashboardTrendMode.KLINE, w, h, plot, daily,
            emptyList(), emptyMap(), emptyList(), candles, emptyList(), maximum, bucketDays,
            ticks(plot, maximum), axisLabels(candles.map { it.key to it.wickX }, h), issues)
    }

    private fun fold(row: HistoryRow, byModel: Boolean): Folded {
        val raw = if (byModel) row.perModel else row.perClient
        val values = linkedMapOf<String, BigInteger>()
        val unknown = linkedSetOf<String>()
        raw.forEach { (name, value) ->
            val key = if (!byModel && name == "antigravity-cli") "antigravity" else name
            val tokens = value.presentationTokens()
            values[key] = (values[key] ?: zero) + (tokens ?: zero)
            if (tokens == null) unknown += key
        }
        // Empty attribution and a known zero total is a known zero bar; an absent positive/unknown attribution is incomplete.
        return Folded(values, unknown, unknown.isEmpty() && (raw.isNotEmpty() || row.tokens == 0L))
    }

    private fun ticks(plot: DashboardTrendPlot, maximum: BigInteger) = (0..4).map { index ->
        val y = plot.y + plot.height - plot.height * index / 4
        DashboardTrendAxisTick(BigDecimal(maximum).multiply(BigDecimal(index)).divide(BigDecimal(4)),
            svgRound(y), svgRound(plot.x - 8), svgRound(y + 3))
    }

    private fun axisLabels(points: List<Pair<String, Double>>, height: Double): List<DashboardTrendAxisLabel> {
        val every = max(1, ceil(points.size / 9.0).toInt())
        return points.mapIndexedNotNull { index, (key, x) ->
            if (index % every == 0) DashboardTrendAxisLabel(shortDate(key), svgRound(x), svgRound(height - 4)) else null
        }
    }

    fun shortDate(key: String): String = date(key)?.let { "${it.monthValue}/${it.dayOfMonth}" } ?: key
    fun svgRound(value: Double): Double = floor(value * 100 + .5) / 100
    private fun date(key: String): LocalDate? = runCatching { LocalDate.parse(key) }.getOrNull()
    private fun count(value: Long?): BigInteger? = value?.takeIf { it >= 0 }?.let(BigInteger::valueOf)
    // SVG geometry uses Number division in the source. Keep exact BigInteger
    // counters/legend values; normalizing pixels does not need decimal long division.
    private fun ratio(value: BigInteger, maximum: BigInteger) = value.toDouble() / maximum.toDouble()
    private fun dimension(value: Double, minimum: Double, fallback: Double) = max(minimum,
        if (!value.isFinite() || value == 0.0) fallback else value)
}
