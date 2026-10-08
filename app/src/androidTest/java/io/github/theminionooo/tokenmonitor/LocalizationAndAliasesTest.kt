package io.github.theminionooo.tokenmonitor

import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.storage.DeviceAliasStore
import io.github.theminionooo.tokenmonitor.localization.*
import io.github.theminionooo.tokenmonitor.ui.*
import io.github.theminionooo.tokenmonitor.widget.*
import org.junit.*
import org.junit.Assert.*
import java.time.Instant

class LocalizationAndAliasesTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    @Before fun setup() { check(context.packageName.endsWith(".preview")); LanguagePreferences.set(context, "zh-CN") }
    @After fun cleanup() { LanguagePreferences.set(context, "system") }
    private fun fixture(): io.github.theminionooo.tokenmonitor.domain.HubSnapshot {
        fun asset(name: String) = instrumentation.context.assets.open("showcase/$name.json").bufferedReader().use { it.readText() }
        return HubProtocolParser.decodeSnapshot(asset("health"), asset("stats"), asset("devices"), asset("history"), asset("subscriptions"), Instant.parse("2026-09-07T18:00:00Z").toEpochMilli(), false)
    }
    @Test fun desktopTermsAndDynamicTemplatesUseResources() {
        assertEquals("主页", uiText(context, "Home"))
        assertEquals("工具", uiText(context, "Tools"))
        assertEquals("设备", uiText(context, "Devices"))
        assertFalse(uiText(context, "12 active days").contains("active days"))
        assertFalse(uiText(context, "Updated 10:30").contains("Updated"))
        assertTrue(uiText(context, "Updated 10:30").contains("10:30"))
        assertEquals("gpt-6.1-sol", uiText(context, "gpt-6.1-sol"))
        LanguagePreferences.set(context, "en")
        assertEquals("Home", uiText(context, "Home"))
        assertEquals("en", context.getSharedPreferences("language_preferences", 0).getString("language", null))
    }
    @Test fun widgetUsesChineseWithEnglishSystemLocale() {
        assertEquals("近 7 天", widgetText(context, "7 DAYS"))
        assertFalse(widgetText(context, "$1.67 estimated cost · MON, SEP 7").contains("estimated cost"))
        instrumentation.runOnMainSync {
            val view = UsageWidgetProvider.render(context, fixture(), InterfaceTheme.Default, WidgetLayout.Large).apply(context, FrameLayout(context))
            assertEquals("近 7 天", view.findViewById<TextView>(R.id.widget_chart_title).text.toString())
            assertEquals("额度", view.findViewById<TextView>(R.id.widget_limits_title).text.toString())
        }
        LanguagePreferences.set(context, "en")
        assertEquals("7 DAYS", widgetText(context, "7 DAYS"))
    }
    @Test fun quotaSourceUsesDeviceAliasWithoutTranslatingAccountName() {
        val account = fixture().stats.limits.providers.first().copy(sourceDeviceId = "source-device", accountName = "Home")
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localizedContext(context), LocalPalette provides Palette.from(InterfaceTheme.Default)) {
                MaterialTheme {
                    LimitAccountRow(account, io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions(showLimitSource = true), mapOf("source-device" to "家中 NAS"))
                }
            }
        }
        compose.onNodeWithText(uiText(context, "Source 家中 NAS"), substring = true).assertIsDisplayed()
        compose.onNodeWithText("Home", substring = true).assertIsDisplayed()
    }
    @Test fun hubNamesWinAndLegacyAliasesRemainDormant() {
        val raw = fixture()
        val first = raw.stats.devices.first()
        val hub = "https://alias-ui.example.com"
        val store = DeviceAliasStore(context)
        val previous = store.alias(hub, first.id)
        try {
            store.save(hub, first.id, "旧安卓别名")
            val display = hubNamedSnapshot(raw)
            compose.setContent {
                CompositionLocalProvider(LocalContext provides localizedContext(context), LocalPalette provides Palette.from(InterfaceTheme.Default)) {
                    MaterialTheme { LazyColumn { deviceItems(display.stats.devices, DashboardPeriod.Today) } }
                }
            }
            compose.onNodeWithText(first.id.ifBlank { first.hostname }).assertIsDisplayed()
            compose.onNodeWithText("旧安卓别名").assertDoesNotExist()
            compose.onAllNodesWithContentDescription(uiText(context, "Rename device") + ":", substring = true).assertCountEquals(0)
            assertEquals("旧安卓别名", store.alias(hub, first.id))
        } finally { store.save(hub, first.id, previous.orEmpty()); store.close() }
    }
}
