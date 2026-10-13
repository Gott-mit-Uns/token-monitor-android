package dev.bing.tokenmonitor
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class SettingSwitchMotionTest {
    private val off=SettingSwitchPose(0f,Color.Black,Color.Gray,Color.White)
    private val on=SettingSwitchPose(1f,Color.Blue,Color.White,Color.Black)
    @Test fun reversalRetainsCurrentPositionAndAllColorsThenReachesNewTarget() {
        val motion=SettingSwitchMotion(off);motion.retarget(on,true);motion.frame(.3f)
        val displayed=motion.pose;assertTrue(displayed.position>0f && displayed.position<1f)
        motion.retarget(off,true);assertEquals(displayed,motion.pose)
        motion.frame(.2f);assertTrue(motion.pose.position<displayed.position)
        val reverse=motion.pose;motion.retarget(on,true);assertEquals(reverse,motion.pose)
        motion.frame(1f);assertEquals(on,motion.pose)
    }
    @Test fun disabledZeroScaleReducedMotionAndBackgroundPolicySettleWithoutReplay() {
        val motion=SettingSwitchMotion(off);motion.retarget(on,true);motion.frame(.2f)
        motion.retarget(off,false);assertEquals(off,motion.pose)
        motion.retarget(on,true);motion.frame(.2f);motion.settle(on);assertEquals(on,motion.pose)
        motion.frame(.4f);assertEquals(on,motion.pose)
        motion.retarget(on,true);assertFalse(motion.pose.running)
    }
}
