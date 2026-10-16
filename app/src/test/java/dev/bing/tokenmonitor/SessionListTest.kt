package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.time.ZoneId

class SessionListTest {
    private val fixture=Json.parseToJsonElement(javaClass.getResource("/session-list-parity.json")!!.readText()).jsonObject
    private val ordering=Json.parseToJsonElement(javaClass.getResource("/session-group-order-069.json")!!.readText()).jsonObject
    private val zone=ZoneId.of("Asia/Shanghai")
    private fun snapshot(input:JsonElement)=HubParser.parse(buildJsonObject {put("periods",buildJsonObject {put("today",input)})}.toString())
    private fun JsonObject.string(key:String)=get(key)!!.jsonPrimitive.content
    private fun expected(row:SessionListRow)=buildJsonObject {
        put("id",row.id);put("name",row.name);put("subtitle",row.subtitle);put("activity",row.activity);put("detail",row.detail)
        put("value",row.tokens.toLong());put("cost",row.cost);put("sortTime",row.sortTime)
    }
    @Test fun rowsAccountingAndOrderingMatchOriginalDesktop() {
        fixture["cases"]!!.jsonArray.forEachIndexed {index,element->
            val case=element.jsonObject;val parsed=snapshot(case["input"]!!).periods.getValue("today").sessions
            val native=SessionListDisplay.raw(parsed,case["now"]!!.jsonPrimitive.long,case["hidden"]!!.jsonPrimitive.boolean,label={when(it) {"codex"->"Codex";"claude"->"Claude";else->it}},zone=zone)
            // v0.72 Android rejects malformed negative counters instead of inventing a 0% cache hit.
            // These four pinned 0.69 golden cases intentionally contained input=-5/read=-1.
            val malformedCache=index in 116..119
            if(malformedCache) {
                assertEquals(-5.0,parsed.single().session!!.accounting.input,0.0)
                assertEquals(-1.0,parsed.single().session!!.accounting.cacheRead,0.0)
            }
            val expectedRows=if(malformedCache) JsonArray(case["expected"]!!.jsonArray.map {element->
                JsonObject(element.jsonObject.mapValues {(key,value)->
                    if(key in setOf("subtitle","activity")) JsonPrimitive(value.jsonPrimitive.content.replace(" · 0%","")) else value
                })
            }) else case["expected"]!!.jsonArray
            assertEquals("row $index",expectedRows,JsonArray(native.map(::expected)))
            val source=parsed.single();assertEquals("rate $index",case["rate"]!!.jsonPrimitive.double,SessionListDisplay.rate(source),1e-9)
            val cache=case["cache"]?.takeUnless {malformedCache}?.takeUnless {it==JsonNull}?.jsonPrimitive?.double
            if(cache==null) assertNull(SessionListDisplay.cachePercent(source)) else assertEquals(cache,SessionListDisplay.cachePercent(source)!!,1e-9)
        }
        val tied=snapshot(Json.parseToJsonElement("""{"sessions":{"old":{"totalTokens":99,"lastUsedAt":"2026-10-09"},"b":{"totalTokens":5,"costUsd":0.2,"title":"Beta"},"a":{"totalTokens":5,"costUsd":0.2,"title":"Alpha"},"cost":{"totalTokens":5,"costUsd":1},"large":{"totalTokens":6}}}""")).periods.getValue("today").sessions
        assertEquals(listOf("old","large","cost","a","b"),SessionListDisplay.raw(tied,0).map {it.id})
    }
    @Test fun identifierAndReviewGroupMatchOriginal() {
        fixture["ids"]!!.jsonArray.forEach {item->val row=item.jsonObject;assertEquals(row.string("expected"),SessionListDisplay.idLabel(row.string("input")))}
        fixture["groups"]!!.jsonArray.forEachIndexed {index,element->
            val case=element.jsonObject
            val clock=fixture["cases"]!!.jsonArray.first().jsonObject["now"]!!.jsonPrimitive.long
            val raw=SessionListDisplay.raw(snapshot(case["input"]!!).periods.getValue("today").sessions,clock,label={"Codex"},zone=zone)
            val grouped=SessionListDisplay.grouped(raw,count={"$it background runs"},compact={it.toString()},now=clock,zone=zone)
            val actual=JsonArray(grouped.map {row->buildJsonObject {
                put("id",row.id);put("name",row.name);put("subtitle",row.subtitle);put("detail",row.detail);put("value",row.tokens.toLong());put("cost",row.cost);put("sortTime",row.sortTime);put("review",row.review);put("reviews",JsonArray(row.reviews.map {JsonPrimitive(it.id)}))
            }})
            // Decimal sums retain exact wire values; compare original amounts numerically rather than binary floating strings.
            // Retain every legacy value/member assertion; use the pinned 0.69 full-group
            // ordering instead of the old background-review-only helper's append order.
            val byId=case["expected"]!!.jsonArray.associateBy {it.jsonObject.string("id")}
            val expected=ordering["legacyOrders"]!!.jsonArray[index].jsonArray.map {byId.getValue(it.jsonPrimitive.content)}
            assertEquals(expected.size,actual.size)
            actual.zip(expected).forEach {(a,b)->
                assertEquals(b.jsonObject.string("cost").toDouble(),a.jsonObject.string("cost").toDouble(),1e-9)
                assertEquals(JsonObject(b.jsonObject-"cost"),JsonObject(a.jsonObject-"cost"))
            }
        }
    }
    @Test fun mixedReviewRelationshipAndGrokOrderingMatchesPinnedDesktop069() {
        val now=ordering["now"]!!.jsonPrimitive.long
        ordering["cases"]!!.jsonArray.forEach {element->
            val case=element.jsonObject
            val raw=SessionListDisplay.raw(snapshot(case["input"]!!).periods.getValue("today").sessions,now)
            val grouped=SessionListDisplay.grouped(raw,now=now)
            val expected=case["expected"]!!.jsonArray
            assertEquals(case.string("name"),expected.map {it.jsonObject.string("id")},grouped.map {it.id})
            grouped.zip(expected).forEach {(actual,reference)->
                assertEquals(BigInteger(reference.jsonObject["value"]!!.jsonPrimitive.content),actual.tokens)
                assertEquals(0,java.math.BigDecimal(reference.jsonObject.string("cost")).compareTo(java.math.BigDecimal(actual.cost)))
                assertEquals(reference.jsonObject["members"]!!.jsonArray.map {it.jsonPrimitive.content},actual.reviews.map {it.id})
            }
        }
    }
    @Test fun hundredRowPageClampingAndBarScaleMatchOriginal() {
        fixture["pages"]!!.jsonArray.forEach {element->val case=element.jsonObject
            val rows=(0 until case["count"]!!.jsonPrimitive.int).map {SessionListRow(it.toString(),null,"","","","",BigInteger.ONE,"0",null,0)}
            val page=SessionListDisplay.page(rows,case["page"]!!.jsonPrimitive.int);val expected=case["expected"]!!.jsonObject
            assertEquals(expected["rows"],JsonArray(page.rows.map {JsonPrimitive(it.id)}))
            for((key,value) in mapOf("page" to page.page,"pageCount" to page.pageCount,"start" to page.start,"end" to page.end,"total" to page.total)) assertEquals(key,expected[key]!!.jsonPrimitive.int,value)
            assertEquals(expected["paginated"]!!.jsonPrimitive.boolean,page.paginated)
        }
        assertEquals(1f,SessionListDisplay.bar(BigInteger.TEN,BigInteger.TEN),0f)
        assertEquals(.02f,SessionListDisplay.bar(BigInteger.ONE,BigInteger.valueOf(1000)),0f)
        assertEquals(0f,SessionListDisplay.bar(BigInteger.ZERO,BigInteger.TEN),0f)
    }
    @Test fun periodOmissionsAndAccountingNeverAdmitConversationBody() {
        val input="""{"sessionDetailsOmitted":{"today":3,"month":0,"allTime":9},"periods":{"today":{"sessions":{"public":{"totalTokens":100,"messageCount":2,"inputTokens":30,"cacheReadTokens":10,"cacheWriteTokens":10,"outputTokens":50,"timedOutputTokens":45,"timedDurationMs":1000,"messages":"DO_NOT_ADMIT","transcript":"DO_NOT_ADMIT"}}},"month":{},"allTime":{}}}"""
        val snapshot=HubParser.parse(input);assertEquals(3L,snapshot.periods.getValue("today").sessionsOmitted);assertEquals(0L,snapshot.periods.getValue("month").sessionsOmitted);assertEquals(0L,snapshot.periods.getValue("allTime").sessionsOmitted)
        assertEquals(45.0,SessionListDisplay.rate(snapshot.periods.getValue("today").sessions.single()),0.0)
        assertFalse(snapshot.toString().contains("DO_NOT_ADMIT"))
        val raw=UsageRow("public","public",Long.MAX_VALUE,"0","codex",null,null,session=SessionMetadata(kind="background-review"))
        val rows=SessionListDisplay.raw(listOf(raw,raw.copy(id="other")),0)
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE)*BigInteger.TWO,SessionListDisplay.grouped(rows,now=0).single().tokens)
    }
}
