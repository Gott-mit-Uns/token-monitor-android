package dev.bing.tokenmonitor

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EditControlsScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
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
    private fun click(tag:String) {compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performClick()}
    private fun save(page:String,tag:String?=null) {saveVisualEvidence(compose.activity,if(tag==null) compose.onRoot() else compose.onNodeWithTag(tag),"edit-$page-${if(dark) "dark" else "light"}.png")}
    @Test fun sourceCodeFieldCopyPasteAndKeyboardApplyKeepFontsVendorRatesAndHubData() {
        val initial=DisplayPreferences(theme=if(dark) ThemeChoice.DARK else ThemeChoice.LIGHT,language=LanguageChoice.ENGLISH,currency="CNY",currencyRates=mapOf("CNY" to "7"),appearance=AppearancePreferences(interfaceFont="serif",displayFont="monospace",vendorColors=mapOf("codex" to "#ff0000")))
        val prefs=mutableStateOf(initial);val actions=Actions()
        val snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":1000,"costUsd":1}}}""")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val manager=compose.activity.getSystemService(ClipboardManager::class.java)
        var before:ClipData?=null
        instrumentation.runOnMainSync {before=manager.primaryClip}
        try {
            compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(prefs.value.theme!=ThemeChoice.LIGHT,prefs.value.language,prefs.value.appearance) {HubScreen(HubState(snapshot=snapshot),actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}
            compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"appearance")
            click("appearance-code-copy")
            val canonical=DesktopAppearance.encode(DesktopAppearance.palette(initial.appearance.colors,dark))
            compose.waitUntil(5000) {manager.primaryClip?.getItemAt(0)?.text?.toString()==canonical}
            compose.onNodeWithTag("appearance-theme-code-field").assertTextEquals(canonical)
            instrumentation.runOnMainSync {manager.setPrimaryClip(ClipData.newPlainText("Public theme","TM1-2563EB-F6F7F9-1C1F26-5B626D"))}
            click("appearance-code-paste")
            compose.waitUntil(5000) {prefs.value.appearance.colors["bg"]=="#f6f7f9"}
            compose.onNodeWithTag("appearance-code-status").assertTextEquals("Theme applied.")
            val applied=prefs.value.appearance.colors
            click("appearance-theme-code-field");compose.onNodeWithTag("appearance-theme-code-field").performTextReplacement("TM3-000000");compose.onNodeWithTag("appearance-theme-code-field").performImeAction()
            compose.onNodeWithTag("appearance-code-status").assertTextEquals("This theme code version is not supported yet.")
            compose.runOnIdle {assertEquals(applied,prefs.value.appearance.colors)}
            compose.onNodeWithTag("appearance-theme-code-field").performTextReplacement("TM2-B7EAD4-303438-EEF5FB-A3ADBB-73BDF5");compose.onNodeWithTag("appearance-theme-code-field").performImeAction()
            compose.waitUntil(5000) {prefs.value.appearance.colors["bg"]=="#303438"}
            compose.runOnIdle {
                assertEquals(initial.appearance.interfaceFont,prefs.value.appearance.interfaceFont)
                assertEquals(initial.appearance.displayFont,prefs.value.appearance.displayFont)
                assertEquals(initial.appearance.vendorColors,prefs.value.appearance.vendorColors)
                assertEquals(initial.currencyRates,prefs.value.currencyRates);assertEquals(0,actions.reads)
            }
            click("appearance-preset-${if(dark) "default" else "porcelain"}");save("theme")
            click("appearance-colors-toggle");click("appearance-interface-reset");click("appearance-display-reset")
            compose.runOnIdle {assertEquals("",prefs.value.appearance.interfaceFont);assertEquals(DesktopAppearance.defaultDisplayFont,prefs.value.appearance.displayFont)}
            save("fonts")
            click("appearance-interface-select")
            compose.onNodeWithTag("appearance-interface-app").assertIsSelected().assertIsFocused()
            save("font-menu","appearance-interface-menu")
            compose.onNodeWithTag("appearance-interface-app").performKeyInput {pressKey(Key.S)}
            compose.onNodeWithTag("appearance-interface-system").assertIsFocused()
            compose.onNodeWithTag("appearance-interface-system").performKeyInput {pressKey(Key.Escape)}
            compose.onNodeWithTag("appearance-interface-menu").assertDoesNotExist()
            assertEquals("",prefs.value.appearance.interfaceFont)
        } finally {
            instrumentation.runOnMainSync {val previous=before;if(previous!=null) manager.setPrimaryClip(previous) else if(Build.VERSION.SDK_INT>=28) manager.clearPrimaryClip() else manager.setPrimaryClip(ClipData.newPlainText("",""))}
        }
    }
    @Test fun colorDialogCancelRgbAndManualRateFallbackOperateActualControlsWithoutReads() {
        val prefs=mutableStateOf(DisplayPreferences(theme=if(dark) ThemeChoice.DARK else ThemeChoice.LIGHT,language=LanguageChoice.ENGLISH))
        val actions=Actions();val state=HubState(snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":1000,"costUsd":1}}}"""))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(prefs.value.theme!=ThemeChoice.LIGHT,prefs.value.language,prefs.value.appearance) {HubScreen(state,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}
        compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"appearance");click("appearance-colors-toggle");click("appearance-color-chart-open")
        compose.onNodeWithTag("appearance-color-chart").performTextReplacement("invalid")
        compose.onNodeWithTag("appearance-color-chart-apply").assertIsNotEnabled()
        compose.onNodeWithTag("appearance-color-chart-R").performSemanticsAction(SemanticsActions.SetProgress) {it(64f)}
        compose.onNodeWithTag("appearance-color-chart").assertTextEquals("#40bdf5")
        val pixels=compose.onNodeWithTag("appearance-color-chart-preview").captureToImage().toPixelMap()
        assertEquals(Color(0xFF40BDF5).toArgb(),pixels[pixels.width/2,pixels.height/2].toArgb())
        save("picker","appearance-color-chart-dialog")
        compose.onNodeWithTag("appearance-color-chart-cancel").performScrollTo().performClick()
        compose.runOnIdle {assertTrue(prefs.value.appearance.colors.isEmpty())}
        click("appearance-color-chart-open");compose.onNodeWithTag("appearance-color-chart").performTextReplacement("#ff0000");compose.onNodeWithTag("appearance-color-chart").performImeAction()
        compose.onNodeWithTag("appearance-color-chart-dialog").assertDoesNotExist()
        compose.runOnIdle {assertEquals("#ff0000",prefs.value.appearance.colors["chart"])}
        save("colors")
        openSettingsSection(compose,"main");click("usage-currency-select");save("currency-menu","usage-currency-menu");compose.onNodeWithTag("usage-currency-CNY").performScrollTo().performClick();click("usage-rate-mode-manual")
        compose.onNodeWithTag("usage-currency-rate").assertTextEquals("6.8")
        click("usage-currency-rate");compose.onNodeWithTag("usage-currency-rate").performTextReplacement("7");compose.onNodeWithTag("usage-currency-rate").performImeAction()
        compose.runOnIdle {assertEquals("7",prefs.value.currencyRates["CNY"])}
        save("currency")
        click("usage-currency-rate");compose.onNodeWithTag("usage-currency-rate").performTextReplacement("");compose.onNodeWithTag("usage-currency-rate").performImeAction()
        compose.onNodeWithTag("usage-rate-mode-builtin").assertIsSelected();compose.onNodeWithTag("usage-currency-rate").assertDoesNotExist()
        compose.runOnIdle {assertFalse(prefs.value.currencyRates.containsKey("CNY"));assertEquals(0,actions.reads);assertEquals(1000L,state.snapshot?.periods?.get("today")?.tokens)}
    }
}
