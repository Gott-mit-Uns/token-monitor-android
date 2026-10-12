package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class DesktopDisclosureMotionTest {
    private fun ms(value: Long) = value * 1_000_000L

    @Test fun accordionUsesSeparateSourceCurvesAndEndsAt250ms() {
        val motion = DesktopDisclosureMotion(false, DesktopDisclosureStyle.ACCORDION)
        motion.retarget(true, true, ms(100))
        assertTrue(motion.pose.running)
        val first = motion.frame(ms(116))
        assertTrue("The first frame includes request-to-frame elapsed", first.heightFraction > 0f)
        val middle = motion.frame(ms(225))
        assertTrue(middle.heightFraction in 0f..1f && middle.opacity in 0f..1f)
        assertNotEquals("CSS opacity ease is distinct from grid-height ease", middle.heightFraction, middle.opacity)
        assertTrue(motion.frame(ms(349)).running)
        val end = motion.frame(ms(350))
        assertEquals(1f, end.heightFraction, 0f)
        assertEquals(1f, end.opacity, 0f)
        assertEquals(1f, end.chevronFraction, 0f)
        assertFalse(end.running)
    }

    @Test fun settingsPanelOpacityEndsAt120msHeightAndChevronAt140ms() {
        val motion = DesktopDisclosureMotion(false, DesktopDisclosureStyle.SETTINGS_PANEL)
        motion.retarget(true, true, ms(5))
        val at120 = motion.frame(ms(125))
        assertEquals(1f, at120.opacity, 0f)
        assertTrue(at120.heightFraction < 1f && at120.running)
        assertFalse(motion.frame(ms(145)).running)
        motion.retarget(false, true, ms(200))
        assertTrue(motion.retained)
        val closing = motion.frame(ms(240))
        assertTrue(closing.heightFraction > 0f && closing.heightFraction < 1f)
        assertTrue(motion.retained)
        motion.frame(ms(340))
        assertFalse(motion.retained)
        assertEquals(0f, motion.pose.opacity, 0f)
    }

    @Test fun reversalSamplesNowAndShortensWithoutJumpOrStaleFrame() {
        val motion = DesktopDisclosureMotion(false, DesktopDisclosureStyle.ACCORDION)
        motion.retarget(true, true, ms(100))
        val at116 = motion.frame(ms(116))
        // Intentionally do not publish the elapsed 40ms frame before reversing.
        val expected = DesktopDisclosureMotion(false, DesktopDisclosureStyle.ACCORDION)
        expected.retarget(true, true, ms(100))
        val at140 = expected.frame(ms(140))
        motion.retarget(false, true, ms(140))
        assertEquals(at140.heightFraction, motion.pose.heightFraction, 0f)
        assertEquals(at140.opacity, motion.pose.opacity, 0f)
        assertTrue(motion.pose.heightFraction > at116.heightFraction)
        val close20 = motion.frame(ms(160))
        assertTrue(close20.heightFraction < at140.heightFraction)
        motion.retarget(true, true, ms(160))
        assertEquals(close20.heightFraction, motion.pose.heightFraction, 0f)
        val later = motion.frame(ms(180))
        assertTrue(later.heightFraction >= close20.heightFraction)
        motion.frame(ms(500))
        assertEquals(1f, motion.pose.heightFraction, 0f)
    }

    @Test fun repeatTargetDoesNotRestartAndExistingVisibleInitiallyOpenIsStatic() {
        val initial = DesktopDisclosureMotion(true, DesktopDisclosureStyle.ACCORDION)
        assertFalse(initial.pose.running)
        assertEquals(1f, initial.pose.heightFraction, 0f)
        val motion = DesktopDisclosureMotion(false, DesktopDisclosureStyle.ACCORDION)
        motion.retarget(true, true, ms(0))
        motion.frame(ms(100))
        motion.retarget(true, true, ms(150))
        assertFalse(motion.frame(ms(250)).running)
    }

    @Test fun offOverridesSystemZeroWhileOnAndStopSettleWithoutReplay() {
        val offScale = DesktopMotionPreference.scale("off", 0f)
        val motion = DesktopDisclosureMotion(false, DesktopDisclosureStyle.ACCORDION)
        motion.retarget(true, offScale > 0f, ms(0), offScale)
        assertTrue(motion.frame(ms(40)).running)
        motion.settle()
        assertEquals(1f, motion.pose.heightFraction, 0f)
        motion.retarget(true, true, ms(300))
        assertFalse(motion.pose.running)
        val onScale = DesktopMotionPreference.scale("on", 1f)
        motion.retarget(false, onScale > 0f, ms(400), onScale)
        assertFalse(motion.retained)
        assertEquals(0f, motion.pose.heightFraction, 0f)
        val systemZero = DesktopMotionPreference.scale("system", 0f)
        motion.retarget(true, systemZero > 0f, ms(500), systemZero)
        assertEquals(1f, motion.pose.heightFraction, 0f)
        assertFalse(motion.pose.running)
    }

    @Test fun nonzeroSystemScaleKeepsTheSameCurvesAndScalesTime() {
        val normal = DesktopDisclosureMotion(false, DesktopDisclosureStyle.ACCORDION)
        val slow = DesktopDisclosureMotion(false, DesktopDisclosureStyle.ACCORDION)
        normal.retarget(true, true, ms(0), 1f)
        slow.retarget(true, true, ms(0), 2f)
        assertEquals(normal.frame(ms(80)).heightFraction, slow.frame(ms(160)).heightFraction, 0f)
        assertTrue(slow.frame(ms(499)).running)
        assertFalse(slow.frame(ms(500)).running)
    }
}
