package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class AppearancePreviewScreenTest {
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
    private fun source(tokens:Long)=HubState(snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":$tokens,"costUsd":1}},"devices":[]}"""),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"))
    private fun click(tag:String) {compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performClick()}
    private fun color(tag:String)=compose.onNodeWithTag(tag).captureToImage().toPixelMap().let {it[1,1].toArgb()}
    private fun save(page:String,dialog:String?=null)=saveVisualEvidence(compose.activity,if(dialog==null) compose.onNodeWithTag("hub-content") else compose.onNodeWithTag(dialog),"preview-$page-${if(dark) "dark" else "light"}.png")
    @Test fun actualPagePreviewKeepsDialogReadableAndSavedBytesUntilConfirm() {
        val name="preview-${UUID.randomUUID()}.bin";val store=DisplayPreferenceStore(compose.activity,name)
        val baseline=DisplayPreferences(theme=if(dark) ThemeChoice.DARK else ThemeChoice.LIGHT,language=LanguageChoice.ENGLISH,appearance=AppearancePreferences(colors=DesktopAppearance.presets.getValue(if(dark) "default" else "porcelain")))
        val prefs=mutableStateOf(baseline);val state=mutableStateOf(source(1000));val actions=Actions();var commits=0
        try {
            store.save(baseline);val original=File(compose.activity.noBackupFilesDir,name).readBytes()
            compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(state.value,actions,preferences=prefs.value,updateDisplay={commits++;prefs.value=it.apply(prefs.value);store.save(prefs.value)})}}}
            compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"appearance");click("appearance-colors-toggle")
            click("appearance-color-bg-open");compose.onNodeWithTag("appearance-color-bg").performTextReplacement("#ffffff")
            compose.waitForIdle();assertEquals(Color.White.toArgb(),color("hub-content"));save("page");save("dialog","appearance-color-bg-dialog")
            val layout=mutableListOf<TextLayoutResult>();compose.onNodeWithTag("appearance-color-bg-title").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layout)}
            assertEquals(hexColor(baseline.appearance.colors.getValue("text")),layout.single().layoutInput.style.color)
            assertEquals(baseline,store.load());assertArrayEquals(original,File(compose.activity.noBackupFilesDir,name).readBytes());assertEquals(0,commits)
            compose.onNodeWithTag("appearance-color-bg-R").performSemanticsAction(SemanticsActions.SetProgress) {it(18f)}
            compose.waitForIdle();assertEquals(Color(0xFF12FFFF).toArgb(),color("hub-content"))
            compose.onNodeWithTag("appearance-color-bg").performTextReplacement("invalid")
            compose.onNodeWithTag("appearance-color-bg-apply").assertIsNotEnabled();assertEquals(Color(0xFF12FFFF).toArgb(),color("hub-content"))
            compose.runOnIdle {state.value=source(5000)}
            compose.onNodeWithTag("appearance-color-bg-cancel").performScrollTo().performClick()
            compose.waitForIdle();assertEquals(hexColor(baseline.appearance.colors.getValue("bg")).toArgb(),color("hub-content"));save("cancel")
            assertArrayEquals(original,File(compose.activity.noBackupFilesDir,name).readBytes());assertEquals(0,commits)
            click("appearance-color-bg-open");compose.onNodeWithTag("appearance-color-bg").performTextReplacement("#132435")
            compose.onNodeWithTag("appearance-color-bg-apply").performScrollTo().performClick()
            compose.waitForIdle();assertEquals("#132435",store.load().appearance.colors["bg"]);assertEquals(1,commits);save("applied")
            compose.onNodeWithTag("hub-settings-toggle").performClick();compose.onNodeWithTag("hub-total-tokens").assertTextEquals("5,000");assertEquals(0,actions.reads)
        } finally {store.clear()}
    }
    @Test fun vendorPreviewDoesNotCommitAndBackgroundDiscardsTheOpenDraft() {
        val name="vendor-preview-${UUID.randomUUID()}.bin";val store=DisplayPreferenceStore(compose.activity,name)
        val baseline=DisplayPreferences(theme=if(dark) ThemeChoice.DARK else ThemeChoice.LIGHT,language=LanguageChoice.ENGLISH)
        val prefs=mutableStateOf(baseline);val actions=Actions();var commits=0
        try {
            store.save(baseline);val original=File(compose.activity.noBackupFilesDir,name).readBytes()
            compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(source(1000),actions,preferences=prefs.value,updateDisplay={commits++;prefs.value=it.apply(prefs.value);store.save(prefs.value)})}}}
            compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"appearance");click("vendor-colors-toggle")
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("vendor-color-list"));compose.onNodeWithTag("vendor-color-list").performScrollToNode(hasTestTag("vendor-color-codex-open"));compose.onNodeWithTag("vendor-color-codex-open").performClick()
            compose.onNodeWithTag("vendor-color-codex").performTextReplacement("#ff0000");compose.waitForIdle();save("vendor","vendor-color-codex-dialog")
            assertArrayEquals(original,File(compose.activity.noBackupFilesDir,name).readBytes());assertEquals(0,commits)
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED);compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitForIdle();compose.onNodeWithTag("vendor-color-codex-dialog").assertDoesNotExist();assertEquals(baseline,store.load())
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("vendor-color-list"));compose.onNodeWithTag("vendor-color-list").performScrollToNode(hasTestTag("vendor-color-codex-open"));compose.onNodeWithTag("vendor-color-codex-open").performClick()
            compose.onNodeWithTag("vendor-color-codex").assertTextEquals("#"+(desktopVendorColors.getValue("codex") and 0xFFFFFF).toString(16).padStart(6,'0'))
            compose.onNodeWithTag("vendor-color-codex-cancel").performScrollTo().performClick();assertEquals(0,commits);assertEquals(0,actions.reads)
        } finally {store.clear()}
    }
}
