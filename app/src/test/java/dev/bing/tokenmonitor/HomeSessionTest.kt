package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class HomeSessionTest {
    private val fixture=Json.parseToJsonElement(javaClass.getResource("/home-sessions-parity.json")!!.readText()).jsonObject
    private fun reading(row:UsageRow,now:Long,expected:JsonObject) {
        assertEquals(expected["activity"]!!.jsonPrimitive.content,SessionDisplay.activity(row,now))
        val context=SessionDisplay.context(row,now)
        if(expected["context"]==JsonNull) assertNull(context) else {
            val source=expected["context"]!!.jsonObject;assertNotNull(context)
            assertEquals(source["contextTokens"]!!.jsonPrimitive.double,context!!.tokens,0.0)
            assertEquals(source["contextWindow"]!!.jsonPrimitive.double,context.window,0.0)
            assertEquals(source["percentLeft"]!!.jsonPrimitive.int,context.left)
            assertEquals(source["percentUsed"]!!.jsonPrimitive.int,context.used)
            assertEquals(source["tone"]!!.jsonPrimitive.content,context.tone)
        }
        val cache=SessionDisplay.cache(row,now)
        if(expected["cache"]==JsonNull) assertNull(cache) else {
            val source=expected["cache"]!!.jsonObject;assertNotNull(cache)
            assertEquals(source["expiresAt"]!!.jsonPrimitive.long,cache!!.expiresAt)
            assertEquals(source["ttlSeconds"]!!.jsonPrimitive.int,cache.ttlSeconds)
            assertEquals(source["minutes"]!!.jsonPrimitive.int,cache.minutes)
        }
    }
    @Test fun original76HomeSelectionsKeepRecentFiveAndAllRunningUsingMonthThenToday() {
        val cases=fixture["cases"]!!.jsonArray;assertEquals(76,cases.size)
        cases.forEachIndexed {index,element->
            val spec=element.jsonObject;val snapshot=HubParser.parse(spec["input"].toString());val now=spec["now"]!!.jsonPrimitive.long
            val actual=SessionDisplay.recent(snapshot,now);val expected=spec["expected"]!!.jsonArray
            assertEquals("selection $index",expected.map {it.jsonObject["key"]!!.jsonPrimitive.content},actual.map {it.id})
            assertEquals(spec["running"]!!.jsonPrimitive.int,actual.count {SessionDisplay.activity(it,now)=="running"})
            assertEquals(spec["next"]!!.jsonPrimitive.long.takeIf {it!=0L},SessionDisplay.nextStatusAt(actual,now))
            expected.zip(actual).forEach {(source,row)->
                val obj=source.jsonObject;reading(row,now,obj)
                assertEquals(obj["title"]!!.jsonPrimitive.content,SessionDisplay.title(row))
                val models=SessionDisplay.models(row)
                assertEquals(obj["models"]!!.jsonPrimitive.content,when(models.size) {0->"";1->models.keys.single();else->"${models.size} models"})
                val shares=obj["shares"]!!.jsonArray.map {it.jsonArray.map {v->v.jsonPrimitive.content}}
                assertEquals(shares,SessionDisplay.modelShares(row).map {listOf(it.name.ifEmpty {"Unclassified"},it.tokens.toString(),it.share)})
            }
        }
    }
    @Test fun original186StatusContextCacheAndExactExpiryBoundaries() {
        val cases=fixture["individual"]!!.jsonArray;assertEquals(186,cases.size)
        cases.forEach {element->
            val spec=element.jsonObject;val root=buildJsonObject {put("periods",buildJsonObject {put("today",buildJsonObject {put("sessions",buildJsonObject {put("one",spec.getValue("row"))})})})}
            val row=HubParser.parse(root.toString()).periods.getValue("today").sessions.single();val now=spec["now"]!!.jsonPrimitive.long
            reading(row,now,spec)
            assertEquals(spec["next"]!!.jsonPrimitive.long.takeIf {it!=0L},SessionDisplay.nextStatusAt(listOf(row),now))
        }
    }
    @Test fun sessionMetadataDoesNotAdmitTranscriptFieldsAndInvalidPairsNeverShowHeadroom() {
        val snapshot=HubParser.parse("""{"periods":{"today":{"sessions":{"one":{"title":"bad\ncontrol","projectLabel":"Public project","sessionId":"abcdefghijklmnop","client":"codex","lastUsedAt":"2026-10-10T02:00:00Z","contextTokens":"NaN","contextWindow":100000,"promptCache":{"observedAt":"2026-10-10T02:00:00Z","ttlSeconds":301},"models":{"bad":-1,"public":12},"messages":[{"text":"PRIVATE_BODY_SENTINEL"}],"transcript":"PRIVATE_BODY_SENTINEL"}}}}}""")
        val row=snapshot.periods.getValue("today").sessions.single()
        assertEquals("Public project",SessionDisplay.title(row));assertNull(row.session!!.title)
        assertEquals(mapOf("public" to 12L),row.session.models)
        assertFalse(snapshot.toString().contains("PRIVATE_BODY_SENTINEL"));assertNull(SessionDisplay.context(row,1791597600000));assertNull(row.session.promptCache)
    }
    @Test fun original32HomeToolAndModelRankingsUseFivePositiveRowsAndSourceDenominators() {
        val cases=fixture["usageCases"]!!.jsonArray;assertEquals(32,cases.size)
        cases.forEach {element->
            val spec=element.jsonObject;val input=spec["input"]!!.jsonArray.map {it.jsonObject}
            val values=input.map {it["key"]!!.jsonPrimitive.content to java.math.BigInteger(it["value"]!!.jsonPrimitive.content)}
            val labels=input.associate {it["key"]!!.jsonPrimitive.content to it["name"]!!.jsonPrimitive.content}
            val actual=HomeUsageDisplay.rows(values,spec["total"]!!.jsonPrimitive.longOrNull,labels,spec["tool"]!!.jsonPrimitive.boolean,java.util.Locale.ENGLISH)
            val expected=spec["expected"]!!.jsonArray
            assertEquals(expected.map {it.jsonObject["key"]!!.jsonPrimitive.content},actual.map {it.id})
            expected.zip(actual).forEach {(source,row)->
                val obj=source.jsonObject;assertEquals(obj["name"]!!.jsonPrimitive.content,row.name)
                assertEquals(obj["value"]!!.jsonPrimitive.long,row.tokens.toLong());assertEquals(obj["share"]!!.jsonPrimitive.double,row.share,1e-12)
            }
        }
    }
}
