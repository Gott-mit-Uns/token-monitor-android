package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Fixed-data old/new comparison; wall-clock measurements are evidence, not flaky pass thresholds. */
class SessionPerformance070Test {
    private val now=Instant.parse("2026-10-14T04:00:00Z").toEpochMilli()
    private val zone=ZoneId.of("Asia/Shanghai")
    private fun fixture(size:Int,related:Boolean=false,waiting:Boolean=true):Snapshot {
        val month=(0 until size).map {index->
            val n=(index*7919)%size
            val id="codex:s$n"
            val at=Instant.ofEpochMilli(now-(n/5)*60_000L).toString()
            val native=if(related && n in 1..2) "11111111-1111-1111-1111-111111111111" else null
            val meta=SessionMetadata(title=if(n%7==0) null else "Conversation $n",projectLabel="Project $n",
                sessionId=if(related && n>0 && n%211==0) "sand-subagent-$n" else if(n%13==0) "rollout-2026-10-14T12-00-00-11111111-1111-1111-1111-111111111111" else "session-$n",
                startedAt=at,kind=if(related && n%97==0) "background-review" else if(n%11==0) "future-kind" else null,
                archived=n%17==0,models=if(related && n>0 && n%211==0) mapOf("grok-bot-future" to 100L) else mapOf("model-${n%3}" to 90L,"future-model" to 10L),
                liveActivity=when {n==size-1->SessionLiveActivity("running",now-1);n%19==0->SessionLiveActivity("unknown",now);else->null},
                waitingForInput=waiting && n%9==0,nativeSessionId=native,
                parentSessionId=if(related && n%41==0) "s${n-1}" else null,
                parentClient=if(related && n%82==0) "unknown-client" else null)
            UsageRow(id,"$n",100+n.toLong(),"0.125",if(related && n>0 && n%211==0) "cursor" else "codex",at,n%5==0,(n%13).toLong(),meta)
        }
        val today=month.take(30).map {it.copy(label="today copy",session=it.session!!.copy(title="Today override"))}+
            UsageRow("today-only","today",1,"0","future-client",Instant.ofEpochMilli(now).toString(),true,
                session=SessionMetadata(sessionId="today",kind="future-kind"))+
            UsageRow("invalid","invalid",1,"0","codex","invalid",false,session=SessionMetadata(title="Invalid"))
        val empty=HubParser.parse("{\"periods\":{\"month\":{},\"today\":{}}}")
        return empty.copy(periods=mapOf("month" to empty.periods.getValue("month").copy(sessions=month),
            "today" to empty.periods.getValue("today").copy(sessions=today)))
    }
    private fun oldSource(snapshot:Snapshot?):List<UsageRow> {
        val selected=linkedMapOf<String,UsageRow>()
        for(period in listOf("month","today")) for(row in snapshot?.periods?.get(period)?.sessions.orEmpty()) {
            if(row.id in selected || row.session?.kind=="background-review" || SessionDisplay.timestamp(row.lastUsed ?: row.session?.startedAt)==null) continue
            selected[row.id]=row
        }
        return selected.values.sortedByDescending {SessionDisplay.timestamp(it.lastUsed ?: it.session?.startedAt)}
    }
    private fun oldRecent(snapshot:Snapshot?,at:Long,cap:Int)=oldSource(snapshot).filterIndexed {index,row->index<cap || SessionDisplay.activity(row,at)=="running"}
    private fun desktop070Recent(snapshot:Snapshot?,at:Long,cap:Int)=oldSource(snapshot).filterIndexed {index,row->index<cap || SessionDisplay.activity(row,at) in listOf("running","waiting")}
    private fun raw(snapshot:Snapshot,old:Boolean,hidden:Boolean=false):List<SessionListRow> {
        val rows=snapshot.periods.getValue("month").sessions
        return if(old) LegacySessionList070.raw(rows,now,hidden,zone=zone) else SessionListDisplay.raw(rows,now,hidden,zone=zone)
    }
    @Test fun largeRecentSelectionPreservesMonthPriorityStableTiesRunningBeyondCapAndUnknownValues() {
        for(size in listOf(2_000,10_000)) for(related in listOf(false,true)) {
            val snapshot=fixture(size,related)
            assertEquals(oldSource(snapshot),SessionDisplay.source(snapshot))
            for(at in listOf(now,now+30_000,now+600_001)) for(cap in listOf(-1,0,1,5,100,size+1))
                assertEquals("size=$size related=$related cap=$cap at=$at",desktop070Recent(snapshot,at,cap),SessionDisplay.recent(snapshot,at,cap))
            val recent=SessionDisplay.recent(snapshot,now)
            assertFalse(recent.any {it.session?.kind=="background-review"})
            assertFalse(recent.any {it.session?.title=="Today override"})
            assertTrue(recent.any {it.id=="codex:s${size-1}"})
            assertEquals(recent.size,recent.map {it.id}.distinct().size)
        }
        assertTrue(SessionDisplay.recent(null,now).isEmpty())
    }
    @Test fun waitingBeyondRecentCapMatchesPinned070AndExpiresWithoutAnotherSnapshot() {
        val quiet=(0 until 5).map {index->UsageRow("quiet-$index","Quiet $index",100,"0","codex",
            Instant.ofEpochMilli(now-index*1000L).toString(),true,session=SessionMetadata(title="Quiet $index"))}
        fun live(id:String,state:String,archived:Boolean=false,review:Boolean=false)=UsageRow(id,id,100,"0","codex",
            Instant.ofEpochMilli(now-86_400_000).toString(),false,session=SessionMetadata(title=id,archived=archived,
                kind=if(review) "background-review" else null,liveActivity=SessionLiveActivity(state,now)))
        val rows=quiet+live("waiting-older","waiting")+live("running-older","running")+
            live("unknown-older","unknown")+live("archive-waiting","waiting",archived=true)+live("review-waiting","waiting",review=true)
        val base=fixture(20)
        val snapshot=base.copy(periods=mapOf("month" to base.periods.getValue("month").copy(sessions=rows),
            "today" to base.periods.getValue("today").copy(sessions=emptyList())))
        val quietIds=(0 until 5).map {"quiet-$it"}
        assertEquals(quietIds+"running-older",oldRecent(snapshot,now,5).map {it.id})
        assertEquals(quietIds+listOf("waiting-older","running-older"),SessionDisplay.recent(snapshot,now).map {it.id})
        assertEquals(quietIds,SessionDisplay.recent(snapshot,now+SessionDisplay.liveWindow).map {it.id})
        assertEquals("waiting",SessionDisplay.activity(SessionDisplay.recent(snapshot,now).first {it.id=="waiting-older"},now))
        assertEquals(snapshot.periods.getValue("month").tokens,base.periods.getValue("month").tokens)
    }
    @Test fun titleGroupingPaginationAndAccountingMatchFrozenAndroid061() {
        for(size in listOf(2_000,10_000)) for(related in listOf(false,true)) for(hidden in listOf(false,true)) {
            val snapshot=fixture(size,related)
            val expectedRaw=raw(snapshot,true,hidden);val actualRaw=raw(snapshot,false,hidden)
            assertEquals(expectedRaw,actualRaw)
            val expected=LegacySessionList070.grouped(expectedRaw,now=now,zone=zone)
            val actual=SessionListDisplay.grouped(actualRaw,now=now,zone=zone)
            assertEquals(expected,actual)
            assertEquals(actualRaw.fold(BigInteger.ZERO) {sum,row->sum+row.tokens},actual.fold(BigInteger.ZERO) {sum,row->sum+row.tokens})
            assertEquals(actualRaw.fold(BigInteger.ZERO) {sum,row->sum+(row.unpricedTotal ?: BigInteger.ZERO)},actual.fold(BigInteger.ZERO) {sum,row->sum+(row.unpricedTotal ?: BigInteger.ZERO)})
            for(page in listOf(-1,0,1,19,99,999)) assertEquals(LegacySessionList070.page(expected,page),SessionListDisplay.page(actual,page))
        }
    }
    @Test fun pairedSameDataBenchmarkReportsMedianAndP95WithoutChangingAcceptance() {
        fun measure(block:()->Any):Long {val start=System.nanoTime();block();return System.nanoTime()-start}
        fun compare(size:Int,stage:String,old:()->Any,new:()->Any) {
            assertEquals(old(),new())
            repeat(5) {old();new()}
            val before=mutableListOf<Long>();val after=mutableListOf<Long>()
            repeat(15) {round->
                if(round%2==0) {before+=measure(old);after+=measure(new)} else {after+=measure(new);before+=measure(old)}
            }
            fun value(samples:List<Long>,index:Int)=String.format(Locale.ROOT,"%.3f",samples.sorted()[index]/1_000_000.0)
            println("SESSION_070_BENCH size=$size stage=$stage samples=15 warmup=5 oldMedianMs=${value(before,7)} newMedianMs=${value(after,7)} oldP95Ms=${value(before,14)} newP95Ms=${value(after,14)}")
        }
        for(size in listOf(2_000,10_000)) {
            // No beyond-cap waiting rows here: old/new performance uses semantically identical results.
            // The intentional waiting fix is verified separately against pinned 0.70 golden expectations.
            val snapshot=fixture(size,waiting=false)
            val rows=raw(snapshot,false)
            compare(size,"source",{oldSource(snapshot)},{SessionDisplay.source(snapshot)})
            compare(size,"recent",{oldRecent(snapshot,now,5)},{SessionDisplay.recent(snapshot,now)})
            compare(size,"title-projection",{raw(snapshot,true)},{raw(snapshot,false)})
            compare(size,"independent-group",{LegacySessionRelationship070.group(rows,"Grok Bot",{"$it"},now)},
                {SessionRelationshipGroups.group(rows,"Grok Bot",{"$it"},now)})
            val related=raw(fixture(size,true),false)
            val grouped=SessionListDisplay.grouped(related,now=now,zone=zone)
            println("SESSION_070_COUNTS size=$size sourceRows=${snapshot.periods.values.sumOf {it.sessions.size}} homeEligible=${SessionDisplay.source(snapshot).size} homeDisplayed=${SessionDisplay.recent(snapshot,now).size} rawProjected=${rows.size} independentGroups=${SessionRelationshipGroups.group(rows,"Grok Bot",{"$it"},now).size} relatedGroups=${grouped.size} relatedMembers=${grouped.sumOf {if(it.review) it.reviews.size else 1}} pageDisplayed=${SessionListDisplay.page(grouped,0).rows.size}")
            compare(size,"related-review-group",{LegacySessionList070.grouped(related,now=now,zone=zone)},
                {SessionListDisplay.grouped(related,now=now,zone=zone)})
        }
    }
}
