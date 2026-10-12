package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** Production Hub routes and production row bars; virtual time is not a device FPS claim. */
class BreakdownMotionScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val animationClock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
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
    private fun snapshot(value:Long=300,month:Long=100)=HubParser.parse("""{
      "periods":{
        "today":{"totalTokens":${value+1000},"clients":{"codex":$value,"claude":1000},"models":{"model-small":$value,"model-big":1000},
          "projects":{"small":{"label":"Small","tokens":$value,"costUsd":1,"clients":{"codex":$value}},"big":{"label":"Big","tokens":1000,"costUsd":1,"clients":{"codex":1000}}}},
        "month":{"totalTokens":${month+1000},"clients":{"codex":$month,"claude":1000},"models":{"model-small":$month,"model-big":1000}}},
      "devices":[{"deviceId":"small","platform":"linux","periods":{"today":{"totalTokens":$value},"month":{"totalTokens":$month}}},
        {"deviceId":"big","platform":"linux","periods":{"today":{"totalTokens":1000},"month":{"totalTokens":1000}}}]
    }""")
    private fun state(value:Long=300)=HubState(snapshot=snapshot(value),history=HubHistory(emptyList(),emptyList()),
        modelNames=ModelNames(emptyMap(),"off"),subscriptions=emptyList(),pricing=HubPricing(true,0,null,emptyList()))
    private fun setup(source:State<HubState>,prefs:State<DisplayPreferences>,actions:Actions,system:State<Float> = mutableStateOf(1f)) {
        compose.activity.runOnUiThread {compose.activity.setContent {
            TokenTheme(true,prefs.value.language,prefs.value.appearance) {
                CompositionLocalProvider(LocalDesktopAnimationClock provides animationClock,
                    LocalDesktopSystemMotionScale provides system.value,
                    LocalHubCalendarClock provides Clock.fixed(Instant.parse("2026-10-11T08:00:00Z"),ZoneId.of("Asia/Shanghai")),
                    LocalHeadlineRenderObserver provides {part->commits.computeIfAbsent(part) {AtomicInteger()}.incrementAndGet()}) {
                    HubScreen(source.value,actions,preferences=prefs.value)
                }
            }
        }}
        compose.waitForIdle()
    }
    private fun select(id:String) {
        compose.onNodeWithTag("hub-view-selector").performClick()
        compose.mainClock.advanceTimeBy(240)
        compose.onNodeWithTag("view-$id").performClick()
        compose.mainClock.advanceTimeBy(32)
    }
    private fun progress(tag:String)=compose.onNodeWithTag(tag,useUnmergedTree=true).fetchSemanticsNode()
        .config[SemanticsProperties.ProgressBarRangeInfo].current
    private fun number(tag:String)=compose.onNodeWithTag(tag,useUnmergedTree=true).fetchSemanticsNode()
        .config[SemanticsProperties.Text].single().text.replace(",","").toLong()
    private fun count(part:String)=commits[part]?.get() ?: 0
    private fun paintedRatio(tag:String):Float {
        val pixels=compose.onNodeWithTag(tag,useUnmergedTree=true).captureToImage().toPixelMap()
        val y=pixels.height/2
        val ink=(0 until pixels.width).count {x->
            val c=pixels[x,y]
            abs(c.red-blue.red)<.01f && abs(c.green-blue.green)<.01f && abs(c.blue-blue.blue)<.01f
        }
        return ink.toFloat()/pixels.width
    }

    @Test fun actualToolsEntryPaints420msBarsAndCurrentViewDoesNotReplay() {
        val source=mutableStateOf(state());val actions=Actions()
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList(),appearance=AppearancePreferences(reduceMotion="off",vendorColors=mapOf("codex" to "#2060a0"))))
        setup(source,prefs,actions);compose.mainClock.autoAdvance=false
        select("tools")
        val tag="breakdown-color-tool-codex"
        val first=progress(tag)
        assertTrue("A true view entry starts below its final .3 target",first>0f && first<.3f)
        assertEquals("Entry animates bars while keeping real row numbers",300L,number("breakdown-tokens-tool-codex"))
        assertEquals(first,paintedRatio(tag),.035f)
        val hub=count("hub");val row=count("breakdown-row:TOOLS:codex")
        compose.mainClock.advanceTimeBy(128)
        assertTrue(progress(tag)>first)
        assertEquals(progress(tag),paintedRatio(tag),.035f)
        assertEquals("Entry frames do not recompose the Hub owner",hub,count("hub"))
        assertEquals("Entry frames do not recompose the full row",row,count("breakdown-row:TOOLS:codex"))
        compose.runOnIdle {source.value=source.value.copy(savedAt=1)}
        compose.mainClock.advanceTimeBy(32)
        assertTrue("An ordinary stats freshness commit does not reset the bar",progress(tag)>first)
        compose.mainClock.advanceTimeBy(500)
        assertEquals(.3f,progress(tag),.0001f)
        select("tools")
        assertEquals("Clicking the current view does not replay its bar",.3f,progress(tag),.0001f)
        assertEquals(0,actions.reads)
        compose.mainClock.autoAdvance=true
    }

    @Test fun actualLiveAndPeriodTabsUseLocalNumbersAndCurrentMenuOpenPreservesTheTimeline() {
        val source=mutableStateOf(state());val actions=Actions()
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList(),appearance=AppearancePreferences(reduceMotion="off",vendorColors=mapOf("codex" to "#2060a0"))))
        setup(source,prefs,actions);compose.mainClock.autoAdvance=false;select("tools");compose.mainClock.advanceTimeBy(500)
        val tag="breakdown-tokens-tool-codex"
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(500))}
        compose.mainClock.advanceTimeBy(32)
        assertTrue(number(tag) in 301..499)
        val hub=count("hub");val row=count("breakdown-row:TOOLS:codex");val leaf=count("breakdown-number:TOOLS:codex")
        compose.mainClock.advanceTimeBy(250)
        val mid=number(tag);assertTrue(mid in 450..499)
        assertEquals(hub,count("hub"));assertEquals(row,count("breakdown-row:TOOLS:codex"))
        assertTrue("Only the small real Text component counts its new values",count("breakdown-number:TOOLS:codex")>leaf)
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(700))}
        compose.mainClock.advanceTimeBy(32)
        assertTrue(number(tag)>=mid && number(tag)<700)
        compose.mainClock.advanceTimeBy(620);assertEquals(700L,number(tag))
        compose.onNodeWithTag("hub-period-month").performClick();compose.mainClock.advanceTimeBy(32)
        assertTrue(number(tag) in 101..699)
        compose.mainClock.advanceTimeBy(360)
        val periodMid=number(tag);assertTrue("The 800ms tab number uses quart easing",periodMid in 120..160)
        compose.onNodeWithTag("hub-period-month").performClick();compose.mainClock.advanceTimeBy(32)
        assertTrue("Opening the active month dropdown is not another period event",number(tag)<=periodMid)
        compose.mainClock.advanceTimeBy(500);assertEquals(100L,number(tag))
        assertEquals(0,actions.reads)
        compose.mainClock.autoAdvance=true
    }

    @Test fun productionModelProjectAndDeviceRowsShareEntryButKeepActualNumbersAndBounds() {
        val source=mutableStateOf(state());val actions=Actions()
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList(),appearance=AppearancePreferences(reduceMotion="off",vendorColors=mapOf("codex" to "#2060a0"))))
        setup(source,prefs,actions);compose.mainClock.autoAdvance=false
        val rows=listOf(Triple("models","breakdown-color-model-model-small","breakdown-tokens-model-model-small"),
            Triple("projects","project-bar-small","project-tokens-small"),Triple("devices","device-bar-small","device-tokens-small"))
        for((page,bar,value) in rows) {
            select(page)
            val initial=progress(bar);assertTrue("$page actual bar enters",initial>0f && initial<.3f)
            assertEquals(300L,number(value))
            val bounds=compose.onNodeWithTag(value,true).fetchSemanticsNode().boundsInRoot
            assertTrue("$page number has real visible bounds",bounds.width>0f && bounds.height>0f)
            val results=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithTag(value,true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(results)}
            assertEquals(1,results.size)
            val actual=results.single()
            // A reused paragraph may keep the available 924px width while Text
            // paints a 60px native wrap-content result. Check actual line glyph
            // extents, height and ellipsis rather than that cached width flag.
            assertFalse("$page complete native number height",actual.didOverflowHeight)
            repeat(actual.lineCount) {line->
                assertFalse("$page complete number line $line",actual.isLineEllipsized(line))
                assertTrue("$page number begins inside its painted box",actual.getLineLeft(line)>=-.5f)
                assertTrue("$page number ends inside its painted box",actual.getLineRight(line)<=actual.size.width+.5f)
            }
            assertEquals("$page text measurement and visible bounds agree",actual.size.width.toFloat(),bounds.width,1f)
            compose.mainClock.advanceTimeBy(450);assertEquals(.3f,progress(bar),.0001f)
        }
        assertEquals(0,actions.reads);compose.mainClock.autoAdvance=true
    }

    @Test fun reducedThreeStatesAndStopOrSettingsReturnSettleTheLatestVisibleValue() {
        val source=mutableStateOf(state());val actions=Actions();val system=mutableStateOf(0f)
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList(),appearance=AppearancePreferences(reduceMotion="system")))
        setup(source,prefs,actions,system);compose.mainClock.autoAdvance=false;select("tools")
        val value="breakdown-tokens-tool-codex";val bar="breakdown-color-tool-codex"
        assertEquals(300L,number(value));assertEquals(.3f,progress(bar),.0001f)
        compose.runOnIdle {prefs.value=prefs.value.copy(appearance=prefs.value.appearance.copy(reduceMotion="on"));system.value=1f;source.value=source.value.copy(snapshot=snapshot(500))}
        compose.mainClock.advanceTimeBy(32);assertEquals(500L,number(value));assertEquals(.5f,progress(bar),.0001f)
        compose.runOnIdle {prefs.value=prefs.value.copy(appearance=prefs.value.appearance.copy(reduceMotion="off"));system.value=0f;source.value=source.value.copy(snapshot=snapshot(800))}
        compose.mainClock.advanceTimeBy(32);assertTrue(number(value) in 501..799)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.mainClock.advanceTimeBy(300)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.mainClock.advanceTimeBy(32)
        assertEquals(800L,number(value));assertEquals(.8f,progress(bar),.0001f)
        compose.mainClock.advanceTimeBy(128);assertEquals(800L,number(value))
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(900))}
        compose.mainClock.advanceTimeBy(32);assertTrue(number(value) in 801..899)
        compose.onNodeWithTag("hub-settings-toggle").performClick();compose.mainClock.advanceTimeBy(300)
        val row=count("breakdown-row:TOOLS:codex")
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(950))}
        compose.mainClock.advanceTimeBy(32)
        assertEquals("The hidden main view does not recompose its full rows",row,count("breakdown-row:TOOLS:codex"))
        compose.onNodeWithTag("hub-settings-toggle").performClick();compose.mainClock.advanceTimeBy(32)
        assertEquals("Returning to the same main view shows the latest prepared target",950L,number(value))
        compose.mainClock.advanceTimeBy(450);assertEquals(950L,number(value))
        assertEquals(0,actions.reads);compose.mainClock.autoAdvance=true
    }

    @Test fun actualProjectRowKeepsALargeExactIntegerCompleteInNarrowLargeTextLayout() {
        val exact=BigInteger("999999999999999999999999999999999999999999999999999999999999999999")
        val row=ProjectRow("exact","Exact",exact,"0",BigInteger.ZERO,emptyList())
        compose.activity.runOnUiThread {compose.activity.setContent {
            TokenTheme(true,LanguageChoice.ENGLISH,AppearancePreferences(textSize="largest",reduceMotion="on")) {
                val density=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                    Box(Modifier.width(180.dp)) {DesktopProjectRow(row,exact,false)}
                }
            }
        }}
        val tag="project-tokens-exact"
        val text=compose.onNodeWithTag(tag,true)
        text.assertTextEquals(NumberFormat.getIntegerInstance(Locale.US).format(exact))
        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertEquals(1,layouts.size)
        val result=layouts.single()
        assertTrue("The full integer uses natural lines",result.lineCount>1)
        assertFalse("Native Text must paint the exact integer without overflow",result.hasVisualOverflow)
        repeat(result.lineCount) {assertFalse(result.isLineEllipsized(it))}
        val bounds=text.fetchSemanticsNode().boundsInRoot
        val outer=compose.onNodeWithTag("project-row-exact").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.left>=outer.left-.5f && bounds.right<=outer.right+.5f)
        assertTrue(bounds.top>=outer.top-.5f && bounds.bottom<=outer.bottom+.5f)
    }
}
