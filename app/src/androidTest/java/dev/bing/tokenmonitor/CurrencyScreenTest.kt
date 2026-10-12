package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/** Public synthetic Hub values, including partial/cost-only data; never private screenshots. */
class CurrencyScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private class Actions : HubUiActions {
        override fun connect(address: String,credential: String,alternate: String) {}
        override fun refreshSaved() {}
        override fun switchEndpoint() {}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadHistory() {}
        override fun loadModelNames() {}
        override fun loadSubscriptions() {}
        override fun forget() {}
        override fun renameDevice(id: String,input: String) {}
    }
    @Test fun sharedCurrencyUpdatesEveryUsageViewAndOfflineWidgetWithoutChangingSourceUnits() {
        val body="""{"periods":{"today":{"totalTokens":200,"costUsd":0.8,"unpricedTokens":40,"cacheReadTokens":50,"cacheWriteTokens":9,"outputTokens":10,"unclassifiedTokens":20,"capabilities":{"tokenComponents":false},
            "clients":{"codex":100},"clientCosts":{"codex":0.5},"clientUnpricedTokens":{"codex":20},"clientCacheReads":{"codex":50},"clientCacheWrites":{"codex":9},"clientOutputs":{"codex":10},"clientUnclassifiedTokens":{"codex":20},
            "models":{"vendor/public":60,"public":40},"modelCosts":{"vendor/public":0.3,"public":0.2,"cost-only":0.1},"modelUnpricedTokens":{"vendor/public":10,"public":10},
            "clientModels":{"codex":{"public":60}},"clientModelCosts":{"codex":{"public":0.3}},"clientModelUnpricedTokens":{"codex":{"public":10}},
            "projects":{"public":{"label":"Public Project","clients":{"codex":200},"tokens":200,"costUsd":0.8,"unpricedTokens":40}},"sessions":{"public":{"sessionId":"public","title":"Public Session","totalTokens":200,"costUsd":0.8,"unpricedTokens":40}}}},
            "devices":[{"deviceId":"public","hostname":"Public Device","periods":{"today":{"totalTokens":200,"costUsd":0.8,"unpricedTokens":40}}}],
            "limits":{"providers":[{"provider":"openrouter","status":"ok","balance":{"amount":7.5,"currency":"EUR"},"windows":[{"kind":"billing","metric":"credits","remaining":7.5,"currency":"EUR","showMeter":false}]}]}}"""
        val source=HubParser.parse(body);val history=HistoryParser.parse("""{"daily":[{"date":"${LocalDate.now()}","tokens":200,"cost":0.8,"unpricedTokens":40}],"monthly":[]}""")
        val state=mutableStateOf(HubState(snapshot=source,history=history,modelNames=ModelNames(mapOf("vendor/public" to "public"),"off")))
        val prefs=mutableStateOf(DisplayPreferences(homeModules=listOf("tools","models")))
        val store=DisplayPreferenceStore(compose.activity,"test-currency-ui-${UUID.randomUUID()}.bin")
        val dark=InstrumentationRegistry.getArguments().getString("visualDark") != "false"
        try {
            compose.activity.runOnUiThread { compose.activity.setContent { TokenTheme(dark) {
                HubScreen(state.value,Actions(),preferences=prefs.value,updateDisplay={ prefs.value=it.apply(prefs.value);store.save(prefs.value) })
            } } }
            fun scroll(matcher: SemanticsMatcher) { compose.onNodeWithTag("hub-content").performScrollToNode(matcher) }
            fun show(text: String) { scroll(hasText(text));compose.onAllNodesWithText(text).onFirst().assertIsDisplayed() }
            fun select(page: String) { compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-$page").performScrollTo().performClick() }
            compose.onNodeWithTag("hub-total-cost").assertTextEquals("$0.8000")
            compose.onNodeWithTag("hub-total-tokens").performClick()
            compose.onNodeWithText("120").assertIsDisplayed();compose.onNodeWithText("缓存写入").assertDoesNotExist();compose.onNodeWithText("关闭").performClick()
            compose.onNodeWithTag("hub-settings-toggle").performClick()
            openSettingsSection(compose,"main")
            scroll(hasTestTag("usage-currency-select"));compose.onNodeWithTag("usage-currency-select").performClick();compose.onNodeWithTag("usage-currency-CNY").performClick()
            scroll(hasTestTag("usage-rate-mode-manual"));compose.onNodeWithTag("usage-rate-mode-manual").performClick()
            scroll(hasTestTag("usage-currency-rate"));compose.onNodeWithTag("usage-currency-rate").performTextReplacement("0")
            compose.onNodeWithTag("usage-currency-rate").performImeAction();assertFalse(store.load().currencyRates.containsKey("CNY"))
            scroll(hasTestTag("usage-rate-mode-manual"));compose.onNodeWithTag("usage-rate-mode-manual").performClick()
            scroll(hasTestTag("usage-currency-rate"))
            compose.onNodeWithTag("usage-currency-rate").performTextReplacement("7")
            compose.onNodeWithTag("usage-rate-save").performScrollTo().performClick()
            assertEquals("CNY",store.load().currency);assertEquals("7",store.load().currencyRates["CNY"])
            compose.onNodeWithTag("hub-settings-toggle").performClick();compose.onNodeWithTag("hub-total-cost").assertTextEquals("¥5.60")
            select("tools");show("¥3.50 + 20 未定价 tokens");compose.onNodeWithText("Codex").performClick()
            compose.onNodeWithTag("tool-detail-mode-models").performClick()
            scroll(hasTestTag("tool-detail-tool-codex-model-public:model"));compose.onNodeWithTag("tool-detail-tool-codex-model-public:model").performClick()
            compose.onNodeWithText("已知估算费用：¥2.10 + 10 未定价 tokens").assertExists();compose.onNodeWithText("关闭").performClick()
            select("models");compose.onAllNodes(hasSetTextAction()).assertCountEquals(0);compose.onNodeWithTag("shared-model-rules-open").assertDoesNotExist();compose.onNodeWithTag("shared-model-rules-load").assertDoesNotExist();show("public");show("¥3.50 + 20 未定价 tokens");show("cost-only");show("¥0.7000")
            saveVisualEvidence(compose.activity,compose.onRoot(),"usage-models-${if(dark) "dark" else "light"}.png")
            select("devices");show("¥5.60 + 40 未定价 tokens")
            compose.onNodeWithText("public").performTouchInput {longClick()}
            compose.onNodeWithText("已知估算费用：¥5.60 + 40 未定价 tokens").assertExists()
            compose.onNodeWithText("关闭").performClick()
            select("projects");show("¥5.60 + 40 未定价 tokens")
            select("sessions");show("¥5.60 + 40 未定价 tokens")
            select("limits");show("EUR 7.50")
            select("trends");compose.onNodeWithTag("trends-main-open-history").performClick()
            compose.onNodeWithTag("dashboard-open-records").performScrollTo().performClick()
            val historyCost="已知估算费用：¥5.60 + 40 未定价 tokens（费用可用 1 天）"
            compose.onNodeWithTag("dashboard-scroll").performScrollToNode(hasText(historyCost))
            compose.onAllNodesWithText(historyCost).onFirst().assertIsDisplayed()
            saveVisualEvidence(compose.activity,compose.onRoot(),"usage-history-${if(dark) "dark" else "light"}.png")
            compose.onNodeWithTag("trends-history-back").performClick()
            compose.onNodeWithTag("hub-view-selector").assertIsDisplayed()
            val localized=HubWidget.localizedContext(compose.activity,prefs.value.language)
            val cache=WidgetCache.Ready(widgetUsage(source,System.currentTimeMillis()))
            compose.runOnIdle {
                val widget=HubWidget.render(localized,prefs.value,cache,8004).apply(localized,FrameLayout(localized))
                assertEquals("仅已知费用：¥5.60",widget.findViewById<TextView>(R.id.widget_cost).text.toString())
            }
            assertEquals("0.8",source.periods.getValue("today").cost)
            assertEquals(40L,source.periods.getValue("today").unpriced)
            assertEquals("EUR",source.limits.single().currency)
            assertEquals("0.8",history.daily.single().cost)
            compose.runOnIdle { state.value=state.value.copy(snapshot=source.copy(periods=mapOf("today" to source.periods.getValue("today").copy(cost="0")))) }
            select("home");compose.onNodeWithTag("hub-total-cost").assertTextEquals("—")
        } finally { store.clear() }
    }
}
