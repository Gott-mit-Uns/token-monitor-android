package dev.bing.tokenmonitor

import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class RecordCalendarScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val today = LocalDate.parse("2026-10-16")
    private val history = HubHistory(listOf(HistoryRow("2026-10-16", 123456789, "12"),
        HistoryRow("2026-10-15", 0, "0"), HistoryRow("2026-10-13", null, null),
        HistoryRow("2026-09-30", 77, "1"), HistoryRow("2025-12-31", 8, "0")),
        listOf(HistoryRow("2026-10", 999999999, "99"), HistoryRow("2025-12", 998, "1")))

    private fun show(history: HubHistory?, loading: Boolean = false, error: Int? = null, clock: Clock? = null) {
        val dark = InstrumentationRegistry.getArguments().getString("visualDark") == "true"
        compose.activity.runOnUiThread { compose.activity.setContent {
            TokenTheme(dark = dark, language = LanguageChoice.ENGLISH) { Surface {
                Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(14.dp)) {
                    HistoryTrend(history, loading, error, clock?.let(LocalDate::now) ?: today)
                }
            } }
        } }
    }

    @Test fun dailySelectionRangeMonthNavigationAndAuthoritativeMonthlyYearGrid() {
        show(history)
        compose.onNodeWithTag("history-record-2026-10-16")
            .assertContentDescriptionEquals("2026-10-16, 123,456,789 Token, Today")
        compose.onNodeWithTag("history-record-2026-10-17").assertIsNotEnabled()
        compose.onNodeWithTag("history-record-2026-10-15").performScrollTo().performClick()
        compose.onNodeWithTag("records-selected-tokens").performScrollTo().assertTextEquals("Token 0")
        compose.onNodeWithTag("history-record-2026-10-14").performScrollTo().performClick()
        compose.onNodeWithText("No record supplied by Hub; this is not zero usage.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("history-record-2026-10-13").performScrollTo().performClick()
        compose.onNodeWithTag("records-selected-tokens").performScrollTo().assertTextEquals("Token Not provided")
        compose.onNodeWithText("7 days").assertDoesNotExist()
        compose.onNodeWithText("All records").assertDoesNotExist()
        compose.onNodeWithTag("history-record-2026-10-09").assertIsEnabled()
        compose.onNodeWithTag("records-calendar-previous").performScrollTo().performClick()
        compose.onNodeWithTag("history-record-2026-09-30").performScrollTo().performClick()
        compose.onNodeWithTag("records-selected-tokens").performScrollTo().assertTextEquals("Token 77")
        compose.onNodeWithTag("records-daily").performScrollTo()
        saveVisualEvidence(compose.activity, compose.onRoot(), "compact-records-daily.png")
        compose.onNodeWithTag("records-monthly").performScrollTo().performClick()
        compose.onNodeWithTag("history-record-2026-10").performScrollTo().performClick()
        compose.onNodeWithTag("records-selected-tokens").performScrollTo().assertTextEquals("Token 999,999,999")
        compose.onNodeWithTag("records-calendar-previous").performScrollTo().performClick()
        compose.onNodeWithTag("records-calendar-title").assertTextEquals("2025")
        compose.onNodeWithTag("history-record-2025-12").performScrollTo().performClick()
        compose.onNodeWithTag("records-selected-tokens").performScrollTo().assertTextEquals("Token 998")
        compose.onNodeWithTag("records-monthly").performScrollTo()
        saveVisualEvidence(compose.activity, compose.onRoot(), "compact-records-monthly.png")
        repeat(2) {
            compose.onNodeWithTag("records-calendar-next").performScrollTo().performClick()
            compose.onNodeWithTag("records-calendar-previous").performScrollTo().performClick()
        }
        compose.onNodeWithTag("records-selected-date").performScrollTo().assertTextEquals("2025-12")
        compose.onNodeWithTag("records-daily").performScrollTo().performClick()
        // The daily viewport was retained at Sep 2026. Cross its year boundary too.
        repeat(9) { compose.onNodeWithTag("records-calendar-previous").performScrollTo().performClick() }
        compose.onNodeWithTag("records-calendar-title").assertTextEquals("December 2025")
        compose.onNodeWithTag("history-record-2025-12-31").performScrollTo().performClick()
        compose.onNodeWithTag("records-selected-tokens").performScrollTo().assertTextEquals("Token 8")
        compose.onNodeWithTag("records-calendar-next").performScrollTo().performClick()
        compose.onNodeWithTag("records-calendar-title").assertTextEquals("January 2026")
    }

    @Test fun failedLoadRetainsKnownDataAndDoesNotInventZeroForMissingCells() {
        show(history, error = R.string.record_read_failed)
        compose.onNodeWithTag("records-error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("history-record-2026-10-16").performScrollTo().performClick()
        compose.onNodeWithTag("records-selected-tokens").performScrollTo().assertTextEquals("Token 123,456,789")
        compose.onNodeWithTag("history-record-2026-10-14").performScrollTo().performClick()
        compose.onNodeWithTag("records-selected-detail").onChildren().filterToOne(hasText("History could not be loaded. Existing records are retained; refresh to retry.")).assertExists()
    }

    @Test fun unloadedLoadingFailedAndSuccessfullyEmptyAreDistinctVisibleStates() {
        val source = mutableStateOf<HubHistory?>(null)
        val loading = mutableStateOf(false)
        val error = mutableStateOf<Int?>(null)
        compose.activity.runOnUiThread { compose.activity.setContent {
            TokenTheme(language = LanguageChoice.ENGLISH) { Surface {
                Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(14.dp)) {
                    HistoryTrend(source.value, loading.value, error.value, today)
                }
            } }
        } }
        compose.onNodeWithTag("history-record-2026-10-14").performScrollTo().performClick()
        compose.onNodeWithText("History has not been loaded.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { loading.value = true }
        compose.onNodeWithTag("records-loading").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("records-selected-tokens").assertDoesNotExist()
        compose.runOnIdle { loading.value = false; error.value = R.string.error_read }
        compose.onNodeWithTag("records-error").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { error.value = null; source.value = HubHistory(emptyList(), emptyList()) }
        compose.onNodeWithText("No record supplied by Hub; this is not zero usage.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("records-selected-tokens").assertDoesNotExist()
    }

    @Test fun restoredYearMonthSelectionUsesTheNewPayloadForDetails() {
        compose.activityRule.scenario.onActivity { activity ->
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            repeat(content.childCount) { (content.getChildAt(it) as? ComposeView)?.disposeComposition() }
            content.removeAllViews()
        }
        val source = mutableStateOf(history)
        val restore = StateRestorationTester(compose)
        restore.setContent { TokenTheme(language = LanguageChoice.ENGLISH) { Surface {
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(14.dp)) {
                HistoryTrend(source.value, today = today)
            }
        } } }
        compose.onNodeWithText("All records").assertDoesNotExist()
        compose.onNodeWithTag("records-calendar-previous").performScrollTo().performClick()
        compose.onNodeWithTag("history-record-2026-09-30").performScrollTo().performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("All records").assertDoesNotExist()
        compose.onNodeWithTag("records-calendar-title").performScrollTo().assertTextEquals("September 2026")
        compose.onNodeWithTag("records-selected-date").performScrollTo().assertTextEquals("2026-09-30")
        compose.onNodeWithTag("records-selected-tokens").assertTextEquals("Token 77")
        compose.onNodeWithTag("records-monthly").performScrollTo().performClick()
        compose.onNodeWithTag("records-calendar-previous").performScrollTo().performClick()
        compose.onNodeWithTag("history-record-2025-12").performScrollTo().performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("records-calendar-title").performScrollTo().assertTextEquals("2025")
        compose.onNodeWithTag("records-selected-tokens").performScrollTo().assertTextEquals("Token 998")
        compose.runOnIdle { source.value = history.copy(monthly = listOf(HistoryRow("2025-12", 13, "0"))) }
        compose.onNodeWithTag("records-selected-tokens").assertTextEquals("Token 13")
    }

    private fun checkHubDateAheadOfPhone(instant: String, sourceDate: String, monthTitle: String) {
        val clock = Clock.fixed(Instant.parse(instant), ZoneId.of("America/Los_Angeles"))
        val hubDate = clock.instant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()
        org.junit.Assert.assertEquals(sourceDate, hubDate.toString())
        org.junit.Assert.assertTrue(hubDate > LocalDate.now(clock))
        val month = sourceDate.take(7)
        val source = HubHistory(listOf(HistoryRow(sourceDate, 81, "1")), listOf(HistoryRow(month, 999, "2")))
        show(source, clock = clock)
        compose.onNodeWithTag("history-record-$sourceDate").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("records-selected-tokens").performScrollTo().assertTextEquals("Token 81")
        compose.onNodeWithText("Known usage: 81 tokens").assertDoesNotExist()
        compose.onNodeWithTag("records-calendar-title").performScrollTo().assertTextEquals(monthTitle)
        compose.onNodeWithTag("records-calendar-previous").performClick()
        compose.onNodeWithTag("records-calendar-next").assertIsEnabled().performClick()
        compose.onNodeWithTag("records-calendar-title").assertTextEquals(monthTitle)
        compose.onNodeWithTag("records-calendar-next").assertIsNotEnabled()
        compose.onNodeWithTag("records-monthly").performScrollTo().performClick()
        compose.onNodeWithTag("history-record-$month").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("records-selected-tokens").performScrollTo().assertTextEquals("Token 999")
        compose.onNodeWithTag("records-calendar-previous").performScrollTo().performClick()
        compose.onNodeWithTag("records-calendar-next").assertIsEnabled().performClick()
        compose.onNodeWithTag("history-record-$month").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("records-calendar-next").assertIsNotEnabled()
    }

    @Test fun hubNextMonthRowRemainsSelectableAcrossPhoneTimezoneAndCalendarNavigation() {
        checkHubDateAheadOfPhone("2026-11-01T00:30:00Z", "2026-11-01", "November 2026")
    }

    @Test fun hubNextYearRowRemainsSelectableAcrossPhoneTimezoneAndCalendarNavigation() {
        checkHubDateAheadOfPhone("2027-01-01T00:30:00Z", "2027-01-01", "January 2027")
    }

    @Test fun sourceOmittedDimensionsDifferFromExplicitEmptyArraysInBothCalendars() {
        val source = mutableStateOf(HistoryParser.parse("""{"daily":[{"date":"2026-10-16","tokens":5}]}"""))
        compose.activity.runOnUiThread { compose.activity.setContent {
            TokenTheme(language = LanguageChoice.ENGLISH) { Surface {
                Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(14.dp)) {
                    HistoryTrend(source.value, today = today)
                }
            } }
        } }
        val unavailable = "Hub has not provided history for this dimension."
        val absent = "No record supplied by Hub; this is not zero usage."
        compose.onNodeWithTag("records-monthly").performClick()
        compose.onNodeWithTag("history-record-2026-10").performScrollTo().performClick()
        compose.onNodeWithText(unavailable).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("records-selected-tokens").assertDoesNotExist()
        compose.runOnIdle { source.value = HistoryParser.parse("""{"daily":[{"date":"2026-10-16","tokens":5}],"monthly":[]}""") }
        compose.onNodeWithText(absent).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { source.value = HistoryParser.parse("""{"daily":null,"monthly":[{"month":"2026-10","tokens":9}]}""") }
        compose.onNodeWithTag("records-daily").performScrollTo().performClick()
        compose.onNodeWithTag("history-record-2026-10-16").performScrollTo().performClick()
        compose.onNodeWithText(unavailable).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("records-selected-tokens").assertDoesNotExist()
        compose.runOnIdle { source.value = HistoryParser.parse("""{"daily":[],"monthly":[{"month":"2026-10","tokens":9}]}""") }
        compose.onNodeWithText(absent).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("records-monthly").performScrollTo().performClick()
        compose.onNodeWithTag("history-record-2026-10").performScrollTo().performClick()
        compose.onNodeWithTag("records-selected-tokens").performScrollTo().assertTextEquals("Token 9")
    }
}
