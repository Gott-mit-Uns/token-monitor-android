package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

/** Decisions/WAAPI constants use the actual chart-motion-source oracle; timeline is native rendering. */
class DashboardChartMotionTest {
    private fun nanos(ms: Long) = ms * 1_000_000
    private fun bar(key: String = "public-date", x: Double = 20.0, y: Double = 40.0,
        width: Double = 30.0, height: Double = 100.0) = DashboardBarTarget(key, DashboardChartRect(x, y, width, height))
    private fun bars(vararg bars: DashboardBarTarget, width: Double = 320.0, height: Double = 200.0) =
        DashboardChartTargets(DashboardTrendMode.BARS, width, height, bars.toList(), emptyList())
    private fun candle(key: String = "public-date", body: DashboardChartRect? = DashboardChartRect(20.0, 40.0, 30.0, 100.0),
        high: Double? = 10.0, low: Double? = 160.0) = DashboardCandleTarget(key, body, 35.0, high, low)
    private fun candles(vararg candles: DashboardCandleTarget) = DashboardChartTargets(DashboardTrendMode.KLINE,
        320.0, 200.0, emptyList(), candles.toList())
    private fun DashboardChartMotion.renderAt(targets: DashboardChartTargets?, event: Any?, revision: Int,
        reason: DashboardMotionReason, ms: Long, animate: Boolean = true, scale: Float = 1f, consume: Boolean = true) =
        render(targets, event, revision, reason, nanos(ms), animate, consume, scale)

    @Test fun existingBoundsUseTopLeftTranslationAndBothScaleAxesFor800Ms() {
        val motion = DashboardChartMotion()
        motion.renderAt(bars(bar(x = 10.0, y = 20.0, width = 40.0, height = 80.0)), "old", 0,
            DashboardMotionReason.NONE, 0)
        val target = bars(bar(x = 30.0, y = 50.0, width = 20.0, height = 40.0))
        motion.renderAt(target, "new", 0, DashboardMotionReason.NONE, 100)
        val first = motion.frame.bars.getValue("public-date")
        assertEquals(DashboardBarTransform(30.0, 50.0, 2.0, 2.0, -20.0, -30.0), first)
        assertEquals(DashboardChartRect(10.0, 20.0, 40.0, 80.0), motion.displayedBars().getValue("public-date"))
        motion.advance(nanos(200))
        val middle = motion.frame.bars.getValue("public-date")
        assertTrue(middle.scaleX in 1.0..2.0 && middle.scaleY in 1.0..2.0)
        assertTrue(middle.translateX in -20.0..0.0 && middle.translateY in -30.0..0.0)
        motion.advance(nanos(899)); assertTrue(motion.running)
        motion.advance(nanos(900)); assertFalse(motion.running)
        assertEquals(target.bars.single().rect, motion.displayedBars().getValue("public-date"))
    }

    @Test fun entrySeriesAndNewKeysGrowAtBottomAndDelayCapsAt216Ms() {
        val targets = bars(*Array(22) { index -> bar(index.toString(), x = index * 10.0) })
        val motion = DashboardChartMotion()
        motion.renderAt(targets, "data", 1, DashboardMotionReason.ENTRY, 0)
        assertEquals(DashboardBarTransform(15.0, 140.0, scaleY = 0.0), motion.frame.bars.getValue("0"))
        motion.advance(nanos(12)); assertTrue(motion.frame.bars.getValue("0").scaleY > 0)
        assertEquals(0.0, motion.frame.bars.getValue("1").scaleY, 0.0)
        motion.advance(nanos(216)); assertEquals(0.0, motion.frame.bars.getValue("21").scaleY, 0.0)
        motion.advance(nanos(1015)); assertTrue(motion.running)
        motion.advance(nanos(1016)); assertFalse(motion.running)
        motion.renderAt(targets, "data", 2, DashboardMotionReason.SERIES, 1020)
        assertEquals(0.0, motion.frame.bars.getValue("0").scaleY, 0.0)
    }

