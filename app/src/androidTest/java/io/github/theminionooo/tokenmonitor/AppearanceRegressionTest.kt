package io.github.theminionooo.tokenmonitor

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.storage.*
import io.github.theminionooo.tokenmonitor.domain.UsagePeriod
import io.github.theminionooo.tokenmonitor.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class AppearanceRegressionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun existingPreferencesUpgradeAndPersistIndependently() {
        val real = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "cf4-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(real) {
            override fun getSharedPreferences(n: String, mode: Int): SharedPreferences = real.getSharedPreferences(name, mode)
        }
        val legacy = context.getSharedPreferences("ignored", 0)
        try {
            legacy.edit().putString("text_scale", "Large").putBoolean("compact_token_total", true).putBoolean("colorful_tool_marks", true).putString("theme_code", InterfaceTheme.Porcelain.code).commit()
            var store = DisplayPreferences(context)
            assertEquals(IconScale.Comfortable, store.options.value.iconScale)
            assertTrue(store.options.value.homeChineseUnits)
            assertEquals(TextScale.Large, store.options.value.textScale)
            for (text in TextScale.entries) for (icon in IconScale.entries) {
                store.setTextScale(text); store.setIconScale(icon)
                assertEquals(text, store.options.value.textScale)
                assertEquals(icon, store.options.value.iconScale)
            }
            store.setHomeChineseUnits(false)
            store = DisplayPreferences(context)
            assertFalse(store.options.value.homeChineseUnits)
            assertEquals(IconScale.Large, store.options.value.iconScale)
            assertEquals(TextScale.Large, store.options.value.textScale)
            assertTrue(store.options.value.compactTokenTotal)
            assertTrue(store.options.value.colorfulToolMarks)
            assertEquals(InterfaceTheme.Porcelain.code, store.options.value.themeCode)
        } finally { real.deleteSharedPreferences(name) }
    }
    @Test fun nineTextIconPairsUseTheRequestedIconBounds() {
        var text by mutableStateOf(TextScale.Comfortable)
        var icon by mutableStateOf(IconScale.Comfortable)
        val palette = Palette.from(InterfaceTheme.Obsidian)
        compose.setContent {
            MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(text.step)) {
                CompositionLocalProvider(LocalPalette provides palette, LocalContentIconSize provides icon.dp.dp) {
                    Column(Modifier.width(360.dp)) {
                        Box(Modifier.testTag("brand-icon")) { UpstreamToolMark("dsh", Blue, LocalContentIconSize.current) }
                        Box(Modifier.testTag("device-icon")) { DevicePlatformMark("linux", Ink, LocalContentIconSize.current) }
                    }
                }
            }
        }
        for (nextText in TextScale.entries) for (nextIcon in IconScale.entries) {
            compose.runOnIdle { text = nextText; icon = nextIcon }
            compose.onNodeWithTag("brand-icon").assertWidthIsEqualTo(nextIcon.dp.dp).assertHeightIsEqualTo(nextIcon.dp.dp)
            compose.onNodeWithTag("device-icon").assertWidthIsEqualTo(nextIcon.dp.dp).assertHeightIsEqualTo(nextIcon.dp.dp)
        }
    }
    @Test fun secondaryTotalsStayExactWhileHomeUnitsChange() {
        var chinese by mutableStateOf(true)
        val palette = Palette.from(InterfaceTheme.Obsidian)
        compose.setContent {
            MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
                CompositionLocalProvider(LocalPalette provides palette, LocalHomeChineseUnits provides chinese, LocalInteractionMotion provides false) {
                    Column(Modifier.width(360.dp)) {
                        TotalPanel(UsagePeriod(totalTokens = 1_234_567), compact = true, home = false)
                        HomeBreakdown(mapOf("dsh" to 1_234_567L), emptyMap(), RankingMetric.Tokens)
                    }
                }
            }
        }
        compose.onNodeWithText("1,234,567").assertIsDisplayed()
        compose.onNodeWithText("123.5万  100%").assertIsDisplayed()
        compose.runOnIdle { chinese = false }
        compose.onNodeWithText("1,234,567").assertIsDisplayed()
        compose.onNodeWithText("1.2M  100%").assertIsDisplayed()
    }
}
