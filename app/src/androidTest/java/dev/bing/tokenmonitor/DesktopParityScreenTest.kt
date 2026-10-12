package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Representative public synthetic data; screenshots never contain a real Hub response. */
class DesktopParityScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private class Actions : HubUiActions {
        override fun connect(address: String, credential: String, alternate: String) {}
        override fun refreshSaved() {}
        override fun switchEndpoint() {}
        override fun hasAlternate() = true
        override fun endpointType() = "https"
        override fun loadHistory() {}
        override fun loadModelNames() {}
        override fun loadSubscriptions() {}
        override fun forget() {}
        override fun renameDevice(id: String, input: String) {}
    }
    @Test fun desktopViewsExposeCostsDetailsDevicesAndCharts() {
        val body = """{"periods":{"today":{"totalTokens":165164772,"costUsd":136.08,"capabilities":{"tokenComponents":true},
            "cacheReadTokens":140000000,"cacheWriteTokens":1000000,"outputTokens":4000000,"unclassifiedTokens":0,
            "clients":{"codex":110000000,"claude":55164772},"clientCosts":{"codex":88.12,"claude":47.96},
            "clientCacheReads":{"codex":100000000,"claude":40000000},"clientCacheWrites":{"codex":0,"claude":1000000},
            "clientOutputs":{"codex":2000000,"claude":2000000},"clientUnclassifiedTokens":{"codex":0,"claude":0},
            "models":{"gpt-5.5":110000000,"claude-sonnet-4.6":55164772},"modelCosts":{"gpt-5.5":88.12,"claude-sonnet-4.6":47.96},
            "clientModels":{"codex":{"gpt-5.5":110000000},"claude":{"claude-sonnet-4.6":55164772}},"clientModelCosts":{"codex":{"gpt-5.5":88.12},"claude":{"claude-sonnet-4.6":47.96}}}},"devices":[]}"""
        val base = HubParser.parse(body); val period = base.periods.getValue("today")
        val state = HubState(snapshot = base.copy(updated = "2026-10-09T08:00:00Z", periods = mapOf("today" to period, "month" to period, "allTime" to period),
            devices = listOf(Device("studio", "Studio Mac", false, "2026-10-09T08:00:00Z", "macOS", null, mapOf("today" to DeviceUsage(145164772, "116.08", mapOf("codex" to 110000000), null, null, null))),
                Device("server", "Home Server", false, "2026-10-09T08:00:00Z", "Linux", null, mapOf("today" to DeviceUsage(20000000, "20.00", mapOf("claude" to 20000000), null, null, null)))),
            limits = listOf(Limit("claude", "ok", 99.0, "2026-10-10T10:00:00Z", "Demo account", "session", "percent", null, null, false, null, null),
                Limit("claude", "ok", 35.0, "2026-10-12T10:00:00Z", "Demo account", "weekly", "percent", null, null, false, null, null),
                Limit("codex", "ok", 88.0, "2026-10-15T10:00:00Z", "Demo account", "weekly", "percent", null, null, false, null, null))),
            history = HubHistory(List(84) { n -> HistoryRow(LocalDate.of(2026, 10, 9).minusDays((83-n).toLong()).toString(), (n % 11 + 1) * 1430000L, "1.0", perClient = mapOf("codex" to HistoryAttribution((n % 11 + 1) * 1000000L, "0.7"), "claude" to HistoryAttribution((n % 11 + 1) * 430000L, "0.3")), perModel = mapOf("gpt-5.5" to HistoryAttribution((n % 11 + 1) * 1000000L, "0.7"), "claude-sonnet-4.6" to HistoryAttribution((n % 11 + 1) * 430000L, "0.3"))) }, emptyList(), HistorySummary(165164772, "136.08", 106, 14, 40, 198000000, 22000000, "gpt-5.5", 1600))).let {it.copy(snapshot=it.snapshot?.copy(historyPreview=it.history))}
        val dark = InstrumentationRegistry.getArguments().getString("visualDark") != "false"
        compose.activity.runOnUiThread { compose.activity.setContent {CompositionLocalProvider(LocalHubCalendarClock provides java.time.Clock.fixed(java.time.Instant.parse("2026-10-09T08:00:00Z"),java.time.ZoneId.of("Asia/Shanghai"))) { TokenTheme(dark) { HubScreen(state, Actions(),preferences=DisplayPreferences(homeModules=defaultHomeModules)) } }} }
        fun save(name: String) {
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            saveVisualEvidence(compose.activity, compose.onRoot(), name)
        }
        compose.onNodeWithText("165,164,772").assertIsDisplayed()
        compose.onNodeWithText("$136.08").assertIsDisplayed()
        compose.onNodeWithTag("hub-total-tokens").performClick()
        compose.onNodeWithText("140,000,000").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        save("desktop-home-${if(dark) "dark" else "light"}.png")
        compose.onNodeWithTag("hub-content").performScrollToNode(hasContentDescription("最高 165,164,772 Token"))
        save("desktop-home-charts-${if(dark) "dark" else "light"}.png")
        fun select(page: String) { compose.onNodeWithTag("hub-view-selector").performClick(); compose.onNodeWithTag("view-$page").performScrollTo().performClick() }
        select("tools")
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("$88.12"))
        compose.onNodeWithText("$88.12").assertIsDisplayed()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("Claude Code"))
        compose.onNodeWithText("Claude Code").performClick()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("40,000,000"))
        compose.onNodeWithText("40,000,000").assertIsDisplayed()
        save("desktop-tools-${if(dark) "dark" else "light"}.png")
        select("trends")
        compose.onNodeWithTag("hub-pinned-total").assertIsDisplayed()
        compose.onNodeWithTag("trends-main-chart").assertIsDisplayed()
        compose.onNodeWithTag("trends-main-active-days",useUnmergedTree=true).assertTextEquals("106")
        save("desktop-trends-${if(dark) "dark" else "light"}.png")
        compose.onNodeWithTag("trends-main-open-history").performClick()
        compose.onNodeWithTag("dashboard-open-records").performScrollTo().performClick()
        compose.onNodeWithText("柱状图").performScrollTo()
        compose.onNodeWithText("柱状图").assertIsDisplayed()
        save("desktop-history-${if(dark) "dark" else "light"}.png")
        compose.onNodeWithTag("desktop-history-chart").performScrollTo()
        // A chart is nested in a tall lazy item. Reveal its full rectangle before
        // tapping, as a person would scroll on a short landscape viewport.
        val chart = compose.onNodeWithTag("desktop-history-chart")
        val viewport = compose.onNodeWithTag("dashboard-scroll").getUnclippedBoundsInRoot()
        val bounds = chart.getUnclippedBoundsInRoot()
        val shift = when { bounds.bottom > viewport.bottom -> bounds.bottom - viewport.bottom; bounds.top < viewport.top -> bounds.top - viewport.top; else -> androidx.compose.ui.unit.Dp(0f) }
        if (shift.value != 0f) compose.onNodeWithTag("dashboard-scroll").performSemanticsAction(SemanticsActions.ScrollBy) { scroll -> scroll(0f, shift.value * compose.density.density) }
        save("desktop-chart-detail-${if(dark) "dark" else "light"}.png")
        compose.onNodeWithTag("desktop-history-chart").performTouchInput { click(center) }
        compose.onAllNodesWithText("$0.7000").onFirst().performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("按模型").performScrollTo()
        compose.onNodeWithText("按模型").performClick()
        compose.onAllNodesWithText("claude-sonnet-4.6").onFirst().performScrollTo()
        compose.onNodeWithText("claude-sonnet-4.6").assertIsDisplayed()
        compose.onNodeWithText("K 线").performScrollTo()
        compose.onNodeWithText("K 线").performClick()
        save("desktop-kline-${if(dark) "dark" else "light"}.png")
        compose.onNodeWithTag("trends-history-back").performClick()
        select("devices")
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("145,164,772"))
        compose.onNodeWithText("145,164,772").assertIsDisplayed()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("$116.08"))
        compose.onNodeWithText("$116.08").assertIsDisplayed()
        save("desktop-devices-${if(dark) "dark" else "light"}.png")
        select("limits")
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("99% left"))
        compose.onNodeWithText("99% left").assertIsDisplayed()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("35% left"))
        compose.onNodeWithText("35% left").assertIsDisplayed()
        save("desktop-limits-${if(dark) "dark" else "light"}.png")
    }

    @Test fun sharedNamesProjectTitlesPricesAndSubscriptionsUseDesktopViews() {
        val snapshot = HubParser.parse("""{"updatedAt":"2026-10-09T08:00:00Z","periods":{"today":{"totalTokens":1000,"costUsd":1.25,
            "clients":{"codex":1000},"clientCosts":{"codex":1.25},"models":{"vendor/gpt-5.5":800,"gpt-5.5":200},"modelCosts":{"vendor/gpt-5.5":1,"gpt-5.5":0.25},
            "projects":{"demo":{"label":"Token Monitor Android","clients":{"codex":1000},"tokens":1000,"costUsd":1.25}},
            "sessions":{"demo":{"sessionId":"demo-session","title":"Align Android with desktop","client":"codex","totalTokens":1000,"costUsd":1.25,"lastUsedAt":"2026-10-09T08:00:00Z","turnEnded":true}}}}}""")
        val state = HubState(snapshot = snapshot,
            modelNames = ModelNamesParser.parse("""{"ok":true,"value":{"modelAliases":{"vendor/gpt-5.5":"GPT 5.5 Standard","gpt-5.5":"GPT 5.5 Standard"},"modelAliasGrouping":"off"}}"""),
            pricing = PricingParser.parse("""{"ok":true,"version":1,"revision":3,"updatedAt":"2026-10-09T08:00:00Z","value":[{"modelId":"gpt-5.5","inputPerM":2.5,"outputPerM":15,"cacheReadPerM":0,"cacheWrite1hPerM":3.75}]}"""),
            subscriptions = SubscriptionParser.parse("""{"subscriptions":[{"provider":"claude","planName":"Claude Pro","amountMinor":2000,"currency":"USD","interval":"month","intervalCount":1,"startDate":"2026-08-01","autoRenew":true,"binding":{"profileName":"Demo account"}},{"provider":"openrouter","planName":"API balance","kind":"topup","currency":"USD","topUps":[{"date":"2026-10-01","amountMinor":1000}]}]}"""))
        val dark = InstrumentationRegistry.getArguments().getString("visualDark") != "false"
        val suffix = if (dark) "dark" else "light"
        val preferences = androidx.compose.runtime.mutableStateOf(DisplayPreferences(theme = if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT))
        compose.activity.runOnUiThread { compose.activity.setContent {CompositionLocalProvider(LocalHubCalendarClock provides java.time.Clock.fixed(java.time.Instant.parse("2026-10-09T08:00:00Z"),java.time.ZoneId.of("Asia/Shanghai"))) { TokenTheme(dark) { HubScreen(state, Actions(), preferences = preferences.value, updateDisplay = { preferences.value = it.apply(preferences.value) }) } }} }
        fun select(page: String) { compose.onNodeWithTag("hub-view-selector").performClick(); compose.onNodeWithTag("view-$page").performScrollTo().performClick() }
        fun save(page: String) { compose.mainClock.advanceTimeBy(500); compose.waitForIdle(); saveVisualEvidence(compose.activity, compose.onRoot(), "desktop-$page-$suffix.png") }
        select("models")
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("GPT 5.5 Standard"))
        compose.onNodeWithText("GPT 5.5 Standard").assertIsDisplayed()
        compose.onNodeWithText("vendor/gpt-5.5").assertDoesNotExist()
        save("models")
        select("projects")
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("Token Monitor Android"))
        compose.onNodeWithText("Token Monitor Android").assertIsDisplayed()
        save("projects")
        select("sessions")
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("Align Android with desktop"))
        compose.onNodeWithText("Align Android with desktop").assertIsDisplayed()
        save("sessions")
        openSharedSettings(compose,"pricing")
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("shared-price-gpt-5.5"))
        compose.onNodeWithTag("shared-price-gpt-5.5").performClick()
        compose.onNodeWithText("输入(命中缓存)：0（明确免费）").assertIsDisplayed()
        compose.onNodeWithText("缓存写入：未设置").assertExists()
        compose.onNodeWithText("关闭").performClick()
        save("pricing")
        openSharedSettings(compose,"subscriptions")
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("Claude Pro",substring=true))
        compose.onNodeWithText("Claude Pro",substring=true).assertIsDisplayed()
        save("subscriptions")
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("API balance",substring=true))
        compose.onNodeWithText("API balance",substring=true).performClick()
        compose.onNodeWithText("充值 2026-10-01 · 10.00 USD").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("hub-settings-toggle").performClick()
        openSettingsSection(compose,"main")
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("main-view-home-settings"));compose.onNodeWithTag("main-view-home-settings").performClick()
        compose.onNodeWithTag("main-view-home-details").assertExists()
        save("settings")
    }
}
