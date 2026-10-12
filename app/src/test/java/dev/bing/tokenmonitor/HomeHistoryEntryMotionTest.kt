package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class HomeHistoryEntryMotionTest {
    private fun ms(value: Long) = value * 1_000_000L
    private val columns get() = HomeHeatColumns(HomeHistoryEntrySourceReference.visibleColumns.first(), HomeHistoryEntrySourceReference.visibleColumns.last())
    private fun prepared(content: Any, hasTrend: Boolean = true, scale: Float = 1f, event: Any = "initial") = HomeHistoryEntryMotion().also {
        it.mainView("HOME")
        it.render(content, available = true, visible = true, hasTrendStroke = hasTrend, scale = scale, renderEventKey = event)
    }

    @Test fun executedSourceVisibleColumnsAnd240msStaggerStayWithinTheirExactBoundaries() {
        assertEquals(columns, HomeHistoryEntryMotion.visibleColumns(120, 60, 12f, 9f, 16))
        HomeHistoryEntrySourceReference.visibleColumns.forEachIndexed { index, column ->
            val content = Any()
            val motion = prepared(content)
            assertTrue(motion.frame.preparing)
            assertTrue(motion.heatViewport(content, columns, 0L, 1f))
            val delay = HomeHistoryEntrySourceReference.heatDelaysMs[index].toLong()
            motion.advance(ms(delay), 1f)
            assertEquals("The captured visible column remains at zero through its delay", 0f, motion.frame.heatOpacity(column), 0f)
            motion.advance(ms(delay + 1), 1f)
            assertTrue(motion.frame.heatOpacity(column) > 0f)
            motion.advance(ms(delay + HomeHistoryEntrySourceReference.heatDurationMs), 1f)
            assertEquals(1f, motion.frame.heatOpacity(column), 0f)
            assertEquals("Columns outside the captured viewport are never assigned entry opacity", 1f, motion.frame.heatOpacity(columns.first - 1), 0f)
            assertEquals(1f, motion.frame.heatOpacity(columns.last + 1), 0f)
        }
    }

    @Test fun heatFinishesAt640msWhileLineAndAreaFinishTogetherAtTheSource920ms() {
        val content = Any()
        val motion = prepared(content)
        motion.heatViewport(content, columns, 0L, 1f)
        assertEquals(0f, motion.frame.trendReveal, 0f)
        motion.advance(ms(400), 1f)
        assertEquals(0f, motion.frame.heatOpacity(columns.last), 0f)
        motion.advance(ms(640), 1f)
        assertTrue((columns.first..columns.last).all { motion.frame.heatOpacity(it) == 1f })
        assertTrue(motion.running)
        assertTrue(motion.frame.trendReveal > 0f && motion.frame.trendReveal < 1f)
        assertEquals(HomeHistoryEntrySourceReference.lineDurationMs, HomeHistoryEntrySourceReference.fillDurationMs)
        motion.advance(ms(HomeHistoryEntrySourceReference.lineDurationMs - 1), 1f)
        assertTrue(motion.running)
        motion.advance(ms(HomeHistoryEntrySourceReference.lineDurationMs), 1f)
        assertFalse(motion.running)
        assertEquals(1f, motion.frame.trendReveal, 0f)
    }

    @Test fun aSingleVisibleHeatColumnNeedsOnly240msWhenThereIsNoVisibleTrendStroke() {
        val content = Any()
        val motion = prepared(content, hasTrend = false)
        motion.heatViewport(content, HomeHeatColumns(5, 5), 0L, 1f)
        assertEquals(1f, motion.frame.trendReveal, 0f)
        motion.advance(ms(239), 1f)
        assertTrue(motion.running)
        motion.advance(ms(HomeHistoryEntrySourceReference.heatDurationMs), 1f)
        assertFalse(motion.running)
    }

    @Test fun repeatedCommitsAndSameViewClicksCannotRestartAnAlreadyConsumedEntry() {
        assertFalse(HomeHistoryEntrySourceReference.ordinaryRenderReplayed)
        assertFalse(HomeHistoryEntrySourceReference.sameViewReplayed)
        val content = Any()
        val motion = prepared(content)
        motion.heatViewport(content, columns, 0L, 1f)
        motion.advance(ms(120), 1f)
        val before = motion.frame
        assertFalse(motion.mainView("HOME"))
        assertFalse(motion.render(content, true, true, true, 1f, "initial"))
        assertEquals(before, motion.frame)
        assertFalse(motion.heatViewport(content, HomeHeatColumns(0, 4), ms(120), 1f))
        assertEquals(before, motion.frame)
        motion.advance(ms(920), 1f)
        assertFalse(motion.running)
    }

    @Test fun realContentOrPeriodReplacementStopsTheOldEntryWithoutManufacturingAnother() {
        assertFalse(HomeHistoryEntrySourceReference.periodReplayed)
        val content = Any()
        val motion = prepared(content)
        motion.heatViewport(content, columns, 0L, 1f)
        motion.advance(ms(200), 1f)
        assertTrue(motion.running)
        // Same projection, actual period render: no new source animateChartsOnRender flag.
        assertTrue(motion.render(content, true, true, true, 1f, "month"))
        assertFalse(motion.running)
        assertEquals(1f, motion.frame.trendReveal, 0f)
        assertFalse(motion.heatViewport(content, columns, ms(200), 1f))
        val updatedContent = Any()
        assertTrue(motion.render(updatedContent, true, true, true, 1f, "month"))
        assertFalse(motion.heatViewport(content, columns, ms(210), 1f))
        assertFalse(motion.heatViewport(updatedContent, columns, ms(210), 1f))
        assertFalse(motion.frame.preparing)
    }

    @Test fun initialConnectionSettingsWaitForActualHomeDataAndSettledViewport() {
        val motion = HomeHistoryEntryMotion()
        motion.mainView("HOME")
        motion.render(null, false, false, false, 1f)
        assertTrue(motion.entryPending)
        val content = Any()
        motion.render(content, true, true, true, 1f)
        assertTrue(motion.frame.preparing)
        assertFalse(motion.running)
        assertFalse("An outdated layout callback cannot consume the current entry", motion.heatViewport(Any(), columns, 0L, 1f))
        assertTrue(motion.heatViewport(content, columns, 0L, 1f))
        assertFalse(motion.entryPending)
        assertTrue(motion.running)
    }

    @Test fun settingsAndStopSettleImmediatelyAndReturningDoesNotReplay() {
        val content = Any()
        val motion = prepared(content)
        motion.heatViewport(content, columns, 0L, 1f)
        motion.advance(ms(100), 1f)
        motion.render(content, true, false, true, 1f, "initial")
        assertFalse(motion.running)
        assertFalse(motion.entryPending)
        motion.render(content, true, true, true, 1f, "initial")
        assertFalse(motion.heatViewport(content, columns, ms(120), 1f))
        motion.stop()
        motion.render(content, true, true, true, 1f, "initial")
        assertFalse(motion.heatViewport(content, columns, ms(150), 1f))
        val waiting = prepared(content)
        waiting.stop()
        waiting.render(content, true, true, true, 1f, "initial")
        assertFalse("STOP cancels a pending layout too", waiting.heatViewport(content, columns, ms(150), 1f))
    }

    @Test fun emptyHomeThenSettingsAndFirstDataWhileHiddenPreservesTheUnconsumedInitialEntry() {
        val motion = HomeHistoryEntryMotion()
        motion.mainView("HOME")
        motion.render(null, available = false, visible = true, hasTrendStroke = false, scale = 1f)
        assertTrue("An actually shown empty Home has no real chart entry to consume", motion.entryPending)
        assertFalse(motion.frame.preparing)
        assertFalse(motion.running)
        motion.render(null, available = false, visible = false, hasTrendStroke = false, scale = 1f)
        assertTrue("Opening Settings cannot cancel an entry that never began", motion.entryPending)
        val content = Any()
        motion.render(content, available = true, visible = false, hasTrendStroke = true, scale = 1f)
        assertTrue("First data arriving while Settings hides the chart remains pending", motion.entryPending)
        assertFalse("A hidden/stale layout callback cannot consume the pending entry", motion.heatViewport(content, columns, ms(100), 1f))
        assertFalse(motion.running)
        assertFalse(motion.mainView("HOME"))
        motion.render(content, available = true, visible = true, hasTrendStroke = true, scale = 1f)
        assertTrue(motion.frame.preparing)
        assertTrue(motion.heatViewport(content, columns, ms(120), 1f))
        assertTrue(motion.running)
        assertFalse(motion.entryPending)
    }

    @Test fun emptyHomeSettingsRoundTripBeforeDataDoesNotLoseTheLaterFirstLayoutEntry() {
        val motion = HomeHistoryEntryMotion()
        motion.mainView("HOME")
        motion.render(null, false, true, false, 1f)
        motion.render(null, false, false, false, 1f)
        motion.render(null, false, true, false, 1f)
        assertTrue(motion.entryPending)
        val content = Any()
        motion.render(content, true, true, true, 1f)
        assertTrue(motion.heatViewport(content, columns, ms(100), 1f))
        assertFalse("Only the first real layout consumes the pending entry", motion.heatViewport(content, columns, ms(110), 1f))
    }

    @Test fun dataAvailableButNotYetLaidOutIsStillPendingAcrossSettings() {
        val content = Any()
        val motion = prepared(content)
        assertTrue(motion.frame.preparing)
        assertTrue(motion.entryPending)
        motion.render(content, true, false, true, 1f, "initial")
        assertTrue("Data availability alone cannot turn preparation into a consumed entry", motion.entryPending)
        assertFalse(motion.frame.preparing)
        assertFalse(motion.heatViewport(content, columns, ms(100), 1f))
        motion.render(content, true, true, true, 1f, "initial")
        assertTrue(motion.heatViewport(content, columns, ms(120), 1f))
        assertTrue(motion.running)
    }

    @Test fun anAlreadyConsumedEntryRemainsCancelledAfterSettingsEvenIfHiddenDataIsReplaced() {
        val content = Any()
        val motion = prepared(content)
        assertTrue(motion.heatViewport(content, columns, 0L, 1f))
        motion.advance(ms(100), 1f)
        assertTrue(motion.running)
        motion.render(content, true, false, true, 1f, "initial")
        assertFalse("Settings cancels the actual active entrance", motion.running)
        assertFalse(motion.entryPending)
        val replacement = Any()
        motion.render(replacement, true, false, true, 1f, "initial")
        motion.render(replacement, true, true, true, 1f, "initial")
        assertFalse("Replacing hidden content does not invent a new main-view entrance", motion.heatViewport(replacement, columns, ms(150), 1f))
        assertEquals(1f, motion.frame.trendReveal, 0f)

        val reduced = prepared(content, scale = 0f)
        assertTrue(reduced.heatViewport(content, columns, 0L, 0f))
        assertFalse(reduced.entryPending)
        reduced.render(content, true, false, true, 0f, "initial")
        reduced.render(content, true, true, true, 1f, "initial")
        assertFalse("A reduced-motion consumption also stays consumed on Settings return", reduced.heatViewport(content, columns, ms(160), 1f))
    }

    @Test fun aRealMainViewRoundTripRequestsAnotherEntryButReductionConsumesItWithoutFrames() {
        assertTrue(HomeHistoryEntrySourceReference.changedViewReplayed)
        assertTrue(HomeHistoryEntrySourceReference.reducedConsumedEntry)
        val content = Any()
        val motion = prepared(content)
        motion.heatViewport(content, columns, 0L, 1f)
        motion.mainView("TOOLS")
        motion.render(content, true, false, true, 1f)
        motion.mainView("HOME")
        motion.render(content, true, true, true, 1f)
        assertTrue(motion.heatViewport(content, columns, ms(100), 1f))
        motion.advance(ms(200), 0f)
        assertFalse(motion.running)
        assertEquals(1f, motion.frame.trendReveal, 0f)
        motion.mainView("TOOLS")
        motion.mainView("HOME")
        motion.render(content, true, true, true, 0f)
        assertTrue(motion.heatViewport(content, columns, ms(210), 0f))
        assertFalse(motion.entryPending)
        assertFalse(motion.running)
    }

    @Test fun positiveSystemScaleChangesDoNotRewindAndReducedPreferenceUsesExistingThreeStates() {
        assertEquals(0f, DesktopMotionPreference.scale("on", 1f), 0f)
        assertEquals(0f, DesktopMotionPreference.scale("system", 0f), 0f)
        assertEquals(1f, DesktopMotionPreference.scale("off", 0f), 0f)
        val content = Any()
        val motion = prepared(content)
        motion.heatViewport(content, columns, 0L, 1f)
        motion.advance(ms(230), 1f)
        val before = motion.frame
        motion.advance(ms(230), 2f)
        assertEquals(before, motion.frame)
        motion.advance(ms(460), 2f)
        assertTrue(motion.frame.trendReveal >= before.trendReveal)
        motion.advance(ms(1609), 2f)
        assertTrue(motion.running)
        motion.advance(ms(1610), 2f)
        assertFalse(motion.running)
    }

    @Test fun horizontalIntersectionUsesCellRectanglesInsteadOfTreatingGapsAsVisibleCells() {
        assertEquals(HomeHeatColumns(0, 0), HomeHistoryEntryMotion.visibleColumns(8, 1, 12f, 9f, 5))
        assertNull(HomeHistoryEntryMotion.visibleColumns(9, 1, 12f, 9f, 5))
        assertEquals(HomeHeatColumns(1, 3), HomeHistoryEntryMotion.visibleColumns(9, 31, 12f, 9f, 5))
        assertNull(HomeHistoryEntryMotion.visibleColumns(0, 0, 12f, 9f, 5))
        assertNull(HomeHistoryEntryMotion.visibleColumns(0, 1, Float.NaN, 9f, 5))
    }
}
