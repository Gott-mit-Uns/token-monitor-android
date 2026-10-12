package dev.bing.tokenmonitor

import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

/** Uses the existing Sessions business projection before feeding the shared finite controllers. */
class SessionBreakdownMotionTest {
    private val static=BreakdownPeriodEvent()
    private fun raw(id:String,tokens:Long,time:String="2026-10-11T08:00:00Z",review:Boolean=false)=
        UsageRow(id,id,tokens,"0","codex",time,true,
            session=SessionMetadata(title=id,kind=if(review) "background-review" else null))
    private fun displayed(rows:List<UsageRow>)=SessionListDisplay.raw(rows,1_791_705_600_000L)
    private fun specs(rows:List<SessionListRow>,max:BigInteger=rows.maxOfOrNull {it.tokens} ?: BigInteger.ZERO)=
        SessionBreakdownMotionProjection.rows(rows,max)
    private fun render(motion:BreakdownMotion,rows:List<BreakdownRowSpec>?,time:Long=0,
        view:String="SESSIONS",period:String="today",event:BreakdownPeriodEvent=static,
        shown:Boolean=true,animate:Boolean=true)=
        motion.render(rows,1,view,period,event,time*1_000_000L,animate,shown)

    @Test fun pageUsesOnlyItsActualHundredRowsAndKeepsTheMatchingCollectionMaximum() {
        val all=displayed((0 until 201).map {raw("public:$it",201L-it)})
        val page=SessionListDisplay.page(all,1)
        val actual=specs(page.rows,all.maxOf {it.tokens})
        assertEquals(100,actual.size)
        assertEquals(page.rows.map {"session:${it.id}"},actual.map {it.key})
        assertEquals("The first row on page two retains the list-wide maximum",101f/201f,actual.first().barScale,.000001f)
        assertEquals(page.rows.map {it.tokens},actual.map {it.number})
        assertEquals(201,page.total)
        assertEquals(listOf("session:public:200"),specs(SessionListDisplay.page(all,9).rows).map {it.key})
    }

    @Test fun groupedReviewExactIntegerAndExpandedRunKeysAreNeverReplacedByIndexKeys() {
        val raw=displayed(listOf(raw("review:first",Long.MAX_VALUE,review=true),raw("review:second",Long.MAX_VALUE,review=true)))
        val group=SessionListDisplay.grouped(raw,now=0).single()
        val exact=BigInteger.valueOf(Long.MAX_VALUE)*BigInteger.TWO
        assertEquals(exact,specs(listOf(group)).single().number)
        assertEquals("session:session-group:codex-auto-review",specs(listOf(group)).single().key)
        assertEquals(raw.map {"session:${it.id}"},specs(group.reviews).map {it.key})
        assertFalse(specs(group.reviews).any {it.key==specs(listOf(group)).single().key})
        assertEquals(exact,group.tokens)
    }

    @Test fun trueSessionsEntryAnimatesAllPageBarsWithoutCountingNumbersOrPerRowOverrides() {
        val rows=SessionListDisplay.page(displayed((0 until 100).map {raw("public:$it",100L-it)}),0).rows
        val targets=specs(rows)
        val motion=BreakdownMotion()
        render(motion,null,view="HOME",shown=false,animate=false)
        render(motion,targets)
        assertTrue(motion.running)
        assertEquals(100,motion.frame.entryBars.size)
        assertEquals(rows.first().tokens,motion.frame.number(targets.first().key,BigInteger.ZERO))
        assertEquals(0,motion.retainedBarTweens);assertEquals(0,motion.retainedNumberTweens)
        val targetMap=motion.frame.targets
        motion.advance(210_000_000L)
        assertTrue(motion.frame.bar(targets.first().key,1f) in .9f..1f)
        assertSame(targetMap,motion.frame.targets)
        assertFalse("An equal business projection cannot restart entry",render(motion,specs(rows),time=210))
        motion.advance(420_000_000L)
        assertFalse(motion.running)
        assertEquals(1f,motion.frame.bar(targets.first().key,1f),0f)
    }

