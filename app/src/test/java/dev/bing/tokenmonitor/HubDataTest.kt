package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class HubDataTest {
    @Test fun readsActualUpstreamHubFixture() {
        val body = javaClass.getResourceAsStream("/upstream-stats.json")!!.bufferedReader().use { it.readText() }
        val snapshot = HubParser.parse(body)
        assertEquals(5000000123L, snapshot.periods["allTime"]!!.tokens)
        assertEquals(2, snapshot.devices.size)
        assertTrue(snapshot.devices.any { it.id == "fixture-offline" })
        assertEquals(5000000123L, snapshot.periods["allTime"]!!.tools["codex"])
    }
    @Test fun retainsLargeCountersAndMissingCosts() {
        val snapshot = HubParser.parse("""{"periods":{"allTime":{"totalTokens":5000000123}},"devices":[{"deviceId":"a","hostname":"Desktop"}]}""")
        assertEquals(5000000123L, snapshot.periods["allTime"]!!.tokens)
        assertNull(snapshot.periods["allTime"]!!.cost)
        assertNull(snapshot.devices.single().stale)
    }
    @Test fun normalizesHttpsRoot() {
        assertEquals("https://example.com", HubAddress.normalize(" https://example.com/ "))
    }
    @Test fun acceptsPrivateHttpButRejectsPublicOrAmbiguousHttp() {
        assertEquals("http://192.168.31.12:17321", HubAddress.normalize("http://192.168.31.12:17321/"))
        listOf("http://8.8.8.8", "http://localhost", "http://192.168.031.12", "http://172.32.0.1", "http://10.1.1.999").forEach {
            assertTrue(runCatching { HubAddress.normalize(it) }.isFailure)
        }
    }
    @Test fun rejectsCredentialBearingOrNonRootUrls() {
        listOf("https://user:password@example.com", "https://example.com/api/stats", "https://example.com?key=value", "http://example.com", "https://example.com/#fragment").forEach {
            assertTrue(runCatching { HubAddress.normalize(it) }.isFailure)
        }
    }
}
