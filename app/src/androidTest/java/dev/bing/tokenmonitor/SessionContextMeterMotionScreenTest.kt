package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** Actual Session row/Canvas with virtual time; injected scale is not an Android-system proof. */
class SessionContextMeterMotionScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val now=Instant.parse("2026-10-11T08:00:00Z")
    private val clock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
    private val commits=ConcurrentHashMap<String,AtomicInteger>()
    private fun row(id:String="public",used:Int?=75,index:Int=0)=UsageRow(id,"Public session $index",1000,"0","codex",
        now.minusSeconds(index.toLong()).toString(),true,session=SessionMetadata(title="Public session $index",sessionId="public-$index",
            contextTokens=used?.let {it*1000.0},contextWindow=100000.0))
    private fun preferences(reduction:String="off",metric:String="used")=DisplayPreferences(language=LanguageChoice.ENGLISH,
        appearance=AppearancePreferences(reduceMotion=reduction),main=MainDisplayPreferences(sessionContextMetric=metric))
    private fun count(key:String)=commits[key]?.get() ?: 0
    private fun setup(record:State<UsageRow>,prefs:State<DisplayPreferences>,system:State<Float> = mutableStateOf(1f)) {
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(true,prefs.value.language,prefs.value.appearance) {
            CompositionLocalProvider(LocalDesktopAnimationClock provides clock,LocalDesktopSystemMotionScale provides system.value,
                LocalMainDisplay provides prefs.value.main,
                LocalHeadlineRenderObserver provides {part->commits.computeIfAbsent(part) {AtomicInteger()}.incrementAndGet()}) {
                val display=SessionListDisplay.raw(listOf(record.value),now.toEpochMilli()).single()
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    key(display.id) {DesktopSessionRow(display,display.tokens,now.toEpochMilli(),reduceMotion=prefs.value.appearance.reduceMotion) {}}
                }
            }
        }}}
        compose.waitForIdle()
    }
    private fun ink(id:String="public"):Float {
        val pixels=compose.onNodeWithTag("session-context-meter-$id",true).captureToImage().toPixelMap()
        val y=pixels.height/2
        // All fixture targets are <=95%; this stable point is inside the track,
        // beyond the fill and away from the rounded outer edge.
        val background=pixels[(pixels.width*.975f).toInt().coerceIn(0,pixels.width-1),y]
        val different=(0 until pixels.width).count {x->
            val c=pixels[x,y]
            abs(c.red-background.red)+abs(c.green-background.green)+abs(c.blue-background.blue)>.10f
        }
        return different.toFloat()/pixels.width
    }

    @Test fun actualInitialGaugeIsStaticAndOnlyRetainedPercentOrMetricScaleChangesTransition() {
        val record=mutableStateOf(row());val prefs=mutableStateOf(preferences())
        setup(record,prefs);compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("session-context-public").assertTextEquals("75%")
        assertEquals("A new DOM gauge has no zero-to-target transition",.75f,ink(),.06f)
        compose.runOnIdle {record.value=row(used=90)};compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("session-context-public").assertTextEquals("90%")
        val initial=ink();assertTrue(initial>.75f && initial<.9f)
        val fullRow=count("session-row:session:public");val leaf=count("session-context-meter:public")
        compose.mainClock.advanceTimeBy(64)
        assertTrue(ink()>initial)
        assertEquals("Canvas frames do not recompose the entire Session row",fullRow,count("session-row:session:public"))
        assertEquals("The gauge's own composition does not read frame state",leaf,count("session-context-meter:public"))
        compose.mainClock.advanceTimeBy(450);assertEquals(.9f,ink(),.06f)
        // Same percentage, unrelated record metadata: do not create another transition.
        compose.runOnIdle {record.value=record.value.copy(cost="1")};compose.mainClock.advanceTimeBy(32)
        assertEquals(.9f,ink(),.06f)
        compose.runOnIdle {prefs.value=prefs.value.copy(main=prefs.value.main.copy(sessionContextMetric="remaining"))}
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("session-context-public").assertTextEquals("10%")
        val metricMiddle=ink();assertTrue("The existing CSS transform transitions but text is immediate",metricMiddle>.1f && metricMiddle<.9f)
        compose.mainClock.advanceTimeBy(450);assertEquals(.1f,ink(),.06f)
        // Used and remaining are both50: writing the same transform is static.
        compose.runOnIdle {record.value=row(used=50)};compose.mainClock.advanceTimeBy(450)
        compose.runOnIdle {prefs.value=prefs.value.copy(main=prefs.value.main.copy(sessionContextMetric="used"))}
        compose.mainClock.advanceTimeBy(32);assertEquals(.5f,ink(),.06f)
        compose.mainClock.autoAdvance=true
    }

    @Test fun missingContextAndARealNewSessionShowTheirOwnTargetImmediatelyAndKeepContextClick() {
        val record=mutableStateOf(row());val prefs=mutableStateOf(preferences())
        setup(record,prefs);compose.mainClock.autoAdvance=false
        compose.runOnIdle {record.value=row(used=null)};compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("session-context-meter-public",true).assertDoesNotExist()
        compose.runOnIdle {record.value=row(used=60)};compose.mainClock.advanceTimeBy(32)
        assertEquals("A display:none gauge returning has no rendered old scale",.6f,ink(),.06f)
        compose.onNodeWithTag("session-context-public").assertTextEquals("60%")
        compose.onNodeWithTag("session-context-public").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.waitUntil(timeoutMillis=2_000) {compose.onNodeWithTag("session-context-details").isDisplayed()}
        compose.onNodeWithTag("session-context-details").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.runOnIdle {record.value=row(id="public-new",used=25)};compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("session-context-meter-public",true).assertDoesNotExist()
        assertEquals(.25f,ink("public-new"),.06f)
        compose.mainClock.autoAdvance=true
    }

    @Test fun injectedThreeStatesAndActualStopSettleTheLatestScaleWithoutResumePlayback() {
        val record=mutableStateOf(row());val prefs=mutableStateOf(preferences("system"));val system=mutableStateOf(0f)
        setup(record,prefs,system);compose.mainClock.autoAdvance=false
        compose.runOnIdle {record.value=row(used=50)};compose.mainClock.advanceTimeBy(32)
        assertEquals(.5f,ink(),.06f)
        compose.runOnIdle {prefs.value=preferences("on");system.value=1f;record.value=row(used=80)}
        compose.mainClock.advanceTimeBy(32);assertEquals(.8f,ink(),.06f)
        compose.runOnIdle {prefs.value=preferences("off");system.value=0f;record.value=row(used=40)}
        compose.mainClock.advanceTimeBy(32);assertTrue(ink()>.4f && ink()<.8f)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);compose.mainClock.advanceTimeBy(32)
        assertEquals(.4f,ink(),.06f)
        compose.mainClock.advanceTimeBy(96);assertEquals(.4f,ink(),.06f)
        // Resume must acknowledge the visible boundary before the next payload;
        // that first later target is a retained gauge change, not resume playback.
        compose.runOnIdle {record.value=row(used=65)};compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("session-context-public").assertTextEquals("65%")
        val resumedMiddle=ink();assertTrue("The first real change after resume must transition",resumedMiddle>.4f && resumedMiddle<.65f)
        compose.mainClock.advanceTimeBy(64);assertTrue(ink()>resumedMiddle)
        compose.mainClock.advanceTimeBy(450);assertEquals(.65f,ink(),.06f)
        compose.mainClock.autoAdvance=true
    }

    @Test fun realLazyUnmountAndLateReturnNeverReplayTheOffscreenGauge() {
        val used=mutableStateOf(75);var jump:(Int)->Unit={}
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(true,LanguageChoice.ENGLISH,AppearancePreferences(reduceMotion="off")) {
            CompositionLocalProvider(LocalDesktopAnimationClock provides clock,
                LocalHeadlineRenderObserver provides {part->commits.computeIfAbsent(part) {AtomicInteger()}.incrementAndGet()}) {
                val state=rememberLazyListState();val scope=rememberCoroutineScope()
                SideEffect {jump={index->scope.launch {state.scrollToItem(index)}}}
                val rows=SessionListDisplay.raw((0 until 20).map {row("public:$it",used.value,it)},now.toEpochMilli())
                LazyColumn(Modifier.height(150.dp).padding(horizontal=16.dp),state=state) {
                    items(rows,key={it.id}) {display->DesktopSessionRow(display,display.tokens,now.toEpochMilli(),reduceMotion="off") {}}
                }
            }
        }}}
        compose.waitForIdle();compose.mainClock.autoAdvance=false
        assertEquals(.75f,ink("public:0"),.06f)
        compose.runOnIdle {used.value=95};compose.mainClock.advanceTimeBy(32)
        assertTrue(ink("public:0")>.75f && ink("public:0")<.95f)
        // scrollToItem is an immediate real native layout jump, not an animated
        // scroll that would silently wait out the420ms effect under test.
        compose.runOnIdle {jump(10)};compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("session-context-meter-public:0",true).assertDoesNotExist()
        assertEquals(.95f,ink("public:10"),.06f)
        compose.runOnIdle {used.value=70};compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle {jump(0)};compose.mainClock.advanceTimeBy(32)
        assertEquals("A real remounted row initializes at the newest reading",.7f,ink("public:0"),.06f)
        compose.mainClock.autoAdvance=true
    }
}
