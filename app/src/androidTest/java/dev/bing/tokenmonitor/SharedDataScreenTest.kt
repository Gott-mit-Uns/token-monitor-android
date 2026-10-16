package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Public fixture interaction; no Hub credentials or real screenshots. */
class SharedDataScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    // Retargeting and frame sampling must share Compose's virtual clock. The production
    // System.nanoTime clock can be ahead of virtual frames and leave disclosure progress at zero.
    // Keep animations enabled and preserve all interaction assertions below.
    private val animationClock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
    private class Actions:HubUiActions {
        var stats=0;var prices=0;var subscriptions=0;var rules=0;var history=0
        override fun connect(address:String,credential:String,alternate:String) {}
        override fun refreshSaved() {stats++}
        override fun switchEndpoint() {}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadPricing() {prices++}
        override fun loadModelNames() {rules++}
        override fun loadHistory() {history++}
        override fun loadSubscriptions() {subscriptions++}
        override fun forget() {}
        override fun renameDevice(id:String,input:String) {}
    }
    private fun select(page:String) {compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-$page").performScrollTo().performClick()}
    private fun show(tag:String) {compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))}
    private fun save(name:String,dark:Boolean,dialog:String?=null) {saveVisualEvidence(compose.activity,if(dialog==null) compose.onRoot() else compose.onNodeWithTag(dialog),"shared-$name-${if(dark) "dark" else "light"}.png")}
    @Test fun loadedPricesAndSubscriptionsAreCompactAndCurrentDetailsUpdateOrCloseOnWithdrawal() {
        val today=LocalDate.now();val start=today.minusMonths(2).withDayOfMonth(1)
        val original=HubParser.parse("""{"periods":{"today":{"totalTokens":1000,"costUsd":1.25}}}""")
        val pricing=PricingParser.parse("""{"ok":true,"version":1,"revision":5,"updatedAt":"2026-10-10T00:00:00Z","value":[{"modelId":"public-model","inputPerM":2.5,"outputPerM":15,"cacheReadPerM":0,"cacheWrite1hPerM":3.75}]}""")
        val plan=SubscriptionParser.parse("""{"subscriptions":[{"id":"public-plan","provider":"codex","planName":"Public Plus","amountMinor":2000,"currency":"USD","interval":"month","intervalCount":1,"startDate":"$start","autoRenew":true,"binding":{"profileName":"Public account"}}]}""").single()
        val top=SubscriptionParser.parse("""{"subscriptions":[{"id":"public-topup","provider":"openrouter","planName":"Public credits","kind":"topup","currency":"USD","topUps":[{"date":"$today","amountMinor":1000}]}]}""").single()
        val state=mutableStateOf(HubState(snapshot=original,pricing=pricing,subscriptions=listOf(plan)))
        val prefs=mutableStateOf(DisplayPreferences());val actions=Actions();val dark=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {CompositionLocalProvider(LocalDesktopAnimationClock provides animationClock) {HubScreen(state.value,actions,preferences=prefs.value)}}}}
        openSharedSettings(compose,"pricing");compose.onNodeWithTag("hub-period-today").assertDoesNotExist();compose.onNodeWithTag("shared-pricing-load").assertDoesNotExist();show("shared-price-public-model")
        compose.onNodeWithTag("shared-pricing-summary").assertTextEquals("1 个模型 · USD / 百万 Token")
        save("pricing",dark);show("shared-pricing-refresh");compose.onNodeWithTag("shared-pricing-refresh").performClick();assertEquals(1,actions.prices);assertEquals(0,actions.stats)
        compose.onNodeWithTag("shared-price-public-model").performClick()
        show("shared-price-cacheReadPerM");compose.onNodeWithTag("shared-price-cacheReadPerM").assertTextEquals("输入(命中缓存)：0（明确免费）")
        show("shared-price-cacheWritePerM");compose.onNodeWithTag("shared-price-cacheWritePerM").assertTextEquals("缓存写入：未设置")
        compose.runOnIdle {prefs.value=prefs.value.copy(currency="CNY",currencyRates=mapOf("CNY" to "7"));state.value=state.value.copy(pricing=pricing.copy(models=pricing.models.map {it.copy(rates=it.rates+("inputPerM" to BigDecimal("3.5")))}))}
        show("shared-price-inputPerM");compose.onNodeWithTag("shared-price-inputPerM").assertTextEquals("输入(未命中缓存)：3.5")
        save("price-detail",dark,"shared-price-details")
        compose.runOnIdle {state.value=state.value.copy(pricing=pricing.copy(models=emptyList()))}
        compose.onNodeWithTag("shared-price-details").assertDoesNotExist()
        compose.runOnIdle {state.value=state.value.copy(pricing=pricing)};compose.onNodeWithTag("shared-price-details").assertDoesNotExist()
        openSharedSettings(compose,"subscriptions");compose.onNodeWithTag("hub-period-month").assertDoesNotExist();compose.onNodeWithTag("shared-subscriptions-load").assertDoesNotExist()
        compose.onNodeWithTag("shared-subscriptions-summary").assertTextEquals("1 笔订阅")
        show("shared-subscription-public-plan");compose.onNodeWithTag("shared-subscription-public-plan-title",useUnmergedTree=true).assertTextContains("Public account",substring=true)
        compose.onNodeWithTag("shared-subscription-public-plan-meta",useUnmergedTree=true).assertTextContains("Public Plus · $20.00 / 月",substring=true)
        compose.onNodeWithTag("settings-subscriptions-refresh").assertDoesNotExist()
        assertEquals(0,actions.subscriptions)
        save("subscriptions",dark)
        compose.onNodeWithTag("shared-subscription-public-plan").performClick()
        compose.onNodeWithTag("shared-subscription-paid").assertTextContains("累计 $60.00",substring=true)
        compose.runOnIdle {state.value=state.value.copy(subscriptions=listOf(plan.copy(autoRenew=false)))}
        compose.onNodeWithTag("shared-subscription-paid").assertTextContains("累计 $20.00",substring=true)
        compose.onNodeWithTag("shared-subscription-boundary").assertTextContains(SubscriptionWording.date(java.util.Locale.SIMPLIFIED_CHINESE,start.plusMonths(1)),substring=true)
        save("subscription-detail",dark,"shared-subscription-details")
        compose.runOnIdle {state.value=state.value.copy(subscriptions=emptyList())};compose.onNodeWithTag("shared-subscription-details").assertDoesNotExist()
        compose.runOnIdle {state.value=state.value.copy(subscriptions=listOf(plan,top))};compose.onNodeWithTag("shared-subscription-details").assertDoesNotExist()
        show("shared-subscription-public-topup");compose.onNodeWithTag("shared-subscription-public-topup").performClick()
        compose.onNodeWithText("充值 $today · 10.00 USD").performScrollTo().assertIsDisplayed();save("topup-detail",dark,"shared-subscription-details")
        compose.onNodeWithText("关闭").performClick();assertEquals(original,state.value.snapshot);assertEquals("2.5",pricing.models.single().rates.getValue("inputPerM").toPlainString());assertEquals(0,actions.stats)
        compose.runOnIdle {state.value=state.value.copy(subscriptions=listOf(plan.copy(id="public-unknown",count=0,interval="week",autoRenew=null,amount=null,currency=null)))}
        show("shared-subscription-public-unknown");compose.onNodeWithTag("shared-subscription-public-unknown-meta",useUnmergedTree=true).assertTextContains("— / —",substring=true)
        compose.onNodeWithTag("shared-subscriptions-summary").assertTextEquals("1 笔订阅")
    }
    @Test fun allModelRulesCanBePagedSearchedRefreshedAndRevokedWithoutReopeningOldDetails() {
        val rules=ModelNames((0..204).associate {"raw-${it.toString().padStart(3,'0')}" to "display-${it.toString().padStart(3,'0')}"},"duplicates")
        val state=mutableStateOf(HubState(snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":1000,"models":{"raw-000":1000}}}}"""),modelNames=rules))
        val actions=Actions();val dark=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark) {CompositionLocalProvider(LocalDesktopAnimationClock provides animationClock) {HubScreen(state.value,actions)}}}}
        compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"tools");compose.onNodeWithTag("shared-model-rules-load").assertDoesNotExist();show("shared-model-rules-open");compose.onNodeWithTag("shared-model-rules-open").performClick()
        compose.onNodeWithTag("shared-model-rules-title").assertTextEquals("模型别名")
        compose.onNodeWithTag("shared-rules-page").assertTextEquals("1/3")
        show("shared-rules-next");compose.onNodeWithTag("shared-rules-next").performClick();compose.onNodeWithTag("shared-rules-page").assertTextEquals("2/3")
        show("shared-rules-next");compose.onNodeWithTag("shared-rules-next").performClick();compose.onNodeWithTag("shared-rules-page").assertTextEquals("3/3")
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("raw-204"));compose.onNodeWithText("raw-204").assertIsDisplayed();save("rules",dark,"shared-model-rules")
        show("shared-rule-search");compose.onNodeWithTag("shared-rule-search").performTextReplacement("203")
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("raw-203"));compose.onNodeWithText("raw-203").assertIsDisplayed();compose.onNodeWithText("raw-204").assertDoesNotExist()
        show("shared-rules-refresh");compose.onNodeWithTag("shared-rules-refresh").performClick();assertEquals(1,actions.rules)
        compose.runOnIdle {state.value=state.value.copy(modelNames=null)};compose.onNodeWithTag("shared-model-rules").assertDoesNotExist()
        compose.runOnIdle {state.value=state.value.copy(modelNames=rules)};compose.onNodeWithTag("shared-model-rules").assertDoesNotExist()
        listOf("prefix" to "移除前缀","duplicates" to "合并重复","off" to "关闭").forEach {(mode,label)->
            compose.runOnIdle {state.value=state.value.copy(modelNames=ModelNames(emptyMap(),mode))}
            show("shared-model-rules-open");compose.onNodeWithTag("shared-model-rules-open-status",useUnmergedTree=true).assertTextEquals(label)
        }
        compose.runOnIdle {state.value=state.value.copy(modelNames=ModelNamesParser.parse("""{"ok":true,"value":null}"""))}
        show("shared-model-rules-open");compose.onNodeWithTag("shared-model-rules-open").assertIsNotEnabled()
        compose.onNodeWithTag("hub-refresh").performClick();assertEquals(1,actions.stats)
        assertEquals(205,rules.aliases.size);assertEquals("display-204",rules.aliases["raw-204"])
    }
}
