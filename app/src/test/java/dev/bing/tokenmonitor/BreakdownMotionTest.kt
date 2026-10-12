package dev.bing.tokenmonitor

import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class BreakdownMotionTest {
    private val static=BreakdownPeriodEvent()
    private fun spec(key:String="row",value:Long=100,scale:Float=1f)=BreakdownRowSpec(key,scale,BigInteger.valueOf(value))
    private fun render(motion:BreakdownMotion,rows:List<BreakdownRowSpec>?,timeMs:Long=0,view:String="TOOLS",period:String="today",event:BreakdownPeriodEvent=static,animate:Boolean=true,shown:Boolean=true,source:Long=1)=
        motion.render(rows,source,view,period,event,timeMs*1_000_000L,animate,shown)
    private fun value(motion:BreakdownMotion,key:String="row")=motion.frame.number(key,BigInteger.ZERO).longValueExact()

    @Test fun actualMainViewEntryHasOnly420msBarsAndOrdinaryCommitsDoNotReplay() {
        val motion=BreakdownMotion()
        render(motion,emptyList(),view="HOME",animate=false)
        val targets=(0 until 320).map {spec("model-$it",it+100L,(it+1)/320f)}
        assertTrue(render(motion,targets,view="MODELS"))
        assertTrue(motion.running)
        assertEquals(0f,motion.frame.bar("model-319",1f),0f)
        assertEquals(419L,value(motion,"model-319"))
        assertEquals(0,motion.retainedBarTweens)
        assertEquals(0,motion.retainedNumberTweens)
        val sharedTargets=motion.frame.targets;val sharedEntry=motion.frame.entryBars
        motion.advance(210_000_000)
        assertTrue(motion.frame.bar("model-319",1f) in .9f..1f)
        assertSame("Entry frames must not rebuild 320 row targets",sharedTargets,motion.frame.targets)
        assertSame("Entry uses one shared fraction",sharedEntry,motion.frame.entryBars)
        assertFalse(render(motion,targets,timeMs=210,view="MODELS"))
        motion.advance(420_000_000)
        assertFalse(motion.running)
        assertEquals(1f,motion.frame.bar("model-319",1f),0f)
        assertFalse(render(motion,targets,timeMs=500,view="MODELS"))
    }

    @Test fun sameActiveTargetsKeepTheir600msTimelineWhenAnotherRowAppears() {
        val motion=BreakdownMotion()
        render(motion,listOf(spec()),animate=false)
        render(motion,listOf(spec(value=200,scale=.5f)))
        motion.advance(150_000_000)
        val first=value(motion)
        assertTrue(first in 168..169)
        assertFalse(render(motion,listOf(spec(value=200,scale=.5f)),timeMs=200))
        render(motion,listOf(spec(value=200,scale=.5f),spec("new",50,.1f)),timeMs=300)
        motion.advance(600_000_000)
        assertEquals("Existing target finishes on the original schedule",200L,value(motion))
        assertTrue("The new row has its own bounded timeline",value(motion,"new") in 46..48)
        motion.advance(900_000_000)
        assertFalse(motion.running)
        assertEquals(50L,value(motion,"new"))
    }

    @Test fun periodTabsUse800msButTheFixedDropdownPathSettlesImmediately() {
        val motion=BreakdownMotion()
        render(motion,listOf(spec(value=300)),animate=false)
        render(motion,listOf(spec(value=100,scale=.3f)),period="month",event=BreakdownPeriodEvent(1,BreakdownPeriodReason.TAB))
        motion.advance(400_000_000)
        assertEquals(113L,value(motion))
        assertTrue(motion.running)
        motion.advance(800_000_000)
        assertEquals(100L,value(motion));assertFalse(motion.running)
        render(motion,listOf(spec(value=500)),timeMs=900,period="last7",event=BreakdownPeriodEvent(2,BreakdownPeriodReason.STATIC))
        assertEquals(500L,value(motion));assertFalse(motion.running)
    }

    @Test fun interruptionStartsAtTheCurrentFractionAndFinishesAtTheLatestExactInteger() {
        val motion=BreakdownMotion()
        render(motion,listOf(spec(value=100)),animate=false)
        render(motion,listOf(spec(value=300,scale=.5f)))
        motion.advance(300_000_000)
        val number=value(motion);val bar=motion.frame.bar("row",1f)
        render(motion,listOf(spec(value=500,scale=.75f)),timeMs=300)
        assertEquals(number,value(motion))
        assertEquals(bar,motion.frame.bar("row",1f),.00001f)
        motion.advance(900_000_000)
        assertEquals(500L,value(motion));assertEquals(.75f,motion.frame.bar("row",1f),0f)
        val exact=BigInteger("999999999999999999999999999999999999999999999999999999999999999999")
        render(motion,listOf(BreakdownRowSpec("row",1f,exact)),timeMs=1000)
        motion.advance(1_600_000_000)
        assertEquals(exact,motion.frame.number("row",BigInteger.ZERO))
    }

    @Test fun bothPreviousAndNextRowCountsMustPassTheFortyRowUpdateGate() {
        val motion=BreakdownMotion()
        val forty=(0 until 40).map {spec("$it",100,.5f)}
        val fortyOne=forty+spec("40",100,.5f)
        render(motion,fortyOne,animate=false)
        render(motion,forty.map {it.copy(number=BigInteger.valueOf(200),barScale=1f)})
        assertFalse(motion.running)
        assertEquals(0,motion.retainedBarTweens);assertEquals(0,motion.retainedNumberTweens)
        render(motion,fortyOne.map {it.copy(number=BigInteger.valueOf(300),barScale=.3f)})
        assertFalse(motion.running)
        assertEquals(0,motion.retainedBarTweens);assertEquals(0,motion.retainedNumberTweens)
        render(motion,forty,animate=false)
        render(motion,forty.map {it.copy(number=BigInteger.valueOf(400),barScale=1f)})
        assertEquals(40,motion.retainedBarTweens);assertEquals(40,motion.retainedNumberTweens)
    }

    @Test fun semanticValuesHiddenUpdatesAndSourceChangesCannotReplayAnOldNumber() {
        val motion=BreakdownMotion()
        render(motion,listOf(spec()),animate=false)
        render(motion,listOf(BreakdownRowSpec("row",.3f,null)))
        assertFalse(motion.running);assertTrue(motion.frame.numbers.isEmpty())
        render(motion,listOf(spec(value=200,scale=.5f)),shown=false)
        assertEquals(200L,value(motion));assertFalse(motion.running)
        assertFalse(render(motion,listOf(spec(value=200,scale=.5f)),timeMs=100))
        render(motion,listOf(spec(value=300)),timeMs=200)
        assertTrue(motion.running)
        motion.stop() // Same operation used synchronously by the STOP observer.
        assertEquals(300L,value(motion));assertFalse(motion.running)
        assertFalse(render(motion,listOf(spec(value=300)),timeMs=300))
        render(motion,listOf(spec(value=700)),timeMs=400,source=2)
        assertEquals(700L,value(motion));assertFalse(motion.running)
    }

    @Test fun showingAnEmptyChangedViewConsumesOnlyThatSynchronousEntryFlag() {
        val motion=BreakdownMotion()
        render(motion,emptyList(),view="HOME",animate=false)
        render(motion,emptyList(),view="TOOLS")
        render(motion,listOf(spec()),timeMs=100,view="TOOLS")
        assertEquals("Later data is the compact live new-row path",1,motion.retainedNumberTweens)
        assertTrue(motion.frame.entryBars.isEmpty())
    }

    @Test fun hiddenMainHasNoNewProjectionAndRestoresLatestValuesWithoutReplay() {
        val motion=BreakdownMotion()
        val targets=listOf(spec())
        render(motion,targets,animate=false)
        val retained=motion.frame.targets
        render(motion,listOf(spec(value=200)),timeMs=20)
        assertTrue(motion.running)
        render(motion,null,timeMs=100,shown=false)
        assertFalse(motion.running)
        assertEquals(200L,value(motion))
        render(motion,null,timeMs=200,shown=false)
        assertFalse(motion.running)
        render(motion,listOf(spec(value=400)),timeMs=300)
        assertEquals(400L,value(motion))
        assertFalse("Returning from Settings/STOP must not animate stale hidden values",motion.running)
        val latest=motion.frame.targets
        assertNotSame(retained,latest)
        assertFalse(render(motion,motion.frame.targets.values.toList(),timeMs=400))
        assertSame("Equal remembered event specs keep a stable target map",latest,motion.frame.targets)
    }

    @Test fun coalescedStopAndResumeSettleNewTargetsWithoutAnInterveningHiddenRender() {
        val motion=BreakdownMotion()
        render(motion,listOf(spec()),animate=false)
        render(motion,listOf(spec(value=200,scale=.5f)),timeMs=20)
        assertTrue(motion.running)
        motion.stop()
        // No shown=false render occurs while backgrounded; resume still respects STOP.
        render(motion,listOf(spec(value=500),spec("new",100)),timeMs=200)
        assertFalse(motion.running)
        assertEquals(500L,value(motion));assertEquals(100L,value(motion,"new"))
        render(motion,listOf(spec(value=600),spec("new",100)),timeMs=300)
        assertTrue("Subsequent foreground business updates keep the normal live animation",motion.running)
    }

    @Test fun exactRowBarScaleHasNoArtificialOneDollarMaximum() {
        assertEquals(1f,BreakdownMotion.bar(.3,.3),0f)
        assertEquals(1f/3,BreakdownMotion.bar(.1,.3),.00001f)
        assertEquals(.02f,BreakdownMotion.bar(.001,1.0),0f)
        assertEquals(0f,BreakdownMotion.bar(0.0,0.0),0f)
        assertEquals(0f,BreakdownMotion.bar(10.0,0.0),0f)
    }
}
