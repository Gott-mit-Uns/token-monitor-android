package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class SettingsManagementScreenTest {
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
    private fun snapshot(remaining:Int)=HubParser.parse("""{"periods":{"today":{"totalTokens":123}},"limits":{"providers":[{"provider":"codex","status":"ok","updatedAt":"${Instant.now()}","accountKey":"public","accountName":"Public account","windows":[{"kind":"session","label":"Public session","remainingPercent":$remaining}]}]}}""")
    private fun state(remaining:Int=19)=HubState(snapshot=snapshot(remaining),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"))
    private fun click(tag:String) {show(tag);compose.onNodeWithTag(tag).performClick()}
    private fun show(tag:String)=compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))
    private fun save(page:String)=saveVisualEvidence(compose.activity,compose.onRoot(),"management-$page-${if(dark) "dark" else "light"}.png")

    @Test fun viewHeaderActionsPrecedeRowsDisableAtDefaultsAndPreserveUnrelatedSettings() {
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH));val actions=Actions()
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(state(),actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}
        compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"main")
        show("main-views-header");compose.onNodeWithTag("main-views-reset").assertIsNotEnabled();compose.onNodeWithTag("main-views-show-all").assertIsNotEnabled()
        val header=compose.onNodeWithTag("main-views-header").getUnclippedBoundsInRoot()
        val first=compose.onNodeWithTag("main-view-visible-home").getUnclippedBoundsInRoot()
        assertTrue("Header precedes first row in layout coordinates: $header / $first",header.bottom<=first.top)
        save("views-default")
        click("main-view-visible-projects");click("main-view-order-sessions");compose.onNodeWithTag("main-view-up-sessions").performClick()
        val selected=prefs.value;show("main-views-header");compose.onNodeWithTag("main-views-reset").assertIsEnabled();compose.onNodeWithTag("main-views-show-all").assertIsEnabled();save("views-custom")
        click("main-views-reset");assertTrue(prefs.value.main.viewOrder.isEmpty());assertEquals(selected.main.hiddenViews,prefs.value.main.hiddenViews)
        click("main-views-show-all");assertTrue(prefs.value.main.hiddenViews.isEmpty());assertEquals(selected.copy(main=selected.main.copy(viewOrder=emptyList(),hiddenViews=emptySet())),prefs.value)
        assertEquals(0,actions.reads)
    }

    @Test fun providerSearchPreservesHiddenItemsDisablesMovesAndRestoresAbsentReadings() {
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,limits=LimitDisplayPreferences(hiddenItems=mapOf("codex" to listOf("credits")))))
        val source=state();val actions=Actions()
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(source,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}
        compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"limits")
        click("limit-provider-search");compose.onNodeWithTag("limit-provider-search").performTextReplacement("  CODEX  ");compose.onNodeWithTag("limit-provider-search").performImeAction()
        compose.onNodeWithTag("limit-items-claude").assertDoesNotExist();show("limit-provider-order-codex");compose.onNodeWithTag("limit-provider-order-codex").assertIsNotEnabled()
        click("limit-items-codex");show("limit-item-codex-1");compose.onNodeWithTag("limit-item-codex-1").assertTextContains("not reported now",substring=true);compose.onNodeWithTag("limit-item-codex-1").assertIsOff();save("provider-filter")
        click("limit-items-restore-codex");assertTrue(prefs.value.limits.hiddenItems.isEmpty())
        click("limit-provider-search");compose.onNodeWithTag("limit-provider-search").performTextReplacement("no-public-match");compose.onNodeWithTag("limit-provider-search").performImeAction()
        show("limit-provider-empty");save("provider-empty")
        click("limit-provider-search-clear");click("limit-provider-order-codex");compose.onNodeWithTag("limit-provider-up-codex").performClick();assertEquals("codex",prefs.value.limits.providerOrder.first())
        assertEquals(0,actions.reads)
    }

    @Test fun homeQuotaSubgroupHighlightAndForcedProviderNamesWorkIndependentlyOfBars() {
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=listOf("limits")));val source=mutableStateOf(state());val actions=Actions()
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(source.value,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}
        compose.onNodeWithTag("home-quota-critical-0-0").assertDoesNotExist()
        compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"limits");compose.onNodeWithTag("home-quota-settings-toggle").assertDoesNotExist()
        openSettingsSection(compose,"main");click("main-view-home-settings");click("home-quota-settings-toggle");click("home-quota-highlight")
        click("home-quota-count");compose.onNodeWithTag("home-quota-count").performTextReplacement("13");compose.onNodeWithTag("home-quota-count").performImeAction();assertEquals(3,prefs.value.limits.homeAccountCount)
        compose.onNodeWithTag("home-quota-count").performTextReplacement("5");compose.onNodeWithTag("home-quota-count").performImeAction();assertEquals(5,prefs.value.limits.homeAccountCount);save("home-options")
        compose.runOnIdle {prefs.value=prefs.value.copy(appearance=prefs.value.appearance.copy(showToolIcons=false))}
        show("home-quota-names");compose.onNodeWithTag("home-quota-names").assertIsOn();compose.onNodeWithTag("home-quota-names").assertIsNotEnabled();assertFalse(prefs.value.limits.showHomeProviderNames)
        compose.onNodeWithTag("hub-settings-toggle").performClick();show("home-quota-critical-0-0")
        val layouts=mutableListOf<TextLayoutResult>();compose.onNodeWithTag("limit-value-0-0").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertEquals(Color(0xFFF47788),layouts.single().layoutInput.style.color);save("home-critical")
        compose.runOnIdle {source.value=state(20)};compose.onNodeWithTag("home-quota-critical-0-0").assertDoesNotExist()
        layouts.clear();compose.onNodeWithTag("limit-value-0-0").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertEquals(Color(desktopVendorColors.getValue("codex")),layouts.single().layoutInput.style.color);save("home-low")
        assertFalse(prefs.value.limits.homeBars);assertEquals(0,actions.reads)
    }
}
