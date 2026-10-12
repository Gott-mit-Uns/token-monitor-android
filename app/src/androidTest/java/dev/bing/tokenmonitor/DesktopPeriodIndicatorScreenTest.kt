package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class DesktopPeriodIndicatorScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val clock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
    private val owners=AtomicInteger()
    private val renderOverflow=AtomicInteger()
    private val renders:MutableList<DesktopPeriodIndicatorRenderObservation> = Collections.synchronizedList(mutableListOf())
    private fun renderCount()=synchronized(renders) {renders.size}
    private fun firstRenderAfter(cursor:Int,target:Int)=synchronized(renders) {renders.drop(cursor).firstOrNull {it.targetIndex==target}}
    private class Actions:HubUiActions {
        var calls=0
        override fun connect(address:String,credential:String,alternate:String) {calls++}
        override fun refreshSaved() {calls++}
        override fun switchEndpoint() {calls++}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadModelNames() {calls++}
        override fun loadHistory() {calls++}
        override fun loadSubscriptions() {calls++}
        override fun forget() {calls++}
        override fun renameDevice(id:String,input:String) {calls++}
    }
    private fun setup(prefs:State<DisplayPreferences>,actions:Actions) {
        val state=HubState(snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":100},"month":{"totalTokens":200},"allTime":{"totalTokens":300}}}"""),
            history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"),subscriptions=emptyList())
        compose.activityRule.scenario.onActivity {it.setContent {TokenTheme(true,prefs.value.language,prefs.value.appearance) {
            CompositionLocalProvider(LocalDesktopAnimationClock provides clock,LocalDesktopSystemMotionScale provides 0f,
                LocalHeadlineRenderObserver provides {part->if(part=="hub") owners.incrementAndGet()},
                LocalDesktopPeriodIndicatorRenderObserver provides {sample->synchronized(renders) {
                    if(renders.size<128) renders.add(sample) else renderOverflow.incrementAndGet()
                }}) {
                HubScreen(state,actions,preferences=prefs.value)
            }
        }}}
        compose.waitForIdle();compose.mainClock.autoAdvance=false
    }
    private fun index()=compose.onNodeWithTag("hub-period-tabs",true).fetchSemanticsNode().config[DesktopPeriodIndicatorIndex]
    private fun running()=compose.onNodeWithTag("hub-period-tabs",true).fetchSemanticsNode().config[DesktopPeriodIndicatorAnimating]
    private fun centerBottomPixel():Int {
        val image=compose.onNodeWithTag("hub-period-tabs",true).captureToImage().toPixelMap()
        return image[image.width/2,(image.height*.82f).toInt()].toArgb()
    }
    @Test fun actualHeaderDrawsOneSlidingSelectionWithoutRecomposingTheHubOnFrames() {
        val actions=Actions();val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList(),appearance=AppearancePreferences(reduceMotion="off")))
        setup(prefs,actions)
        assertEquals(0f,index(),0f);val before=centerBottomPixel()
        compose.onNodeWithTag("hub-period-allTime").performClick();compose.mainClock.advanceTimeBy(32)
        assertTrue(index()>0f && index()<2f);assertTrue(running())
        val middle=centerBottomPixel();assertNotEquals("Actual shared selection pixels cross the middle slot",before,middle)
        val count=owners.get();compose.mainClock.advanceTimeBy(96)
        assertEquals("Only the selection drawing reads motion frames",count,owners.get())
        compose.mainClock.advanceTimeBy(160)
        assertEquals(2f,index(),0f);assertFalse(running());assertEquals(before,centerBottomPixel())
        assertEquals(0,actions.calls);compose.mainClock.autoAdvance=true
    }
    @Test fun realHeaderSupportsQuickReverseReductionAndCoalescedStopWithoutReplay() {
        val actions=Actions();val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList(),appearance=AppearancePreferences(reduceMotion="off")))
        setup(prefs,actions)
        val forwardCursor=renderCount()
        compose.onNodeWithTag("hub-period-allTime").performClick()
        compose.mainClock.advanceTimeUntil(timeoutMillis=220) {firstRenderAfter(forwardCursor,2)!=null}
        val forward=requireNotNull(firstRenderAfter(forwardCursor,2))
        assertEquals("Forward retarget begins at the previous displayed slot",0f,forward.frame.index,0f)
        assertTrue(forward.frame.running)
        compose.mainClock.advanceTimeBy(32)
        val lastDrawnForward=index()
        assertTrue(lastDrawnForward>0f && lastDrawnForward<2f)
        val reverseCursor=renderCount()
        compose.onNodeWithTag("hub-period-today").performClick()
        compose.mainClock.advanceTimeUntil(timeoutMillis=220) {firstRenderAfter(reverseCursor,0)!=null}
        val reverse=requireNotNull(firstRenderAfter(reverseCursor,0))
        // Input dispatch, recomposition and the render commit may have distinct
        // virtual timestamps. Compare at the ACTUAL retarget commit captured
        // after production render(), never click-return time or a fixed16ms.
        val elapsed=(reverse.atNanos-forward.atNanos).coerceAtLeast(0L)
        val inputProgress=(elapsed/220_000_000f).coerceIn(0f,1f)
        val currentAtInput=2f*CubicBezierEasing(.22f,1f,.36f,1f).transform(inputProgress)
        assertTrue("Rapid reversal must occur before the forward endpoint",inputProgress>0f && inputProgress<1f)
        assertEquals("Rapid reversal samples the current transform at the actual retarget commit",currentAtInput,reverse.frame.index,.001f)
        assertTrue("Reversal cannot jump back to the original slot",reverse.frame.index>0f && reverse.frame.index<2f)
        assertTrue("Forward sampling cannot jump behind its last actual drawing",reverse.frame.index+.001f>=lastDrawnForward)
        assertTrue(reverse.frame.running)
        assertEquals("Retarget evidence must remain bounded",0,renderOverflow.get())
        compose.mainClock.advanceTimeBy(240);assertEquals(0f,index(),0f);assertFalse(running())
        compose.onNodeWithTag("hub-period-month").performClick();compose.mainClock.advanceTimeBy(32);assertTrue(running())
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);compose.mainClock.advanceTimeBy(32)
        assertEquals(1f,index(),0f);assertFalse(running())
        compose.onNodeWithTag("hub-period-today").performClick();compose.mainClock.advanceTimeBy(32)
        assertTrue("The first real selection after resumed visibility still transitions",running())
        assertTrue(index()>0f && index()<1f)
        compose.mainClock.advanceTimeBy(240);assertEquals(0f,index(),0f);assertFalse(running())
        compose.runOnIdle {prefs.value=prefs.value.copy(appearance=prefs.value.appearance.copy(reduceMotion="system"))}
        compose.onNodeWithTag("hub-period-allTime").performClick();compose.mainClock.advanceTimeBy(32)
        assertEquals(2f,index(),0f);assertFalse(running())
        compose.runOnIdle {prefs.value=prefs.value.copy(appearance=prefs.value.appearance.copy(reduceMotion="on"))}
        compose.onNodeWithTag("hub-period-today").performClick();compose.mainClock.advanceTimeBy(32)
        assertEquals(0f,index(),0f);assertFalse(running())
        compose.runOnIdle {prefs.value=prefs.value.copy(appearance=prefs.value.appearance.copy(reduceMotion="off"))}
        compose.onNodeWithTag("hub-period-month").performClick();compose.mainClock.advanceTimeBy(32);assertTrue(running())
        assertEquals(0,actions.calls);compose.mainClock.autoAdvance=true
    }
}
