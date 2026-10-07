package io.github.theminionooo.tokenmonitor.data.protocol

import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Responses produced by tools/check-hub-contract.mjs from the exact released desktop source. */
class ReleasedHubContractTest {
    @Test fun `released Hub endpoints and v2 stream preserve synthetic usage`() {
        val directory = System.getenv("TOKEN_MONITOR_CONTRACT_DIR")
        assumeTrue("Run the pinned Hub contract harness to supply responses", !directory.isNullOrBlank())
        fun response(name: String) = File(directory!!, "$name.json").readText()
        val snapshot = HubProtocolParser.decodeSnapshot(
            response("health"), response("stats"), response("devices"), response("history"), response("subscriptions"), 1,
        )
        assertTrue(snapshot.health.ok)
        assertEquals("hub", snapshot.health.role)
        assertTrue(snapshot.health.hubBuild.isNotBlank())
        assertEquals(1234L, snapshot.today.totalTokens)
        assertEquals(1234L, snapshot.today.clients["mcode"])
        assertTrue(snapshot.stats.devices.any { it.id == "contract-desktop" })
        val streamed = HubStreamProtocol.normalizeComplete(response("stream-snapshot"))
        assertEquals(1234L, HubProtocolParser.decodeStats(streamed).periods["today"]?.totalTokens)
        val refreshed = HubStreamProtocol.mergeFreshness(streamed, response("freshness"))
        assertEquals(1234L, HubProtocolParser.decodeStats(refreshed).periods["today"]?.totalTokens)
        val changed = HubStreamProtocol.normalizeComplete(response("stream-stats"))
        assertEquals(1235L, HubProtocolParser.decodeStats(changed).periods["today"]?.totalTokens)
        val shared = HubProtocolParser.decodeStats(response("titles-enabled"))
        assertEquals("Synthetic shared title", shared.periods.getValue("today").sessions.single().title)
        val revoked = HubProtocolParser.decodeStats(response("titles-revoked"))
        assertEquals("", revoked.periods.getValue("today").sessions.single().title)
        assertEquals(shared.periods.getValue("today").totalTokens, revoked.periods.getValue("today").totalTokens)
    }
}
