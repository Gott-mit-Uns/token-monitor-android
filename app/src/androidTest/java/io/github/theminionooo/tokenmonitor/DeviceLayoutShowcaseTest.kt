package io.github.theminionooo.tokenmonitor

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.HubRepositoryState
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import io.github.theminionooo.tokenmonitor.localization.*
import androidx.compose.ui.platform.LocalContext
import java.time.Instant

/** Captures production components with synthetic fixtures; never reads stored pairing or a network. */
class DeviceLayoutShowcaseTest {
    @Before fun chinese() { LanguagePreferences.set(context, "zh-CN") }
    @After fun resetLanguage() { LanguagePreferences.set(context, "system") }
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val captured = Instant.parse("2026-09-07T18:00:00Z").toEpochMilli()
    private fun snapshot(): io.github.theminionooo.tokenmonitor.domain.HubSnapshot {
        fun asset(name: String) = instrumentation.context.assets.open("showcase/$name.json").bufferedReader().use { it.readText() }
        return HubProtocolParser.decodeSnapshot(asset("health"), asset("stats"), asset("devices"), asset("history"), asset("subscriptions"), captured, false)
    }
    private fun save(name: String, bitmap: Bitmap) {
        context.openFileOutput("device-layout-$name.png", 0).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun lightDevices() = dashboardGallery(true)
    @Test fun darkDevices() = dashboardGallery(false)
    @Test fun compactNormal() = dashboardGallery(false, 1f, 16)
    @Test fun comfortableNormal() = dashboardGallery(true, 1f, 20)
    @Test fun largeNormal() = dashboardGallery(false, 1f, 24)
    @Test fun compactLargeFont() = dashboardGallery(true, 1.5f, 16)
    @Test fun comfortableLargeFont() = dashboardGallery(false, 1.5f, 20)
    @Test fun largeLargeFont() = dashboardGallery(true, 1.5f, 24)
    @Test fun compactLargestFont() = dashboardGallery(false, 2f, 16)
    @Test fun comfortableLargestFont() = dashboardGallery(true, 2f, 20)
    @Test fun largeLargestFont() = dashboardGallery(false, 2f, 24)

    @Test fun largestIntegerRemainsWhole() = dashboardGallery(true, 2f, 24, hugeCounter = true)

    @Test fun emptyDebianDeviceCanRevealFullSystem() {
        val device = snapshot().stats.devices.first().copy(hostname = "测试 NAS", platform = "linux", trackedClients = emptyList())
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalContext provides localizedContext(context), LocalPalette provides Palette.from(InterfaceTheme.Obsidian), LocalInteractionMotion provides false) {
                    DeviceUsageRow(device, io.github.theminionooo.tokenmonitor.domain.UsagePeriod(), "Debian GNU/Linux 12 (bookworm)", "", 0f)
                }
            }
        }
        compose.onNodeWithText("Debian GNU/Linux 12 (bookworm)").assertDoesNotExist()
        compose.onNodeWithText("测试 NAS").performClick()
        compose.onNodeWithText("Debian GNU/Linux 12 (bookworm)").assertIsDisplayed()
    }

    private fun dashboardGallery(light: Boolean, fontScale: Float = 1f, iconDp: Int = 20, hugeCounter: Boolean = false) {
        check(context.packageName.endsWith(".preview"))
        val base = snapshot()
        val clients = linkedMapOf("codex" to 25200000L, "hermes" to 12400000L, "dsh" to 7980000L)
        val models = linkedMapOf("gpt-6.1-sol" to 25200000L, "claude-sonnet-4-6" to 12400000L, "deepseek-v4-flash" to 7980000L)
        val usage = io.github.theminionooo.tokenmonitor.domain.UsagePeriod(
            totalTokens = clients.values.sum(), costUsd = 18.75, clients = clients,
            clientCosts = mapOf("codex" to 12.6, "hermes" to 4.65, "dsh" to 1.5),
            models = models, modelCosts = mapOf("gpt-6.1-sol" to 12.6, "claude-sonnet-4-6" to 4.65, "deepseek-v4-flash" to 1.5),
            clientModels = mapOf("codex" to mapOf("gpt-6.1-sol" to 25200000L), "hermes" to mapOf("claude-sonnet-4-6" to 12400000L), "dsh" to mapOf("deepseek-v4-flash" to 7980000L)))
        val seed = base.stats.devices.first()
        val devices = listOf(Triple("Mac Mini", "darwin", 8700664L), Triple("NAS DXP4800", "linux", 19574770L), Triple("Windows Mini", "win32", 63862035L), Triple("NAS DH4300plus", "linux", 0L)).mapIndexed { i, (name, platform, tokens) ->
            seed.copy(id = if (fontScale > 1f && i == 1) "家中的绿联 NAS DXP4800 备份与开发服务器" else name, hostname = "original-host-$i", platform = platform, osName = when (platform) { "darwin" -> "macOS"; "win32" -> "Windows"; else -> "Debian GNU/Linux" }, osVersion = if (platform == "linux") "12 (bookworm)" else if (platform == "darwin") "27.0.1" else "11 26H2", stale = i == 2, updatedAt = if (i == 3) "" else Instant.ofEpochMilli(captured - i * 240000L).toString(),
                periods = mapOf("today" to usage.copy(totalTokens = if (hugeCounter && i == 0) Long.MAX_VALUE else tokens, costUsd = listOf(2.21, 0.63, 11.73, 0.0)[i], clients = emptyMap(), models = emptyMap())))
        }
        val codex = base.stats.limits.providers.first().copy(provider = "codex", accountName = "", accountEmail = "", windows = listOf(
            io.github.theminionooo.tokenmonitor.domain.LimitWindow("session", "Session", 77.0, 23.0, null, "2026-09-07T18:51:00Z", "percent", "", "", true),
            io.github.theminionooo.tokenmonitor.domain.LimitWindow("weekly", "Weekly", 66.0, 34.0, null, "2026-09-13T12:00:00Z", "percent", "", "", true)))
        val deepseek = codex.copy(provider = "deepseek", plan = "", windows = listOf(
            io.github.theminionooo.tokenmonitor.domain.LimitWindow("balance", "Balance", null, null, 28.37, "", "balance", "CNY", "", false)))
        val raw = base.copy(stats = base.stats.copy(devices = devices, periods = mapOf("today" to usage.copy(totalTokens = 92137469L, costUsd = 14.57)), limits = base.stats.limits.copy(providers = listOf(codex, deepseek))))
        val originalNames = raw.stats.devices.associate { it.id to if (it.platform == "linux") "nas-original-${it.id}" else it.hostname }
        val state = HubRepositoryState(hasConnection = true, snapshot = io.github.theminionooo.tokenmonitor.fork.hubNamedSnapshot(raw), streamActive = true)
        var destination by mutableStateOf(DashboardDestination.Devices)
        var options by mutableStateOf(DisplayOptions(themeCode = (if (light) InterfaceTheme.Porcelain else InterfaceTheme.Obsidian).code, colorfulToolMarks = false, visibleHomeModules = listOf("Limits", "Tools", "Devices", "Models")))
        val palette = Palette.from(if (light) InterfaceTheme.Porcelain else InterfaceTheme.Obsidian)
        var captureWindow: android.view.Window? = null
        compose.setContent {
            var owner = androidx.compose.ui.platform.LocalView.current.context
            while (owner is android.content.ContextWrapper && owner !is android.app.Activity) owner = owner.baseContext
            captureWindow = (owner as android.app.Activity).window
            MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
                CompositionLocalProvider(LocalContext provides localizedContext(context), LocalPalette provides palette, LocalInteractionMotion provides false, LocalNow provides captured, LocalColorfulToolMarks provides false, LocalContentIconSize provides iconDp.dp, androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(androidx.compose.ui.platform.LocalDensity.current.density, fontScale)) {
                    Box(Modifier.size(360.dp, 1100.dp).background(Brush.linearGradient(colorStops = arrayOf(0f to palette.gradientTop, 0.38f to palette.shell, 1f to palette.gradientBottom))).testTag("showcase")) {
                        DashboardScaffold(state = state, destination = destination, form = ConnectionFormState(), displayOptions = options, serviceStatus = ServiceStatusState(),
                    onChoose = { destination = it }, onRefresh = {}, onSaveConnection = { _, _, _, _ -> },
                    originalDeviceNames = emptyMap(), onRenameDevice = null,
                    onColorfulToolMarksChange = {}, onCompactTokenTotalChange = {}, onReduceMotionChange = {}, onTextScaleChange = { options = options.copy(textScale = it) },
                    onIconScaleChange = { options = options.copy(iconScale = it) },
                    onHomeChineseUnitsChange = { options = options.copy(homeChineseUnits = it) },
                    onThemeCodeChange = {}, onFollowSystemThemeChange = {}, onShowLiveIndicatorChange = {}, onShowToolIconsChange = {}, onRankingMetricChange = {},
                    onShowLimitSourceChange = {}, onShowAccountEmailsChange = {}, onLimitBarMetricChange = {}, onDefaultPeriodChange = {},
                    onViewVisibleChange = { _, _ -> }, onHomeModuleVisibleChange = { _, _ -> }, onMoveView = { _, _ -> }, onMoveHomeModule = { _, _ -> },
                    onDisconnect = {}, onOpenServicePage = {}, onOpenReleasePage = {}, discovery = HubDiscoveryState(), onFindHomeHub = {},
                        )
                    }
                }
            }
        }
        fun capture(name: String) {
            compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle()
            instrumentation.waitForIdleSync()
            val drawn = java.util.concurrent.CountDownLatch(1)
            val decor = checkNotNull(captureWindow).decorView
            decor.post {
                decor.requestLayout()
                decor.invalidate()
                decor.postOnAnimation { decor.postOnAnimation { drawn.countDown() } }
            }
            check(drawn.await(5, java.util.concurrent.TimeUnit.SECONDS))
            Thread.sleep(250)
            // Capture the final window composition, including positioned hardware layers.
            val bounds = compose.onNodeWithTag("showcase").fetchSemanticsNode().boundsInWindow
            val bitmap = Bitmap.createBitmap(bounds.width.toInt(), bounds.height.toInt(), Bitmap.Config.ARGB_8888)
            val copied = java.util.concurrent.CountDownLatch(1)
            var result = -1
            android.view.PixelCopy.request(checkNotNull(captureWindow), android.graphics.Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()), bitmap,
                { result = it; copied.countDown() }, android.os.Handler(android.os.Looper.getMainLooper()))
            check(copied.await(5, java.util.concurrent.TimeUnit.SECONDS) && result == android.view.PixelCopy.SUCCESS)
            save(name, bitmap)
            bitmap.recycle()
        }
        if (fontScale == 1f) capture("${if (light) "light" else "dark"}-$iconDp")
        for (device in state.snapshot!!.stats.devices) {
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(device.hostname))
            compose.onNodeWithText(device.hostname).assertIsDisplayed()
            compose.onNodeWithText(formatTokens(device.periods.getValue("today").totalTokens)).assertExists()
        }
        capture("${if (light) "light" else "dark"}-$fontScale-$iconDp-bottom")
    }
}
