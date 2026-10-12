package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate

/** Normal expectations are captured from ORIGINAL renderTrends/usageCharts, not an implementation mirror.
 * See evidence/dashboard-parity/reference/trend-source.json named cases and the executable source oracle.
 * Unknown, duplicate and >Number.MAX_SAFE_INTEGER cases separately assert explicit Android adaptations.
 */
class DashboardTrendProjectionTest {
    private fun big(value: Long) = BigInteger.valueOf(value)
    private fun attribution(vararg values: Pair<String, Long?>) = values.associate { it.first to HistoryAttribution(it.second, null) }
    private fun row(date: String, tokens: Long?, vararg tools: Pair<String, Long?>) =
        HistoryRow(date, tokens, null, perClient = attribution(*tools))
    private fun project(rows: List<HistoryRow>, range: String = "all", byModel: Boolean = false,
        mode: DashboardTrendMode = DashboardTrendMode.BARS, width: Double = 320.0, height: Double = 200.0) =
        DashboardTrendProjection.project(HubHistory(rows, emptyList()), range, byModel, mode, width, height)
    private val sparse = listOf(row("2026-10-01", 1000, "codex" to 100L),
        row("2026-10-05", 20, "codex" to 20L), row("2026-10-09", 10, "codex" to 10L))
    private val series = List(400) { index -> row(LocalDate.of(2025, 1, 1).plusDays(index.toLong()).toString(),
        (index + 1).toLong(), "codex" to (index + 1).toLong()) }

    @Test fun sourceRangesKeepLastRecordsInAscendingOrderFromDescendingWireRows() {
        val expected = listOf(Triple("7", 7, "2026-01-29"), Triple("30", 30, "2026-01-06"),
            Triple("90", 90, "2025-11-07"), Triple("365", 365, "2025-02-05"), Triple("all", 400, "2025-01-01"))
        expected.forEach { (range, count, first) ->
            val model = project(series.reversed(), range)
            assertEquals("range_$range", count, model.bars.size)
            assertEquals(first, model.bars.first().label)
            assertEquals("2026-02-04", model.bars.last().label)
            assertEquals(model.daily.map { it.date }, model.bars.map { it.label })
            assertEquals(5, model.axisTicks.size)
            assertTrue(model.axisLabels.size <= 9)
        }
        assertEquals(listOf("1/6", "1/10", "1/14", "1/18", "1/22", "1/26", "1/30", "2/3"),
            project(series, "30").axisLabels.map { it.label })
    }

    @Test fun sparseLastSevenDoesNotBecomeSevenCalendarSlots() {
        val model = project(sparse.reversed(), "7")
        assertEquals(listOf("2026-10-01", "2026-10-05", "2026-10-09"), model.daily.map { it.date })
        assertEquals(listOf("10/1", "10/5", "10/9"), model.axisLabels.map { it.label })
        assertEquals(3, model.bars.size)
    }

    @Test fun attributionSumSuppliesHeightMaximumAndLegendWithoutUnassignedTokens() {
        val model = project(sparse)
        assertEquals(listOf(big(100), big(20), big(10)), model.bars.map { it.total })
        assertEquals(big(100), model.maxValue)
        assertEquals(listOf("codex"), model.keys)
        assertEquals(mapOf("codex" to big(130)), model.keyTotals)
        assertEquals(big(130), model.legend.single().value)
        assertEquals("100.0", model.legend.single().percent.toPlainString())
        val first = model.bars.first().segments.single()
        assertEquals(64.7, first.x, 1e-9)
        assertEquals(59.266666666666666, first.width, 1e-9)
        assertEquals(10.0, first.y, 1e-9)
        assertEquals(166.0, first.height, 1e-9)
        assertEquals(142.8, model.bars[1].segments.single().y, 1e-9)
        assertEquals(listOf("0", "25", "50", "75", "100"), model.axisTicks.map { it.value.toPlainString() })
        assertEquals(listOf(179.0, 137.5, 96.0, 54.5, 13.0), model.axisTicks.map { it.labelY })
        assertTrue(model.bars.all { it.attributionComplete })
        assertFalse(model.hasUnknownData)
    }

    @Test fun overassignedAttributionIsNotReplacedByDailyTotal() {
        val model = project(listOf(row("2026-10-09", 1, "a" to 7L, "b" to 3L)))
        assertEquals(big(10), model.bars.single().total)
        assertEquals(big(10), model.maxValue)
        assertEquals(listOf("a", "b"), model.bars.single().segments.map { it.key })
        assertEquals(listOf("70.0", "30.0"), model.legend.map { it.percent.toPlainString() })
    }

    @Test fun onlyClientAntigravityAliasFoldsAndModelIdentityRemainsRaw() {
        val entries = attribution("antigravity-cli" to 7L, "antigravity" to 3L, "omp" to 20L)
        val row = HistoryRow("2026-10-09", 30, null, perClient = entries, perModel = entries)
        val client = project(listOf(row))
        assertEquals(listOf("omp", "antigravity"), client.keys)
        assertEquals(listOf(big(20), big(10)), client.bars.single().segments.map { it.value })
        val model = project(listOf(row), byModel = true)
        assertEquals(listOf("omp", "antigravity-cli", "antigravity"), model.keys)
        assertEquals(big(7), model.keyTotals["antigravity-cli"])
    }

