package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** Production Sessions rows with deterministic time; these are not physical phone/FPS evidence. */
class SessionBreakdownMotionScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val now=Instant.parse("2026-10-11T08:00:00Z")
    private val clock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
    private val blue=Color(0xFF2060A0)
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
    private fun record(index:Int,value:Long,swap:Boolean=false,review:Boolean=false)=buildJsonObject {
        put("sessionId","public-session-$index");put("title","Public session $index");put("client","codex")
        put("totalTokens",value);put("costUsd",0.125);put("turnEnded",true)
        put("lastUsedAt",now.minusSeconds(if(swap && index==0) 120 else index*60L).toString())
        if(review) put("sessionKind","background-review")
        if(index==0 && !review) {put("contextTokens",75000);put("contextWindow",100000)}
    }
    private fun snapshot(value:Long=300,count:Int=2,swap:Boolean=false,review:Boolean=false)=HubParser.parse(buildJsonObject {
        put("periods",buildJsonObject {
            for((period,small) in listOf("today" to value,"month" to 100L)) put(period,buildJsonObject {
                put("totalTokens",if(review) 2000 else small+1000);put("costUsd",1.0)
                put("sessions",buildJsonObject {repeat(count) {index->
                    put("${if(review) "review" else "public"}:$index",record(index,if(review) value else if(index==0) small else 1000,swap,review))
                }})
            })
        })
    }.toString())
    private fun fallback(value:Long)=HubParser.parse("""{"periods":{"today":{"totalTokens":${value+1000},"clients":{"codex":$value,"claude":1000},"models":{"codex":$value,"claude":1000}}}}""")
    private fun source(snapshot:Snapshot)=HubState(snapshot=snapshot,savedAt=now.toEpochMilli(),history=HubHistory(emptyList(),emptyList()),
        modelNames=ModelNames(emptyMap(),"off"),subscriptions=emptyList(),pricing=HubPricing(true,0,null,emptyList()))
    private fun preferences(reduce:String="off")=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList(),
        appearance=AppearancePreferences(reduceMotion=reduce,vendorColors=mapOf("codex" to "#2060a0")))
    private fun setup(source:State<HubState>,prefs:State<DisplayPreferences>,actions:Actions) {
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(true,prefs.value.language,prefs.value.appearance) {
            CompositionLocalProvider(LocalDesktopAnimationClock provides clock,
                LocalHubCalendarClock provides Clock.fixed(now,ZoneId.systemDefault()),
                LocalHeadlineRenderObserver provides {part->commits.computeIfAbsent(part) {AtomicInteger()}.incrementAndGet()}) {
                HubScreen(source.value,actions,preferences=prefs.value)
            }
        }}}
        compose.waitForIdle()
    }
    private fun select(page:String) {
        compose.onNodeWithTag("hub-view-selector").performClick();compose.mainClock.advanceTimeBy(240)
        compose.onNodeWithTag("view-$page").performClick();compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
    }
    private fun number(id:String)=compose.onNodeWithTag("session-tokens-$id",true).fetchSemanticsNode()
        .config[SemanticsProperties.Text].single().text.replace(",","").toBigInteger()
    private fun layer(key:String)=compose.onNodeWithTag("breakdown-row-layer-$key",true).fetchSemanticsNode()
    private fun alpha(key:String)=layer(key).config[BreakdownRowAlpha]
    private fun offset(key:String)=layer(key).config[BreakdownRowTranslationY]
    private fun count(key:String)=commits[key]?.get() ?: 0
    private fun painted(id:String):Float {
        // Capture the actual production Canvas. No artificial semantics/draw is added for a test.
        val pixels=compose.onNodeWithTag("session-bar-$id",true).captureToImage().toPixelMap()
        val y=pixels.height/2
        val ink=(0 until pixels.width).count {x->
            val c=pixels[x,y]
            abs(c.red-blue.red)<.01f && abs(c.green-blue.green)<.01f && abs(c.blue-blue.blue)<.01f
        }
        return ink.toFloat()/pixels.width
    }
    private fun releaseSettingsExit() {
        val tag="hub-settings-panel-motion"
        compose.onNodeWithTag(tag,true)
            .assert(SemanticsMatcher.expectValue(DisclosureRetainsPixels,true))
            .assert(SemanticsMatcher.expectValue(DisclosureAcceptsInput,false))
            .assert(SemanticsMatcher.expectValue(DisclosureMotionPlaying,true))
        val pose=compose.onNodeWithTag(tag,true).fetchSemanticsNode().config
        assertTrue("The actual closing cache still has a positive public height",pose[DisclosureHeightFraction]>0f)
        assertTrue("The actual closing cache still has positive public opacity",pose[DisclosureOpacity]>0f)
        val began=compose.mainClock.currentTime
        val deadline=began+maxOf(DesktopDisclosureMotionTokens.panelHeightDurationMs,
            DesktopDisclosureMotionTokens.panelOpacityDurationMs)+32L
        // Wait for the production retained layer's release, within its own finite
        // deadline. This does not wait out/replay the independent420ms row entry.
        while(compose.onAllNodesWithTag(tag,true).fetchSemanticsNodes().isNotEmpty() &&
            compose.mainClock.currentTime<deadline) {
            compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
        }
        compose.onAllNodesWithTag(tag,true).assertCountEquals(0)
    }
    private fun completeText(id:String) {
        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("session-tokens-$id",true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        val result=layouts.single();assertFalse(result.didOverflowHeight)
        repeat(result.lineCount) {line->
            assertFalse(result.isLineEllipsized(line))
            assertTrue(result.getLineLeft(line)>=-.5f)
            assertTrue(result.getLineRight(line)<=result.size.width+.5f)
        }
    }

    @Test fun actualSessionsEntryLiveAndTabPaintAndCountWithNoOwnerOrFullRowFrameCommits() {
        val state=mutableStateOf(source(snapshot()));val prefs=mutableStateOf(preferences());val actions=Actions()
        setup(state,prefs,actions);compose.mainClock.autoAdvance=false;select("sessions")
        val key="session:public:0"
        val first=painted("public:0")
        assertTrue("The actual 420ms entry paints a partial .3 bar",first>0f && first<.3f)
        assertEquals(BigInteger.valueOf(300),number("public:0"))
        assertEquals("True view entry does not fade rows",1f,alpha(key),0f);assertEquals(0f,offset(key),0f)
        val hub=count("hub");val row=count("session-row:$key")
        compose.mainClock.advanceTimeBy(96)
        val next=painted("public:0");assertTrue(next>first && next<.3f)
        assertEquals(hub,count("hub"));assertEquals(row,count("session-row:$key"))
        compose.runOnIdle {state.value=state.value.copy(savedAt=now.toEpochMilli()+1000)}
        compose.mainClock.advanceTimeBy(32)
        assertTrue("Header freshness does not restart entry",painted("public:0")>first)
        compose.mainClock.advanceTimeBy(500);assertEquals(.3f,painted("public:0"),.025f)
        select("sessions");assertEquals("Current view does not replay",.3f,painted("public:0"),.025f)
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(500))};compose.mainClock.advanceTimeBy(32)
        assertTrue(number("public:0") in BigInteger.valueOf(301)..BigInteger.valueOf(499))
        val liveHub=count("hub");val liveRow=count("session-row:$key");val leaf=count("breakdown-number:$key")
        compose.mainClock.advanceTimeBy(250)
        assertTrue(number("public:0") in BigInteger.valueOf(450)..BigInteger.valueOf(499))
        assertTrue(painted("public:0")>.3f && painted("public:0")<.5f)
        assertEquals(liveHub,count("hub"));assertEquals(liveRow,count("session-row:$key"))
        assertTrue(count("breakdown-number:$key")>leaf)
        compose.mainClock.advanceTimeBy(400);assertEquals(BigInteger.valueOf(500),number("public:0"))
        compose.onNodeWithTag("hub-period-month").performClick();compose.mainClock.advanceTimeBy(32)
        assertTrue(number("public:0") in BigInteger.valueOf(101)..BigInteger.valueOf(499))
        compose.mainClock.advanceTimeBy(360)
        assertTrue("Actual tab uses the source 800ms quart number",number("public:0") in BigInteger.valueOf(120)..BigInteger.valueOf(160))
        compose.mainClock.advanceTimeBy(500);assertEquals(BigInteger.valueOf(100),number("public:0"));completeText("public:0")
        assertEquals(0,actions.reads);compose.mainClock.autoAdvance=true
    }

    @Test fun actualSessionMetadataReorderFlipsNativeBoundsAndNewRowFadesWithoutResettingDetailState() {
        val state=mutableStateOf(source(snapshot()));val prefs=mutableStateOf(preferences());val actions=Actions()
        setup(state,prefs,actions);compose.mainClock.autoAdvance=false;select("sessions");compose.mainClock.advanceTimeBy(500)
        val key="session:public:0";val before=layer(key).boundsInRoot.top
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(swap=true))};compose.mainClock.advanceTimeBy(32)
        val outer=compose.onNodeWithTag("breakdown-row-layout-$key",true).fetchSemanticsNode().boundsInRoot
        val initial=offset(key)
        assertTrue("Timestamp ordering moves the unchanged-number row down",outer.top>before && initial<0f)
        assertEquals(initial,layer(key).boundsInRoot.top-outer.top,1f)
        assertEquals(BigInteger.valueOf(300),number("public:0"))
        val hub=count("hub");val row=count("session-row:$key")
        compose.mainClock.advanceTimeBy(96);assertTrue(offset(key)>initial && offset(key)<0f)
        assertEquals(hub,count("hub"));assertEquals(row,count("session-row:$key"))
        compose.mainClock.advanceTimeBy(240);assertEquals(0f,offset(key),0f)
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(count=3,swap=true))};compose.mainClock.advanceTimeBy(32)
        val added="session:public:2";val a=alpha(added);val y=offset(added)
        assertTrue(a>0f && a<1f);assertTrue(y>0f)
        compose.mainClock.advanceTimeBy(96);assertTrue(alpha(added)>a);assertTrue(offset(added)<y)
        compose.mainClock.advanceTimeBy(620);assertEquals(1f,alpha(added),0f);assertEquals(0f,offset(added),0f)
        compose.onNodeWithTag("session-row-public:0").performClick()
        // Manual time must deliver input/recomposition and the native Dialog's
        // first layout. The row's preceding finite animations are already ended.
        compose.mainClock.advanceTimeBy(32)
        compose.waitUntil(timeoutMillis=2_000) {compose.onNodeWithTag("session-summary-details").isDisplayed()}
        compose.onNodeWithTag("session-summary-details").assertIsDisplayed()
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(500,count=3,swap=true))};compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("session-summary-details").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick();compose.mainClock.advanceTimeBy(620)
        assertEquals(BigInteger.valueOf(500),number("public:0"));assertEquals(0,actions.reads)
        compose.mainClock.autoAdvance=true
    }

    @Test fun groupedReviewKeepsExactAggregateAndOpenRunIdentityWhileItsPrimaryNumberAnimates() {
        val start=Long.MAX_VALUE-1000
        val state=mutableStateOf(source(snapshot(start,review=true)));val prefs=mutableStateOf(preferences());val actions=Actions()
        setup(state,prefs,actions);compose.mainClock.autoAdvance=false;select("sessions");compose.mainClock.advanceTimeBy(500)
        val id="session-group:codex-auto-review";val initial=BigInteger.valueOf(start)*BigInteger.TWO
        assertEquals(initial,number(id));completeText(id)
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(Long.MAX_VALUE,review=true))};compose.mainClock.advanceTimeBy(32)
        val exact=BigInteger.valueOf(Long.MAX_VALUE)*BigInteger.TWO
        assertTrue(number(id)>initial && number(id)<exact)
        compose.mainClock.advanceTimeBy(620);assertEquals(exact,number(id));completeText(id)
        compose.onNodeWithTag("session-row-$id").performClick();compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("session-return-list").assertIsDisplayed();compose.onNodeWithTag("session-pager").assertDoesNotExist()
        compose.onNodeWithTag("session-row-review:0").assertIsDisplayed()
        layer("session:review:0");layer("session:review:1")
        compose.mainClock.advanceTimeBy(800)
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE),number("review:0"))
        compose.onNodeWithTag("session-return-list").performClick();compose.mainClock.advanceTimeBy(800)
        assertEquals(exact,number(id));assertEquals(0,actions.reads);compose.mainClock.autoAdvance=true
    }

    @Test fun actualHundredRowPagesPreservePagerAndSkipLargeLiveNumbersAndRowTransforms() {
        val state=mutableStateOf(source(snapshot(count=201)));val prefs=mutableStateOf(preferences());val actions=Actions()
        setup(state,prefs,actions);compose.mainClock.autoAdvance=false;select("sessions")
        assertTrue("Large true entry still paints its finite shared bar",painted("public:0")<.3f)
        assertEquals(1f,alpha("session:public:0"),0f);compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(500,count=201))};compose.mainClock.advanceTimeBy(32)
        assertEquals(BigInteger.valueOf(500),number("public:0"));assertEquals(.5f,painted("public:0"),.025f)
        assertEquals(1f,alpha("session:public:0"),0f);assertEquals(0f,offset("session:public:0"),0f)
        // Scroll a real late row after entry has settled. The unchanged pager is
        // in the bottom bar; clicking it must restore the next page's first row.
        compose.mainClock.autoAdvance=true
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("session-row-public:50"))
        assertEquals(1f,alpha("session:public:50"),0f);assertEquals(0f,offset("session:public:50"),0f)
        compose.onNodeWithTag("session-page-status").assertTextEquals("1–100 of 201")
        compose.onNodeWithTag("session-page-next").performClick()
        compose.onNodeWithTag("session-page-status").assertTextEquals("101–200 of 201")
        compose.onNodeWithTag("session-row-public:100").assertIsDisplayed()
        assertEquals(1f,alpha("session:public:100"),0f);assertEquals(0f,offset("session:public:100"),0f)
        assertEquals(0,actions.reads)
    }

    @Test fun stopReductionSettingsAndFallbackKeepLatestTargetWithoutAnotherEntry() {
        val state=mutableStateOf(source(snapshot()));val prefs=mutableStateOf(preferences());val actions=Actions()
        setup(state,prefs,actions);compose.mainClock.autoAdvance=false;select("sessions");compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(500))};compose.mainClock.advanceTimeBy(32)
        assertTrue(number("public:0")<BigInteger.valueOf(500))
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);compose.mainClock.advanceTimeBy(32)
        assertEquals(BigInteger.valueOf(500),number("public:0"));assertEquals(.5f,painted("public:0"),.025f)
        compose.runOnIdle {prefs.value=preferences("on");state.value=state.value.copy(snapshot=snapshot(700))}
        compose.mainClock.advanceTimeBy(32);assertEquals(BigInteger.valueOf(700),number("public:0"))
        compose.runOnIdle {prefs.value=preferences("off");state.value=state.value.copy(snapshot=snapshot(900))}
        compose.mainClock.advanceTimeBy(32);assertTrue(number("public:0")<BigInteger.valueOf(900))
        compose.onNodeWithTag("hub-settings-toggle").performClick();compose.mainClock.advanceTimeBy(300)
        val row=count("session-row:session:public:0")
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(950))};compose.mainClock.advanceTimeBy(32)
        assertEquals(row,count("session-row:session:public:0"))
        compose.onNodeWithTag("hub-settings-toggle").performClick();compose.mainClock.advanceTimeBy(32)
        assertEquals(BigInteger.valueOf(950),number("public:0"))
        assertEquals(1f,alpha("session:public:0"),0f);assertEquals(0f,offset("session:public:0"),0f)
        // The closing Settings cache still composites over the ready main Canvas
        // at32ms. Read the same original pixels only after that exit layer releases.
        releaseSettingsExit()
        assertEquals(.95f,painted("public:0"),.025f)
        compose.runOnIdle {state.value=state.value.copy(snapshot=fallback(400))};compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("breakdown-tokens-model-codex",true).assertTextEquals("400")
        layer("session-fallback:true:codex")
        compose.runOnIdle {state.value=state.value.copy(snapshot=fallback(600))};compose.mainClock.advanceTimeBy(32)
        val shown=compose.onNodeWithTag("breakdown-tokens-model-codex",true).fetchSemanticsNode().config[SemanticsProperties.Text].single().text.toInt()
        assertTrue("The unchanged fallback identity uses the same 600ms path",shown in 401..599)
        compose.mainClock.advanceTimeBy(620);compose.onNodeWithTag("breakdown-tokens-model-codex",true).assertTextEquals("600")
        assertEquals(0,actions.reads);compose.mainClock.autoAdvance=true
    }

    @Test fun actualFixedPeriodPlaceholderConsumesViewEntryBeforeTodayTabReturnsRows() {
        val state=mutableStateOf(source(snapshot()).copy(deviceHistories=DeviceHistories("public-fixture",emptyList())))
        val prefs=mutableStateOf(preferences().copy(lastPeriod="last7",monthMode="last7"));val actions=Actions()
        setup(state,prefs,actions);compose.mainClock.autoAdvance=false;select("sessions")
        compose.onNodeWithTag("session-row-public:0").assertDoesNotExist()
        compose.onNodeWithTag("hub-period-today").performClick();compose.mainClock.advanceTimeBy(32)
        assertTrue("A displayed no-details view consumed entry; the real tab now counts the new row",
            number("public:0")>=BigInteger.ZERO && number("public:0")<BigInteger.valueOf(300))
        compose.mainClock.advanceTimeBy(820);assertEquals(BigInteger.valueOf(300),number("public:0"))
        assertEquals(.3f,painted("public:0"),.025f)
        assertEquals(0,actions.reads);compose.mainClock.autoAdvance=true
    }
}
