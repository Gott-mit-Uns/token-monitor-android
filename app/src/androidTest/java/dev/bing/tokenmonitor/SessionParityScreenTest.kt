package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class SessionParityScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val animationClock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private class Actions:HubUiActions {
        var reads=0
        override fun connect(address:String,credential:String,alternate:String) {reads++}
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
    private val aliases=ModelNames(mapOf("vendor/shared" to "Public model","shared" to "Public model"),"off")
    private fun state(snapshot:Snapshot,names:ModelNames=aliases)=HubState(snapshot=snapshot,history=HubHistory(emptyList(),emptyList()),modelNames=names)
    private fun sessions() {compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-sessions").performScrollTo().performClick()}
    private fun show(tag:String)=compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))
    private fun save(page:String)=saveVisualEvidence(compose.activity,compose.onRoot(),"session-parity-$page-${if(dark) "dark" else "light"}.png")
    @Test fun fallbackUsesAliasesCostRankingAndExpandableMetadataWithoutChangingSource() {
        val snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":1000,"costUsd":21,"models":{"vendor/shared":500,"shared":300,"other":200},"modelCosts":{"vendor/shared":1,"shared":0,"other":20},"modelCacheReads":{"vendor/shared":100,"shared":100,"other":20},"modelOutputs":{"vendor/shared":300,"shared":150,"other":100},"tokenComponentsAvailable":true}}}""")
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,modelRanking="cost"));val actions=Actions()
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(state(snapshot),actions,preferences=prefs.value)}}}
        sessions();show("breakdown-model-other")
        assertTrue(compose.onNodeWithTag("breakdown-model-other").getUnclippedBoundsInRoot().top<compose.onNodeWithTag("breakdown-model-Public model").getUnclippedBoundsInRoot().top)
        compose.onNodeWithText("vendor/shared").assertDoesNotExist();save("fallback-cost")
        show("breakdown-model-Public model");compose.onNodeWithTag("breakdown-model-Public model").performClick().assertIsSelected();show("tool-detail-model-Public model-hit");compose.onNodeWithTag("tool-detail-model-Public model-hit-value").assertTextEquals("200");save("fallback-detail")
        show("breakdown-model-Public model");compose.onNodeWithTag("breakdown-model-Public model").performClick().assertIsNotSelected()
        compose.runOnIdle {prefs.value=prefs.value.copy(modelRanking="tokens")}
        show("breakdown-model-Public model")
        assertTrue(compose.onNodeWithTag("breakdown-model-Public model").getUnclippedBoundsInRoot().top<compose.onNodeWithTag("breakdown-model-other").getUnclippedBoundsInRoot().top)
        save("fallback-tokens");assertEquals(500L,snapshot.periods.getValue("today").models.getValue("vendor/shared"));assertEquals(0,actions.reads)
    }
    private fun snapshot(at:Instant,ended:Boolean=false)=HubParser.parse("""{"periods":{"today":{"totalTokens":1000,"costUsd":0,"sessions":{"public":{"client":"codex","totalTokens":1000,"costUsd":0,"title":"Public session","lastUsedAt":"$at","turnEnded":$ended,"models":{"vendor/shared":700,"shared":300}}}}}}""")
    @Test fun actualHomeAndSessionAliasesCollapseModelsAndRemoveObsoletePopover() {
        val snapshot=snapshot(Instant.now());val source=mutableStateOf(state(snapshot,ModelNames(emptyMap(),"off")));val actions=Actions()
        val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=listOf("sessions"))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.language,prefs.appearance) {HubScreen(source.value,actions,preferences=prefs)}}}
        sessions();show("session-row-public");compose.onNodeWithTag("session-models-public").performClick();compose.onNodeWithTag("session-model-details").assertExists()
        compose.runOnIdle {source.value=source.value.copy(modelNames=aliases)}
        compose.onNodeWithTag("session-model-details").assertDoesNotExist();compose.onNodeWithTag("session-models-public").assertDoesNotExist()
        compose.onNodeWithTag("session-subtitle-public",useUnmergedTree=true).assertTextContains("Public model",substring=true);save("aliases-list")
        compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-home").performScrollTo().performClick();show("home-session-public")
        compose.onNodeWithText("Public model",substring=true,useUnmergedTree=true).assertExists();save("aliases-home")
        assertEquals(mapOf("vendor/shared" to 700L,"shared" to 300L),source.value.snapshot!!.periods.getValue("today").sessions.single().session!!.models)
        assertEquals(0,actions.reads)
    }
    @Test fun timestampFlareIsOnceOnlyRespectsReductionAndSettlesAcrossBackgroundReturn() {
        val initial=Instant.now().minusSeconds(30);val source=mutableStateOf(state(snapshot(initial)));val actions=Actions()
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList(),appearance=AppearancePreferences(reduceMotion="off")))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {CompositionLocalProvider(LocalDesktopAnimationClock provides animationClock) {HubScreen(source.value,actions,preferences=prefs.value)}}}}
        sessions();show("session-row-public")
        fun pulse(value:Boolean)=compose.onNodeWithTag("session-running-public",useUnmergedTree=true).assert(SemanticsMatcher.expectValue(SessionPulsePlaying,value))
        pulse(false);save("motion-start");compose.mainClock.autoAdvance=false
        compose.runOnIdle {source.value=state(snapshot(initial.plusSeconds(1)))}
        compose.mainClock.advanceTimeBy(64);pulse(true);compose.mainClock.advanceTimeBy(320);pulse(true);save("motion-middle")
        compose.mainClock.advanceTimeBy(1300);pulse(false);save("motion-end")
        compose.runOnIdle {source.value=source.value.copy(snapshot=snapshot(initial.plusSeconds(1)))}
        compose.mainClock.advanceTimeBy(64);pulse(false)
        compose.runOnIdle {source.value=state(snapshot(initial.plusSeconds(2),true))};compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("session-running-public",useUnmergedTree=true).assertDoesNotExist()
        compose.runOnIdle {source.value=state(snapshot(initial.plusSeconds(3)))};compose.mainClock.advanceTimeBy(64);pulse(true)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED);compose.mainClock.advanceTimeBy(300)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {source.value=state(snapshot(initial.plusSeconds(4)))}
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);compose.mainClock.advanceTimeBy(64);pulse(false)
        compose.runOnIdle {prefs.value=prefs.value.copy(appearance=prefs.value.appearance.copy(reduceMotion="on"));source.value=state(snapshot(initial.plusSeconds(5)))}
        compose.mainClock.advanceTimeBy(64);pulse(false);save("motion-reduced")
        assertEquals(0,actions.reads);compose.mainClock.autoAdvance=true
    }
}
