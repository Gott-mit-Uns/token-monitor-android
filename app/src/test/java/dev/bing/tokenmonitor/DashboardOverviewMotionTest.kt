package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DashboardOverviewMotionTest {
    private val today=LocalDate.of(2026,10,11)
    private val a=DashboardRankKey(true,"A")
    private val b=DashboardRankKey(true,"B")
    private fun model(aTokens:Long=100,bTokens:Long=50)=DashboardOverviewProjection.project(HubHistory(
        listOf(HistoryRow("2026-10-11",200,"1",perModel=linkedMapOf("A" to HistoryAttribution(aTokens,"1"),"B" to HistoryAttribution(bTokens,"0")))),emptyList()),today)
    private fun render(engine:DashboardOverviewMotion,m:DashboardOverviewModel=model(),event:String="initial",revision:Int=0,
        reason:DashboardMotionReason=DashboardMotionReason.ENTRY,now:Long=0L,width:Double=800.0,cost:Boolean=true,
        visible:Boolean=true,scale:Float=1f)=engine.render(m,event,revision,reason,width,cost,now,visible && scale>0,visible,scale)

    @Test fun entryUsesTwoFrameGuardHeat720AndRanking800FromZero() {
        val engine=DashboardOverviewMotion()
        render(engine)
        assertTrue(engine.frame.heatPending)
        assertEquals(0f,engine.frame.heatAlpha(0,50),0f)
        assertEquals(0f,engine.frame.rankingFraction(a,120.0),0f)
        engine.advance(16_000_000)
        assertTrue(engine.frame.heatPending)
        assertTrue(engine.frame.rankingFraction(a,120.0)>0)
        engine.advance(32_000_000)
        assertFalse(engine.frame.heatPending)
        assertEquals(0f,engine.frame.heatAlpha(0,50),0f)
        engine.advance(312_000_000)
        assertEquals(1f,engine.frame.heatAlpha(0,50),0f)
        assertEquals(0f,engine.frame.heatAlpha(50,50),0f)
        assertTrue(engine.frame.heatAlpha(25,50) in .01f.. .99f)
        engine.advance(752_000_000)
        assertEquals(1f,engine.frame.heatAlpha(50,50),0f)
        assertTrue(engine.running) // Ranking is 800ms from render, heat ends at 32+720.
        engine.advance(800_000_000)
        assertFalse(engine.running)
        assertEquals(.5f,engine.frame.rankingFraction(b,120.0),0f)
    }

    @Test fun updateCapturesDisplayedPixelWidthAndUsesNewTrackWidth() {
        val engine=DashboardOverviewMotion()
        render(engine)
        engine.advance(16_000_000);engine.advance(32_000_000)
        assertFalse("This case updates an entry that has actually begun",engine.frame.heatPending)
        engine.recordPaintedWidth(a,120.0,.2f)
        engine.recordPaintedWidth(b,120.0,.1f)
        render(engine,model(50,100),event="update",now=200_000_000)
        assertFalse(engine.frame.heatPending)
        assertEquals(.1f,engine.frame.rankingFraction(a,240.0),.00001f)
        assertEquals(.05f,engine.frame.rankingFraction(b,240.0),.00001f)
        engine.advance(600_000_000)
        assertTrue(engine.frame.rankingFraction(a,240.0) in .45f.. .5f)
        engine.advance(1_000_000_000)
        assertEquals(.5f,engine.frame.rankingFraction(a,240.0),0f)
        assertEquals(1f,engine.frame.rankingFraction(b,240.0),0f)
    }

    @Test fun actualTabRevisionReplaysEntryWhileSameEventDoesNotRestart() {
        val engine=DashboardOverviewMotion()
        render(engine)
        engine.advance(16_000_000);engine.advance(32_000_000);engine.advance(200_000_000)
        val frame=engine.frame
        assertFalse(render(engine,now=200_000_000))
        assertEquals(frame,engine.frame)
        engine.recordPaintedWidth(a,100.0,.8f)
        render(engine,revision=1,now=200_000_000)
        assertTrue(engine.frame.heatPending)
        assertEquals(0f,engine.frame.rankingFraction(a,100.0),0f)
    }

    @Test fun refreshBusyWaitsBeforeDoubleFrameAndPendingRerenderUsesNewGeneration() {
        val engine=DashboardOverviewMotion()
        render(engine)
        engine.advance(100_000_000,refreshBusy=true)
        assertTrue(engine.frame.heatPending)
        engine.advance(116_000_000)
        assertTrue(engine.frame.heatPending)
        render(engine,event="refresh",now=120_000_000)
        engine.advance(132_000_000)
        assertTrue(engine.frame.heatPending)
        engine.advance(148_000_000)
        assertFalse(engine.frame.heatPending)
        assertEquals(0f,engine.frame.heatAlpha(0,50),0f)
        engine.advance(868_000_000)
        assertEquals(1f,engine.frame.heatAlpha(50,50),0f)
    }

    @Test fun metricAndResizeRepaintWithoutNewRankingMotion() {
        val engine=DashboardOverviewMotion()
        render(engine)
        engine.advance(16_000_000);engine.advance(32_000_000);engine.advance(400_000_000)
        render(engine,width=320.0,cost=false,now=400_000_000)
        assertFalse(engine.running)
        assertEquals(1f,engine.frame.rankingFraction(a,90.0),0f)
        assertEquals(1f,engine.frame.heatAlpha(0,50),0f)
        render(engine,width=320.0,cost=true,now=500_000_000)
        assertFalse(engine.running)
    }

    @Test fun hiddenFirstDataRetainsEntryButTemporarySettingsAndStopSettleConsumedEntry() {
        val engine=DashboardOverviewMotion()
        render(engine,visible=false)
        assertFalse(engine.running)
        render(engine,visible=true)
        assertTrue(engine.frame.heatPending)
        engine.recordPaintedWidth(a,100.0,.1f)
        engine.settle()
        render(engine,visible=false)
        render(engine,visible=true)
        assertFalse(engine.running)
        render(engine,model(50,100),event="next",now=200_000_000)
        assertFalse(engine.frame.heatPending)
        assertEquals(1f,engine.frame.rankingFraction(a,100.0),.00001f) // The settled old endpoint.
    }

    @Test fun reducedMotionAndScaleChangeSettleBothWithoutReplay() {
        val engine=DashboardOverviewMotion()
        render(engine,scale=0f)
        assertFalse(engine.running)
        assertEquals(1f,engine.frame.heatAlpha(50,50),0f)
        assertFalse(render(engine,scale=1f))
        render(engine,event="next",now=100_000_000)
        assertTrue(engine.running)
        engine.advance(116_000_000,0f)
        assertFalse(engine.running)
        assertEquals(1f,engine.frame.rankingFraction(a,100.0),0f)
    }

    @Test fun emptyRetainedHistoryStillAnimatesNeutralYearButNeverAddsRankingWork() {
        val engine=DashboardOverviewMotion()
        val empty=DashboardOverviewProjection.project(HubHistory(emptyList(),emptyList()),today)
        render(engine,empty)
        assertTrue(engine.frame.targets.isEmpty())
        engine.advance(16_000_000);engine.advance(32_000_000);engine.advance(752_000_000)
        assertFalse(engine.running)
        assertEquals(1f,engine.frame.heatAlpha(50,50),0f)
    }

    @Test fun unavailableDataClearsBindingGeometryAndDoesNotConsumeNextInitialEntry() {
        val engine=DashboardOverviewMotion()
        render(engine);engine.recordPaintedWidth(a,100.0,.9f)
        engine.render(null,"other-binding",0,DashboardMotionReason.ENTRY,800.0,true,0,false,false)
        assertTrue(engine.frame.targets.isEmpty())
        render(engine,event="other-binding")
        assertTrue(engine.frame.heatPending)
        assertEquals(0f,engine.frame.rankingFraction(a,100.0),0f)
    }

    @Test fun positiveScaleChangesNeverRewindAndNewSegmentsUseTheirStartScale() {
        val engine=DashboardOverviewMotion()
        render(engine,scale=1f)
        engine.advance(16_000_000);engine.advance(32_000_000);engine.advance(400_000_000)
        val rankBefore=engine.frame.rankingFraction(a,120.0)
        val heatBefore=engine.frame.heatAlpha(25,50)
        engine.advance(416_000_000,2f)
        assertTrue("A running rank cannot rewind when positive scale changes",engine.frame.rankingFraction(a,120.0)>=rankBefore)
        assertTrue("A running heat cell cannot rewind",engine.frame.heatAlpha(25,50)>=heatBefore)
        engine.advance(800_000_000,2f)
        assertFalse("The original scale1 endpoints stay800/32+720",engine.running)
        render(engine,revision=1,now=1_000_000_000,scale=2f)
        engine.advance(1_016_000_000,2f);engine.advance(1_032_000_000,2f)
        engine.advance(1_832_000_000,1f)
        assertTrue("The new rank retains scale2 after system returns1",engine.frame.rankingFraction(a,120.0)<1f)
        assertTrue("The last scale2 heat cell has not yet finished",engine.frame.heatAlpha(50,50)<1f)
        engine.advance(2_600_000_000,1f)
        assertFalse(engine.running)
        render(engine,revision=2,now=3_000_000_000)
        engine.advance(3_016_000_000,Float.NaN)
        assertFalse("Nonfinite scale settles immediately",engine.running)
        assertEquals(1f,engine.frame.heatAlpha(50,50),0f)
        assertEquals(1f,engine.frame.rankingFraction(a,120.0),0f)
    }
}
