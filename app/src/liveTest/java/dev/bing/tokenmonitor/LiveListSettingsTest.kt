package dev.bing.tokenmonitor

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.text.NumberFormat
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*

/** Actual controls and client; all private source comparisons stay in device memory. */
class LiveListSettingsTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun realToolAndActivityControlsKeepRawSourcesAndRestoreAfterBackground() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val port=InstrumentationRegistry.getArguments().getString("liveHubPort")!!.toInt();val store=HubStore(context)
        assertNull(store.load());lateinit var model:HubViewModel
        compose.activityRule.scenario.onActivity {model=ViewModelProvider(it)[HubViewModel::class.java]}
        val revisionChanges=AtomicInteger();val observation=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val historyObserver=observation.launch {
            var previous:String?=null
            model.state.collect {state -> state.snapshot?.historyRevision?.let {next ->
                if(previous!=null && previous!=next) revisionChanges.incrementAndGet()
                previous=next
            }}
        }
        fun await(condition:()->Boolean) {
            val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(25)
            while(!condition() && System.nanoTime()<until) {compose.waitForIdle();Thread.sleep(30)}
            assertTrue("List settings condition unavailable; private diagnostics withheld",condition())
        }
        fun stage(value:String)=instrumentation.sendStatus(2,Bundle().apply {putString("nativeLiveStage",value)})
        fun focused()=compose.activityRule.scenario.state==Lifecycle.State.RESUMED && run {var focus=false;compose.activityRule.scenario.onActivity {focus=it.hasWindowFocus()};focus}
        fun click(tag:String) {compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performClick()}
        fun verify() {
            await {
                val current=model.state.value;val saved=store.load() ?: return@await false
                val total=current.snapshot?.periods?.get("today")?.tokens ?: return@await false
                val text=compose.onAllNodesWithTag("hub-total-tokens").fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("") {it.text}
                text==NumberFormat.getIntegerInstance(context.resources.configuration.locales[0]).format(total) && HubParser.parse(saved.snapshot)==current.snapshot && saved.history?.let(HistoryParser::parse)==current.history
            }
            val prefs=DisplayPreferenceStore(context).load()
            assertTrue("Local selection survives without rewriting Hub source",prefs==model.display.value && "hermes" in prefs.pinnedTools && "claude" !in prefs.hiddenTools && prefs.heatmapMetric=="tokens" && prefs.homeActiveDaysWindow=="year" && prefs.homeModuleOrder==listOf("limits","models","tools","sessions","devices","activity") && "devices" !in prefs.homeModules)
        }
        try {
            await {!model.state.value.loading && focused()}
            compose.activityRule.scenario.onActivity {model.connect("http://10.0.2.2:$port",UUID.randomUUID().toString(),"")}
            await {model.state.value.live && model.state.value.history!=null && !model.state.value.loading && store.load()?.history!=null}
            stage("sustain");compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"tools")
            click("tool-pin-hermes");click("tool-visible-claude")
            await {"hermes" in model.display.value.pinnedTools && "claude" in model.display.value.hiddenTools}
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("tool-list-search"));compose.onNodeWithTag("tool-list-search").performTextReplacement(" code CLA ")
            compose.onNodeWithTag("tool-setting-claude").assertExists();compose.onNodeWithTag("tool-setting-codex").assertDoesNotExist();compose.onNodeWithTag("tool-order-claude").assertIsNotEnabled()
            click("tool-list-search-clear");compose.onNodeWithTag("tool-list-search").performImeAction();click("tool-list-show-all")
            openSettingsSection(compose,"main");click("main-view-home-settings")
            val beforeOrder=HomeModulePreferences.order(model.display.value)
            click("home-module-order-tools");compose.onNodeWithTag("home-module-down-tools").performClick()
            await {model.display.value.homeModuleOrder.isNotEmpty() && model.display.value.homeModuleOrder!=beforeOrder && "tools" !in model.display.value.homeModules}
            val chosenOrder=model.display.value.homeModuleOrder
            click("home-modules-show-all")
            await {model.display.value.homeModules==chosenOrder}
            click("home-module-devices")
            await {"devices" !in model.display.value.homeModules && model.display.value.homeModuleOrder==chosenOrder}
            click("home-activity-settings");click("home-heatmap-tokens");click("home-active-days-year")
            await {model.display.value.heatmapMetric=="tokens" && model.display.value.homeActiveDaysWindow=="year" && model.display.value.hiddenTools.isEmpty()}
            compose.onNodeWithTag("hub-settings-toggle").performClick();verify();stage("lists_verified")
            assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
            await {!model.state.value.live && compose.activityRule.scenario.state!=Lifecycle.State.RESUMED};stage("background");Thread.sleep(6000)
            assertTrue("Pin and activity selection persist in background",DisplayPreferenceStore(context).load().pinnedTools.contains("hermes"))
            context.startActivity(Intent(Intent.ACTION_MAIN).setClass(context,MainActivity::class.java).addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            await {model.state.value.live && focused() && !model.state.value.loading};verify()
            instrumentation.sendStatus(2,Bundle().apply {putString("nativeHistoryRevisionChanges",revisionChanges.get().toString())})
            stage("resumed")
        } finally {
            historyObserver.cancel();observation.cancel()
            instrumentation.runOnMainSync {model.setForeground(false);model.forget()};await {!model.state.value.loading};store.clear()
        }
    }
}
