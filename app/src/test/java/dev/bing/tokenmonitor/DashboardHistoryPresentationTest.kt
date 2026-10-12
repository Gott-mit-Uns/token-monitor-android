package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.time.LocalDate

/** IPC presentation cases are backed by the executed original history-presentation-source oracle. */
class DashboardHistoryPresentationTest {
    private fun bucket(tokens: Long?, cost: String? = "0") = HistoryAttribution(tokens, cost)
    private fun row(date: String = "2026-10-09", models: Map<String, HistoryAttribution>, tokens: Long? = 19) =
        HistoryRow(date, tokens, "2.5", perClient = mapOf("codex" to bucket(tokens)), perModel = models)
    private fun summary(favorite: String? = "model-y") = HistorySummary(999, "9.99", 7, 2, 4, 1234, 19, favorite, 8)
    private fun sample() = HubHistory(listOf(row(models = linkedMapOf(
        "provider/model-x" to bucket(4), "model-x" to bucket(6), "model-y" to bucket(9)))), emptyList(), summary())
    private val duplicateRules = ModelNames(emptyMap(), "duplicates")

    @Test fun absentOffAndUninitializedRulesPreserveRawHistoryAndOldConstructors() {
        val raw = sample()
        assertSame(raw, DashboardHistoryPresentation.project(raw, null))
        assertSame(raw, DashboardHistoryPresentation.project(raw, ModelNames(emptyMap(), "off")))
        assertSame(raw, DashboardHistoryPresentation.project(raw, ModelNames(mapOf("model-x" to "unused"), "prefix", false)))
        assertTrue(HubHistory(emptyList(), emptyList()).observedModelIds.isEmpty())
        assertNull(HistoryAttribution(1, "0").displayTokens)
    }

    @Test fun explicitAliasesAlwaysApplyOnceAndDoNotChangeWireTotalsToolsOrLifetime() {
        val raw = sample()
        val names = ModelNames(mapOf("provider/model-x" to "model-x", "model-x" to "Friendly"), "off")
        val shown = DashboardHistoryPresentation.project(raw, names)
        assertEquals(listOf("model-x", "Friendly", "model-y"), shown.daily.single().perModel.keys.toList())
        assertEquals(4L, shown.daily.single().perModel.getValue("model-x").tokens)
        assertEquals(6L, shown.daily.single().perModel.getValue("Friendly").tokens)
        assertEquals(raw.daily.single().tokens, shown.daily.single().tokens)
        assertEquals(raw.daily.single().cost, shown.daily.single().cost)
        assertSame(raw.daily.single().perClient, shown.daily.single().perClient)
        assertEquals(raw.summary, shown.summary)
        assertEquals(listOf("provider/model-x", "model-x", "model-y"), raw.daily.single().perModel.keys.toList())
    }

    @Test fun duplicatesUseCompleteDailyAndMonthlyWindowBeforeRangeSelection() {
        val raw = HubHistory(listOf(row(models = mapOf("provider/model-x" to bucket(4)), tokens = 4)),
            listOf(row("2026-09", mapOf("model-x" to bucket(600)), 600)), summary("provider/model-x"))
        val shown = DashboardHistoryPresentation.project(raw, duplicateRules)
        assertEquals(listOf("model-x"), shown.daily.single().perModel.keys.toList())
        assertEquals(4L, shown.daily.single().perModel.getValue("model-x").tokens)
        assertEquals("model-x", shown.summary!!.favoriteModel)
        assertEquals(listOf("model-x"), DashboardTrendProjection.project(shown, "7", true).keys)
        val isolated = HubHistory(raw.daily, emptyList(), summary("provider/model-x"))
        assertSame(isolated, DashboardHistoryPresentation.project(isolated, duplicateRules))
    }

    @Test fun parserRetainsCostOnlyAndSummaryObservationKeysAndPrefixDoesNotStripSuffixes() {
        val raw = HistoryParser.parse("""{"daily":[{"date":"2026-10-09","tokens":4,"perModel":{"provider/model-x":{"tokens":4,"cost":0}},"clientModelCosts":{"codex":{"model-x":0.1}}}],"monthly":[{"month":"2026-09","clientModelCosts":{"claude":{"hidden-model":0}}}],"summary":{"favoriteModel":"summary-model","clientModelCosts":{"codex":{"summary-cost-model":0}}}}""")
        assertEquals(listOf("provider/model-x", "model-x", "hidden-model", "summary-model", "summary-cost-model"), raw.observedModelIds)
        assertEquals(listOf("model-x"), DashboardHistoryPresentation.project(raw, duplicateRules).daily.single().perModel.keys.toList())
        val prefix = HubHistory(listOf(row(models = mapOf("vendor/model-cc" to bucket(4)), tokens = 4)), emptyList())
        assertEquals(listOf("model-cc"), DashboardHistoryPresentation.project(prefix, ModelNames(emptyMap(), "prefix"))
            .daily.single().perModel.keys.toList())
    }

