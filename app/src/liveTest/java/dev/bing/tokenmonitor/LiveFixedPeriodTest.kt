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

/** Real Hub UI actions; no private values, screenshots or accessibility trees are logged. */
class LiveFixedPeriodTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val activity get() = compose.activityRule
    @Test fun realDeviceHistoryAndActualMenuShareTotalsAndStopInBackground() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val port = InstrumentationRegistry.getArguments().getString("liveHubPort")!!.toInt()
        val store = HubStore(context)
        assertNull(store.load())
        lateinit var model: HubViewModel
        activity.scenario.onActivity { model = ViewModelProvider(it)[HubViewModel::class.java] }
        fun await(condition: () -> Boolean) {
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(25)
            while (!condition() && System.nanoTime() < until) { compose.waitForIdle(); Thread.sleep(30) }
            assertTrue("Real fixed-period condition unavailable; private values withheld", condition())
        }
        fun stage(value: String) = instrumentation.sendStatus(2, Bundle().apply { putString("nativeLiveStage", value) })
        fun control(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()
        fun click(tag: String) {
            await { control(tag) != null }
            val target = control(tag)!!
            var clicked = false
            compose.runOnIdle { clicked = target.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true }
            assertTrue("Public period control should activate", clicked)
        }
        fun focused() = activity.scenario.state == Lifecycle.State.RESUMED && run {
            var focus = false; activity.scenario.onActivity { focus = it.hasWindowFocus() }; focus
        }
        try {
            await { !model.state.value.loading && focused() }
            activity.scenario.onActivity { model.connect("http://10.0.2.2:$port", UUID.randomUUID().toString(), "") }
            await { model.state.value.live && model.state.value.history != null && !model.state.value.loading }
            stage("sustain")
            click("hub-period-month"); click("hub-period-month"); click("hub-period-menu-last7")
            for (selection in listOf("last7", "week", "last30")) {
                if (selection != "last7") { click("hub-period-month"); click("hub-period-menu-$selection") }
                await { model.display.value.lastPeriod == selection && !model.state.value.loading && model.state.value.deviceHistories != null }
                await { control("hub-period-month")?.config?.getOrNull(SemanticsProperties.Selected) == true }
                val current = model.state.value
                val result = FixedPeriods.evaluate(selection, current.snapshot!!, current.deviceHistories)
                assertNull("Real source history/windows should provide the range", result.issue)
                assertNotNull(result.period)
                assertTrue("Device sums should equal the selected headline", result.devices.values.fold(0L) { sum, period -> Math.addExact(sum, period.tokens ?: 0) } == result.period!!.tokens)
                val rendered = NumberFormat.getIntegerInstance(Locale.CHINESE).format(result.period!!.tokens)
                await { control("hub-total-tokens")?.config?.getOrNull(SemanticsProperties.Text)?.singleOrNull()?.text == rendered }
            }
            await { store.load()?.deviceHistory != null }
            val saved = store.load()!!
            assertTrue("Only projected history should be cached", DeviceHistoryParser.parse(saved.deviceHistory!!).signature == FixedPeriods.signature(HubParser.parse(saved.snapshot)))
            stage("fixed_verified")
            assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
            await { !model.state.value.live && activity.scenario.state != Lifecycle.State.RESUMED }
            stage("background")
            Thread.sleep(6000)
            assertNotNull(store.load()?.deviceHistory)
            context.startActivity(Intent(Intent.ACTION_MAIN).setClass(context, MainActivity::class.java).addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            await { model.state.value.live && focused() && model.state.value.deviceHistories != null && !model.state.value.loading }
            assertEquals("last30", model.display.value.lastPeriod)
            stage("resumed")
        } finally {
            instrumentation.runOnMainSync { model.setForeground(false); model.forget() }
            await { !model.state.value.loading }
            store.clear()
        }
    }
}
