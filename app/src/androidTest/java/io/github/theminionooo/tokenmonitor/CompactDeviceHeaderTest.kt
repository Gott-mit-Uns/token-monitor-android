package io.github.theminionooo.tokenmonitor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.domain.*
import io.github.theminionooo.tokenmonitor.localization.*
import io.github.theminionooo.tokenmonitor.ui.*
import org.junit.*
import org.junit.Assert.*
import java.time.Instant

class CompactDeviceHeaderTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val now = Instant.parse("2026-09-07T18:00:00Z").toEpochMilli()
    @Before fun setup() { check(context.packageName.endsWith(".preview")); LanguagePreferences.set(context, "zh-CN") }
    @After fun cleanup() { LanguagePreferences.set(context, "system") }
    private fun device(name: String, tokens: Long): DeviceUsage {
        val wire = instrumentation.context.assets.open("showcase/stats.json").bufferedReader().use { it.readText() }
        return HubProtocolParser.decodeStats(wire).devices.first().copy(id = name, hostname = name, platform = "linux",
            updatedAt = Instant.ofEpochMilli(now - 240_000).toString(), stale = false, trackedClients = emptyList(),
            periods = mapOf("today" to UsagePeriod(totalTokens = tokens), "month" to UsagePeriod(totalTokens = 20_000_000 - tokens)))
    }
    private fun waitForRoot() = compose.waitUntil(10_000) {
        runCatching { compose.onAllNodesWithTag("compact-device-test").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    }
    @Test fun normalHeaderHasTwoRowsCenteredIconAndSharedColumnEdges() {
        val device = device("DXP4800", 19_574_770)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localizedContext(context), LocalPalette provides Palette.from(InterfaceTheme.Obsidian), LocalNow provides now, LocalInteractionMotion provides false) {
                MaterialTheme(typography = tokenMonitorTypography(1)) {
                    Box(Modifier.width(360.dp).padding(14.dp).testTag("compact-device-test")) {
                        DeviceUsageRow(device, device.periods.getValue("today").copy(costUsd = 0.63), "Debian GNU/Linux 12 (bookworm)", "", 0.3f)
                    }
                }
            }
        }
        waitForRoot()
        fun bounds(part: String) = compose.onNodeWithTag("device-$part-DXP4800", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val header = bounds("summary"); val icon = bounds("icon")
        assertEquals(header.center.y, icon.center.y, 1f)
        assertEquals(bounds("name").left, bounds("system").left, 1f)
        assertEquals(bounds("tokens").right, bounds("cost").right, 1f)
        assertEquals(bounds("status").right, bounds("age").right, 1f)
        assertEquals(bounds("name").center.y, bounds("tokens").center.y, 1f)
        assertEquals(bounds("system").top, bounds("cost").top, 1f)
        assertTrue(bounds("name").right <= bounds("status").left)
        assertTrue(bounds("status").right <= bounds("tokens").left)
        compose.onNodeWithText("已同步").assertIsDisplayed()
        compose.onNodeWithText("4 分钟前").assertIsDisplayed()
        compose.onNodeWithText("19,574,770").assertIsDisplayed()
        compose.onNodeWithText("$0.63").assertIsDisplayed()
        compose.onNodeWithText("Debian GNU/Linux 12 (bookworm)").assertDoesNotExist()
        compose.onNodeWithText("DXP4800").performClick()
        compose.onNodeWithText("Debian GNU/Linux 12 (bookworm)").assertIsDisplayed()
    }
    @Test fun homeRanksSelectedPeriodAndZeroDeviceRemainsInDetails() {
        val devices = listOf(device("DH4300Plus", 0), device("Mac Mini", 8_700_664), device("Windows Mini", 19_574_770), device("DXP4800", 12_000_000))
        var period by mutableStateOf(DashboardPeriod.Today)
        var details by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localizedContext(context), LocalPalette provides Palette.from(InterfaceTheme.Obsidian), LocalNow provides now, LocalInteractionMotion provides false) {
                MaterialTheme(typography = tokenMonitorTypography(1)) {
                    Box(Modifier.width(360.dp).testTag("compact-device-test")) {
                        if (details) androidx.compose.foundation.lazy.LazyColumn { deviceItems(devices, period) }
                        else HomeDevices(devices, period)
                    }
                }
            }
        }
        waitForRoot()
        compose.onNodeWithText("DH4300Plus").assertDoesNotExist()
        fun top(name: String) = compose.onNodeWithText(name).fetchSemanticsNode().boundsInRoot.top
        assertTrue(top("Windows Mini") < top("DXP4800")); assertTrue(top("DXP4800") < top("Mac Mini"))
        compose.runOnIdle { period = DashboardPeriod.Month }
        compose.onNodeWithText("DH4300Plus").assertIsDisplayed()
        assertTrue(top("DH4300Plus") < top("Mac Mini"))
        compose.runOnIdle { period = DashboardPeriod.Today; details = true }
        compose.onNodeWithText("DH4300Plus").assertIsDisplayed()
        compose.onNodeWithText("0").assertIsDisplayed()
    }
}
