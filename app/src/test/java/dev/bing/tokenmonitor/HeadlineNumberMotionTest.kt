package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class HeadlineNumberMotionTest {
    @Test fun desktopQuartSamplesInBothDirectionsAndFinalEndpoint() {
        val rising = HeadlineNumberMotion(1000)
        rising.retarget(2000,true)
        rising.frame(0.25);assertEquals(1684L,rising.displayed)
        rising.frame(0.5);assertEquals(1938L,rising.displayed)
        rising.frame(1.0);assertEquals(2000L,rising.displayed);assertFalse(rising.running)
        val falling = HeadlineNumberMotion(2000)
        falling.retarget(1000,true)
        falling.frame(0.25);assertEquals(1316L,falling.displayed);assertEquals(2000L,falling.widest)
        falling.frame(1.0);assertEquals(1000L,falling.widest)
    }
    @Test(timeout=3000) fun interruptionContinuesFromFractionalFrameAndIdenticalTargetKeepsProgress() {
        val motion = HeadlineNumberMotion(1000)
        motion.retarget(2000,true);motion.frame(0.25)
        assertFalse(motion.retarget(2000,true));assertEquals(1684L,motion.displayed)
        motion.retarget(3000,true);assertEquals(1684L,motion.displayed)
        motion.frame(0.5);assertEquals(2918L,motion.displayed)
        motion.retarget(0,true);assertEquals(2918L,motion.displayed)
        motion.frame(1.0);assertEquals(0L,motion.displayed)
        // A long foreground stream can retarget every frame without accumulating decimal tails.
        repeat(3000) {index ->
            val previous=motion.displayed!!
            motion.retarget((index+1)*1000L,true);motion.frame(0.25)
            assertTrue(motion.displayed!!>=previous)
        }
        motion.frame(1.0);assertEquals(3000000L,motion.displayed)
    }
    @Test fun firstAvailableSnapshotStartsFromSourceZeroAndUnavailablePeriodCancels() {
        val motion = HeadlineNumberMotion(null)
        assertTrue(motion.retarget(1000,true));assertEquals(0L,motion.displayed);assertTrue(motion.running)
        assertEquals(1000L,motion.widest)
        motion.frame(0.25);assertEquals(684L,motion.displayed)
        motion.retarget(2000,true);motion.frame(0.2)
        motion.retarget(null,true);assertNull(motion.displayed);assertFalse(motion.running)
        motion.frame(0.7);assertNull(motion.displayed)
        // The source resets currentTotal, but leaves its private fractional value.
        // A later available period must restart from the reset baseline, not that fraction.
        assertTrue(motion.retarget(1000,true));assertEquals(0L,motion.displayed)
        motion.frame(0.25);assertEquals(684L,motion.displayed)
    }
    @Test fun knownUnavailableDetailRetainsSourceStaticBaseline() {
        val motion = HeadlineNumberMotion(1000)
        motion.retarget(2000,true);motion.frame(0.25)
        motion.unavailable(2400)
        assertEquals(2400L,motion.displayed);assertEquals(2400L,motion.widest);assertFalse(motion.running)
        motion.frame(0.75);assertEquals(2400L,motion.displayed)
        assertTrue(motion.retarget(3400,true));assertEquals(2400L,motion.displayed)
        motion.frame(0.25);assertEquals(3084L,motion.displayed)
    }
    @Test fun coldZeroAndReducedInitialCountsHaveNoPendingTween() {
        val zero = HeadlineNumberMotion(null)
        assertFalse(zero.retarget(0,true));assertEquals(0L,zero.displayed);assertFalse(zero.running)
        val reduced = HeadlineNumberMotion(null)
        assertFalse(reduced.retarget(1000,false));assertEquals(1000L,reduced.displayed);assertFalse(reduced.running)
    }
    @Test fun firstFrameUsesTimeSinceCallRatherThanStartingItsOwnClock() {
        // Original app.js oracle: call at 100ms, first RAF at 116ms, 350ms, 1100ms.
        val timeline = HeadlineNumberTimeline()
        timeline.start(100_000_000L,1000)
        val motion = HeadlineNumberMotion(null)
        motion.retarget(1000,true)
        assertEquals(0.016,timeline.progress(116_000_000L,1f),0.0000001)
        motion.frame(timeline.progress(116_000_000L,1f));assertEquals(62L,motion.displayed)
        motion.frame(timeline.progress(350_000_000L,1f));assertEquals(684L,motion.displayed)
        motion.frame(timeline.progress(1_100_000_000L,1f));assertEquals(1000L,motion.displayed);assertFalse(motion.running)
        // Zero is a valid monotonic start, not an uninitialized timeline sentinel.
        timeline.start(0L,800)
        assertEquals(0.5,timeline.progress(400_000_000L,1f),0.0)
        assertEquals(0.25,timeline.progress(400_000_000L,2f),0.0)
        assertEquals(1.0,timeline.progress(800_000_000L,1f),0.0)
        assertEquals(1.0,timeline.progress(1L,0f),0.0)
    }
    @Test fun reducedMotionAndBackgroundCancellationPreventOldFramesOverwritingNewData() {
        val motion = HeadlineNumberMotion(1000)
        motion.retarget(2000,true);motion.frame(0.2);motion.settle()
        assertEquals(2000L,motion.displayed);assertFalse(motion.running)
        motion.retarget(0,false);motion.frame(0.9);assertEquals(0L,motion.displayed)
    }
    @Test fun largeCountsKeepExactEndpointAndSingleTokenChangesBeyondDoublePrecision() {
        val motion = HeadlineNumberMotion(Long.MAX_VALUE-1)
        motion.retarget(Long.MAX_VALUE,true);motion.frame(0.25)
        assertEquals(Long.MAX_VALUE,motion.displayed)
        motion.frame(1.0);assertEquals(Long.MAX_VALUE,motion.displayed)
        motion.retarget(0,true);motion.frame(0.5)
        assertEquals(576460752303423488L,motion.displayed)
        motion.frame(1.0);assertEquals(0L,motion.displayed)
    }
}
