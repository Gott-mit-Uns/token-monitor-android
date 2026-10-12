package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic public data under real Android font settings, including nonlinear sp conversion. */
class HomeLargeFontScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private class Actions : HubUiActions {
        var reads = 0
        override fun connect(address:String,credential:String,alternate:String) { reads++ }
        override fun refreshSaved() { reads++ }
        override fun switchEndpoint() { reads++ }
        override fun hasAlternate() = false
        override fun endpointType() = "https"
        override fun loadHistory() { reads++ }
        override fun loadModelNames() { reads++ }
        override fun loadSubscriptions() { reads++ }
        override fun forget() {}
        override fun renameDevice(id:String,input:String) {}
    }
    private fun complete(tag:String, singleLine:Boolean = false) {
        val node = compose.onNodeWithTag(tag, true).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertFalse("$tag must paint complete text: size=${layout.size}, width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}, lines=${layout.lineCount}, text=${layout.layoutInput.text}, style=${layout.layoutInput.style}", layout.hasVisualOverflow)
        if (singleLine) assertEquals("$tag must not split digits from percent", 1, layout.lineCount)
        repeat(layout.lineCount) { assertFalse(layout.isLineEllipsized(it)) }
    }
    @Test fun exactHeadlineAndPeriodsAndSharesRemainComplete() {
        val args = InstrumentationRegistry.getArguments()
        val dark = args.getString("visualDark") != "false"
        val actions = Actions()
        val snapshot = HubParser.parse("""{"periods":{"today":{"totalTokens":199470859,"costUsd":32.78,"clients":{"codex":161571396,"deepseek":37899463}}}}""")
        val state = HubState(snapshot=snapshot,history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"))
        val prefs = DisplayPreferences(language=LanguageChoice.CHINESE,homeModules=listOf("tools"),main=MainDisplayPreferences(compactTotal=true,compactUnits="localized"),appearance=AppearancePreferences(reduceMotion="on"))
        var observed = 0f
        compose.activity.runOnUiThread { compose.activity.setContent {
            observed = LocalDensity.current.fontScale
            TokenTheme(dark,prefs.language,prefs.appearance) { HubScreen(state,actions,preferences=prefs) }
        } }
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("199,470,859")
        args.getString("expectFontScale")?.toFloat()?.let { assertEquals(it, observed, .01f) }
        saveVisualEvidence(compose.activity,compose.onRoot(),"home-before-assert-$observed-${if(dark) "dark" else "light"}.png")
        complete("hub-total-tokens")
        complete("hub-total-compact")
        complete("hub-period-label-allTime",true)
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("home-tool-codex"))
        complete("home-tool-codex-value",true)
        complete("home-tool-codex-percent",true)
        saveVisualEvidence(compose.activity,compose.onRoot(),"home-font-$observed-${if(dark) "dark" else "light"}.png")
        assertEquals(0,actions.reads)
    }
}
