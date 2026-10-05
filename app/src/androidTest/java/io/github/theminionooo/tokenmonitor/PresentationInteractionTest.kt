package io.github.theminionooo.tokenmonitor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.data.HubRepositoryState
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.domain.*
import io.github.theminionooo.tokenmonitor.ui.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class PresentationInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sessionSearchRespectsTitlePrivacyAndCanBeCleared() {
        val session = SessionUsage("search-42", "Private planning title", "codex", "Example", 100, 0.0, listOf("gpt-6"), 1, "", "")
        val snapshot = HubSnapshot(stats = HubStats(periods = mapOf("month" to UsagePeriod(sessions = listOf(session)))))
        var titles by mutableStateOf(true)
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalSessionTitles provides titles, LocalInteractionMotion provides false) {
            DashboardContent(Modifier.fillMaxSize(), HubRepositoryState(snapshot = snapshot), DashboardDestination.Sessions,
                DashboardPeriod.Month, {}, {}, false, ServiceStatusState(), DisplayOptions(), {})
        } } }
        compose.onNode(hasSetTextAction()).performTextInput("planning")
        compose.onNodeWithText("Private planning title").assertIsDisplayed()
        compose.runOnIdle { titles = false }
        compose.onNodeWithText("No sessions match this search.").assertIsDisplayed()
        compose.onNodeWithText("Private planning title").assertDoesNotExist()
        compose.onNodeWithContentDescription("Clear search", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Codex · gpt-6").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("search-42")
        compose.onNodeWithText("Codex · gpt-6").assertIsDisplayed()
    }

    @Test fun segmentedControlsExposeTheirSelectionAtLargeTextSize() {
        compose.setContent { MaterialTheme {
            var selected by remember { mutableStateOf("first") }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f), LocalInteractionMotion provides false) {
                ChoiceGroup(listOf("FIRST" to "first", "SECOND" to "second"), selected, { selected = it })
            }
        } }
        compose.onNodeWithText("FIRST").assertIsSelected()
        compose.onNodeWithText("SECOND").assertIsNotSelected().performClick().assertIsSelected()
        compose.onNodeWithText("FIRST").assertIsNotSelected()
    }

    @Test fun bothTrendChartsDescribeMissingDaysAndPreserveRecordedTotals() {
        val start = LocalDate.of(2026, 10, 1)
        val trend = prepareTrends(listOf(HistoryPoint(start.toString(), 100, 0.0), HistoryPoint(start.plusDays(2).toString(), 200, 0.0)), false, start, start.plusDays(6))
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalInteractionMotion provides false) {
            Column {
                StackedTrendChart(trend, 178.dp)
                CandleTrendChart(trend, 178.dp)
            }
        } } }
        compose.onNodeWithContentDescription(trendChartDescription(trend, false)).assertIsDisplayed()
        compose.onNodeWithContentDescription(trendChartDescription(trend, true)).assertIsDisplayed()
    }
}
