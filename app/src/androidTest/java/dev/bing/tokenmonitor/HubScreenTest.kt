package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HubScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private class Actions : HubUiActions {
        var renamed: Pair<String, String>? = null
        override fun connect(address: String, credential: String, alternate: String) {}
        override fun refreshSaved() {}
        override fun switchEndpoint() {}
        override fun hasAlternate() = false
        override fun endpointType() = "https"
        override fun loadModelNames() {}
        override fun loadHistory() {}
        override fun loadSubscriptions() {}
        override fun forget() {}
        override fun renameDevice(id: String, input: String) { renamed = id to input }
    }
    @Test fun syntheticOverviewSourceListsDetailsAndLocalRename() {
        val actions = Actions()
        val dark = InstrumentationRegistry.getArguments().getString("visualDark") == "true"
        val quota = Limit("Demo Quota", "ok", null, "2026-10-31T00:00:00Z", "Demo Account", "billing", "spend", null, "CNY", false, null, "Usage credits",
            usedAmount = "1.25", boundaryKind = "expiry")
        val period = Period(12345, "0.125", mapOf("Claude Code" to 12000), mapOf("model-demo" to 12345),
            listOf(UsageRow("one", "demo-project", 12000, "0.12", "Claude Code", null, null,project=ProjectMetadata("demo-project",12000,mapOf("Claude Code" to 12000))),
                UsageRow("two", "other-project", 345, "0.005", "Codex", null, false,project=ProjectMetadata("other-project",345,mapOf("Codex" to 345)))), emptyList(), 45,
            TokenComponents(10000, 100, 1000, 45, 1200))
        val state = HubState(snapshot = Snapshot("2026-10-09T08:00:00Z", mapOf("today" to period),
            listOf(Device("device-demo", "Demo Computer", false, "2026-10-09T07:59:00Z", "macOS", null,
                periods = mapOf("today" to DeviceUsage(12345, "0.125", mapOf("Codex" to 12345), "2026-10-09", null, "Asia/Shanghai")),
                health = DeviceHealth("2026-10-09T07:58:00Z", listOf(ClientHealth("Codex", "waiting", "detected", "unknown", 0, null, emptyList()))))), listOf(quota), true))
        compose.activity.runOnUiThread { compose.activity.setContent { TokenTheme(dark = dark) { HubScreen(state, actions,preferences=DisplayPreferences(homeModules=defaultHomeModules)) } } }
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("12,345").assertIsDisplayed()
        compose.onNodeWithText("$0.1250").assertIsDisplayed()
        saveVisualEvidence(compose.activity, compose.onRoot(), if (dark) "visual-overview-dark.png" else "visual-overview.png")
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("home-device-list"))
        compose.onNodeWithTag("home-device-list").performClick()
        compose.onNodeWithText("device-demo").performTouchInput {longClick()}
        compose.onNodeWithText("来源尚未观察到用量").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("采集：未知").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("当前扫描累计 0 Token").performScrollTo().assertIsDisplayed()
        saveVisualEvidence(compose.activity, compose.onNode(isDialog()), if (dark) "visual-device-dark.png" else "visual-device.png")
        compose.onNodeWithText("修改本地名称").performClick()
        compose.onNodeWithText("名称（最多32个字符）").performTextInput("我的电脑")
        compose.onNodeWithText("保存").performClick()
        assertEquals("device-demo" to "我的电脑", actions.renamed)
        compose.onNodeWithTag("hub-view-selector").performClick()
        compose.onNodeWithTag("view-projects").performScrollTo().performClick()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("demo-project"))
        compose.onNodeWithText("demo-project").performTouchInput {longClick()}
        compose.onNodeWithText("已知估算费用：$0.1200").assertIsDisplayed()
        compose.onNodeWithText("本轮状态未提供").assertDoesNotExist()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("hub-view-selector").performClick()
        compose.onNodeWithTag("view-limits").performScrollTo().performClick()
        compose.onNodeWithText("Demo Quota").performClick()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("已用金额 1.25 CNY"))
        compose.onNodeWithText("已用金额 1.25 CNY").assertIsDisplayed()
        compose.onNodeWithText("未记录支出上限").assertIsDisplayed()
        compose.onNodeWithText("到期时间 2026-10-31T00:00:00Z").assertIsDisplayed()
        compose.onNodeWithText("剩余 0.0%").assertDoesNotExist()
    }
}
