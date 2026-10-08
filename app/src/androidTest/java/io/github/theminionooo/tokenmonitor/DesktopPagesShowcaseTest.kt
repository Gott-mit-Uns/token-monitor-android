package io.github.theminionooo.tokenmonitor

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.HubRepositoryState
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.localization.LanguagePreferences
import io.github.theminionooo.tokenmonitor.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import java.io.File
import java.time.Instant

class DesktopPagesShowcaseTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    @Before fun language() { check(context.packageName.endsWith(".preview")); LanguagePreferences.set(context, "zh-CN") }
    @After fun cleanup() { LanguagePreferences.set(context, "system") }
    @Test fun toolsLight() = show(DashboardDestination.Tools, true)
    @Test fun toolsDark() = show(DashboardDestination.Tools, false)
    @Test fun modelsLight() = show(DashboardDestination.Models, true)
    @Test fun modelsDark() = show(DashboardDestination.Models, false)
    @Test fun sessionsLight() = show(DashboardDestination.Sessions, true)
    @Test fun sessionsDark() = show(DashboardDestination.Sessions, false)
    @Test fun projectsLight() = show(DashboardDestination.Projects, true)
    @Test fun projectsDark() = show(DashboardDestination.Projects, false)
    @Test fun limitsLight() = show(DashboardDestination.Limits, true)
    @Test fun limitsDark() = show(DashboardDestination.Limits, false)
    @Test fun trendsLight() = show(DashboardDestination.Trends, true)
    @Test fun trendsDark() = show(DashboardDestination.Trends, false)
    @Test fun modelsLarge() = show(DashboardDestination.Models, false, 2f)
    @Test fun limitsLarge() = show(DashboardDestination.Limits, false, 2f)
    private fun show(destination: DashboardDestination, light: Boolean, scale: Float = 1f) {
        fun asset(name: String) = instrumentation.context.assets.open("showcase/$name.json").bufferedReader().use { it.readText() }
        val captured = Instant.parse("2026-09-07T18:00:00Z").toEpochMilli()
        val snapshot = HubProtocolParser.decodeSnapshot(asset("health"), asset("stats"), asset("devices"), asset("history"), asset("subscriptions"), captured, false)
        val palette = Palette.from(if (light) InterfaceTheme.Porcelain else InterfaceTheme.Obsidian)
        compose.setContent {
            CompositionLocalProvider(LocalPalette provides palette, LocalNow provides captured, LocalInteractionMotion provides false, LocalDensity provides Density(LocalDensity.current.density, scale)) {
                MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
                    DashboardContent(Modifier.background(palette.shell), HubRepositoryState(hasConnection = true, snapshot = snapshot, streamActive = true),
                        destination, DashboardPeriod.Today, {}, {}, false, ServiceStatusState(), DisplayOptions(), {})
                }
            }
        }
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(context.getExternalFilesDir(null), "desktop-pages").apply { mkdirs() }
        File(directory, "${destination.name.lowercase()}-${if (light) "light" else "dark"}-$scale.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