    @Test fun skipThresholdRequiresAllFourStrictlySmallDifferences() {
        fun run(dx: Double, width: Double): DashboardChartMotion {
            val motion = DashboardChartMotion()
            motion.renderAt(bars(bar(x = 20.0 + dx, width = width)), "old", 0, DashboardMotionReason.NONE, 0)
            motion.renderAt(bars(bar()), "new", 0, DashboardMotionReason.UPDATE, 1)
            return motion
        }
        assertFalse(run(.49, 30.0).running)
        assertTrue(run(.5, 30.0).running)
        assertTrue(run(0.0, 30.3).running)
    }

    @Test fun midFlightDataRenderCapturesDisplayedBoundsAndSameTargetRestartsOnlyForNewEvent() {
        val motion = DashboardChartMotion()
        val target = bars(bar())
        motion.renderAt(target, "first", 1, DashboardMotionReason.ENTRY, 0)
        motion.advance(nanos(100))
        val displayed = motion.displayedBars().getValue("public-date")
        val before = motion.frame
        assertFalse(motion.renderAt(target, "first", 1, DashboardMotionReason.ENTRY, 100))
        assertEquals(before, motion.frame)
        assertTrue(motion.renderAt(target, "new-data", 1, DashboardMotionReason.ENTRY, 100))
        assertEquals(displayed, motion.displayedBars().getValue("public-date"))
        assertTrue(motion.frame.bars.getValue("public-date").scaleY > 0)
        motion.advance(nanos(801)); assertTrue(motion.running)
        motion.advance(nanos(900)); assertFalse(motion.running)
    }

    @Test fun zeroRectCaptureAndPositiveToZeroFallbackKeepOriginalDelayRules() {
        val motion = DashboardChartMotion()
        motion.renderAt(bars(bar("zero", height = 0.0), bar("positive")), "old", 0, DashboardMotionReason.NONE, 0)
        assertFalse(motion.displayedBars().containsKey("zero"))
        motion.renderAt(bars(bar("zero"), bar("positive", height = 0.0)), "new", 0, DashboardMotionReason.UPDATE, 1)
        assertEquals(0.0, motion.frame.bars.getValue("positive").scaleY, 0.0)
        motion.advance(nanos(2))
        // Old positive key exists even with a zero target: delay remains zero, not index1×12ms.
        assertTrue(motion.frame.bars.getValue("positive").scaleY > 0)
        assertTrue(motion.frame.bars.getValue("zero").scaleY > 0)
        val invisible = DashboardChartMotion()
        invisible.renderAt(bars(bar(height = 0.0)), "zero-only", 0, DashboardMotionReason.ENTRY, 0)
        assertTrue(invisible.running)
        assertFalse(invisible.hasVisibleMotion)
        invisible.advance(nanos(800)); assertFalse(invisible.running)
    }

    @Test fun consumedEntryAndSeriesReasonsDoNotReplayForLaterDataOrSettingsReturn() {
        val motion = DashboardChartMotion()
        val initial = bars(bar())
        motion.renderAt(initial, "first", 1, DashboardMotionReason.ENTRY, 0)
        motion.advance(nanos(800))
        motion.renderAt(bars(bar(height = 50.0)), "second", 1, DashboardMotionReason.ENTRY, 900)
        assertEquals(2.0, motion.frame.bars.getValue("public-date").scaleY, 0.0)
        motion.settle()
        val frame = motion.frame
        assertFalse(motion.renderAt(frame.targets, "second", 1, DashboardMotionReason.ENTRY, 901, animate = false, consume = false))
        assertFalse(motion.renderAt(frame.targets, "second", 1, DashboardMotionReason.ENTRY, 902))
        assertFalse(motion.running)
        motion.renderAt(frame.targets, "second", 2, DashboardMotionReason.ENTRY, 903)
        assertEquals(0.0, motion.frame.bars.getValue("public-date").scaleY, 0.0)
    }

