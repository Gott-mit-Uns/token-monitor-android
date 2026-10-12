package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class VendorScreenTest {
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
    private fun show(tag:String) {
        val container=if(compose.onAllNodesWithTag("dashboard-records").fetchSemanticsNodes().isNotEmpty()) "dashboard-scroll" else "hub-content"
        compose.onNodeWithTag(container).performScrollToNode(hasTestTag(tag))
    }
    private fun click(tag:String) {show(tag);compose.onNodeWithTag(tag).performClick()}
    private fun openAppearance() {if(compose.onAllNodesWithTag("appearance-controls").fetchSemanticsNodes().isEmpty()) openSettingsSection(compose,"appearance")}
    private fun openVendor() {openAppearance();if(compose.onAllNodesWithTag("vendor-color-list").fetchSemanticsNodes().isEmpty()) click("vendor-colors-toggle")}
    private fun vendor(tag:String) {show("vendor-color-list");compose.onNodeWithTag("vendor-color-list").performScrollToNode(hasTestTag(tag))}
    private fun vendorClick(tag:String) {vendor(tag);compose.onNodeWithTag(tag).performClick()}

    private fun select(page:String) {compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-$page").performScrollTo().performClick()}
    private fun color(tag:String,expected:Color) {show(tag);val pixels=compose.onNodeWithTag(tag).captureToImage().toPixelMap();assertEquals(expected.toArgb(),pixels[pixels.width/4,pixels.height/2].toArgb())}
    private fun save(name:String,dark:Boolean) {saveVisualEvidence(compose.activity,compose.onRoot(),"vendor-$name-${if(dark) "dark" else "light"}.png")}
    @Test fun selectedVendorRgbReachesActualToolModelQuotaAndHistoryPixelsWhileThemeAndSourceStayIntact() {
        val dark=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
        val prefs=mutableStateOf(DisplayPreferences(theme=if(dark) ThemeChoice.DARK else ThemeChoice.LIGHT,currency="CNY",currencyRates=mapOf("CNY" to "7")))
        val today=LocalDate.now().toString()
        val snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":1000,"costUsd":1,"clients":{"codex":1000},"models":{"gpt-5":1000}}},"limits":{"providers":[{"provider":"codex","windows":[{"kind":"weekly","remainingPercent":75}]}]}}""")
        val history=HubHistory(listOf(HistoryRow(today,1000,"1",perClient=mapOf("codex" to HistoryAttribution(1000,"1")),perModel=mapOf("gpt-5" to HistoryAttribution(1000,"1")))),emptyList())
        val state=HubState(snapshot=snapshot,history=history,modelNames=ModelNames(emptyMap(),"off"));val actions=Actions()
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(state,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}
        compose.onNodeWithTag("hub-settings-toggle").performClick();openVendor();vendorClick("vendor-color-codex-open")
        compose.onNodeWithTag("vendor-color-codex").performTextReplacement("#ff0000");compose.onNodeWithTag("vendor-color-codex").performImeAction()
        compose.runOnIdle {assertEquals(mapOf("codex" to "#ff0000"),prefs.value.appearance.vendorColors);assertTrue(prefs.value.appearance.colors.isEmpty())}
        compose.waitUntil(5000) {ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) != true}
        compose.onNodeWithText("Σ",useUnmergedTree=true).assertIsDisplayed()
        save("settings",dark)
        vendorClick("vendor-color-codex-open");compose.onNodeWithTag("vendor-color-codex").performTextReplacement("bad");compose.onNodeWithTag("vendor-color-codex-apply").assertIsNotEnabled();compose.onNodeWithTag("vendor-color-codex-cancel").performScrollTo().performClick()
        select("tools");color("breakdown-color-tool-codex",Color.Red);save("tools",dark)
        select("models");color("breakdown-color-model-gpt-5",Color.Red)
        select("limits");color("limit-color-codex",Color.Red.copy(alpha=0.68f).compositeOver(Color.Red.copy(alpha=0.16f).compositeOver(hexColor(DesktopAppearance.palette(emptyMap(),dark).getValue("bg")))))
        select("trends");compose.onNodeWithTag("trends-main-open-history").performClick()
        compose.onNodeWithTag("dashboard-open-records").performScrollTo().performClick()
        color("history-color-codex",Color.Red);save("history",dark)
        click("history-by-model");color("history-color-gpt-5",Color.Red)
        compose.runOnIdle {assertEquals(snapshot,state.snapshot);assertEquals(history,state.history);assertEquals(0,actions.reads);assertEquals("7",prefs.value.currencyRates["CNY"])}
        compose.onNodeWithTag("trends-history-back").performClick()
        compose.onNodeWithTag("hub-view-selector").assertIsDisplayed()
        compose.onNodeWithTag("hub-settings-toggle").performClick();openVendor();vendorClick("vendor-color-codex-reset");compose.runOnIdle {assertTrue(prefs.value.appearance.vendorColors.isEmpty())}
        select("tools");color("breakdown-color-tool-codex",Color(0xFF49A3B0))
    }
    @Test fun iconsBecomeDotsNavigationAndLimitsRemainAndActivityRestoresLocalChoices() {
        val dark=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
        val prefs=mutableStateOf(DisplayPreferences(theme=if(dark) ThemeChoice.DARK else ThemeChoice.LIGHT))
        val snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":1000,"clients":{"codex":1000},"models":{"gpt-5":1000}}},"devices":[{"deviceId":"public-device","hostname":"Public device","platform":"linux"}],"limits":{"providers":[{"provider":"codex","windows":[{"kind":"weekly","remainingPercent":75}]}]}}""")
        val state=mutableStateOf(HubState(snapshot=snapshot,live=true,history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off")));val actions=Actions()
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(state.value,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}
        compose.onNodeWithTag("hub-live-dot",useUnmergedTree=true).assertExists()
        compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"appearance");click("appearance-tool-icons");click("appearance-live-dot")
        compose.onNodeWithTag("hub-live-dot",useUnmergedTree=true).assertDoesNotExist();compose.runOnIdle {assertTrue(state.value.live);assertEquals(0,actions.reads)}
        select("tools");show("vendor-dot-codex");compose.onNodeWithTag("vendor-icon-codex").assertDoesNotExist();save("dots",dark)
        select("devices");compose.onNodeWithTag("hub-content").performScrollToNode(hasText("public-device"));compose.onNodeWithTag("device-dot-public-device",useUnmergedTree=true).assertExists();compose.onNodeWithTag("device-icon-public-device",useUnmergedTree=true).assertDoesNotExist()
        select("limits");compose.onNodeWithTag("hub-content").performScrollToNode(hasText("Codex"));compose.onNodeWithTag("vendor-icon-codex",useUnmergedTree=true).assertExists();compose.onNodeWithTag("hub-view-selector").assertIsDisplayed();compose.onNodeWithTag("hub-settings-toggle").assertIsDisplayed()
        compose.onNodeWithTag("hub-settings-toggle").performClick();openAppearance();click("appearance-tool-icons");click("appearance-live-dot")
        compose.onNodeWithTag("hub-live-dot",useUnmergedTree=true).assertExists();select("tools");show("vendor-icon-codex");compose.onNodeWithTag("vendor-dot-codex").assertDoesNotExist()
        val store=DisplayPreferenceStore(compose.activity);val before=store.load()
        try {
            val saved=prefs.value.copy(language=LanguageChoice.ENGLISH,currency="CNY",currencyRates=mapOf("CNY" to "7"),appearance=prefs.value.appearance.copy(vendorColors=mapOf("codex" to "#ff0000"),showToolIcons=false,showLiveDot=false,textSize="largest",interfaceFont="serif"))
            compose.activityRule.scenario.onActivity {ViewModelProvider(it)[HubViewModel::class.java].updateDisplay(saved)}
            compose.waitUntil(5000) {store.load()==saved};compose.activityRule.scenario.recreate()
            openSettingsSection(compose,"appearance");show("appearance-tool-icons");compose.onNodeWithTag("appearance-tool-icons").assertIsOff();show("appearance-live-dot");compose.onNodeWithTag("appearance-live-dot").assertIsOff()
            openVendor();vendorClick("vendor-color-codex-open");compose.onNodeWithTag("vendor-color-codex").assertTextContains("#ff0000");compose.onNodeWithTag("vendor-color-codex-cancel").performScrollTo().performClick()
            assertEquals(saved,store.load());assertEquals(snapshot,state.value.snapshot)
        } finally {store.save(before)}
    }
}
