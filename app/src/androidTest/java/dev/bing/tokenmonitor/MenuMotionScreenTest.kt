package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MenuMotionScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val animationClock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private class Actions:HubUiActions {
        var reads=0
        override fun connect(address:String,credential:String,alternate:String) {}
        override fun refreshSaved() {reads++}
        override fun switchEndpoint() {}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadHistory() {reads++}
        override fun loadModelNames() {reads++}
        override fun loadSubscriptions() {reads++}
        override fun forget() {}
        override fun renameDevice(id:String,input:String) {}
    }
    private fun setup(scale:State<Float>,actions:Actions) {
        val state=HubState(snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":1000},"month":{"totalTokens":1000}}}"""),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"))
        val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=listOf("devices"))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.language,prefs.appearance) {CompositionLocalProvider(LocalHeadlineMotionScale provides scale.value,LocalDesktopAnimationClock provides animationClock) {HubScreen(state,actions,preferences=prefs)}}}}
    }
    private fun pose(tag:String)=compose.onNodeWithTag("$tag-transition").fetchSemanticsNode().config[MenuMotionFraction]
    private fun save(name:String,tag:String) {
        val root=compose.onNode(isRoot() and hasAnyDescendant(hasTestTag("$tag-transition")),useUnmergedTree=true)
        saveVisualEvidence(compose.activity,root,"menu-motion-$name-${if(dark) "dark" else "light"}.png")
    }
    @Test fun actualNavigationFramesExitInputAndRapidReopenPreserveHome() {
        val scale=mutableStateOf(1f);val actions=Actions();setup(scale,actions)
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("1,000")
        compose.waitForIdle()
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("hub-view-selector").performClick();compose.mainClock.advanceTimeBy(32)
        val start=pose("hub-view-menu");save("start","hub-view-menu")
        val width=compose.onNodeWithTag("hub-view-menu-transition").fetchSemanticsNode().boundsInRoot.width
        compose.mainClock.advanceTimeBy(48);val middle=pose("hub-view-menu")
        assertTrue(middle>start && middle<1f)
        assertTrue(compose.onNodeWithTag("hub-view-menu-transition").fetchSemanticsNode().boundsInRoot.width>=width)
        save("middle","hub-view-menu")
        compose.mainClock.advanceTimeBy(220);assertEquals(1f,pose("hub-view-menu"),0f);save("open","hub-view-menu")
        compose.onNodeWithTag("view-home").performKeyInput {pressKey(Key.Escape)}
        compose.onNodeWithTag("hub-view-menu").assertDoesNotExist()
        assertFalse(compose.onNodeWithTag("hub-view-menu-transition").fetchSemanticsNode().config[MenuAcceptsInput])
        compose.onNodeWithTag("hub-view-menu-transition").performTouchInput {click(center)}
        compose.mainClock.advanceTimeBy(16);val exiting=pose("hub-view-menu");assertTrue(exiting in 0f..1f)
        save("exit","hub-view-menu")
        compose.onNodeWithTag("hub-view-selector").performClick();compose.mainClock.advanceTimeByFrame()
        val reopened=pose("hub-view-menu")
        // The closing transition advances during the tap's input/recomposition frame.
        assertTrue(reopened>0f && reopened<=exiting+.002f)
        compose.mainClock.advanceTimeBy(32);assertTrue(pose("hub-view-menu")>reopened)
        compose.mainClock.advanceTimeBy(240);assertEquals(1f,pose("hub-view-menu"),0f);save("reopen","hub-view-menu")
        compose.runOnIdle {scale.value=0f}
        compose.onNodeWithTag("view-home").performKeyInput {pressKey(Key.Escape)}
        compose.onNodeWithTag("hub-view-menu-transition").assertDoesNotExist()
        assertEquals(0,actions.reads)
        compose.mainClock.autoAdvance=true
    }
    @Test fun periodReductionAndBackgroundReturnDoNotReplay() {
        val scale=mutableStateOf(0f);val actions=Actions();setup(scale,actions)
        compose.onNodeWithTag("hub-period-month").performClick();compose.onNodeWithTag("hub-period-month").performClick()
        assertEquals(1f,pose("hub-period-menu"),0f);save("reduced","hub-period-menu")
        compose.onNodeWithTag("hub-period-menu-month").performKeyInput {pressKey(Key.Escape)}
        compose.onNodeWithTag("hub-period-menu-transition").assertDoesNotExist()
        compose.runOnIdle {scale.value=1f};compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("hub-period-month").performClick();compose.mainClock.advanceTimeBy(48)
        save("period","hub-period-menu")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("hub-period-menu-transition").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(240)
        compose.onNodeWithTag("hub-period-menu-transition").assertDoesNotExist()
        assertEquals(0,actions.reads);compose.mainClock.autoAdvance=true
    }
}
