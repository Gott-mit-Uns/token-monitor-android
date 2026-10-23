package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.UUID

class ThemeSemanticScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private class Actions:HubUiActions {
        var reads=0
        override fun connect(address:String,credential:String,alternate:String) {reads++}
        override fun refreshSaved() {reads++}
        override fun switchEndpoint() {reads++}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadHistory() {reads++}
        override fun loadModelNames() {reads++}
        override fun loadSubscriptions() {reads++}
        override fun forget() {}
        override fun renameDevice(id:String,input:String) {}
    }
    private fun preferences(theme:ThemeChoice=ThemeChoice.DARK)=DisplayPreferences(theme=theme,language=LanguageChoice.ENGLISH,
        homeModules=listOf("activity"),appearance=AppearancePreferences(reduceMotion="on"))
    private fun source():HubState {
        val history=HubHistory(listOf(HistoryRow(LocalDate.now().toString(),1423L,"1")),emptyList())
        return HubState(snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":123456789,"costUsd":1}},"devices":[]}"""),
            history=history,modelNames=ModelNames(emptyMap(),"off"))
    }
    private fun host(prefs:MutableState<DisplayPreferences>,actions:Actions,store:DisplayPreferenceStore?=null,systemDark:Boolean=true) {
        val state=source()
        compose.activityRule.scenario.onActivity {activity->activity.setContent {
            val current=prefs.value
            val dark=when(current.theme) {ThemeChoice.DARK->true;ThemeChoice.LIGHT->false;ThemeChoice.SYSTEM->systemDark}
            TokenTheme(dark,current.language,current.appearance) {
                HubScreen(state,actions,preferences=current,updateDisplay={change->
                    prefs.value=change.apply(prefs.value);store?.save(prefs.value)
                })
            }
        }}
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("123,456,789")
    }
    private fun layout(tag:String,unmerged:Boolean=false):TextLayoutResult {
        val values=mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag,useUnmergedTree=unmerged).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(values)}
        return values.single()
    }
    private fun assertHeadline(color:Color) {
        compose.waitForIdle()
        assertEquals(color.toArgb(),layout("hub-total-tokens").layoutInput.style.color.toArgb())
        // Verify pixels from the actual Text, rather than only observing a theme helper/local.
        val pixels=compose.onNodeWithTag("hub-total-tokens").captureToImage().toPixelMap()
        var matches=0
        for(y in 0 until pixels.height) for(x in 0 until pixels.width) if(pixels[x,y].toArgb()==color.toArgb()) matches++
        assertTrue("The actual headline glyphs must paint the semantic number colour",matches>5)
    }
    private fun click(tag:String) {
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }
    private fun openAppearance() {
        compose.onNodeWithTag("hub-settings-toggle").performClick()
        openSettingsSection(compose,"appearance")
    }
    private fun closeSettings()=compose.onNodeWithTag("hub-settings-toggle").performClick()
    /**
     * Opens the advanced colour rows. The desktop never collapses the advanced disclosure when the
     * settings pane is hidden again (setupSettingsAccordion only toggles on a real click), so a
     * second close/reopen must still find it expanded instead of collapsing it from the test.
     */
    private fun openColorRows(expectPersistedOpen:Boolean=false) {
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("appearance-colors-toggle"))
        val expanded=compose.onAllNodesWithTag("appearance-color-text-open").fetchSemanticsNodes().isNotEmpty()
        if(expectPersistedOpen) assertTrue("advanced customization must stay expanded as the desktop does",expanded)
        if(!expanded) click("appearance-colors-toggle")
    }
    private fun applyExplicitDefaultText() {
        openColorRows()
        click("appearance-color-text-open")
        compose.onNodeWithTag("appearance-color-text").performTextReplacement("#eef5fb")
        compose.onNodeWithTag("appearance-color-text").performImeAction()
    }

    @Test fun actualHeadlineDistinguishesGraphiteAndImportedDefaultAndActivityCountHasIndependentInk() {
        val prefs=mutableStateOf(preferences());val actions=Actions()
        host(prefs,actions)
        assertHeadline(Color(0xFFF3FBF7))
        compose.runOnIdle {
            prefs.value=DisplayChange.ThemeColor("accent","#ff0000").apply(prefs.value)
            prefs.value=DisplayChange.ThemeColor("chart","#00ff00").apply(prefs.value)
        }
        assertHeadline(Color(0xFFF3FBF7))
        for ((preset,color) in listOf("default" to Color(0xFFF3FBF7),"obsidian" to Color(0xFFECEEF2),"porcelain" to Color(0xFF1C1F26),"default" to Color(0xFFF3FBF7))) {
            openAppearance();click("appearance-preset-$preset");closeSettings();assertHeadline(color)
        }
        assertTrue(prefs.value.appearance.colors.isEmpty())
        openAppearance();applyExplicitDefaultText();closeSettings()
        assertTrue(prefs.value.appearance.colors.containsKey("text"))
        assertHeadline(Color(0xFFEEF5FB))
        openAppearance();click("appearance-reset-colors");closeSettings();assertHeadline(Color(0xFFF3FBF7))

        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("home-activity-day-open"))
        compose.onNodeWithTag("home-activity-day-open").performClick()
        // Home patches today's history with the current period, as the desktop does.
        compose.onNodeWithTag("home-activity-day-tokens").assertTextEquals("123,456,789 Token")
        val text=compose.onNodeWithTag("home-activity-day-tokens").fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertEquals(2,text.spanStyles.size)
        assertEquals("123,456,789",text.text.substring(text.spanStyles[0].start,text.spanStyles[0].end))
        assertEquals(Color(0xFFF3FBF7),text.spanStyles[0].item.color)
        assertEquals(" Token",text.text.substring(text.spanStyles[1].start,text.spanStyles[1].end))
        assertEquals(Color(0xFFA3ADBB),text.spanStyles[1].item.color)
        assertEquals(Color(0xFFA3ADBB),layout("home-activity-selected-date").layoutInput.style.color)
        compose.onNodeWithText("Close").performClick()
        assertEquals(0,actions.reads)
    }

    @Test fun realTextPickerPreviewsCancelsAppliesAndPersistedDefaultPresenceSurvivesActivityRecreation() {
        val name="theme-semantic-${UUID.randomUUID()}.bin"
        val store=DisplayPreferenceStore(compose.activity,name)
        val prefs=mutableStateOf(preferences());val actions=Actions()
        try {
            store.save(prefs.value)
            val baseline=File(compose.activity.noBackupFilesDir,name).readBytes()
            host(prefs,actions,store)
            openAppearance();openColorRows();click("appearance-color-text-open")
            compose.onNodeWithTag("appearance-color-text").performTextReplacement("#abcdef")
            compose.waitForIdle()
            // The modal owns its visible preview sample. A background page swatch
            // can be clipped by imePadding or obscured by a different Window.
            // Also inspect the real page field's rendered text style to prove that
            // preview reached the page theme, rather than only the modal's draft.
            assertEquals(Color(0xFFABCDEF),layout("appearance-interface-value",unmerged=true).layoutInput.style.color)
            val previewNode=compose.onNodeWithTag("appearance-color-text-preview").performScrollTo().assertIsDisplayed()
            val swatch=previewNode.captureToImage().toPixelMap()
            assertEquals(Color(0xFFABCDEF).toArgb(),swatch[swatch.width/2,swatch.height/2].toArgb())
            assertFalse(prefs.value.appearance.colors.containsKey("text"))
            assertArrayEquals(baseline,File(compose.activity.noBackupFilesDir,name).readBytes())
            compose.onNodeWithTag("appearance-color-text-cancel").performScrollTo().performClick()
            closeSettings();assertHeadline(Color(0xFFF3FBF7))
            assertArrayEquals(baseline,File(compose.activity.noBackupFilesDir,name).readBytes())

            openAppearance();openColorRows(expectPersistedOpen=true);click("appearance-color-text-open")
            compose.onNodeWithTag("appearance-color-text").performTextReplacement("#abcdef")
            compose.onNodeWithTag("appearance-color-text-apply").performScrollTo().performClick()
            closeSettings();assertHeadline(Color(0xFFABCDEF))
            assertEquals("#abcdef",store.load().appearance.colors["text"])
            openAppearance();openColorRows(expectPersistedOpen=true);click("appearance-color-text-reset");closeSettings();assertHeadline(Color(0xFFF3FBF7))
            openAppearance();applyExplicitDefaultText();closeSettings();assertHeadline(Color(0xFFEEF5FB))

            val imported=store.load()
            assertTrue(imported.appearance.colors.containsKey("text"))
            compose.activityRule.scenario.recreate()
            val restored=DisplayPreferenceStore(compose.activity,name).load()
            assertEquals(imported,restored)
            compose.runOnIdle {prefs.value=restored}
            host(prefs,actions,store);assertHeadline(Color(0xFFEEF5FB))
            openAppearance();click("appearance-reset-colors");closeSettings();assertHeadline(Color(0xFFF3FBF7))
            assertTrue(store.load().appearance.colors.isEmpty())
            assertEquals(0,actions.reads)
        } finally {store.clear()}
    }

    @Test fun actualModeControlsKeepLightBaseReadableAndRestoreSavedModeWithoutHubReads() {
        val name="theme-mode-${UUID.randomUUID()}.bin"
        val store=DisplayPreferenceStore(compose.activity,name)
        val prefs=mutableStateOf(preferences(ThemeChoice.SYSTEM));val actions=Actions()
        try {
            store.save(prefs.value)
            host(prefs,actions,store,systemDark=false)
            assertHeadline(Color(0xFF1C1F26))
            openAppearance();click("settings-theme-DARK");closeSettings();assertHeadline(Color(0xFFF3FBF7))
            openAppearance();click("settings-theme-LIGHT");closeSettings();assertHeadline(Color(0xFF1C1F26))
            assertEquals(ThemeChoice.LIGHT,store.load().theme)
            compose.activityRule.scenario.recreate()
            val restored=DisplayPreferenceStore(compose.activity,name).load()
            assertEquals(ThemeChoice.LIGHT,restored.theme)
            compose.runOnIdle {prefs.value=restored}
            host(prefs,actions,store,systemDark=true)
            assertHeadline(Color(0xFF1C1F26))
            openAppearance();click("settings-theme-SYSTEM");closeSettings();assertHeadline(Color(0xFFF3FBF7))
            assertEquals(ThemeChoice.SYSTEM,store.load().theme)
            assertEquals(0,actions.reads)
        } finally {store.clear()}
    }
}
