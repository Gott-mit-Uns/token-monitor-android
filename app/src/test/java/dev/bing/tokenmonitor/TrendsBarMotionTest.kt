package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

/** Timing and key cases come from the executed v0.68.0 source in evidence/desktop-source-motion/reference. */
class TrendsBarMotionTest {
    private fun ms(value: Long) = value * 1_000_000L
    private fun bar(key: String, height: Double) = TrendsBarSpec(key, height)
    private fun seed(vararg bars: TrendsBarSpec) = TrendsBarMotion().also {
        it.render(bars.toList(), "seed", 0L, animate = false)
        assertFalse(it.entryPending)
    }
    private fun height(motion: TrendsBarMotion, index: Int = 0) =
        motion.frame.targets[index].height * motion.frame.scales[index].toDouble()
    private fun scales(motion: TrendsBarMotion, vararg expected: Float) {
        assertEquals(expected.size, motion.frame.scales.size)
        expected.forEachIndexed { index, value ->
            assertEquals("scale at $index", value, motion.frame.scales[index], 0.0001f)
        }
    }

    @Test fun entryUses420msAnd14msStaggerCappedAt196ms() {
        val motion = TrendsBarMotion()
        val targets = (0..16).map { bar("day-$it", if (it == 5) 0.0 else 120.0) }
        motion.render(targets, "entry", ms(1000), animate = true)
        assertFalse(motion.entryPending)
        assertTrue(motion.running)
        assertEquals(targets, motion.frame.targets)
        assertTrue(motion.frame.scales.all { it == 0f })
        motion.advance(ms(1013))
        assertTrue(motion.frame.scales[0] > 0f)
        assertEquals(0f, motion.frame.scales[1], 0f)
        motion.advance(ms(1014))
        assertEquals(0f, motion.frame.scales[1], 0f)
        motion.advance(ms(1015))
        assertTrue(motion.frame.scales[1] > 0f)
        motion.advance(ms(1100))
        // The source (.22,1,.36,1) curve rises about .746 at 100/420; linear
        // or headline quart easing would fall outside this deliberately broad interval.
        assertTrue(motion.frame.scales[0] > .73f && motion.frame.scales[0] < .76f)
        motion.advance(ms(1420))
        assertEquals(1f, motion.frame.scales[0], 0f)
        assertTrue(motion.running)
        assertEquals(motion.frame.scales[14], motion.frame.scales[16], 0f)
        motion.advance(ms(1615))
        assertTrue(motion.running)
        motion.advance(ms(1616))
        assertFalse(motion.running)
        assertTrue(motion.frame.scales.all { it == 1f })
    }

    @Test fun interruptionCapturesCurrentHeightAndOnlyNewDataEventRestartsSameTarget() {
        val motion = seed(bar("day", 120.0))
        motion.render(listOf(bar("day", 60.0)), "update-1", ms(1000), animate = true)
        motion.advance(ms(1120))
        val interrupted = height(motion)
        assertTrue(interrupted > 60.0 && interrupted < 120.0)
        motion.render(listOf(bar("day", 90.0)), "update-2", ms(1120), animate = true)
        assertEquals(interrupted, height(motion), 0.0001)
        motion.advance(ms(1500))
        val beforeSameEvent = motion.frame
        motion.render(listOf(bar("day", 90.0)), "update-2", ms(1500), animate = true)
        assertEquals(beforeSameEvent, motion.frame)
        motion.advance(ms(1540))
        assertFalse("same event must retain its original deadline", motion.running)
        assertEquals(90.0, height(motion), 0.0)

        val rerender = seed(bar("day", 120.0))
        rerender.render(listOf(bar("day", 60.0)), "first", ms(1000), animate = true)
        rerender.advance(ms(1120))
        val current = height(rerender)
        rerender.render(listOf(bar("day", 60.0)), "second", ms(1120), animate = true)
        assertEquals(current, height(rerender), 0.0001)
        rerender.advance(ms(1420))
        assertTrue("distinct data render restarts from captured height", rerender.running)
        rerender.advance(ms(1540))
        assertFalse(rerender.running)
        assertEquals(60.0, height(rerender), 0.0)
    }

