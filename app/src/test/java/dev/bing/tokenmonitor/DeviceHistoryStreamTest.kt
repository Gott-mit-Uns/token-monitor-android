package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.CancellationException

class DeviceHistoryStreamTest {
    private fun project(body: String, signature: String = "public-device-r1") =
        DeviceHistoryStream.project(ByteArrayInputStream(body.toByteArray()), signature)

    @Test fun streamedInventoryMatchesExistingRetainedCalendarAndKeepsExactAttribution() {
        val body = """{"devices":[{"deviceId":"public-1","historyAvailable":true,"unrelated":{"large":"discard"},"history":{"monthly":[{"month":"2026-10"}],"daily":[{"date":"2026-10-09","tokens":5000000123,"cost":0.000000000123,"unpricedTokens":17,"cacheReadTokens":16,"outputTokens":7,"tokenComponentsAvailable":true,"perClient":{"codex":{"tokens":5000000123,"cost":0.000000000123,"unrelated":"discard"}},"perModel":{"public-model":{"tokens":5000000123,"cost":0.000000000123}},"unrelated":"discard"}]}}],"unrelated":{"large":"discard"}}"""
        val streamed = project(body)
        assertEquals(DeviceHistoryParser.parse(DeviceHistoryParser.project(body, "public-device-r1")), DeviceHistoryParser.parse(streamed))
        assertFalse(streamed.contains("unrelated"))
        assertFalse(streamed.contains("monthly"))
        val row = DeviceHistoryParser.parse(streamed).devices.single().history!!.daily.single()
        assertEquals(5000000123L, row.tokens)
        assertEquals("0.000000000123", row.cost)
        assertEquals(5000000123L, row.perModel.getValue("public-model").tokens)
    }

    @Test fun missingNullAndExplicitEmptyHistoryHaveDistinctAvailability() {
        val body = """[{"id":"fallback","historyAvailable":true,"history":{"daily":[]}},{"deviceId":"none","historyAvailable":false,"history":{"daily":[]}},{"deviceId":"missing","historyAvailable":true},{"deviceId":"null","historyAvailable":true,"history":null},{"deviceId":null,"id":"ignored","historyAvailable":true,"history":{"daily":[]}}]"""
        val old = DeviceHistoryParser.parse(DeviceHistoryParser.project(body, "public-device-r1"))
        val next = DeviceHistoryParser.parse(project(body))
        assertEquals(old, next)
        assertEquals(listOf(true, false, false, false), next.devices.map { it.available })
        assertTrue(next.devices.first().history!!.daily.isEmpty())
    }

    @Test fun inputFieldOrderAndUnknownNestedInventoryDoNotChangeRetainedData() {
        val body = """{"ignored":[{"nested":[1,2,3]}],"devices":[{"history":{"ignored":[],"daily":[null,1,{"perModel":{"m":{"messages":2,"tokens":10}},"cost":0.125,"tokens":10,"date":"2026-10-09"}]},"historyAvailable":"true","id":" fallback "}]}"""
        assertEquals(DeviceHistoryParser.parse(DeviceHistoryParser.project(body, "public-device-r1")), DeviceHistoryParser.parse(project(body)))
    }

    @Test fun utf8ByteLimitAndCancellationCoverIgnoredDataBeforePublication() {
        val body = """{"devices":[],"ignored":"中文🙂"}""".toByteArray()
        assertEquals(emptyList<DeviceHistory>(), DeviceHistoryParser.parse(DeviceHistoryStream.project(
            HubCheckedInputStream(ByteArrayInputStream(body), body.size.toLong()), "r")).devices)
        assertTrue(runCatching { DeviceHistoryStream.project(HubCheckedInputStream(ByteArrayInputStream(body), body.size.toLong()-1), "r") }.isFailure)
        var reads = 0
        val result = runCatching { DeviceHistoryStream.project(HubCheckedInputStream(ByteArrayInputStream(body), 1024) { ++reads < 2 }, "r") }
        assertTrue(result.exceptionOrNull() is CancellationException)
    }

    @Test fun malformedInventoryUtf8AndTrailingDataCannotReturnPartialSuccess() {
        for (body in listOf("{}", "{\"devices\":null}", "{\"devices\":[]} {}", "{\"devices\":[{\"deviceId\":\"one\",\"historyAvailable\":true,\"history\":{\"daily\":[{}]}}]}"))
            assertTrue(runCatching { project(body) }.isFailure)
        for (date in listOf("null", "12", "\"2026-02-30\"", "\"invalid\""))
            assertTrue(runCatching { project("[{\"id\":\"one\",\"historyAvailable\":true,\"history\":{\"daily\":[{\"date\":$date}]}}]") }.isFailure)
        assertTrue(runCatching { DeviceHistoryStream.project(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28)), "r") }.isFailure)
    }

    @Test fun duplicateSelectedFieldsAndExcessiveUnknownDepthAreRejected() {
        for (body in listOf("{\"devices\":[],\"devices\":[]}", "[{\"id\":\"first\",\"id\":\"second\"}]", "[{\"id\":\"one\",\"historyAvailable\":true,\"history\":{\"daily\":[{\"tokens\":1,\"tokens\":2}]}}]"))
            assertTrue(runCatching { project(body) }.isFailure)
        val deep = "{\"devices\":[],\"ignored\":" + "[".repeat(130) + "0" + "]".repeat(130) + "}"
        assertTrue(runCatching { project(deep) }.isFailure)
    }
}
