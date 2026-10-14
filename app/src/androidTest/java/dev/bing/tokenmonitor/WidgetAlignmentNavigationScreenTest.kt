package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WidgetAlignmentNavigationScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private class Actions : HubUiActions {
        var reads = 0
        override fun connect(address: String, credential: String, alternate: String) { reads++ }
        override fun refreshSaved() { reads++ }
        override fun switchEndpoint() { reads++ }
        override fun hasAlternate() = false
        override fun endpointType() = "https"
        override fun loadHistory() { reads++ }
        override fun loadModelNames() { reads++ }
        override fun loadSubscriptions() { reads++ }
        override fun forget() {}
        override fun renameDevice(id: String, input: String) {}
    }
    @Test fun eachInstanceUsesItsPageAndNaturalPeriodEvenWhenViewHiddenAndMonthPreferenceIsLast30() {
        val actions = Actions()
        val event = mutableStateOf(WidgetOpenRequest(1, "models", "month", 701))
        val preferences = DisplayPreferences(language = LanguageChoice.ENGLISH, monthMode = "last30", lastPeriod = "allTime",
            appearance = AppearancePreferences(reduceMotion = "on"), main = MainDisplayPreferences(hiddenViews = setOf("models", "tools", "trends")))
        val state = HubState(snapshot = HubParser.parse("""{"periods":{"today":{"totalTokens":1000},"month":{"totalTokens":12000},"allTime":{"totalTokens":20000}}}"""))
        compose.activity.runOnUiThread { compose.activity.setContent {
            TokenTheme(false, preferences.language, preferences.appearance) { HubScreen(state, actions, preferences = preferences, startup = HubStartup.HOME, widgetOpenRequest = event.value) }
        } }
        compose.onNodeWithTag("hub-period-month").assertIsSelected()
        compose.onNodeWithTag("hub-view-selector").performClick(); compose.onNodeWithTag("view-models").assertIsSelected(); compose.onNodeWithTag("view-models").performClick()
        compose.runOnIdle { event.value = WidgetOpenRequest(2, "tools", "today", 702) }
        compose.onNodeWithTag("hub-period-today").assertIsSelected()
        compose.onNodeWithTag("hub-view-selector").performClick(); compose.onNodeWithTag("view-tools").assertIsSelected(); compose.onNodeWithTag("view-tools").performClick()
        compose.runOnIdle { event.value = WidgetOpenRequest(3, "summary", "allTime", 703) }
        compose.onNodeWithTag("hub-period-allTime").assertIsSelected()
        compose.onNodeWithTag("hub-view-selector").performClick(); compose.onNodeWithTag("view-home").assertIsSelected(); compose.onNodeWithTag("view-home").performClick()
        compose.runOnIdle { event.value = WidgetOpenRequest(4, "activity", "today", 704) }
        compose.onNodeWithTag("hub-view-selector").performClick(); compose.onNodeWithTag("view-trends").assertIsSelected()
        assertEquals(0, actions.reads)
    }
    @Test fun configurationCancelDoesNotSaveAndCustomChoiceCapsTwoAccounts() {
        var saved: WidgetConfiguration? = null; var cancelled = false
        val initial = WidgetConfiguration(mode = "dashboard", period = "month")
        val snapshot = HubParser.parse("""{"periods":{},"limits":{"providers":[
          {"provider":"codex","accountEmail":"one@example.invalid","windows":[]},
          {"provider":"codex","accountEmail":"two@example.invalid","windows":[]},
          {"provider":"claude","accountEmail":"three@example.invalid","windows":[]}]}}""")
        compose.activity.runOnUiThread { compose.activity.setContent {
            TokenTheme(false, LanguageChoice.ENGLISH, AppearancePreferences(reduceMotion = "on")) {
                WidgetConfigurationContent(initial, snapshot, false, { cancelled = true }, { saved = it })
            }
        } }
        compose.onNodeWithTag("widget-quota-custom").performScrollTo().performClick()
        val keys = LimitDisplay.groups(snapshot.limits).map(::widgetQuotaAccountId)
        compose.onNodeWithTag("widget-account-${keys[0]}").performScrollTo().performClick()
        compose.onNodeWithTag("widget-account-${keys[1]}").performScrollTo().performClick()
        compose.onNodeWithTag("widget-account-${keys[2]}").assertIsNotEnabled()
        compose.onNodeWithTag("widget-config-cancel").performScrollTo().performClick()
        assertTrue(cancelled); assertNull(saved)
        compose.onNodeWithTag("widget-config-save").performClick()
        assertEquals("month", saved?.period)
        assertFalse(saved!!.quotaAutomatic)
        assertEquals(keys.take(2), saved?.quotaAccounts)
    }
}
