package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class HistoryTest {
    @Test fun keepsLocalCalendarKeysAndLargeValuesWithoutFillingMissingDays() {
        val history = HistoryParser.parse("""{"daily":[{"date":"2026-10-08","tokens":5000000123},{"date":"2026-10-09"},{"date":"invalid","tokens":10}],"monthly":[{"month":"2026-10","tokens":5000000123}]}""")
        assertEquals(2, history.daily.size)
        assertEquals("2026-10-09", history.daily.first().date)
        assertNull(history.daily.first().tokens)
        assertEquals(5000000123L, history.daily.last().tokens)
        assertEquals("2026-10", history.monthly.single().date)
    }
    @Test fun summarizesCalendarWindowWithoutInventingMissingDaysOrOverflow() {
        val history = HistoryParser.parse("""{"daily":[{"date":"2026-10-09","tokens":9223372036854775807,"cost":"1.25","unpricedTokens":42},{"date":"2026-10-08","tokens":9223372036854775807,"cost":"2.50"},{"date":"2026-10-06","tokens":0,"cost":"0"},{"date":"2026-09-30","tokens":999,"cost":"NaN"}]}""")
        val range = HistoryRanges.summarize(history, 7)!!
        assertEquals("2026-10-03", range.start.toString())
        assertEquals(7L, range.calendarDays)
        assertEquals(3, range.recordedDays)
        assertEquals(3, range.knownTokenDays)
        assertEquals("18446744073709551614", range.tokens.toString())
        assertEquals(java.math.BigDecimal("3.75"), range.cost)
        assertEquals("42", range.unpricedTokens.toString())
        assertNull(history.daily.last().cost)
    }
    @Test fun duplicateDatesAreAmbiguousAndEmptyHistoryHasNoInventedAnchor() {
        val history = HistoryParser.parse("""{"daily":[{"date":"2026-10-09","tokens":10},{"date":"2026-10-09","tokens":20},{"date":"2026-10-08"}]}""")
        val range = HistoryRanges.summarize(history, null)!!
        assertEquals(2, range.recordedDays)
        assertEquals(0, range.knownTokenDays)
        assertEquals(java.math.BigInteger.ZERO, range.tokens)
        assertNull(HistoryRanges.summarize(HubHistory(emptyList(), emptyList()), 30))
    }
    @Test fun calendarAlignsMondayAndDistinguishesUnknownZeroAndFuture() {
        val history = HistoryParser.parse("""{"daily":[{"date":"2026-10-09","tokens":12},{"date":"2026-10-08","tokens":0}]}""")
        val weeks = HistoryCalendar.weeks(history)
        assertEquals(12, weeks.size)
        assertEquals("2026-10-05", weeks.last()[0]!!.date)
        assertNull(weeks.last()[0]!!.tokens)
        assertEquals(0L, weeks.last()[3]!!.tokens)
        assertEquals(12L, weeks.last()[4]!!.tokens)
        assertNull(weeks.last()[5])
        assertNull(weeks.last()[6])
        assertTrue(HistoryCalendar.weeks(HubHistory(emptyList(), emptyList())).isEmpty())
    }
}
