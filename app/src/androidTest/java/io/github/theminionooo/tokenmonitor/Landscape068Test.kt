package io.github.theminionooo.tokenmonitor

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.HubRepositoryState
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.ui.*
import org.junit.Rule
import org.junit.Test

/** Short landscape viewports may scroll; whole numeric fields must remain reachable. */
class Landscape068Test {
    @get:Rule val compose = createComposeRule()
    @Test fun normalLandscape() = gallery(1f)
    @Test fun largeLandscape() = gallery(2f)
    private fun gallery(scale: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun asset(name: String) = instrumentation.context.assets.open("showcase/$name.json").bufferedReader().use { it.readText() }
        val snapshot = hubNamedSnapshot(HubProtocolParser.decodeSnapshot(asset("health"), asset("stats"), asset("devices"), asset("history"), asset("subscriptions"), 0))
        var destination by mutableStateOf(DashboardDestination.Models)
        val palette = Palette.from(InterfaceTheme.Obsidian)
        compose.setContent {
            CompositionLocalProvider(LocalPalette provides palette, LocalInteractionMotion provides false, LocalDensity provides Density(LocalDensity.current.density, scale)) {
                MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
                    DashboardContent(Modifier.fillMaxSize().background(palette.shell), HubRepositoryState(snapshot = snapshot), destination, DashboardPeriod.Today, { destination = it }, {}, false, ServiceStatusState(), DisplayOptions(), {})
                }
            }
        }
        val model = snapshot.today.models.maxBy { it.value }.key
        compose.onNodeWithText(model).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { destination = DashboardDestination.Devices }
        val device = snapshot.stats.devices.last()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(device.hostname))
        compose.onNodeWithText(device.hostname).assertIsDisplayed()
        compose.onNodeWithText(formatTokens(device.periods.getValue("today").totalTokens)).performScrollTo().assertIsDisplayed()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        instrumentation.targetContext.openFileOutput("landscape-068-$scale.png", 0).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
