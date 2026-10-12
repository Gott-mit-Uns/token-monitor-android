package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Public fixtures only; the real network check is opt-in and never saves screenshots. */
class HomeChartScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private class Actions : HubUiActions {
        override fun connect(address: String,credential: String,alternate: String) {}
        override fun refreshSaved() {}
        override fun switchEndpoint() {}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadHistory() {}
        override fun loadModelNames() {}
        override fun loadSubscriptions() {}
        override fun forget() {}
        override fun renameDevice(id: String,input: String) {}
    }
    @Test fun liveTodayReplacesFrozenPreviewAndSelectionsPersistWithoutChangingHistory() {
        val today=LocalDate.now(); val yesterday=today.minusDays(1)
        val source=HubParser.parse("""{"periods":{"today":{"totalTokens":1000,"costUsd":2.5},"allTime":{"totalTokens":90000,"costUsd":90}},"historyPreview":{"daily":[{"date":"$yesterday","tokens":100,"cost":0.1},{"date":"$today","tokens":25,"cost":0.025}],"monthly":[],"summary":{"activeDays":106}}}""")
        val state=mutableStateOf(HubState(snapshot=source,savedAt=System.currentTimeMillis()))
        val preferences=mutableStateOf(DisplayPreferences(homeModules=defaultHomeModules))
        val store=DisplayPreferenceStore(compose.activity,"test-home-ui-${UUID.randomUUID()}.bin")
        val dark=InstrumentationRegistry.getArguments().getString("visualDark") != "false"
        try {
            compose.activity.runOnUiThread { compose.activity.setContent { TokenTheme(dark) {
                HubScreen(state.value,Actions(),preferences=preferences.value,updateDisplay={ preferences.value=it.apply(preferences.value);store.save(preferences.value) })
            } } }
            fun scroll(tag: String) { compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag)) }
            fun todayCount(count: String) { scroll("home-activity-chart"); compose.onNodeWithTag("home-activity-chart").assertContentDescriptionEquals("活动, $today，$count Token") }
            todayCount("1,000")
            compose.onNodeWithText("活跃 106 天").assertExists()
            compose.runOnIdle { state.value=state.value.copy(history=HubHistory(emptyList(),emptyList())) }
            todayCount("1,000")
            val history=HubHistory(listOf(HistoryRow(yesterday.toString(),500,"0.5"),HistoryRow(today.toString(),50,"0.05")),emptyList())
            val next=source.periods.getValue("today").copy(tokens=2000,cost="4")
            compose.runOnIdle { state.value=state.value.copy(history=history,snapshot=source.copy(periods=source.periods+("today" to next))) }
            todayCount("2,000")
            scroll("home-trend-chart"); compose.onNodeWithContentDescription("最高 2,000 Token").assertIsDisplayed()
            compose.onNodeWithTag("hub-period-allTime").performClick()
            todayCount("2,000")
            compose.onNodeWithTag("hub-settings-toggle").performClick()
            openSettingsSection(compose,"main")
            scroll("main-view-home-settings");compose.onNodeWithTag("main-view-home-settings").performClick()
            compose.onNodeWithTag("home-activity-details").assertDoesNotExist()
            scroll("home-activity-settings");compose.onNodeWithTag("home-activity-settings").performClick()
            scroll("home-heatmap-tokens"); compose.onNodeWithTag("home-heatmap-tokens").performClick().assertIsSelected()
            scroll("home-active-days-year"); compose.onNodeWithTag("home-active-days-year").performClick().assertIsSelected()
            assertEquals("tokens",store.load().heatmapMetric); assertEquals("year",store.load().homeActiveDaysWindow)
            scroll("home-modules-reset");compose.onNodeWithTag("home-modules-reset").assertIsNotEnabled()
            scroll("home-module-tools");compose.onNodeWithTag("home-module-tools").performClick().assertIsOff()
            assertFalse("Hide only changes local modules", "tools" in store.load().homeModules)
            val retained=store.load().homeModules
            scroll("home-modules-show-all");compose.onNodeWithTag("home-modules-show-all").performClick()
            assertEquals(listOf("limits","tools","models","sessions","devices","activity"),store.load().homeModules)
            compose.onNodeWithTag("home-modules-reset").performClick()
            assertEquals(listOf("limits","tools","models","sessions","devices","activity"),store.load().homeModules)
            assertEquals("tokens",store.load().heatmapMetric);assertEquals("year",store.load().homeActiveDaysWindow)
            scroll("home-active-days-year")
            saveVisualEvidence(compose.activity,compose.onRoot(),"home-settings-${if(dark) "dark" else "light"}.png")
            compose.onNodeWithTag("hub-settings-toggle").performClick()
            todayCount("2,000"); compose.onNodeWithText("近 12 个月活跃 2 天").assertExists()
            saveVisualEvidence(compose.activity,compose.onRoot(),"home-live-${if(dark) "dark" else "light"}.png")
            assertEquals(50L,history.daily.last().tokens)
            assertEquals(25L,source.historyPreview!!.daily.first().tokens)
            val expired=yesterday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            compose.runOnIdle { state.value=state.value.copy(savedAt=expired) }
            todayCount("50")
        } finally { store.clear() }
    }
    @Test fun actualDayTapAndAccessibleNavigationKeepTheSelectedDateDuringLiveRefresh() {
        val today=LocalDate.now();val yesterday=today.minusDays(1);val zeroDay=today.minusDays(2)
        val history=HubHistory(listOf(HistoryRow(zeroDay.toString(),0,"0"),HistoryRow(yesterday.toString(),100,"0.1"),HistoryRow(today.toString(),50,"0.05")),emptyList())
        val source=HubParser.parse("""{"periods":{"today":{"totalTokens":5000,"costUsd":5,"unpricedTokens":20}}}""")
        val state=mutableStateOf(HubState(snapshot=source,history=history,savedAt=System.currentTimeMillis()))
        val preferences=mutableStateOf(DisplayPreferences(currency="CNY",currencyRates=mapOf("CNY" to "7")))
        val dark=InstrumentationRegistry.getArguments().getString("visualDark") != "false"
        compose.activity.runOnUiThread { compose.activity.setContent { TokenTheme(dark,preferences.value.language) { HubScreen(state.value,Actions(),preferences=preferences.value) } } }
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("home-activity-chart"))
        val projection=HomeCharts.project(history,source.periods["today"],today)
        val point=projection.cells.last();val density=compose.activity.resources.displayMetrics.density
        compose.onNodeWithTag("home-activity-month-${today.toString().take(7)}").assertExists()
        val chart=compose.onNodeWithTag("home-activity-chart")
        // LazyColumn can compose a tall item's chart before that child enters the
        // landscape viewport. Bring the actual tap point into view vertically.
        repeat(6) {
            val bounds=chart.getUnclippedBoundsInRoot()
            val targetY=(point.weekday*12+4+bounds.top.value)*density
            val viewport=compose.onNodeWithTag("hub-content").fetchSemanticsNode().boundsInRoot
            val delta=when { targetY >= viewport.bottom -> targetY-viewport.bottom+32f; targetY < viewport.top -> targetY-viewport.top-32f; else -> 0f }
            if (delta != 0f) compose.onNodeWithTag("hub-content").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f,delta) }
        }
        val full=chart.getUnclippedBoundsInRoot();val visible=chart.fetchSemanticsNode().boundsInRoot
        // TouchInjectionScope starts at the clipped visible bounds of a scrolled chart.
        val tap=Offset((point.column*12+4+full.left.value)*density-visible.left,(point.weekday*12+4+full.top.value)*density-visible.top)
        chart.performTouchInput { click(tap) }
        compose.onNodeWithTag("home-activity-selected-date").assertTextEquals(today.toString())
        compose.onNodeWithTag("home-activity-day-tokens").assertTextEquals("5,000 Token")
        compose.onNodeWithTag("home-activity-day-cost").assertTextEquals("已知估算费用：¥35.00 + 20 未定价 tokens")
        compose.onNodeWithTag("home-activity-day-next").assertIsNotEnabled()
        val live=source.periods.getValue("today").copy(tokens=6000,cost="6",unpriced=30)
        compose.runOnIdle { state.value=state.value.copy(snapshot=source.copy(periods=mapOf("today" to live))) }
        compose.onNodeWithTag("home-activity-selected-date").assertTextEquals(today.toString())
        compose.onNodeWithTag("home-activity-day-tokens").assertTextEquals("6,000 Token")
        compose.onNodeWithTag("home-activity-day-cost").assertTextEquals("已知估算费用：¥42.00 + 30 未定价 tokens")
        saveVisualEvidence(compose.activity,compose.onNodeWithTag("home-activity-day"),"home-day-${if(dark) "dark" else "light"}.png")
        compose.onNodeWithTag("home-activity-day-previous").performClick()
        compose.onNodeWithTag("home-activity-selected-date").assertTextEquals(yesterday.toString())
        compose.onNodeWithTag("home-activity-day-tokens").assertTextEquals("100 Token")
        compose.onNodeWithTag("home-activity-day-cost").assertTextEquals("已知估算费用：¥0.7000")
        compose.onNodeWithTag("home-activity-day-previous").performClick()
        compose.onNodeWithTag("home-activity-day-tokens").assertTextEquals("0 Token")
        compose.onNodeWithTag("home-activity-day-cost").assertTextEquals("已知估算费用：¥0.0000")
        compose.onNodeWithTag("home-activity-day-previous").performClick()
        compose.onNodeWithTag("home-activity-day-tokens").assertTextEquals("— Token")
        compose.onNodeWithTag("home-activity-day-cost").assertTextEquals("已知估算费用：—")
        compose.onNodeWithText("该日数据不可用。").assertExists()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("home-activity-chart").performTouchInput { swipeRight() }
        compose.onNodeWithTag("home-activity-day").assertDoesNotExist()
        compose.onNodeWithTag("home-activity-chart").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("home-activity-selected-date").assertTextEquals(today.toString())
        assertEquals(50L,history.daily.last().tokens);assertEquals("0.05",history.daily.last().cost)
        compose.onNodeWithText("关闭").performClick()
        compose.runOnIdle { preferences.value=preferences.value.copy(language=LanguageChoice.ENGLISH) }
        compose.onNodeWithTag("home-activity-chart").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("home-activity-day-cost").assertTextEquals("Known estimated cost: ¥42.00 + 30 unpriced tokens")
        compose.onNodeWithText("Close").performClick()
    }
}
