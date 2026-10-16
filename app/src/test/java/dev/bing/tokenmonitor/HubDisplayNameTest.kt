package dev.bing.tokenmonitor

import java.io.ByteArrayInputStream
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class HubDisplayNameTest {
    private val body="""{"hub":{"displayName":" DXP4800 "},"periods":{"today":{"totalTokens":123,"costUsd":"0.001"}},"devices":[]}"""
    @Test fun authenticatedNameSurvivesHttpProjectionStreamProjectionAndOfflineDisplay() {
        val http=HubParser.parse(HubJsonProjection.stats(ByteArrayInputStream(body.toByteArray())))
        val stream=HubParser.parse(HubJsonProjection.streamStats(ByteArrayInputStream("""{"type":"snapshot","stats":$body}""".toByteArray())))
        assertEquals("DXP4800",http.hubDisplayName)
        assertEquals(http,stream)
        assertEquals(123L,http.periods.getValue("today").tokens)
        val connected=HubState(snapshot=http,connectedEndpoint="http://192.168.31.12:17321",lastSuccessfulEndpoint="http://192.168.31.12:17321")
        assertEquals("DXP4800",SyncSettingsPresentation.hubHostName(connected))
        assertEquals("DXP4800",SyncSettingsPresentation.hubHostName(connected.copy(connectedEndpoint=null,error=1)))
        val fresh=HubParser.applyFreshness(http,Json.parseToJsonElement("""{"hub":{"displayName":"forged"},"updatedAt":"2026-10-16T00:00:00Z","devices":[]}""").jsonObject)
        assertEquals("DXP4800",fresh.hubDisplayName) // Freshness cannot replace authoritative metadata.
        assertEquals(http.periods,fresh.periods)
    }
    @Test fun legacyMissingInvalidAndRemovedNamesFallBackWithoutCollectorGuessing() {
        for (hub in listOf("null","{}","[]","123","{\"displayName\":123}","{\"displayName\":true}","{\"displayName\":\" \"}","{\"displayName\":\"a\\nb\"}","{\"displayName\":\"a\\u202eb\"}","{\"displayName\":\"${"x".repeat(81)}\"}")) {
            val parsed=HubParser.parse("""{"hub":$hub,"periods":{"today":{}},"devices":[{"deviceId":"DXP4800"}]}""")
            assertNull(parsed.hubDisplayName)
            assertNull(SyncSettingsPresentation.hubHostName(HubState(snapshot=parsed,connectedEndpoint="http://192.168.31.12:17321")))
        }
        val old=HubParser.parse("""{"periods":{"today":{}},"devices":[]}""")
        assertNull(old.hubDisplayName)
        assertEquals("legacy.example.invalid",SyncSettingsPresentation.hubHostName(HubState(snapshot=old,connectedEndpoint="https://legacy.example.invalid")))
        assertEquals("机".repeat(80),HubDisplayName.normalize("机".repeat(80)))
        assertNull(HubDisplayName.normalize("a\u200bb"))
        assertNull(HubDisplayName.normalize("\ud800"))
    }
}
