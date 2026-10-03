package io.github.theminionooo.tokenmonitor.data

import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import org.junit.Assert.assertEquals
import org.junit.Test

class HubRepositoryMergeTest {
    @Test
    fun `dashboard streaming takes priority and widget alone polls`() {
        assertEquals(HubWorkMode.Idle, selectHubWorkMode(dashboardVisible = false, widgetActive = false))
        assertEquals(HubWorkMode.WidgetPolling, selectHubWorkMode(dashboardVisible = false, widgetActive = true))
        assertEquals(HubWorkMode.DashboardStreaming, selectHubWorkMode(dashboardVisible = true, widgetActive = false))
        assertEquals(HubWorkMode.DashboardStreaming, selectHubWorkMode(dashboardVisible = true, widgetActive = true))
    }

    @Test
    fun `widget polling cannot run faster than the documented cadence`() {
        assertEquals(60_000L, WIDGET_POLL_INTERVAL_MS)
    }

    @Test
    fun `stream updates retain detailed device history from the snapshot read`() {
        val previous = HubProtocolParser.decodeDevices(resource("devices.json"))
        val streamed = HubProtocolParser.decodeStats(resource("stats.json"))

        val merged = retainDeviceHistory(streamed, previous)

        assertEquals(125_430, merged.devices.single().history.daily.single().tokens)
    }

    private fun resource(name: String): String = checkNotNull(
        javaClass.classLoader?.getResourceAsStream("protocol/v0.54.0/$name"),
    ).bufferedReader().use { it.readText() }
}
