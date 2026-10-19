package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Actual HubScreen → Dashboard → Records route; all payloads/actions are public fixtures. */
class RecordHistoryReadScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val history = HubHistory(listOf(HistoryRow("2026-10-16", 81, "1"), HistoryRow("2026-10-14", 5, "0")),
        listOf(HistoryRow("2026-10", 999, "2")))
    private val snapshot = HubParser.parse("""{"periods":{"today":{"totalTokens":81,"costUsd":1},"month":{"totalTokens":999,"costUsd":2},"allTime":{"totalTokens":999,"costUsd":2}}}""")
        .copy(historyPreview = history, historyRevision = "public-records-r1", modelNamesRevision = "public-aliases-r1")
    private val key get() = HistoryReadKey(0, homeHistorySignature(snapshot))
    private val absent = "No record supplied by Hub; this is not zero usage."
    private val failed = "History could not be loaded. Existing records are retained; refresh to retry."
    private val blocked = "The credential is invalid. Connect again."
    private class Actions : HubUiActions {
        var histories = 0
        var aliases = 0
        var onAliases: () -> Unit = {}
        override fun connect(address: String, credential: String, alternate: String) {}
        override fun refreshSaved() {}
        override fun switchEndpoint() {}
        override fun hasAlternate() = false
        override fun endpointType() = "https"
        override fun loadHistory() { histories++ }
        override fun loadHistory(manual: Boolean): Boolean { histories++; return false }
        override fun loadModelNames() { aliases++; onAliases() }
        override fun loadSubscriptions() {}
        override fun forget() {}
        override fun renameDevice(id: String, input: String) {}
    }
    private object FrozenRetryClock : HomeHistoryReadClock {
        override fun nowMs() = 0L
        override suspend fun waitMs(durationMs: Long) { awaitCancellation() }
    }
    private fun host(state: MutableState<HubState>, actions: Actions) {
        val dark = InstrumentationRegistry.getArguments().getString("visualDark") != "false"
        val preferences = DisplayPreferences(language = LanguageChoice.ENGLISH, homeModules = emptyList(),
            appearance = AppearancePreferences(reduceMotion = "on"))
        val animationClock = DesktopAnimationClock { compose.mainClock.currentTime * 1_000_000L }
        compose.activity.runOnUiThread { compose.activity.setContent {
            CompositionLocalProvider(LocalHubCalendarClock provides Clock.fixed(Instant.parse("2026-10-16T08:00:00Z"), ZoneId.of("Asia/Shanghai")),
                LocalHomeHistoryReadClock provides FrozenRetryClock,
                LocalDesktopAnimationClock provides animationClock) {
                TokenTheme(dark, LanguageChoice.ENGLISH, preferences.appearance) { HubScreen(state.value, actions, preferences = preferences) }
            }
        } }
    }
    private fun openRecords() {
        compose.onNodeWithTag("hub-view-selector").performClick()
        compose.onNodeWithTag("view-trends").performScrollTo().performClick()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("trends-main-open-history"))
        compose.onNodeWithTag("trends-main-open-history").performClick()
        compose.onNodeWithTag("dashboard-open-records").performScrollTo().performClick()
        compose.onNodeWithTag("history-record-2026-10-15").performScrollTo().performClick()
    }
    private fun assertSelectedMessage(message: String) {
        compose.onNodeWithTag("records-selected-detail").performScrollTo().onChildren().filterToOne(hasText(message)).assertIsDisplayed()
    }
    private fun assertNoReadStatus() {
        compose.onNodeWithTag("dashboard-history-loading").assertDoesNotExist()
        compose.onNodeWithTag("dashboard-history-error").assertDoesNotExist()
        compose.onNodeWithTag("records-loading").assertDoesNotExist()
        compose.onNodeWithTag("records-error").assertDoesNotExist()
    }

    @Test fun unrelatedAliasPricingSubscriptionAndDeviceReadsDoNotPolluteLoadedRecords() {
        val accepted = HistoryReadReceipt(key, 1, 1, 1, HistoryReadPhase.RESOLVED, fetchedDays = history.daily.size)
        val state = mutableStateOf(HubState(snapshot = snapshot, history = history, live = true, historyReadReceipt = accepted))
        val actions = Actions().apply { onAliases = { state.value = state.value.copy(loading = true, error = null) } }
        host(state, actions)
        openRecords()
        // Opening the real Dashboard automatically requested aliases, sharing the global busy flag.
        assertEquals(1, actions.aliases)
        assertTrue(state.value.loading)
        assertNoReadStatus()
        assertSelectedMessage(absent)
        compose.runOnIdle { state.value = state.value.copy(loading = false, error = R.string.error_read, modelNames = ModelNames(emptyMap(), "off")) }
        assertNoReadStatus()
        assertSelectedMessage(absent)
        for (sharedError in listOf(R.string.error_read, R.string.error_details_save, R.string.error_revision_changed)) {
            // readPart uses the same flags for pricing, subscriptions and device details.
            compose.runOnIdle { state.value = state.value.copy(loading = true, error = null) }
            assertNoReadStatus()
            assertSelectedMessage(absent)
            compose.runOnIdle { state.value = state.value.copy(loading = false, error = sharedError) }
            assertNoReadStatus()
            assertSelectedMessage(absent)
        }
        compose.onNodeWithText("7 days").assertDoesNotExist()
        compose.onNodeWithTag("records-calendar-title").performScrollTo().assertExists()
        assertNoReadStatus()
        assertSelectedMessage(absent)
        compose.onNodeWithTag("history-record-2026-10-16").performScrollTo().performClick()
        compose.onNodeWithTag("records-selected-tokens").performScrollTo().assertTextEquals("Token 81")
        saveVisualEvidence(compose.activity, compose.onRoot(), "records-shared-read-isolation.png")
        assertEquals(0, actions.histories)
    }

    @Test fun historyPendingFailedAuthCacheAndOlderTargetKeepTheirOwnStateAcrossRecordFilters() {
        val coordinator = HistoryReadCoordinator()
        val cached = coordinator.target(key, trustedCachedDays = history.daily.size)
        val state = mutableStateOf(HubState(snapshot = snapshot, history = history, live = true,
            modelNames = ModelNames(emptyMap(), "off"), historyReadReceipt = cached))
        val actions = Actions()
        host(state, actions)
        openRecords()
        assertNoReadStatus()
        assertSelectedMessage(absent)
        val start = coordinator.begin(key, 11, manual = true, previewHadDays = true)!!
        compose.runOnIdle { state.value = state.value.copy(loading = true, historyReadReceipt = start) }
        compose.onNodeWithTag("dashboard-history-loading").assertExists()
        compose.onNodeWithTag("records-loading").assertExists()
        assertSelectedMessage("Loading history…")
        val failure = coordinator.finish(start, HistoryReadPhase.FAILED)!!
        // The global error may already describe a different shared request; history owns its message.
        compose.runOnIdle { state.value = state.value.copy(loading = false, error = R.string.error_details_save, historyReadReceipt = failure) }
        compose.onNodeWithTag("records-error").performScrollTo().assertTextEquals(failed)
        compose.onNodeWithTag("dashboard-history-error").assertTextEquals(failed)
        assertSelectedMessage(failed)
        compose.onNodeWithTag("history-record-2026-10-16").performScrollTo().performClick()
        compose.onNodeWithTag("records-selected-tokens").performScrollTo().assertTextEquals("Token 81")
        val blockedStart = coordinator.begin(key, 12, manual = true, previewHadDays = true)!!
        val auth = coordinator.finish(blockedStart, HistoryReadPhase.AUTH_BLOCKED)!!
        compose.runOnIdle { state.value = state.value.copy(error = R.string.error_read, historyReadReceipt = auth) }
        compose.onNodeWithTag("history-record-2026-10-15").performScrollTo().performClick()
        compose.onNodeWithTag("records-error").performScrollTo().assertTextEquals(blocked)
        compose.onNodeWithTag("dashboard-history-error").assertTextEquals(blocked)
        assertSelectedMessage(blocked)
        saveVisualEvidence(compose.activity, compose.onRoot(), "records-history-auth.png")
        val nextSnapshot = snapshot.copy(historyRevision = "public-records-r2")
        val nextKey = HistoryReadKey(0, homeHistorySignature(nextSnapshot))
        val nextCache = coordinator.target(nextKey, trustedCachedDays = history.daily.size)
        val nextHistory = history.copy(daily = listOf(HistoryRow("2026-10-16", 22, "0"), HistoryRow("2026-10-14", 1, "0")))
        compose.runOnIdle { state.value = state.value.copy(snapshot = nextSnapshot, history = nextHistory,
            loading = false, error = null, historyReadReceipt = nextCache) }
        compose.onNodeWithTag("records-calendar-title").performScrollTo().assertExists()
        assertNoReadStatus()
        assertSelectedMessage(absent)
        // The existing coordinator rejects the stale response; old-key presentation is ignored too.
        assertNull(coordinator.finish(start, HistoryReadPhase.RESOLVED, fetchedDays = 999))
        assertSame(nextCache, coordinator.receipt)
        compose.runOnIdle { state.value = state.value.copy(historyReadReceipt = start, loading = true, error = R.string.error_read) }
        assertNoReadStatus()
        assertSelectedMessage(absent)
        compose.onNodeWithTag("history-record-2026-10-16").performScrollTo().performClick()
        compose.onNodeWithTag("records-selected-tokens").performScrollTo().assertTextEquals("Token 22")
        assertEquals(nextHistory, state.value.history)
        assertEquals(0, actions.histories)
    }
}
