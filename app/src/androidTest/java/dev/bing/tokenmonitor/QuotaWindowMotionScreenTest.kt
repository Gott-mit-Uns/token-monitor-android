package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
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

/** Real Limits route, native Canvas pixels, native Text paint ranges and owner isolation. */
class QuotaWindowMotionScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val clock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
    private val commits=ConcurrentHashMap<String,AtomicInteger>()
    private val old="2026-10-11T00:00:00Z"
    private val next="2026-10-12T00:00:00Z"
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
    private fun provider(account:String,remaining:Double,at:String=old,updated:String=old)=buildJsonObject {
        put("provider","codex");put("status","ok");put("accountKey",account);put("accountName",account);put("updatedAt",updated)
        put("windows",buildJsonArray {add(buildJsonObject {put("kind","session");put("limitId","session");put("remainingPercent",remaining);put("resetsAt",at)})})
    }
    private fun snapshot(vararg providers:JsonObject)=HubParser.parse(buildJsonObject {
        put("periods",buildJsonObject {put("today",buildJsonObject {put("totalTokens",1000)})})
        put("limits",buildJsonObject {put("providers",JsonArray(providers.toList()))})
    }.toString())
    private fun state(snapshot:Snapshot)=HubState(snapshot=snapshot,history=HubHistory(emptyList(),emptyList()),
        modelNames=ModelNames(emptyMap(),"off"),subscriptions=emptyList(),pricing=HubPricing(true,0,null,emptyList()))
    private fun prefs(used:Boolean=false)=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList(),
        limits=LimitDisplayPreferences(showUsed=used),appearance=AppearancePreferences(reduceMotion="off",vendorColors=mapOf("codex" to "#2060a0")))
    private fun setup(state:State<HubState>,prefs:State<DisplayPreferences>,actions:Actions,system:State<Float> = mutableStateOf(1f)) {
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(true,prefs.value.language,prefs.value.appearance) {
            CompositionLocalProvider(LocalDesktopAnimationClock provides clock,LocalDesktopSystemMotionScale provides system.value,
                LocalHubCalendarClock provides Clock.fixed(Instant.parse(old),ZoneId.of("Asia/Shanghai")),
                LocalHeadlineRenderObserver provides {part->commits.computeIfAbsent(part) {AtomicInteger()}.incrementAndGet()}) {
                HubScreen(state.value,actions,preferences=prefs.value)
            }
        }}}
        compose.waitForIdle()
    }
    private fun select(page:String="limits") {
        compose.onNodeWithTag("hub-view-selector").performClick();compose.mainClock.advanceTimeBy(240)
        compose.onNodeWithTag("view-$page").performClick();compose.mainClock.advanceTimeBy(32)
    }
    private fun meter(index:Int=0)=compose.onNodeWithTag("limit-meter-$index-0",true)
    private fun fill(index:Int=0)=meter(index).fetchSemanticsNode().config[QuotaMotionFill]
    private fun glow(index:Int=0)=meter(index).fetchSemanticsNode().config[QuotaMotionGlow]
    private fun running(index:Int=0)=meter(index).fetchSemanticsNode().config[QuotaMotionRunning]
    private fun count(part:String)=commits[part]?.get() ?: 0
    private fun percent(index:Int=0)=compose.onNodeWithTag("limit-value-$index-0",true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text]
        .single().text.substringBefore('%').toInt()
    private fun paintedFill():Float {
        val pixels=meter().captureToImage().toPixelMap();val y=pixels.height/2
        val ink=pixels[minOf(4,pixels.width-1),y]
        return (0 until pixels.width).count {x->val c=pixels[x,y];
            abs(c.red-ink.red)<.015f && abs(c.green-ink.green)<.015f && abs(c.blue-ink.blue)<.015f
        }.toFloat()/pixels.width
    }
    private fun assertCompleteText(tag:String) {
        val results=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag(tag,true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(results)}
        val result=results.single()
        assertFalse(result.didOverflowHeight)
        repeat(result.lineCount) {line->
            assertFalse(result.isLineEllipsized(line));assertTrue(result.getLineLeft(line)>=-.5f)
            assertTrue(result.getLineRight(line)<=result.size.width+.5f)
            assertTrue(result.getLineBottom(line)<=result.size.height+.5f)
        }
        val bounds=compose.onNodeWithTag(tag,true).fetchSemanticsNode().boundsInRoot
        val body=compose.onNodeWithTag("hub-content").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.left>=body.left-.5f && bounds.right<=body.right+.5f)
    }

    @Test fun actualLimitsEntryPaints420msBarsWithoutCountingValuesOrReplayingCurrentView() {
        val source=mutableStateOf(state(snapshot(provider("fixture-a",80.0))));val prefs=mutableStateOf(prefs());val actions=Actions()
        setup(source,prefs,actions);compose.mainClock.autoAdvance=false;select()
        val first=fill();assertTrue(first>0f && first<.8f)
        compose.onNodeWithTag("limit-value-0-0").assertTextEquals("80% left")
        assertEquals(first,paintedFill(),.035f)
        val hub=count("hub");val account=count("quota-account:0")
        compose.mainClock.advanceTimeBy(128)
        assertTrue(fill()>first);assertEquals(fill(),paintedFill(),.035f)
        assertEquals(hub,count("hub"));assertEquals(account,count("quota-account:0"))
        compose.mainClock.advanceTimeBy(500);assertEquals(.8f,fill(),.0001f)
        select();assertEquals(.8f,fill(),.0001f);assertFalse(running())
        select("models");select();assertTrue(fill()<.8f)
        assertCompleteText("limit-value-0-0");assertEquals(0,actions.reads);compose.mainClock.autoAdvance=true
    }

    @Test fun actualResetBatchSharesDeadlineAndFreshnessKeepsPercentAndCompletionGlowTimeline() {
        val source=mutableStateOf(state(snapshot(provider("fixture-a",20.0),provider("fixture-b",90.0))))
        val prefs=mutableStateOf(prefs());val actions=Actions()
        setup(source,prefs,actions);compose.mainClock.autoAdvance=false;select();compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(provider("fixture-a",100.0,next),provider("fixture-b",100.0,next)))}
        compose.mainClock.advanceTimeBy(32)
        assertTrue(fill()>=.2f && fill()<1f);assertTrue(fill(1)>=.9f && fill(1)<1f)
        val bounds=meter().fetchSemanticsNode().boundsInRoot
        val hub=count("hub");val account=count("quota-account:0")
        compose.mainClock.advanceTimeBy(640)
        assertTrue(percent() in 70..90);assertTrue(percent(1) in 96..99)
        assertEquals(fill(),paintedFill(),.035f)
        assertEquals(bounds,meter().fetchSemanticsNode().boundsInRoot)
        assertEquals(hub,count("hub"));assertEquals(account,count("quota-account:0"))
        val before=percent();val beforeFill=fill()
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(provider("fixture-a",100.0,next,"2026-10-11T00:01:00Z"),provider("fixture-b",100.0,next,"2026-10-11T00:01:00Z")))}
        compose.mainClock.advanceTimeBy(32)
        assertTrue(percent()>=before);assertTrue(fill()>=beforeFill)
        compose.mainClock.advanceTimeBy(800)
        assertEquals(100,percent());assertEquals(100,percent(1));assertEquals(1f,fill(),.0001f);assertEquals(1f,fill(1),.0001f)
        assertTrue(glow()>0f);assertTrue(glow(1)>0f)
        val glowPixels=meter().captureToImage().toPixelMap();val glowing=glowPixels[glowPixels.width/2,glowPixels.height/2]
        compose.mainClock.advanceTimeBy(600);assertFalse(running());assertEquals(0f,glow(),0f)
        val finalPixels=meter().captureToImage().toPixelMap();val final=finalPixels[finalPixels.width/2,finalPixels.height/2]
        assertTrue("The real native highlight visibly lightens the fill",glowing.red>final.red && glowing.green>final.green && glowing.blue>final.blue)
        assertCompleteText("limit-value-0-0");assertEquals(0,actions.reads);compose.mainClock.autoAdvance=true
    }

    @Test fun actualUsedModeStableAccountReorderAndNonResetCorrectionsKeepSourceRules() {
        val source=mutableStateOf(state(snapshot(provider("fixture-a",10.0),provider("fixture-b",50.0))))
        val prefs=mutableStateOf(prefs(true));val actions=Actions()
        setup(source,prefs,actions);compose.mainClock.autoAdvance=false;select();compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(provider("fixture-a",100.0,next),provider("fixture-b",50.0)))}
        compose.mainClock.advanceTimeBy(400);assertTrue(percent() in 1..89)
        val before=percent()
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(provider("fixture-b",50.0),provider("fixture-a",100.0,next)))}
        compose.mainClock.advanceTimeBy(32)
        assertEquals(50,percent(0));assertTrue(percent(1)<=before);assertTrue(running(1))
        compose.mainClock.advanceTimeBy(2200);assertEquals(0,percent(1));assertEquals(0f,fill(1),0f)
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(provider("fixture-b",40.0),provider("fixture-a",80.0,next)))}
        compose.mainClock.advanceTimeBy(32);assertEquals(20,percent(1));assertEquals(.2f,fill(1),.0001f);assertFalse(running())
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(provider("fixture-b",40.0),provider("fixture-a",100.0,next)))}
        compose.mainClock.advanceTimeBy(32);assertEquals(0,percent(1));assertFalse(running()) // Same cycle rejects correction.
        assertCompleteText("limit-value-1-0");assertEquals(0,actions.reads);compose.mainClock.autoAdvance=true
    }

    @Test fun activeResetStopsImmediatelyAndSettingsReturnAndReductionCannotReplayBackgroundChanges() {
        val source=mutableStateOf(state(snapshot(provider("fixture-a",20.0))));val prefs=mutableStateOf(prefs());val actions=Actions()
        val system=mutableStateOf(1f)
        setup(source,prefs,actions,system);compose.mainClock.autoAdvance=false;select();compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(provider("fixture-a",100.0,next)))}
        compose.mainClock.advanceTimeBy(100);assertTrue(running())
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);compose.mainClock.advanceTimeBy(32)
        assertFalse(running());assertEquals(100,percent());assertEquals(1f,fill(),0f);assertEquals(0f,glow(),0f)
        compose.onNodeWithTag("hub-settings-toggle").performClick();compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(provider("fixture-a",30.0,next)))}
        compose.onNodeWithTag("hub-settings-toggle").performClick();compose.mainClock.advanceTimeBy(32)
        assertFalse(running());assertEquals(.3f,fill(),.0001f)
        compose.runOnIdle {prefs.value=prefs.value.copy(appearance=prefs.value.appearance.copy(reduceMotion="system"));system.value=0f
            source.value=source.value.copy(snapshot=snapshot(provider("fixture-a",100.0,"2026-10-13T00:00:00Z")))}
        compose.mainClock.advanceTimeBy(32);assertFalse(running());assertEquals(100,percent())
        compose.runOnIdle {prefs.value=prefs.value.copy(appearance=prefs.value.appearance.copy(reduceMotion="off"))}
        compose.mainClock.advanceTimeBy(32);assertFalse(running())
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(provider("fixture-a",20.0,"2026-10-13T00:00:00Z")))}
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(provider("fixture-a",100.0,"2026-10-14T00:00:00Z")))}
        compose.mainClock.advanceTimeBy(32);assertTrue("Forced off retains source motion even with system scale zero",running())
        compose.runOnIdle {prefs.value=prefs.value.copy(appearance=prefs.value.appearance.copy(reduceMotion="on"))}
        compose.mainClock.advanceTimeBy(32);assertFalse(running());assertEquals(100,percent());assertEquals(0f,glow(),0f)
        assertEquals(0,actions.reads);compose.mainClock.autoAdvance=true
    }

    @Test fun resumedVisibleQuotaAcknowledgesItsTargetBeforeTheFirstRealRefill() {
        val source=mutableStateOf(state(snapshot(provider("fixture-a",20.0))));val prefs=mutableStateOf(prefs());val actions=Actions()
        setup(source,prefs,actions);compose.mainClock.autoAdvance=false;select()
        assertTrue("A real LIMITS entry is running before STOP",running())
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);compose.mainClock.advanceTimeBy(32)
        assertFalse("Resuming the unchanged target cannot replay entry",running())
        assertEquals(.2f,fill(),.0001f);assertEquals(20,percent());assertEquals(0f,glow(),0f)
        // This is the first payload change after resume. It is a genuine reset
        // of the same window into a later cycle, not an entry or data correction.
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(provider("fixture-a",100.0,next)))}
        compose.mainClock.advanceTimeBy(32)
        assertTrue("The first eligible live reset after resume must not be swallowed as restoration",running())
        assertTrue(fill()>=.2f && fill()<1f);assertTrue(percent() in 20..99)
        compose.mainClock.advanceTimeBy(2200)
        assertFalse(running());assertEquals(1f,fill(),0f);assertEquals(100,percent());assertEquals(0f,glow(),0f)
        assertEquals(0,actions.reads);compose.mainClock.autoAdvance=true
    }

    @Test fun actualMoneyValueOverrideStaysCompleteWhileItsRemainingMeterRefills() {
        fun funds(amount:Double,remaining:Double,at:String)=buildJsonObject {
            put("provider","openrouter");put("status","ok");put("accountKey","fixture-funds")
            put("balance",buildJsonObject {put("amount",amount);put("currency","USD")})
            put("windows",buildJsonArray {add(buildJsonObject {put("kind","billing");put("metric","credits");put("label","Credits")
                put("limitId","credits");put("remainingPercent",remaining);put("resetsAt",at);put("remaining",amount);put("currency","USD")})})
        }
        val source=mutableStateOf(state(snapshot(funds(20.0,20.0,old))))
        val prefs=mutableStateOf(prefs(true));val actions=Actions()
        setup(source,prefs,actions);compose.mainClock.autoAdvance=false;select();compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("limit-value-0-0").assertTextEquals("$20.00")
        assertEquals(.2f,fill(),.0001f) // Money overrides always use remaining, even with used mode on.
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(funds(100.0,100.0,next)))}
        compose.mainClock.advanceTimeBy(200);assertTrue(fill()>.2f && fill()<1f)
        compose.onNodeWithTag("limit-value-0-0").assertTextEquals("$100.00")
        assertCompleteText("limit-value-0-0")
        val hub=count("hub");val account=count("quota-account:0")
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag("limit-value-0-0").assertTextEquals("$100.00")
        assertEquals(hub,count("hub"));assertEquals(account,count("quota-account:0"))
        assertEquals(0,actions.reads);compose.mainClock.autoAdvance=true
    }
}
