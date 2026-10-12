package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class SessionContextMeterMotionTest {
    @Test fun firstRenderedTargetIsStaticAndRetainedTargetChangesUse420ms() {
        val motion=SessionContextMeterMotion(.75f)
        assertEquals(.75f,motion.frame.scale,0f);assertFalse(motion.frame.running)
        assertFalse(motion.render(.75f,true,0,1f))
        motion.render(.25f,true,10_000_000L,1f)
        assertEquals(.75f,motion.frame.scale,0f)
        motion.advance(220_000_000L)
        assertTrue(motion.frame.running);assertTrue(motion.frame.scale>.25f && motion.frame.scale<.30f)
        motion.advance(430_000_000L)
        assertEquals(.25f,motion.frame.scale,0f);assertFalse(motion.frame.running)
    }

    @Test fun unchangedPercentageAndHeaderOrMetricRecommitCannotRestartTheTimeline() {
        val motion=SessionContextMeterMotion(.25f)
        motion.render(.75f,true,0,1f);motion.advance(50_000_000L)
        val middle=motion.frame.scale
        assertFalse(motion.render(.75f,true,50_000_000L,1f))
        assertEquals(middle,motion.frame.scale,0f)
        motion.advance(420_000_000L)
        assertEquals(.75f,motion.frame.scale,0f);assertFalse(motion.frame.running)
        // Used=remaining=50 writes the same CSS transform: preference identity alone has no tween.
        motion.render(.5f,true,500_000_000L,1f);motion.advance(920_000_000L)
        assertFalse(motion.render(.5f,true,930_000_000L,1f));assertFalse(motion.frame.running)
    }

    @Test fun rapidReverseSamplesActualCurrentScaleAndShortensLikeCss() {
        val motion=SessionContextMeterMotion(.25f)
        motion.render(.75f,true,0,1f);motion.advance(30_000_000L)
        val middle=motion.frame.scale
        assertTrue(middle>.25f && middle<.75f)
        motion.render(.25f,true,30_000_000L,1f)
        assertEquals(middle,motion.frame.scale,0f)
        motion.advance(250_000_000L)
        assertEquals("CSS reversal finishes before a fresh 420ms reverse",.25f,motion.frame.scale,0f)
        assertFalse(motion.frame.running)
    }

    @Test fun unrelatedNewTargetStartsAtCurrentScaleAndHonorsDurationScale() {
        val motion=SessionContextMeterMotion(.25f)
        motion.render(.75f,true,0,1f);motion.advance(100_000_000L)
        val middle=motion.frame.scale
        motion.render(.9f,true,100_000_000L,2f)
        assertEquals(middle,motion.frame.scale,0f)
        motion.advance(520_000_000L)
        assertTrue(motion.frame.running);assertTrue(motion.frame.scale>middle && motion.frame.scale<.9f)
        motion.advance(940_000_000L)
        assertEquals(.9f,motion.frame.scale,0f);assertFalse(motion.frame.running)
    }

    @Test fun hiddenUpdatesReductionAndCoalescedStopResumeSettleWithoutReplay() {
        val motion=SessionContextMeterMotion(.75f)
        motion.render(.25f,true,0,1f);motion.advance(30_000_000L)
        motion.stop()
        motion.render(.5f,true,40_000_000L,1f)
        assertEquals(.5f,motion.frame.scale,0f);assertFalse(motion.frame.running)
        motion.render(.9f,false,50_000_000L,1f)
        motion.render(.8f,true,60_000_000L,1f)
        assertEquals(.8f,motion.frame.scale,0f);assertFalse(motion.frame.running)
        motion.render(.2f,true,70_000_000L,0f)
        assertEquals(.2f,motion.frame.scale,0f);assertFalse(motion.frame.running)
        motion.render(.7f,true,80_000_000L,1f)
        assertTrue(motion.frame.running)
        motion.advance(90_000_000L,0f)
        assertEquals(.7f,motion.frame.scale,0f);assertFalse(motion.frame.running)
        motion.render(.6f,true,100_000_000L,1f)
        assertEquals(.6f,motion.frame.scale,0f);assertFalse(motion.frame.running)
    }

    @Test fun missingGaugeOrLazyRowRemountUsesTheLatestTargetWithoutAnEntry() {
        val old=SessionContextMeterMotion(.75f)
        old.render(.9f,true,0,1f);old.advance(30_000_000L);old.stop()
        // A missing/disposed gauge has no rendered before-change CSS style.
        val new=SessionContextMeterMotion(.4f)
        assertFalse(new.render(.4f,true,60_000_000L,1f))
        assertEquals(.4f,new.frame.scale,0f);assertFalse(new.frame.running)
        val invalid=SessionContextMeterMotion(Float.NaN)
        invalid.render(Float.POSITIVE_INFINITY,true,0,1f)
        assertEquals(0f,invalid.frame.scale,0f)
    }
}
