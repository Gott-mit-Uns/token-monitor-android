package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class HistoryUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun syntheticHistoryHasReadableLabelsAndOpensExactDetail() {
        val dark = InstrumentationRegistry.getArguments().getString("visualDark") == "true"
        val history = HubHistory(listOf(
            HistoryRow("2026-10-09", 123, "0.25"), HistoryRow("2026-10-07", 12, "0.01")
        ), listOf(HistoryRow("2026-10", 135, "0.26")))
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                TokenTheme(dark = dark) {
                    Surface { Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) { HistoryTrend(history) } }
                }
            }
        }
        compose.onNodeWithText("最高 123 Token").assertIsDisplayed()
        compose.onNodeWithText("日期间缺口表示没有记录，不代表零用量。").assertExists()
        val output = File(compose.activity.getExternalFilesDir(null), if (dark) "visual-history-dark.png" else "visual-history.png")
        output.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNode(hasText("2026-10-09") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("已知估算费用：$0.2500").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("每月").performScrollTo().performClick()
        compose.onNodeWithText("135 Token").assertIsDisplayed()
    }
}
