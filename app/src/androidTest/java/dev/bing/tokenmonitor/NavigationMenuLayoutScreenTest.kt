package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Measures real popup/row/text layouts; ordinary portrait tests never scroll first. */
class NavigationMenuLayoutScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val dark get() = InstrumentationRegistry.getArguments().getString("visualDark") != "false"
    private val pages = MainDisplay.ids.map { id -> HubPage.entries.first { it.name.lowercase() == id } }
    private val density get() = compose.activity.resources.displayMetrics.density
    private class Actions : HubUiActions {
        var reads = 0
        override fun connect(address: String, credential: String, alternate: String) { reads++ }
        override fun refreshSaved() { reads++ }
        override fun switchEndpoint() { reads++ }
        override fun hasAlternate() = false
        override fun endpointType() = "https"
        override fun loadHistory() { reads++ }
        override fun loadModelNames() { reads++ }
        override fun loadPricing() { reads++ }
        override fun loadSubscriptions() { reads++ }
        override fun loadDeviceHistory() { reads++ }
        override fun forget() { reads++ }
        override fun renameDevice(id: String, input: String) { reads++ }
    }
    private fun state() = HubState(
        snapshot = HubParser.parse("""{"periods":{"today":{"totalTokens":1000},"month":{"totalTokens":1000},"allTime":{"totalTokens":1000}}}"""),
        history = HubHistory(emptyList(), emptyList()),
        modelNames = ModelNames(emptyMap(), "off")
    )
    private fun expectedLabel(page: HubPage, language: LanguageChoice) = when (page) {
        HubPage.HOME -> if (language == LanguageChoice.CHINESE) "主页" else "Home"
        HubPage.LIMITS -> if (language == LanguageChoice.CHINESE) "额度" else "Limits"
        HubPage.TOOLS -> if (language == LanguageChoice.CHINESE) "工具" else "Tools"
        HubPage.MODELS -> if (language == LanguageChoice.CHINESE) "模型" else "Models"
        HubPage.PROJECTS -> if (language == LanguageChoice.CHINESE) "项目" else "Projects"
        HubPage.SESSIONS -> if (language == LanguageChoice.CHINESE) "会话" else "Sessions"
        HubPage.DEVICES -> if (language == LanguageChoice.CHINESE) "设备" else "Devices"
        HubPage.TRENDS -> if (language == LanguageChoice.CHINESE) "趋势" else "Trends"
        else -> error("Only the eight Hub views belong in this fixture")
    }
    private fun save(name: String) = saveVisualEvidence(compose.activity, compose.onNodeWithTag("hub-view-menu"), "navigation-layout-$name-${if (dark) "dark" else "light"}.png")

    @Test fun ordinaryPortraitMenusShowEveryChineseAndEnglishEntryAcrossTextScales() {
        val actions = Actions()
        val publicState = state()
        val preferences = mutableStateOf(DisplayPreferences(language = LanguageChoice.CHINESE, homeModules = emptyList(), appearance = AppearancePreferences(reduceMotion = "on")))
        val systemTextScale = mutableStateOf(1f)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                val original = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(original.density, systemTextScale.value)) {
                    TokenTheme(dark, preferences.value.language, preferences.value.appearance) {
                        HubScreen(publicState, actions, preferences = preferences.value)
                    }
                }
            }
        }
        for (language in listOf(LanguageChoice.CHINESE, LanguageChoice.ENGLISH)) {
            for (systemScale in listOf(1f, 1.4f, 2f)) {
                for (appScale in listOf("standard", "largest")) {
                    compose.runOnIdle {
                        systemTextScale.value = systemScale
                        preferences.value = preferences.value.copy(language = language, appearance = preferences.value.appearance.copy(textSize = appScale))
                    }
                    compose.onNodeWithTag("hub-view-selector").performClick()
                    assertNavigationMenuLayout(compose, pages, requireNoScroll = navigationWindowCanFitDefaultRows(compose.activity), density = density)
                    pages.forEach { page -> compose.onNodeWithTag("view-${page.name.lowercase()}-label", useUnmergedTree = true).assertTextEquals(expectedLabel(page, language)) }
                    compose.onNodeWithTag("view-home").assertIsSelected()
                    if (systemScale == 1f && appScale == "standard" || systemScale == 2f && appScale == "largest") save("${language.name.lowercase()}-${systemScale.toString().replace('.', '-') }-$appScale")
                    compose.onNodeWithTag("view-home").performKeyInput { pressKey(Key.Escape) }
                    compose.onNodeWithTag("hub-view-menu").assertDoesNotExist()
                }
            }
        }
        assertEquals("Layout and text scaling must not cause Hub reads", 0, actions.reads)
    }

    @Test fun reorderedAndHiddenEntriesFitTogetherEvenWhenCurrentPageIsLast() {
        val actions = Actions()
        val publicState = state()
        val preferences = mutableStateOf(DisplayPreferences(language = LanguageChoice.ENGLISH, homeModules = emptyList(), appearance = AppearancePreferences(reduceMotion = "on")))
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                TokenTheme(dark, preferences.value.language, preferences.value.appearance) {
                    HubScreen(publicState, actions, preferences = preferences.value)
                }
            }
        }
        val reversed = MainDisplay.ids.reversed()
        val configurations = listOf(
            MainDisplayPreferences(viewOrder = reversed),
            MainDisplayPreferences(viewOrder = reversed, hiddenViews = setOf("limits", "tools")),
            MainDisplayPreferences(viewOrder = listOf("projects", "sessions", "home"))
        )
        for ((index, configuration) in configurations.withIndex()) {
            compose.runOnIdle { preferences.value = preferences.value.copy(main = configuration) }
            compose.onNodeWithTag("hub-view-selector").performClick()
            val visible = MainDisplay.visible(configuration).map { id -> pages.first { it.name.lowercase() == id } }
            assertNavigationMenuLayout(compose, visible, requireNoScroll = navigationWindowCanFitDefaultRows(compose.activity), density = density)
            (MainDisplay.ids - visible.map { it.name.lowercase() }.toSet()).forEach { id -> compose.onNodeWithTag("view-$id").assertDoesNotExist() }
            compose.onNodeWithTag("view-home").assertIsSelected()
            if (index == 0) save("reversed-current-last")
            compose.onNodeWithTag("view-home").performKeyInput { pressKey(Key.Escape) }
        }
        assertEquals(0, actions.reads)
    }

    @Test fun anExplicitlyConstrainedPopupScrollsWhileKeepingFullSizeTouchTargets() {
        val expanded = mutableStateOf(true)
        val selection = mutableStateOf("")
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                TokenTheme(dark, LanguageChoice.ENGLISH, AppearancePreferences(reduceMotion = "on")) {
                    Surface(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.align(Alignment.BottomStart).size(48.dp)) {
                                DesktopOptionMenu(
                                    expanded.value,
                                    pages.map { DesktopMenuOption(it.name.lowercase(), expectedLabel(it, LanguageChoice.ENGLISH), "view-${it.name.lowercase()}", it.icon) },
                                    "home", "hub-view-menu", width = 126.dp, above = true,
                                    wrapNavigation = true, horizontalNavigation = true, maxHeight = 180.dp,
                                    dismiss = { expanded.value = false }
                                ) { id -> selection.value = id; expanded.value = false }
                            }
                        }
                    }
                }
            }
        }
        assertNavigationMenuLayout(compose, pages, requireNoScroll = false, density = density)
        val scroll = compose.onNodeWithTag("hub-view-menu").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("A genuinely short viewport remains scrollable", scroll.maxValue() > 0f)
        // Scrolling is intentional only in this explicitly constrained scenario.
        compose.onNodeWithTag("view-trends").performScrollTo().assertIsDisplayed().performClick()
        assertEquals("trends", selection.value)
        compose.onNodeWithTag("hub-view-menu").assertDoesNotExist()
    }
}
