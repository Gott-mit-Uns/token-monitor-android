package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** Native lazy rows and native layers. Tests use deterministic time, not a physical FPS claim. */
class BreakdownRowPlacementMotionScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val clock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
    private val commits=ConcurrentHashMap<String,AtomicInteger>()
    private val blue=Color(0xFF2060A0)
    private class Actions:HubUiActions {
        var reads=0
        override fun connect(address:String,credential:String,alternate:String) {reads++}
        override fun refreshSaved() {reads++}
        override fun switchEndpoint() {reads++}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadHistory() {reads++}
        override fun loadModelNames() {reads++}
        override fun loadPricing() {reads++}
        override fun loadSubscriptions() {reads++}
        override fun loadDeviceHistory() {reads++}
        override fun forget() {}
        override fun renameDevice(id:String,input:String) {}
    }
    private fun snapshot(models:Map<String,Long> = mapOf("a" to 1000,"b" to 700,"c" to 500),
        tools:Map<String,Long> = mapOf("claude" to 1000)):Snapshot {
        val body=buildJsonObject {put("periods",buildJsonObject {put("today",buildJsonObject {
            put("totalTokens",maxOf(models.values.sum(),tools.values.sum()))
            put("models",buildJsonObject {models.forEach {(key,value)->put(key,value)}})
            put("clients",buildJsonObject {tools.forEach {(key,value)->put(key,value)}})
        })})}
        return HubParser.parse(body.toString())
    }
    private fun source(snapshot:Snapshot)=HubState(snapshot=snapshot,history=HubHistory(emptyList(),emptyList()),
        modelNames=ModelNames(emptyMap(),"off"),subscriptions=emptyList(),pricing=HubPricing(true,0,null,emptyList()))
    private fun prefs()=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList(),appearance=AppearancePreferences(reduceMotion="off",vendorColors=mapOf("codex" to "#2060a0")))
    private fun setup(source:State<HubState>,prefs:State<DisplayPreferences>,actions:Actions) {
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(true,prefs.value.language,prefs.value.appearance) {
            CompositionLocalProvider(LocalDesktopAnimationClock provides clock,LocalDesktopSystemMotionScale provides 1f,
                LocalHubCalendarClock provides Clock.fixed(Instant.parse("2026-10-11T08:00:00Z"),ZoneId.of("Asia/Shanghai")),
                LocalHeadlineRenderObserver provides {part->commits.computeIfAbsent(part) {AtomicInteger()}.incrementAndGet()}) {
                HubScreen(source.value,actions,preferences=prefs.value)
            }
        }}}
        compose.waitForIdle()
    }
    private fun select(page:String) {
        compose.onNodeWithTag("hub-view-selector").performClick();compose.mainClock.advanceTimeBy(240)
        compose.onNodeWithTag("view-$page").performClick();compose.mainClock.advanceTimeBy(32)
    }
    private fun layer(key:String)=compose.onNodeWithTag("breakdown-row-layer-$key",true).fetchSemanticsNode()
    private fun offset(key:String)=layer(key).config[BreakdownRowTranslationY]
    private fun alpha(key:String)=layer(key).config[BreakdownRowAlpha]
    private fun count(key:String)=commits[key]?.get() ?: 0
    private fun counterHasNoOverflow(tag:String) {
        val results=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag(tag,true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(results)}
        assertEquals(1,results.size)
        val result=results.single()
        assertFalse("The native counter has no vertical text overflow",result.didOverflowHeight)
        repeat(result.lineCount) {line->
            assertFalse("Line $line is not ellipsized",result.isLineEllipsized(line))
            assertTrue("Line $line begins inside its real Text width",result.getLineLeft(line)>=-.5f)
            assertTrue("Line $line paints inside its real Text width",result.getLineRight(line)<=result.size.width+.5f)
            assertTrue("Line $line paints inside its real Text height",result.getLineBottom(line)<=result.size.height+.5f)
        }
    }

    @Test fun actualModelsReorderMovesPaintedRowsWith280msFlipAndKeepsOwnerOutOfFrames() {
        val source=mutableStateOf(source(snapshot()));val prefs=mutableStateOf(prefs());val actions=Actions()
        setup(source,prefs,actions);compose.mainClock.autoAdvance=false;select("models");compose.mainClock.advanceTimeBy(500)
        val key="MODELS:b"
        val before=layer(key).boundsInRoot.top
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(mapOf("a" to 1000,"b" to 400,"c" to 800)))}
        compose.mainClock.advanceTimeBy(32)
        val outer=compose.onNodeWithTag("breakdown-row-layout-$key",true).fetchSemanticsNode().boundsInRoot
        val native=layer(key).boundsInRoot
        val start=offset(key)
        assertTrue("The old second row moves down to its actual new layout position",outer.top>before)
        assertTrue("The row begins with a negative placement transform",start<0f)
        assertTrue(native.top>=before-.5f && native.top<outer.top)
        assertEquals("The native layer bounds carry the actual transform",start,native.top-outer.top,1f)
        counterHasNoOverflow("breakdown-tokens-model-b")
        val hub=count("hub");val row=count("breakdown-row:MODELS:b")
        val firstAt=compose.mainClock.currentTime
        compose.mainClock.advanceTimeBy(96)
        val middle=offset(key)
        assertTrue("FLIP advances from $start to $middle over actual virtual interval ${compose.mainClock.currentTime-firstAt}ms",middle>start && middle<0f)
        assertEquals(hub,count("hub"));assertEquals(row,count("breakdown-row:MODELS:b"))
        compose.mainClock.advanceTimeBy(240)
        assertEquals(0f,offset(key),0f);assertEquals(1f,alpha(key),0f)
        val final=layer(key).boundsInRoot.top
        assertEquals(outer.top,final,1f)
        compose.runOnIdle {source.value=source.value.copy(savedAt=42)}
        compose.mainClock.advanceTimeBy(32);assertEquals(0f,offset(key),0f)
        assertEquals(0,actions.reads);compose.mainClock.autoAdvance=true
    }

    @Test fun actualNewToolLayerFadesAndMovesItsNativeBoundsAndPixelsWithoutChangingTheBarOrNumberSource() {
        val source=mutableStateOf(source(snapshot()));val prefs=mutableStateOf(prefs());val actions=Actions()
        setup(source,prefs,actions);compose.mainClock.autoAdvance=false;select("tools");compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(tools=mapOf("claude" to 1000,"codex" to 500)))}
        compose.mainClock.advanceTimeBy(32)
        val key="TOOLS:codex"
        val initialAlpha=alpha(key);val initialY=offset(key)
        assertTrue(initialAlpha>0f && initialAlpha<1f)
        assertTrue(initialY>0f)
        val outer=compose.onNodeWithTag("breakdown-row-layout-$key",true).fetchSemanticsNode().boundsInRoot
        assertEquals("Row layer semantic bounds expose its current translated layout",initialY,layer(key).boundsInRoot.top-outer.top,1f)
        fun painted()=compose.onNodeWithTag("breakdown-color-tool-codex",true).captureToImage().toPixelMap().let {pixels->
            pixels[(pixels.width*.015f).toInt().coerceIn(0,pixels.width-1),pixels.height/2]
        }
        val firstPixel=painted()
        assertNotEquals("Native bar pixels include the row layer's current opacity",blue.toArgb(),firstPixel.toArgb())
        val hub=count("hub");val row=count("breakdown-row:TOOLS:codex")
        compose.mainClock.advanceTimeBy(96)
        assertTrue(alpha(key)>initialAlpha);assertTrue(offset(key)<initialY)
        assertEquals(hub,count("hub"));assertEquals(row,count("breakdown-row:TOOLS:codex"))
        compose.mainClock.advanceTimeBy(220)
        assertEquals(1f,alpha(key),0f);assertEquals(0f,offset(key),0f)
        val lastPixel=painted()
        assertTrue(abs(lastPixel.red-blue.red)<.01f && abs(lastPixel.green-blue.green)<.01f && abs(lastPixel.blue-blue.blue)<.01f)
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("breakdown-tokens-tool-codex",true).assertTextEquals("500")
        counterHasNoOverflow("breakdown-tokens-tool-codex")
        assertEquals(0,actions.reads);compose.mainClock.autoAdvance=true
    }

    @Test fun actualLargeGateAndLateLazyMountDoNotCreateDelayedRowAnimations() {
        val large=(0 until 320).associate {"model-$it" to (it+1L)}
        val source=mutableStateOf(source(snapshot(large)));val prefs=mutableStateOf(prefs());val actions=Actions()
        setup(source,prefs,actions);compose.mainClock.autoAdvance=false;select("models")
        assertEquals("Main entry keeps the real source 420ms bar path, with no row fade",1f,alpha("MODELS:model-319"),0f)
        assertEquals(0f,offset("MODELS:model-319"),0f)
        compose.mainClock.advanceTimeBy(500)
        val compact=(0 until 39).associate {"compact-$it" to (1000L-it)}
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(compact))};compose.mainClock.advanceTimeBy(32)
        assertEquals("320→39 fails the old-side gate",1f,alpha("MODELS:compact-0"),0f)
        val withNew=compact+("late-new" to 1L)
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(withNew))};compose.mainClock.advanceTimeBy(400)
        // A native lazy scroll waits for a real layout frame. The placement's
        // finite entry has already ended, so enable only ordinary scroll frames.
        compose.mainClock.autoAdvance=true
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("breakdown-row-layer-MODELS:late-new"))
        compose.mainClock.autoAdvance=false
        compose.mainClock.advanceTimeBy(32)
        assertEquals("A new lazy row appearing after 240ms plus stagger stays settled",1f,alpha("MODELS:late-new"),0f)
        assertEquals(0f,offset("MODELS:late-new"),0f)
        assertFalse(layer("MODELS:late-new").config[BreakdownRowAnimating])
        counterHasNoOverflow("breakdown-tokens-model-late-new")
        assertEquals(0,actions.reads);compose.mainClock.autoAdvance=true
    }

    @Test fun activeRowTransformsSettleOnReductionStopAndSameViewSettingsReturn() {
        val source=mutableStateOf(source(snapshot()));val prefs=mutableStateOf(prefs());val actions=Actions()
        setup(source,prefs,actions);compose.mainClock.autoAdvance=false;select("models");compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(mapOf("a" to 1000,"b" to 400,"c" to 800)))}
        compose.mainClock.advanceTimeBy(32);assertTrue(offset("MODELS:b")<0f)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);compose.mainClock.advanceTimeBy(32)
        assertEquals(0f,offset("MODELS:b"),0f);assertEquals(1f,alpha("MODELS:b"),0f)
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(mapOf("a" to 1000,"b" to 900,"c" to 600)))}
        compose.mainClock.advanceTimeBy(32);assertTrue(offset("MODELS:b")>0f)
        compose.runOnIdle {prefs.value=prefs.value.copy(appearance=prefs.value.appearance.copy(reduceMotion="on"))}
        compose.mainClock.advanceTimeBy(32);assertEquals(0f,offset("MODELS:b"),0f)
        compose.runOnIdle {prefs.value=prefs.value.copy(appearance=prefs.value.appearance.copy(reduceMotion="off"))}
        compose.mainClock.advanceTimeBy(32);assertEquals(0f,offset("MODELS:b"),0f)
        compose.onNodeWithTag("hub-settings-toggle").performClick();compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(mapOf("a" to 1000,"b" to 400,"c" to 800,"new" to 200)))}
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("hub-settings-toggle").performClick();compose.mainClock.advanceTimeBy(32)
        assertEquals("Returning to the same hidden main view is not a new-row entry",1f,alpha("MODELS:new"),0f)
        assertEquals(0f,offset("MODELS:new"),0f)
        compose.mainClock.advanceTimeBy(400);assertEquals(1f,alpha("MODELS:new"),0f)
        assertEquals(0,actions.reads);compose.mainClock.autoAdvance=true
    }
}
