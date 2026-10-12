package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ConnectionDisplayTest {
    @Test fun originalHostSummaryOmitsCredentialsAndPathsAndNormalizesDefaultPorts() {
        val cases=Json.parseToJsonElement(javaClass.getResource("/connection-display-parity.json")!!.readText()).jsonObject.getValue("cases").jsonArray
        assertEquals(14,cases.size)
        cases.forEachIndexed {index,item->val row=item.jsonObject
            assertEquals("Public endpoint case $index",row.getValue("expected").jsonPrimitive.contentOrNull,ConnectionDisplay.endpoint(row.getValue("value").jsonPrimitive.content))
        }
        assertNull(ConnectionDisplay.endpoint(null))
    }
}
