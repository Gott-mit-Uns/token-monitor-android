package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

class SessionSettingsScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
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
    private fun click(tag:String) {compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performClick()}
    private fun save(page:String)=saveVisualEvidence(compose.activity,compose.onRoot(),"session-settings-$page-${if(dark) "dark" else "light"}.png")
    private fun state():HubState {
        val now=Instant.now().toString()
        val period="""{"totalTokens":1000,"sessions":{"public:one":{"title":"Public visible session","projectLabel":"Public project label","client":"codex","sessionId":"public-one","lastUsedAt":"$now","totalTokens":1000,"contextTokens":75000,"contextWindow":100000}},"projects":{"public:project":{"projectLabel":"Public project label","totalTokens":1000}}}"""
        return HubState(snapshot=HubParser.parse("""{"periods":{"today":$period,"month":$period}}"""),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"))
    }
    @Test fun sessionSubgroupControlsHomeAndFullSessionTitlesAndContextWithoutNetworkReads() {
        val name="session-ui-${UUID.randomUUID()}.bin";val store=DisplayPreferenceStore(compose.activity,name)
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=listOf("sessions")))
        val source=state();val actions=Actions()
        try {
            store.save(prefs.value)
            compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(source,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value);store.save(prefs.value)})}}}
            compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"appearance")
            compose.onNodeWithTag("session-context-remaining").assertDoesNotExist()
            openSettingsSection(compose,"main");click("main-view-sessions-settings")
            compose.onNodeWithTag("session-titles").assertIsOn();save("controls")
            click("session-titles");click("session-context-remaining")
            assertFalse(store.load().sessionTitlesEnabled);assertFalse(store.load().hideTitles);assertEquals("remaining",store.load().main.sessionContextMetric)
            compose.onNodeWithTag("hub-settings-toggle").performClick()
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("home-session-public:one"));compose.onNodeWithText("Public visible session").assertDoesNotExist()
            compose.onNodeWithTag("home-session-context-public:one").assertTextEquals("25%")
            save("home-hidden")
            compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-sessions").performScrollTo().performClick()
            compose.onNodeWithTag("session-title-public:one",useUnmergedTree=true).assertTextEquals("Codex")
            compose.onNodeWithTag("session-context-public:one").assertTextEquals("25%")
            save("list-hidden")
            compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"main")
            if(compose.onAllNodesWithTag("main-view-sessions-details").fetchSemanticsNodes().isEmpty()) click("main-view-sessions-settings")
            click("session-titles");click("session-context-used")
            compose.onNodeWithTag("hub-settings-toggle").performClick()
            compose.onNodeWithTag("session-title-public:one",useUnmergedTree=true).assertTextEquals("Public visible session")
            compose.onNodeWithTag("session-context-public:one").assertTextEquals("75%")
            save("list-restored");assertEquals(0,actions.reads)
            assertEquals("Public visible session",source.snapshot!!.periods.getValue("today").sessions.single().session!!.title)
        } finally {store.clear()}
    }
    @Test fun hiddenSessionConfigurationAndGlobalPrivacyRemainIndependentFromViewOrder() {
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,hideTitles=true,main=MainDisplayPreferences(hiddenViews=setOf("sessions"))))
        val actions=Actions();val source=state()
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(source,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}
        compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"main")
        click("main-view-sessions-settings");compose.onNodeWithText("The project and session privacy setting currently hides all titles.").assertExists()
        click("session-titles");click("main-view-order-sessions");compose.onNodeWithTag("main-view-up-sessions").performClick()
        assertTrue("sessions" in prefs.value.main.hiddenViews);assertTrue(prefs.value.hideSessionTitles)
        compose.onNodeWithTag("main-view-sessions-details").assertExists()
        click("main-views-reset");assertTrue("sessions" in prefs.value.main.hiddenViews);assertFalse(prefs.value.sessionTitlesEnabled)
        click("main-views-show-all");assertTrue(prefs.value.main.hiddenViews.isEmpty());assertFalse(prefs.value.sessionTitlesEnabled)
        click("main-view-sessions-settings");compose.onNodeWithTag("main-view-sessions-details").assertDoesNotExist()
        click("main-view-sessions-settings");save("privacy")
        assertEquals(0,actions.reads)
    }
    @Test fun lastVisibleViewCannotBeHiddenAndShowingAnotherViewPreservesChoices() {
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,main=MainDisplayPreferences(hiddenViews=MainDisplay.ids.toSet()-"sessions")))
        val actions=Actions();val source=state()
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(source,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}
        compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"main")
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("main-view-visible-sessions"))
        compose.onNodeWithTag("main-view-visible-sessions").assertIsNotEnabled()
        click("main-view-visible-home")
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("main-view-visible-sessions"))
        compose.onNodeWithTag("main-view-visible-sessions").assertIsEnabled().performClick()
        assertEquals(listOf("home"),MainDisplay.visible(prefs.value.main))
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("main-view-visible-home"))
        compose.onNodeWithTag("main-view-visible-home").assertIsNotEnabled()
        save("last-view");assertEquals(0,actions.reads)
    }

}
