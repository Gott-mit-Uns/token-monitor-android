package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.data.storage.DesktopOptions
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.*

class Desktop068Test {
    @Test fun observedCodexAndUnpricedCountsAreExplicit() {
        val stats = HubProtocolParser.decodeStats("""{"periods":{"today":{"totalTokens":100,"costUsd":0,"unpricedTokens":30,"clientUnpricedTokens":{"codex":30},"modelUnpricedTokens":{"unknown":30},"clientModelUnpricedTokens":{"codex":{"unknown":30}},"sessions":{"s":{"client":"codex","totalTokens":100,"models":{"unknown":100},"unpricedTokens":30,"usageSource":"codex-dots-local","usageCoverage":"observed-only"}}}}}""")
        val period = stats.periods.getValue("today")
        assertEquals(30L, period.unpricedTokens)
        assertEquals(30L, period.modelsForTool("codex").modelUnpricedTokens["unknown"])
        assertEquals("observed-only", period.sessions.single().usageCoverage)
        assertEquals(mapOf("unknown" to 100L), period.sessions.single().modelTokens)
        assertEquals(0.0, period.costUsd, 0.0)
    }
    @Test fun zeroCostDoesNotImplyMissingPricing() {
        val stats = HubProtocolParser.decodeStats("""{"periods":{"today":{"totalTokens":100,"costUsd":0}}}""")
        assertEquals(0L, stats.periods.getValue("today").unpricedTokens)
    }
    @Test fun groupingRespectsSupplyPrefixesAndManualPrecedence() {
        val names = listOf("vendor/GPT_6.SOL", "gpt-6-sol", "other/unique")
        val off = modelResolver(DesktopOptions(), names)
        assertEquals(names.first(), off(names.first()))
        val duplicates = modelResolver(DesktopOptions(modelGrouping = "duplicates"), names)
        assertEquals("gpt-6-sol", duplicates(names.first()))
        assertEquals("other/unique", duplicates("other/unique"))
        val prefix = modelResolver(DesktopOptions(modelGrouping = "prefix", modelAliases = mapOf("gpt-6-sol" to "display")), names)
        assertEquals("display", prefix(names.first()))
        assertEquals("unique", prefix("other/unique"))
    }
    @Test fun fontsHaveIndependentRolesAndPreserveSizes() {
        val original = tokenMonitorTypography(1)
        val changed = tokenMonitorTypography(1, "system", "mono")
        assertEquals(original.bodySmall.fontSize, changed.bodySmall.fontSize)
        assertEquals(androidx.compose.ui.text.font.FontFamily.SansSerif, changed.bodySmall.fontFamily)
        assertEquals(androidx.compose.ui.text.font.FontFamily.Monospace, changed.displayMedium.fontFamily)
    }
    @Test fun bundleHasFourPrivacyFilteredFilesAndChineseCsvBom() {
        val sample = io.github.theminionooo.tokenmonitor.domain.HubSnapshot(history = io.github.theminionooo.tokenmonitor.domain.HubHistory(daily = listOf(
            io.github.theminionooo.tokenmonitor.domain.HistoryPoint("2026-10-09", 100, 0.5, perModel = mapOf("模型" to io.github.theminionooo.tokenmonitor.domain.HistoryAttribution(tokens = 100, costUsd = 0.5, cacheReadTokens = 20, cacheWriteTokens = 10, outputTokens = 30, unclassifiedTokens = 5)))
        )))
        val files = mutableMapOf<String, String>()
        java.util.zip.ZipInputStream(exportUsageBundle(sample).inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                files[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertEquals(4, files.size)
        assertTrue(files.getValue("token-monitor-daily-models.csv").startsWith("\uFEFF"))
        assertTrue(files.getValue("token-monitor-daily-models.csv").contains("35,30,20,10,5,100,0.5"))
        assertFalse(files.values.any { it.contains("secret") })
    }
    @Test fun liveRateUsesOnlyIncrementalPerformanceCounters() {
        val tracker = LiveTokenRateTracker()
        val base = io.github.theminionooo.tokenmonitor.domain.UsagePeriod(throughputAvailable = true, timedTokens = 1000, timedOutputTokens = 100, timedDurationMs = 10000)
        assertNull(tracker.observe(base, 0))
        val sample = tracker.observe(base.copy(timedTokens = 1100, timedOutputTokens = 120, timedDurationMs = 11000), 1)!!
        assertEquals(20.0, sample.speed, 0.0)
        assertEquals(6000.0, sample.burn, 0.0)
        assertEquals(sample, tracker.observe(base.copy(timedTokens = 1100, timedOutputTokens = 120, timedDurationMs = 11000), 2))
        assertNull(tracker.observe(base, 3))
        assertNull(tracker.observe(base.copy(throughputAvailable = false), 4))
    }
    @Test fun manualOrderingAndPinsDoNotChangeStatistics() {
        val tokens = mapOf("a" to 100L, "b" to 50L, "c" to 1L)
        assertEquals(listOf("c", "b", "a"), rankedUsageNames(tokens, emptyMap(), false, false, DesktopOptions(pinnedTools = setOf("c"), toolOrder = listOf("b", "a"))))
        assertEquals(mapOf("a" to 100L, "b" to 50L, "c" to 1L), tokens)
        assertEquals(listOf("b", "a", "c"), moveUsageName(listOf("a", "b", "c"), "b", -1))
    }
    @Test fun aliasesMatchOfficialDesktop068ResolverCases() {
        val source = javaClass.classLoader!!.getResource("protocol/v0.68.0/model-alias-parity.json")!!.readText()
        Json.parseToJsonElement(source).jsonArray.forEach { row ->
            val test = row.jsonObject
            val aliases = test.getValue("aliases").jsonObject.mapValues { it.value.jsonPrimitive.content }
            val models = test.getValue("models").jsonArray.map { it.jsonPrimitive.content }
            val resolver = modelResolver(DesktopOptions(modelAliases = aliases, modelGrouping = test.getValue("mode").jsonPrimitive.content), models)
            test.getValue("expected").jsonObject.forEach { (name, expected) -> assertEquals(expected.jsonPrimitive.content, resolver(name)) }
        }
    }
    @Test fun subscriptionRenewalKeepsMonthEndAnchorAndHonorsCancellation() {
        val subscription = io.github.theminionooo.tokenmonitor.domain.Subscription("id", "codex", "plan", 100, "USD", "2026-01-31", "month", true)
        assertEquals("2026-02-28", subscriptionRenewal(subscription, java.time.LocalDate.parse("2026-02-10")))
        assertEquals("2026-03-31", subscriptionRenewal(subscription, java.time.LocalDate.parse("2026-03-01")))
        assertEquals("", subscriptionRenewal(subscription.copy(autoRenew = false)))
        assertEquals("", subscriptionRenewal(subscription.copy(kind = "topup")))
        assertEquals("2026-04-01", subscriptionRenewal(subscription.copy(nextRenewalOverride = "2026-04-01"), java.time.LocalDate.parse("2026-03-01")))
    }
    @Test fun malformedMoneyDoesNotCrashPresentation() {
        assertEquals("—", convertedCost(Double.POSITIVE_INFINITY, DesktopOptions()))
        assertEquals("—", convertedCost(Double.NaN, DesktopOptions()))
    }
    @Test fun providerValueUsesAllSubscriptionsRatherThanDuplicatingPerAccount() {
        val sub = io.github.theminionooo.tokenmonitor.domain.Subscription("a", "codex", "plan", 2000, "USD", "2026-01-01", "month", true)
        val snapshot = io.github.theminionooo.tokenmonitor.domain.HubSnapshot(stats = io.github.theminionooo.tokenmonitor.domain.HubStats(periods = mapOf("month" to io.github.theminionooo.tokenmonitor.domain.UsagePeriod(clientCosts = mapOf("codex" to 100.0)))), subscriptions = io.github.theminionooo.tokenmonitor.domain.HubSubscriptions(entries = listOf(sub, sub.copy(id = "b"))))
        assertEquals(2.5, providerValueMultiple(snapshot, "codex", DesktopOptions(), java.time.LocalDate.parse("2026-10-09"))!!, 0.0)
        assertNull(providerValueMultiple(snapshot, "unknown", DesktopOptions()))
    }
    @Test fun agentVersionIsReadWithoutChangingDeviceIdentity() {
        val stats = HubProtocolParser.decodeStats("""{"devices":[{"deviceId":"NAS 自定义名称","hostname":"raw-host","agentVersion":"0.68.0"}]}""")
        assertEquals("0.68.0", stats.devices.single().agentVersion)
        assertEquals("NAS 自定义名称", hubNamedSnapshot(io.github.theminionooo.tokenmonitor.domain.HubSnapshot(stats = stats)).stats.devices.single().hostname)
    }
    @Test fun historyCapabilityDeterminesResidualInputWithoutDiscardingKnownComponents() {
        fun history(known: Boolean) = HubProtocolParser.decodeHistory("""{"daily":[{"date":"2026-10-09","tokens":100,"tokenComponentsAvailable":$known,"perModel":{"model":{"tokens":100,"cacheReadTokens":20,"cacheWriteTokens":10,"outputTokens":30}}}]}""")
        val priced = history(true)
        assertEquals(0L, priced.daily.single().perModel.getValue("model").unclassifiedTokens)
        val unknown = history(false)
        assertEquals(40L, unknown.daily.single().perModel.getValue("model").unclassifiedTokens)
        assertEquals(20L, unknown.daily.single().perModel.getValue("model").cacheReadTokens)
        val csv = exportDailyUsage(io.github.theminionooo.tokenmonitor.domain.HubSnapshot(history = priced), true)
        assertTrue(csv.contains("40,30,20,10,0,100"))
    }
}
