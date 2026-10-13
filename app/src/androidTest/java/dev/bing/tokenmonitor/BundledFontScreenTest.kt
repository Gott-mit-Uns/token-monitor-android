package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BundledFontScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun lightMixedNarrowLargestTextRemainsComplete() = scenario(false)
    @Test fun darkMixedNarrowLargestTextRemainsComplete() = scenario(true)
    private fun scenario(dark: Boolean) {
        var refresh = 0; var settings = 0
        var observedFontScale = 0f
        val actualSystemFont = InstrumentationRegistry.getArguments().getString("actualSystemFont") == "true"
        compose.activity.runOnUiThread { compose.activity.setContent {
            val scene: @Composable () -> Unit = {
                observedFontScale = LocalDensity.current.fontScale
                TokenTheme(dark, LanguageChoice.CHINESE, AppearancePreferences(textSize="largest")) {
                    val width = if (actualSystemFont) Modifier.fillMaxWidth() else Modifier.width(320.dp)
                    // Match text and semantics to the allocated window width; unconstrained
                    // intrinsic Text width differs from Compose semantics reconstruction.
                    Column(width.fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp)) {
                        Text("办公 Wi-Fi ↔ 蜂窝：中文与 English 混排，动态设备名龘𠮷，粗体总量 123,456.78", Modifier.fillMaxWidth().testTag("font-body"), style=quotaTextStyle(14,true))
                        Text("−12,345.67 / $123.45", Modifier.fillMaxWidth().testTag("font-data"), style=quotaDataTextStyle(14))
                        Row {
                            TextButton({refresh++}, Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).testTag("font-refresh")) { Text("刷新") }
                            TextButton({settings++}, Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).testTag("font-settings")) { Text("设置") }
                        }
                    }
                }
            }
            if (actualSystemFont) scene() else {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) { scene() }
            }
        } }
        saveVisualEvidence(compose.activity, compose.onRoot(), "bundled-font-before-assert-${if(dark) "dark" else "light"}.png")
        for (tag in listOf("font-body", "font-data")) {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            assertTrue(results.isNotEmpty())
            assertTrue("$tag actual=$actualSystemFont fontScale=$observedFontScale layouts=${results.map { "size=${it.size} paragraphWidth=${it.multiParagraph.width} paragraphHeight=${it.multiParagraph.height} constraints=${it.layoutInput.constraints} width=${it.didOverflowWidth} height=${it.didOverflowHeight} lines=${it.lineCount} lastLineBottom=${it.getLineBottom(it.lineCount-1)} text=${it.layoutInput.text}" }}", results.all { !it.didOverflowWidth && !it.didOverflowHeight })
            assertEquals(if(tag=="font-data") BundledFonts.data else BundledFonts.ui, results.first().layoutInput.style.fontFamily)
        }
        compose.onNodeWithTag("font-refresh").performClick(); compose.onNodeWithTag("font-settings").performClick()
        compose.runOnIdle {
            assertEquals(1, refresh); assertEquals(1, settings)
            if (actualSystemFont) assertEquals("Compose uses the real system font scale",
                compose.activity.resources.configuration.fontScale, observedFontScale, .001f)
            val config = compose.activity.resources.configuration
            println("PUBLIC_FONT_LAYOUT actualSystem=$actualSystemFont screenWidthDp=${config.screenWidthDp} systemFontScale=${config.fontScale} composeFontScale=$observedFontScale appTextSize=largest dark=$dark")
        }
        saveVisualEvidence(compose.activity, compose.onRoot(), "bundled-font-${if(dark) "dark" else "light"}.png")
    }
}
