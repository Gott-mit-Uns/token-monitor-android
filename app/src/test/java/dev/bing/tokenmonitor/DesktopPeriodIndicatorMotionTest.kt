package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class DesktopPeriodIndicatorMotionTest {
    @Test fun oneSharedIndicatorMovesBetweenSlotsFor220msWithoutFirstMountOrSameSlotReplay() {
        val motion=DesktopPeriodIndicatorMotion(1)
        assertFalse(motion.frame.running);assertEquals(1f,motion.frame.index,0f)
        motion.render(2,true,0,1f)
        assertEquals(1f,motion.frame.index,0f)
        motion.advance(110_000_000)
        assertTrue(motion.frame.index in 1.9f..2f);assertTrue(motion.frame.running)
        val middle=motion.frame.index
        assertFalse(motion.render(2,true,110_000_000,1f))
        assertEquals(middle,motion.frame.index,0f)
        motion.advance(220_000_000)
        assertEquals(2f,motion.frame.index,0f);assertFalse(motion.frame.running)
    }
    @Test fun rapidReverseSamplesTheCurrentTransformAndUsesCssReversalShortening() {
        val motion=DesktopPeriodIndicatorMotion(0)
        motion.render(2,true,0,1f);motion.advance(30_000_000)
        val current=motion.frame.index
        motion.render(0,true,30_000_000,1f)
        assertEquals(current,motion.frame.index,0f)
        motion.advance(190_000_000)
        assertEquals(0f,motion.frame.index,0f);assertFalse(motion.frame.running)
    }
    @Test fun reductionHiddenChangesAndCoalescedStopResumeSettleWithoutReplaying() {
        val motion=DesktopPeriodIndicatorMotion(0)
        motion.render(1,true,0,1f);motion.advance(20_000_000)
        motion.stop();motion.render(2,true,30_000_000,1f)
        assertEquals(2f,motion.frame.index,0f);assertFalse(motion.frame.running)
        motion.render(0,false,40_000_000,1f);motion.render(0,true,50_000_000,1f)
        assertEquals(0f,motion.frame.index,0f);assertFalse(motion.frame.running)
        motion.render(1,true,60_000_000,0f)
        assertEquals(1f,motion.frame.index,0f);assertFalse(motion.frame.running)
        motion.render(2,true,70_000_000,1f)
        assertTrue(motion.frame.running)
    }
}
