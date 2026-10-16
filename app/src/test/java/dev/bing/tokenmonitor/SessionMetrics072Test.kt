package dev.bing.tokenmonitor

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class SessionMetrics072Test {
    private val now=1792126800000L
    private fun raw(sessions:String)=SessionListDisplay.raw(HubParser.parse("""{"periods":{"today":{"sessions":$sessions}}}""").periods.getValue("today").sessions,now,zone=ZoneId.of("UTC"))
    private fun group(sessions:String)=SessionListDisplay.grouped(raw(sessions),now=now).single()
    @Test fun weightedFamilyCountersCapEachTimedMemberBeforeSummingAndCountEachKeyOnce() {
        val rows=raw("""{
            "codex:p":{"client":"codex","sessionId":"p","totalTokens":100,"messageCount":2,"inputTokens":10,"cacheReadTokens":90,"cacheWriteTokens":0,"outputTokens":10,"timedOutputTokens":1000,"timedDurationMs":1000,"lastUsedAt":"2026-10-16T01:00:00Z"},
            "codex:c":{"client":"codex","sessionId":"c","parentSessionId":"p","totalTokens":200,"messageCount":8,"inputTokens":900,"cacheReadTokens":100,"cacheWriteTokens":0,"outputTokens":100,"timedOutputTokens":100,"timedDurationMs":9000,"lastUsedAt":"2026-10-16T02:00:00Z"},
            "codex:u":{"client":"codex","sessionId":"u","parentSessionId":"c","totalTokens":300,"messageCount":1,"inputTokens":100,"cacheReadTokens":0,"cacheWriteTokens":0,"cacheReported":true,"outputTokens":9999,"timedOutputTokens":9999,"timedDurationMs":0,"lastUsedAt":"2026-10-16T03:00:00Z"}
        }""")
        val family=SessionRelationshipGroups.group(rows+rows.first(),"Grok",{"$it"},now).single()
        val metrics=family.groupMetrics!!
        assertEquals(11.0,metrics.calls!!,0.0)
        assertEquals(190.0/1200*100,metrics.cachePercent!!,1e-9)
        assertEquals(11.0,metrics.tokensPerSecond!!,0.0)
        assertEquals(3,family.reviews.size)
        assertEquals(600L,family.tokens.toLong())
        assertEquals(SessionDisplay.timestamp("2026-10-16T03:00:00Z"),family.sortTime)
        assertEquals(90.0,SessionListDisplay.cachePercent(rows.single {it.id=="codex:p"}.source!!)!!,0.0)
        assertEquals(10.0,SessionListDisplay.rate(rows.single {it.id=="codex:p"}.source!!),0.0)
    }
    @Test fun continuationAndNestedChildrenUseSameDisjointMembersAsTotals() {
        val family=group("""{
          "codex:p":{"client":"codex","sessionId":"p","nativeSessionId":"11111111-1111-1111-1111-111111111111","totalTokens":10,"messageCount":2},
          "codex:k":{"client":"codex","sessionId":"k","nativeSessionId":"11111111-1111-1111-1111-111111111111","totalTokens":20,"messageCount":3},
          "claude:c":{"client":"claude","sessionId":"c","parentClient":"codex","parentSessionId":"k","totalTokens":30,"messageCount":4},
          "opencode:d":{"client":"opencode","sessionId":"d","parentClient":"claude","parentSessionId":"c","totalTokens":40,"messageCount":5}
        }""")
        assertEquals(100L,family.tokens.toLong());assertEquals(14.0,family.groupMetrics!!.calls!!,0.0)
        assertEquals(4,family.reviews.map {it.id}.distinct().size)
        assertEquals(2,family.reviews.count {it.groupRole=="main"})
        assertNull(family.groupMetrics.cachePercent);assertNull(family.groupMetrics.tokensPerSecond)
    }
    @Test fun missingOldHubFieldsStayUnknownButExplicitColdCacheIsZero() {
        val empty=SessionFamilyMetrics.from(emptyList())
        assertNull(empty.calls);assertNull(empty.cachePercent);assertNull(empty.tokensPerSecond)
        val absent=group("""{"codex:p":{"client":"codex","sessionId":"p","totalTokens":1,"messageCount":3},"codex:c":{"client":"codex","sessionId":"c","parentSessionId":"p","totalTokens":1}}""")
        assertNull(absent.groupMetrics!!.calls);assertNull(absent.groupMetrics.cachePercent);assertNull(absent.groupMetrics.tokensPerSecond)
        assertEquals("Calls unknown · —% · — tok/s",absent.groupMetrics.label())
        val cold=raw("""{"codex:p":{"client":"codex","totalTokens":1,"messageCount":0,"inputTokens":50,"cacheReadTokens":0,"cacheWriteTokens":0,"cacheReported":true}}""").single()
        assertEquals(0.0,SessionListDisplay.cachePercent(cold.source!!)!!,0.0)
        val zero=SessionFamilyMetrics.from(listOf(cold))
        assertEquals(0.0,zero.calls!!,0.0);assertEquals(0.0,zero.cachePercent!!,0.0);assertNull(zero.tokensPerSecond)
        assertTrue(zero.label().startsWith("0 calls · 0%"))
        val zeroInput=cold.copy(source=cold.source.copy(session=cold.source.session!!.copy(accounting=cold.source.session.accounting.copy(input=0.0))))
        assertNull(SessionListDisplay.cachePercent(zeroInput.source!!));assertNull(SessionFamilyMetrics.from(listOf(zeroInput)).cachePercent)
    }
    @Test fun unknownCacheMemberIsNotAssumedToBeCacheMissAndInvalidCountersNeverProduceInfinity() {
        val rows=raw("""{"codex:a":{"client":"codex","totalTokens":1,"messageCount":2,"inputTokens":10,"cacheReadTokens":90},"codex:b":{"client":"codex","totalTokens":1,"messageCount":-1,"inputTokens":100,"timedDurationMs":1000,"timedOutputTokens":10}}""")
        assertNull(SessionFamilyMetrics.from(rows).cachePercent)
        assertNull(SessionFamilyMetrics.from(rows).calls)
        assertNull(SessionFamilyMetrics.from(rows).tokensPerSecond)
        assertFalse(rows.last().source!!.session!!.accounting.callsKnown)
        // A numeric JSON string is not a reported numeric counter, in either title layout.
        for(title in listOf("","Named session")) {
            val stringCounter=raw("""{"codex:string":{"client":"codex","title":"$title","totalTokens":1,"messageCount":"7"}}""").single()
            assertFalse(stringCounter.source!!.session!!.accounting.callsKnown)
            assertFalse((stringCounter.subtitle+stringCounter.activity).contains("7 calls"))
            val unavailable=SessionFamilyMetrics.from(listOf(stringCounter))
            assertNull(unavailable.calls)
            assertTrue(unavailable.label().startsWith("Calls unknown"))
        }
    }
    @Test fun reviewAndGrokSummariesRetainOwnMembersAndExposeMetrics() {
        for((client,kind,id) in listOf(Triple("codex","background-review","a"),Triple("cursor","","sand-subagent-a"))) {
            val family=group("""{"$client:$id":{"client":"$client","sessionId":"$id","sessionKind":"$kind","totalTokens":1,"messageCount":7}}""")
            assertEquals(7.0,family.groupMetrics!!.calls!!,0.0);assertEquals(1,family.reviews.size)
        }
    }
    @Test fun delegatedRootRoleSurvivesNewerUnlinkedContinuationButNotInternalOrConflictingLinks() {
        fun sessions(parent:String,other:String="")="""{
            "codex:aaa":{"client":"codex","sessionId":"aaa","nativeSessionId":"11111111-1111-1111-1111-111111111111","parentSessionId":"$parent","totalTokens":1,"messageCount":3,"lastUsedAt":"2026-10-16T01:00:00Z"},
            "codex:zzz":{"client":"codex","sessionId":"zzz","nativeSessionId":"11111111-1111-1111-1111-111111111111","totalTokens":1,"messageCount":4,"lastUsedAt":"2026-10-16T02:00:00Z"$other}
        }"""
        for(family in listOf(sessions("external"),sessions("external").replace("aaa","temporary").replace("zzz","aaa").replace("temporary","zzz"))) {
            val delegated=group(family)
            assertEquals("subagent",delegated.groupRole)
            assertEquals(7.0,delegated.groupMetrics!!.calls!!,0.0)
        }
        assertNull(group(sessions("zzz")).groupRole)
        SessionListDisplay.grouped(raw(sessions("external",",\"parentSessionId\":\"different\"")),now=now).forEach {assertNull(it.groupRole)}
        val single=SessionListDisplay.grouped(raw("""{"codex:p":{"client":"codex","sessionId":"p","parentSessionId":"external","totalTokens":1}}"""),now=now).single()
        assertEquals("subagent",single.groupRole)
    }
    @Test fun fullSnapshotProjectionAndFreshnessKeepCacheReportingEvidence() {
        val body="""{"periods":{"today":{"sessions":{"codex:a":{"client":"codex","totalTokens":1,"inputTokens":100,"cacheReadTokens":0,"cacheWriteTokens":0,"cacheReported":true}}}}}"""
        val projected=HubJsonProjection.stats(body.byteInputStream()).body
        val full=Json.parseToJsonElement(projected) as kotlinx.serialization.json.JsonObject
        val fresh=StreamMerge.applyStats(full,"freshness",Json.parseToJsonElement("""{"updatedAt":"2026-10-16T01:00:00Z","periods":{"today":{"sessions":{}}}}""") as kotlinx.serialization.json.JsonObject)!!
        for(value in listOf(projected,fresh.toString())) {
            val row=HubParser.parse(value).periods.getValue("today").sessions.single()
            assertTrue(row.session!!.accounting.cacheReported);assertEquals(0.0,SessionListDisplay.cachePercent(row)!!,0.0)
        }
    }
}
