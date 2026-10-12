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

/** Actual Home compact meter pixels, static real text and owner/row frame isolation. */
class HomeQuotaEntryScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val clock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
    private val commits=ConcurrentHashMap<String,AtomicInteger>()
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
    private fun snapshot(remaining:Double=80.0,cycle:String="2026-10-12T00:00:00Z")=HubParser.parse(buildJsonObject {
        put("periods",buildJsonObject {put("today",buildJsonObject {put("totalTokens",1000)})})
        put("limits",buildJsonObject {put("providers",buildJsonArray {add(buildJsonObject {
            put("provider","codex");put("status","ok");put("accountKey","fixture-home-account")
            put("windows",buildJsonArray {add(buildJsonObject {put("kind","session");put("remainingPercent",remaining);put("resetsAt",cycle)})})
        })})})
    }.toString())
    private fun state()=HubState(snapshot=snapshot(),savedAt=Instant.parse("2026-10-11T04:00:00Z").toEpochMilli(),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"),
        subscriptions=emptyList(),pricing=HubPricing(true,0,null,emptyList()))
    private fun prefs()=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=listOf("limits"),
        limits=LimitDisplayPreferences(homeBars=true),appearance=AppearancePreferences(reduceMotion="off",vendorColors=mapOf("codex" to "#2060a0")))
    private fun setup(state:State<HubState>,prefs:State<DisplayPreferences>,actions:Actions,system:State<Float> = mutableStateOf(1f),
        calendar:State<Clock> = mutableStateOf(Clock.fixed(Instant.parse("2026-10-11T00:00:00Z"),ZoneId.of("Asia/Shanghai")))) {
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(true,prefs.value.language,prefs.value.appearance) {
            CompositionLocalProvider(LocalDesktopAnimationClock provides clock,LocalDesktopSystemMotionScale provides system.value,
                LocalHubCalendarClock provides calendar.value,
                LocalHeadlineRenderObserver provides {part->commits.computeIfAbsent(part) {AtomicInteger()}.incrementAndGet()}) {
                HubScreen(state.value,actions,preferences=prefs.value)
            }
        }}}
        compose.waitForIdle()
    }
    private fun select(page:String) {
        compose.onNodeWithTag("hub-view-selector").performClick();compose.mainClock.advanceTimeBy(240)
        compose.onNodeWithTag("view-$page").performClick();compose.mainClock.advanceTimeBy(32)
    }
    private fun meter()=compose.onNodeWithTag("home-quota-entry-0-0",true)
    private fun fill()=meter().fetchSemanticsNode().config[HomeQuotaEntryFill]
    private fun running()=meter().fetchSemanticsNode().config[HomeQuotaEntryRunning]
    private fun count(part:String)=commits[part]?.get() ?: 0
    private fun paintedFill():Float {
        val pixels=meter().captureToImage().toPixelMap();val y=pixels.height/2;val ink=pixels[minOf(4,pixels.width-1),y]
        return (0 until pixels.width).count {x->val c=pixels[x,y];
            abs(c.red-ink.red)<.015f && abs(c.green-ink.green)<.015f && abs(c.blue-ink.blue)<.015f
        }.toFloat()/pixels.width
    }
    private fun assertCompleteText() {
        val results=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("limit-value-0-0",true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(results)}
        val result=results.single();assertFalse(result.didOverflowHeight)
        repeat(result.lineCount) {line->assertFalse(result.isLineEllipsized(line));assertTrue(result.getLineLeft(line)>=-.5f)
            assertTrue(result.getLineRight(line)<=result.size.width+.5f);assertTrue(result.getLineBottom(line)<=result.size.height+.5f)}
        val value=compose.onNodeWithTag("limit-value-0-0",true).fetchSemanticsNode().boundsInRoot
        val body=compose.onNodeWithTag("hub-content").fetchSemanticsNode().boundsInRoot
        assertTrue(value.left>=body.left-.5f && value.right<=body.right+.5f && value.top>=body.top-.5f && value.bottom<=body.bottom+.5f)
    }

    @Test fun actualHomeInitializationIsStaticAndTrueReturnPaintsOnly420msBarsWithRealValues() {
        val source=mutableStateOf(state());val prefs=mutableStateOf(prefs());val actions=Actions()
        val calendar=mutableStateOf<Clock>(Clock.fixed(Instant.parse("2026-10-11T00:00:00Z"),ZoneId.of("Asia/Shanghai")))
        setup(source,prefs,actions,calendar=calendar);compose.mainClock.autoAdvance=false
        assertEquals(.8f,fill(),.0001f);assertFalse(running())
        select("models");select("home")
        val first=fill();assertTrue(first>0f && first<.8f)
        compose.onNodeWithTag("limit-value-0-0").assertTextEquals("80% left")
        assertEquals(first,paintedFill(),.035f)
        val bounds=meter().fetchSemanticsNode().boundsInRoot
        val hub=count("hub");val row=count("home-quota-row:0")
        compose.mainClock.advanceTimeBy(128)
        assertTrue(fill()>first);assertEquals(fill(),paintedFill(),.035f)
        assertEquals(bounds,meter().fetchSemanticsNode().boundsInRoot)
        assertEquals(hub,count("hub"));assertEquals(row,count("home-quota-row:0"))
        // The rounded 24h boundary text is identical at +30s; this is an owner clock tick,
        // not an actual Home content replacement and must not snap the entry to full.
        compose.runOnIdle {calendar.value=Clock.fixed(Instant.parse("2026-10-11T00:00:30Z"),ZoneId.of("Asia/Shanghai"))}
        compose.mainClock.advanceTimeBy(32);assertTrue(running());assertTrue(fill()<.8f)
        // Persisting the unchanged snapshot only advances cache freshness; it does not replace Home content.
        val beforeFreshness=fill()
        compose.runOnIdle {source.value=source.value.copy(savedAt=source.value.savedAt!!+1000)}
        compose.mainClock.advanceTimeBy(32);assertTrue(running());assertTrue(fill()>beforeFreshness && fill()<.8f)
        compose.mainClock.advanceTimeBy(500);assertEquals(.8f,fill(),.0001f);assertFalse(running())
        select("home");assertEquals(.8f,fill(),.0001f);assertFalse(running())
        assertCompleteText();assertEquals(0,actions.reads);compose.mainClock.autoAdvance=true
    }

    @Test fun actualHomeContentReplacementAndRealQuotaResetStayStaticWithoutCountOrGlow() {
        val source=mutableStateOf(state());val prefs=mutableStateOf(prefs());val actions=Actions()
        setup(source,prefs,actions);compose.mainClock.autoAdvance=false;select("models");select("home")
        assertTrue(running())
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(20.0))}
        compose.mainClock.advanceTimeBy(32);assertEquals(.2f,fill(),.0001f);assertFalse(running())
        compose.onNodeWithTag("limit-value-0-0").assertTextEquals("20% left")
        val pixels=meter().captureToImage().toPixelMap();val before=pixels[4,pixels.height/2]
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(100.0,"2026-10-13T00:00:00Z"))}
        compose.mainClock.advanceTimeBy(32);assertEquals(1f,fill(),0f);assertFalse(running())
        compose.onNodeWithTag("limit-value-0-0").assertTextEquals("100% left")
        compose.mainClock.advanceTimeBy(1460)
        val final=meter().captureToImage().toPixelMap();val current=final[4,final.height/2]
        assertEquals(before.red,current.red,.005f);assertEquals(before.green,current.green,.005f);assertEquals(before.blue,current.blue,.005f)
        compose.onNodeWithTag("limit-value-0-0").assertTextEquals("100% left") // No reset count/glow timeline on Home.
        compose.runOnIdle {prefs.value=prefs.value.copy(limits=prefs.value.limits.copy(homeBars=false))}
        compose.mainClock.advanceTimeBy(32);meter().assertDoesNotExist()
        compose.runOnIdle {prefs.value=prefs.value.copy(limits=prefs.value.limits.copy(homeBars=true))}
        compose.mainClock.advanceTimeBy(32);assertEquals(1f,fill(),0f);assertFalse(running())
        assertCompleteText();assertEquals(0,actions.reads);compose.mainClock.autoAdvance=true
    }

    @Test fun actualHomeStopSettingsAndThreeReductionModesDoNotReplayConsumedEntry() {
        val source=mutableStateOf(state());val prefs=mutableStateOf(prefs());val actions=Actions();val system=mutableStateOf(1f)
        setup(source,prefs,actions,system);compose.mainClock.autoAdvance=false;select("models");select("home");assertTrue(running())
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);compose.mainClock.advanceTimeBy(32)
        assertEquals(.8f,fill(),.0001f);assertFalse(running())
        compose.onNodeWithTag("hub-settings-toggle").performClick();compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(60.0))}
        compose.onNodeWithTag("hub-settings-toggle").performClick();compose.mainClock.advanceTimeBy(32)
        assertEquals(.6f,fill(),.0001f);assertFalse(running())
        compose.runOnIdle {prefs.value=prefs.value.copy(appearance=prefs.value.appearance.copy(reduceMotion="system"));system.value=0f}
        select("models");select("home");assertEquals(.6f,fill(),.0001f);assertFalse(running())
        compose.runOnIdle {prefs.value=prefs.value.copy(appearance=prefs.value.appearance.copy(reduceMotion="off"))}
        select("models");select("home");assertTrue(running());assertTrue(fill()<.6f)
        compose.runOnIdle {prefs.value=prefs.value.copy(appearance=prefs.value.appearance.copy(reduceMotion="on"))}
        compose.mainClock.advanceTimeBy(32);assertEquals(.6f,fill(),.0001f);assertFalse(running())
        compose.runOnIdle {prefs.value=prefs.value.copy(appearance=prefs.value.appearance.copy(reduceMotion="off"))}
        compose.mainClock.advanceTimeBy(32);assertEquals(.6f,fill(),.0001f);assertFalse(running())
        assertCompleteText();assertEquals(0,actions.reads);compose.mainClock.autoAdvance=true
    }
}