    @Test fun datesSurviveReorderingRemovedKeysHaveNoExitAndFallingScaleCanExceedOne() {
        val motion = seed(bar("a", 120.0), bar("b", 30.0), bar("removed", 90.0))
        val targets = listOf(bar("b", 60.0), bar("a", 60.0), bar("new", 120.0))
        motion.render(targets, "new-period", ms(1000), animate = true)
        assertEquals(targets, motion.frame.targets)
        scales(motion, 0.5f, 2f, 0f)
        motion.advance(ms(1027))
        assertEquals(0f, motion.frame.scales[2], 0f)
        motion.advance(ms(1029))
        assertTrue(motion.frame.scales[2] > 0f)
        motion.advance(ms(1448))
        assertFalse(motion.running)
        scales(motion, 1f, 1f, 1f)
        val epsilon = seed(bar("day", 100.0))
        epsilon.render(listOf(bar("day", 100.05)), "near", ms(1000), animate = true)
        assertFalse("source skips ratios within .001 of the endpoint", epsilon.running)
        scales(epsilon, 1f)
        epsilon.render(listOf(bar("day", 100.25)), "outside", ms(1010), animate = true)
        assertTrue(epsilon.running)
    }

    @Test fun existingZeroKeepsKeyAndNewZeroStillOwnsAnInvisibleTween() {
        val motion = seed(bar("old-zero", 0.0), bar("positive", 120.0), bar("still-zero", 0.0))
        val targets = listOf(bar("old-zero", 90.0), bar("positive", 0.0), bar("still-zero", 0.0), bar("new-zero", 0.0))
        motion.render(targets, "update", ms(1000), animate = true)
        scales(motion, 0f, 1f, 1f, 0f)
        motion.advance(ms(1001))
        assertTrue("existing zero rises without stagger", motion.frame.scales[0] > 0f)
        assertEquals(0f, motion.frame.scales[3], 0f)
        motion.advance(ms(1420))
        assertTrue("the invisible new zero waits 42ms then runs 420ms", motion.running)
        assertFalse("zero-only work needs no Canvas frame loop", motion.hasVisibleMotion)
        assertEquals(0.0, height(motion, 3), 0.0)
        motion.advance(ms(1462))
        assertFalse(motion.running)
        scales(motion, 1f, 1f, 1f, 1f)
    }

    @Test fun emptyUnavailableAndHiddenRendersDoNotConsumePendingEntry() {
        val motion = TrendsBarMotion()
        motion.render(emptyList(), "empty", ms(1000), animate = true)
        assertTrue(motion.entryPending)
        assertTrue(motion.frame.targets.isEmpty())
        assertFalse(motion.running)
        val targets = listOf(bar("day", 120.0))
        motion.render(targets, "snapshot", ms(1010), animate = false, consumeEntry = false)
        assertTrue(motion.entryPending)
        scales(motion, 1f)
        motion.render(null, "waiting", ms(1020), animate = false, consumeEntry = false)
        assertEquals(targets, motion.frame.targets)
        assertTrue(motion.entryPending)
        motion.render(targets, "snapshot", ms(1030), animate = true)
        scales(motion, 0f)
        assertFalse(motion.entryPending)
        motion.advance(ms(1100))
        assertTrue(motion.running)
        motion.render(null, "unavailable", ms(1110), animate = true)
        assertEquals(targets, motion.frame.targets)
        assertFalse(motion.running)
        assertFalse(motion.entryPending)
        scales(motion, 1f)
        motion.mainView("home")
        motion.render(emptyList(), "empty-again", ms(1120), animate = true)
        assertTrue(motion.entryPending)
        assertTrue(motion.frame.targets.isEmpty())
    }