    @Test fun pureResizeSettlesButDataWithDifferentPlotHeightUsesGeometryUpdate() {
        val motion = DashboardChartMotion()
        motion.renderAt(bars(bar()), "same", 0, DashboardMotionReason.ENTRY, 0)
        motion.advance(nanos(100))
        motion.renderAt(bars(bar(x = 25.0), width = 420.0), "same", 0, DashboardMotionReason.NONE, 100)
        assertFalse(motion.running)
        assertEquals(1.0, motion.frame.bars.getValue("public-date").scaleY, 0.0)
        motion.renderAt(bars(bar(height = 50.0), height = 260.0), "new-data", 0, DashboardMotionReason.NONE, 101)
        assertTrue(motion.running)
        assertEquals(2.0, motion.frame.bars.getValue("public-date").scaleY, 0.0)
    }

    @Test fun everyCandleUpdateReplaysBodyCenterAndWickFor560MsWith180MsCap() {
        val motion = DashboardChartMotion()
        val targets = candles(*Array(22) { candle(it.toString()) })
        motion.renderAt(targets, "first", 0, DashboardMotionReason.UPDATE, 0)
        assertEquals(DashboardCandleProgress(0.0, 0.0, 0.0), motion.frame.candles.getValue("0"))
        motion.advance(nanos(10)); assertTrue(motion.frame.candles.getValue("0").bodyScaleY > 0)
        assertEquals(0.0, motion.frame.candles.getValue("1").bodyScaleY, 0.0)
        motion.advance(nanos(180)); assertEquals(0.0, motion.frame.candles.getValue("21").bodyScaleY, 0.0)
        motion.advance(nanos(739)); assertTrue(motion.running)
        motion.advance(nanos(740)); assertFalse(motion.running)
        motion.renderAt(targets, "new-data", 0, DashboardMotionReason.UPDATE, 741)
        assertEquals(0.0, motion.frame.candles.getValue("0").bodyScaleY, 0.0)
    }

    @Test fun unknownCandleDoesNotInventBodyOrShiftLaterDateDelayAndZeroLengthWicksStayStatic() {
        val motion = DashboardChartMotion()
        val targets = candles(candle("unknown", body = null), candle("known", high = 90.0, low = 90.0))
        motion.renderAt(targets, "data", 0, DashboardMotionReason.ENTRY, 0)
        assertEquals(DashboardCandleProgress(), motion.frame.candles.getValue("unknown"))
        assertEquals(0.0, motion.frame.candles.getValue("known").bodyScaleY, 0.0)
        assertEquals(1.0, motion.frame.candles.getValue("known").highWickProgress, 0.0)
        motion.advance(nanos(10)); assertEquals(0.0, motion.frame.candles.getValue("known").bodyScaleY, 0.0)
        motion.advance(nanos(11)); assertTrue(motion.frame.candles.getValue("known").bodyScaleY > 0)
    }

    @Test fun reducedHiddenAndScaledTimelinesAreBoundedWithoutExtraEntryOnReturn() {
        val motion = DashboardChartMotion()
        val targets = bars(bar())
        motion.renderAt(targets, "data", 1, DashboardMotionReason.ENTRY, 0, animate = false, consume = false)
        assertFalse(motion.running)
        motion.renderAt(targets, "data", 1, DashboardMotionReason.ENTRY, 1, scale = 2f)
        assertTrue(motion.running)
        motion.advance(nanos(1600)); assertTrue(motion.running)
        motion.advance(nanos(1601)); assertFalse(motion.running)
        motion.renderAt(targets, "data", 2, DashboardMotionReason.ENTRY, 1602)
        motion.advance(nanos(1700))
        val beforeRatioChange = motion.frame.bars.getValue("public-date").scaleY
        motion.advance(nanos(1700), 2f)
        assertEquals("Positive ratio is captured at start; changing it cannot reverse current progress",
            beforeRatioChange, motion.frame.bars.getValue("public-date").scaleY, 0.0)
        motion.advance(nanos(1700), 0f); assertFalse(motion.running)
        assertEquals(1.0, motion.frame.bars.getValue("public-date").scaleY, 0.0)
        motion.renderAt(null, "empty", 2, DashboardMotionReason.UPDATE, 1800)
        assertNull(motion.frame.targets)
        assertTrue(motion.frame.bars.isEmpty())
    }

