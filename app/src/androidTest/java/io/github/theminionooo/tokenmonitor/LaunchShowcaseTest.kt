package io.github.theminionooo.tokenmonitor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
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
import io.github.theminionooo.tokenmonitor.widget.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Captures production components with synthetic fixtures; never reads stored pairing or a network. */
class LaunchShowcaseTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val captured = Instant.parse("2026-09-07T18:00:00Z").toEpochMilli()
    private fun snapshot(): io.github.theminionooo.tokenmonitor.domain.HubSnapshot {
        fun asset(name: String) = instrumentation.context.assets.open("showcase/$name.json").bufferedReader().use { it.readText() }
        return HubProtocolParser.decodeSnapshot(asset("health"), asset("stats"), asset("devices"), asset("history"), asset("subscriptions"), captured, false)
    }
    private fun save(name: String, bitmap: Bitmap) {
        context.openFileOutput("launch-$name.png", 0).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun homeGallery() = dashboardGallery(DashboardDestination.Home)
    @Test fun modelsGallery() = dashboardGallery(DashboardDestination.Models)
    @Test fun devicesGallery() = dashboardGallery(DashboardDestination.Devices)
    @Test fun projectsGallery() = dashboardGallery(DashboardDestination.Projects)
    @Test fun trendsGallery() = dashboardGallery(DashboardDestination.Trends)
    @Test fun settingsGallery() = dashboardGallery(DashboardDestination.Settings)
    @Test fun filteredModelsGallery() = dashboardGallery(DashboardDestination.Tools)

    private fun dashboardGallery(initial: DashboardDestination) {
        check(context.packageName.endsWith(".preview"))
        val state = HubRepositoryState(hasConnection = true, snapshot = snapshot(), streamActive = true)
        var destination by mutableStateOf(initial)
        val palette = Palette.from(InterfaceTheme.Default)
        var captureWindow: android.view.Window? = null
        compose.setContent {
            var owner = androidx.compose.ui.platform.LocalView.current.context
            while (owner is android.content.ContextWrapper && owner !is android.app.Activity) owner = owner.baseContext
            captureWindow = (owner as android.app.Activity).window
            MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
                CompositionLocalProvider(LocalPalette provides palette, LocalInteractionMotion provides false, LocalNow provides captured, LocalColorfulToolMarks provides true) {
                    Box(Modifier.size(393.dp, 820.dp).background(Brush.linearGradient(colorStops = arrayOf(0f to palette.gradientTop, 0.38f to palette.shell, 1f to palette.gradientBottom))).testTag("showcase")) {
                        DashboardScaffold(state = state, destination = destination, form = ConnectionFormState(), displayOptions = DisplayOptions(colorfulToolMarks = true), serviceStatus = ServiceStatusState(),
                    onChoose = { destination = it }, onRefresh = {}, onSaveConnection = { _, _, _, _ -> },
                    onColorfulToolMarksChange = {}, onCompactTokenTotalChange = {}, onReduceMotionChange = {}, onTextScaleChange = {},
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
        if (initial == DashboardDestination.Tools) {
            compose.onNodeWithText("Codex").performClick()
            compose.onNodeWithText("Codex · Models").assertIsDisplayed()
            capture("filtered-models")
        } else {
            capture(initial.name.lowercase())
            if (initial == DashboardDestination.Trends) {
                compose.onNodeWithText("TRENDS").performClick()
                capture("trends-bars")
                compose.onNodeWithText("K-LINE").performClick()
                capture("trends-kline")
            }
        }
    }

    @Test fun widgetGallery() {
        val data = snapshot()
        instrumentation.runOnMainSync {
            val preview = android.widget.RemoteViews(context.packageName, R.layout.usage_widget_preview).apply(context, FrameLayout(context))
            val pixels = (160 * context.resources.displayMetrics.density).toInt()
            preview.measure(View.MeasureSpec.makeMeasureSpec(pixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(pixels, View.MeasureSpec.EXACTLY))
            preview.layout(0, 0, pixels, pixels)
            val pickerBitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
            preview.draw(Canvas(pickerBitmap))
            save("widget-picker-preview", pickerBitmap)
            val sizes = listOf(Triple(WidgetLayout.Compact, 160, 140), Triple(WidgetLayout.Portrait, 160, 260), Triple(WidgetLayout.Wide, 360, 144), Triple(WidgetLayout.Overview, 360, 280), Triple(WidgetLayout.Large, 360, 400))
            for ((layout, widthDp, heightDp) in sizes) {
                val view = UsageWidgetProvider.render(context, data, InterfaceTheme.Default, layout, session = WidgetSession(enabled = true, connected = true, expiresAt = System.currentTimeMillis() + 3_600_000), size = android.util.SizeF(widthDp.toFloat(), heightDp.toFloat())).apply(context, FrameLayout(context))
                val density = context.resources.displayMetrics.density
                val width = (widthDp * density).toInt()
                val height = (heightDp * density).toInt()
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, width, height)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                drawWidget(view, bitmap)
                save("widget-${layout.name.lowercase()}", bitmap)
            }
        }
    }
}
