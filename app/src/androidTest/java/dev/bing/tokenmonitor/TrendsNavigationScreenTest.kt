package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Public actual-screen journeys; main snapshot and dashboard history are distinct sources. */
class TrendsNavigationScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val history=HubHistory(List(40) {n->HistoryRow(java.time.LocalDate.of(2026,10,9).minusDays(n.toLong()).toString(),30000L,"1",perClient=mapOf("codex" to HistoryAttribution(30000L,"1")),activeTimeMs=120000)},
        listOf(HistoryRow("2026-10",100000L,"2"),HistoryRow("2026-09",80000L,"1")),HistorySummary(180000,"3",106,14,40,900000,50000,"public-model",40))
    private val snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":34567,"costUsd":1.25},"month":{"totalTokens":77777,"costUsd":2.5},"allTime":{"totalTokens":99999,"costUsd":3.75}}}""").copy(historyPreview=history)
    private open class Actions(val historyRead:()->Unit={}) : HubUiActions {
        var snapshots=0;var histories=0;var devices=0
        override fun connect(address:String,credential:String,alternate:String) {}
        override fun refreshSaved() {snapshots++}
        override fun loadHistory() {histories++;historyRead()}
        override fun loadDeviceHistory() {devices++}
        override fun switchEndpoint() {}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadModelNames() {}
        override fun loadSubscriptions() {}
        override fun forget() {}
        override fun renameDevice(id:String,input:String) {}
    }
    private fun selectTrends() {
        compose.onNodeWithTag("hub-view-selector").performClick()
        compose.onNodeWithTag("view-trends").performScrollTo().performClick()
    }
    private fun show(tag:String)=compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))
    private fun back()=compose.activity.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}

    @Test fun mainPeriodAndDashboardRangeStayIndependentAcrossBackAndSettings() {
        val actions=Actions()
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH))
        compose.activity.runOnUiThread {compose.activity.setContent {
            CompositionLocalProvider(LocalHubCalendarClock provides Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"),ZoneId.of("Asia/Shanghai"))) {
                TokenTheme(true,prefs.value.language) {HubScreen(HubState(snapshot=snapshot,history=history),actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}
            }
        }}
        selectTrends()
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("34,567")
        compose.onNodeWithTag("hub-total-cost").assertTextEquals("$1.2500")
        show("trends-main-peak")
        compose.onNodeWithTag("trends-main-active-days").assertTextEquals("106")
        compose.onNodeWithTag("trends-main-streak").assertTextEquals("14")
        compose.onNodeWithTag("trends-main-time").assertTextEquals("2m")
        compose.onNodeWithText("Historical usage").assertDoesNotExist()
        compose.onNodeWithTag("hub-period-month").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("77,777")
        show("trends-main-chart")
        compose.onNodeWithTag("trends-main-chart").performClick()
        compose.onNodeWithTag("trends-history-detail").assertExists()
        compose.onNodeWithTag("hub-pinned-total").assertDoesNotExist()
        compose.onNodeWithTag("hub-period-month").assertDoesNotExist()
        compose.onNodeWithTag("dashboard-tab-overview").assertIsSelected()
        compose.onNodeWithTag("dashboard-tab-trends").performClick()
        compose.onNodeWithTag("dashboard-range-7").performClick()
        compose.onNodeWithTag("dashboard-chart").assertContentDescriptionEquals("Usage chart with 7 records. Select a column for details.")
        compose.onNodeWithTag("dashboard-open-records").performScrollTo().performClick()
        compose.onNodeWithText("7 days").performScrollTo().performClick()
        compose.onNodeWithText("2026-10-03 to 2026-10-09").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("dashboard-records-back").performScrollTo().performClick()
        compose.onNodeWithTag("dashboard-range-7").assertIsSelected()
        compose.onNodeWithTag("hub-refresh").performClick()
        assertEquals(1,actions.histories);assertEquals(0,actions.snapshots)
        compose.onNodeWithTag("hub-settings-toggle").performClick()
        compose.onNodeWithTag("hub-settings-toggle").performClick()
        compose.onNodeWithTag("dashboard-range-7").assertIsSelected()
        compose.onNodeWithTag("trends-history-back").performClick()
        compose.onNodeWithTag("hub-period-month").assertIsSelected()
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("77,777")
        show("trends-main-open-history")
        compose.onNodeWithTag("trends-main-open-history").performClick()
        compose.onNodeWithTag("dashboard-tab-overview").assertIsSelected()
        compose.onNodeWithTag("dashboard-tab-trends").performClick()
        compose.onNodeWithTag("dashboard-range-30").assertIsSelected()
        back()
        compose.onNodeWithTag("trends-main").assertExists()
        compose.onNodeWithTag("trends-history-detail").assertDoesNotExist()
        compose.onNodeWithTag("hub-refresh").performClick()
        assertEquals(1,actions.snapshots);assertEquals(1,actions.histories)
        assertEquals("month",prefs.value.lastPeriod)
        compose.onNodeWithTag("hub-period-month").performClick()
        compose.onNodeWithTag("hub-period-menu-last7").performClick()
        compose.onNodeWithTag("trends-main-chart").assertDoesNotExist()
        show("trends-main-open-history")
        compose.onNodeWithTag("trends-main-open-history").performClick()
        compose.onNodeWithTag("dashboard-tab-overview").assertIsSelected()
        compose.onNodeWithTag("dashboard-tab-trends").performClick()
        compose.onNodeWithTag("dashboard-range-30").assertIsSelected()
        back()
        assertEquals("last7",prefs.value.lastPeriod)
        compose.onNodeWithTag("trends-main-chart").assertDoesNotExist()
        assertEquals(1,actions.histories)
        assertEquals(0,actions.devices)
    }

    @Test fun previewUpdatesWithoutFullHistoryReadsAndLoadedDashboardDoesNotRereadOnReentry() {
        // This journey changes only the main-window preview while the Hub's full
        // history revision remains fixed, so the two retained sources stay distinct.
        val sourceSnapshot=snapshot.copy(historyRevision="public-preview-source-r1")
        val coordinator=HistoryReadCoordinator()
        val state=mutableStateOf(HubState(snapshot=sourceSnapshot,modelNames=ModelNames(emptyMap(),"off"),
            historyReadReceipt=coordinator.target(HistoryReadKey(0,homeHistorySignature(sourceSnapshot)))))
        var requestId=0L
        val actions=object:Actions() {
            override fun loadHistory() {loadHistory(false)}
            override fun loadHistory(manual:Boolean):Boolean {
                if(state.value.loading || !state.value.live) return false
                val key=HistoryReadKey(state.value.connectionRevision,homeHistorySignature(state.value.snapshot))
                val start=coordinator.begin(key,++requestId,manual,state.value.snapshot?.historyPreview?.daily?.isNotEmpty()==true) ?: return false
                super.loadHistory()
                state.value=state.value.copy(loading=true,historyReadReceipt=start)
                state.value=state.value.copy(loading=false,history=history,
                    historyReadReceipt=coordinator.finish(start,HistoryReadPhase.RESOLVED,history.daily.size))
                return true
            }
        }
        compose.activity.runOnUiThread {compose.activity.setContent {
            CompositionLocalProvider(LocalHubCalendarClock provides Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"),ZoneId.of("Asia/Shanghai"))) {
                TokenTheme(true,LanguageChoice.ENGLISH) {HubScreen(state.value,actions,preferences=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList()))}
            }
        }}
        selectTrends()
        compose.runOnIdle {state.value=state.value.copy(live=true)}
        compose.waitForIdle()
        assertEquals(0,actions.histories)
        compose.onNodeWithTag("trends-main-open-history").performClick()
        compose.waitUntil(5000) {actions.histories==1}
        compose.onNodeWithText("Usage Dashboard").assertIsDisplayed()
        compose.onNodeWithTag("dashboard-tab-overview").assertIsSelected()
        back()
        compose.onNodeWithTag("trends-main-open-history").performClick()
        compose.waitForIdle()
        assertEquals(1,actions.histories)
        compose.onNodeWithTag("trends-history-back").performClick()
        val newPreview=history.copy(summary=history.summary!!.copy(activeDays=7,currentStreak=2))
        compose.runOnIdle {state.value=state.value.copy(snapshot=sourceSnapshot.copy(historyPreview=newPreview))}
        show("trends-main-active-days")
        compose.onNodeWithTag("trends-main-active-days").assertTextEquals("7")
        compose.onNodeWithTag("trends-main-streak").assertTextEquals("2")
        assertEquals(106L,state.value.history!!.summary!!.activeDays)
        compose.runOnIdle {state.value=state.value.copy(snapshot=sourceSnapshot.copy(historyPreview=HubHistory(emptyList(),emptyList()),periods=sourceSnapshot.periods+ ("today" to sourceSnapshot.periods.getValue("today").copy(tokens=0))))}
        compose.onNodeWithText("No usage history yet").assertIsDisplayed()
        compose.onNodeWithTag("trends-main-chart").assertDoesNotExist()
        compose.onNodeWithTag("trends-main-active-days").assertDoesNotExist()
        show("trends-main-open-history")
        compose.onNodeWithTag("trends-main-open-history").performClick()
        compose.onNodeWithText("Usage Dashboard").assertIsDisplayed()
        compose.onNodeWithTag("dashboard-tab-overview").assertIsSelected()
        assertEquals(1,actions.histories);assertEquals(0,actions.snapshots)
        compose.runOnIdle {
            val revision=state.value.connectionRevision+1
            state.value=state.value.copy(connectionRevision=revision,live=false,history=null,
                historyReadReceipt=coordinator.target(HistoryReadKey(revision,homeHistorySignature(state.value.snapshot))))
        }
        compose.onNodeWithTag("trends-history-detail").assertDoesNotExist()
        compose.onNodeWithText("No usage history yet").assertIsDisplayed()
        assertEquals(1,actions.histories);assertEquals(0,actions.devices)
    }
}
