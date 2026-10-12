package dev.bing.tokenmonitor

import java.math.BigInteger
import java.time.LocalDate

internal enum class TrendsMainRange { WEEK, MONTH, BY_MONTH }
internal data class TrendsMainPoint(val label: String, val tokens: Long)
internal data class TrendsMainStats(
    val activeDays: BigInteger,
    val currentStreak: BigInteger,
    val activeTimeMs: BigInteger,
    val peakDayTokens: BigInteger
)
internal data class TrendsMainModel(
    val points: List<TrendsMainPoint>,
    val range: TrendsMainRange,
    val stats: TrendsMainStats,
    val showZeroMarkers: Boolean
)

/** Desktop v0.68.0 main-window Trends rules; retained history is never changed. */
internal object TrendsMainProjection {
    private val datePrefix = Regex("^(\\d{4})-(\\d{2})-(\\d{2})")
    private val minuteMs = BigInteger.valueOf(60_000)
    private val halfMinuteMs = BigInteger.valueOf(30_000)
    private val hourMinutes = BigInteger.valueOf(60)

    fun project(
        preview: HubHistory?,
        period: String,
        todayTokens: Long?,
        today: LocalDate,
        fixed: FixedPeriodView? = null
    ): TrendsMainModel? {
        val derived = FixedPeriods.derived(period)
        val fixedReady = derived && fixed != null && fixed.period != null && fixed.issue == null
        if (derived && !fixedReady) return null

        // The Hub source is ascending; the Android parser retains descending rows.
        val daily = preview?.daily.orEmpty().sortedBy { it.date }
        val monthly = preview?.monthly.orEmpty().sortedBy { it.date }
        val summary = preview?.summary
        val range = when {
            fixedReady || period == "allTime" -> TrendsMainRange.BY_MONTH
            period == "month" -> TrendsMainRange.MONTH
            else -> TrendsMainRange.WEEK
        }
        val selected = when (range) {
            TrendsMainRange.BY_MONTH -> monthly
            TrendsMainRange.MONTH -> {
                val latestMonth = daily.lastOrNull()?.date?.take(7).orEmpty()
                daily.filter { it.date.take(7) == latestMonth }
            }
            TrendsMainRange.WEEK -> daily.takeLast(7)
        }
        val points = if (period == "today") {
            val live = todayTokens ?: 0L
            if (selected.isEmpty() && live == 0L) emptyList()
            else {
                val byDate = selected.associateBy { it.date.take(10) }
                (-6L..0L).map { offset ->
                    val key = today.plusDays(offset).toString()
                    TrendsMainPoint(key, if (offset == 0L) live else byDate[key]?.tokens ?: 0L)
                }
            }
        } else selected.map { TrendsMainPoint(it.date, it.tokens ?: 0L) }

        // Unlike the month chart, the month cards use the local current month.
        val currentDay = today.toString()
        val currentMonth = currentDay.take(7)
        val activityRows = daily.filter {
            if (period == "today") it.date.take(10) == currentDay else it.date.take(7) == currentMonth
        }
        val activeTime = when {
            fixedReady -> integer(fixed?.activity?.activeTimeMs)
            period == "allTime" -> integer(summary?.activeTimeMs)
            else -> activityRows.fold(BigInteger.ZERO) { sum, row -> sum + integer(row.activeTimeMs) }
        }
        val peak = when {
            fixedReady -> integer(fixed?.activity?.peakDayTokens)
            period == "allTime" -> integer(summary?.peakDayTokens)
            else -> activityRows.fold(BigInteger.ZERO) { value, row -> value.max(integer(row.tokens)) }
        }
        return TrendsMainModel(points, range, TrendsMainStats(
            integer(summary?.activeDays), integer(summary?.currentStreak), activeTime, peak
        ), showZeroMarkers = period == "today")
    }

    /** Point labels are parsed yyyy-MM or yyyy-MM-dd, matching the source label key. */
    fun shortLabel(label: String): String {
        val match = datePrefix.find(label) ?: return label
        return "${match.groupValues[2].toInt()}/${match.groupValues[3].toInt()}"
    }

    fun activeDuration(ms: BigInteger): String {
        val minutes = (ms.max(BigInteger.ZERO) + halfMinuteMs) / minuteMs
        val hours = minutes / hourMinutes
        val remainder = minutes % hourMinutes
        return if (hours.signum() > 0) "${hours}h ${remainder}m" else "${minutes}m"
    }

    private fun integer(value: Long?): BigInteger = value?.let(BigInteger::valueOf) ?: BigInteger.ZERO
}
