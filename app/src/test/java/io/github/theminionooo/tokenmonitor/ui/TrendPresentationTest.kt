package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.HistoryAttribution
import io.github.theminionooo.tokenmonitor.domain.HistoryPoint
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TrendPresentationTest {
    private val start = LocalDate.of(2026, 10, 1)
    private fun day(offset: Long, total: Long, parts: Map<String, Long> = emptyMap()) =
        HistoryPoint(start.plusDays(offset).toString(), total, 0.0, perClient = parts.mapValues { HistoryAttribution(it.value) })

    @Test fun `partial and absent attribution retain the reported daily total`() {
        val trend = prepareTrends(listOf(day(0, 100, mapOf("codex" to 60)), day(1, 200)), false, start, start.plusDays(1))
        assertEquals(listOf(100L, 200L), trend.days.map { it.segments.values.sum() })
        assertEquals(240L, trend.unattributedTokens)
        assertEquals(60L, trend.days.first().segments[TrendSeries("codex")])
    }

    @Test fun `missing days retain calendar spacing and are never joined by candles`() {
        val trend = prepareTrends(listOf(day(0, 100), day(2, 300), day(3, 0)), false, start, start.plusDays(6))
        assertEquals(4L, trend.missingDays)
        assertEquals(listOf(0L, 2L, 3L), trend.days.map { trend.dayOffset(it.date) })
        val candles = trendCandles(trend)
        assertEquals(listOf(start, start.plusDays(2)), candles.map { it.first })
        assertEquals(start.plusDays(3), candles.last().last)
        assertEquals(0L, candles.last().low) // A reported zero is an observation.
        assertTrue(trendChartDescription(trend, true).contains("4 days have no observation"))
    }

    @Test fun `excess attribution is disclosed without inflating or rescaling usage`() {
        val trend = prepareTrends(listOf(day(0, 10, mapOf("one" to Long.MAX_VALUE, "two" to 1))), false, start, start)
        assertTrue(trend.days.single().inconsistentAttribution)
        assertEquals(mapOf(TrendSeries(null) to 10L), trend.days.single().segments)
    }

    @Test fun `long legends group the remainder without losing usage or colliding with real names`() {
        val parts = (1L..9).associate { "tool-$it" to it } + ("Other tools" to 100L) + ("Unattributed" to 200L)
        val trend = prepareTrends(listOf(day(0, 400, parts)), false, start, start)
        assertEquals(400L, trend.days.single().segments.values.sum())
        assertTrue(TrendSeries("Other tools") in trend.series)
        assertTrue(TrendSeries("Other tools", other = true) in trend.series)
        assertTrue(TrendSeries(null) in trend.series)
    }

    @Test fun `all history uses valid unique observations without manufacturing missing zero days`() {
        val trend = prepareTrends(listOf(day(2, 10), day(2, 20), day(8, 100), HistoryPoint("invalid", 200, 0.0)), false, null, start.plusDays(6))
        assertEquals(start.plusDays(2), trend.start)
        assertEquals(20L, trend.totalTokens)
        assertEquals(4L, trend.missingDays)
        assertEquals(1, trendCandles(trend).size)
    }
}
