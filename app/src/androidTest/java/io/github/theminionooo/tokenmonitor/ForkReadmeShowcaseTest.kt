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
class ForkReadmeShowcaseTest {
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
        context.openFileOutput("fork-home-$name.png", 0).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun lightHome() = dashboardGallery(light = true)
    @Test fun darkHome() = dashboardGallery(light = false)

    private fun dashboardGallery(light: Boolean) {
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
        val devices = listOf(Triple("Mac Mini", "darwin", 19200000L), Triple("NAS DXP4800", "linux", 11900000L), Triple("Windows Mini", "win32", 8500000L), Triple("NAS DH4300plus", "linux", 5980000L)).mapIndexed { i, (name, platform, tokens) ->
            seed.copy(id = "readme-device-$i", hostname = name, platform = platform, osName = when (platform) { "darwin" -> "macOS"; "win32" -> "Windows"; else -> "UGOS" }, stale = false,
                periods = mapOf("today" to usage.copy(totalTokens = tokens, costUsd = 18.75 * tokens / usage.totalTokens)))
        }
        val codex = base.stats.limits.providers.first().copy(provider = "codex", accountName = "", accountEmail = "", windows = listOf(
            io.github.theminionooo.tokenmonitor.domain.LimitWindow("session", "Session", 77.0, 23.0, null, "2026-09-07T18:51:00Z", "percent", "", "", true),
            io.github.theminionooo.tokenmonitor.domain.LimitWindow("weekly", "Weekly", 66.0, 34.0, null, "2026-09-13T12:00:00Z", "percent", "", "", true)))
        val deepseek = codex.copy(provider = "deepseek", plan = "", windows = listOf(
            io.github.theminionooo.tokenmonitor.domain.LimitWindow("balance", "Balance", null, null, 28.37, "", "balance", "CNY", "", false)))
        val raw = base.copy(stats = base.stats.copy(devices = devices, periods = mapOf("today" to usage), limits = base.stats.limits.copy(providers = listOf(codex, deepseek))))
        val originalNames = raw.stats.devices.associate { it.id to it.hostname }
        val state = HubRepositoryState(hasConnection = true, snapshot = raw, streamActive = true)
        var destination by mutableStateOf(DashboardDestination.Home)
        var options by mutableStateOf(DisplayOptions(colorfulToolMarks = false, visibleHomeModules = listOf("Limits", "Tools", "Devices", "Models")))
        val palette = Palette.from(if (light) InterfaceTheme.Porcelain else InterfaceTheme.Obsidian)
        var captureWindow: android.view.Window? = null
        compose.setContent {
            var owner = androidx.compose.ui.platform.LocalView.current.context
            while (owner is android.content.ContextWrapper && owner !is android.app.Activity) owner = owner.baseContext
            captureWindow = (owner as android.app.Activity).window
            MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
                CompositionLocalProvider(LocalContext provides localizedContext(context), LocalPalette provides palette, LocalInteractionMotion provides false, LocalNow provides captured, LocalColorfulToolMarks provides false) {
                    Box(Modifier.size(393.dp, 1000.dp).background(Brush.linearGradient(colorStops = arrayOf(0f to palette.gradientTop, 0.38f to palette.shell, 1f to palette.gradientBottom))).testTag("showcase")) {
                        DashboardScaffold(state = state, destination = destination, form = ConnectionFormState(), displayOptions = options, serviceStatus = ServiceStatusState(),
                    onChoose = { destination = it }, onRefresh = {}, onSaveConnection = { _, _, _, _ -> },
                    originalDeviceNames = originalNames, onRenameDevice = { _, _ -> null },
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
        compose.onNodeWithText("DeepSeek Harness").assertIsDisplayed()
        compose.onNodeWithText("Hermes Agent").assertIsDisplayed()
        compose.onNodeWithText("NAS DH4300plus").assertIsDisplayed()
        capture(if (light) "light" else "dark")
    }
}
