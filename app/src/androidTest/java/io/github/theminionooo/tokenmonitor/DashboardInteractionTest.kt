package io.github.theminionooo.tokenmonitor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.HubRepositoryState
import io.github.theminionooo.tokenmonitor.data.network.WireHubSnapshot
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.data.storage.SnapshotCache
import io.github.theminionooo.tokenmonitor.domain.*
import io.github.theminionooo.tokenmonitor.ui.*
import io.github.theminionooo.tokenmonitor.widget.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import android.view.View
import android.widget.FrameLayout
import java.time.LocalDate

class DashboardInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sessionsFromDifferentToolsMayShareAnId() {
        val first = SessionUsage("same-id", "Example Codex session", "codex", "", 42, 0.0, emptyList(), 0, "", "")
        val second = first.copy(client = "muse", title = "Example Muse session")
        compose.setContent {
            MaterialTheme { androidx.compose.foundation.lazy.LazyColumn {
                sessionItems(listOf(first, second), DashboardPeriod.Today)
            } }
        }
        compose.onNodeWithText("Example Codex session").assertIsDisplayed()
        compose.onNodeWithText("Example Muse session").assertIsDisplayed()
        compose.onNodeWithText("Example Codex session").performClick()
        compose.onAllNodesWithContentDescription("Collapse session details").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Expand session details").assertCountEquals(1)
    }

    @Test fun newHomeModuleDefaultsDoNotResetSavedLayouts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".preview"))
        val prefs = context.getSharedPreferences("display_preferences", android.content.Context.MODE_PRIVATE)
        val key = "visible_home_modules"
        val original = prefs.getString(key, null)
        try {
            prefs.edit().remove(key).commit()
            assertTrue("Sessions" in io.github.theminionooo.tokenmonitor.data.storage.DisplayPreferences(context).options.value.visibleHomeModules)
            prefs.edit().putString(key, "Devices,Tools,Limits").commit()
            val store = io.github.theminionooo.tokenmonitor.data.storage.DisplayPreferences(context)
            assertEquals(listOf("Devices", "Tools", "Limits"), store.options.value.visibleHomeModules)
            store.setHomeModuleVisible("Sessions", true)
            assertEquals(listOf("Devices", "Tools", "Limits", "Sessions"), store.options.value.visibleHomeModules)
            assertEquals(store.options.value.visibleHomeModules, io.github.theminionooo.tokenmonitor.data.storage.DisplayPreferences(context).options.value.visibleHomeModules)
        } finally {
            prefs.edit().putString(key, original).commit()
        }
    }

    @Test fun homeSessionsPreserveTitlesContextAndOpenSessions() {
        val now = java.time.Instant.parse("2026-09-30T12:00:00Z").toEpochMilli()
        val session = SessionUsage("sample", "fix a mixedCase bug", "muse", "Example", 42, 0.0, emptyList(), 0,
            "2026-09-30T11:55:00Z", "2026-09-30T11:59:00Z", contextTokens = 25, contextWindow = 100, turnEnded = false)
        val snapshot = HubSnapshot(stats = HubStats(periods = mapOf("month" to UsagePeriod(sessions = listOf(session)))))
        var chosen: DashboardDestination? = null
        compose.setContent {
            MaterialTheme { CompositionLocalProvider(LocalNow provides now) {
                HomeSessionsModule(snapshot) { chosen = it }
            } }
        }
        compose.onNodeWithText("fix a mixedCase bug").assertIsDisplayed()
        compose.onNodeWithText("1 running").assertIsDisplayed()
        compose.onNodeWithText("Context 75% left").assertIsDisplayed()
        compose.onNodeWithText("Muse Code · Running · 1m ago · Example").assertIsDisplayed()
        compose.onNodeWithText("SESSIONS").performClick()
        compose.runOnIdle { assertEquals(DashboardDestination.Sessions, chosen) }
    }

    @Test fun toolsOpenOnlyTheirOwnModelsWithMotion() = checkToolNavigation(true)

    @Test fun toolsOpenOnlyTheirOwnModelsWithoutMotion() = checkToolNavigation(false)

    private fun checkToolNavigation(motion: Boolean) {
        val usage = UsagePeriod(totalTokens = 900, clients = mapOf("codex" to 200, "opencode" to 700), models = mapOf("gpt-5" to 200, "gpt-4" to 700), clientModels = mapOf("codex" to mapOf("gpt-5" to 200), "opencode" to mapOf("gpt-4" to 700)))
        compose.setContent {
            var destination by remember { mutableStateOf(DashboardDestination.Tools) }
            MaterialTheme { CompositionLocalProvider(LocalInteractionMotion provides motion) {
                DashboardScaffold(
                    state = HubRepositoryState(snapshot = HubSnapshot(stats = HubStats(periods = mapOf("today" to usage, "allTime" to usage)))),
                    destination = destination, form = ConnectionFormState(), displayOptions = DisplayOptions(), serviceStatus = ServiceStatusState(),
                    onChoose = { destination = it }, onRefresh = {}, onSaveConnection = { _, _, _, _ -> },
                    onColorfulToolMarksChange = {}, onCompactTokenTotalChange = {}, onReduceMotionChange = {}, onTextScaleChange = {},
                    onThemeCodeChange = {}, onFollowSystemThemeChange = {}, onShowLiveIndicatorChange = {}, onShowToolIconsChange = {}, onRankingMetricChange = {},
                    onShowLimitSourceChange = {}, onShowAccountEmailsChange = {}, onLimitBarMetricChange = {}, onDefaultPeriodChange = {},
                    onViewVisibleChange = { _, _ -> }, onHomeModuleVisibleChange = { _, _ -> }, onMoveView = { _, _ -> }, onMoveHomeModule = { _, _ -> },
                    onDisconnect = {}, onOpenServicePage = {}, onOpenReleasePage = {}, discovery = HubDiscoveryState(), onFindHomeHub = {},
                )
            } }
        }
        compose.onNodeWithText("Codex").performClick()
        compose.onNodeWithText("Codex · Models").assertIsDisplayed()
        compose.onNodeWithText("gpt-5").assertIsDisplayed()
        compose.onAllNodesWithText("200").assertCountEquals(2)
        compose.onNodeWithText("gpt-4").assertDoesNotExist()
        compose.onNodeWithText("900").assertDoesNotExist()
        compose.onNodeWithText("CLOSE").assertDoesNotExist()
        compose.onNodeWithText("TOTAL").performClick()
        compose.onNodeWithText("Codex · Models").assertIsDisplayed()
        compose.onAllNodesWithText("200").assertCountEquals(2)
        compose.onNodeWithText("← Back to Tools").performClick()
        compose.onNodeWithText("OpenCode").performClick()
        compose.onNodeWithText("OpenCode · Models").assertIsDisplayed()
        compose.onNodeWithText("gpt-4").assertIsDisplayed()
        compose.onNodeWithText("gpt-5").assertDoesNotExist()
        compose.onAllNodesWithText("700").assertCountEquals(2)
        compose.onNodeWithText("ALL MODELS").performClick()
        compose.onNodeWithText("900").assertIsDisplayed()
        compose.onNodeWithText("gpt-5").assertIsDisplayed()
        compose.onNodeWithText("gpt-4").assertIsDisplayed()
        compose.onNodeWithText("OpenCode · Models").assertDoesNotExist()
        compose.onNodeWithContentDescription("Choose view").performClick()
        compose.onNodeWithText("Tools").performClick()
        compose.onNodeWithText("Codex").performClick()
        compose.onNodeWithText("Codex · Models").assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose view").performClick()
        compose.onAllNodesWithText("Models").onLast().performClick()
        compose.onNodeWithText("Codex · Models").assertDoesNotExist()
        compose.onNodeWithText("900").assertIsDisplayed()
        compose.onNodeWithText("gpt-4").assertIsDisplayed()
    }

    @Test fun heatmapOpensAccessibleDayDetailsAtLargeTextSize() {
        val history = listOf(HistoryPoint(LocalDate.now().toString(), 42, 0.12, perModel = mapOf("sample-model" to HistoryAttribution(42, 0.12))))
        compose.setContent {
            MaterialTheme { CompositionLocalProvider(LocalInteractionMotion provides false, LocalDensity provides Density(LocalDensity.current.density, 1.3f), LocalPalette provides Palette.from(InterfaceTheme.Porcelain)) {
                ActivityHeatmapGrid(buildActivityHeatmap(history), history)
            } }
        }
        compose.onNodeWithContentDescription("Activity heatmap. Open daily details or use the day navigation below.").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("sample-model").assertIsDisplayed().performClick()
        compose.onNodeWithText("42 tokens across 1 recorded day").assertIsDisplayed()
    }

    @Test fun heatmapNextStopsAtTodayAndNeverWraps() {
        val today = LocalDate.now()
        val history = listOf(HistoryPoint(today.toString(), 42, 0.12))
        compose.setContent { MaterialTheme { ActivityHeatmapGrid(buildActivityHeatmap(history), history) } }
        compose.onNodeWithText("NEXT DAY").assertIsNotEnabled()
        compose.onNodeWithText("PREVIOUS").performClick()
        compose.onNodeWithText("NEXT DAY").assertIsEnabled().performClick()
        compose.onNodeWithText("NEXT DAY").assertIsNotEnabled()
        compose.onNodeWithText("42 tokens").assertIsDisplayed()
    }

    @Test fun streamCacheSurvivesDiskRoundTripAndWidgetUsesSavedData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".preview")) { "Run device tests with -PtokenMonitorPreview=true" }
        fun asset(name: String) = InstrumentationRegistry.getInstrumentation().context.assets.open("protocol/v0.54.0/$name").bufferedReader().use { it.readText() }
        val event = asset("stats-stream.sse").lineSequence().first { it.startsWith("data:") }.removePrefix("data:").trim()
        val cache = SnapshotCache(context)
        cache.save(WireHubSnapshot(asset("health.json"), event, asset("devices.json"), asset("history.json"), asset("subscriptions.json"), 1788513600000L))
        val restored = checkNotNull(cache.read())
        val snapshot = HubProtocolParser.decodeSnapshot(restored.health, restored.stats, restored.devices, restored.history, restored.subscriptions, restored.capturedAt, true)
        assertEquals(125430L, snapshot.today.totalTokens)
        compose.runOnUiThread {
            for (layout in WidgetLayout.entries) {
                val view = UsageWidgetProvider.render(context, snapshot, InterfaceTheme.Porcelain, layout).apply(context, FrameLayout(context))
                assertEquals(if (layout in listOf(WidgetLayout.Compact, WidgetLayout.Wide)) View.GONE else View.VISIBLE, view.findViewById<View>(R.id.widget_limits)?.visibility ?: View.GONE)
                assertEquals(if (layout == WidgetLayout.Large) View.VISIBLE else View.GONE, view.findViewById<View>(R.id.widget_chart)?.visibility ?: View.GONE)
            }
        }
        cache.clear()
    }
}
