package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class Session069Test {
    private val fixture=Json.parseToJsonElement(javaClass.getResource("/session-069-parity.json")!!.readText()).jsonObject
    private fun rows(sessions:JsonElement)=HubParser.parse(buildJsonObject {put("periods",buildJsonObject {put("today",buildJsonObject {put("sessions",sessions)})})}.toString()).periods.getValue("today").sessions
    @Test fun statesMatchFixed069IncludingExpiryUnknownFutureAndArchive() {
        fixture.getValue("states").jsonArray.forEach {value->
            val c=value.jsonObject;val row=rows(buildJsonObject {put("codex:a",c.getValue("data"))}).single();val now=c.getValue("now").jsonPrimitive.long
            assertEquals(c.getValue("name").jsonPrimitive.content,c.getValue("expected").jsonPrimitive.content,SessionDisplay.activity(row,now))
            row.session?.liveActivity?.let {reading->
                if(reading.state!="unknown" && reading.observedAt<=now && now-reading.observedAt<30000 && row.session.archived!=true)
                    assertTrue(SessionDisplay.nextStatusAt(listOf(row),now)!! <= reading.observedAt+30000)
            }
        }
    }
    @Test fun graphMembersAndDisjointAccountingMatchUnmodifiedDesktopFunctions() {
        fixture.getValue("cases").jsonArray.forEach {value->
            val c=value.jsonObject;val now=c.getValue("now").jsonPrimitive.long
            val raw=SessionListDisplay.raw(rows(c.getValue("sessions")),now)
            val grouped=SessionListDisplay.grouped(raw,now=now)
            val expected=c.getValue("expected").jsonArray.associateBy {it.jsonObject.getValue("id").jsonPrimitive.content}
            assertEquals(c.getValue("name").jsonPrimitive.content,expected.keys,grouped.map {it.id}.toSet())
            grouped.forEach {group->
                val e=expected.getValue(group.id).jsonObject
                assertEquals(BigInteger(e.getValue("tokens").jsonPrimitive.content),group.tokens)
                assertEquals(c.getValue("name").jsonPrimitive.content+":"+group.id,0,BigDecimal(e.getValue("cost").jsonPrimitive.content).compareTo(BigDecimal(group.cost)))
                assertEquals(BigInteger(e.getValue("unpriced").jsonPrimitive.content),group.unpricedTotal ?: BigInteger.ZERO)
                val members=e.getValue("members").jsonArray.map {m->m.jsonObject.getValue("id").jsonPrimitive.content to m.jsonObject["role"]?.takeUnless {it==JsonNull}?.jsonPrimitive?.content}
                assertEquals(members,group.reviews.map {it.id to it.groupRole})
            }
            val expanded=grouped.flatMap {if(it.review) it.reviews else listOf(it)}
            assertEquals(raw.map {it.id}.sorted(),expanded.map {it.id}.sorted())
            assertEquals(expanded.size,expanded.map {it.id}.distinct().size)
            assertEquals(raw.fold(BigInteger.ZERO) {sum,row->sum+row.tokens},grouped.fold(BigInteger.ZERO) {sum,row->sum+row.tokens})
        }
    }
    @Test fun malformedOptionalFieldsRemainAbsentAndZeroRowsAreNotInvented() {
        val row=rows(Json.parseToJsonElement("""{"codex:a":{"client":"codex","sessionId":"a","totalTokens":1,"nativeSessionId":"invalid","t3ThreadId":"text with spaces","waitingForInput":"true","liveActivity":{"state":"approval","observedAt":"bad"}}}""")).single()
        assertNull(row.session!!.nativeSessionId);assertNull(row.session.t3ThreadId);assertNull(row.session.waitingForInput);assertNull(row.session.liveActivity)
        val missing=rows(Json.parseToJsonElement("""{"codex:b":{"client":"codex","sessionId":"b","liveActivity":{"state":"running","observedAt":"2026-10-12T12:00:00Z"}}}"""))
        assertNull(missing.single().tokens);assertTrue(SessionListDisplay.raw(missing,0).isEmpty())
    }
    @Test fun groupingKeepsLargeCountersModelBreakdownAndDuplicateKeysCountedOnce() {
        val raw=SessionListDisplay.raw(rows(Json.parseToJsonElement("""{"codex:p":{"client":"codex","sessionId":"p","totalTokens":9223372036854775807,"unpricedTokens":9223372036854775807,"models":{"m":9223372036854775807}},"codex:c":{"client":"codex","sessionId":"c","parentSessionId":"p","totalTokens":1,"unpricedTokens":1,"models":{"m":1}}}""")),0)
        val group=SessionRelationshipGroups.group(raw+raw.first(),"Grok Bot",{"$it"},0).single()
        val total=BigInteger.valueOf(Long.MAX_VALUE)+BigInteger.ONE
        assertEquals(total,group.tokens);assertEquals(total,group.unpricedTotal);assertEquals(total,group.groupModels["m"])
        assertEquals(2,group.reviews.size)
    }
    @Test fun fullSnapshotTitleUpdatesAndReasoningNeverReaddsAuthoritativeTotals() {
        fun body(title:String)="""{"periods":{"today":{"totalTokens":100,"reasoningTokens":30,"sessions":{"antigravity:a":{"client":"antigravity","sessionId":"a","title":"$title","totalTokens":100,"reasoningTokens":30,"outputTokens":50,"timedOutputTokens":40,"timedDurationMs":2000}}}}}"""
        val before=HubParser.parse(HubJsonProjection.stats(body("Before").byteInputStream()))
        val after=HubParser.parse(HubJsonProjection.stats(body("After").byteInputStream()))
        assertEquals("Before",before.periods.getValue("today").sessions.single().session!!.title)
        val period=after.periods.getValue("today");val session=period.sessions.single()
        assertEquals("After",session.session!!.title);assertEquals(100L,period.tokens);assertEquals(100L,session.tokens)
        assertEquals(20.0,SessionListDisplay.rate(session),1e-9)
        assertEquals(100L,HistoryParser.parse("""{"daily":[{"date":"2026-10-12","tokens":100,"reasoningTokens":30}]}""").daily.single().tokens)
        assertEquals(0.0,SessionListDisplay.rate(session.copy(session=session.session.copy(accounting=SessionAccounting()))),1e-9)
    }
    @Test fun freshnessDoesNotRewriteSessionRelationshipsOrTitles() {
        val old=Json.parseToJsonElement("""{"periods":{"today":{"sessions":{"codex:a":{"client":"codex","totalTokens":1,"waitingForInput":true,"nativeSessionId":"11111111-1111-1111-1111-111111111111","title":"Original"}}}}}""").jsonObject
        val fresh=StreamMerge.applyStats(old,"freshness",Json.parseToJsonElement("""{"periods":{"today":{"sessions":{}}},"updatedAt":"2026-10-12T12:00:00Z"}""").jsonObject)!!
        val row=HubParser.parse(fresh.toString()).periods.getValue("today").sessions.single()
        assertTrue(row.session!!.waitingForInput==true);assertEquals("Original",row.session.title);assertNotNull(row.session.nativeSessionId)
    }
}
