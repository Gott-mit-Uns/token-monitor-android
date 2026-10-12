package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class StatusMotionParityTest {
    private val source=Json.parseToJsonElement(javaClass.getResource("/status-motion-parity.json")!!.readText()).jsonObject
    @Test fun originalSmilSegmentsAndNegativeBeginOffsetsMatchEveryBar() {
        source.getValue("spinner").jsonArray.forEach {row ->
            val f=row.jsonObject;val time=f.getValue("ms").jsonPrimitive.double
            f.getValue("alpha").jsonArray.forEachIndexed {index,value->assertEquals("Original SVG at $time bar $index",value.jsonPrimitive.float,DesktopSessionSpinner.alpha(time,index),.0001f)}
        }
    }
    @Test fun originalBrowserFlashUsesPerSegmentBezierAndSettlesAfter620Millis() {
        source.getValue("flash").jsonArray.forEach {row ->
            val f=row.jsonObject;val time=f.getValue("ms").jsonPrimitive.float;val actual=DesktopRateFlash.frame(time/620f)
            assertEquals("Scale at $time",f.getValue("scale").jsonPrimitive.float,actual.scale,.0001f)
            assertEquals("Blur at $time",f.getValue("blur").jsonPrimitive.float,actual.blur,.0005f)
            assertEquals("Opacity at $time",f.getValue("opacity").jsonPrimitive.float,actual.opacity,.0021f)
        }
    }
}
