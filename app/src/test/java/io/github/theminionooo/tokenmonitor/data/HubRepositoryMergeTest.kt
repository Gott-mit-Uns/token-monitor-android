package io.github.theminionooo.tokenmonitor.data

import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.network.WireHubSnapshot
import io.github.theminionooo.tokenmonitor.data.protocol.HubStreamProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `freshness retains the newest device history rather than the initial endpoint history`() {
        val initial = wire(
            """{"devices":[{"deviceId":"desktop","periods":{"today":{"totalTokens":900}}}],"historyPreview":{"daily":[{"date":"2026-10-04","tokens":900}]}}""",
        )
        val previous = HubProtocolParser.decodeStats(
            """{"devices":[{"deviceId":"desktop","historyAvailable":true,"history":{"daily":[{"date":"2026-10-04","tokens":700}]}}]}""",
        ).devices
        val freshStats = HubStreamProtocol.mergeFreshness(initial.stats,
            """{"stats":{"devices":[{"deviceId":"desktop","ageMs":30000,"stale":true}]}}""")

        val next = decodeStatsUpdate(initial.copy(stats = freshStats), previous)

        val device = next.stats.devices.single()
        assertEquals(700L, device.history.daily.single().tokens)
        assertEquals(900L, device.periods.getValue("today").totalTokens)
        assertEquals(true, device.historyAvailable)
        assertTrue(device.stale)
        assertEquals(30_000L, device.ageMs)
        assertEquals(900L, next.history.daily.single().tokens)
        assertEquals(1234L, next.capturedAt)
    }

    @Test
    fun `complete updates replace device history and do not restore removed devices`() {
        val previous = HubProtocolParser.decodeStats(
            """{"devices":[{"deviceId":"desktop","history":{"daily":[{"date":"2026-10-04","tokens":700}]}},{"deviceId":"removed"}]}""",
        ).devices
        val next = decodeStatsUpdate(wire(
            """{"devices":[{"deviceId":"desktop","history":{"daily":[{"date":"2026-10-04","tokens":1200}]}}]}""",
        ), previous)

        assertEquals(listOf("desktop"), next.stats.devices.map { it.id })
        assertEquals(1200L, next.stats.devices.single().history.daily.single().tokens)
        assertTrue(decodeStatsUpdate(wire("""{"devices":[]}"""), previous).stats.devices.isEmpty())
    }

    private fun wire(stats: String) = WireHubSnapshot(
        health = """{"ok":true,"role":"hub"}""",
        stats = stats,
        devices = """{"devices":[{"deviceId":"desktop","history":{"daily":[{"date":"2026-10-04","tokens":100}]}}]}""",
        history = """{"daily":[{"date":"2026-10-04","tokens":100}]}""",
        subscriptions = """{"subscriptions":[]}""",
        capturedAt = 1234,
    )

    private fun resource(name: String): String = checkNotNull(
        javaClass.classLoader?.getResourceAsStream("protocol/v0.54.0/$name"),
    ).bufferedReader().use { it.readText() }
}
