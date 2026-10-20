package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** The exact public 84-day fixture used by the unmodified desktop Dashboard capture. */
class DashboardParityScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private class Actions:HubUiActions {
        var histories=0;var snapshots=0
        override fun connect(address:String,credential:String,alternate:String) {}
        override fun refreshSaved() {snapshots++}
        override fun loadHistory() {histories++}
        override fun switchEndpoint() {}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadModelNames() {}
        override fun loadSubscriptions() {}
        override fun forget() {}
        override fun renameDevice(id:String,input:String) {}
    }
    private val history=HubHistory(List(84) {n->
        val multiple=(n%11+1).toLong()
        HistoryRow(LocalDate.of(2026,10,9).minusDays((83-n).toLong()).toString(),multiple*1430000,"1",
            perClient=linkedMapOf("codex" to HistoryAttribution(multiple*1000000,".7"),"claude" to HistoryAttribution(multiple*430000,".3")),
            perModel=linkedMapOf("gpt-5.5" to HistoryAttribution(multiple*1000000,".7"),"claude-sonnet-4.6" to HistoryAttribution(multiple*430000,".3")))
    },emptyList(),HistorySummary(165164772,"136.08",106,14,40,198000000,22000000,"gpt-5.5",1600))
    private val snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":165164772,"costUsd":136.08}}}""").copy(historyPreview=history)
    private fun open() {
        compose.onNodeWithTag("hub-view-selector").performClick()
        compose.onNodeWithTag("view-trends").performScrollTo().performClick()
        compose.onNodeWithTag("trends-main-open-history").performScrollTo().performClick()
    }
    @Test fun completeDashboardUsesSourceSummaryRankingsControlsAndChartModes() {
        val arguments=InstrumentationRegistry.getArguments()
        val dark=arguments.getString("visualDark")!="false"
        val english=arguments.getString("visualLanguage")=="en"
        val language=if(english) LanguageChoice.ENGLISH else LanguageChoice.CHINESE
        val suffix=arguments.getString("visualSuffix") ?: "phone"
        val actions=Actions()
        val preferences=mutableStateOf(DisplayPreferences(language=language,theme=if(dark) ThemeChoice.DARK else ThemeChoice.LIGHT))
        compose.activity.runOnUiThread {compose.activity.setContent {
            CompositionLocalProvider(LocalHubCalendarClock provides Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"),ZoneId.of("Asia/Shanghai"))) {
                TokenTheme(dark,language) {HubScreen(HubState(snapshot=snapshot,history=history),actions,preferences=preferences.value,
                    updateDisplay={preferences.value=it.apply(preferences.value)})}
            }
        }}
        fun save(view:String) {
            compose.mainClock.advanceTimeBy(1200)
            compose.waitForIdle()
            saveVisualEvidence(compose.activity,compose.onRoot(),"dashboard-$view-${if(english) "en" else "zh-CN"}-${if(dark) "dark" else "light"}-$suffix.png")
        }
        open()
        compose.onNodeWithTag("dashboard-tab-trends").assertIsSelected()
        compose.onNodeWithTag("dashboard-tab-overview").performClick()
        compose.onNodeWithTag("dashboard-trends").assertDoesNotExist()
        compose.onNodeWithTag("dashboard-card-tokens-value",useUnmergedTree=true).assertTextEquals("165.2M")
        compose.onNodeWithTag("dashboard-card-cost-value",useUnmergedTree=true).assertTextEquals("$136.08")
        compose.onNodeWithTag("dashboard-card-time-value",useUnmergedTree=true).assertTextEquals("55h 0m")
        compose.onNodeWithTag("dashboard-card-messages-value",useUnmergedTree=true).assertTextEquals("1.6K")
        save("overview")
        compose.onNodeWithTag("dashboard-ranking-models-gpt-5.5-percent",useUnmergedTree=true).performScrollTo().assertTextEquals("69.9%")
        compose.onNodeWithTag("dashboard-ranking-models-gpt-5.5-value",useUnmergedTree=true).assertTextEquals("490M")
        compose.onNodeWithTag("dashboard-heatmap-metric-tokens").performScrollTo().performClick()
        assertEquals("tokens",preferences.value.heatmapMetric)
        compose.onNodeWithTag("dashboard-card-tokens-value",useUnmergedTree=true).performScrollTo()
        save("overview-tokens")
        compose.onNodeWithTag("dashboard-tab-trends").performClick()
        compose.onNodeWithTag("dashboard-overview").assertDoesNotExist()
        compose.onNodeWithTag("dashboard-range-30").assertIsSelected()
        compose.onNodeWithTag("dashboard-chart").performScrollTo().assertIsDisplayed()
        save("bars-tool")
        compose.onNodeWithTag("dashboard-dimension-model").performScrollTo().performClick()
        // The legend has its own bounded scroll area. Bring that area into the
        // outer viewport before scrolling a row within it on a short landscape screen.
        compose.onNodeWithTag("dashboard-legend").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("dashboard-legend-gpt-5.5").performScrollTo().assertIsDisplayed()
        save("bars-model")
        compose.onNodeWithTag("dashboard-mode-kline").performScrollTo().performClick()
        compose.onNodeWithTag("dashboard-dimension-model").assertDoesNotExist()
        compose.onNodeWithTag("dashboard-legend").assertDoesNotExist()
        compose.onNodeWithTag("dashboard-chart").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) {it()}
        compose.onNodeWithTag("dashboard-candle-detail").assertIsDisplayed()
        compose.onNodeWithText(if(english) "Close" else "关闭").performClick()
        save("kline")
        compose.onNodeWithTag("hub-refresh").performClick()
        assertEquals(1,actions.histories);assertEquals(0,actions.snapshots)
        compose.onNodeWithTag("trends-history-back").performClick()
        compose.onNodeWithTag("hub-pinned-total").assertIsDisplayed()
        assertEquals(1,actions.histories)
    }

    @Test fun monthlyOnlyAndEmptyHistoryRemainRefreshableWithoutFabricatingDailyUsage() {
        val state=mutableStateOf(HubState(snapshot=snapshot,history=HubHistory(emptyList(),listOf(HistoryRow("2026-10",1000,"1")))))
        val actions=Actions()
        compose.activity.runOnUiThread {compose.activity.setContent {
            CompositionLocalProvider(LocalHubCalendarClock provides Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"),ZoneId.of("Asia/Shanghai"))) {
                TokenTheme(true,LanguageChoice.ENGLISH) {HubScreen(state.value,actions,preferences=DisplayPreferences(language=LanguageChoice.ENGLISH))}
            }
        }}
        open()
        compose.onNodeWithTag("dashboard-tab-overview").performClick()
        compose.onNodeWithTag("dashboard-heatmap").assertExists()
        compose.onNodeWithTag("dashboard-empty").assertDoesNotExist()
        compose.onNodeWithTag("dashboard-card-tokens-value",useUnmergedTree=true).assertTextEquals("—")
        compose.onNodeWithTag("dashboard-tab-trends").performClick()
        compose.onNodeWithText("Hub has monthly history but no daily records for this chart.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {state.value=state.value.copy(history=HubHistory(emptyList(),emptyList()),error=R.string.error_revision_changed)}
        compose.onNodeWithTag("dashboard-empty").assertIsDisplayed()
        compose.onNodeWithTag("dashboard-chart").assertDoesNotExist()
        compose.onNodeWithTag("hub-refresh").performClick()
        assertEquals(1,actions.histories)
        compose.onNodeWithTag("trends-history-back").performClick()
        compose.onNodeWithTag("trends-main").assertExists()
    }

    @Test fun actualRootUsesSuccessfulReadEventsAndKeepsStaticRegionsOutOfChartFrames() {
        val state=mutableStateOf(HubState(snapshot=snapshot,history=history))
        val commits=AtomicInteger()
        val actions=Actions()
        val animationClock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
        compose.activity.runOnUiThread {compose.activity.setContent {
            CompositionLocalProvider(LocalDesktopAnimationClock provides animationClock,
                LocalHeadlineRenderObserver provides {part->if(part=="hub") commits.incrementAndGet()},
                LocalHubCalendarClock provides Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"),ZoneId.of("Asia/Shanghai"))) {
                TokenTheme(true,LanguageChoice.ENGLISH) {HubScreen(state.value,actions,preferences=DisplayPreferences(language=LanguageChoice.ENGLISH,
                    appearance=AppearancePreferences(reduceMotion="off")))}
            }
        }}
        open()
        compose.onNodeWithTag("dashboard-tab-trends").performClick()
        compose.onNodeWithTag("dashboard-mode-kline").performClick()
        compose.mainClock.advanceTimeBy(1200)
        fun ink():Int {
            val image=compose.onNodeWithTag("dashboard-chart").captureToImage().toPixelMap()
            val colors=listOf(androidx.compose.ui.graphics.Color(DashboardTrendProjection.CANDLE_UP_ARGB),
                androidx.compose.ui.graphics.Color(DashboardTrendProjection.CANDLE_DOWN_ARGB))
            return (0 until image.width).sumOf {x->(0 until image.height).count {y->
                val value=image[x,y]
                colors.any {target->listOf(value.red-target.red,value.green-target.green,value.blue-target.blue).all {abs(it*255)<3}}
            }}
        }
        val steady=ink()
        assertTrue("Actual K-line fixture has painted bodies and wicks",steady>100)
        compose.mainClock.autoAdvance=false
        compose.runOnIdle {state.value=state.value.copy(historyRenderRevision=state.value.historyRenderRevision+1)}
        var restarted=steady
        repeat(8) {
            if(restarted==0 || restarted>=steady) {compose.mainClock.advanceTimeBy(16);restarted=ink()}
        }
        assertTrue("An accepted equal history read replays after its final native layout commit: current=$restarted steady=$steady",restarted in 1 until steady)
        val beforeFrames=commits.get()
        compose.mainClock.advanceTimeBy(256)
        val middle=ink()
        assertTrue("Actual root chart paints progress",middle>restarted)
        assertEquals("Chart frames do not recompose the Hub owner",beforeFrames,commits.get())
        compose.mainClock.advanceTimeBy(1200)
        assertEquals(steady,ink())
        compose.runOnIdle {state.value=state.value.copy(savedAt=Instant.parse("2026-10-09T08:01:00Z").toEpochMilli())}
        compose.mainClock.advanceTimeBy(32)
        assertEquals("A stats freshness timestamp is not a Dashboard history render",steady,ink())
        compose.onNodeWithTag("hub-settings-toggle").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("hub-settings-toggle").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("dashboard-mode-kline").assertIsSelected()
        assertEquals("Settings return retains the already consumed window entry",steady,ink())
        assertEquals(0,actions.histories);assertEquals(0,actions.snapshots)
        compose.mainClock.autoAdvance=true
        compose.onNodeWithTag("dashboard-mode-bars").performClick()
        compose.onNodeWithTag("dashboard-dimension-model").performClick()
        compose.onNodeWithTag("dashboard-legend-gpt-5.5").performScrollTo().assertExists()
        compose.onNodeWithTag("dashboard-legend-claude-sonnet-4.6").assertExists()
        compose.runOnIdle {state.value=state.value.copy(modelNames=ModelNames(
            linkedMapOf("gpt-5.5" to "Public combined model","claude-sonnet-4.6" to "Public combined model"),"off"))}
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithTag("dashboard-legend-Public combined model").performScrollTo().assertExists()
        compose.onNodeWithTag("dashboard-legend-gpt-5.5").assertDoesNotExist()
        compose.onNodeWithTag("dashboard-legend-claude-sonnet-4.6").assertDoesNotExist()
        compose.onNodeWithTag("dashboard-tab-overview").performClick()
        compose.onNodeWithTag("dashboard-card-favorite-value",useUnmergedTree=true).performScrollTo().assertTextEquals("Public combined model")
        compose.onNodeWithTag("dashboard-ranking-models-Public combined model-value",useUnmergedTree=true)
            .performScrollTo().assertTextEquals("700.7M")
        compose.onNodeWithTag("dashboard-card-tokens-value",useUnmergedTree=true).performScrollTo().assertTextEquals("165.2M")
        assertEquals("Shared display rules do not request history again",0,actions.histories)
        assertEquals("Raw history retains its original model identities",setOf("gpt-5.5","claude-sonnet-4.6"),state.value.history!!.daily.first().perModel.keys)
    }
}
