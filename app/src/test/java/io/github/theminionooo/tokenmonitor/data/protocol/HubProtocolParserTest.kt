package io.github.theminionooo.tokenmonitor.data.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HubProtocolParserTest {
    @Test
    fun `v0 54 fixture maps every dashboard surface`() {
        val snapshot = HubProtocolParser.decodeSnapshot(
            healthRaw = resource("health.json"),
            statsRaw = resource("stats.json"),
            devicesRaw = resource("devices.json"),
            historyRaw = resource("history.json"),
            subscriptionsRaw = resource("subscriptions.json"),
            capturedAt = 1_725_464_110_000,
        )

        assertTrue(snapshot.health.ok)
        assertEquals("node-hub", snapshot.health.runtime)
        assertEquals(125_430, snapshot.today.totalTokens)
        assertFalse(snapshot.today.throughputAvailable)
        assertEquals(0, snapshot.today.timedTokens)
        assertEquals(2, snapshot.today.clients.size)
        assertEquals(60_000, snapshot.today.clientCacheReads.getValue("codex"))
        assertEquals(9_000, snapshot.today.clientOutputs.getValue("codex"))
        assertEquals(60_000, snapshot.today.modelCacheReads.getValue("gpt-5"))
        assertEquals("Token Monitor", snapshot.today.projects.single().label)
        assertEquals(81_300, snapshot.today.projects.single().clients.getValue("codex"))
        assertEquals("session-001", snapshot.today.sessions.single().id)
        assertEquals(3, snapshot.today.sessions.single().messageCount)
        assertEquals("Studio", snapshot.stats.devices.single().hostname)
        assertEquals("11 24H2", snapshot.stats.devices.single().osVersion)
        assertEquals(125_430, snapshot.stats.devices.single().history.daily.single().tokens)
        assertEquals(64.0, snapshot.stats.limits.providers.single().windows.single().remainingPercent!!, 0.001)
        assertEquals(3, snapshot.history.daily.size)
        assertEquals(71_310, snapshot.history.daily.first().perClient.getValue("codex").tokens)
        assertEquals(50_000, snapshot.history.daily.first().perModel.getValue("gpt-5").cacheReadTokens)
        assertTrue(snapshot.history.daily.first().tokenComponentsAvailable)
        assertEquals("Plus", snapshot.subscriptions.entries.single().planName)
        assertFalse(snapshot.fromCache)
    }

    @Test
    fun `unknown and missing optional fields are safe`() {
        val stats = HubProtocolParser.decodeStats(
            """{"periods":{"today":{"totalTokens":9,"newField":{"nested":true}}},"unknown":42}""",
        )

        assertEquals(9, stats.periods.getValue("today").totalTokens)
        assertTrue(stats.devices.isEmpty())
        assertTrue(stats.limits.providers.isEmpty())
        assertTrue(stats.historyPreview.daily.isEmpty())
        assertFalse(stats.periods.getValue("today").throughputAvailable)
    }

    @Test
    fun `v0 55 throughput counters preserve capability provenance`() {
        val stats = HubProtocolParser.decodeStats(resource("stats.json", "v0.55.0"))
        val today = stats.periods.getValue("today")

        assertTrue(today.throughputAvailable)
        assertEquals(118_000, today.timedTokens)
        assertEquals(14_500, today.timedOutputTokens)
        assertEquals(58_000, today.timedDurationMs)

        val legacy = HubProtocolParser.decodeStats(resource("stats.json", "v0.54.0"))
        assertFalse(legacy.periods.getValue("today").throughputAvailable)

        val explicitlyUnavailable = HubProtocolParser.decodeStats(
            """{"periods":{"today":{"capabilities":{"throughput":false},"timedTokens":9,"timedOutputTokens":3,"timedDurationMs":100}}}""",
        ).periods.getValue("today")
        assertFalse(explicitlyUnavailable.throughputAvailable)
        assertEquals(9, explicitlyUnavailable.timedTokens)
    }

    @Test
    fun `v0 55 stream carries throughput without changing older envelopes`() {
        val data = resource("stats-stream.sse", "v0.55.0")
            .lineSequence()
            .filter { it.startsWith("data:") }
            .joinToString("\n") { it.removePrefix("data:").trimStart() }

        val today = checkNotNull(HubProtocolParser.decodeStatsStreamEvent(data)).periods.getValue("today")
        assertTrue(today.throughputAvailable)
        assertEquals(14_750, today.timedOutputTokens)
    }

    @Test
    fun `v0 56 boundary and background review metadata are preserved`() {
        val stats = HubProtocolParser.decodeStats(resource("stats.json", "v0.56.0"))

        assertEquals("background-review", stats.periods.getValue("today").sessions.single().sessionKind)
        assertEquals("expiry", stats.limits.providers.first().windows.last().boundaryKind)
        assertEquals("mixed", stats.limits.providers.last().windows.single().boundaryKind)
    }

    @Test
    fun `v0 60 preserves session activity context and new limit shapes`() {
        val stats = HubProtocolParser.decodeStats(resource("stats.json", "v0.60.0"))
        val sessions = stats.periods.getValue("today").sessions.associateBy { it.id }

        assertEquals(false, sessions.getValue("active").turnEnded)
        assertEquals(170_000, sessions.getValue("active").contextTokens)
        assertEquals(200_000, sessions.getValue("active").contextWindow)
        assertEquals(true, sessions.getValue("finished").turnEnded)
        assertNull(sessions.getValue("unknown").turnEnded)
        assertEquals("factory", stats.limits.providers.first().provider)
        val credits = stats.limits.providers.last().windows.single()
        assertEquals(42.50, credits.remaining!!, 0.001)
        assertEquals("USD", credits.currency)
        assertEquals(false, credits.showMeter)
    }

    @Test
    fun `v0 61 preserves canonical clients limits and full endpoint snapshot`() {
        val snapshot = HubProtocolParser.decodeSnapshot(
            healthRaw = resource("health.json", "v0.61.0"),
            statsRaw = resource("stats.json", "v0.61.0"),
            devicesRaw = resource("devices.json", "v0.61.0"),
            historyRaw = resource("history.json", "v0.61.0"),
            subscriptionsRaw = resource("subscriptions.json", "v0.61.0"),
            capturedAt = 1_800_000_000_000,
        )

        assertEquals(setOf("codex", "mimo", "devin", "copilot", "cline"), snapshot.today.clients.keys)
        assertEquals(listOf("cline", "devin", "mimo"), snapshot.stats.limits.providers.map { it.provider })
        assertEquals(170_000, snapshot.today.sessions.first { it.client == "codex" }.contextTokens)
        assertEquals("Studio", snapshot.stats.devices.single().hostname)
        assertEquals("mimo", snapshot.subscriptions.entries.single().provider)
        assertEquals(3, snapshot.history.daily.size)
    }

    @Test
    fun `v0 61 stream carries canonical client ids`() {
        val data = resource("stats-stream.sse", "v0.61.0")
            .lineSequence()
            .filter { it.startsWith("data:") }
            .joinToString("\n") { it.removePrefix("data:").trimStart() }

        val today = checkNotNull(HubProtocolParser.decodeStatsStreamEvent(data)).periods.getValue("today")
        assertEquals(61_000, today.clients.getValue("mimo"))
        assertEquals(45_000, today.clients.getValue("devin"))
    }

    @Test
    fun `v0 62 keeps Pi and Oh My Pi separate and reads TypeSafe limits`() {
        val snapshot = HubProtocolParser.decodeSnapshot(
            healthRaw = resource("health.json", "v0.62.0"),
            statsRaw = resource("stats.json", "v0.62.0"),
            devicesRaw = resource("devices.json", "v0.62.0"),
            historyRaw = resource("history.json", "v0.62.0"),
            subscriptionsRaw = resource("subscriptions.json", "v0.62.0"),
            capturedAt = 1_800_000_000_000,
        )

        assertEquals(30_000, snapshot.today.clients.getValue("pi"))
        assertEquals(60_000, snapshot.today.clients.getValue("omp"))
        assertEquals("omp", snapshot.today.sessions.single().client)
        assertEquals(setOf("pi", "omp"), snapshot.history.daily.last().perClient.keys - "codex")
        assertEquals(listOf("codex", "pi", "omp"), snapshot.stats.devices.single().trackedClients)
        assertEquals("codex", snapshot.subscriptions.entries.single().provider)
        val typesafe = snapshot.stats.limits.providers.first { it.provider == "typesafe" }
        assertEquals("Pro", typesafe.plan)
        assertEquals(42.50, typesafe.windows.single().remaining!!, 0.001)
        assertEquals("expiry", typesafe.windows.single().boundaryKind)
        assertEquals("Core", snapshot.stats.limits.providers.first { it.provider == "devin" }.plan)
        assertEquals("", snapshot.today.sessions.single().title)
    }

    @Test
    fun `v0 62 stream and freshness preserve separate clients and update timestamps`() {
        val statsEvent = resource("stats-stream.sse", "v0.62.0")
            .lineSequence().first { it.startsWith("data:") }.removePrefix("data:").trimStart()
        val today = checkNotNull(HubProtocolParser.decodeStatsStreamEvent(statsEvent)).periods.getValue("today")
        assertEquals(30_000, today.clients.getValue("pi"))
        assertEquals(61_000, today.clients.getValue("omp"))

        val freshnessEvent = resource("stats-freshness.sse", "v0.62.0")
            .lineSequence().first { it.startsWith("data:") }.removePrefix("data:").trimStart()
        val merged = HubStreamProtocol.mergeFreshness(resource("stats.json", "v0.62.0"), freshnessEvent)
        val refreshed = HubProtocolParser.decodeStats(merged)
        assertEquals(60_000, refreshed.periods.getValue("today").clients.getValue("omp"))
        assertEquals("2026-09-24T14:32:00.000Z", refreshed.limits.updatedAt)
    }

    @Test
    fun `v0 63 reads Cursor conversation titles without changing older sessions`() {
        val snapshot = HubProtocolParser.decodeSnapshot(
            healthRaw = resource("health.json", "v0.63.0"),
            statsRaw = resource("stats.json", "v0.63.0"),
            devicesRaw = resource("devices.json", "v0.63.0"),
            historyRaw = resource("history.json", "v0.63.0"),
            subscriptionsRaw = resource("subscriptions.json", "v0.63.0"),
            capturedAt = 1_800_000_000_000,
        )

        assertEquals(10_000, snapshot.today.clients.getValue("cursor"))
        assertEquals("Example planning conversation", snapshot.today.sessions.first { it.client == "cursor" }.title)
        assertEquals("", snapshot.today.sessions.first { it.client == "omp" }.title)
        assertEquals(10_000, snapshot.history.daily.last().perClient.getValue("cursor").tokens)
        assertEquals(listOf("codex", "cursor", "pi", "omp"), snapshot.stats.devices.single().trackedClients)
        assertEquals("codex", snapshot.subscriptions.entries.single().provider)

        val stream = resource("stats-stream.sse", "v0.63.0")
            .lineSequence().first { it.startsWith("data:") }.removePrefix("data:").trimStart()
        val today = checkNotNull(HubProtocolParser.decodeStatsStreamEvent(stream)).periods.getValue("today")
        assertEquals("Example planning conversation", today.sessions.single().title)

        val freshness = resource("stats-freshness.sse", "v0.63.0")
            .lineSequence().first { it.startsWith("data:") }.removePrefix("data:").trimStart()
        val merged = HubStreamProtocol.mergeFreshness(resource("stats.json", "v0.63.0"), freshness)
        assertEquals("Example planning conversation", HubProtocolParser.decodeStats(merged).periods.getValue("today")
            .sessions.first { it.client == "cursor" }.title)
    }

    @Test
    fun `v0 63 1 preserves canonical Cursor Auto and reasoning inclusive totals`() {
        val snapshot = HubProtocolParser.decodeSnapshot(
            healthRaw = resource("health.json", "v0.63.1"),
            statsRaw = resource("stats.json", "v0.63.1"),
            devicesRaw = resource("devices.json", "v0.63.1"),
            historyRaw = resource("history.json", "v0.63.1"),
            subscriptionsRaw = resource("subscriptions.json", "v0.63.1"),
            capturedAt = 1_800_000_000_000,
        )

        assertTrue(snapshot.health.ok)
        assertEquals(280_000, snapshot.today.totalTokens)
        assertEquals(40_000, snapshot.today.clientModels.getValue("cursor").getValue("cursor-auto"))
        assertEquals(40_000, snapshot.today.models.getValue("cursor-auto"))
        assertEquals(listOf("cursor-auto"), snapshot.today.sessions.single().modelNames)
        assertEquals(55_000, snapshot.today.clientOutputs.values.sum())
        assertEquals(
            snapshot.today.totalTokens,
            snapshot.today.clientOutputs.values.sum() + snapshot.today.clientUnclassifiedTokens.values.sum(),
        )
        assertEquals(18_000, snapshot.today.clientOutputs.getValue("opencode"))
        assertEquals(14_000, snapshot.today.clientOutputs.getValue("zcode"))
        assertEquals(32_000, snapshot.today.modelOutputs.getValue("qwen3-coder"))
        assertEquals(40_000, snapshot.history.daily.last().perModel.getValue("cursor-auto").tokens)
        assertEquals(
            snapshot.history.daily.last().outputTokens,
            snapshot.history.daily.last().perClient.values.sumOf { it.outputTokens },
        )
        assertEquals(18_000, snapshot.history.daily.last().perClient.getValue("opencode").outputTokens)
        assertEquals(14_000, snapshot.history.daily.last().perClient.getValue("zcode").outputTokens)
        assertEquals(listOf("codex", "cursor", "opencode", "zcode"), snapshot.stats.devices.single().trackedClients)
        assertEquals("codex", snapshot.subscriptions.entries.single().provider)

        val stream = resource("stats-stream.sse", "v0.63.1")
            .lineSequence().first { it.startsWith("data:") }.removePrefix("data:").trimStart()
        val today = checkNotNull(HubProtocolParser.decodeStatsStreamEvent(stream)).periods.getValue("today")
        assertEquals(40_000, today.models.getValue("cursor-auto"))

        val freshness = resource("stats-freshness.sse", "v0.63.1")
            .lineSequence().first { it.startsWith("data:") }.removePrefix("data:").trimStart()
        val merged = HubStreamProtocol.mergeFreshness(resource("stats.json", "v0.63.1"), freshness)
        val refreshed = HubProtocolParser.decodeStats(merged)
        assertEquals(40_000, refreshed.periods.getValue("today").models.getValue("cursor-auto"))
        assertEquals("2026-09-28T12:02:00.000Z", refreshed.limits.updatedAt)
    }

    @Test
    fun `v0 54 stream snapshot maps stats and ignores envelope fields`() {
        val data = resource("stats-stream.sse")
            .lineSequence()
            .filter { it.startsWith("data:") }
            .joinToString("\n") { it.removePrefix("data:").trimStart() }

        val stats = HubProtocolParser.decodeStatsStreamEvent(data)

        assertNotNull(stats)
        assertEquals(125_430, stats!!.periods.getValue("today").totalTokens)
        assertEquals("2026-09-03T12:00:00.000Z", stats.subscriptionsUpdatedAt)
    }

    @Test
    fun `malformed stream event is ignored`() {
        assertEquals(null, HubProtocolParser.decodeStatsStreamEvent("not json"))
    }

    @Test
    fun `v0 64 preserves Muse Grok StepFun and normalized limit data`() {
        val snapshot = HubProtocolParser.decodeSnapshot(
            resource("health.json", "v0.64.0"), resource("stats.json", "v0.64.0"),
            resource("devices.json", "v0.64.0"), resource("history.json", "v0.64.0"),
            resource("subscriptions.json", "v0.64.0"), 1_800_000_000_000,
        )
        assertEquals("v0.64.0", HubProtocolParser.SUPPORTED_UPSTREAM_VERSION)
        assertEquals(150_000, snapshot.today.totalTokens)
        assertEquals(9_000, snapshot.today.clientOutputs.getValue("muse"))
        assertEquals(snapshot.today.totalTokens, snapshot.today.clientOutputs.values.sum() + snapshot.today.clientUnclassifiedTokens.values.sum())
        val grok = snapshot.today.sessions.first { it.client == "grok" }
        assertEquals("fix a mixedCase bug", grok.title)
        assertEquals("Example Project", grok.projectLabel)
        assertFalse(grok.archived)
        assertTrue(snapshot.today.sessions.first { it.client == "muse" }.archived)
        val providers = snapshot.stats.limits.providers
        assertEquals(listOf("Coding Plan", "Token Plan"), providers.filter { it.provider == "stepfun" }.map { it.plan })
        assertEquals(75.0, providers.first().windows.first().remainingPercent!!, 0.001)
        assertEquals("", providers[1].windows.single().resetsAt)
        assertEquals(listOf("Pro", "Pro More", "Pro Max"), providers.filter { it.provider == "codex" }.map { it.plan })
        assertEquals(7.0, providers.last().windows.single().remaining!!, 0.001)
        assertEquals(9_000, snapshot.history.daily.single().perClient.getValue("muse").outputTokens)
        assertEquals(listOf("codex", "muse", "grok"), snapshot.stats.devices.single().trackedClients)
        assertEquals("Pro More", snapshot.subscriptions.entries.single().planName)

        val stream = resource("stats-stream.sse", "v0.64.0").lineSequence()
            .first { it.startsWith("data:") }.removePrefix("data:").trimStart()
        assertEquals(grok, HubProtocolParser.decodeStatsStreamEvent(stream)!!.periods.getValue("today").sessions.first())
        val freshness = resource("stats-freshness.sse", "v0.64.0").lineSequence()
            .first { it.startsWith("data:") }.removePrefix("data:").trimStart()
        val refreshed = HubProtocolParser.decodeStats(HubStreamProtocol.mergeFreshness(resource("stats.json", "v0.64.0"), freshness))
        assertEquals("2026-09-30T12:02:00.000Z", refreshed.updatedAt)
        assertEquals(grok, refreshed.periods.getValue("today").sessions.first())
    }

    @Test fun `archive flags are optional aliases and plan labels win over account labels`() {
        val stats = HubProtocolParser.decodeStats("""{"periods":{"today":{"sessions":{"a":{"archived":true},"b":{"deleted":true},"c":{}}}},"limits":{"providers":[{"provider":"stepfun","planLabel":"Explicit","accountLabel":"Fallback"},{"provider":"claude","accountLabel":"Private identity"}]}}""")
        assertEquals(listOf(true, true, false), stats.periods.getValue("today").sessions.map { it.archived })
        assertEquals(listOf("Explicit", ""), stats.limits.providers.map { it.plan })
    }

    private fun resource(name: String, version: String = "v0.54.0"): String = checkNotNull(
        javaClass.classLoader?.getResourceAsStream("protocol/$version/$name"),
    ).bufferedReader().use { it.readText() }
}
