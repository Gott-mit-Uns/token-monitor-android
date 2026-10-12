package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

/** The lifetime summary is authoritative; retained daily rows may cover much less. */
class HistoryUnpricedSummaryTest {
    @Test fun preservesLifetimeUnpricedCountAndKnownCostWithoutDerivingEitherFromDailyRows() {
        val history = HistoryParser.parse("""{"daily":[{"date":"2026-10-09","tokens":100,"cost":0.25,"unpricedTokens":20}],"monthly":[],"summary":{"totalTokens":9223372036854775807,"totalCost":1.25,"unpricedTokens":9223372036854775806}}""")
        assertEquals(Long.MAX_VALUE, history.summary!!.totalTokens)
        assertEquals(Long.MAX_VALUE - 1, history.summary!!.unpricedTokens)
        assertEquals("1.25", history.summary!!.totalCost)
        assertEquals(20L, history.daily.single().unpricedTokens)
        assertEquals("0.25", history.daily.single().cost)
    }

    @Test fun zeroMissingAndLegacySummaryKeepTheirDistinctSourceFields() {
        val zero = HistoryParser.parse("""{"daily":[],"summary":{"totalTokens":1000,"totalCost":0,"unpricedTokens":0}}""").summary!!
        val missing = HistoryParser.parse("""{"daily":[{"date":"2026-10-09","tokens":1000,"cost":0,"unpricedTokens":1000}],"summary":{"totalTokens":1000,"totalCost":0}}""").summary!!
        assertEquals(0L, zero.unpricedTokens)
        assertNull(missing.unpricedTokens)
        // Old callers and encrypted JSON caches remain readable without synthesizing a field.
        assertNull(HistorySummary(1000, "0", 1, 1, 1, 0, 1000, "public", 1).unpricedTokens)
        assertNull(HistoryParser.parse("""{"daily":[],"monthly":[]}""").summary)
    }

    @Test fun invalidUnpricedCountersDoNotBecomeAnExactZero() {
        for (counter in listOf("-1", "1.5", "9223372036854775808", "\"NaN\"", "null")) {
            val summary = HistoryParser.parse("""{"daily":[],"summary":{"totalTokens":1000,"totalCost":0,"unpricedTokens":$counter}}""").summary!!
            assertNull(summary.unpricedTokens)
            assertEquals("0", summary.totalCost)
        }
    }
}
