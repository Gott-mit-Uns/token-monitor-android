package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Raster assertions: semantics alone cannot detect a stale clipped ancestor display list. */
class SettingsLiveDrawingScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val ink = Color(0xFF19C77A)

    private fun setup(open: State<Boolean>, animated: Boolean = false) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                TokenTheme(true, LanguageChoice.ENGLISH) {
                    CompositionLocalProvider(
                        LocalDesktopMotionScale provides if (animated) 1f else 0f,
                        LocalDesktopAnimationClock provides DesktopAnimationClock {
                            compose.mainClock.currentTime * 1_000_000L
                        }) {
                        Box(Modifier.fillMaxSize().background(Color.Black).testTag("settings-raster-root")) {
                            DesktopSettingsPane(open.value) {
                                LazyColumn(Modifier.fillMaxSize().testTag("settings-raster-viewport"),
                                    state = rememberLazyListState()) {
                                    item {
                                        // A long settings accordion is one existing Lazy item; it must
                                        // keep drawing newly exposed rows after the ancestor is cached.
                                        DesktopAccordion(true, "settings-raster-long-group") {
                                            repeat(60) { index ->
                                                Box(Modifier.fillMaxWidth().height(64.dp).background(ink)
                                                    .testTag("settings-raster-row-$index")) {
                                                    Text("Public setting $index", color = Color.Black)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertRowPainted(index: Int) {
        val row = compose.onNodeWithTag("settings-raster-row-$index").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val root = compose.onNodeWithTag("settings-raster-root").fetchSemanticsNode().boundsInRoot
        val pixels = compose.onNodeWithTag("settings-raster-root").captureToImage().toPixelMap()
        val x = (row.center.x - root.left).toInt().coerceIn(0, pixels.width - 1)
        val y = (row.center.y - root.top).toInt().coerceIn(0, pixels.height - 1)
        val actual = pixels[x, y]
        assertTrue("Visible setting $index must be painted, not merely present in semantics: $actual",
            actual.green > .6f && actual.red < .3f && actual.blue > .25f)
    }

    @Test fun longAccordionPaintsNewlyExposedRowsAfterRepeatedScrolls() {
        setup(mutableStateOf(true))
        for (index in listOf(0, 20, 45, 8)) {
            compose.onNodeWithTag("settings-raster-viewport")
                .performScrollToNode(hasTestTag("settings-raster-row-$index"))
            compose.waitForIdle()
            assertRowPainted(index)
            saveVisualEvidence(compose.activity, compose.onRoot(), "settings-live-scroll-$index.png")
        }
    }

    @Test fun openingSettingsPaintsTheExpandedViewportWithoutAnotherStateChange() {
        val open = mutableStateOf(false)
        setup(open, animated = true)
        compose.runOnIdle { open.value = true }
        compose.waitForIdle()
        assertRowPainted(0)
        assertRowPainted(4)
        saveVisualEvidence(compose.activity, compose.onRoot(), "settings-live-open.png")
    }
}
