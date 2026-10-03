package io.github.theminionooo.tokenmonitor.data.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HubStreamProtocolTest {
    @Test fun `HTTP error JSON cannot replace a complete snapshot`() {
        org.junit.Assert.assertThrows(HubProtocolException::class.java) {
            HubStreamProtocol.normalizeComplete("""{"error":"authentication required"}""")
        }
    }

    @Test fun `unnamed freshness frame is recognized by protocol type`() {
        assertTrue(HubStreamProtocol.isFreshness("""{"type":"freshness","stats":{"updatedAt":"new"}}""", "message"))
        org.junit.Assert.assertFalse(HubStreamProtocol.isFreshness("""{"type":"stats","stats":{"periods":{}}}""", "stats"))
    }

    @Test fun `unidentified device freshness must not match other unidentified devices`() {
        val merged = HubStreamProtocol.mergeFreshness("""{"periods":{},"devices":[{"stale":false}]}""", """{"devices":[{"stale":true}]}""")
        assertTrue(merged.contains("\"stale\":false"))
    }

    @Test fun `freshness cannot change counters accounts or membership`() {
        val complete = """{"periods":{"today":{"totalTokens":42}},"limits":{"providers":[{"provider":"codex"}]},"devices":[{"deviceId":"a","hostname":"Studio","periods":{"today":{"totalTokens":42}}}]}"""
        val freshness = """{"stats":{"periods":{"today":{"totalTokens":0}},"limits":{"providers":[]},"devices":[{"deviceId":"a","hostname":"bad","periods":{"today":{"totalTokens":0}},"stale":true},{"deviceId":"new","hostname":"phantom"}]}}"""
        val stats = HubProtocolParser.decodeStats(HubStreamProtocol.mergeFreshness(complete, freshness))
        assertEquals(42, stats.periods.getValue("today").totalTokens)
        assertEquals("codex", stats.limits.providers.single().provider)
        assertEquals("Studio", stats.devices.single().hostname)
        assertEquals(42, stats.devices.single().periods.getValue("today").totalTokens)
        assertTrue(stats.devices.single().stale)
    }

    @Test fun `freshness updates metadata without erasing complete stats`() {
        val complete = """{"updatedAt":"old","periods":{"today":{"totalTokens":42}},"limits":{"updatedAt":"old","providers":[{"provider":"codex"}]},"devices":[{"deviceId":"a","hostname":"Studio","updatedAt":"old","stale":false,"periods":{"today":{"totalTokens":42}}}]}"""
        val freshness = """{"stats":{"limits":{"updatedAt":"new"},"devices":[{"deviceId":"a","updatedAt":"new","receivedAt":"new","stale":true}]}}"""

        val merged = HubStreamProtocol.mergeFreshness(complete, freshness)
        val stats = HubProtocolParser.decodeStats(merged)

        assertEquals(42, stats.periods.getValue("today").totalTokens)
        assertEquals("new", stats.limits.updatedAt)
        assertEquals("codex", stats.limits.providers.single().provider)
        assertEquals("Studio", stats.devices.single().hostname)
        assertEquals("new", stats.devices.single().updatedAt)
        assertTrue(stats.devices.single().stale)
        assertEquals(42, stats.devices.single().periods.getValue("today").totalTokens)
    }
}