    @Test fun equalTotalsUseExplicitEnglishSourceCollationIncludingCaseAndAccents() {
        val names = listOf("z", "A", "a", "á", "aa", "ä", "é", "e")
        val row = row("2026-10-09", 8, *names.map { it to 1L }.toTypedArray())
        val model = project(listOf(row))
        assertEquals(listOf("a", "A", "á", "ä", "aa", "e", "é", "z"), model.keys)
        assertEquals(model.keys, model.legend.map { it.key })
    }

    @Test fun zeroKeysRemainGeometryWhileEmptyPositiveAttributionIsIncomplete() {
        val model = project(listOf(row("2026-10-08", 0, "zero" to 0L), row("2026-10-09", 100)))
        assertEquals(listOf("zero"), model.keys)
        assertEquals(listOf(BigInteger.ZERO, BigInteger.ZERO), model.bars.map { it.total })
        assertEquals(BigInteger.ONE, model.maxValue)
        assertEquals(0.0, model.bars.first().segments.single().height, 0.0)
        assertTrue(model.legend.isEmpty())
        assertTrue(model.bars.first().attributionComplete)
        assertFalse(model.bars.last().attributionComplete)
        assertEquals(setOf("2026-10-09"), model.issues.incompleteAttributionDates)
    }

    @Test fun sparseCandlesUseActualFirstAndLastRecordsNotCalendarBoundaryLookups() {
        val model = project(sparse.reversed(), mode = DashboardTrendMode.KLINE)
        assertEquals(2, model.bucketDays)
        assertEquals(listOf("2026-10-01", "2026-10-05", "2026-10-09"), model.candles.map { it.key })
        assertEquals(model.candles.map { it.key }, model.candles.map { it.endKey })
        assertEquals(listOf(big(1000), big(20), big(10)), model.candles.map { it.open })
        assertEquals(model.candles.map { it.open }, model.candles.map { it.close })
        assertTrue(model.candles.all { it.complete && it.days == 1 && it.knownDays == 1 })
        assertTrue(model.legend.isEmpty())
        assertTrue(model.keys.isEmpty())
    }

    @Test fun shortSpanSourceOhlcGeometryAndUpDownColorsAgree() {
        val model = project(listOf(row("2026-10-01", 4), row("2026-10-02", 9),
            row("2026-10-03", 7), row("2026-10-04", 1)), mode = DashboardTrendMode.KLINE)
        assertEquals(2, model.bucketDays)
        val up = model.candles.first()
        assertEquals(listOf(big(4), big(9), big(4), big(9)), listOf(up.open, up.high, up.low, up.close))
        assertEquals("2026-10-02", up.endKey)
        assertEquals(true, up.up)
        assertEquals(0xFF4EC77FL, up.colorArgb)
        assertEquals(77.4, up.x, 1e-9)
        assertEquals(76.2, up.width, 1e-9)
        assertEquals(115.5, up.wickX, 1e-9)
        assertEquals(10.0, up.yHigh!!, 1e-9)
        assertEquals(92.22222222222223, up.bodyHeight!!, 1e-9)
        val down = model.candles.last()
        assertEquals(listOf(big(7), big(7), big(1), big(1)), listOf(down.open, down.high, down.low, down.close))
        assertEquals(false, down.up)
        assertEquals(0xFFF06A7BL, down.colorArgb)
        assertEquals(listOf("10/1", "10/3"), model.axisLabels.map { it.label })
    }

    @Test fun candleBucketSizeUsesPlotWidthAndSourceHalfUpTargetRounding() {
        val rows = series.take(365)
        listOf(Triple(320.0, 33, 12), Triple(366.0, 28, 14), Triple(800.0, 12, 31)).forEach { (width, days, count) ->
            val model = project(rows, mode = DashboardTrendMode.KLINE, width = width)
            assertEquals(days, model.bucketDays)
            assertEquals(count, model.candles.size)
            assertEquals(52.0, model.plot.x, 0.0)
            assertEquals(width - 66, model.plot.width, 0.0)
            assertEquals("2025-12-31", model.candles.last().endKey)
        }
        assertEquals("2025-01-02", project(rows, mode = DashboardTrendMode.KLINE).candles.first().endKey)
        assertEquals(listOf("1/1", "2/5", "4/12", "6/17", "8/22", "10/27"),
            project(rows, mode = DashboardTrendMode.KLINE).axisLabels.map { it.label })
    }

    @Test fun minimumAndFallbackDimensionsDoNotRemoveAxisSpace() {
        val minimum = project(sparse, width = 100.0, height = 100.0)
        assertEquals(320.0, minimum.width, 0.0)
        assertEquals(200.0, minimum.height, 0.0)
        assertEquals(DashboardTrendPlot(52.0, 10.0, 254.0, 166.0), minimum.plot)
        val fallback = project(sparse, width = 0.0, height = 0.0)
        assertEquals(800.0, fallback.width, 0.0)
        assertEquals(360.0, fallback.height, 0.0)
    }