    @Test fun sameMainViewAndPeriodRenderDoNotReplayEntryButReturningFromAnotherViewDoes() {
        val motion = TrendsBarMotion()
        motion.mainView("trends")
        motion.render(listOf(bar("day", 120.0)), "seed", 0L, animate = false)
        motion.mainView("trends")
        assertFalse(motion.entryPending)
        motion.render(listOf(bar("day", 60.0)), "period-change", ms(1000), animate = true)
        scales(motion, 2f)
        motion.advance(ms(1100))
        val frame = motion.frame
        // A details return still belongs to the same main view, with the same data event.
        motion.mainView("trends")
        motion.render(frame.targets, "period-change", ms(1100), animate = true)
        assertEquals(frame, motion.frame)
        assertFalse(motion.entryPending)
        motion.mainView("home")
        motion.mainView("trends")
        assertTrue(motion.entryPending)
        motion.render(frame.targets, "period-change", ms(1100), animate = true)
        scales(motion, 0f)
        assertFalse(motion.entryPending)
    }

    @Test fun reducedMotionAndExplicitSettleCancelFramesAndDurationScaleChangesDeadline() {
        val motion = seed(bar("day", 120.0))
        motion.render(listOf(bar("day", 60.0)), "slow", ms(1000), animate = true, durationScale = 2f)
        motion.advance(ms(1420), durationScale = 2f)
        assertTrue(motion.running)
        assertTrue(motion.frame.scales[0] > 1f && motion.frame.scales[0] < 2f)
        motion.advance(ms(1840), durationScale = 2f)
        assertFalse(motion.running)
        scales(motion, 1f)
        motion.render(listOf(bar("day", 120.0)), "reduced", ms(2000), animate = false)
        scales(motion, 1f)
        assertFalse(motion.running)
        motion.render(listOf(bar("day", 60.0)), "zero-scale", ms(2100), animate = true, durationScale = 0f)
        assertFalse(motion.running)
        scales(motion, 1f)
        motion.render(listOf(bar("day", 120.0)), "active", ms(2200), animate = true)
        motion.advance(ms(2300))
        assertTrue(motion.running)
        motion.advance(ms(2310), durationScale = 0f)
        assertFalse(motion.running)
        scales(motion, 1f)
        motion.render(listOf(bar("day", 60.0)), "active-again", ms(2400), animate = true)
        motion.advance(ms(2500))
        assertTrue(motion.running)
        motion.settle()
        assertFalse(motion.running)
        scales(motion, 1f)
        motion.advance(ms(9999))
        assertEquals(60.0, height(motion), 0.0)
    }

    @Test fun targetHeightsUseRounded120CoordinatesKeepZeroDatesAndFallbackKeys() {
        val points = listOf(TrendsMainPoint("a", 1), TrendsMainPoint("zero", 0), TrendsMainPoint("peak", 7), TrendsMainPoint("", 2))
        assertEquals(listOf(bar("a", 17.14), bar("zero", 0.0), bar("peak", 120.0), bar("3", 34.29)), TrendsBarMotion.targets(points))
        // Original svgRound uses Math.round: positive half hundredths round upward.
        assertEquals(listOf(bar("tiny", 0.01), bar("peak", 120.0)), TrendsBarMotion.targets(listOf(TrendsMainPoint("tiny", 1), TrendsMainPoint("peak", 24_000))))
        assertEquals(emptyList<TrendsBarSpec>(), TrendsBarMotion.targets(emptyList()))
        assertEquals(listOf(bar("zero", 0.0), bar("negative", 0.0)), TrendsBarMotion.targets(listOf(TrendsMainPoint("zero", 0), TrendsMainPoint("negative", -7))))
        assertEquals(listOf(bar("large", 120.0), bar("half", 60.0)), TrendsBarMotion.targets(listOf(TrendsMainPoint("large", Long.MAX_VALUE), TrendsMainPoint("half", Long.MAX_VALUE / 2))))
    }
}
