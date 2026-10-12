package dev.bing.tokenmonitor

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Production controls/client. Private data and screen pixels stay in device memory. */
class LiveAppearanceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun actualAppearanceControlsKeepRealHubSourcesAndRestoreAfterBackground() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val port=InstrumentationRegistry.getArguments().getString("liveHubPort")!!.toInt();val store=HubStore(context)
        assertNull(store.load());lateinit var model:HubViewModel
        compose.activityRule.scenario.onActivity { model=ViewModelProvider(it)[HubViewModel::class.java] }
        fun await(condition:()->Boolean) {
            val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(25)
            while(System.nanoTime()<until) {compose.waitForIdle();if(condition()) return;Thread.sleep(30)}
            throw AssertionError("Real appearance condition unavailable; private values withheld")
        }
        fun stage(value:String)=instrumentation.sendStatus(2,Bundle().apply {putString("nativeLiveStage",value)})
        fun focused()=compose.activityRule.scenario.state==Lifecycle.State.RESUMED && run {var focus=false;compose.activityRule.scenario.onActivity {focus=it.hasWindowFocus()};focus}
        fun click(tag:String) {compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performClick()}
        fun verify() {
            await {
                val value=model.state.value.snapshot?.periods?.get("today")?.tokens ?: return@await false
                val text=compose.onAllNodesWithTag("hub-total-tokens").fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("") {it.text}
                text==NumberFormat.getIntegerInstance(Locale.CHINESE).format(value)
            }
            val layout=mutableListOf<TextLayoutResult>();compose.onNodeWithTag("hub-total-tokens").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layout)}
            assertTrue("Display font is independently applied",layout.single().layoutInput.style.fontFamily==FontFamily.Monospace)
            val pixels=compose.onRoot().captureToImage().toPixelMap()
            assertTrue("Actual background uses selected palette",pixels[0,pixels.height/2].toArgb()==Color(0xFF0B0C0E).toArgb())
            await {store.load()?.let {saved -> HubParser.parse(saved.snapshot)==model.state.value.snapshot && saved.history?.let(HistoryParser::parse)==model.state.value.history}==true}
            assertTrue("Appearance persists without connection changes",DisplayPreferenceStore(context).load().appearance==model.display.value.appearance)
            assertTrue("Session titles/context survive foreground return",!DisplayPreferenceStore(context).load().sessionTitlesEnabled && DisplayPreferenceStore(context).load().main.sessionContextMetric=="remaining")
        }
        try {
            await {!model.state.value.loading && focused()}
            compose.activityRule.scenario.onActivity {model.connect("http://10.0.2.2:$port",UUID.randomUUID().toString(),"")}
            await {model.state.value.live && model.state.value.history!=null && !model.state.value.loading && store.load()?.history!=null}
            stage("sustain");compose.onNodeWithTag("hub-settings-toggle").performClick();click("appearance-toggle")
            click("appearance-preset-obsidian");click("appearance-colors-toggle");click("appearance-size-select");compose.onNodeWithTag("appearance-size-largest").performClick();click("appearance-interface-select");compose.onNodeWithTag("appearance-interface-system").performClick();click("appearance-display-select");compose.onNodeWithTag("appearance-display-mono").performClick()
            await {DisplayPreferenceStore(context).load()==model.display.value}
            val beforePreview=DisplayPreferenceStore(context).load()
            val preferenceBytes=java.io.File(context.noBackupFilesDir,"display-preferences.bin").readBytes()
            click("appearance-color-bg-open");compose.onNodeWithTag("appearance-color-bg").performTextReplacement("#ffffff")
            compose.waitForIdle()
            val pagePreview=compose.onNodeWithTag("hub-content").captureToImage().toPixelMap()
            assertTrue("Whole page receives live colour preview",pagePreview[1,1].toArgb()==Color.White.toArgb())
            assertTrue("Preview leaves model and persisted preferences intact",model.display.value==beforePreview && DisplayPreferenceStore(context).load()==beforePreview && preferenceBytes.contentEquals(java.io.File(context.noBackupFilesDir,"display-preferences.bin").readBytes()))
            assertTrue("SSE stays live during preview",model.state.value.live)
            compose.onNodeWithTag("appearance-color-bg-cancel").performScrollTo().performClick()
            compose.waitForIdle()
            val canceled=compose.onNodeWithTag("hub-content").captureToImage().toPixelMap()
            assertTrue("Cancel restores the page and leaves saved preferences intact",canceled[1,1].toArgb()==Color(0xFF0B0C0E).toArgb() && model.display.value==beforePreview)
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("appearance-color-chart-open"));compose.onNodeWithTag("appearance-color-chart-open").performClick()
            compose.onNodeWithTag("appearance-color-chart").performTextReplacement("#ff0000");compose.onNodeWithTag("appearance-color-chart").performImeAction()
            await {model.display.value.appearance.let {it.textSize=="largest" && it.colors["bg"]=="#0b0c0e" && it.colors["chart"]=="#ff0000" && it.interfaceFont==DesktopAppearance.systemFont && it.displayFont==DesktopAppearance.defaultInterfaceFont}}
            await {compose.onAllNodesWithTag("appearance-color-chart-dialog").fetchSemanticsNodes().isEmpty() && focused()}
            openSettingsSection(compose,"main");click("main-view-sessions-settings")
            compose.onNodeWithTag("session-titles").assertIsOn();click("session-titles");click("session-context-remaining")
            await {DisplayPreferenceStore(context).load()==model.display.value}
            assertTrue("Session display choices are local and persisted",!model.display.value.sessionTitlesEnabled && model.display.value.main.sessionContextMetric=="remaining")
            assertTrue("Session configuration leaves the foreground stream live",model.state.value.live)
            compose.onNodeWithTag("hub-settings-toggle").performClick()
            verify()
            stage("appearance_verified")
            compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"appearance");click("appearance-colors-toggle")
            click("appearance-color-bg-open");compose.onNodeWithTag("appearance-color-bg").performTextReplacement("#ffffff")
            compose.waitForIdle();assertTrue("Background draft is not saved",model.display.value.appearance.colors["bg"]=="#0b0c0e")
            assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
            await {!model.state.value.live && compose.activityRule.scenario.state!=Lifecycle.State.RESUMED};stage("background");Thread.sleep(6000)
            assertTrue("Background retains local appearance",DisplayPreferenceStore(context).load().appearance.colors["chart"]=="#ff0000")
            context.startActivity(Intent(Intent.ACTION_MAIN).setClass(context,MainActivity::class.java).addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            await {model.state.value.live && focused() && !model.state.value.loading}
            compose.onNodeWithTag("appearance-color-bg-dialog").assertDoesNotExist()
            compose.onNodeWithTag("hub-settings-toggle").performClick();verify();stage("resumed")
        } finally {
            instrumentation.runOnMainSync {model.setForeground(false);model.forget()};await {!model.state.value.loading};store.clear()
        }
    }
}
