package io.github.theminionooo.tokenmonitor

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.storage.*
import io.github.theminionooo.tokenmonitor.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class DesktopAlignmentTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @org.junit.Before fun language() { io.github.theminionooo.tokenmonitor.localization.LanguagePreferences.set(context, "zh-CN") }
    @org.junit.After fun cleanupLanguage() { io.github.theminionooo.tokenmonitor.localization.LanguagePreferences.set(context, "system") }
    @Test fun presentationPreferencesPersistAndRejectInvalidRates() {
        check(context.packageName.endsWith(".preview"))
        val prefs = context.getSharedPreferences("desktop_presentation", Context.MODE_PRIVATE)
        val previous = prefs.getString("options", null)
        try {
            val store = DesktopPreferences(context)
            val options = DesktopOptions("CNY", 7.0, mapOf("m1" to "merged", "m2" to "merged"), setOf("tool"), setOf("model"), currencyRates = mapOf("CNY" to 7.0))
            assertTrue(store.save(options))
            assertEquals(options, DesktopPreferences(context).options.value)
            assertFalse(store.save(options.copy(usdRate = Double.NaN)))
            assertFalse(store.save(options.copy(usdRate = 0.0)))
            assertEquals(options, store.options.value)
        } finally { prefs.edit().putString("options", previous).commit(); DesktopPreferences(context) }
    }
    @Test fun lightSettings() = settings(InterfaceTheme.Porcelain, "light", 1f)
    @Test fun darkSettings() = settings(InterfaceTheme.Obsidian, "dark", 1f)
    @Test fun largeSettings() = settings(InterfaceTheme.Obsidian, "large", 2f)
    private fun settings(theme: InterfaceTheme, name: String, scale: Float) {
        var options by mutableStateOf(DesktopOptions())
        val palette = Palette.from(theme)
        compose.setContent {
            CompositionLocalProvider(LocalPalette provides palette, LocalDensity provides Density(LocalDensity.current.density, scale)) {
                MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
                    LazyColumn(Modifier.fillMaxSize().background(palette.shell), contentPadding = PaddingValues(14.dp)) {
                        item { DesktopSettingsPanel(io.github.theminionooo.tokenmonitor.domain.HubSnapshot(), options) { options = it; true } }
                    }
                }
            }
        }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            val directory = File(context.getExternalFilesDir(null), "desktop-alignment").apply { mkdirs() }
            File(directory, "fonts-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("CNY", useUnmergedTree = true).performClick()
        compose.onNodeWithText("1 USD = 多少 CNY").performTextReplacement("7")
        compose.onNodeWithText("保存币种与汇率").performScrollTo().performClick()
        assertEquals("CNY", options.currency)
        assertEquals(7.0, options.usdRate, 0.0)
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            val directory = File(context.getExternalFilesDir(null), "desktop-alignment").apply { mkdirs() }
            File(directory, "settings-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("模型别名与合并 · 0 项").performScrollTo().performClick()
        compose.onNodeWithText("来源模型 ID，每行一个").performScrollTo().performTextReplacement("m1\nm2")
        compose.onNodeWithText("目标模型 ID／显示名称").performScrollTo().performTextReplacement("merged")
        compose.onNodeWithText("保存模型别名", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(mapOf("m1" to "merged", "m2" to "merged"), options.modelAliases)
    }
    @Test fun completeNumbersSurviveLargeTypography() {
        compose.setContent {
            CompositionLocalProvider(LocalPalette provides Palette.from(InterfaceTheme.Obsidian), LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                MaterialTheme { Box(Modifier.width(360.dp)) { AlignedUsageIdentity("9,223,372,036,854,775,807", "CN¥123,456,789.00") { androidx.compose.material3.Text("非常长的模型名称") } } }
            }
        }
        compose.onNodeWithText("9,223,372,036,854,775,807").assertIsDisplayed()
        compose.onNodeWithText("CN¥123,456,789.00").assertIsDisplayed()
    }

    @Test fun chartTapKeepsMissingDatesUnknown() {
        val trend = prepareTrends(listOf(
            io.github.theminionooo.tokenmonitor.domain.HistoryPoint("2026-10-01", 100, 1.0),
            io.github.theminionooo.tokenmonitor.domain.HistoryPoint("2026-10-03", 200, 2.0),
        ), false, java.time.LocalDate.parse("2026-10-01"), java.time.LocalDate.parse("2026-10-03"))
        compose.setContent { CompositionLocalProvider(LocalPalette provides Palette.from(InterfaceTheme.Obsidian)) {
            MaterialTheme { Column { StackedTrendChart(trend, 160.dp) } }
        } }
        compose.onNode(hasContentDescription("Daily usage bar chart.", substring = true)).performTouchInput { click(center) }
        compose.onNodeWithText("该日期没有记录，不能视为零用量").assertIsDisplayed()
    }
    @Test fun toolDetailsDoNotTriggerModelNavigation() {
        var selected = false
        compose.setContent { CompositionLocalProvider(LocalPalette provides Palette.from(InterfaceTheme.Obsidian), LocalInteractionMotion provides false) {
            MaterialTheme { DesktopUsageRow("codex", 100, "$1.00", 1f, 30, 25, 5, false, { selected = true }, 20) }
        } }
        compose.onNodeWithText("Token 明细").performClick()
        assertFalse(selected)
        compose.onNodeWithText("其中缓存写入").assertIsDisplayed()
        compose.onNodeWithText("Codex").performClick()
        assertTrue(selected)
    }
    @Test fun longPressHandleMovesOnlyAfterDrop() {
        var shift = 0
        compose.setContent { MaterialTheme { DragOrderHandle("测试", onMove = { shift = it }) } }
        compose.onNodeWithContentDescription("拖动排序：测试").performTouchInput {
            down(center)
            advanceEventTime(650)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 150f), delayMillis = 100)
            up()
        }
        compose.waitForIdle()
        assertTrue(shift > 0)
    }
    @Test fun fontChoicesAreChineseAndIndependent() {
        var options by mutableStateOf(DesktopOptions())
        compose.setContent { MaterialTheme { LazyColumn { item { DesktopSettingsPanel(null, options) { options = it; true } } } } }
        compose.onAllNodesWithText("系统", useUnmergedTree = true)[0].performClick()
        assertEquals("system", options.interfaceFont)
        assertEquals("system", options.displayFont)
        compose.onAllNodesWithText("等宽", useUnmergedTree = true)[1].performClick()
        assertEquals("system", options.interfaceFont)
        assertEquals("mono", options.displayFont)
    }
    @Test fun oldSettingsMigrateAndNewPreferencesPersist() {
        check(context.packageName.endsWith(".preview"))
        val prefs = context.getSharedPreferences("desktop_presentation", Context.MODE_PRIVATE)
        val previous = prefs.getString("options", null)
        try {
            prefs.edit().putString("options", """{"currency":"CNY","rate":7.0,"aliases":{"a":"b"},"tools":["tool"],"models":[]}""").commit()
            val store = DesktopPreferences(context)
            val old = store.options.value
            assertEquals("mono", old.interfaceFont)
            assertEquals("system", old.displayFont)
            assertEquals("off", old.modelGrouping)
            assertEquals(mapOf("a" to "b"), old.modelAliases)
            val changed = old.copy(interfaceFont = "system", displayFont = "follow", modelGrouping = "duplicates", contextRemaining = true, pinnedTools = setOf("codex"), toolOrder = listOf("codex", "hermes"))
            assertTrue(store.save(changed))
            val reopened = DesktopPreferences(context).options.value
            assertEquals(changed.copy(currencyRates = mapOf("CNY" to 7.0)), reopened)
            assertTrue(store.save(reopened.copy(currency = "HKD", usdRate = 7.8)))
            assertEquals(mapOf("CNY" to 7.0, "HKD" to 7.8), DesktopPreferences(context).options.value.currencyRates)
        } finally { prefs.edit().putString("options", previous).commit(); DesktopPreferences(context) }
    }
}
