package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

/** NAS source generated wire fixtures: see docs/ALIGN_072_UPDATE_OPENCODE.md for exact boundary. */
class OpenCode072CompatibilityTest {
    private val cases = Json.parseToJsonElement(javaClass.getResource("/opencode-072-wire.json")!!.readText()).jsonObject
    private fun snapshot(raw: JsonObject): Snapshot = HubParser.parse(HubJsonProjection.stats(raw.toString().byteInputStream()))

    @Test fun legacyAndCurrentSqliteMetadataSurviveAgentSanitizerHubAggregationAndAndroidProjection() {
        val legacy = snapshot(cases.getValue("legacy").jsonObject)
        val current = snapshot(cases.getValue("current").jsonObject)
        assertEquals(legacy, current)
        for (stats in listOf(legacy, current)) {
            val period = stats.periods.getValue("today")
            assertEquals(150L, period.tokens)
            assertEquals(150L, period.tools["opencode"])
            val row = period.sessions.single()
            assertEquals("opencode", row.tool)
            assertEquals("OpenCode compatibility", row.label)
            assertEquals(150L, row.tokens)
            assertEquals("2026-10-16T01:01:00.000Z", row.lastUsed)
            assertEquals(true, row.turnEnded)
            assertEquals(1.0, row.session!!.accounting.calls, 0.0)
            assertEquals(150L, row.session.models["opencode-model"])
            assertEquals("ended", SessionDisplay.activity(row, SessionDisplay.timestamp(row.lastUsed)!!))
            // today + month contain the same session: Home counts one identity, not two.
            assertEquals(1, SessionDisplay.source(stats).size)
            val list = SessionListDisplay.grouped(SessionListDisplay.raw(period.sessions, 0), now = 0)
            assertEquals(1, list.size)
            assertEquals(BigInteger.valueOf(150), list.single().tokens)
        }
    }
    @Test fun coldRestoreAndFreshnessRetainOpenCodeUsageAndDoNotPromoteMissingMetadataToActivity() {
        val root = cases.getValue("current").jsonObject
        val restored = Json.parseToJsonElement(root.toString()).jsonObject
        val full = snapshot(restored)
        val device = restored.getValue("devices").jsonArray.single().jsonObject
        val freshness = buildJsonObject { put("stats", buildJsonObject {
            put("updatedAt", "2026-10-16T01:02:00Z")
            put("devices", buildJsonArray {add(buildJsonObject {
                put("deviceId", device.getValue("deviceId")); put("stale", true)
            })})
        }) }
        val merged = StreamMerge.apply(restored, "freshness", freshness.toString())!!
        assertEquals(full.periods, snapshot(merged).periods)
        assertEquals(true, snapshot(merged).devices.single().stale)
        val period = root.getValue("periods").jsonObject.getValue("today").jsonObject
        val key = period.getValue("sessions").jsonObject.keys.single()
        val row = period.getValue("sessions").jsonObject.getValue(key).jsonObject
        val oldRow = JsonObject(row.filterKeys { it !in setOf("lastUsedAt", "startedAt", "turnEnded", "messageCount", "inputTokens", "outputTokens") })
        val oldRoot = buildJsonObject {put("periods", buildJsonObject {put("today", buildJsonObject {
            put("totalTokens", 150); put("sessions", buildJsonObject {put(key, oldRow)})
        })})}
        val old = snapshot(oldRoot).periods.getValue("today").sessions.single()
        assertNull(old.lastUsed); assertNull(old.turnEnded)
        assertFalse(old.session!!.accounting.callsKnown)
        assertNull(SessionListDisplay.cachePercent(old))
        assertEquals("idle", SessionDisplay.activity(old, 0))
    }
}
