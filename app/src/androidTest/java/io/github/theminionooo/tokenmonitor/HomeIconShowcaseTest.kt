package io.github.theminionooo.tokenmonitor

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.storage.RankingMetric
import io.github.theminionooo.tokenmonitor.data.storage.IconScale
import io.github.theminionooo.tokenmonitor.data.storage.TextScale
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.domain.*
import io.github.theminionooo.tokenmonitor.localization.*
import io.github.theminionooo.tokenmonitor.ui.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Same synthetic data and production home modules are used for before/after captures. */
class HomeIconShowcaseTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val now = Instant.parse("2026-09-07T18:00:00Z").toEpochMilli()
    @Test fun darkNormal() = gallery(false, 1f, true)
    @Test fun darkLarge() = gallery(false, 1.5f, true)
    @Test fun darkLargest() = gallery(false, 2f, true)
    @Test fun lightNormal() = gallery(true, 1f, true)
    @Test fun lightLarge() = gallery(true, 1.5f, false)
    @Test fun lightLargest() = gallery(true, 2f, false)

    @Test fun pairCompactCompact() = gallery(false, 1f, true, IconScale.Compact, TextScale.Compact)
    @Test fun pairCompactComfortable() = gallery(false, 1f, true, IconScale.Comfortable, TextScale.Compact)
    @Test fun pairCompactLarge() = gallery(false, 1f, true, IconScale.Large, TextScale.Compact)
    @Test fun pairComfortableCompact() = gallery(false, 1f, true, IconScale.Compact, TextScale.Comfortable)
    @Test fun pairComfortableComfortable() = gallery(false, 1f, true, IconScale.Comfortable, TextScale.Comfortable)
    @Test fun pairComfortableLarge() = gallery(false, 1f, true, IconScale.Large, TextScale.Comfortable)
    @Test fun pairLargeCompact() = gallery(false, 1f, true, IconScale.Compact, TextScale.Large)
    @Test fun pairLargeComfortable() = gallery(false, 1f, true, IconScale.Comfortable, TextScale.Large)
    @Test fun pairLargeLarge() = gallery(false, 1f, true, IconScale.Large, TextScale.Large)

    private fun gallery(light: Boolean, fontScale: Float, colorful: Boolean, iconScale: IconScale = IconScale.Comfortable, textScale: TextScale = TextScale.Comfortable) {
        check(context.packageName.endsWith(".preview"))
        LanguagePreferences.set(context, "zh-CN")
        fun asset(name: String) = instrumentation.context.assets.open("showcase/$name.json").bufferedReader().use { it.readText() }
        val raw = HubProtocolParser.decodeSnapshot(asset("health"), asset("stats"), asset("devices"), asset("history"), asset("subscriptions"), now, false)
        val clients = linkedMapOf("codex" to 67459000L, "dsh" to 95800L, "hermes" to 1124000L)
        val usage = raw.today.copy(totalTokens = clients.values.sum(), costUsd = 11.49, clients = clients, clientCosts = clients.mapValues { it.value / 6e6 }, models = linkedMapOf("gpt-6.1-sol" to 67459000L, "deepseek-flash" to 95800L), modelCosts = mapOf("gpt-6.1-sol" to 11.4, "deepseek-flash" to 0.09), sessions = raw.today.sessions.take(2).mapIndexed { i, session -> session.copy(title = if (i == 0) "优化安卓客户端主页图标" else "检查 NAS 的媒体服务", client = if (i == 0) "codex" else "hermes", modelNames = if (i == 0) listOf("gpt-6.1-sol", "deepseek-flash") else listOf("deepseek-flash"), lastUsedAt = Instant.ofEpochMilli(now - 10000).toString()) })
        val seed = raw.stats.devices.first()
        val devices = listOf("Mac Mini" to "darwin", "办公室 Windows" to "win32", "家中 NAS DXP4800" to "linux", "备份 NAS DH4300plus" to "linux").mapIndexed { i, (name, platform) -> seed.copy(id = "icon-device-$i", hostname = name, platform = platform, osName = when(platform) {"darwin" -> "macOS"; "win32" -> "Windows"; else -> "UGOS"}, stale = i == 3, periods = mapOf("today" to usage.copy(totalTokens = if(i == 3) 0 else (i+1)*100000L))) }
        val snapshot = raw.copy(stats = raw.stats.copy(devices = devices, periods = mapOf("today" to usage, "month" to usage)))
        val palette = Palette.from(if (light) InterfaceTheme.Porcelain else InterfaceTheme.Obsidian)
        compose.setContent {
            val density = LocalDensity.current.density
            MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(textScale.step)) {
                CompositionLocalProvider(LocalContext provides localizedContext(context), LocalDensity provides Density(density, fontScale), LocalPalette provides palette, LocalNow provides now, LocalToolIcons provides true, LocalContentIconSize provides iconScale.dp.dp, LocalColorfulToolMarks provides colorful) {
                    Column(Modifier.width(360.dp).fillMaxHeight().background(palette.shell).verticalScroll(rememberScrollState()).padding(14.dp).testTag("icon-home")) {
                        DesktopModule("LIMITS", DashboardDestination.Limits, {}) { HomeLimits(snapshot.stats.limits.providers, DisplayOptions()) }
                        DesktopModule("TOOLS", DashboardDestination.Tools, {}) { HomeBreakdown(usage.clients, usage.clientCosts, RankingMetric.Tokens) }
                        DesktopModule("MODELS", DashboardDestination.Models, {}) { HomeBreakdown(usage.models, usage.modelCosts, RankingMetric.Tokens, modelRows = true) }
                        DesktopModule("DEVICES", DashboardDestination.Devices, {}) { HomeDevices(devices, DashboardPeriod.Today, usage) }
                        HomeSessionsModule(snapshot, {})
                    }
                }
            }
        }
        val prefix = "cf4-${textScale.name}-${iconScale.name}-icons-${if(light) "light" else "dark"}-$fontScale-${if(colorful) "brand" else "mono"}"
        fun save(suffix: String) {
            compose.waitForIdle()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            context.openFileOutput("$prefix-$suffix.png", 0).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        save("top")
        compose.onNodeWithText("家中 NAS DXP4800").performScrollTo().assertIsDisplayed()
        val before = InstrumentationRegistry.getArguments().getString("iconBefore") == "true"
        if (!before) compose.onNodeWithText("备份 NAS DH4300plus").performScrollTo().assertIsDisplayed()
        save("devices")
        compose.onNodeWithText("检查 NAS 的媒体服务").performScrollTo().assertIsDisplayed()
        save("sessions")
        LanguagePreferences.set(context, "system")
    }
}
