package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class LimitDisplayScreenTest {
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
    private fun show(tag:String)=compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))
    private fun click(tag:String) {show(tag);compose.onNodeWithTag(tag).performClick()}
    private fun select(page:String) {compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-$page").performScrollTo().performClick()}
    private fun value(tag:String,text:String) {show(tag);compose.onNodeWithTag(tag).assertTextEquals(text).assertIsDisplayed()}
    private fun save(name:String,dark:Boolean)=saveVisualEvidence(compose.activity,compose.onRoot(),"limits-$name-${if(dark) "dark" else "light"}.png")
    private val body="""{"periods":{"today":{"totalTokens":1000,"clients":{"codex":1000},"models":{"unknown":800,"future-custom-model":200},"clientModels":{"codex":{"unknown":800}}}},"limits":{"providers":[
        {"provider":"codex","status":"ok","accountEmail":"javis@example.com","accountName":"Workspace","accountLabel":"Plus","accountKey":"sha256:abcdef01","source":"rpc","sourceDetail":"app","sourceDeviceId":"Public Mac","windows":[{"kind":"session","remainingPercent":75,"usedPercent":99},{"kind":"weekly","remainingPercent":65}]},
        {"provider":"codex","status":"ok","accountEmail":"jonas@example.com","accountName":"Workspace","accountLabel":"Plus","accountKey":"sha256:abcdef02","windows":[{"kind":"session","remainingPercent":55}]},
        {"provider":"commandcode","status":"ok","accountLabel":"Pro","windows":[{"kind":"billing","metric":"credits","remaining":47.42,"limit":70,"currency":"USD","remainingPercent":68}]},
        {"provider":"openrouter","status":"ok","accountName":"Demo API","balance":{"amount":102345.675,"monthSpend":10000,"currency":"USD"},"windows":[{"kind":"billing","metric":"credits","currency":"USD"}]},
        {"provider":"grok","status":"ok","source":"rpc","accountLabel":"SuperGrok","windows":[{"kind":"billing","remainingPercent":90,"showMeter":false,"detail":"unlimited"}]},
        {"provider":"kiro","windows":[{"kind":"billing","label":"Overage","used":12.5,"remaining":3.2,"showMeter":false}]}]}}"""

    @Test fun samePlanAccountsMaskedCollisionsAndUsedAmountsStayConsistentAcrossHomeLimitsSubscriptionsAndDetails() {
        val dark=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
        val prefs=mutableStateOf(DisplayPreferences(theme=if(dark) ThemeChoice.DARK else ThemeChoice.LIGHT,homeModules=listOf("limits","models")))
        val snapshot=HubParser.parse(body);val today=LocalDate.now()
        val subs=SubscriptionParser.parse("""{"subscriptions":[{"id":"public-sub","provider":"codex","planName":"Plus","binding":{"profileName":"javis@example.com"},"amountMinor":2000,"currency":"USD","interval":"month","intervalCount":1,"startDate":"2026-08-01","autoRenew":true}]}""")
        val state=HubState(snapshot=snapshot,subscriptions=subs,history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"));val actions=Actions()
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(state,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}
        select("limits");value("limit-value-0-0","75% left");value("limit-value-1-0","55% left")
        compose.onNodeWithTag("hub-settings-toggle").performClick();click("limit-settings-toggle");click("limit-mode-used");click("limit-mask-emails");click("limit-show-source");save("settings",dark)
        select("limits");show("limit-account-0");compose.onAllNodesWithText("j***s@example.com · Workspace · #abcdef01").onFirst().assertIsDisplayed()
        value("limit-value-0-0","25% used");show("limit-account-0");compose.onNodeWithTag("limit-meta-0",useUnmergedTree=true).assertTextContains("App · Public Mac",substring=true)
        click("limit-account-0");show("limit-expanded-account-0");compose.onNodeWithTag("limit-expanded-account-0").assertTextEquals("j***s@example.com · Workspace · #abcdef01");compose.onAllNodesWithText("javis@example.com",substring=true).assertCountEquals(0)
        click("limit-account-0");save("accounts",dark)
        value("limit-value-2-0","32% used");value("limit-detail-2-0","$22.58 / $70.00")
        value("limit-value-3-0","$102345.68");save("money",dark)
        value("limit-value-4-0","Unlimited");show("limit-window-4-0");compose.onNode(hasTestTag("limit-color-grok")).assertDoesNotExist()
        value("limit-value-5-0","12.5 credits · $3.20")
        select("home");value("limit-value-0-0","25% used");save("home",dark)
        openSharedSettings(compose,"subscriptions");val identity=SubscriptionDisplay.keyed(subs).single().first
        show("shared-subscription-$identity");compose.onNodeWithTag("shared-subscription-$identity-title",useUnmergedTree=true).assertTextContains("j***s@example.com",substring=true);click("shared-subscription-$identity")
        compose.onAllNodesWithText("javis@example.com",substring=true).assertCountEquals(0);compose.onNodeWithText("关闭").performClick()
        compose.runOnIdle {assertEquals(snapshot,state.snapshot);assertEquals(subs,state.subscriptions);assertEquals(0,actions.reads);assertEquals(LimitDisplayPreferences(true,true,true),prefs.value.limits)}
    }
    @Test fun unknownModelAndGrokMarksUseDesktopRulesAndActualActivityRestoresAllThreeLocalControls() {
        val dark=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
        val prefs=mutableStateOf(DisplayPreferences(theme=if(dark) ThemeChoice.DARK else ThemeChoice.LIGHT))
        val state=mutableStateOf(HubState(snapshot=HubParser.parse(body),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off")));val actions=Actions()
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(state.value,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}
        select("models");show("vendor-icon-codex");compose.onNodeWithTag("vendor-icon-codex",useUnmergedTree=true).assertExists();show("model-sigma-future-custom-model");save("models",dark)
        compose.runOnIdle {state.value=state.value.copy(snapshot=HubParser.parse(body.replace("\"unknown\":800}}","\"unknown\":700}}")))}
        show("model-dot-unknown");compose.onNodeWithTag("model-dot-unknown",useUnmergedTree=true).assertExists();compose.onNodeWithTag("vendor-icon-codex",useUnmergedTree=true).assertDoesNotExist()
        compose.runOnIdle {prefs.value=prefs.value.copy(appearance=prefs.value.appearance.copy(showToolIcons=false))}
        show("model-dot-future-custom-model");compose.onNodeWithTag("model-sigma-future-custom-model").assertDoesNotExist()
        select("limits");show("limit-account-4");compose.onNodeWithTag("vendor-icon-grok",useUnmergedTree=true).assertExists();save("grok",dark)
        val store=DisplayPreferenceStore(compose.activity);val before=store.load()
        try {
            val saved=prefs.value.copy(language=LanguageChoice.ENGLISH,currency="CNY",currencyRates=mapOf("CNY" to "7"),limits=LimitDisplayPreferences(true,true,true))
            compose.activityRule.scenario.onActivity {ViewModelProvider(it)[HubViewModel::class.java].updateDisplay(saved)}
            compose.waitUntil(5000) {store.load()==saved};compose.activityRule.scenario.recreate()
            if(compose.onAllNodesWithTag("hub-settings-panel-motion").fetchSemanticsNodes().isEmpty()) compose.onNodeWithTag("hub-settings-toggle").performClick()
            openSettingsSection(compose,"limits")
            show("limit-mode-used");compose.onNodeWithTag("limit-mode-used").assertIsSelected();show("limit-mask-emails");compose.onNodeWithTag("limit-mask-emails").assertIsOn();show("limit-show-source");compose.onNodeWithTag("limit-show-source").assertIsOn()
            assertEquals(saved,store.load());assertEquals(0,actions.reads)
        } finally {store.save(before)}
    }
}