    @Test fun fractionalSourceGeometryHasFiveTicksAndSvgRoundedDatePositions() {
        val model = project(listOf(row("2026-10-08", 3, "a" to 1L, "b" to 2L),
            row("2026-10-09", 7, "a" to 7L)), width = 321.0, height = 201.0)
        assertEquals(71.125, model.bars.first().x, 1e-9)
        assertEquals(89.25, model.bars.first().width, 1e-9)
        assertEquals(23.857142857142858, model.bars.first().segments[0].height, 1e-9)
        assertEquals(105.42857142857143, model.bars.first().segments[1].y, 1e-9)
        assertEquals(listOf("80.0", "20.0"), model.legend.map { it.percent.toPlainString() })
        assertEquals(0, model.axisTicks[1].value.compareTo(BigDecimal("1.75")))
        assertEquals(138.25, model.axisTicks[1].labelY, 0.0)
        assertEquals(DashboardTrendAxisLabel("10/8", 115.75, 197.0), model.axisLabels.first())
        assertEquals(71.13, DashboardTrendProjection.svgRound(model.bars.first().x), 0.0)
    }

    @Test fun emptyAndMonthlyOnlyHaveNoInventedDailyPlot() {
        listOf(HubHistory(emptyList(), emptyList()), HubHistory(emptyList(), listOf(HistoryRow("2026-10", 100, null)))).forEach { history ->
            val model = DashboardTrendProjection.project(history)
            assertTrue(model.daily.isEmpty())
            assertTrue(model.bars.isEmpty())
            assertTrue(model.candles.isEmpty())
            assertTrue(model.axisTicks.isEmpty())
            assertTrue(model.axisLabels.isEmpty())
            assertTrue(model.legend.isEmpty())
        }
    }

    @Test fun androidUnknownAdaptationKeepsMissingCountsAndCandleEndpointsNullable() {
        val bar = project(listOf(row("2026-10-09", null, "unknown" to null)))
        assertFalse(bar.bars.single().segments.single().known)
        assertFalse(bar.bars.single().attributionComplete)
        assertTrue(bar.hasUnknownData)
        val candle = project(listOf(row("2026-10-08", null), row("2026-10-09", 10)), mode = DashboardTrendMode.KLINE).candles.single()
        assertNull(candle.open)
        assertEquals(big(10), candle.close)
        assertEquals(big(10), candle.low)
        assertFalse(candle.complete)
        assertEquals(1, candle.knownDays)
        assertNull(candle.up)
        assertNull(candle.bodyHeight)
        assertNull(candle.colorArgb)
    }

    @Test fun androidDuplicateAdaptationCollapsesAmbiguousDateWithoutSummingIt() {
        val model = project(listOf(row("2026-10-09", 10, "a" to 10L), row("2026-10-09", 20, "a" to 20L)))
        assertEquals(1, model.daily.size)
        assertNull(model.daily.single().tokens)
        assertEquals(BigInteger.ZERO, model.bars.single().total)
        assertTrue(model.bars.single().duplicate)
        assertFalse(model.bars.single().attributionComplete)
        assertEquals(setOf("2026-10-09"), model.issues.duplicateDates)
        assertTrue(model.hasUnknownData)
    }

    @Test fun androidExactCountsRemainPreciseBeyondSourceNumberSafeRangeAndLongAggregate() {
        val unsafe = project(listOf(row("2026-10-09", 9007199254740991L, "a" to 9007199254740991L, "b" to 2L)))
        assertEquals(BigInteger("9007199254740993"), unsafe.bars.single().total)
        assertEquals(BigInteger("9007199254740993"), unsafe.maxValue)
        val huge = project(listOf(row("2026-10-08", Long.MAX_VALUE, "a" to Long.MAX_VALUE),
            row("2026-10-09", Long.MAX_VALUE, "a" to Long.MAX_VALUE)))
        assertEquals(BigInteger("18446744073709551614"), huge.keyTotals["a"])
        assertEquals(BigInteger("18446744073709551614"), huge.legend.single().value)
        assertEquals("100.0", huge.legend.single().percent.toPlainString())
        assertTrue(huge.bars.all { it.segments.single().height.isFinite() })
    }

    @Test fun malformedManualFixtureIsExplicitUnknownAndNeverPoisonsGeometry() {
        val model = project(listOf(row("invalid-date", 7, "a" to 7L), row("2026-10-09", -1, "a" to -1L)))
        assertEquals(setOf("invalid-date"), model.issues.invalidDates)
        assertEquals(setOf("2026-10-09"), model.issues.missingTokenDates)
        assertFalse(model.bars.single().segments.single().known)
        assertTrue(model.bars.single().segments.single().height.isFinite())
        assertTrue(model.hasUnknownData)
    }
}
