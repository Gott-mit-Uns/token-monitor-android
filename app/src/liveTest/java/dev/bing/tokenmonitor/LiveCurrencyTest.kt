package dev.bing.tokenmonitor

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Opt-in production UI/client check. Private values never leave device memory. */
class LiveCurrencyTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun actualCurrencyControlsConvertRealHubAndRetainUsdCacheAcrossBackground() {
        val instrumentation=InstrumentationRegistry.getInstrumentation(); val context=instrumentation.targetContext
        val port=InstrumentationRegistry.getArguments().getString("liveHubPort")!!.toInt()
        val store=HubStore(context); assertNull(store.load())
        lateinit var model: HubViewModel
        compose.activityRule.scenario.onActivity { model=ViewModelProvider(it)[HubViewModel::class.java] }
        fun await(condition: () -> Boolean) {
            val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(25)
            while (!condition() && System.nanoTime()<until) { compose.waitForIdle();Thread.sleep(30) }
            assertTrue("Real currency condition unavailable; private values withheld",condition())
        }
        fun stage(value: String)=instrumentation.sendStatus(2,Bundle().apply { putString("nativeLiveStage",value) })
        fun focused()=compose.activityRule.scenario.state==Lifecycle.State.RESUMED && run {
            var focus=false; compose.activityRule.scenario.onActivity { focus=it.hasWindowFocus() };focus
        }
        fun verifyHeader(currency: UsageCurrency) {
            await {
                val period=model.state.value.snapshot?.periods?.get("today") ?: return@await false
                val actual=compose.onAllNodesWithTag("hub-total-cost").fetchSemanticsNodes().firstOrNull()?.config
                    ?.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
                actual==currency.headline(period.cost,period.unpriced)
            }
            val current=model.state.value
            assertTrue("Raw Hub USD remains in encrypted snapshot",HubParser.parse(store.load()!!.snapshot).periods["today"]?.cost==current.snapshot!!.periods["today"]?.cost)
            assertTrue("Currency does not rewrite retained history",HistoryParser.parse(store.load()!!.history!!)==current.history)
        }
        try {
            await { !model.state.value.loading && focused() }
            compose.activityRule.scenario.onActivity { model.connect("http://10.0.2.2:$port",UUID.randomUUID().toString(),"") }
            await { model.state.value.live && model.state.value.history!=null && !model.state.value.loading && store.load()?.history!=null }
            stage("sustain");verifyHeader(UsageCurrency())
            compose.onNodeWithTag("hub-settings-toggle").performClick()
            openSettingsSection(compose,"main")
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("usage-currency-select"));compose.onNodeWithTag("usage-currency-select").performClick()
            compose.onNodeWithTag("usage-currency-CNY").performClick()
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("usage-rate-mode-manual"));compose.onNodeWithTag("usage-rate-mode-manual").performClick()
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("usage-currency-rate"))
            compose.onNodeWithTag("usage-currency-rate").performTextReplacement("7")
            compose.onNodeWithTag("usage-rate-save").performScrollTo().performClick()
            await { model.display.value.currency=="CNY" && model.display.value.currencyRates["CNY"]=="7" }
            compose.onNodeWithTag("hub-settings-toggle").performClick()
            val currency=UsageCurrency("CNY",mapOf("CNY" to "7"));verifyHeader(currency)
            val cached=widgetUsage(model.state.value.snapshot!!,System.currentTimeMillis())
            compose.runOnIdle {
                val prefs=model.display.value;val localized=HubWidget.localizedContext(context,prefs.language)
                val widget=HubWidget.render(localized,prefs,WidgetCache.Ready(cached),8004).apply(localized,FrameLayout(localized))
                val expected=localized.getString(if (cached.unpriced) R.string.widget_cost_partial_formatted else R.string.widget_cost_formatted,
                    currency.headline(cached.cost,if(cached.unpriced) 1L else 0L))
                assertTrue("Offline widget uses selected currency",widget.findViewById<TextView>(R.id.widget_cost).text.toString()==expected)
            }
            stage("usage_verified")
            assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
            await { !model.state.value.live && compose.activityRule.scenario.state!=Lifecycle.State.RESUMED }
            stage("background");Thread.sleep(6000)
            assertTrue("Local rate survives background",DisplayPreferenceStore(context).load().currencyRates["CNY"]=="7")
            context.startActivity(Intent(Intent.ACTION_MAIN).setClass(context,MainActivity::class.java).addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            await { model.state.value.live && focused() && !model.state.value.loading }
            verifyHeader(currency);stage("resumed")
        } finally {
            instrumentation.runOnMainSync { model.setForeground(false);model.forget() }
            await { !model.state.value.loading };store.clear()
        }
    }
}
