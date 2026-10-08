package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.*
import io.github.theminionooo.tokenmonitor.data.storage.DesktopOptions
import org.junit.Assert.*
import org.junit.Test

class DesktopPresentationTest {
    private fun snapshot(): HubSnapshot {
        val period = UsagePeriod(totalTokens = 100, costUsd = 3.0,
            clients = mapOf("codex" to 100L), clientCosts = mapOf("codex" to 3.0),
            models = mapOf("model-a" to 40L, "model-b" to 60L), modelCosts = mapOf("model-a" to 1.0, "model-b" to 2.0),
            modelCacheWrites = mapOf("model-a" to 3L, "model-b" to 7L),
            clientModels = mapOf("codex" to mapOf("model-a" to 40L, "model-b" to 60L)),
            clientModelCosts = mapOf("codex" to mapOf("model-a" to 1.0, "model-b" to 2.0)),
            sessions = listOf(SessionUsage("id", "PRIVATE TITLE", "codex", "project", 100, 3.0, listOf("model-a", "model-b"), 1, "", "")))
        return HubSnapshot(stats = HubStats(periods = mapOf("today" to period)), history = HubHistory(daily = listOf(
            HistoryPoint("2026-10-09", 100, 3.0, perModel = mapOf("model-a" to HistoryAttribution(40, 1.0), "model-b" to HistoryAttribution(60, 2.0)))
        )))
    }
    @Test fun aliasesMergeEveryReportedModelDimensionAndRetainRawSnapshot() {
        val raw = snapshot()
        val displayed = presentSnapshot(raw, DesktopOptions(modelAliases = mapOf("model-a" to "merged", "model-b" to "merged")))
        assertEquals(mapOf("merged" to 100L), displayed.today.models)
        assertEquals(mapOf("merged" to 3.0), displayed.today.modelCosts)
        assertEquals(mapOf("merged" to 10L), displayed.today.modelCacheWrites)
        assertEquals(mapOf("merged" to 100L), displayed.today.clientModels["codex"])
        assertEquals(listOf("merged"), displayed.today.sessions.first().modelNames)
        assertEquals(100L, displayed.history.daily.first().perModel["merged"]?.tokens)
        assertEquals(raw.today.totalTokens, displayed.today.totalTokens)
        assertEquals(2, raw.today.models.size)
    }
    @Test fun hidingDoesNotChangeTotalsHistoryOrSource() {
        val raw = snapshot()
        val displayed = visibleUsage(raw.today, DesktopOptions(hiddenTools = setOf("codex"), hiddenModels = setOf("model-a")))
        assertTrue(displayed.clients.isEmpty())
        assertFalse("model-a" in displayed.models)
        assertEquals(100L, displayed.totalTokens)
        assertEquals(3.0, displayed.costUsd, 0.0)
        assertEquals(1, raw.today.clients.size)
    }
    @Test fun conversionIsExplicitAndUsdIgnoresRate() {
        assertEquals("CN¥21.00", convertedCost(3.0, DesktopOptions(currency = "CNY", usdRate = 7.0)))
        assertEquals("$3.00", convertedCost(3.0, DesktopOptions(currency = "USD", usdRate = 7.0)))
    }
    @Test fun exportsNeverCarrySessionTitlesOrConnectionFields() {
        listOf("json", "csv").forEach { format ->
            val text = exportUsage(snapshot(), format)
            assertFalse(text.contains("PRIVATE TITLE"))
            assertFalse(text.contains("secret"))
            assertTrue(text.contains("100"))
            assertTrue(text.contains("model-a"))
        }
    }
    @Test fun csvEscapesNamesAndNeutralizesFormulas() {
        val raw = snapshot()
        val sample = raw.copy(stats = raw.stats.copy(periods = mapOf("today" to raw.today.copy(models = mapOf(" =HYPERLINK(\"bad\")" to 100L)))))
        assertTrue(exportUsage(sample, "csv").contains("' =HYPERLINK(\"\"bad\"\")"))
    }
    @Test fun aliasesAreOneStepAndCanBeRemovedWithoutChangingCache() {
        assertEquals("b", modelAlias("a", DesktopOptions(modelAliases = mapOf("a" to "b", "b" to "c"))))
        assertEquals("a", modelAlias("a", DesktopOptions()))
    }
    @Test fun cacheWriteIsIndependentAndComponentsConserveTotal() {
        val c = tokenComponents(100, 30, 20, 25, 5)
        assertEquals(20L, c.cacheMiss)
        assertEquals(20L, c.cacheWrite)
        assertEquals(100L, c.cacheRead + c.cacheMiss + c.cacheWrite + c.output + c.unclassified)
        assertEquals(30.0 / 70 * 100, c.hitPercent, 0.00001)
    }
    @Test fun malformedComponentsAreBoundedAndNeverNegative() {
        val c = tokenComponents(10, 100, -3, 9, -5)
        assertEquals(10L, c.cacheRead)
        assertEquals(0L, c.cacheMiss)
        assertEquals(0L, c.cacheWrite)
        assertEquals(0L, c.output)
    }
}
