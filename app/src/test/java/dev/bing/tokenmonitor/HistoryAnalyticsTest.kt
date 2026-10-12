package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class HistoryAnalyticsTest {
    @Test fun retainsAttributionAndLifetimeSummaryBeyondDailyRetention() {
        val history = HistoryParser.parse("""{"daily":[{"date":"2026-10-09","tokens":30,"cost":1.5,"perClient":{"codex":{"tokens":20,"cost":1},"claude":{"tokens":10,"cost":0.5}},"perModel":{"demo":{"tokens":30,"cost":1.5}},"activeTimeMs":60000}],"monthly":[],"summary":{"totalTokens":9000,"totalCost":400,"activeDays":106,"currentStreak":7,"activeTimeMs":3600000,"peakDayTokens":3000,"favoriteModel":"demo","messages":42}}""")
        assertEquals(20L, history.daily.single().perClient.getValue("codex").tokens)
        assertEquals("0.5", history.daily.single().perClient.getValue("claude").cost)
        assertEquals(9000L, history.summary?.totalTokens)
        assertEquals(42L, history.summary?.messages)
        assertEquals(60000L, history.daily.single().activeTimeMs)
    }
    @Test fun incompleteAttributionRemainsUnassignedAndAliasesFold() {
        val row = HistoryRow("2026-10-09", 30, null, perClient = mapOf("antigravity-cli" to HistoryAttribution(7, null), "antigravity" to HistoryAttribution(3, null)))
        assertEquals(mapOf("antigravity" to BigInteger.TEN, HistoryAnalytics.UNATTRIBUTED to BigInteger.valueOf(20)), HistoryAnalytics.stacks(row, false))
        assertEquals(mapOf(HistoryAnalytics.UNATTRIBUTED to BigInteger.valueOf(30)), HistoryAnalytics.stacks(row, true))
        assertNull(HistoryParser.parse("""{"daily":[{"date":"2026-10-09","tokens":0,"perClient":{"demo":{"tokens":-1,"cost":"NaN"}}}]}""").daily.single().perClient["demo"]?.tokens)
    }
    @Test fun candlesUseCalendarBucketsAndDoNotTurnUnknownDaysIntoZero() {
        val rows = listOf(HistoryRow("2026-10-01", 9, null), HistoryRow("2026-10-02", 30, null), HistoryRow("2026-10-03", 12, null),
            HistoryRow("2026-10-04", null, null), HistoryRow("2026-10-05", 25, null), HistoryRow("2026-10-06", 0, null))
        val candles = HistoryAnalytics.candles(rows, 3)
        assertEquals(2, candles.size)
        assertEquals(HistoryCandle("2026-10-01", "2026-10-03", 3, 3, 9, 12, 30, 9), candles.first())
        assertNull(candles.last().open)
        assertEquals(0L, candles.last().close)
        assertEquals(2, candles.last().recorded)
        assertEquals(3L, candles.last().calendarDays)
        assertEquals(0L, candles.last().low)
    }
    @Test fun annualCalendarContainsExactly365CalendarDaysIncludingUnknowns() {
        val weeks = HistoryCalendar.weeks(HubHistory(listOf(HistoryRow("2026-10-09", 0, null)), emptyList()), 53)
        assertEquals(365, weeks.flatten().count { it != null })
        assertEquals("2025-10-10", weeks.flatten().filterNotNull().first().date)
        assertEquals(0L, weeks.flatten().filterNotNull().last().tokens)
        assertNull(weeks.flatten().filterNotNull().first().tokens)
    }
}
