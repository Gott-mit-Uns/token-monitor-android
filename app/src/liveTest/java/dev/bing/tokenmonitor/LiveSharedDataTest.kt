package dev.bing.tokenmonitor

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Real production read-only pages. Private names/amounts remain in device memory. */
class LiveSharedDataTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun realSharedPagesRefreshTheirOwnDataRetainEncryptedSourcesAndStopInBackground() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val port=InstrumentationRegistry.getArguments().getString("liveHubPort")!!.toInt();val store=HubStore(context)
        val strings=context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {setLocale(java.util.Locale.SIMPLIFIED_CHINESE)}).resources
        assertNull(store.load());lateinit var model:HubViewModel
        compose.activityRule.scenario.onActivity {model=ViewModelProvider(it)[HubViewModel::class.java]}
        fun await(condition:()->Boolean) {
            val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(25)
            while(!condition() && System.nanoTime()<until) {compose.waitForIdle();Thread.sleep(30)}
            assertTrue("Real shared-data condition unavailable; private diagnostics withheld",condition())
        }
        fun stage(value:String)=instrumentation.sendStatus(2,Bundle().apply {putString("nativeLiveStage",value)})
        fun focused()=compose.activityRule.scenario.state==Lifecycle.State.RESUMED && run {var focus=false;compose.activityRule.scenario.onActivity {focus=it.hasWindowFocus()};focus}
        fun text(tag:String)=compose.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("") {it.text}
        fun select(page:String) {compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-$page").performScrollTo().performClick()}
        fun retained() {
            await {store.load()?.let {saved ->
                saved.history?.let(HistoryParser::parse)==model.state.value.history && HubParser.parse(saved.snapshot)==model.state.value.snapshot &&
                saved.pricing?.let(PricingParser::parse)==model.state.value.pricing && saved.subscriptions?.let(SubscriptionParser::parse)==model.state.value.subscriptions &&
                saved.modelNames?.let(ModelNamesParser::parse)==model.state.value.modelNames
            }==true}
        }
        try {
            await {!model.state.value.loading && focused()}
            compose.activityRule.scenario.onActivity {model.connect("http://10.0.2.2:$port",UUID.randomUUID().toString(),"")}
            await {model.state.value.live && model.state.value.history!=null && !model.state.value.loading && store.load()?.history!=null};stage("sustain")
            openSharedSettings(compose,"pricing");await {model.state.value.pricing!=null && !model.state.value.loading && store.load()?.pricing!=null}
            assertTrue("Loaded pricing does not retain onboarding controls",compose.onAllNodesWithTag("shared-pricing-load").fetchSemanticsNodes().isEmpty())
            val price=model.state.value.pricing!!.models.firstOrNull()
            if(price!=null) {
                compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("shared-price-${price.model}"));compose.onNodeWithTag("shared-price-${price.model}").performClick()
                val labels=mapOf("inputPerM" to R.string.shared_price_miss,"outputPerM" to R.string.token_output,"cacheReadPerM" to R.string.shared_price_hit,"cacheWritePerM" to R.string.token_cache_write,"cacheWrite1hPerM" to R.string.shared_price_hour)
                labels.forEach {(key,label) ->
                    val rate=price.rates[key];val value=when {rate==null -> strings.getString(R.string.price_unset);rate.toDouble()==0.0 -> strings.getString(R.string.price_zero);else -> sharedRateText(rate)}
                    assertTrue("Real price detail retains USD rate precision and missing/zero fields",text("shared-price-$key")==strings.getString(R.string.price_rate,strings.getString(label),value))
                }
                compose.onNodeWithText(strings.getString(R.string.close)).performClick()
            }
            compose.onNodeWithTag("hub-refresh").performClick();await {!model.state.value.loading && model.state.value.pricing!=null}
            openSharedSettings(compose,"subscriptions");await {model.state.value.subscriptions!=null && !model.state.value.loading && store.load()?.subscriptions!=null}
            assertTrue("Loaded subscriptions do not retain onboarding controls",compose.onAllNodesWithTag("shared-subscriptions-load").fetchSemanticsNodes().isEmpty())
            val rows=SubscriptionDisplay.keyed(model.state.value.subscriptions!!)
            rows.firstOrNull()?.let {(identity,record) ->
                compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("shared-subscription-$identity"));compose.onNodeWithTag("shared-subscription-$identity").performClick()
                val expected=SubscriptionReadContext(model.state.value.subscriptions.orEmpty(),SubscriptionAccounts.records(model.state.value.snapshot!!.limits),emptyMap(),LocalDate.now(),strings.configuration.locales[0],UsageCurrency()).forSubscription(record,null,false).firstOrNull {it.label==SubscriptionWording.text(strings.configuration.locales[0],"subscription.tooltip.subscribed")}?.value
                assertTrue("Real subscription detail retains original currency and source record",expected==null || text("shared-subscription-paid")==expected)
                compose.onNodeWithText(strings.getString(R.string.close)).performClick()
            }
            compose.onNodeWithTag("hub-refresh").performClick();await {!model.state.value.loading && model.state.value.subscriptions!=null}
            select("models");await {model.state.value.modelNames!=null && !model.state.value.loading && store.load()?.modelNames!=null}
            val names=model.state.value.modelNames!!
            compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"tools")
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("shared-model-rules-open"))
            if(names.initialized) {
                compose.onNodeWithTag("shared-model-rules-open").performClick();names.aliases.keys.minOrNull()?.let {key ->
                    assertTrue("Real model rule source is inspectable",compose.onAllNodesWithText(key).fetchSemanticsNodes().isNotEmpty())
                };compose.onNodeWithText(strings.getString(R.string.close)).performClick()
            } else compose.onNodeWithTag("shared-model-rules-open").assertIsNotEnabled()
            retained();stage("shared_verified")
            assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
            await {!model.state.value.live && compose.activityRule.scenario.state!=Lifecycle.State.RESUMED};stage("background");Thread.sleep(6000)
            assertNotNull(store.load()?.pricing);assertNotNull(store.load()?.subscriptions);assertNotNull(store.load()?.modelNames)
            context.startActivity(Intent(Intent.ACTION_MAIN).setClass(context,MainActivity::class.java).addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            await {model.state.value.live && focused() && !model.state.value.loading};retained();stage("resumed")
        } finally {
            instrumentation.runOnMainSync {model.setForeground(false);model.forget()};await {!model.state.value.loading};store.clear()
        }
    }
}
