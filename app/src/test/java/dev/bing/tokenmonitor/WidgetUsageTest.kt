package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class WidgetUsageTest {
    private fun snapshot(periods: String) = HubParser.parse("""{"updatedAt":"2026-10-08T00:00:00Z","periods":$periods}""")
    @Test fun usesHubTodayWithoutFallingBackToAllTimeOrReplacingUnknownWithZero() {
        val usage = widgetUsage(snapshot("""{"allTime":{"totalTokens":123,"costUsd":1}}"""), 100)
        assertNull(usage.tokens); assertNull(usage.cost)
        assertEquals("2026-10-08T00:00:00Z", usage.updated)
        assertEquals(100L, usage.savedAt)
    }
    @Test fun retainsLongMaximumExactDecimalAndExplicitZero() {
        val usage = widgetUsage(snapshot("""{"today":{"totalTokens":9223372036854775807,"costUsd":"0.1234567890123456789","unpricedTokens":12}}"""), 100)
        assertEquals(Long.MAX_VALUE, usage.tokens)
        assertEquals("0.1234567890123456789", usage.cost)
        assertTrue(usage.unpriced)
        val zero = widgetUsage(snapshot("""{"today":{"totalTokens":0,"costUsd":0}}"""), 100)
        assertEquals(0L, zero.tokens); assertEquals("0", zero.cost); assertFalse(zero.unpriced)
    }
    @Test fun rejectsMalformedNegativeAndExpansionHeavyCosts() {
        listOf("bad", "-1", "1e1000000000", "1e-1000000000").forEach { value ->
            assertNull(widgetUsage(snapshot("""{"today":{"costUsd":"$value"}}"""), 0).cost)
        }
    }
}
