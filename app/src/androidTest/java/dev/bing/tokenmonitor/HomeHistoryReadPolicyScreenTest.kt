package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Uses the actual HubScreen serial read queue and explicit public request receipts; no Hub connection. */
class HomeHistoryReadPolicyScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private class Actions(private val state: MutableState<HubState>) : HubUiActions {
        var historyReads = 0
        var manualReads = 0
        var otherReads = 0
        var rejectAutomatic = false
        var lastForceHistory = false
        private var requestSequence = 100L
        private var cycleSequence = state.value.historyReadReceipt?.cycleId ?: 1L
        private fun key() = HistoryReadKey(state.value.connectionRevision, homeHistorySignature(state.value.snapshot))
        override fun connect(address: String, credential: String, alternate: String) {}
        override fun refreshSaved() = refreshSaved(false)
        override fun refreshSaved(forceHistory: Boolean) {
            if (state.value.loading) return
            otherReads++
            lastForceHistory = forceHistory
            state.value = state.value.copy(loading = true, error = null)
        }
        fun finishStats(success: Boolean) {
            val old = state.value.historyReadReceipt
            val receipt = if (success && lastForceHistory) HistoryReadReceipt(key(), ++cycleSequence, null, 0,
                HistoryReadPhase.QUEUED, bindingRequested = old?.bindingRequested == true) else old
            state.value = state.value.copy(loading = false, historyReadReceipt = receipt,
                error = if (success) null else R.string.error_unavailable)
        }
        override fun switchEndpoint() {}
        override fun hasAlternate() = false
        override fun endpointType() = "https"
        override fun loadHistory() { loadHistory(false) }
        override fun loadHistory(manual: Boolean): Boolean {
            val previous = state.value.historyReadReceipt ?: return false
            if (state.value.loading || !state.value.live || previous.phase == HistoryReadPhase.AUTH_BLOCKED ||
                (!manual && (rejectAutomatic || !previous.allowsAutomaticRead))) return false
            historyReads++
            if (manual) manualReads++
            val receipt = HistoryReadReceipt(key(), if (manual) ++cycleSequence else previous.cycleId,
                ++requestSequence, if (manual) 1 else previous.attempt + 1, HistoryReadPhase.STARTED,
                state.value.snapshot?.historyPreview?.daily?.isNotEmpty() == true, bindingRequested = true)
            state.value = state.value.copy(loading = true, error = null, historyReadReceipt = receipt)
            return true
        }
        fun finish(history: HubHistory?, failed: Boolean) {
            val start = state.value.historyReadReceipt!!
            check(start.phase == HistoryReadPhase.STARTED)
            val terminal = start.copy(phase = if (failed) HistoryReadPhase.FAILED else HistoryReadPhase.RESOLVED,
                fetchedDays = if (failed) null else history!!.daily.size)
            state.value = state.value.copy(loading = false,
                history = if (terminal.accepted) history else state.value.history,
                error = if (failed) R.string.error_unavailable else null, historyReadReceipt = terminal)
        }
        override fun loadModelNames() { otherReads++ }
        override fun loadSubscriptions() { otherReads++ }
        override fun forget() {}
        override fun renameDevice(id: String, input: String) {}
    }
    private val complete = HistoryParser.parse("""{"daily":[{"date":"2026-10-08","tokens":100,"cost":0.1},{"date":"2026-10-09","tokens":50,"cost":0.05}],"monthly":[{"month":"2026-10","tokens":150,"cost":0.15}],"summary":{"totalTokens":150,"totalCost":0.15,"activeDays":2}}""")
    private val empty = HubHistory(emptyList(), emptyList())
    private fun snapshot(revision: String = "public-r1") = HubParser.parse("""{"historyRevision":"$revision","updatedAt":"2026-10-09T08:00:00Z","periods":{"today":{"totalTokens":1000,"costUsd":1}},"historyPreview":{"daily":[{"date":"2026-10-08","tokens":100,"cost":0.1},{"date":"2026-10-09","tokens":50,"cost":0.05}],"monthly":[{"month":"2026-10","tokens":150,"cost":0.15}],"summary":{"activeDays":2}}}""")
    private fun initial(cached: Boolean = false): HubState {
        val snapshot = snapshot()
        return HubState(snapshot = snapshot, history = complete, live = true, modelNames = ModelNames(emptyMap(), "off"),
            historyReadReceipt = HistoryReadReceipt(HistoryReadKey(0, homeHistorySignature(snapshot)), 1, null, 0,
                if (cached) HistoryReadPhase.CACHED else HistoryReadPhase.QUEUED,
                fetchedDays = if (cached) complete.daily.size else null))
    }
    private fun step(ms: Long) { compose.mainClock.advanceTimeBy(ms); compose.runOnIdle {} }
    private fun show(state: MutableState<HubState>, actions: Actions) {
        val prefs = DisplayPreferences(language = LanguageChoice.ENGLISH, homeModules = listOf("activity"), appearance = AppearancePreferences(reduceMotion = "on"))
        val dark = InstrumentationRegistry.getArguments().getString("visualDark") != "false"
        val policyClock = object : HomeHistoryReadClock {
            override fun nowMs() = compose.mainClock.currentTime
            override suspend fun waitMs(durationMs: Long) {
                val until = nowMs() + durationMs
                while (nowMs() < until) withFrameNanos { }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.activity.runOnUiThread { compose.activity.setContent {
            TokenTheme(dark, prefs.language, prefs.appearance) {
                CompositionLocalProvider(LocalHomeHistoryReadClock provides policyClock,
                    LocalHubCalendarClock provides Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"), ZoneId.of("Asia/Shanghai"))) {
                    HubScreen(state.value, actions, preferences = prefs)
                }
            }
        } }
        step(64)
    }
    private fun finish(actions: Actions, history: HubHistory? = null, failed: Boolean = true) {
        compose.runOnIdle { actions.finish(history, failed) }
        step(64)
    }
    private fun select(page: String) {
        compose.onNodeWithTag("hub-view-selector").performClick(); step(64)
        // These menu rows are already composed. Scroll/focus synchronization would
        // await a frame while the policy's deliberately manual clock is paused.
        compose.onNodeWithTag("view-$page").performSemanticsAction(SemanticsActions.OnClick) { it() }; step(64)
    }
    private fun openDashboard() {
        select("trends")
        compose.onNodeWithTag("trends-main-open-history").performSemanticsAction(SemanticsActions.OnClick) { it() }; step(64)
        compose.onNodeWithTag("trends-history-detail").assertExists()
    }
    private fun assertOldHistory(state: MutableState<HubState>) {
        assertEquals(complete, state.value.history)
        compose.onNodeWithTag("dashboard-card-tokens-value", useUnmergedTree = true).assertTextEquals("150")
    }

    @Test fun manualFailureKeepsOldContentAndRetriesFourTimesDespiteFreshnessAndOtherApiLoading() {
        val state = mutableStateOf(initial(cached = true))
        val actions = Actions(state)
        try {
            show(state, actions)
            assertEquals("A trusted cached receipt stops only the initial cycle", 0, actions.historyReads)
            openDashboard()
            compose.onNodeWithTag("hub-refresh").performClick(); step(64)
            assertEquals(1, actions.historyReads)
            assertEquals(1, actions.manualReads)
            compose.onNodeWithTag("hub-refresh").assertIsNotEnabled()
            assertOldHistory(state)
            finish(actions)
            assertOldHistory(state)
            step(3800)
            assertEquals(1, actions.historyReads)
            compose.runOnIdle { state.value = state.value.copy(loading = true,
                snapshot = state.value.snapshot!!.copy(updated = "2026-10-09T08:01:00Z"), error = null) }
            step(300)
            assertEquals("Another API occupies the same serial queue", 1, actions.historyReads)
            compose.runOnIdle { state.value = state.value.copy(loading = false) }; step(64)
            assertEquals("Unrelated loading/freshness must not restart the four-second deadline", 2, actions.historyReads)
            assertEquals(2, state.value.historyReadReceipt!!.attempt)
            finish(actions, empty, false)
            assertOldHistory(state)
            step(4100); assertEquals(3, actions.historyReads)
            finish(actions); assertOldHistory(state)
            step(4100); assertEquals(4, actions.historyReads)
            finish(actions); assertOldHistory(state)
            step(12000); assertEquals(4, actions.historyReads)
            val exhaustedCycle = state.value.historyReadReceipt!!.cycleId
            compose.onNodeWithTag("hub-refresh").performClick(); step(64)
            assertEquals(5, actions.historyReads)
            assertEquals(2, actions.manualReads)
            assertEquals(1, state.value.historyReadReceipt!!.attempt)
            assertTrue(state.value.historyReadReceipt!!.cycleId > exhaustedCycle)
            finish(actions); step(4100); assertEquals(6, actions.historyReads)
            finish(actions, complete, false); step(12000)
            assertEquals(6, actions.historyReads)
            assertEquals(0, actions.otherReads)
            compose.onNodeWithTag("trends-history-back").performClick(); step(64)
            compose.onNodeWithTag("trends-history-detail").assertDoesNotExist()
            assertEquals(6, actions.historyReads)
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun backgroundAndHiddenViewsCancelWaitWithoutRefundingAnyAcceptedAttempt() {
        val state = mutableStateOf(initial())
        val actions = Actions(state)
        try {
            show(state, actions); assertEquals(1, actions.historyReads)
            finish(actions); step(2000)
            // The fake remains live: the policy must consult the Activity's actual lifecycle.
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED); step(12000)
            assertTrue(state.value.live); assertEquals(1, actions.historyReads)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED); step(64)
            step(3800); assertEquals(1, actions.historyReads)
            step(300); assertEquals(2, actions.historyReads)
            finish(actions)
            select("tools"); step(12000); assertEquals(2, actions.historyReads)
            select("home"); step(3800); assertEquals(2, actions.historyReads)
            step(300); assertEquals(3, actions.historyReads)
            finish(actions, empty, false); step(4100); assertEquals(4, actions.historyReads)
            finish(actions); step(12000); assertEquals(4, actions.historyReads)
            compose.runOnIdle { state.value = state.value.copy(live = false) }; step(64)
            compose.runOnIdle { state.value = state.value.copy(live = true) }; step(12000)
            assertEquals(4, actions.historyReads)
            assertEquals(4, state.value.historyReadReceipt!!.attempt)
            assertEquals(complete, state.value.history)
            assertEquals(0, actions.otherReads)
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun activityRecreationUsesRetainedReceiptBudgetAndRestartsTheFullWait() {
        val retained = initial().let { it.copy(historyReadReceipt = it.historyReadReceipt!!.copy(cycleId = 7,
            requestId = 103, attempt = 3, phase = HistoryReadPhase.FAILED, previewHadDays = true, bindingRequested = true)) }
        val state = mutableStateOf(retained)
        val actions = Actions(state)
        try {
            show(state, actions); step(2000); assertEquals(0, actions.historyReads)
            compose.activityRule.scenario.recreate()
            show(state, actions)
            step(3800); assertEquals(0, actions.historyReads)
            step(300); assertEquals(1, actions.historyReads)
            assertEquals(4, state.value.historyReadReceipt!!.attempt)
            finish(actions)
            compose.activityRule.scenario.recreate()
            show(state, actions); step(12000)
            assertEquals("Rebuilding the screen cannot turn attempt four into attempt zero", 1, actions.historyReads)
            assertEquals(4, state.value.historyReadReceipt!!.attempt)
            assertEquals(complete, state.value.history)
            assertEquals(0, actions.otherReads)
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun homeRefreshQueuesHistoryOnlyAfterStatsSuccessAndMainTrendsRefreshDoesNot() {
        val state = mutableStateOf(initial(cached = true))
        val actions = Actions(state)
        try {
            show(state, actions)
            val cached = state.value.historyReadReceipt
            compose.onNodeWithTag("hub-refresh").performClick(); step(64)
            assertTrue(actions.lastForceHistory)
            assertEquals(0, actions.historyReads)
            compose.runOnIdle { actions.finishStats(false) }; step(12000)
            assertEquals(cached, state.value.historyReadReceipt)
            assertEquals(0, actions.historyReads)
            compose.onNodeWithTag("hub-refresh").performClick(); step(64)
            assertEquals(0, actions.historyReads)
            compose.runOnIdle { actions.finishStats(true) }; step(64)
            assertEquals(1, actions.historyReads)
            assertEquals(0, actions.manualReads)
            assertEquals(complete, state.value.history)
            finish(actions, complete, false)
            select("trends")
            val accepted = state.value.historyReadReceipt
            compose.onNodeWithTag("hub-refresh").performClick(); step(64)
            assertFalse(actions.lastForceHistory)
            compose.runOnIdle { actions.finishStats(true) }; step(12000)
            assertEquals(accepted, state.value.historyReadReceipt)
            assertEquals(1, actions.historyReads)
            assertEquals(3, actions.otherReads)
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun explicitLoadButtonStartsManualCycleAndRejectedAutomaticDispatchConsumesNothing() {
        val state = mutableStateOf(initial().copy(history = null))
        val actions = Actions(state).apply { rejectAutomatic = true }
        try {
            show(state, actions)
            assertEquals(0, actions.historyReads)
            assertEquals(0, state.value.historyReadReceipt!!.attempt)
            openDashboard()
            compose.onNodeWithTag("history-load").assertIsEnabled().performClick(); step(64)
            assertEquals(1, actions.historyReads)
            assertEquals(1, actions.manualReads)
            assertEquals(1, state.value.historyReadReceipt!!.attempt)
            // Freeze the request's absent-preview state in a new manual cycle.
            finish(actions, complete, false)
            compose.runOnIdle { state.value = state.value.copy(snapshot = state.value.snapshot!!.copy(historyPreview = empty)) }
            step(64)
            compose.onNodeWithTag("hub-refresh").performClick(); step(64)
            assertFalse(state.value.historyReadReceipt!!.previewHadDays)
            finish(actions, empty, false); step(12000)
            assertEquals(empty, state.value.history)
            compose.onNodeWithTag("dashboard-empty").assertExists()
            assertEquals(2, actions.historyReads)
            assertEquals(0, actions.otherReads)
        } finally { compose.mainClock.autoAdvance = true }
    }
}
