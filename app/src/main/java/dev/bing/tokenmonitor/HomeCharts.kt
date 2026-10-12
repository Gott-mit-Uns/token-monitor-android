package dev.bing.tokenmonitor

import java.math.BigDecimal
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

data class HomeHeatCell(val date: String, val column: Int, val weekday: Int, val value: HistoryRow?, val tokenLevel: Int, val costLevel: Int)
data class HomeMonthLabel(val month: String, val column: Int)
data class HomeLinePoint(val x: Double, val y: Double)
data class HomeLineCommand(val point: HomeLinePoint, val control1: HomeLinePoint? = null, val control2: HomeLinePoint? = null)
data class HomeChartProjection(val history: HubHistory, val today: LocalDate, val cells: List<HomeHeatCell>,
    val trend: List<HistoryRow>, val peakTokens: Long?, val activeDays: Long) {
    val columns: Int get() = cells.lastOrNull()?.column?.plus(1) ?: 0
    val months: List<HomeMonthLabel> get() = cells.filter { it.date.endsWith("-01") }.map { HomeMonthLabel(it.date.take(7), it.column) }
}

/** Desktop homeOverview.patchDailyToday/usageCharts; a presentation copy, never cached. */
object HomeCharts {
    fun pickHistory(full: HubHistory?, preview: HubHistory?): HubHistory? = full?.takeIf { it.daily.isNotEmpty() } ?: preview ?: full

    fun project(history: HubHistory, todayPeriod: Period?, today: LocalDate, activeDaysWindow: String = "all", previewActiveDays: Long? = history.summary?.activeDays): HomeChartProjection {
        // The desktop renderer shows its no-history state before patching an empty series.
        if (history.daily.isEmpty()) return HomeChartProjection(history, today, emptyList(), emptyList(), null, 0)
        val daily = history.daily.sortedBy { it.date }.toMutableList()
        if (todayPeriod != null) {
            val index = daily.indexOfFirst { it.date == today.toString() }
            if (index < 0) daily.add(HistoryRow(today.toString(), todayPeriod.tokens, todayPeriod.cost, todayPeriod.unpriced))
            else daily[index] = daily[index].copy(tokens = todayPeriod.tokens, cost = todayPeriod.cost, unpricedTokens = todayPeriod.unpriced)
        }
        val projected = history.copy(daily = daily)
        val maxTokens = daily.mapNotNull { it.tokens }.maxOrNull()?.coerceAtLeast(0)
        val maxCost = daily.mapNotNull { decimal(it.cost) }.maxOrNull() ?: BigDecimal.ZERO
        val groups = daily.groupBy { it.date }
        val start = today.withDayOfMonth(1).minusMonths(11).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val cells = mutableListOf<HomeHeatCell>()
        var date = start
        while (date <= today) {
            val index = cells.size
            // Keep an ambiguous or unprovided day unknown in the data model. Its
            // neutral color agrees with desktop empty activity; it is not a zero record.
            val row = groups[date.toString()]?.singleOrNull()
            cells.add(HomeHeatCell(date.toString(), index / 7, index % 7, row,
                level(row?.tokens?.let { BigDecimal.valueOf(it) }, maxTokens?.let { BigDecimal.valueOf(it) }), level(decimal(row?.cost), maxCost)))
            date = date.plusDays(1)
        }
        val visibleActive = cells.count { (it.value?.tokens ?: 0) > 0 }.toLong()
        val peak = listOfNotNull(history.summary?.peakDayTokens, maxTokens).maxOrNull()
        return HomeChartProjection(projected, today, cells, daily.takeLast(45), peak,
            if (activeDaysWindow == "year") visibleActive else previewActiveDays ?: visibleActive)
    }

    /** Same 9dp cells/3dp gaps as desktop. Gaps and future cells have no day target. */
    fun hitCell(projection: HomeChartProjection, x: Double, y: Double): HomeHeatCell? {
        if (!x.isFinite() || !y.isFinite() || x < 0 || y < 0 || x % 12 >= 9 || y % 12 >= 9) return null
        val column = kotlin.math.floor(x / 12).toInt()
        val weekday = kotlin.math.floor(y / 12).toInt()
        if (column >= projection.columns || weekday >= 7) return null
        return projection.cells.getOrNull(column * 7 + weekday)
    }

    /** Desktop areaLineChart with its 300x70 home viewport and curve=true. */
    fun lineSegments(rows: List<HistoryRow>): List<List<HomeLineCommand>> {
        val max = (rows.mapNotNull { it.tokens }.maxOrNull() ?: 1L).coerceAtLeast(1)
        val groups = mutableListOf<MutableList<HomeLinePoint>>()
        var group: MutableList<HomeLinePoint>? = null
        rows.forEachIndexed { index, row ->
            val tokens = row.tokens
            if (tokens == null) group = null else {
                if (group == null) { group = mutableListOf(); groups.add(group!!) }
                group!!.add(HomeLinePoint(3.0 + if (rows.size <= 1) 147.0 else 294.0 * index / (rows.size - 1),
                    66.0 - 62.0 * tokens.toDouble() / max))
            }
        }
        fun round(p: HomeLinePoint) = HomeLinePoint(Math.round(p.x * 100.0) / 100.0, Math.round(p.y * 100.0) / 100.0)
        return groups.map { points -> points.mapIndexed { index, end ->
            if (index == 0 || points.size < 3) HomeLineCommand(round(end)) else {
                val p0 = points[(index - 2).coerceAtLeast(0)]; val p1 = points[index - 1]
                val p3 = points[(index + 1).coerceAtMost(points.lastIndex)]
                HomeLineCommand(round(end), round(HomeLinePoint(p1.x + (end.x - p0.x) / 6, p1.y + (end.y - p0.y) / 6)),
                    round(HomeLinePoint(end.x - (p3.x - p1.x) / 6, end.y - (p3.y - p1.y) / 6)))
            }
        } }
    }

    private fun decimal(value: String?) = value?.takeIf { it.length <= 128 }?.let { runCatching { BigDecimal(it) }.getOrNull() }
        ?.takeIf { it.signum() >= 0 && it.precision() <= 100 && it.scale() in -100..100 }
    private fun level(value: BigDecimal?, max: BigDecimal?): Int {
        if (value == null || value.signum() <= 0 || max == null || max.signum() <= 0) return 0
        return when {
            value >= max.multiply(BigDecimal("0.75")) -> 4
            value >= max.multiply(BigDecimal("0.5")) -> 3
            value >= max.multiply(BigDecimal("0.25")) -> 2
            else -> 1
        }
    }
}
