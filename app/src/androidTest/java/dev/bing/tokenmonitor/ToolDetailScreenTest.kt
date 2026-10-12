package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Public synthetic sources only, including tiny shares and missing model fields. */
class ToolDetailScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private class Actions : HubUiActions {
        var reads=0
        override fun connect(address: String,credential: String,alternate: String) { reads++ }
        override fun refreshSaved() { reads++ }
        override fun switchEndpoint() { reads++ }
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadHistory() { reads++ }
        override fun loadModelNames() { reads++ }
        override fun loadSubscriptions() { reads++ }
        override fun forget() {}
        override fun renameDevice(id: String,input: String) {}
    }
    private fun source(codex: Int=1000,models: Boolean=true): Snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":${codex+1000},"costUsd":${if(codex==1000) 2.5 else 2.7},"unpricedTokens":50,"capabilities":{"tokenComponents":true},"cacheReadTokens":701,"outputTokens":100,"unclassifiedTokens":0,
        "clients":{"codex":$codex,"claude":800,"opencode":200},"clientCosts":{"codex":${if(codex==1000) 1.5 else 1.7},"claude":0.8,"opencode":0.2},"clientUnpricedTokens":{"codex":50},"clientCacheReads":{"codex":1,"claude":700,"opencode":0},"clientCacheWrites":{"codex":25},"clientOutputs":{"codex":0,"claude":100,"opencode":0},"clientUnclassifiedTokens":{"codex":0,"claude":0,"opencode":0},
        "clientModels":{"claude":{"other":800}${if(models) ",\"codex\":{\"vendor/public\":600,\"public\":${codex-700}}" else ""}},
        "clientModelCosts":{"claude":{"other":0.8}${if(models) ",\"codex\":{\"vendor/public\":0.6,\"public\":${if(codex==1000) 0.3 else 0.5},\"cost-only\":0.1}" else ""}},
        "clientModelUnpricedTokens":{${if(models) "\"codex\":{\"vendor/public\":30,\"public\":10,\"cost-only\":10}" else ""}}}}}""")
    private fun select(page: String) { compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-$page").performScrollTo().performClick() }
    private fun show(tag: String) { compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag)) }
    private fun open(tool: String) { show("breakdown-tool-$tool");compose.onNodeWithTag("breakdown-tool-$tool").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick().assertIsSelected() }
    @Test fun contextualFooterAndSingleExpandedRowKeepLiveSharesAliasesAndCostsAccurate() {
        val original=source();val actions=Actions()
        val state=mutableStateOf(HubState(snapshot=original,modelNames=ModelNames(mapOf("vendor/public" to "public"),"off")))
        val prefs=mutableStateOf(DisplayPreferences())
        val dark=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
        compose.activity.runOnUiThread { compose.activity.setContent { TokenTheme(dark,prefs.value.language) { HubScreen(state.value,actions,preferences=prefs.value) } } }
        select("tools");compose.onAllNodes(hasSetTextAction()).assertCountEquals(0);compose.onNodeWithTag("tool-detail-switch").assertDoesNotExist();open("codex")
        compose.onNodeWithTag("tool-detail-mode-tokens").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithTag("tool-detail-mode-models").assertIsDisplayed()
        show("tool-detail-tool-codex-hit");compose.onNodeWithTag("tool-detail-tool-codex-hit-percent").assertTextEquals("<1%")
        compose.onNodeWithTag("tool-detail-tool-codex-miss-value").assertTextEquals("999")
        compose.onNodeWithTag("tool-detail-tool-codex-miss-percent").assertTextEquals("100%")
        compose.onNodeWithTag("tool-detail-tool-codex-unclassified").assertDoesNotExist()
        saveVisualEvidence(compose.activity,compose.onRoot(),"detail-tokens-${if(dark) "dark" else "light"}.png")
        compose.onNodeWithTag("tool-detail-mode-models").performClick().assertIsSelected()
        val public="tool-detail-tool-codex-model-public:model"
        show(public);compose.onNodeWithTag("$public-value",useUnmergedTree=true).assertTextEquals("900")
        compose.onNodeWithTag("$public-percent",useUnmergedTree=true).assertTextEquals("90%")
        compose.onNodeWithTag("tool-detail-tool-codex-model-__unattributed:remainder-percent",useUnmergedTree=true).assertTextEquals("10%")
        compose.onNodeWithTag("tool-detail-tool-codex-model-cost-only:model-value",useUnmergedTree=true).assertTextEquals("$0.1000 + 10 未定价 tokens")
        saveVisualEvidence(compose.activity,compose.onRoot(),"detail-models-${if(dark) "dark" else "light"}.png")
        compose.onNodeWithTag(public).performClick()
        compose.onNodeWithTag("tool-model-inspection-cost").assertTextEquals("已知估算费用：$0.9000 + 40 未定价 tokens")
        compose.onNodeWithText("关闭").performClick()
        compose.runOnIdle { state.value=state.value.copy(snapshot=source(1200));prefs.value=prefs.value.copy(currency="CNY",currencyRates=mapOf("CNY" to "7")) }
        compose.onNodeWithTag("tool-detail-mode-models").assertIsSelected()
        show(public);compose.onNodeWithTag("$public-value",useUnmergedTree=true).assertTextEquals("1,100")
        compose.onNodeWithTag("$public-percent",useUnmergedTree=true).assertTextEquals("92%")
        compose.onNodeWithTag(public).performClick();compose.onNodeWithTag("tool-model-inspection-cost").assertTextEquals("已知估算费用：¥7.70 + 40 未定价 tokens")
        saveVisualEvidence(compose.activity,compose.onNodeWithTag("tool-model-inspection"),"detail-cost-${if(dark) "dark" else "light"}.png")
        compose.onNodeWithText("关闭").performClick()
        open("claude");compose.onNodeWithTag("breakdown-tool-codex").assertIsNotSelected()
        compose.onNodeWithTag("tool-detail-tool-codex-models").assertDoesNotExist()
        compose.onNodeWithTag("tool-detail-mode-models").assertIsSelected()
        compose.onNodeWithTag("tool-detail-mode-tokens").performClick();show("tool-detail-tool-claude-hit")
        compose.onNodeWithTag("tool-detail-tool-claude-hit-value").assertTextEquals("700")
        compose.onNodeWithTag("tool-detail-mode-models").performClick();open("opencode")
        compose.onNodeWithTag("tool-detail-switch").assertDoesNotExist()
        show("tool-detail-tool-opencode-miss");compose.onNodeWithTag("tool-detail-tool-opencode-miss-value").assertTextEquals("200")
        open("codex");compose.onNodeWithTag("tool-detail-mode-models").assertIsSelected()
        show(public);compose.onNodeWithTag(public).performClick()
        compose.runOnIdle { state.value=state.value.copy(snapshot=source(1200,false)) }
        compose.onNodeWithTag("tool-model-inspection").assertDoesNotExist();compose.onNodeWithTag("tool-detail-switch").assertDoesNotExist()
        show("tool-detail-tool-codex-miss");compose.onNodeWithTag("tool-detail-tool-codex-miss-value").assertTextEquals("1,199")
        compose.runOnIdle { state.value=state.value.copy(snapshot=source(1200,true)) }
        compose.onNodeWithTag("tool-model-inspection").assertDoesNotExist()
        compose.onNodeWithTag("tool-detail-mode-models").assertIsSelected()
        assertEquals(1000L,original.periods.getValue("today").toolDetails.getValue("codex").tokens)
        assertEquals(0,actions.reads)
    }
    @Test fun modelOnlyAndMissingClassificationPreserveUnknownVersusExplicitZero() {
        val actions=Actions();val source=HubParser.parse("""{"periods":{"today":{"totalTokens":100,"clients":{"codex":100},"clientModels":{"codex":{"public":100}},"clientModelCosts":{"codex":{"public":0}}}}}""")
        val state=mutableStateOf(HubState(snapshot=source))
        val dark=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
        compose.activity.runOnUiThread { compose.activity.setContent { TokenTheme(dark) { HubScreen(state.value,actions) } } }
        compose.onNodeWithTag("hub-total-tokens").performClick()
        compose.onNodeWithText("Token 分类数据不可用。").assertExists();compose.onNodeWithText("关闭").performClick()
        select("tools");open("codex");compose.onNodeWithTag("tool-detail-switch").assertDoesNotExist()
        val public="tool-detail-tool-codex-model-public:model"
        show(public);compose.onNodeWithTag("$public-value",useUnmergedTree=true).assertTextEquals("100")
        compose.onNodeWithTag(public).performClick();compose.onNodeWithTag("tool-model-inspection-cost").assertTextEquals("已知估算费用：$0.0000")
        compose.onNodeWithText("关闭").performClick()
        compose.runOnIdle { state.value=state.value.copy(snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":100,"clients":{"codex":100}}}}""")) }
        compose.onNodeWithTag("tool-detail-tool-codex-models").assertDoesNotExist()
        compose.onNodeWithTag("breakdown-tool-codex").assertHasNoClickAction()
        compose.runOnIdle { state.value=state.value.copy(snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":9223372036854775807,"clients":{"codex":9223372036854775807},"clientCacheReads":{"codex":9223372036854775807},"clientOutputs":{"codex":0},"clientUnclassifiedTokens":{"codex":0}}}}""")) }
        show("tool-detail-tool-codex-hit")
        compose.onNodeWithTag("tool-detail-tool-codex-hit-value").assertTextEquals("9,223,372,036,854,775,807")
        val layout=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("tool-detail-tool-codex-hit-value").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        assertTrue(layout.isNotEmpty() && layout.none { it.hasVisualOverflow })
        assertEquals(0,actions.reads)
    }
}
