package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.protocol.HubStreamProtocol
import io.github.theminionooo.tokenmonitor.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class UnpricedUsageTest {
    private var previousLocale = java.util.Locale.getDefault()
    @org.junit.Before fun english() { previousLocale = java.util.Locale.getDefault(); java.util.Locale.setDefault(java.util.Locale.US) }
    @org.junit.After fun restoreLocale() { java.util.Locale.setDefault(previousLocale) }
    private fun fixture(name: String) = checkNotNull(javaClass.classLoader?.getResourceAsStream("protocol/v0.68.0/$name"))
        .bufferedReader().use { it.readText() }

    @Test fun `v068 fields survive all read endpoints and freshness`() {
        val snapshot = HubProtocolParser.decodeSnapshot(fixture("health.json"), fixture("stats.json"), fixture("devices.json"),
            fixture("history.json"), fixture("subscriptions.json"), 1)
        assertEquals("v0.68.0", snapshot.health.hubBuild)
        assertEquals(1000L, snapshot.today.totalTokens)
        assertEquals(400L, snapshot.today.unpricedTokens)
        assertEquals(400L, snapshot.today.projects.single().unpricedTokens)
        assertTrue(snapshot.today.sessions.single().dotsObservedOnly)
        assertEquals(400L, snapshot.stats.devices.single().periods.getValue("today").unpricedTokens)
        assertEquals(400L, snapshot.history.monthly.single().unpricedTokens)
        assertEquals(400L, snapshot.history.daily.single().perModel.getValue("unpriced-model").unpricedTokens)
        val stream = fixture("stats-stream.sse").lineSequence().first { it.startsWith("data:") }.removePrefix("data:").trim()
        val freshness = fixture("stats-freshness.sse").lineSequence().first { it.startsWith("data:") }.removePrefix("data:").trim()
        val refreshed = HubProtocolParser.decodeStats(HubStreamProtocol.mergeFreshness(HubStreamProtocol.normalizeComplete(stream), freshness))
        assertEquals(snapshot.today, refreshed.periods.getValue("today"))
        assertEquals("2026-10-08T12:02:00.000Z", refreshed.updatedAt)
    }

    @Test fun `legacy zero cost is not inferred to be unpriced`() {
        val legacy = HubProtocolParser.decodeStats("""{"periods":{"today":{"totalTokens":100,"costUsd":0}}}""").periods.getValue("today")
        assertEquals(0L, legacy.unpricedTokens)
        assertEquals("$0.00", formatUsageCost(legacy.costUsd, legacy.unpricedTokens))
        assertEquals("$1.25 + 400 unpriced", formatUsageCost(1.25, 400))
        assertEquals("— (400 unpriced)", formatUsageCost(0.0, 400))
        assertEquals("$1.25 + ?", formatUsageCost(1.25, 400, compact = true))
        assertEquals("— (?)", formatUsageCost(0.0, 400, compact = true))
    }

    @Test fun `malformed counts are bounded and observed provenance requires the complete pair`() {
        val stats = HubProtocolParser.decodeStats("""{"periods":{"today":{"totalTokens":100,"unpricedTokens":999,"clients":{"codex":60,"claude":40},"clientUnpricedTokens":{"codex":99,"claude":99,"unknown":100},"models":{"a":100},"modelUnpricedTokens":{"a":-5},"sessions":{"a":{"client":"codex","totalTokens":10,"unpricedTokens":20,"usageSource":"codex-dots-local","usageCoverage":"observed-only"},"b":{"client":"claude","usageSource":"codex-dots-local","usageCoverage":"observed-only"},"c":{"client":"codex","usageSource":"codex-dots-local"}}}}}""")
        val usage = stats.periods.getValue("today")
        assertEquals(100L, usage.unpricedTokens)
        assertEquals(mapOf("codex" to 60L, "claude" to 40L), usage.clientUnpricedTokens)
        assertTrue(usage.modelUnpricedTokens.isEmpty())
        assertEquals(listOf(true, false, false), usage.sessions.map { it.dotsObservedOnly })
        assertEquals(10L, usage.sessions.first().unpricedTokens)
        assertEquals("Dots · observed only", sessionMetricLabels(usage.sessions.first(), 0))
        assertFalse(sessionRowLabels(usage.sessions.first()).meta.contains("observed only"))
    }

    @Test fun `tool filtering never borrows global unpriced attribution`() {
        val usage = HubProtocolParser.decodeStats(fixture("stats.json")).periods.getValue("today")
        val codex = usage.modelsForTool("codex")
        assertEquals(400L, codex.unpricedTokens)
        assertEquals(mapOf("unpriced-model" to 400L), codex.modelUnpricedTokens)
        assertEquals(0L, usage.modelsForTool("claude").unpricedTokens)
        assertTrue(usage.modelsForTool("claude").modelUnpricedTokens.isEmpty())
        assertTrue(usage.copy(clientModelUnpricedTokens = emptyMap()).modelsForTool("codex").modelUnpricedTokens.isEmpty())
    }

    @Test fun `live history replaces today's incomplete cost without double counting`() {
        val today = LocalDate.now()
        val point = HistoryPoint(today.toString(), 100, 0.0, unpricedTokens = 100,
            perClient = mapOf("codex" to HistoryAttribution(tokens = 100, unpricedTokens = 100)))
        val usage = UsagePeriod(totalTokens = 100, costUsd = 1.0, unpricedTokens = 40, clients = mapOf("codex" to 100),
            clientUnpricedTokens = mapOf("codex" to 40), models = mapOf("gpt-5" to 100), modelUnpricedTokens = mapOf("gpt-5" to 40))
        val snapshot = HubSnapshot(stats = HubStats(updatedAt = "${today}T12:00:00Z", periods = mapOf("today" to usage)), history = HubHistory(daily = listOf(point)))
        val history = usageHistory(snapshot)
        assertEquals(40L, history.single().unpricedTokens)
        assertEquals(40L, history.single().perClient.getValue("codex").unpricedTokens)
        val rolling = aggregateHistory(snapshot, today.minusDays(6))
        assertEquals(100L, rolling.totalTokens)
        assertEquals(40L, rolling.unpricedTokens)
        assertEquals(40L, rolling.modelUnpricedTokens["gpt-5"])
        val sparseChanged = mergeUsageHistory(listOf(point), listOf(point.copy(unpricedTokens = 40, perClient = emptyMap()))).single()
        assertTrue(sparseChanged.perClient.isEmpty())
    }
}
