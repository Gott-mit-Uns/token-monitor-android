package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class AppearanceScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private class Actions:HubUiActions {
        var reads=0
        override fun connect(address:String,credential:String,alternate:String) {}
        override fun refreshSaved() { reads++ }
        override fun switchEndpoint() {}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadHistory() { reads++ }
        override fun loadModelNames() { reads++ }
        override fun loadSubscriptions() { reads++ }
        override fun forget() {}
        override fun renameDevice(id:String,input:String) {}
    }
    private fun show(tag:String) { compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag)) }
    private fun click(tag:String) { show(tag);compose.onNodeWithTag(tag).performClick() }
    private fun layout(tag:String):TextLayoutResult {
        val result=mutableListOf<TextLayoutResult>();compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(result) };return result.single()
    }
    @Test fun presetsAndCustomChartUpdateActualThemeWithoutChangingHubData() {
        val dark=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
        // Font/layout/storage checks use a stable fixture, including production Activity recreation.
        // System.nanoTime animations cannot advance with the Compose virtual frame clock.
        val prefs=mutableStateOf(DisplayPreferences(theme=if(dark) ThemeChoice.DARK else ThemeChoice.LIGHT,
            appearance=AppearancePreferences(reduceMotion="on")))
        val today=LocalDate.now().toString()
        val snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":123456789,"costUsd":12.5}},"historyPreview":{"daily":[{"date":"$today","tokens":100,"cost":1}],"monthly":[]}}""")
        val state=HubState(snapshot=snapshot,savedAt=System.currentTimeMillis());val actions=Actions()
        var actualBg=Color.Unspecified;var actualChart=Color.Unspecified;var actualSuccess=Color.Unspecified
        compose.activity.runOnUiThread { compose.activity.setContent { TokenTheme(prefs.value.theme!=ThemeChoice.LIGHT,prefs.value.language,prefs.value.appearance) {
            val colors=MaterialTheme.colorScheme;val chart=LocalDesktopChartColor.current
            SideEffect { actualBg=colors.background;actualChart=chart;actualSuccess=colors.tertiary }
            HubScreen(state,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})
        } } }
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("123,456,789")
        compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"appearance")
        for(id in listOf("default","obsidian","porcelain")) {
            click("appearance-preset-$id");compose.onNodeWithTag("appearance-preset-$id").assertIsSelected()
            compose.runOnIdle { assertEquals(hexColor(DesktopAppearance.presets.getValue(id).getValue("bg")).toArgb(),actualBg.toArgb());assertEquals(Color(0xFF73BDF5).toArgb(),actualChart.toArgb()) }
            val pixels=compose.onRoot().captureToImage().toPixelMap()
            assertEquals(hexColor(DesktopAppearance.presets.getValue(id).getValue("bg")).toArgb(),pixels[0,pixels.height/2].toArgb())
        }
        click("appearance-preset-${if(dark) "default" else "porcelain"}")
        saveVisualEvidence(compose.activity,compose.onRoot(),"appearance-palette-${if(dark) "dark" else "light"}.png")
        click("appearance-colors-toggle");click("appearance-color-chart-open")
        compose.onNodeWithTag("appearance-color-chart").performTextReplacement("#ff0000");compose.onNodeWithTag("appearance-color-chart").performImeAction()
        compose.runOnIdle { assertEquals(Color.Red.toArgb(),actualChart.toArgb());assertEquals(if(dark) Color(0xFFB7EAD4).toArgb() else Color(0xFF18794E).toArgb(),actualSuccess.toArgb()) }
        compose.onNodeWithTag("appearance-theme-code-field").assertDoesNotExist()
        click("appearance-preset-porcelain")
        compose.runOnIdle { assertEquals(Color(0xFF73BDF5).toArgb(),actualChart.toArgb());assertEquals(ThemeChoice.LIGHT,prefs.value.theme);assertEquals(snapshot,state.snapshot);assertEquals(0,actions.reads) }
        compose.onNodeWithTag("hub-settings-toggle").performClick();compose.onNodeWithTag("hub-total-tokens").assertTextEquals("123,456,789")
        compose.runOnIdle { prefs.value=prefs.value.copy(theme=if(dark) ThemeChoice.DARK else ThemeChoice.LIGHT,appearance=prefs.value.appearance.copy(colors=mapOf("chart" to "#ff0000"))) }
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("home-trend-chart"))
        saveVisualEvidence(compose.activity,compose.onRoot(),"appearance-chart-${if(dark) "dark" else "light"}.png")
    }
    @Test fun textAndIndependentFontsKeepTotalSizeAndActualActivityRestoresAllChoices() {
        val dark=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
        // Font/layout/storage checks use a stable fixture, including production Activity recreation.
        // System.nanoTime animations cannot advance with the Compose virtual frame clock.
        val prefs=mutableStateOf(DisplayPreferences(theme=if(dark) ThemeChoice.DARK else ThemeChoice.LIGHT,
            appearance=AppearancePreferences(reduceMotion="on")))
        val state=HubState(snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":123456789,"costUsd":12.5}}}"""))
        val actions=Actions();var labelSize=0f
        compose.activity.runOnUiThread { compose.activity.setContent { TokenTheme(dark,prefs.value.language,prefs.value.appearance) { val size=MaterialTheme.typography.bodyMedium.fontSize.value;SideEffect {labelSize=size};HubScreen(state,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)}) } } }
        val initialTotal=layout("hub-total-tokens")
        val total=initialTotal.layoutInput.style.fontSize
        compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"appearance");click("appearance-colors-toggle");click("appearance-size-select");compose.onNodeWithTag("appearance-size-largest").performClick()
        // Isolate App text size from typeface changes: a new display face may need a different fitted size on a narrow screen.
        compose.onNodeWithTag("hub-settings-toggle").performClick()
        assertEquals(total,layout("hub-total-tokens").layoutInput.style.fontSize)
        compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"appearance")
        click("appearance-interface-select");compose.onNodeWithTag("appearance-interface-system").performClick();click("appearance-display-select");compose.onNodeWithTag("appearance-display-mono").performClick()
        compose.runOnIdle { assertEquals(16.8f,labelSize,0.001f) }
        val familyList="\"TokenMonitorMissingPublicFont, monospace\", serif"
        // Seed an existing stored font rather than exposing a new custom-font editor.
        compose.runOnIdle {prefs.value=prefs.value.copy(appearance=prefs.value.appearance.copy(interfaceFont=familyList))}
        show("appearance-interface-select");compose.onNodeWithTag("appearance-interface-custom-field").assertDoesNotExist()
        saveVisualEvidence(compose.activity,compose.onRoot(),"appearance-fonts-${if(dark) "dark" else "light"}.png")
        compose.onNodeWithTag("hub-settings-toggle").performClick()
        val fittedTotal=layout("hub-total-tokens")
        assertEquals(initialTotal.layoutInput.text,fittedTotal.layoutInput.text)
        assertFalse("Selected display face must fit the full total",fittedTotal.hasVisualOverflow)
        repeat(fittedTotal.lineCount) {assertFalse(fittedTotal.isLineEllipsized(it))}
        assertEquals(FontFamily.Monospace,fittedTotal.layoutInput.style.fontFamily)
        assertEquals(FontFamily.Serif,layout("hub-total-cost").layoutInput.style.fontFamily)
        compose.runOnIdle { assertEquals(familyList,prefs.value.appearance.interfaceFont);assertEquals(0,actions.reads) }
        // Return to production Activity composition and verify disk/state recreation, no synthetic connection.
        val store=DisplayPreferenceStore(compose.activity);val previous=store.load()
        try {
            val saved=prefs.value.copy(language=LanguageChoice.ENGLISH,currency="CNY",currencyRates=mapOf("CNY" to "7"),appearance=prefs.value.appearance.copy(colors=DesktopAppearance.presets.getValue("obsidian")))
            compose.activityRule.scenario.onActivity { ViewModelProvider(it)[HubViewModel::class.java].updateDisplay(saved) }
            compose.waitUntil(5000) { store.load()==saved };compose.activityRule.scenario.recreate()
            // Production restoration may legitimately land on Home when a cached Hub exists.
            // Establish Settings explicitly before checking the unchanged preference controls.
            compose.waitUntil(5000) { compose.onAllNodesWithTag("hub-startup").fetchSemanticsNodes().isEmpty() }
            val settingsInitiallyOpen=compose.onAllNodesWithTag("hub-settings-panel-motion").fetchSemanticsNodes().isNotEmpty()
            if (!settingsInitiallyOpen) compose.onNodeWithTag("hub-settings-toggle").performClick()
            val restoredGeneral=compose.onNodeWithTag("settings-general-toggle")
            val generalInitiallyComposed=compose.onAllNodesWithTag("settings-general-toggle").fetchSemanticsNodes().isNotEmpty()
            val restoredBounds=if(generalInitiallyComposed) restoredGeneral.getUnclippedBoundsInRoot() else null
            val restoredViewport=compose.onNodeWithTag("hub-content").getUnclippedBoundsInRoot()
            java.io.File(compose.activity.getExternalFilesDir(null),"appearance-restored-general-${if(dark) "dark" else "light"}.txt").writeText("settingsInitiallyOpen=$settingsInitiallyOpen generalInitiallyComposed=$generalInitiallyComposed general=$restoredBounds viewport=$restoredViewport systemFontScale=${compose.activity.resources.configuration.fontScale}\n")
            saveVisualEvidence(compose.activity,compose.onRoot(),"appearance-restored-before-general-scroll-${if(dark) "dark" else "light"}.png")
            // Recreation preserves the actual list position; bring the intended control into view before asserting it.
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("settings-general-toggle"))
            restoredGeneral.assertIsDisplayed()
            openSettingsSection(compose,"appearance");click("appearance-colors-toggle");click("appearance-size-select");compose.onNodeWithTag("appearance-size-largest").assertIsSelected();compose.onNodeWithTag("appearance-size-largest").performClick()
            show("appearance-interface-select");compose.onNodeWithTag("appearance-interface-value",useUnmergedTree=true).assertTextEquals("Saved custom font")
            compose.onNodeWithTag("appearance-interface-custom-field").assertDoesNotExist()
            click("appearance-display-select");compose.onNodeWithTag("appearance-display-mono").assertIsSelected();compose.onNodeWithTag("appearance-display-mono").performClick()
            assertEquals(saved,store.load())
        } finally { store.save(previous) }
    }
}
