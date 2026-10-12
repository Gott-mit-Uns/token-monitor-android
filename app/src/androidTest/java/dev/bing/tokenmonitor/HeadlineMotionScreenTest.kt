package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HeadlineMotionScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val animationClock = DesktopAnimationClock { compose.mainClock.currentTime * 1_000_000L }
    private class Actions: HubUiActions {
        var reads = 0
        override fun connect(address:String,credential:String,alternate:String) {}
        override fun refreshSaved() { reads++ }
        override fun switchEndpoint() {}
        override fun hasAlternate() = false
        override fun endpointType() = "https"
        override fun loadHistory() { reads++ }
        override fun loadModelNames() { reads++ }
        override fun loadSubscriptions() { reads++ }
        override fun forget() {}
        override fun renameDevice(id:String,input:String) {}
    }
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private fun snapshot(tokens:Long) = HubParser.parse("""{"periods":{"today":{"totalTokens":$tokens},"month":{"totalTokens":0}}}""")
    private fun shown():Long = compose.onNodeWithTag("hub-total-tokens").fetchSemanticsNode().config[SemanticsProperties.Text].single().text.replace(",","").toLong()
    private fun save(name:String) = saveVisualEvidence(compose.activity,compose.onRoot(),"headline-$name-${if(dark) "dark" else "light"}.png")

    @Test fun homeCountChangesInterruptAndPeriodChangeUsesDesktopTiming() {
        val state=mutableStateOf(HubState(snapshot=snapshot(999999999),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off")))
        val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=listOf("devices"),main=MainDisplayPreferences(compactTotal=true))
        val actions=Actions()
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.language,prefs.appearance) {CompositionLocalProvider(LocalDesktopAnimationClock provides animationClock,LocalHeadlineMotionScale provides 1f) {HubScreen(state.value,actions,preferences=prefs)}}}}
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("999,999,999")
        compose.mainClock.autoAdvance=false
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(2000000000))}
        compose.mainClock.advanceTimeBy(32)
        assertTrue(shown() in 1000000000L..1999999999L)
        save("start")
        val height=compose.onNodeWithTag("hub-total-tokens").fetchSemanticsNode().boundsInRoot.height
        compose.mainClock.advanceTimeBy(250)
        val middle=shown();assertTrue(middle in 1600000000L..1800000000L)
        compose.onNodeWithTag("hub-total-compact").assertTextEquals("≈ 2B")
        assertEquals(height,compose.onNodeWithTag("hub-total-tokens").fetchSemanticsNode().boundsInRoot.height,0.01f)
        save("middle")
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(3000000000))}
        compose.mainClock.advanceTimeBy(32)
        assertTrue(shown() >= middle && shown()<2000000000L)
        compose.mainClock.advanceTimeBy(1100)
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("3,000,000,000")
        save("end")
        compose.onNodeWithTag("hub-period-month").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.mainClock.advanceTimeBy(400)
        assertTrue(shown() in 100000000L..250000000L)
        compose.mainClock.advanceTimeBy(450)
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("0")
        compose.onNodeWithTag("hub-total-compact").assertDoesNotExist()
        assertEquals(0,actions.reads)
        compose.mainClock.autoAdvance=true
    }

    @Test fun androidReducedMotionSettlesMidAnimationAndExactCountsNeverLoseLastDigit() {
        val state=mutableStateOf(HubState(snapshot=snapshot(1000),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off")))
        val scale=mutableStateOf(1f)
        val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList())
        val actions=Actions()
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.language,prefs.appearance) {CompositionLocalProvider(LocalDesktopAnimationClock provides animationClock,LocalHeadlineMotionScale provides scale.value) {HubScreen(state.value,actions,preferences=prefs)}}}}
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("1,000")
        compose.mainClock.autoAdvance=false
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(2000))}
        compose.mainClock.advanceTimeBy(32)
        assertTrue(shown() in 1001..1999)
        compose.mainClock.advanceTimeBy(250)
        assertTrue(shown() in 1500..1999)
        compose.runOnIdle {scale.value=0f}
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("2,000")
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(Long.MAX_VALUE))}
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("9,223,372,036,854,775,807")
        save("reduced")
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("9,223,372,036,854,775,807")
        assertEquals(0,actions.reads)
        compose.mainClock.autoAdvance=true
    }

    @Test fun publicRenderedFramesAndBackgroundReturnSettleWithoutReplaying() {
        val state=mutableStateOf(HubState(snapshot=snapshot(999999999),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off")))
        val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=listOf("devices"),main=MainDisplayPreferences(compactTotal=true))
        val actions=Actions()
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.language,prefs.appearance) {CompositionLocalProvider(LocalDesktopAnimationClock provides animationClock,LocalHeadlineMotionScale provides 1f) {HubScreen(state.value,actions,preferences=prefs)}}}}
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("999,999,999")
        compose.mainClock.autoAdvance=false
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(2000000000))}
        compose.mainClock.advanceTimeBy(32)
        assertTrue(shown() in 1000000000L..1999999999L)
        val capture=InstrumentationRegistry.getArguments().getString("recordHeadlineFrames")=="true"
        if(capture) save("frame-00")
        var previous=shown()
        val frames=if(capture) 32 else 4
        repeat(frames) {index ->
            compose.mainClock.advanceTimeBy(if(capture) 32 else 64)
            val current=shown();assertTrue(current>=previous);previous=current
            if(capture) save("frame-${(index+1).toString().padStart(2,'0')}")
        }
        compose.mainClock.advanceTimeBy(1100)
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("2,000,000,000")
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(4000000000))}
        compose.mainClock.advanceTimeBy(32);shown()
        compose.mainClock.advanceTimeBy(250)
        assertTrue(shown() in 3200000000L..3600000000L)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.mainClock.advanceTimeBy(400)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("4,000,000,000")
        assertEquals(0,actions.reads)
        compose.mainClock.autoAdvance=true
    }

    @Test fun actualAndroidAnimationScaleIsRespectedWithoutAnOverride() {
        val state=mutableStateOf(HubState(snapshot=snapshot(1000),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off")))
        val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList())
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.language,prefs.appearance) {CompositionLocalProvider(LocalDesktopAnimationClock provides animationClock) {HubScreen(state.value,Actions(),preferences=prefs)}}}}
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("1,000")
        compose.mainClock.autoAdvance=false
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(2000))}
        compose.mainClock.advanceTimeBy(32)
        val scale=android.provider.Settings.Global.getFloat(compose.activity.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)
        if(scale<=0f) compose.onNodeWithTag("hub-total-tokens").assertTextEquals("2,000")
        else { assertTrue(shown() in 1001..1999);compose.mainClock.advanceTimeBy(250);assertTrue(shown() in 1001..1999) }
        compose.mainClock.advanceTimeBy((1100*scale.coerceAtLeast(1f)).toLong())
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("2,000")
        compose.mainClock.autoAdvance=true
    }
}
