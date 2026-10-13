package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,appearance=AppearancePreferences(reduceMotion="on")))
        compose.activity.runOnUiThread {compose.activity.setContent {
            CompositionLocalProvider(LocalHubCalendarClock provides Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"),ZoneId.of("Asia/Shanghai"))) {
                TokenTheme(true,prefs.value.language,prefs.value.appearance) {HubScreen(HubState(snapshot=snapshot,history=history),actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}
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
        val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList(),appearance=AppearancePreferences(reduceMotion="on"))
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
                TokenTheme(true,prefs.language,prefs.appearance) {HubScreen(state.value,actions,preferences=prefs)}
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

    @Test fun fixedRangeHistoryRecoveryRemovesStatusAndRestoresFullChartHeight() {
        val args=InstrumentationRegistry.getArguments()
        val english=args.getString("periodEnglish")=="true"
        val dark=args.getString("visualDark")!="false"
        val prefs=DisplayPreferences(language=if(english) LanguageChoice.ENGLISH else LanguageChoice.CHINESE,
            homeModules=emptyList(),lastPeriod="last7",monthMode="last7",
            appearance=AppearancePreferences(reduceMotion="on",textSize=if(args.getString("periodLargest")=="true") "largest" else "standard"))
        val sourceSnapshot=HubParser.parse("""{"deviceHistoryRevision":"period-row-r1","periods":{"today":{"totalTokens":500,"costUsd":0.5},"month":{"totalTokens":6000,"costUsd":6},"allTime":{"totalTokens":90000,"costUsd":90}},"devices":[{"deviceId":"public","hostname":"Public Desktop","periodWindows":{"timeZone":"Etc/UTC","today":{"key":"2026-10-09","endsAt":"2999-10-10T00:00:00Z"}},"periods":{"today":{"totalTokens":500,"costUsd":0.5},"allTime":{"totalTokens":90000}}}]}""").copy(historyPreview=history)
        val retained="""{"devices":[{"deviceId":"public","historyAvailable":true,"history":{"daily":[{"date":"2026-10-03","tokens":100,"cost":0.1},{"date":"2026-10-08","tokens":200,"cost":0.2},{"date":"2026-10-09","tokens":50,"cost":0.05}]}}]}"""
        val state=mutableStateOf(HubState(snapshot=sourceSnapshot,deviceHistories=DeviceHistories(
            FixedPeriods.signature(sourceSnapshot),listOf(DeviceHistory("public",false,null)))))
        val actions=object:Actions() {
            override fun loadDeviceHistory() {
                super.loadDeviceHistory()
                state.value=state.value.copy(deviceHistories=DeviceHistoryParser.parse(
                    DeviceHistoryParser.project(retained,FixedPeriods.signature(state.value.snapshot!!))))
            }
        }
        val suffix="${if(english) "en" else "zh"}-${if(dark) "dark" else "light"}"
        fun save(phase:String) {
            compose.waitForIdle()
            saveVisualEvidence(compose.activity,compose.onRoot(),"fixed-trends-recovery-$phase-$suffix.png")
        }
        fun pending(message:String,phase:String) {
            compose.onNodeWithTag("hub-total-tokens").assertTextEquals("—")
            compose.onNodeWithTag("fixed-period-range").assertDoesNotExist()
            compose.onNodeWithTag("trends-main-chart").assertDoesNotExist()
            show("fixed-period-unavailable")
            compose.onNodeWithTag("fixed-period-unavailable").assertIsDisplayed().assertTextEquals(message)
            show("fixed-period-read")
            compose.onNodeWithTag("fixed-period-read").assertIsDisplayed().assertIsEnabled()
                .assertTextEquals(if(english) "Read device history" else "读取设备历史")
            val read=compose.onNodeWithTag("fixed-period-read").getUnclippedBoundsInRoot()
            val node=compose.onNodeWithTag("fixed-period-read").fetchSemanticsNode()
            val pixels=node.boundsInRoot
            val minimum=with(node.layoutInfo.density) {48.dp.roundToPx()}
            println("PUBLIC_PERIOD_READ_GEOMETRY dpWidth=${(read.right-read.left).value} dpHeight=${(read.bottom-read.top).value} pixelWidth=${pixels.width} pixelHeight=${pixels.height} rawWidth=${node.size.width} rawHeight=${node.size.height} minimumPx=$minimum density=${compose.activity.resources.displayMetrics.density}")
            save("$phase-before-size-assert")
            assertTrue("The manual read target retains 48dp width and height in raw layout pixels",node.size.width>=minimum && node.size.height>=minimum)
            save(phase)
        }
        fun recovered(phase:String):Pair<Float,Float> {
            compose.onNodeWithTag("hub-total-tokens").assertTextEquals("800")
            compose.onNodeWithTag("hub-total-cost").assertTextEquals("$0.8000")
            compose.onNodeWithTag("fixed-period-unavailable").assertDoesNotExist()
            compose.onNodeWithTag("fixed-period-read").assertDoesNotExist()
            compose.onNodeWithTag("fixed-period-range").assertDoesNotExist()
            compose.onNodeWithTag("hub-content").performScrollToIndex(0)
            compose.onNodeWithTag("trends-main-chart").assertIsDisplayed()
            val viewport=compose.onNodeWithTag("hub-content").getUnclippedBoundsInRoot()
            val main=compose.onNodeWithTag("trends-main").getUnclippedBoundsInRoot()
            val chart=compose.onNodeWithTag("trends-main-chart").getUnclippedBoundsInRoot()
            assertEquals("A recovered range leaves only the normal top inset, without a status row or gap",12f,(main.top-viewport.top).value,1f)
            assertTrue("Recovered chart retains its full preferred height",(chart.bottom-chart.top).value>=159.5f)
            compose.onNodeWithTag("trends-main-axis-start").assertTextEquals("2026-09")
            compose.onNodeWithTag("trends-main-axis-end").assertTextEquals("2026-10")
            show("trends-main-active-days")
            compose.onNodeWithTag("trends-main-active-days").assertTextEquals("106")
            compose.onNodeWithTag("trends-main-streak").assertTextEquals("14")
            assertEquals(history,state.value.snapshot!!.historyPreview)
            assertEquals(500L,state.value.snapshot!!.periods.getValue("today").tokens)
            compose.onNodeWithTag("hub-content").performScrollToIndex(0)
            save(phase)
            return (main.bottom-main.top).value to (chart.bottom-chart.top).value
        }
        compose.activity.runOnUiThread {compose.activity.setContent {
            CompositionLocalProvider(LocalHubCalendarClock provides Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"),ZoneId.of("Asia/Shanghai"))) {
                TokenTheme(dark,prefs.language,prefs.appearance) {HubScreen(state.value,actions,preferences=prefs)}
            }
        }}
        selectTrends()
        pending(if(english) "Some devices do not provide complete history. This range is unavailable." else "部分设备未提供完整历史，无法显示此范围。","missing")
        compose.onNodeWithTag("fixed-period-read").performTouchInput {click(Offset(center.x,1f))}
        val firstHeight=recovered("ready")
        assertEquals(1,actions.devices)
        compose.runOnIdle {state.value=state.value.copy(snapshot=sourceSnapshot.copy(deviceHistoryRevision="period-row-r2"))}
        pending(if(english) "Read each device’s retained history to calculate this range." else "读取每台设备的完整历史后，计算此范围。","revision-pending")
        compose.onNodeWithTag("fixed-period-read").performTouchInput {click(Offset(center.x,height.toFloat()-1f))}
        val secondHeight=recovered("ready-again")
        assertEquals("A second recovery restores the same complete Trends item height",firstHeight.first,secondHeight.first,0.01f)
        assertEquals("A second recovery restores the same chart height",firstHeight.second,secondHeight.second,0.01f)
        assertEquals(2,actions.devices)
        assertEquals(0,actions.histories)
        assertEquals(0,actions.snapshots)
    }
}
