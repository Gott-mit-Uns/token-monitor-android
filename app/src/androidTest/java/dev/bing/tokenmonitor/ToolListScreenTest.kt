package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Public synthetic data, isolated presentation file, no real Hub or network calls. */
class ToolListScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val snapshot = HubParser.parse("""{"periods":{"today":{"totalTokens":480,"costUsd":0.48,"clients":{"claude":300,"codex":100,"opencode":60,"hermes":20},"clientCosts":{"claude":0.3,"codex":0.1,"opencode":0.06,"hermes":0.02}}}}""")
    private val actions = object : HubUiActions {
        override fun connect(address: String, credential: String, alternate: String) {}
        override fun refreshSaved() {}
        override fun switchEndpoint() {}
        override fun hasAlternate() = false
        override fun endpointType() = "https"
        override fun loadModelNames() {}
        override fun loadHistory() {}
        override fun loadSubscriptions() {}
        override fun forget() {}
        override fun renameDevice(id: String, input: String) {}
    }
    private fun select(page: String) {
        compose.onNodeWithTag("hub-view-selector").performClick()
        compose.onNodeWithTag("view-$page").performScrollTo().performClick()
    }
    private fun settings() {
        compose.onNodeWithTag("hub-settings-toggle").performClick()
        openSettingsSection(compose,"tools")
        compose.onNodeWithTag("tool-list-toggle").assertDoesNotExist()
        compose.onNodeWithTag("tool-list-search").assertExists()
    }
    private fun action(tag: String) {
        val ordering=tag.startsWith("tool-up-") || tag.startsWith("tool-down-")
        val id=tag.removePrefix("tool-up-").removePrefix("tool-down-")
        val target=if(ordering) "tool-order-$id" else tag
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(target))
        if(ordering) compose.onNodeWithTag(target).performClick()
        compose.onNodeWithTag(tag).performClick()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(if(ordering) "tool-setting-$id" else tag))
    }
    private fun show(text: String) {
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }
    private fun totals() {
        compose.onNodeWithTag("hub-total-tokens").assertIsDisplayed().assertTextEquals("480")
        compose.onNodeWithText("$0.4800").assertIsDisplayed()
    }
    private fun before(first: String, second: String) {
        // Short landscape screens virtualize rows. Compare each visible row's
        // scroll position instead of assuming both are composed simultaneously.
        fun position(text: String): Pair<Float, Float> {
            show(text)
            val scroll = compose.onNodeWithTag("hub-content").fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].value()
            return scroll to compose.onNodeWithText(text).getUnclippedBoundsInRoot().top.value
        }
        val a = position(first)
        val b = position(second)
        assertTrue("$first must precede $second in the rendered list", a.first < b.first || (a.first == b.first && a.second < b.second))
    }
    private fun save(name: String, suffix: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        saveVisualEvidence(compose.activity, compose.onRoot(), "tool-$name-$suffix.png")
    }
    @Test fun visibilityPinsManualOrderAndResetsAffectToolViewAndSurviveReloadWhileHomeRetainsSourceRanking() {
        val name = "test-tool-screen-${java.util.UUID.randomUUID()}.bin"
        val store = DisplayPreferenceStore(compose.activity, name)
        val dark = InstrumentationRegistry.getArguments().getString("visualDark") != "false"
        val suffix = if (dark) "dark" else "light"
        val preferences = mutableStateOf(DisplayPreferences(theme = if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT, homeModules = listOf("tools")))
        try {
            compose.activity.runOnUiThread { compose.activity.setContent { TokenTheme(dark) {
                HubScreen(HubState(snapshot = snapshot), actions, preferences = preferences.value, updateDisplay = { change ->
                    store.save(change.apply(preferences.value))
                    preferences.value = DisplayPreferenceStore(compose.activity, name).load()
                })
            } } }
            select("tools")
            show("Claude Code")
            settings()
            action("tool-pin-hermes")
            compose.onNodeWithTag("tool-pin-hermes").assertIsOn()
            action("tool-visible-claude")
            compose.onNodeWithTag("tool-visible-claude").assertIsOff()
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("tool-setting-hermes"))
            save("settings", suffix)
            select("tools")
            compose.onNodeWithText("Claude Code").assertDoesNotExist()
            show("Hermes Agent")
            before("Hermes Agent", "Codex")
            totals()
            save("list-pinned", suffix)
            select("home")
            // Original Home ignores the detailed tool list visibility/pins and shows usage leaders.
            show("Claude Code")
            show("Hermes Agent")
            totals()
            settings()
            action("tool-up-codex")
            assertTrue(store.load().toolOrder.isNotEmpty())
            assertTrue(store.load().pinnedTools.isEmpty())
            select("tools")
            show("Codex")
            before("Codex", "Hermes Agent")
            save("list-manual", suffix)
            select("home")
            save("home-manual", suffix)
            settings()
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("tool-list-search"))
            compose.onNodeWithTag("tool-list-search").performTextInput("Hermes")
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("tool-order-hermes"))
            compose.onNodeWithTag("tool-order-hermes").assertIsNotEnabled()
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("tool-list-search-clear"))
            compose.onNodeWithTag("tool-list-search-clear").performClick()
            compose.onNodeWithTag("tool-list-search").performTextReplacement(" code CLA ")
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("tool-setting-claude"))
            compose.onNodeWithTag("tool-setting-claude").assertIsDisplayed()
            compose.onNodeWithTag("tool-visible-claude").assertIsOff()
            compose.onNodeWithTag("tool-setting-codex").assertDoesNotExist()
            compose.onNodeWithTag("tool-list-search").performTextReplacement("public-no-match")
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("tool-list-empty"))
            compose.onNodeWithTag("tool-list-empty").assertIsDisplayed()
            assertTrue("Filtering must retain saved visibility", "claude" in store.load().hiddenTools)
            compose.onNodeWithTag("tool-list-search-clear").performClick()
            compose.onNodeWithTag("tool-list-search").performImeAction()
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("tool-list-reset"))
            compose.onNodeWithTag("tool-list-reset").performClick()
            compose.onNodeWithTag("tool-list-show-all").performClick()
            assertEquals(DisplayPreferences(theme = if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT, homeModules = listOf("tools")), store.load())
            select("tools")
            show("Claude Code")
            before("Claude Code", "Codex")
        } finally { store.clear() }
    }
    @Test fun allHiddenToolsShowAnExplanationAndTotalsRemainComplete() {
        val dark = InstrumentationRegistry.getArguments().getString("visualDark") != "false"
        val preferences = mutableStateOf(DisplayPreferences(homeModules = listOf("tools"), hiddenTools = setOf("claude", "codex", "opencode", "hermes")))
        compose.activity.runOnUiThread { compose.activity.setContent { TokenTheme(dark) {
            HubScreen(HubState(snapshot = snapshot), actions, preferences = preferences.value, updateDisplay = { preferences.value = it.apply(preferences.value) })
        } } }
        select("tools")
        show("列表中的工具已隐藏，可在设置里重新显示。")
        totals()
        compose.onNodeWithText("Claude Code").assertDoesNotExist()
        save("list-hidden", if (dark) "dark" else "light")
        settings()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("tool-list-show-all"))
        compose.onNodeWithTag("tool-list-show-all").performClick()
        select("tools")
        show("Claude Code")
        totals()
    }
}