    @Test fun svgFractionBoundaryUsesRawPathEndpointsAndUnionWithIndependentRectRounding() {
        val source = DashboardTrendProjection.project(HubHistory(listOf(
            HistoryRow("2026-10-09", 1, null, perClient = mapOf("a" to HistoryAttribution(1, null)))), emptyList()))
        val path = DashboardTrendSegment("a", java.math.BigInteger.ONE, true, .005, .005, 10.005, 20.005)
        fun target(segments: List<DashboardTrendSegment>) = DashboardChartMotion.targets(source.copy(bars = listOf(
            source.bars.single().copy(x = .005, width = 10.005, segments = segments)))).bars.single().rect
        val onlyPath = target(listOf(path))
        assertEquals(.01, onlyPath.x, 0.0)
        assertEquals(.01, onlyPath.y, 0.0)
        assertEquals(10.0, onlyPath.width, 1e-12)
        assertEquals(20.0, onlyPath.height, 1e-12)
        val rect = path.copy(key = "b", y = 20.005, height = 10.005)
        val mixed = target(listOf(rect, path))
        assertEquals(.01, mixed.x, 0.0)
        assertEquals(.01, mixed.y, 0.0)
        assertEquals(10.01, mixed.width, 1e-12)
        assertEquals(30.01, mixed.height, 1e-12)
    }

    @Test fun sourceWickMidpointRoundsRawGeometryAndKeepsZeroLengthWickStaticAtFractionBoundary() {
        val source = DashboardTrendProjection.project(HubHistory(listOf(
            HistoryRow("2026-10-09", 4, null), HistoryRow("2026-10-10", 10, null)), emptyList()),
            mode = DashboardTrendMode.KLINE)
        val original = source.candles.first()
        val model = source.copy(candles = listOf(original.copy(
            bodyY = .005, bodyHeight = 20.005, yHigh = 10.01, yLow = 10.02)))
        val targets = DashboardChartMotion.targets(model)
        val target = targets.candles.single()
        val body = checkNotNull(target.body)
        assertEquals(.01, body.y, 0.0)
        assertEquals(20.01, body.height, 0.0)
        assertEquals(10.015, body.y + body.height / 2, 1e-12)
        assertEquals(10.01, target.wickMiddleY!!, 0.0)
        val motion = DashboardChartMotion()
        motion.renderAt(targets, "fraction", 0, DashboardMotionReason.ENTRY, 0)
        val first = motion.frame.candles.getValue(original.key)
        assertEquals(0.0, first.bodyScaleY, 0.0)
        assertEquals("The source high wick has exactly zero length and does not animate", 1.0,
            first.highWickProgress, 0.0)
        assertEquals("The nonzero 0.01-unit low wick follows the source draw animation", 0.0,
            first.lowWickProgress, 0.0)
        motion.advance(nanos(100))
        val middle = motion.frame.candles.getValue(original.key)
        assertEquals(1.0, middle.highWickProgress, 0.0)
        assertTrue(middle.bodyScaleY > 0.0 && middle.bodyScaleY < 1.0)
        assertEquals(middle.bodyScaleY, middle.lowWickProgress, 0.0)
        motion.advance(nanos(560))
        assertEquals(DashboardCandleProgress(), motion.frame.candles.getValue(original.key))
        assertFalse(motion.running)
    }
}
