package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.time.LocalDate

/** Source cases are checked in evidence/trends-main; huge integers explicitly extend JS precision. */
class TrendsMainProjectionTest {
    private val today = LocalDate.of(2026, 10, 11)
    private val summary = HistorySummary(50_000, "1.25", 73, 4, 8, 900_000, 12_345, "public-model", 10)
    private fun integer(value: Long) = BigInteger.valueOf(value)
    private fun project(history: HubHistory?, period: String, live: Long? = null, day: LocalDate = today, fixed: FixedPeriodView? = null) =
        TrendsMainProjection.project(history, period, live, day, fixed)
    private fun ready(activity: FixedActivity? = FixedActivity(2, 1, 5_000, 77)) = FixedPeriodView(
        period = HubParser.parse("""{"periods":{"month":{"totalTokens":222}}}""").periods.getValue("month"),
        daily = listOf(HistoryRow("2026-10-11", 999, null, activeTimeMs = 999)), activity = activity
    )

    @Test fun dayMatchesSourceSparseCalendarLivePatchAndUnpatchedActivityCards() {
        val history = HistoryParser.parse("""{"daily":[
            {"date":"2026-09-30","tokens":900},
            {"date":"2026-10-05","tokens":12},
            {"date":"2026-10-07","tokens":31},
            {"date":"2026-10-11","tokens":45,"activeTimeMs":700}],"monthly":[],
            "summary":{"activeDays":73,"currentStreak":4}}""")
        assertEquals("Parser order is different from the source order", "2026-10-11", history.daily.first().date)
        val result = project(history, "today", 99)!!
        assertEquals(TrendsMainRange.WEEK, result.range)
        assertTrue(result.showZeroMarkers)
        assertEquals(listOf("2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09", "2026-10-10", "2026-10-11"), result.points.map { it.label })
        assertEquals(listOf(12L, 0L, 31L, 0L, 0L, 0L, 99L), result.points.map { it.tokens })
        assertEquals(TrendsMainStats(integer(73), integer(4), integer(700), integer(45)), result.stats)
        assertEquals("Live chart presentation does not rewrite cached daily", 45L, history.daily.first().tokens)
    }

    @Test fun dayBuildsItsCalendarFromOnlyTheLastSevenSourceRecords() {
        val history = HubHistory((5..12).map { day -> HistoryRow("2026-10-${day.toString().padStart(2, '0')}", day.toLong(), null) }.reversed(), emptyList())
        val result = project(history, "today", 99)!!
        // Oct 5 is inside the calendar but outside the source's last seven records.
        assertEquals(listOf(0L, 6L, 7L, 8L, 9L, 10L, 99L), result.points.map { it.tokens })
        assertEquals(integer(11), result.stats.peakDayTokens)
        assertEquals(BigInteger.ZERO, result.stats.activeDays)
    }

    @Test fun monthChartUsesLatestPreviewMonthWhileCardsUseCurrentLocalMonth() {
        val history = HubHistory(listOf(
            HistoryRow("2026-11-01", 69, null, activeTimeMs = 100),
            HistoryRow("2026-10-02", 17, null, activeTimeMs = 60),
            HistoryRow("2026-09-30", 9, null, activeTimeMs = 7),
            HistoryRow("2026-10-01", 12, null, activeTimeMs = 3)
        ), emptyList(), summary)
        val result = project(history, "month", 999, LocalDate.of(2026, 10, 15))!!
        assertEquals(TrendsMainRange.MONTH, result.range)
        assertFalse(result.showZeroMarkers)
        assertEquals(listOf(TrendsMainPoint("2026-11-01", 69)), result.points)
        assertEquals(TrendsMainStats(integer(73), integer(4), integer(63), integer(17)), result.stats)

        val oldPreview = history.copy(daily = history.daily.filter { it.date.startsWith("2026-10") })
        val afterMidnight = project(oldPreview, "month", null, LocalDate.of(2026, 11, 1))!!
        assertEquals(listOf(TrendsMainPoint("2026-10-01", 12), TrendsMainPoint("2026-10-02", 17)), afterMidnight.points)
        assertEquals(BigInteger.ZERO, afterMidnight.stats.activeTimeMs)
        assertEquals(BigInteger.ZERO, afterMidnight.stats.peakDayTokens)
    }

    @Test fun totalUsesAscendingMonthlyAndLifetimeSummaryWithoutWindowInference() {
        val history = HubHistory(listOf(HistoryRow("2026-10-11", 99_999, null, activeTimeMs = 3_000_000)), listOf(
            HistoryRow("2026-11", 69, null), HistoryRow("2026-09", 90, null), HistoryRow("2026-10", 110, null)
        ), summary)
        val result = project(history, "allTime", Long.MAX_VALUE)!!
        assertEquals(TrendsMainRange.BY_MONTH, result.range)
        assertFalse(result.showZeroMarkers)
        assertEquals(listOf(TrendsMainPoint("2026-09", 90), TrendsMainPoint("2026-10", 110), TrendsMainPoint("2026-11", 69)), result.points)
        assertEquals(TrendsMainStats(integer(73), integer(4), integer(900_000), integer(12_345)), result.stats)
        assertEquals(TrendsMainStats(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO), project(history.copy(summary = null), "allTime")!!.stats)
    }

