package dev.bing.tokenmonitor

import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class BreakdownRowPlacementMotionTest {
    private val static=BreakdownPeriodEvent()
    private fun row(key:String,value:Long=100)=BreakdownRowSpec(key,1f,BigInteger.valueOf(value))
    private fun render(model:BreakdownRowPlacementMotion,rows:List<BreakdownRowSpec>?,token:Any,time:Long=0,
        view:String="TOOLS",period:String="today",event:BreakdownPeriodEvent=static,shown:Boolean=true,
        animate:Boolean=true,source:Long=1,scale:Float=1f)=
        model.render(rows,source,view,period,event,token,time*1_000_000L,shown,animate,scale)
    private fun at(model:BreakdownRowPlacementMotion,key:String,y:Float,token:Any,time:Long=0)=
        model.positioned(key,y,token,time*1_000_000L)

    @Test fun reorderedMountedRowsUseCapturedDisplayedCoordinatesAndFinishIn280ms() {
        val model=BreakdownRowPlacementMotion();val first=Any();val next=Any()
        render(model,listOf(row("a"),row("b"),row("c")),first,animate=false)
        at(model,"a",0f,first);at(model,"b",60f,first);at(model,"c",120f,first)
        render(model,listOf(row("a"),row("c"),row("b")),next)
        at(model,"a",0f,next);at(model,"c",60f,next);at(model,"b",120f,next)
        assertEquals(60f,model.frame.row("c").translationY,0f)
        assertEquals(-60f,model.frame.row("b").translationY,0f)
        assertEquals(1f,model.frame.row("b").alpha,0f)
        model.advance(140_000_000L)
        assertTrue(model.frame.row("c").translationY in 0f..10f)
        assertTrue(model.frame.row("b").translationY in -10f..0f)
        model.advance(280_000_000L)
        assertFalse(model.running);assertEquals(0f,model.frame.row("c").translationY,0f)
    }

    @Test fun aRealPlacementInterruptionKeepsTheCurrentDisplayedY() {
        val model=BreakdownRowPlacementMotion();val first=Any();val second=Any();val third=Any()
        render(model,listOf(row("a"),row("b")),first,animate=false)
        at(model,"a",0f,first);at(model,"b",60f,first)
        render(model,listOf(row("b"),row("a")),second)
        at(model,"b",0f,second);at(model,"a",60f,second)
        model.advance(70_000_000L)
        val oldDisplayed=60f+model.frame.row("a").translationY
        render(model,listOf(row("a"),row("b")),third,time=70)
        at(model,"a",0f,third,time=70);at(model,"b",60f,third,time=70)
        assertEquals(oldDisplayed,model.frame.row("a").translationY,.0001f)
        model.advance(350_000_000L);assertFalse(model.running)
    }

    @Test fun equalPlacementCommitsAndScrollingNeverCreateOrRestartFlip() {
        val model=BreakdownRowPlacementMotion();val first=Any();val changed=Any();val ordinary=Any()
        val rows=listOf(row("a"),row("b"))
        render(model,rows,first,animate=false);at(model,"a",0f,first);at(model,"b",60f,first)
        render(model,rows.reversed(),changed);at(model,"a",60f,changed);at(model,"b",0f,changed)
        model.advance(80_000_000L)
        val current=model.frame.row("a").translationY
        assertFalse(render(model,rows.reversed(),changed,time=80))
        render(model,rows.reversed().map {it.copy(number=BigInteger.valueOf(200))},ordinary,time=80)
        at(model,"a",60f,ordinary,time=80);at(model,"b",0f,ordinary,time=80)
        assertEquals(current,model.frame.row("a").translationY,0f)
        model.advance(280_000_000L);assertFalse(model.running)
        at(model,"a",10f,ordinary,time=300);at(model,"b",-50f,ordinary,time=300)
        assertFalse("Scroll geometry is recorded without starting another tween",model.running)
    }

    @Test fun newRowsFadeAndMoveFor240msWithOnlyTheSourceCappedStagger() {
        val model=BreakdownRowPlacementMotion();val base=Any();val next=Any()
        render(model,emptyList(),base,animate=false)
        val rows=(0 until 10).map {row("$it")}
        render(model,rows,next)
        assertEquals(0f,model.frame.row("0").alpha,0f)
        assertEquals(7f,model.frame.row("0").translationY,0f)
        model.advance(120_000_000L)
        assertTrue(model.frame.row("0").alpha>.9f)
        assertTrue(model.frame.row("6").alpha<model.frame.row("0").alpha)
        assertEquals(model.frame.row("6"),model.frame.row("9"))
        model.advance(240_000_000L)
        assertEquals(BreakdownRowLayer(),model.frame.row("0"))
        assertTrue(model.running)
        model.advance(348_000_000L)
        assertFalse(model.running)
        at(model,"9",500f,next,time=500)
        assertEquals("A new row mounted after the source timeline cannot replay",BreakdownRowLayer(),model.frame.row("9"))
        assertFalse(model.running)
    }

    @Test fun largeListsAndUnknownValuesCannotCreateCoordinatesOrRowAnimations() {
        val model=BreakdownRowPlacementMotion();val first=Any();val next=Any();val forty=Any()
        val rows=(0 until 320).map {row("$it")}
        render(model,rows,first,animate=false)
        rows.forEachIndexed {index,r->at(model,r.key,index*50f,first)}
        assertEquals(0,model.retainedCoordinateCount);assertEquals(0,model.retainedAnimationCount)
        render(model,rows.take(40),next)
        assertFalse("The old-side gate applies to 320→40 too",model.running)
        render(model,rows.take(40)+row("new"),forty)
        assertFalse("The new-side gate applies to 40→41 too",model.running)
        val semantic=Any()
        render(model,listOf(BreakdownRowSpec("unknown",1f,null)),semantic,animate=false)
        val changed=Any();at(model,"unknown",0f,semantic)
        render(model,listOf(BreakdownRowSpec("unknown",.5f,null),BreakdownRowSpec("new-unknown",1f,null)),changed)
        at(model,"unknown",50f,changed)
        assertFalse(model.running);assertTrue(model.frame.layers.isEmpty())
    }

    @Test fun trueViewEntryStaticPeriodStopAndHiddenReturnRemainSettled() {
        val model=BreakdownRowPlacementMotion();val first=Any();val view=Any()
        render(model,emptyList(),first,view="HOME",animate=false)
        render(model,listOf(row("a"),row("b")),view,view="TOOLS")
        assertFalse("420ms view bar entry must not also become a new-row fade",model.running)
        at(model,"a",0f,view);at(model,"b",60f,view)
        val period=Any()
        render(model,listOf(row("b"),row("a")),period,period="month",event=BreakdownPeriodEvent(1,BreakdownPeriodReason.STATIC))
        assertFalse(model.running)
        at(model,"b",0f,period);at(model,"a",60f,period)
        val tab=Any()
        render(model,listOf(row("a"),row("b")),tab,period="today",event=BreakdownPeriodEvent(2,BreakdownPeriodReason.TAB))
        at(model,"a",0f,tab);at(model,"b",60f,tab)
        assertTrue(model.running)
        model.stop() // Synchronous STOP uses this exact operation.
        assertFalse(model.running)
        render(model,null,tab,time=100,shown=false)
        val restore=Any()
        render(model,listOf(row("b"),row("a"),row("new")),restore,time=200)
        assertFalse(model.running)
        val live=Any()
        render(model,listOf(row("new"),row("b"),row("a"),row("more")),live,time=300)
        assertTrue(model.running)
        render(model,listOf(row("new"),row("b"),row("a"),row("more")),live,time=320,scale=0f)
        assertFalse(model.running);assertEquals(BreakdownRowLayer(),model.frame.row("more"))
        render(model,listOf(row("different")),Any(),time=400,source=2)
        assertFalse(model.running)
    }

    @Test fun detachedExistingRowsNeverReuseStaleWindowCoordinates() {
        val model=BreakdownRowPlacementMotion();val first=Any();val next=Any()
        render(model,listOf(row("a"),row("b")),first,animate=false)
        at(model,"a",0f,first);at(model,"b",60f,first)
        model.detached("a")
        render(model,listOf(row("b"),row("a")),next)
        at(model,"b",0f,next)
        at(model,"a",60f,next)
        assertEquals("An old offscreen lazy row has no measured old coordinate",0f,model.frame.row("a").translationY,0f)
        assertEquals("It remains an existing row, so it cannot get a delayed new-row fade",1f,model.frame.row("a").alpha,0f)
        assertTrue(model.retainedCoordinateCount<=40)
    }

    @Test fun stopWithoutAnIntermediateHiddenRenderSkipsLatestRowsAndClearsPendingRouteEntry() {
        val model=BreakdownRowPlacementMotion();val first=Any();val update=Any()
        render(model,listOf(row("a"),row("b")),first,animate=false)
        at(model,"a",0f,first);at(model,"b",60f,first)
        render(model,listOf(row("b"),row("a"),row("new")),update)
        at(model,"b",0f,update);at(model,"a",60f,update);at(model,"new",120f,update)
        assertTrue(model.running)
        model.stop()
        assertFalse(model.running);assertEquals(0,model.retainedCoordinateCount)
        // CREATED and RESUMED can occur before another Compose commit. There is
        // deliberately no render(shown=false) between this stop and visible data.
        val resumed=Any()
        render(model,listOf(row("a"),row("new"),row("b"),row("latest")),resumed,time=100)
        assertFalse("Latest background data must not start FLIP/new-row entry",model.running)
        assertEquals(BreakdownRowLayer(),model.frame.row("latest"))
        at(model,"a",0f,resumed,time=100);at(model,"b",120f,resumed,time=100)
        // An earlier hidden route flag also expires at STOP rather than producing
        // a delayed entry after the activity becomes visible again.
        render(model,null,resumed,time=200,view="MODELS",shown=false)
        model.stop()
        render(model,listOf(row("model-a")),Any(),time=300,view="MODELS")
        assertFalse(model.running);assertEquals(BreakdownRowLayer(),model.frame.row("model-a"))
    }
}