    @Test fun aggregateFavoriteReranksFullRetainedDailyAndNeverUsesMonthlyOrLifetimeRanking() {
        val raw = HubHistory(listOf(row(models = linkedMapOf("vendor/a" to bucket(5), "a" to bucket(4), "b" to bucket(8)), tokens = 17)),
            listOf(row("2026-09", mapOf("b" to bucket(100000)), 100000)), summary("b"))
        val shown = DashboardHistoryPresentation.project(raw, duplicateRules)
        assertEquals("a", shown.summary!!.favoriteModel)
        assertEquals(raw.summary!!.totalTokens, shown.summary.totalTokens)
        assertEquals(raw.summary.totalCost, shown.summary.totalCost)
        assertEquals(raw.monthly.single().tokens, shown.monthly.single().tokens)
    }

    @Test fun absentOrEmptyFavoriteStaysAbsentAndNoAttributionOnlyRenamesExistingFavorite() {
        for (favorite in listOf<String?>(null, "")) {
            val raw = sample().copy(summary = summary(favorite))
            assertEquals(favorite, DashboardHistoryPresentation.project(raw, duplicateRules).summary!!.favoriteModel)
        }
        val raw = HubHistory(listOf(HistoryRow("2026-10-09", 10, "0")), emptyList(), summary("provider/model-x"))
        assertEquals("Friendly", DashboardHistoryPresentation.project(raw,
            ModelNames(mapOf("provider/model-x" to "Friendly"), "off")).summary!!.favoriteModel)
    }

    @Test fun integerObjectKeysEnumerateBeforeStringKeysAndControlEqualFavoriteAndTopFive() {
        val raw = HubHistory(listOf(row(models = linkedMapOf("z" to bucket(10), "10" to bucket(10), "2" to bucket(10),
            "01" to bucket(10), "4294967295" to bucket(10), "4294967294" to bucket(10), "a" to bucket(10)), tokens = 70)), emptyList(), summary("z"))
        val shown = DashboardHistoryPresentation.project(raw, ModelNames(mapOf("unobserved" to "target"), "off"))
        assertEquals(listOf("2", "10", "4294967294", "z", "01", "4294967295", "a"), shown.daily.single().perModel.keys.toList())
        assertEquals("2", shown.summary!!.favoriteModel)
        assertEquals(listOf("2", "10", "4294967294", "z", "01"), DashboardOverviewProjection.project(shown,
            LocalDate.of(2026, 10, 11)).models.map { it.key })
    }

    @Test fun aliasCollisionIntoIntegerKeyReordersObjectAndOrdinaryStringTiesKeepFirstInsertion() {
        val raw = HubHistory(listOf(row(models = linkedMapOf("a" to bucket(3), "z" to bucket(3), "2" to bucket(3)), tokens = 9)), emptyList(), summary("a"))
        val shown = DashboardHistoryPresentation.project(raw, ModelNames(mapOf("z" to "10"), "off"))
        assertEquals(listOf("2", "10", "a"), shown.daily.single().perModel.keys.toList())
        assertEquals("2", shown.summary!!.favoriteModel)
        val strings = raw.copy(daily = listOf(row(models = linkedMapOf("z" to bucket(3), "a" to bucket(3)), tokens = 6)))
        assertEquals("z", DashboardHistoryPresentation.project(strings, ModelNames(mapOf("none" to "alias"), "off")).summary!!.favoriteModel)
    }

