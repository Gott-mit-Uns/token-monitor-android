package dev.bing.tokenmonitor
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
class StreamTest {
    @Test fun retryAfterSupportsSecondsDatesAndRejectsMalformedValues() {
        val now = java.time.Instant.parse("2026-10-09T00:00:00Z")
        assertEquals(120000L, HubRetryAfter.parse("120", now))
        assertEquals(90000L, HubRetryAfter.parse("Fri, 9 Oct 2026 00:01:30 GMT", now))
        assertEquals(0L, HubRetryAfter.parse("Thu, 8 Oct 2026 23:59:00 GMT", now))
        assertEquals(999999999000L, HubRetryAfter.parse("999999999", now))
        assertNull(HubRetryAfter.parse("-1", now))
        assertNull(HubRetryAfter.parse("not a date", now))
        assertNull(HubRetryAfter.parse(null, now))
    }
    @Test fun decodesHeartbeatAndMultilineData() {
        val frames = StreamFrames()
        assertNull(frames.line(": hb")); assertNull(frames.line(""))
        frames.line("event: stats"); frames.line("data: one"); frames.line("data: two")
        assertEquals("stats" to "one\ntwo", frames.line(""))
    }
    @Test fun freshnessPreservesUsageAndIgnoresUnexpectedFields() {
        val old = Json.parseToJsonElement("""{"periods":{"today":{"totalTokens":42}},"devices":[{"deviceId":"a","periods":{"today":{"totalTokens":42}}}],"limits":{"providers":[{"provider":"example"}]}}""").jsonObject
        val next = StreamMerge.apply(old, "freshness", """{"stats":{"updatedAt":"later","periods":{},"devices":[{"deviceId":"a","stale":true,"periods":{}}],"limits":{"updatedAt":"later","providers":[]}}}""")!!
        assertEquals(old["periods"], next["periods"])
        assertEquals(old["limits"]!!.jsonObject["providers"], next["limits"]!!.jsonObject["providers"])
        assertEquals(old["devices"]!!.jsonArray[0].jsonObject["periods"], next["devices"]!!.jsonArray[0].jsonObject["periods"])
        assertNull(StreamMerge.apply(null, "freshness", """{"stats":{}}"""))
    }
}
