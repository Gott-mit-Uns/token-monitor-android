package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Menu and all affected views use public synthetic device history. */
class FixedPeriodScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val snapshot = HubParser.parse("""{"deviceHistoryRevision":"public","periods":{"today":{"totalTokens":500,"costUsd":0.5},"month":{"totalTokens":6000,"costUsd":6},"allTime":{"totalTokens":90000,"costUsd":90}},"devices":[{"deviceId":"public","hostname":"Public Desktop","periodWindows":{"timeZone":"Etc/UTC","today":{"key":"2026-10-09","endsAt":"2999-10-10T00:00:00Z"}},"periods":{"today":{"totalTokens":500,"costUsd":0.5,"clients":{"codex":500},"clientCosts":{"codex":0.5},"models":{"demo-model":500},"modelCosts":{"demo-model":0.5},"cacheReadTokens":250,"cacheWriteTokens":0,"outputTokens":100,"unclassifiedTokens":0,"clientCacheReads":{"codex":250},"clientCacheWrites":{"codex":0},"clientOutputs":{"codex":100},"clientUnclassifiedTokens":{"codex":0},"modelCacheReads":{"demo-model":250},"modelCacheWrites":{"demo-model":0},"modelOutputs":{"demo-model":100},"modelUnclassifiedTokens":{"demo-model":0},"capabilities":{"tokenComponents":true}},"allTime":{"totalTokens":90000}}}]}""")
    private val response = """{"devices":[{"deviceId":"public","historyAvailable":true,"history":{"daily":[{"date":"2026-09-20","tokens":300,"cost":0.3,"tokenComponentsAvailable":true,"unclassifiedTokens":0,"perClient":{"codex":{"tokens":300,"cost":0.3,"unclassifiedTokens":0}},"perModel":{"demo-model":{"tokens":300,"cost":0.3,"unclassifiedTokens":0}}},{"date":"2026-10-03","tokens":100,"cost":0.1,"tokenComponentsAvailable":true,"unclassifiedTokens":0,"perClient":{"codex":{"tokens":100,"cost":0.1,"unclassifiedTokens":0}},"perModel":{"demo-model":{"tokens":100,"cost":0.1,"unclassifiedTokens":0}}},{"date":"2026-10-08","tokens":200,"cost":0.2,"tokenComponentsAvailable":true,"unclassifiedTokens":0,"perClient":{"codex":{"tokens":200,"cost":0.2,"unclassifiedTokens":0}},"perModel":{"demo-model":{"tokens":200,"cost":0.2,"unclassifiedTokens":0}}},{"date":"2026-10-09","tokens":50,"cost":0.05,"unclassifiedTokens":0,"tokenComponentsAvailable":true}]}}]}"""
    private open class Actions : HubUiActions {
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
    private fun history(snapshot: Snapshot) = DeviceHistoryParser.parse(DeviceHistoryParser.project(response, FixedPeriods.signature(snapshot)))
    private fun select(view: String) {
        compose.onNodeWithTag("hub-view-selector").performClick()
        compose.onNodeWithTag("view-$view").performScrollTo().performClick()
    }
    private fun mode(value: String) {
        compose.onNodeWithTag("hub-period-month").performClick()
        // An inactive MONTH slot selects its remembered mode first, like desktop.
        if (compose.onAllNodesWithTag("hub-period-menu-$value").fetchSemanticsNodes().isEmpty()) compose.onNodeWithTag("hub-period-month").performClick()
        compose.onNodeWithTag("hub-period-menu-$value").performClick()
    }
    private fun total(tokens: String) {
        compose.onNodeWithTag("hub-total-tokens").assertIsDisplayed().assertTextEquals(tokens)
    }
    private fun show(value: String) {
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText(value))
        compose.onNodeWithText(value).assertIsDisplayed()
    }
    private fun showCost(name: String) {
        val matcher = hasText(name) and hasText("$0.8000")
        compose.onNodeWithTag("hub-content").performScrollToNode(matcher)
        compose.onNode(matcher).assertIsDisplayed()
    }
    private fun save(view: String, dark: Boolean) {
        compose.waitForIdle()
        saveVisualEvidence(compose.activity, compose.onRoot(), "fixed-$view-${if (dark) "dark" else "light"}.png")
    }
    @Test fun desktopMenuAppliesToToolsModelsDevicesAndPersistsPreferredMode() {
        val dark = InstrumentationRegistry.getArguments().getString("visualDark") != "false"
        val name = "test-fixed-ui-${UUID.randomUUID()}.bin"
        val store = DisplayPreferenceStore(compose.activity, name)
        val preferences = mutableStateOf(DisplayPreferences())
        try {
            compose.activity.runOnUiThread { compose.activity.setContent { TokenTheme(dark) {
                HubScreen(HubState(snapshot = snapshot, deviceHistories = history(snapshot)), Actions(), preferences = preferences.value,
                    updateDisplay = { store.save(it.apply(preferences.value)); preferences.value = DisplayPreferenceStore(compose.activity, name).load() })
            } } }
            total("500")
            mode("last7")
            total("800")
            compose.onNodeWithTag("hub-period-month").assertIsSelected()
            assertEquals("last7", store.load().monthMode)
            assertEquals("last7", store.load().lastPeriod)
            save("range", dark)
            compose.onNodeWithTag("hub-period-month").performClick()
            compose.onNodeWithTag("hub-period-menu-last7").assertIsSelected().assertIsFocused()
            saveVisualEvidence(compose.activity,compose.onNodeWithTag("hub-period-menu"),"fixed-menu-${if(dark) "dark" else "light"}.png")
            compose.onNodeWithTag("hub-period-menu-last7").performKeyInput {pressKey(Key.MoveHome)}
            compose.onNodeWithTag("hub-period-menu-month").assertIsFocused()
            compose.onNodeWithTag("hub-period-menu-month").performKeyInput {pressKey(Key.DirectionUp)}
            compose.onNodeWithTag("hub-period-menu-last30").assertIsFocused()
            compose.onNodeWithTag("hub-period-menu-last30").performKeyInput {pressKey(Key.DirectionDown)}
            compose.onNodeWithTag("hub-period-menu-month").assertIsFocused()
            compose.onNodeWithTag("hub-period-menu-month").performKeyInput {pressKey(Key.MoveEnd)}
            compose.onNodeWithTag("hub-period-menu-last30").assertIsFocused()
            compose.onNodeWithTag("hub-period-menu-last30").performKeyInput {pressKey(Key.Escape)}
            compose.onNodeWithTag("hub-period-menu").assertDoesNotExist()
            assertEquals("last7",store.load().lastPeriod)
            select("tools"); show("Codex"); showCost("Codex"); save("tools", dark)
            select("models"); show("demo-model")
            select("devices"); show("public"); showCost("public"); save("devices", dark)
            compose.onNodeWithText("public").performTouchInput {longClick()}
            compose.onNodeWithText("来源窗口 2026-10-03 – 2026-10-09").assertExists()
            compose.onNodeWithText("关闭").performClick()
            select("sessions"); show("历史没有提供此范围的项目或会话归属。")
            mode("last30"); total("1,100")
            compose.onNodeWithTag("hub-period-today").performClick(); total("500")
            compose.onNodeWithTag("hub-period-month").performClick(); total("1,100")
            mode("week"); total("700")
            compose.onNodeWithTag("hub-period-allTime").performClick(); total("90,000")
            assertEquals("week", store.load().monthMode)
            assertEquals("allTime", store.load().lastPeriod)
        } finally { store.clear() }
    }
    @Test fun missingHistoryNeverShowsPartialTotalsAndManualReadRestoresTheRange() {
        val dark = InstrumentationRegistry.getArguments().getString("visualDark") != "false"
        val state = mutableStateOf(HubState(snapshot = snapshot, deviceHistories = DeviceHistories(FixedPeriods.signature(snapshot), listOf(DeviceHistory("public", false, null)))))
        var reads = 0
        val actions = object : Actions() { override fun loadDeviceHistory() { reads++; state.value = state.value.copy(deviceHistories = history(state.value.snapshot!!)) } }
        compose.activity.runOnUiThread { compose.activity.setContent { TokenTheme(dark) { HubScreen(state.value, actions) } } }
        mode("last7"); total("—")
        show("部分设备未提供完整历史，无法显示此范围。"); save("unavailable", dark)
        compose.onNodeWithTag("fixed-period-read").performClick(); total("800")
        assertEquals(1, reads)
        compose.runOnIdle { state.value = state.value.copy(snapshot = snapshot.copy(deviceHistoryRevision = "new")) }
        total("—"); show("读取每台设备的完整历史后，计算此范围。")
        compose.onNodeWithTag("fixed-period-read").performClick(); total("800")
        assertEquals(2, reads)
    }
}