    @Test fun derivedReadyKeepsMonthlyContextAndOnlyUsesFixedTimeAndPeak() {
        val history = HubHistory(listOf(HistoryRow("2026-10-11", 999, null)), listOf(
            HistoryRow("2026-10", 110, null), HistoryRow("2026-09", 90, null)
        ), summary)
        for (period in listOf("week", "last7", "last30")) {
            val result = project(history, period, 999, fixed = ready())!!
            assertEquals(period, TrendsMainRange.BY_MONTH, result.range)
            assertEquals(period, listOf(TrendsMainPoint("2026-09", 90), TrendsMainPoint("2026-10", 110)), result.points)
            assertEquals(period, TrendsMainStats(integer(73), integer(4), integer(5_000), integer(77)), result.stats)
            assertFalse(period, result.showZeroMarkers)
        }
        val missingFixedActivity = project(history, "last7", fixed = ready(null))!!
        assertEquals(BigInteger.ZERO, missingFixedActivity.stats.activeTimeMs)
        assertEquals(BigInteger.ZERO, missingFixedActivity.stats.peakDayTokens)
        assertEquals(integer(73), missingFixedActivity.stats.activeDays)
    }

    @Test fun onlyDerivedWaitingReturnsNullAndEmptyPreviewKeepsSourceEmpty() {
        for (period in listOf("week", "last7", "last30")) {
            assertNull(project(null, period))
            assertNull(project(null, period, fixed = FixedPeriodView()))
            assertNull(project(null, period, fixed = ready().copy(issue = FixedPeriodIssue.HISTORY)))
            assertTrue(project(null, period, fixed = ready())!!.points.isEmpty())
        }
        for (period in listOf("today", "month", "allTime")) assertTrue(project(null, period, 0)!!.points.isEmpty())
        val liveOnly = project(null, "today", 10)!!
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 0L, 10L), liveOnly.points.map { it.tokens })
        val retainedToday = HubHistory(listOf(HistoryRow("2026-10-11", 100, null)), emptyList())
        assertEquals(0L, project(retainedToday, "today", null)!!.points.last().tokens)
        assertEquals(integer(100), project(retainedToday, "today", null)!!.stats.peakDayTokens)
    }

    @Test fun activitySumsRemainExactBeyondLongWithoutDerivingLifetimeCounts() {
        val history = HubHistory(listOf(
            HistoryRow("2026-10-10", Long.MAX_VALUE, null, activeTimeMs = Long.MAX_VALUE),
            HistoryRow("2026-10-11", Long.MAX_VALUE, null, activeTimeMs = Long.MAX_VALUE)
        ), emptyList(), summary.copy(activeDays = null, currentStreak = null))
        val result = project(history, "month")!!
        assertEquals(BigInteger("18446744073709551614"), result.stats.activeTimeMs)
        assertEquals(integer(Long.MAX_VALUE), result.stats.peakDayTokens)
        assertEquals(BigInteger.ZERO, result.stats.activeDays)
        assertEquals(BigInteger.ZERO, result.stats.currentStreak)
        assertEquals(Long.MAX_VALUE, result.points.first().tokens)
    }

    @Test fun labelsAndDurationUseSourceFormatAndMinuteRounding() {
        assertEquals("1/2", TrendsMainProjection.shortLabel("2026-01-02"))
        assertEquals("10/11", TrendsMainProjection.shortLabel("2026-10-11"))
        assertEquals("2026-10", TrendsMainProjection.shortLabel("2026-10"))
        assertEquals("unknown", TrendsMainProjection.shortLabel("unknown"))
        assertEquals("", TrendsMainProjection.shortLabel(""))
        val cases = listOf(-30_000L to "0m", 0L to "0m", 29_999L to "0m", 30_000L to "1m", 89_999L to "1m", 90_000L to "2m", 3_540_000L to "59m", 3_570_000L to "1h 0m", 3_630_000L to "1h 1m")
        for ((ms, expected) in cases) assertEquals(ms.toString(), expected, TrendsMainProjection.activeDuration(integer(ms)))
    }

    @Test fun durationKeepsExactIntegerMinutesBeyondJavaScriptNumberPrecision() {
        // The source format stays h/m; BigInteger deliberately avoids Number precision loss.
        assertEquals("5124095576030431h 0m", TrendsMainProjection.activeDuration(BigInteger("18446744073709551600000")))
    }
}