    @Test fun smallActualSessionsUpdateAndTabUseTheirOriginal600And800msTimelines() {
        val key=SessionBreakdownMotionProjection.key("public:small")
        fun targets(small:Long)=specs(displayed(listOf(raw("public:small",small),raw("public:big",1000))))
        val motion=BreakdownMotion()
        render(motion,targets(300),animate=false)
        render(motion,targets(500))
        motion.advance(300_000_000L)
        assertEquals(BigInteger.valueOf(488),motion.frame.number(key,BigInteger.ZERO))
        assertTrue(motion.frame.bar(key,0f)>.3f && motion.frame.bar(key,0f)<.5f)
        assertFalse(render(motion,targets(500),time=300))
        motion.advance(600_000_000L)
        assertEquals(BigInteger.valueOf(500),motion.frame.number(key,BigInteger.ZERO))
        render(motion,targets(100),time=700,period="month",event=BreakdownPeriodEvent(1,BreakdownPeriodReason.TAB))
        motion.advance(1_100_000_000L)
        assertEquals(BigInteger.valueOf(125),motion.frame.number(key,BigInteger.ZERO))
        motion.advance(1_500_000_000L)
        assertEquals(BigInteger.valueOf(100),motion.frame.number(key,BigInteger.ZERO))
        assertFalse(motion.running)
    }

    @Test fun bothActualPageSidesGateLargeUpdatesAndStopDoesNotReplayOnSettingsReturn() {
        fun rows(count:Int,value:Long)=specs(displayed((0 until count).map {raw("public:$it",value)}))
        val motion=BreakdownMotion()
        render(motion,rows(41,100),animate=false)
        render(motion,rows(40,200))
        assertFalse(motion.running);assertEquals(0,motion.retainedNumberTweens)
        render(motion,rows(40,300))
        assertEquals(40,motion.retainedNumberTweens)
        motion.stop()
        assertFalse(motion.running)
        render(motion,null,time=100,shown=false,animate=false)
        render(motion,rows(40,500),time=200)
        assertFalse("Same backing Sessions view returns at the latest target",motion.running)
        assertEquals(BigInteger.valueOf(500),motion.frame.number("session:public:0",BigInteger.ZERO))
        render(motion,rows(100,600),time=300)
        assertFalse(motion.running);assertEquals(0,motion.retainedBarTweens);assertEquals(0,motion.retainedNumberTweens)
    }

    @Test fun visibleEmptySessionsConsumesEntryBeforeLaterRowsUseTheNormalUpdatePath() {
        val motion=BreakdownMotion()
        render(motion,null,view="HOME",shown=false,animate=false)
        render(motion,specs(emptyList()))
        val targets=specs(displayed(listOf(raw("public:first",300))))
        render(motion,targets,time=100)
        assertTrue(motion.running)
        assertTrue("This is a real later row update, not an unconsumed entry",motion.frame.entryBars.isEmpty())
        assertEquals(BigInteger.ZERO,motion.frame.number("session:public:first",BigInteger.ZERO))
        assertEquals(1,motion.retainedNumberTweens)
    }

    @Test fun fallbackIdentitySeparatesModelAndToolAndNeverAnimatesCostOrUnavailableAsZero() {
        val components=TokenComponents(null,null,null,null,null)
        val costOnly=UsageBreakdown(null,"1",components)
        val rows=listOf(SessionFallbackRow("public",BigInteger.ZERO,costOnly,true,1.0),
            SessionFallbackRow("public",BigInteger.TEN,null,false,10.0),
            SessionFallbackRow("unknown",null,null,true,0.0))
        val targets=SessionBreakdownMotionProjection.fallback(rows,10.0)
        assertEquals(listOf("session-fallback:true:public","session-fallback:false:public","session-fallback:true:unknown"),targets.map {it.key})
        assertNull(targets.first().number);assertNull(targets.last().number)
        val motion=BreakdownMotion();render(motion,targets,animate=false)
        render(motion,targets.map {it.copy(barScale=.5f)})
        assertFalse(motion.frame.numbers.containsKey(targets.first().key))
        assertFalse(motion.frame.numbers.containsKey(targets.last().key))
        assertEquals(BigInteger.TEN,motion.frame.number(targets[1].key,BigInteger.ZERO))
    }
}
