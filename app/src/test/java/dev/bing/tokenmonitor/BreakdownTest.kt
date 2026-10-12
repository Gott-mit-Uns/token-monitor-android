package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class BreakdownTest {
    @Test fun keepsDesktopCostsComponentsAndToolModelAttribution() {
        val value = HubParser.parse("""{"periods":{"today":{"totalTokens":120,"capabilities":{"tokenComponents":true},"clients":{"codex":120},"clientCosts":{"codex":0.0125},"clientCacheReads":{"codex":80},"clientCacheWrites":{},"clientOutputs":{"codex":10},"clientUnclassifiedTokens":{},"clientModels":{"codex":{"gpt":120}},"clientModelCosts":{"codex":{"gpt":0.0125}},"models":{"gpt":120},"modelCosts":{"gpt":0.0125}}}}""").periods.getValue("today")
        val codex = value.toolDetails.getValue("codex")
        assertEquals("0.0125", codex.cost)
        assertEquals(0L, codex.components.cacheWrite)
        assertEquals(30L, codex.components.inputMiss)
        assertEquals(120L, codex.models.getValue("gpt"))
        assertEquals("0.0125", codex.modelCosts.getValue("gpt"))
        assertEquals("0.0125", value.modelDetails.getValue("gpt").cost)
        assertNull(value.modelDetails.getValue("gpt").components.inputMiss)
    }
    @Test fun absentSourceComponentsAndInvalidPricesStayUnknown() {
        val p = HubParser.parse("""{"periods":{"today":{"clients":{"codex":120},"clientCosts":{"codex":-1},"clientOutputs":{"codex":130}}}}""").periods.getValue("today")
        assertNull(p.toolDetails.getValue("codex").cost)
        assertNull(p.toolDetails.getValue("codex").components.output)
        assertNull(p.toolDetails.getValue("codex").components.cacheRead)
        assertNull(p.toolDetails.getValue("codex").components.inputMiss)
    }
    @Test fun invalidPresentComponentIsNotAssumedToBeZero() {
        val p = HubParser.parse("""{"periods":{"today":{"capabilities":{"tokenComponents":true},"clients":{"codex":120},"clientCacheReads":{"codex":-1},"clientCacheWrites":{},"clientOutputs":{},"clientUnclassifiedTokens":{}}}}""").periods.getValue("today")
        assertNull(p.toolDetails.getValue("codex").components.cacheRead)
        assertNull(p.toolDetails.getValue("codex").components.inputMiss)
        assertEquals(0L, p.toolDetails.getValue("codex").components.cacheWrite)
    }
    @Test fun modelAliasesPreserveKnownCostAndDoNotInventUnknownParts() {
        val rules = ModelNames(mapOf("vendor/a" to "a"), "off")
        val parts = TokenComponents(80, 0, 10, 0, 10)
        val map = mapOf("vendor/a" to UsageBreakdown(100, "0.125", parts), "a" to UsageBreakdown(100, "0.25", parts))
        val merged = rules.projectDetails(map, map.keys).getValue("a")
        assertEquals(200L, merged.tokens)
        assertEquals("0.375", merged.cost)
        assertEquals(160L, merged.components.cacheRead)
        val unknown = rules.projectDetails(map + ("a" to map.getValue("a").copy(cost = null)), map.keys).getValue("a")
        assertNull(unknown.cost)
    }
}
