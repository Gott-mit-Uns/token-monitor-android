package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.io.File

/** Synthetic local data only; gesture callbacks never have a Repository/network dependency. */
class DailyChartInspectionScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val rows = listOf(
        HistoryRow("2026-10-01", 10, ".1", perClient = mapOf("codex" to HistoryAttribution(10, ".1"))),
        HistoryRow("2026-10-02", 0, "0"),
        HistoryRow("2026-10-03", null, null))
    private var taps = 0
    private fun dashboard(data: State<List<HistoryRow>> = mutableStateOf(rows)) {
        compose.activity.runOnUiThread { compose.activity.setContent {
            TokenTheme(false, LanguageChoice.ENGLISH) {
                ChartInspectionHost {
                    Column(Modifier.fillMaxSize().testTag("inspection-scroll").verticalScroll(rememberScrollState())) {
                        val model = DashboardTrendProjection.project(HubHistory(data.value, emptyList()), "7")
                        DesktopDashboardChart(model, Modifier.height(220.dp), { _, _ -> taps++ }, {})
                        Text("Outside chart", Modifier.fillMaxWidth().height(100.dp).testTag("inspection-outside"))
                        Spacer(Modifier.height(1000.dp))
                    }
                }
            }
        } }
    }
    private fun dragToLast() {
        compose.onNodeWithTag("dashboard-chart").performTouchInput {
            down(Offset(width * .22f, height * .8f))
            advanceEventTime(700)
            moveTo(Offset(width * .95f, height * .8f), 100)
            up()
        }
    }
    private fun capture(name: String) {
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        File(directory, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun longPressDragRetainsMissingDayAndOutsideTouchDismissesWithoutTapCallback() {
        dashboard()
        dragToLast()
        compose.onNodeWithTag("dashboard-inspection").assertExists()
        compose.onNodeWithText("2026-10-03").assertExists()
        compose.onNodeWithText("Recorded tokens: —", substring = true).assertExists()
        assertEquals(0, taps)
        capture("daily-inspection-missing.png")
        compose.onNodeWithTag("inspection-outside").performTouchInput { click() }
        compose.onNodeWithTag("dashboard-inspection").assertDoesNotExist()
    }
    @Test fun zeroDayIsRealZeroAndTappingSelectedDateDismisses() {
        dashboard()
        compose.onNodeWithTag("dashboard-chart").performTouchInput { longClick(Offset(width * .53f, height * .8f)) }
        compose.onNodeWithTag("dashboard-inspection").assertExists()
        compose.onNodeWithText("2026-10-02").assertExists()
        compose.onNodeWithText("Recorded tokens: 0", substring = true).assertExists()
        compose.onNodeWithTag("dashboard-chart").performTouchInput { click(Offset(width * .53f, height * .8f)) }
        compose.onNodeWithTag("dashboard-inspection").assertDoesNotExist()
        assertEquals(0, taps)
    }
    @Test fun ordinaryTapStillOpensDetailAndOrdinarySwipeDoesNotInspect() {
        dashboard()
        compose.onNodeWithTag("dashboard-chart").performTouchInput { click(Offset(width * .22f, height * .8f)) }
        assertEquals(1, taps)
        compose.onNodeWithTag("dashboard-chart").performTouchInput { swipeUp() }
        compose.onNodeWithTag("dashboard-inspection").assertDoesNotExist()
        assertEquals(1, taps)
    }
    @Test fun retainedDateUsesUpdatedDataAndDisappearingDateCloses() {
        val data = mutableStateOf(rows)
        dashboard(data)
        dragToLast()
        compose.runOnIdle { data.value = rows.dropLast(1) + rows.last().copy(tokens = 99, cost = "1") }
        compose.onNodeWithTag("dashboard-inspection").assertExists()
        compose.onNodeWithText("Recorded tokens: 99", substring = true).assertExists()
        compose.runOnIdle { data.value = rows.dropLast(1) }
        compose.onNodeWithTag("dashboard-inspection").assertDoesNotExist()
    }
    @Test fun recordBarsAndLineKeepDailyInspectionAndAccessibleAdjacentOperations() {
        compose.activity.runOnUiThread { compose.activity.setContent {
            TokenTheme(true, LanguageChoice.ENGLISH) {
                ChartInspectionHost {
                    Column(Modifier.fillMaxSize().testTag("inspection-scroll").verticalScroll(rememberScrollState())) {
                        DesktopHistoryChart(rows, LocalDate.parse(rows.first().date), LocalDate.parse(rows.last().date)) { taps++ }
                    }
                }
            }
        } }
        compose.onNodeWithTag("desktop-history-chart").performTouchInput { longClick(Offset(width * .5f, height * .8f)) }
        compose.onNodeWithTag("history-inspection").assertExists()
        capture("daily-inspection-history.png")
        compose.onNodeWithText("Line", substring = false).performClick()
        compose.onNodeWithTag("history-inspection").assertDoesNotExist()
        val actions = compose.onNodeWithTag("desktop-history-chart").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
        compose.runOnIdle { actions.last().action() }
        compose.onNodeWithTag("history-inspection").assertExists()
        assertEquals(0, taps)
    }
    @Test fun dataUpdateDuringLockedDragDoesNotCancelOrMoveTheSelectedIdentity() {
        val data = mutableStateOf(rows)
        dashboard(data)
        compose.onNodeWithTag("dashboard-chart").performTouchInput {
            down(Offset(width * .22f, height * .8f))
            advanceEventTime(700)
            moveTo(Offset(width * .22f + 1, height * .8f))
        }
        compose.onNodeWithText("2026-10-01").assertExists()
        compose.runOnIdle { data.value = listOf(rows.first().copy(tokens = 123)) + rows.drop(1) }
        compose.onNodeWithText("Recorded tokens: 123", substring = true).assertExists()
        compose.onNodeWithTag("dashboard-chart").performTouchInput {
            moveTo(Offset(width * .95f, height * .8f))
            moveTo(Offset(width * .22f, height * .8f))
            up()
        }
        compose.onNodeWithText("2026-10-01").assertExists()
        assertEquals(0, taps)
    }
    @Test fun singlePointClampsAndEmptyDataDoesNotInventSelection() {
        val data = mutableStateOf(rows.take(1))
        dashboard(data)
        compose.onNodeWithTag("dashboard-chart").performTouchInput {
            down(Offset(width * .5f, height * .8f))
            advanceEventTime(700)
            moveTo(Offset(-10f, height * .8f))
            moveTo(Offset(width + 10f, height * .8f))
            up()
        }
        compose.onNodeWithText("2026-10-01").assertExists()
        compose.runOnIdle { data.value = emptyList() }
        compose.onNodeWithTag("dashboard-inspection").assertDoesNotExist()
        compose.onNodeWithTag("dashboard-chart").performTouchInput { longClick(center) }
        compose.onNodeWithTag("dashboard-inspection").assertDoesNotExist()
        assertEquals(0, taps)
    }
    @Test fun recordHorizontalSwipeKeepsExistingScrollAndDoesNotReserveLongPress() {
        val many = List(90) { index -> HistoryRow(LocalDate.of(2026, 7, 1).plusDays(index.toLong()).toString(), index.toLong(), "0") }
        compose.activity.runOnUiThread { compose.activity.setContent {
            TokenTheme(false, LanguageChoice.ENGLISH) {
                ChartInspectionHost {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        DesktopHistoryChart(many, LocalDate.parse(many.first().date), LocalDate.parse(many.last().date)) { taps++ }
                    }
                }
            }
        } }
        val viewport = compose.onNodeWithTag("history-chart-viewport")
        val before = viewport.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange].value()
        viewport.performTouchInput { swipeRight() }
        val after = viewport.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange].value()
        assertTrue("Normal swipe moves the existing horizontal scroll", after < before)
        compose.onNodeWithTag("history-inspection").assertDoesNotExist()
        assertEquals(0, taps)
    }
    private class NetworkActions : HubUiActions {
        var reads = 0
        override fun connect(address: String, credential: String, alternate: String) { reads++ }
        override fun refreshSaved() { reads++ }
        override fun loadHistory() { reads++ }
        override fun switchEndpoint() { reads++ }
        override fun hasAlternate() = false
        override fun endpointType() = "https"
        override fun loadModelNames() { reads++ }
        override fun loadSubscriptions() { reads++ }
        override fun forget() {}
        override fun renameDevice(id: String, input: String) { reads++ }
    }
    @Test fun completeHubScreenDoesNotRequestNetworkForLongPressOrEachDraggedDate() {
        val clock = DesktopAnimationClock { compose.mainClock.currentTime * 1_000_000L }
        val history = HubHistory(rows, emptyList())
        val actions = NetworkActions()
        val snapshot = HubParser.parse("""{ "periods": { "today": { "totalTokens": 10, "costUsd": 0.1 } } }""").copy(historyPreview = history)
        compose.activity.runOnUiThread { compose.activity.setContent {
            TokenTheme(false, LanguageChoice.ENGLISH) {
                CompositionLocalProvider(LocalDesktopAnimationClock provides clock) {
                HubScreen(HubState(snapshot = snapshot, history = history), actions,
                    preferences = DisplayPreferences(language = LanguageChoice.ENGLISH, theme = ThemeChoice.LIGHT))
                }
            }
        } }
        compose.onNodeWithTag("hub-view-selector").performClick()
        compose.onNodeWithTag("view-trends").performScrollTo().performClick()
        compose.onNodeWithTag("trends-main-open-history").performScrollTo().performClick()
        compose.onNodeWithTag("dashboard-tab-trends").performClick()
        compose.onNodeWithTag("dashboard-chart").performScrollTo()
        val before = actions.reads
        dragToLast()
        compose.onNodeWithTag("dashboard-inspection").assertExists()
        compose.onNodeWithTag("dashboard-chart").performTouchInput { longClick(Offset(width * .22f, height * .8f)) }
        assertEquals("Point inspection never requests a refresh, history or another Hub read", before, actions.reads)
    }

}