    @Test fun exactMergedTokensBeyondLongReachBothDashboardModelsWhileRawCountsStayIntact() {
        val raw = HubHistory(listOf(row(models = mapOf("vendor/model-x" to bucket(Long.MAX_VALUE), "model-x" to bucket(Long.MAX_VALUE)), tokens = 19)), emptyList(), summary("model-x"))
        val shown = DashboardHistoryPresentation.project(raw, duplicateRules)
        val exact = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO)
        val grouped = shown.daily.single().perModel.getValue("model-x")
        assertNull(grouped.tokens)
        assertEquals(exact, grouped.presentationTokens())
        val trend = DashboardTrendProjection.project(shown, byModel = true)
        assertEquals(exact, trend.bars.single().total)
        assertTrue(trend.bars.single().attributionComplete)
        val overview = DashboardOverviewProjection.project(shown, LocalDate.of(2026, 10, 11))
        assertEquals(exact, overview.models.single().tokens)
        assertEquals(BigInteger.valueOf(19), overview.retainedTokens)
        assertEquals(Long.MAX_VALUE, raw.daily.single().perModel.getValue("vendor/model-x").tokens)
    }

    @Test fun knownCostsUseDecimalAndOptionalUnpricedMessagesAndKnownExactComponentsAccumulate() {
        val first = HistoryAttribution(4, "0.10", components = TokenComponents(1, null, 3, 1, null), exact = true, messages = 1)
        val second = HistoryAttribution(6, "0.20", 7, TokenComponents(2, null, 4, null, null), true, 2)
        val raw = HubHistory(listOf(row(models = mapOf("vendor/model-x" to first, "model-x" to second), tokens = 10)), emptyList())
        val value = DashboardHistoryPresentation.project(raw, duplicateRules).daily.single().perModel.getValue("model-x")
        assertEquals(10L, value.tokens)
        assertEquals("0.30", value.cost)
        assertEquals(7L, value.unpricedTokens)
        assertEquals(3L, value.messages)
        assertEquals(TokenComponents(3, 0, 7, 1, null), value.components)
        assertEquals(true, value.exact)
    }

    @Test fun unknownRequiredValuesStayUnknownAndCannotInventGroupedFavorite() {
        val raw = HubHistory(listOf(row(models = linkedMapOf("vendor/model-x" to HistoryAttribution(4, "1"),
            "model-x" to HistoryAttribution(null, null, 2), "other" to bucket(99)), tokens = 103)), emptyList(), summary("vendor/model-x"))
        val shown = DashboardHistoryPresentation.project(raw, duplicateRules)
        val grouped = shown.daily.single().perModel.getValue("model-x")
        assertNull(grouped.tokens)
        assertNull(grouped.presentationTokens())
        assertNull(grouped.cost)
        assertNull(grouped.components.cacheRead)
        assertNull(grouped.components.unclassified)
        assertEquals(2L, grouped.unpricedTokens)
        assertEquals("model-x", shown.summary!!.favoriteModel)
        assertFalse(DashboardTrendProjection.project(shown, byModel = true).bars.single().attributionComplete)
    }

    @Test fun rawCacheStringRoundTripRestoresObservationKeysAndNeverPersistsDisplayAliases() {
        val body = """{"daily":[{"date":"2026-10-09","tokens":4,"perModel":{"vendor/model-x":{"tokens":4,"cost":0}},"clientModelCosts":{"codex":{"model-x":0}}}],"monthly":[],"summary":{"favoriteModel":"vendor/model-x","totalTokens":4}}"""
        val envelope = buildJsonObject { put("history", body) }.toString()
        val restoredBody = Json.parseToJsonElement(envelope).jsonObject.getValue("history").jsonPrimitive.content
        assertEquals(body, restoredBody)
        val raw = HistoryParser.parse(restoredBody)
        assertEquals(HistoryParser.parse(body), raw)
        assertEquals(listOf("vendor/model-x", "model-x"), raw.observedModelIds)
        val displayed = DashboardHistoryPresentation.project(raw, duplicateRules)
        assertEquals(listOf("model-x"), displayed.daily.single().perModel.keys.toList())
        assertEquals(listOf("vendor/model-x"), HistoryParser.parse(restoredBody).daily.single().perModel.keys.toList())
        assertNull(raw.daily.single().perModel.getValue("vendor/model-x").displayTokens)
    }

    @Test fun modelPresentationNeverChangesToolChartCandlesHeatOrSourceLifetimeCounters() {
        val raw = sample()
        val shown = DashboardHistoryPresentation.project(raw, duplicateRules)
        assertEquals(BigInteger.TEN, DashboardTrendProjection.project(shown, byModel = true).keyTotals["model-x"])
        assertEquals(DashboardTrendProjection.project(raw), DashboardTrendProjection.project(shown).copy(daily = raw.daily))
        val before = DashboardTrendProjection.project(raw, mode = DashboardTrendMode.KLINE)
        val after = DashboardTrendProjection.project(shown, mode = DashboardTrendMode.KLINE)
        assertEquals(before.candles, after.candles)
        val today = LocalDate.of(2026, 10, 11)
        val original = DashboardOverviewProjection.project(raw, today)
        val displayed = DashboardOverviewProjection.project(shown, today)
        assertEquals(original.tools, displayed.tools)
        assertEquals(original.cells.map { it.tokenLevel to it.costLevel }, displayed.cells.map { it.tokenLevel to it.costLevel })
        assertEquals(raw.summary!!.totalTokens, shown.summary!!.totalTokens)
        assertEquals("model-x", displayed.summary!!.favoriteModel)
    }
}
