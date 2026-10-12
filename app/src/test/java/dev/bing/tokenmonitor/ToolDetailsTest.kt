package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ToolDetailsTest {
    private fun fixture()=javaClass.classLoader!!.getResourceAsStream("tool-detail-parity.json")!!.bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
    @Test fun percentageAndInputShareLabelsMatchTheActualDesktopFunctions() {
        fixture().getValue("labels").jsonArray.forEach { raw ->
            val row=raw.jsonObject
            val input=when(row.getValue("kind").jsonPrimitive.content) { "nan" -> Double.NaN;"infinity" -> Double.POSITIVE_INFINITY;"negative-infinity" -> Double.NEGATIVE_INFINITY;else -> row.getValue("value").jsonPrimitive.double }
            assertEquals(row.getValue("expected").jsonPrimitive.content,ToolDetails.percentLabel(input))
        }
        fixture().getValue("inputs").jsonArray.forEach { raw ->
            val row=raw.jsonObject;val input=row.getValue("input").jsonObject
            fun count(key: String)=input.getValue(key).jsonPrimitive.long
            val parts=DesktopTokenParts.source(count("totalTokens"),count("cacheReadTokens"),count("outputTokens"),count("unclassifiedTokens"))
            val counts=TokenComponents(parts.cacheRead,null,parts.output,parts.unclassified,parts.cacheMiss)
            val shares=ToolDetails.inputPercentages(counts);val wanted=row.getValue("percent").jsonObject
            assertEquals(wanted.getValue("hit").jsonPrimitive.double,shares.first!!,0.00000001)
            assertEquals(wanted.getValue("miss").jsonPrimitive.double,shares.second!!,0.00000001)
            val labels=row.getValue("labels").jsonObject;val native=ToolDetails.tokenRows(counts)
            assertEquals(labels.getValue("hit").jsonPrimitive.content,native.first { it.key=="hit" }.percent)
            assertEquals(labels.getValue("miss").jsonPrimitive.content,native.first { it.key=="miss" }.percent)
        }
    }
    @Test fun modelRowsSharesOrderingAndNoDataCasesMatchDesktop() {
        fixture().getValue("models").jsonArray.forEach { raw ->
            val row=raw.jsonObject;val input=row.getValue("input")
            val period=HubParser.parse(buildJsonObject { put("periods",buildJsonObject { put("today",input) }) }.toString()).periods.getValue("today")
            val models=ToolDetails.project(period.toolDetails["codex"]).models
            val expected=row.getValue("expected").jsonArray
            assertEquals(expected.map { it.jsonObject.getValue("key").jsonPrimitive.content },models.map { it.key })
            expected.zip(models).forEach { (raw,actual) ->
                val wanted=raw.jsonObject
                assertEquals(wanted.getValue("value").jsonPrimitive.long,actual.usage.tokens ?: 0)
                assertEquals(wanted.getValue("cost").jsonPrimitive.double,actual.usage.cost?.toDouble() ?: 0.0,0.0000001)
                assertEquals((wanted["percentLabel"] as? JsonPrimitive)?.contentOrNull,actual.percent)
                assertEquals(wanted.getValue("unattributed").jsonPrimitive.boolean,actual.unattributed)
                assertEquals((wanted["unpricedTokens"] as? JsonPrimitive)?.longOrNull ?: 0,actual.usage.unpriced ?: 0)
            }
        }
    }
    @Test fun modelAvailabilityDistinguishesMissingEmptyAndInvalidWithoutInventingTotals() {
        fun parse(fields: String)=HubParser.parse("""{"periods":{"today":{"clients":{"codex":50},"clientCosts":{"codex":1}$fields}}}""").periods.getValue("today").toolDetails.getValue("codex")
        val missing=parse("");assertFalse(missing.modelDataAvailable);assertTrue(ToolDetails.project(missing).models.isEmpty())
        val empty=parse(",\"clientModels\":{\"codex\":{}}")
        assertTrue(empty.modelDataAvailable);assertTrue(ToolDetails.project(empty).models.single().unattributed)
        val invalid=parse(",\"clientModels\":{\"codex\":null}");assertFalse(invalid.modelDataAvailable);assertTrue(ToolDetails.project(invalid).models.isEmpty())
        val unpriced=parse(",\"clientModelUnpricedTokens\":{\"codex\":{\"unknown\":10}}")
        assertTrue(unpriced.modelDataAvailable);assertNull(ToolDetails.project(unpriced).models.first { !it.unattributed }.usage.tokens)
    }
    @Test fun aliasGroupingPreservesModelSharesAndCannotRenameTheSyntheticRemainder() {
        val source=UsageBreakdown(100,"1",TokenComponents(50,9,10,20,20),mapOf("a" to 40,"b" to 30),mapOf("a" to "0.4","b" to "0.3"),40,mapOf("a" to 10,"b" to 20),true)
        val names=ModelNames(mapOf("a" to "public","b" to "public",UsageAttribution.UNATTRIBUTED to "wrong"),"off")
        val result=ToolDetails.project(source,names)
        assertEquals(listOf("public",UsageAttribution.UNATTRIBUTED),result.models.map { it.key })
        assertEquals(listOf("70%","30%"),result.models.map { it.percent })
        assertEquals(30L,result.models.first().usage.unpriced)
        assertEquals(10L,result.models.last().usage.unpriced)
        assertTrue(result.models.last().unattributed)
        assertEquals(mapOf("a" to 40L,"b" to 30L),source.models)
        assertEquals("1",source.cost)
    }
    @Test fun unknownSharesStayUnknownAndKnownCountsCannotOverflowOrHideSmallShares() {
        assertNull(ToolDetails.percentLabel(null))
        val partial=ToolDetails.project(UsageBreakdown(100,null,TokenComponents(1,null,null,null,null)))
        assertNull(partial.tokens.first().percent);assertNull(partial.tokens[1].percent)
        val full=ToolDetails.project(UsageBreakdown(100,null,TokenComponents(0,0,0,0,100)))
        assertEquals("0%",full.tokens.first().percent);assertEquals("100%",full.tokens[1].percent)
        assertFalse(full.tokens.any { it.key=="unclassified" })
        val high=ToolDetails.inputPercentages(TokenComponents(Long.MAX_VALUE-1,null,0,0,1))
        assertEquals("100%",ToolDetails.percentLabel(high.first));assertEquals("<1%",ToolDetails.percentLabel(high.second))
        val onlyModels=ToolDetails.project(UsageBreakdown(100,null,TokenComponents(null,null,null,null,null),mapOf("public" to 100)))
        assertFalse(onlyModels.canSwitch);assertEquals("models",onlyModels.mode("tokens"))
        assertEquals("tokens",full.mode("models"))
    }
}
