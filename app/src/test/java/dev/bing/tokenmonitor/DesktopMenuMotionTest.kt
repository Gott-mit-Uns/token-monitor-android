package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DesktopMenuMotionTest {
    @Test fun actualOriginalCssFramesAndInterruptedReversalMatch() {
        val reference=Json.parseToJsonElement(javaClass.getResource("/menu-motion-reference.json")!!.readText()).jsonObject
        reference.forEach {(kind,steps)->val motion=DesktopMenuMotion()
            steps.jsonArray.forEachIndexed {index,row->val s=row.jsonObject
                when(s.getValue("action").jsonPrimitive.content) {
                    "open"->motion.retarget(true,true)
                    "close"->motion.retarget(false,true)
                    else->motion.frame(s.getValue("time").jsonPrimitive.float/motion.durationMs)
                }
                assertEquals("$kind opacity $index",s.getValue("opacity").jsonPrimitive.float,motion.value,.002f)
                assertEquals("$kind duration $index",s.getValue("duration").jsonPrimitive.float,motion.durationMs,.3f)
                assertEquals("$kind scale $index",s.getValue("scale").jsonPrimitive.float,1f-.015f*(1f-motion.value),.0001f)
                assertEquals("$kind translation $index",s.getValue("translateY").jsonPrimitive.float,(if(kind=="navigation") 6f else -6f)*(1f-motion.value),.02f)
                assertEquals("$kind blur $index",s.getValue("blur").jsonPrimitive.float,2f*(1f-motion.value),.005f)
            }
        }
    }
    @Test fun reductionAndForegroundSettlementDoNotReplayOrJump() {
        val motion=DesktopMenuMotion();motion.retarget(true,true);motion.frame(.25f)
        val shown=motion.value;motion.retarget(false,true);assertEquals(shown,motion.value,0f)
        motion.frame(.25f);motion.retarget(true,false);assertEquals(1f,motion.value,0f);assertFalse(motion.running)
        motion.retarget(false,false);assertEquals(0f,motion.value,0f)
        motion.retarget(true,true);motion.frame(.3f);motion.settle(false)
        motion.retarget(false,true);assertEquals(0f,motion.value,0f);assertFalse(motion.running)
    }
}
