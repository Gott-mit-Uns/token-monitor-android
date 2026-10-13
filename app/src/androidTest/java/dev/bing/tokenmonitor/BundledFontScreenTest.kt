package dev.bing.tokenmonitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BundledFontScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun lightMixedNarrowLargestTextRemainsComplete() = scenario(false)
    @Test fun darkMixedNarrowLargestTextRemainsComplete() = scenario(true)
    private fun scenario(dark: Boolean) {
        var refresh = 0; var settings = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                TokenTheme(dark, LanguageChoice.CHINESE, AppearancePreferences(textSize="largest")) {
                    Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp)) {
                        Text("办公 Wi-Fi ↔ 蜂窝：中文与 English 混排，动态设备名龘𠮷，粗体总量 123,456.78", Modifier.testTag("font-body"), style=quotaTextStyle(14,true))
                        Text("−12,345.67 / $123.45", Modifier.testTag("font-data"), style=quotaDataTextStyle(14))
                        Row {
                            TextButton({refresh++}, Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).testTag("font-refresh")) { Text("刷新") }
                            TextButton({settings++}, Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).testTag("font-settings")) { Text("设置") }
                        }
                    }
                }
            }
        }
        for (tag in listOf("font-body", "font-data")) {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            assertTrue(results.isNotEmpty())
            assertTrue(results.all { !it.didOverflowWidth && !it.didOverflowHeight })
            assertEquals(if(tag=="font-data") BundledFonts.data else BundledFonts.ui, results.first().layoutInput.style.fontFamily)
        }
        compose.onNodeWithTag("font-refresh").performClick(); compose.onNodeWithTag("font-settings").performClick()
        compose.runOnIdle { assertEquals(1, refresh); assertEquals(1, settings) }
        saveVisualEvidence(compose.activity, compose.onRoot(), "bundled-font-${if(dark) "dark" else "light"}.png")
    }
}
