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
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Production Activity/Hub reads; private values are inspected only in device memory. */
class LiveHomeChartTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun realPreviewHistoryLiveTodayAndBackgroundCacheAgreeOnTheActualScreen() {
        val instrumentation=InstrumentationRegistry.getInstrumentation(); val context=instrumentation.targetContext
        val port=InstrumentationRegistry.getArguments().getString("liveHubPort")!!.toInt()
        val store=HubStore(context); assertNull(store.load())
        lateinit var model: HubViewModel
        compose.activityRule.scenario.onActivity { model=ViewModelProvider(it)[HubViewModel::class.java] }
        fun await(condition: () -> Boolean) {
            val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(25)
            while (!condition() && System.nanoTime()<until) { compose.waitForIdle();Thread.sleep(30) }
            assertTrue("Real home chart condition unavailable; private values withheld",condition())
        }
        fun stage(value: String)=instrumentation.sendStatus(2,Bundle().apply { putString("nativeLiveStage",value) })
        fun focused()=compose.activityRule.scenario.state==Lifecycle.State.RESUMED && run {
            var focus=false; compose.activityRule.scenario.onActivity { focus=it.hasWindowFocus() };focus
        }
        fun verify() {
            val current=model.state.value; val snapshot=current.snapshot!!
            assertNotNull("Real source provides history preview",snapshot.historyPreview)
            assertTrue("Retained daily history is available",current.history!!.daily.isNotEmpty())
            val projection=HomeCharts.project(HomeCharts.pickHistory(current.history,snapshot.historyPreview)!!,
                snapshot.periods["today"],LocalDate.now(),model.display.value.homeActiveDaysWindow,snapshot.historyPreview?.summary?.activeDays)
            val tokens=NumberFormat.getIntegerInstance(Locale.CHINESE).format(snapshot.periods["today"]!!.tokens)
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("home-activity-chart"))
            await { compose.onAllNodesWithTag("home-activity-chart").fetchSemanticsNodes().firstOrNull()?.config
                ?.getOrNull(SemanticsProperties.ContentDescription)?.any { it.contains(tokens+" Token") } == true }
            compose.onNodeWithTag("home-activity-month-${LocalDate.now().toString().take(7)}").assertExists()
            compose.onNodeWithTag("home-activity-chart").performSemanticsAction(SemanticsActions.OnClick) { it() }
            await {
                val live=model.state.value.snapshot!!.periods["today"]!!
                val actual=compose.onAllNodesWithTag("home-activity-day-tokens").fetchSemanticsNodes().firstOrNull()?.config
                    ?.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
                actual==NumberFormat.getIntegerInstance(Locale.CHINESE).format(live.tokens)+" Token"
            }
            await {
                val live=model.state.value.snapshot!!.periods["today"]!!
                val actual=compose.onAllNodesWithTag("home-activity-day-cost").fetchSemanticsNodes().firstOrNull()?.config
                    ?.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
                actual=="已知估算费用："+UsageCurrency().label(live.cost,live.unpriced?.let(java.math.BigInteger::valueOf),
                    formatTokens={ NumberFormat.getIntegerInstance(Locale.CHINESE).format(it) },unpricedLabel="未定价 tokens")
            }
            compose.onNodeWithText("关闭").performClick()
            val peak="最高 "+NumberFormat.getIntegerInstance(Locale.CHINESE).format(projection.peakTokens)+" Token"
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("home-trend-chart"))
            await { compose.onAllNodesWithText(peak).fetchSemanticsNodes().isNotEmpty() }
            assertTrue("Live overlay is separate from retained encrypted history",HistoryParser.parse(store.load()!!.history!!) == current.history)
        }
        try {
            await { !model.state.value.loading && focused() }
            compose.activityRule.scenario.onActivity { model.connect("http://10.0.2.2:$port",UUID.randomUUID().toString(),"") }
            await { model.state.value.live && model.state.value.history!=null && !model.state.value.loading && store.load()?.history!=null }
            stage("sustain"); verify(); stage("charts_verified")
            assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
            await { !model.state.value.live && compose.activityRule.scenario.state!=Lifecycle.State.RESUMED }
            stage("background");Thread.sleep(6000)
            assertNotNull(store.load()?.history)
            context.startActivity(Intent(Intent.ACTION_MAIN).setClass(context,MainActivity::class.java).addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            await { model.state.value.live && focused() && !model.state.value.loading }
            verify();stage("resumed")
        } finally {
            instrumentation.runOnMainSync { model.setForeground(false);model.forget() }
            await { !model.state.value.loading };store.clear()
        }
    }
}
