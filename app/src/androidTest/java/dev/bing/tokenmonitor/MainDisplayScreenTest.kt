package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MainDisplayScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
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
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private fun select(id:String) {compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-$id").performScrollTo().performClick()}
    @Test fun originalTitleCompactUnitsActionsAndHiddenViewFallbackRemainUsable() {
        val snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":5000000123,"costUsd":1},"month":{"totalTokens":23456,"costUsd":2}}}""")
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=listOf("sessions")))
        val actions=Actions();val state=HubState(snapshot=snapshot,history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(state,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}
        compose.onNodeWithTag("hub-title-icon",useUnmergedTree=true).assertTextEquals("Σ")
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("5,000,000,123")
        compose.onNodeWithTag("hub-total-compact").assertDoesNotExist()
        compose.runOnIdle {prefs.value=prefs.value.copy(main=prefs.value.main.copy(titleIconOnly=false,compactTotal=true))}
        // The original title intentionally falls back to Σ if the text cannot fit this profile.
        compose.waitUntil(5000) {compose.onAllNodesWithTag("hub-title-name",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithTag("hub-title-icon",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("hub-total-compact").assertTextEquals("≈ 5B")
        val before=compose.onNodeWithTag("hub-settings-toggle").fetchSemanticsNode().boundsInRoot.left
        val refreshBefore=compose.onNodeWithTag("hub-refresh").fetchSemanticsNode().boundsInRoot.left
        assertTrue(before>refreshBefore)
        compose.runOnIdle {prefs.value=DisplayChange.SwapMainActions(true).apply(prefs.value)}
        assertTrue(compose.onNodeWithTag("hub-settings-toggle").fetchSemanticsNode().boundsInRoot.left<compose.onNodeWithTag("hub-refresh").fetchSemanticsNode().boundsInRoot.left)
        saveVisualEvidence(compose.activity,compose.onRoot(),"main-header-${if(dark) "dark" else "light"}.png")
        compose.onNodeWithTag("hub-view-current").performClick();compose.onNodeWithTag("hub-view-current").assertTextContains("Limits")
        compose.onNodeWithTag("hub-view-current").performTouchInput {longClick()};compose.onNodeWithTag("view-models").performScrollTo().performClick()
        compose.runOnIdle {prefs.value=prefs.value.copy(main=prefs.value.main.copy(viewOrder=listOf("limits","home"),hiddenViews=setOf("home","models")))}
        compose.waitUntil(5000) {compose.onAllNodesWithTag("hub-view-current").filter(hasText("Limits",substring=true)).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-home").assertDoesNotExist();compose.onNodeWithTag("view-models").assertDoesNotExist()
        compose.onNodeWithTag("view-devices").performScrollTo().performClick()
        compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithTag("hub-view-current").assertTextContains("Limits")
        assertEquals(0,actions.reads) // Cached navigation and local formatting never start reads.
    }
    @Test fun originalMainSettingsControlsSaveLocallyAndOrderResetIsSeparateFromShowAll() {
        val name="main-controls-${java.util.UUID.randomUUID()}.bin";val store=DisplayPreferenceStore(compose.activity,name)
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=listOf("sessions")))
        val source=HubParser.parse("""{"periods":{"today":{"totalTokens":12345}},"devices":[{"deviceId":"a","displayName":"Public A","hostname":"Public A"},{"deviceId":"b","displayName":"Public B","hostname":"Public B"}]}""")
        val state=HubState(snapshot=source,history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"));val actions=Actions()
        try {
            store.save(prefs.value)
            compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(state,actions,preferences=prefs.value,updateDisplay={store.save(it.apply(prefs.value));prefs.value=store.load()})}}}
            compose.onNodeWithTag("hub-settings-toggle").performClick()
            fun click(tag:String) {compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performClick()}
            openSettingsSection(compose,"appearance");click("appearance-colors-toggle");click("main-title-icon");click("main-compact-total");click("main-live-rate")
            click("main-units-localized");click("main-rate-device");compose.onNodeWithTag("main-rate-device-1").performClick()
            click("main-swap-actions")
            compose.onNodeWithTag("hub-live-rate").assertDoesNotExist() // Original settings panel hides this footer indicator.
            saveVisualEvidence(compose.activity,compose.onRoot(),"main-settings-${if(dark) "dark" else "light"}.png")
            openSettingsSection(compose,"main");click("main-view-sessions-settings");click("session-context-remaining");click("main-view-visible-projects");click("main-view-order-sessions");compose.onNodeWithTag("main-view-up-sessions").performClick()
            val before=store.load();assertTrue("projects" in before.main.hiddenViews);assertTrue(before.main.viewOrder.isNotEmpty())
            click("main-views-reset");assertEquals(emptyList<String>(),store.load().main.viewOrder);assertEquals(setOf("projects"),store.load().main.hiddenViews)
            click("main-views-show-all");assertTrue(store.load().main.hiddenViews.isEmpty())
            val main=store.load().main;assertFalse(main.titleIconOnly);assertTrue(main.compactTotal);assertTrue(main.showLiveRate)
            assertEquals("localized",main.compactUnits);assertEquals("b",main.rateDevice);assertTrue(main.swapActions);assertEquals("remaining",main.sessionContextMetric)
            compose.onNodeWithTag("hub-settings-toggle").performClick();compose.onNodeWithTag("hub-live-rate").assertTextEquals("— tok/s")
            compose.onNodeWithTag("hub-total-tokens").assertTextEquals("12,345");compose.onNodeWithTag("hub-total-compact").assertTextEquals("≈ 12.3K")
            assertEquals(0,actions.reads)
        } finally {store.clear()}
    }
    @Test fun realPerDeviceDeltasTapModeHoldModelsScopeAndForegroundReset() {
        var clock=0L;val now:()->Long={clock};val actions=Actions()
        fun snapshot(a:Int,b:Int)=HubParser.parse("""{"periods":{"today":{"totalTokens":50000}},"devices":[{"deviceId":"a","displayName":"Public A","hostname":"Public A","stale":false,"periods":{"today":{"timedTokens":${100+a*20},"timedOutputTokens":${40+a*10},"timedDurationMs":${1000+a*200},"modelThroughput":{"public-alpha":{"timedTokens":${100+a*20},"timedOutputTokens":${40+a*10},"timedDurationMs":${1000+a*200}}}}}},{"deviceId":"b","displayName":"Public B","hostname":"Public B","stale":false,"periods":{"today":{"timedTokens":${200+b*50},"timedOutputTokens":${80+b*25},"timedDurationMs":${2000+b*500},"modelThroughput":{"public-beta":{"timedTokens":${200+b*50},"timedOutputTokens":${80+b*25},"timedDurationMs":${2000+b*500}}}}}}]}""")
        val state=mutableStateOf(HubState(snapshot=snapshot(0,0),live=true,history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off")))
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=listOf("sessions"),main=MainDisplayPreferences(showLiveRate=true)))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {CompositionLocalProvider(LocalRateNow provides now) {HubScreen(state.value,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}}
        compose.onNodeWithTag("hub-live-rate").assertTextEquals("— tok/s")
        compose.runOnIdle {clock=800;state.value=state.value.copy(snapshot=snapshot(1,0))}
        compose.waitUntil(5000) {compose.onAllNodesWithText("50 tok/s").fetchSemanticsNodes().isNotEmpty()}
        compose.runOnIdle {clock=1800;state.value=state.value.copy(snapshot=snapshot(1,1))}
        compose.waitUntil(5000) {compose.onAllNodesWithText("100 tok/s").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("hub-live-rate").performClick();compose.onNodeWithTag("hub-live-rate").assertTextEquals("12K TPM")
        compose.onNodeWithTag("hub-live-rate").performTouchInput {longClick()}
        compose.onNodeWithTag("live-rate-current").assertTextEquals("12K TPM")
        compose.onNodeWithText("Public A").assertIsDisplayed();compose.onNodeWithText("Public B").assertIsDisplayed()
        compose.onNodeWithText("public-alpha").assertIsDisplayed();compose.onNodeWithText("public-beta").assertIsDisplayed()
        saveVisualEvidence(compose.activity,compose.onNode(isDialog()),"main-live-rate-${if(dark) "dark" else "light"}.png")
        compose.onNodeWithText("Close").performClick()
        compose.runOnIdle {prefs.value=DisplayChange.LiveRateDevice("a").apply(prefs.value)}
        compose.onNodeWithTag("hub-live-rate").assertTextEquals("— TPM")
        compose.runOnIdle {clock=1900;state.value=state.value.copy(snapshot=snapshot(2,1))}
        compose.waitUntil(5000) {compose.onAllNodesWithText("6K TPM").fetchSemanticsNodes().isNotEmpty()}
        compose.runOnIdle {state.value=state.value.copy(live=false)}
        compose.onNodeWithTag("hub-live-rate").assertTextEquals("— TPM")
        compose.runOnIdle {state.value=state.value.copy(live=true)}
        compose.onNodeWithTag("hub-live-rate").assertTextEquals("— TPM")
        compose.runOnIdle {prefs.value=DisplayChange.ShowLiveRate(false).apply(prefs.value)}
        compose.onNodeWithTag("hub-live-rate").assertDoesNotExist();assertEquals(0,actions.reads)
    }
}
