package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QuotaWidgetNavigationScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private class Actions:HubUiActions {
        var reads=0
        override fun connect(address:String,credential:String,alternate:String) {reads++}
        override fun refreshSaved() {reads++}
        override fun switchEndpoint() {reads++}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadHistory() {reads++}
        override fun loadModelNames() {reads++}
        override fun loadPricing() {reads++}
        override fun loadSubscriptions() {reads++}
        override fun forget() {}
        override fun renameDevice(id:String,input:String) {}
    }
    @Test fun initialAndRepeatedQuotaRouteOpensEvenWhenHiddenWithoutNavigationReads() {
        val actions=Actions();val event=mutableLongStateOf(1L)
        val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH,appearance=AppearancePreferences(reduceMotion="on"),main=MainDisplayPreferences(hiddenViews=setOf("limits")))
        val state=HubState(snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":1000}},"limits":{"providers":[{"provider":"codex","windows":[{"kind":"weekly","remainingPercent":70}]}]}}"""))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(false,prefs.language,prefs.appearance) {HubScreen(state,actions,preferences=prefs,startup=HubStartup.HOME,quotaOpenRequest=event.longValue)}}}
        compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-limits").assertIsSelected()
        compose.onNodeWithTag("view-home").performClick()
        compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-home").assertIsSelected();compose.onNodeWithTag("view-home").performClick()
        compose.runOnIdle {event.longValue++}
        compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-limits").assertIsSelected()
        assertEquals(0,actions.reads)
    }
}
