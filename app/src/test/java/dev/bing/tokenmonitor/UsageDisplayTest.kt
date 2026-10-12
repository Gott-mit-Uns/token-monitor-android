package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class UsageDisplayTest {
    private fun fixture() = javaClass.classLoader!!.getResourceAsStream("usage-display-parity.json")!!.bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
    @Test fun componentOutputsMatchDesktopAndMissingInputsRemainUnknownInTheViewer() {
        fixture().getValue("components").jsonArray.forEach { raw ->
            val row=raw.jsonObject;val input=row.getValue("input").jsonObject;val expected=row.getValue("expected").jsonObject
            fun count(key: String)=(input[key] as? JsonPrimitive)?.longOrNull ?: 0
            val parts=DesktopTokenParts.source(count("totalTokens"),count("cacheReadTokens"),count("outputTokens"),count("unclassifiedTokens"))
            assertEquals(expected.getValue("cacheRead").jsonPrimitive.long,parts.cacheRead)
            assertEquals(expected.getValue("cacheMiss").jsonPrimitive.long,parts.cacheMiss)
            assertEquals(expected.getValue("output").jsonPrimitive.long,parts.output)
            assertEquals(expected.getValue("unclassified").jsonPrimitive.long,parts.unclassified)
            assertEquals(expected.getValue("hitPct").jsonPrimitive.int,parts.hitPct)
            assertEquals(expected.getValue("missPct").jsonPrimitive.int,parts.missPct)
        }
        assertNull(DesktopTokenParts.known(100,TokenComponents(null,null,10,0,null)).inputMiss)
        val known=DesktopTokenParts.known(100,TokenComponents(50,9,10,20,null))
        assertEquals(20L,known.inputMiss)
        assertEquals(9L,known.cacheWrite)
    }
    @Test fun attributionAndToolModelRemaindersMatchDesktopIncludingTinyInvisibleCosts() {
        fun compare(expected: JsonArray, result: Map<String,UsageBreakdown>) {
            assertEquals(expected.map { it.jsonObject.getValue("key").jsonPrimitive.content }.toSet(),result.keys)
            expected.forEach { raw ->
                val row=raw.jsonObject;val value=result.getValue(row.getValue("key").jsonPrimitive.content)
                assertEquals(row.getValue("value").jsonPrimitive.long,value.tokens ?: 0)
                assertEquals(row.getValue("cost").jsonPrimitive.double,value.cost?.toDouble() ?: 0.0,0.00000001)
                assertEquals((row["unpricedTokens"] as? JsonPrimitive)?.longOrNull ?: 0,value.unpriced ?: 0)
            }
        }
        fixture().getValue("attribution").jsonArray.forEach { raw ->
            val row=raw.jsonObject;val options=row.getValue("options").jsonObject
            val period=HubParser.parse(buildJsonObject { put("periods",buildJsonObject { put("today",buildJsonObject {
                put("clients",row.getValue("values"));put("clientCosts",row.getValue("costs"))
                options["unpricedTokens"]?.let { put("clientUnpricedTokens",it) }
                options["totalValue"]?.let { put("totalTokens",it) };options["totalCost"]?.let { put("costUsd",it) }
                options["totalUnpricedTokens"]?.let { put("unpricedTokens",it) }
            }) }) }.toString()).periods.getValue("today")
            compare(row.getValue("visible").jsonArray,UsageAttribution.rows(period.tools,period.toolDetails,period.tokens,period.cost,period.unpriced))
        }
        fixture().getValue("models").jsonArray.forEach { raw ->
            val row=raw.jsonObject
            val period=HubParser.parse(buildJsonObject { put("periods",buildJsonObject { put("today",row.getValue("input")) }) }.toString()).periods.getValue("today")
            compare(row.getValue("expected").jsonArray,UsageAttribution.toolModels(period.toolDetails.getValue("codex")))
        }
    }
    @Test fun unpricedOnlyRowsAndAliasGroupsRetainKnownUnpricedWithoutInventingCounters() {
        val value=HubParser.parse("""{"periods":{"today":{"clientUnpricedTokens":{"codex":100},"modelUnpricedTokens":{"public":100},"clientModelUnpricedTokens":{"codex":{"public":100}}}}}""").periods.getValue("today")
        assertNull(value.toolDetails.getValue("codex").tokens)
        assertEquals(100L,value.toolDetails.getValue("codex").unpriced)
        assertEquals(100L,value.toolDetails.getValue("codex").modelUnpriced.getValue("public"))
        assertEquals(100L,value.modelDetails.getValue("public").unpriced)
        val rules=ModelNames(mapOf("vendor/public" to "public"),"off")
        val details=mapOf("vendor/public" to UsageBreakdown(100,"0.8",TokenComponents(null,null,null,null,null),unpriced=20),
            "public" to UsageBreakdown(100,"0.2",TokenComponents(null,null,null,null,null),unpriced=30))
        assertEquals(50L,rules.projectDetails(details,details.keys).getValue("public").unpriced)
        assertEquals(20L,rules.projectDetails(details+ ("public" to details.getValue("public").copy(unpriced=null)),details.keys).getValue("public").unpriced)
    }
}
