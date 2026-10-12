package dev.bing.tokenmonitor

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Real production Activity/client; inspect values in device memory, never archive them. */
class LiveToolDetailsTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun actualFooterModesMatchRealToolSharesAndStopAllReadsInBackground() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val port=InstrumentationRegistry.getArguments().getString("liveHubPort")!!.toInt()
        val store=HubStore(context);assertNull(store.load())
        lateinit var model: HubViewModel
        compose.activityRule.scenario.onActivity { model=ViewModelProvider(it)[HubViewModel::class.java] }
        fun await(condition: () -> Boolean) {
            val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(25)
            while(!condition() && System.nanoTime()<until) { compose.waitForIdle();Thread.sleep(30) }
            assertTrue("Real tool detail condition unavailable; private values withheld",condition())
        }
        fun stage(value: String)=instrumentation.sendStatus(2,Bundle().apply { putString("nativeLiveStage",value) })
        fun focused()=compose.activityRule.scenario.state==Lifecycle.State.RESUMED && run { var focus=false;compose.activityRule.scenario.onActivity { focus=it.hasWindowFocus() };focus }
        fun text(tag: String, unmerged: Boolean=false)=compose.onAllNodesWithTag(tag,useUnmergedTree=unmerged).fetchSemanticsNodes().firstOrNull()?.config
            ?.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
        lateinit var client: String
        val numbers=NumberFormat.getIntegerInstance(Locale.CHINESE)
        fun projected()=ToolDetails.project(model.state.value.snapshot!!.periods["today"]!!.toolDetails[client],model.state.value.modelNames)
        fun retained() {
            assertTrue("Rendered tool modes do not rewrite the encrypted source",HubParser.parse(store.load()!!.snapshot)==model.state.value.snapshot)
            assertTrue("Retained history is unchanged by tool modes",HistoryParser.parse(store.load()!!.history!!)==model.state.value.history)
        }
        fun verify() {
            compose.onNodeWithTag("tool-detail-mode-tokens").performClick().assertIsSelected()
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("tool-detail-tool-$client-hit"))
            await {
                val hit=projected().tokens.first { it.key=="hit" }
                text("tool-detail-tool-$client-hit-value")==hit.tokens?.let(numbers::format) && text("tool-detail-tool-$client-hit-percent")==hit.percent
            }
            compose.onNodeWithTag("tool-detail-mode-models").performClick().assertIsSelected()
            val row=projected().models.first { (it.usage.tokens ?: 0)>0 }
            val identity=row.key+if(row.unattributed) ":remainder" else ":model"
            val tag="tool-detail-tool-$client-model-$identity"
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))
            await {
                val current=projected().models.firstOrNull { it.key==row.key && it.unattributed==row.unattributed } ?: return@await false
                text("$tag-value",true)==current.usage.tokens?.let(numbers::format) && text("$tag-percent",true)==current.percent
            }
            retained()
        }
        try {
            await { !model.state.value.loading && focused() }
            compose.activityRule.scenario.onActivity { model.connect("http://10.0.2.2:$port",UUID.randomUUID().toString(),"") }
            await { model.state.value.live && model.state.value.history!=null && !model.state.value.loading && store.load()?.history!=null }
            stage("sustain")
            client=model.state.value.snapshot!!.periods.getValue("today").toolDetails.entries.firstOrNull {
                (it.value.tokens ?: 0)>0 && it.value.components.inputMiss!=null && ToolDetails.project(it.value).canSwitch
            }?.key ?: error("Real classified tool/model data unavailable; values withheld")
            compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-tools").performScrollTo().performClick()
            await { model.state.value.modelNames!=null && !model.state.value.loading && store.load()?.modelNames!=null }
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("breakdown-tool-$client"))
            compose.onNodeWithTag("breakdown-tool-$client").performSemanticsAction(SemanticsActions.OnClick) { it() }
            verify();stage("details_verified")
            assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
            await { !model.state.value.live && compose.activityRule.scenario.state!=Lifecycle.State.RESUMED }
            stage("background");Thread.sleep(6000)
            assertNotNull(store.load()?.modelNames)
            context.startActivity(Intent(Intent.ACTION_MAIN).setClass(context,MainActivity::class.java).addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            await { model.state.value.live && focused() && !model.state.value.loading }
            compose.onNodeWithTag("tool-detail-mode-models").assertIsSelected();verify();stage("resumed")
        } finally {
            instrumentation.runOnMainSync { model.setForeground(false);model.forget() }
            await { !model.state.value.loading };store.clear()
        }
    }
}
