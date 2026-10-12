package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual Hub trends surface and shared currency, using public source-shaped histories. */
class HistorySummaryScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private class Actions : HubUiActions {
        var reads = 0
        override fun connect(address: String, credential: String, alternate: String) {}
        override fun refreshSaved() { reads++ }
        override fun switchEndpoint() {}
        override fun hasAlternate() = false
        override fun endpointType() = "https"
        override fun loadHistory() { reads++ }
        override fun loadModelNames() { reads++ }
        override fun loadSubscriptions() { reads++ }
        override fun forget() {}
        override fun renameDevice(id: String, input: String) {}
    }
    private fun history(cost: String, missing: Long?, dailyCost: String = "0", summary: Boolean = true): HubHistory =
        HistoryParser.parse("""{"daily":[{"date":"2026-10-09","tokens":100,"cost":$dailyCost,"unpricedTokens":20}],"monthly":[{"month":"2026-10","tokens":10000,"cost":$cost}],"summary":${if (summary) """{"totalTokens":10000,"totalCost":$cost${missing?.let { ",\"unpricedTokens\":$it" }.orEmpty()},"activeDays":1,"currentStreak":1,"activeTimeMs":60000,"peakDayTokens":100,"favoriteModel":"public-model","messages":1}""" else "null"}}""")

    @Test fun lifetimeCostPreservesUnknownPartialFreeAndLegacySemanticsAcrossCurrencyChanges() {
        val source = HubParser.parse("""{"periods":{"today":{"totalTokens":100,"costUsd":0},"allTime":{"totalTokens":10000,"costUsd":0}}}""")
        val initial = history("0", 10000)
        val state = mutableStateOf(HubState(snapshot = source, history = initial))
        val prefs = mutableStateOf(DisplayPreferences(language = LanguageChoice.ENGLISH))
        val actions = Actions()
        val dark = InstrumentationRegistry.getArguments().getString("visualDark") != "false"
        compose.activity.runOnUiThread { compose.activity.setContent {
            TokenTheme(dark, prefs.value.language, prefs.value.appearance) {
                HubScreen(state.value, actions, preferences = prefs.value)
            }
        } }
        compose.onNodeWithTag("hub-view-selector").performClick()
        compose.onNodeWithTag("view-trends").performScrollTo().performClick()
        compose.onNodeWithTag("trends-main-open-history").performClick()
        compose.onNodeWithTag("dashboard-open-records").performScrollTo().performClick()
        fun showCost() {
            compose.onNodeWithTag("dashboard-scroll").performScrollToNode(hasTestTag("history-summary-cost"))
            if (compose.onAllNodesWithTag("history-summary-unpriced").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithTag("history-summary-unpriced").performScrollTo().assertIsDisplayed()
            }
            compose.onNodeWithTag("history-summary-cost").assertIsDisplayed()
        }
        fun save(name: String) = saveVisualEvidence(compose.activity, compose.onRoot(), "history-summary-$name-${if (dark) "dark" else "light"}.png")
        showCost()
        compose.onNodeWithTag("history-summary-cost").assertTextEquals("—")
        compose.onNodeWithTag("history-summary-unpriced").assertTextEquals("10,000 tokens have no price")
        save("unknown")

        val partial = history("1.25", 2000, "0.25")
        compose.runOnIdle { state.value = state.value.copy(history = partial) }
        showCost()
        compose.onNodeWithTag("history-summary-cost").assertTextEquals("$1.2500")
        compose.onNodeWithTag("history-summary-unpriced").assertTextEquals("2,000 tokens have no price")
        compose.runOnIdle { prefs.value = prefs.value.copy(currency = "CNY", currencyRates = mapOf("CNY" to "7")) }
        showCost()
        compose.onNodeWithTag("history-summary-cost").assertTextEquals("¥8.75")
        compose.onNodeWithTag("history-summary-unpriced").assertTextEquals("2,000 tokens have no price")
        save("partial")
        assertEquals("1.25", partial.summary!!.totalCost)
        assertEquals("0.25", partial.daily.single().cost)
        assertEquals(2000L, partial.summary!!.unpricedTokens)

        compose.runOnIdle { state.value = state.value.copy(history = history("0", 0)) }
        showCost()
        compose.onNodeWithTag("history-summary-cost").assertTextEquals("¥0.0000")
        compose.onNodeWithTag("history-summary-unpriced").assertDoesNotExist()
        save("free")
        // Legacy absence follows the desktop's missing-count compatibility behavior,
        // and must not infer lifetime missing prices from the retained daily window.
        compose.runOnIdle { state.value = state.value.copy(history = history("0", null)) }
        showCost()
        compose.onNodeWithTag("history-summary-cost").assertTextEquals("¥0.0000")
        compose.onNodeWithTag("history-summary-unpriced").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(history = history("0", null, summary = false)) }
        compose.onNodeWithTag("history-summary").assertDoesNotExist()
        compose.onNodeWithTag("trends-history-back").performClick()
        compose.onNodeWithTag("hub-view-selector").assertIsDisplayed()
        assertEquals(source, state.value.snapshot)
        assertEquals(10000L, initial.summary!!.unpricedTokens)
        assertEquals(0, actions.reads)
    }
}
