package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AverageRateParityTest {
    @Test fun originalPeriodRatiosAndTitleFormatsStayIndependentOfLiveDeltaCounters() {
        val source=Json.parseToJsonElement(javaClass.getResource("/average-rate-parity.json")!!.readText()).jsonObject
        source.getValue("cases").jsonArray.forEach {row ->
            val c=row.jsonObject;val input=c.getValue("period").jsonObject
            val rate=HubParser.parse(buildJsonObject {put("periods",buildJsonObject {put("today",input)})}.toString()).periods.getValue("today").averageRate!!
            assertEquals(c.getValue("speed").jsonPrimitive.double,rate.speed,1e-9)
            assertEquals(c.getValue("burn").jsonPrimitive.double,rate.burn,1e-9)
            assertEquals(c.getValue("text").jsonPrimitive.contentOrNull,DesktopAverageRate.text(rate,c.getValue("mode").jsonPrimitive.content,c.getValue("units").jsonPrimitive.content,c.getValue("locale").jsonPrimitive.content))
        }
    }
}
