package dev.bing.tokenmonitor

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Actual production controls. Private values and any captured pixels stay in device memory. */
class LiveVendorTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun realVendorAndVisibilityControlsKeepSseSourcesAndRestoreAfterHome() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val port=InstrumentationRegistry.getArguments().getString("liveHubPort")!!.toInt();val store=HubStore(context)
        assertNull(store.load());lateinit var model:HubViewModel
        compose.activityRule.scenario.onActivity {model=ViewModelProvider(it)[HubViewModel::class.java]}
        fun await(condition:()->Boolean) {
            val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(25)
            while(!condition() && System.nanoTime()<until) {compose.waitForIdle();Thread.sleep(30)}
            assertTrue("Real vendor condition unavailable; private diagnostics withheld",condition())
        }
        fun stage(value:String)=instrumentation.sendStatus(2,Bundle().apply {putString("nativeLiveStage",value)})
        fun focused()=compose.activityRule.scenario.state==Lifecycle.State.RESUMED && run {var focus=false;compose.activityRule.scenario.onActivity {focus=it.hasWindowFocus()};focus}
        fun show(tag:String)=compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))
        fun click(tag:String) {show(tag);compose.onNodeWithTag(tag).performClick()}
        fun verify() {
            await {store.load()?.let {saved -> HubParser.parse(saved.snapshot)==model.state.value.snapshot && saved.history?.let(HistoryParser::parse)==model.state.value.history && saved.modelNames?.let(ModelNamesParser::parse)==model.state.value.modelNames}==true}
            val prefs=model.display.value
            assertTrue("Local choices retained independently",prefs.appearance.vendorColors["codex"]=="#ff0000" && !prefs.appearance.showToolIcons && !prefs.appearance.showLiveDot && DisplayPreferenceStore(context).load()==prefs)
            compose.onNodeWithTag("hub-live-dot",useUnmergedTree=true).assertDoesNotExist()
            assertTrue("Hiding connection dot leaves foreground SSE live",model.state.value.live)
            val rows=model.state.value.snapshot?.periods?.get("today")?.tools.orEmpty()
            val value=rows["codex"] ?: 0L;val maximum=rows.values.maxOrNull() ?: 1L
            if(value>0 && value.toDouble()/maximum.coerceAtLeast(1)>0.01) {
                show("breakdown-color-tool-codex")
                val pixels=compose.onNodeWithTag("breakdown-color-tool-codex").captureToImage().toPixelMap()
                assertTrue("Existing real Codex bar uses configured RGB",(0 until pixels.width).any {x -> pixels[x,pixels.height/2].toArgb()==Color.Red.toArgb()})
                compose.onNodeWithTag("vendor-dot-codex",useUnmergedTree=true).assertExists()
                compose.onNodeWithTag("vendor-icon-codex",useUnmergedTree=true).assertDoesNotExist()
            }
        }
        try {
            await {!model.state.value.loading && focused()}
            compose.activityRule.scenario.onActivity {model.connect("http://10.0.2.2:$port",UUID.randomUUID().toString(),"")}
            await {model.state.value.live && model.state.value.history!=null && !model.state.value.loading && store.load()?.history!=null};stage("sustain")
            compose.onNodeWithTag("hub-settings-toggle").performClick();click("appearance-toggle");click("vendor-colors-toggle");show("vendor-color-list");compose.onNodeWithTag("vendor-color-list").performScrollToNode(hasTestTag("vendor-color-codex-open"));compose.onNodeWithTag("vendor-color-codex-open").performClick()
            compose.onNodeWithTag("vendor-color-codex").performTextReplacement("#ff0000");compose.onNodeWithTag("vendor-color-codex").performImeAction()
            click("appearance-tool-icons");click("appearance-live-dot")
            await {model.display.value.appearance.let {it.vendorColors["codex"]=="#ff0000" && !it.showToolIcons && !it.showLiveDot}}
            compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-tools").performScrollTo().performClick()
            await {model.state.value.modelNames!=null && !model.state.value.loading && store.load()?.modelNames!=null};verify();stage("vendor_verified")
            assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
            await {!model.state.value.live && compose.activityRule.scenario.state!=Lifecycle.State.RESUMED};stage("background");Thread.sleep(6000)
            assertTrue(!DisplayPreferenceStore(context).load().appearance.showLiveDot)
            context.startActivity(Intent(Intent.ACTION_MAIN).setClass(context,MainActivity::class.java).addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            await {model.state.value.live && focused() && !model.state.value.loading};verify();stage("resumed")
        } finally {
            instrumentation.runOnMainSync {model.setForeground(false);model.forget()};await {!model.state.value.loading};store.clear()
        }
    }
}
