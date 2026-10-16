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
    @Test fun streamMergeKeepsAuthoritativeNameThroughFreshnessAndClearsItOnLegacyFullSnapshot() {
        val initial = StreamMerge.apply(null, "snapshot", """{"stats":$body}""")!!
        val original = HubParser.parse(initial, HubModelIndex.EMPTY)
        assertEquals("DXP4800", original.hubDisplayName)
        val changed = StreamMerge.apply(initial, "freshness", """{"stats":{
            "hub":{"displayName":"Forged freshness name"},"updatedAt":"2026-10-16T00:00:00Z",
            "periods":{"today":{"totalTokens":999}},"devices":[]
        }}""")!!
        val optimized = HubParser.applyFreshness(original, changed)
        val reparsed = HubParser.parse(changed, HubModelIndex.EMPTY)
        assertEquals(optimized, reparsed)
        assertEquals("DXP4800", reparsed.hubDisplayName)
        assertEquals(original.periods, reparsed.periods)
        assertEquals(123L, reparsed.periods.getValue("today").tokens)
        assertEquals("0.001", reparsed.periods.getValue("today").cost)
        // A legacy full snapshot is authoritative, so absence removes a name
        // rather than borrowing it from the previous full/freshness basis.
        val legacy = StreamMerge.apply(changed, "stats", """{"stats":{
            "periods":{"today":{"totalTokens":123,"costUsd":"0.001"}},"devices":[]
        }}""")!!
        val accepted = HubParser.parse(legacy, HubModelIndex.EMPTY)
        assertNull(accepted.hubDisplayName)
        assertEquals(original.periods, accepted.periods)
        assertNull(SyncSettingsPresentation.hubHostName(HubState(snapshot=accepted,
            connectedEndpoint="https://127.0.0.1:17321",lastSuccessfulEndpoint="https://127.0.0.1:17321")))
    }

}
