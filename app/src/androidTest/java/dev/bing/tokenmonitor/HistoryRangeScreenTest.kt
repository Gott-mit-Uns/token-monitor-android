package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class HistoryRangeScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun rangesExposeCoverageAndKnownTotals() {
        val dark = InstrumentationRegistry.getArguments().getString("visualDark") == "true"
        val history = HistoryParser.parse("""{"daily":[{"date":"2026-10-09","tokens":100,"cost":"1.25","unpricedTokens":10},{"date":"2026-10-08","tokens":0,"cost":"0"},{"date":"2026-09-30","tokens":200,"cost":"2.50"}],"monthly":[{"month":"2026-10","tokens":100,"cost":"1.25"}]}""")
        compose.activity.runOnUiThread { compose.activity.setContent {
            TokenTheme(dark = dark) { Surface { Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("history-range-scroll").verticalScroll(rememberScrollState()).padding(24.dp)) { HistoryTrend(history, today = java.time.LocalDate.parse("2026-10-16")) } } }
        } }
        compose.onNodeWithText("7天").performClick()
        compose.onNodeWithText("2026-10-03 至 2026-10-09").assertIsDisplayed()
        compose.onNodeWithText("已记录 2 / 7 天；Token 可用 2 天").assertExists()
        compose.onNodeWithText("已知用量 100 Token").assertIsDisplayed()
        compose.onNodeWithText("其中 10 Token 尚未定价").assertExists()
        saveVisualEvidence(compose.activity, compose.onRoot(), if (dark) "visual-history-range-dark.png" else "visual-history-range.png")
        compose.onNodeWithText("全部记录").performClick()
        compose.onNodeWithText("已知用量 300 Token").assertIsDisplayed()
        compose.onNodeWithText("已记录 3 / 10 天；Token 可用 3 天").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("history-record-2026-10-08").performScrollTo().performClick()
        compose.onNodeWithText("Token 0").performScrollTo().assertIsDisplayed()
        saveVisualEvidence(compose.activity, compose.onRoot(), "visual-calendar-detail.png")
        compose.onNodeWithTag("history-record-2026-10-07").performScrollTo().performClick()
        compose.onNodeWithText("Hub 未提供记录，不代表零用量。").performScrollTo().assertIsDisplayed()
        saveVisualEvidence(compose.activity, compose.onRoot(), if (dark) "visual-history-activity-dark.png" else "visual-history-activity.png")
    }
}
