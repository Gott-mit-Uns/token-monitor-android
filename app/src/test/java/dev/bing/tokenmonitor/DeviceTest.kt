package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class DeviceTest {
    @Test fun desktopTimeZoneAppliesToEveryDevicePeriodAtWindowRoot() {
        val snapshot = HubParser.parse("""{"periods":{},"devices":[{"deviceId":"demo",
          "periods":{"today":{"totalTokens":1},"month":{"totalTokens":2},"allTime":{"totalTokens":3}},
          "periodWindows":{"timeZone":"Asia/Hong_Kong",
            "today":{"key":"2026-05-18","endsAt":"2026-05-19T00:00:00.000Z","timeZone":"legacy/nested"},
            "month":{"key":"2026-05","endsAt":"2026-06-01T00:00:00.000Z"}}}]}""")
        val periods = snapshot.devices.single().periods
        assertTrue(periods.values.all { it.zone == "Asia/Hong_Kong" })
        assertEquals("2026-05-18", periods.getValue("today").window)
        assertEquals("2026-06-01T00:00:00.000Z", periods.getValue("month").ends)
        assertNull(periods.getValue("allTime").window)
    }
    @Test fun deviceUsageDoesNotOverrideHealthScanAndFutureStatesRemainUnknown() {
        val data = HubParser.parse("""{"periods":{},"devices":[{"deviceId":"demo","hostname":"Demo","osName":"macOS","osVersion":"27.0",
          "periods":{"allTime":{"totalTokens":123,"costUsd":0.01,"clients":{"codex":123}}},
          "periodWindows":{"allTime":{"key":"all","timeZone":"Asia/Shanghai"}},
          "clientHealth":{"version":1,"observedAt":"2026-10-09T00:00:00Z","clients":{"codex":{
            "overall":"waiting","source":{"state":"detected"},"collection":{"state":"future-value"},"data":{"liveTokens":0},
            "diagnostics":[{"code":"no-usage-observed"},{"code":"unexpected-private-detail"}]}}}}]}""")
        val device = data.devices.single()
        assertEquals("macOS 27.0", device.platform)
        assertEquals(123L, device.periods.getValue("allTime").tokens)
        assertEquals("Asia/Shanghai", device.periods.getValue("allTime").zone)
        val health = device.health!!.clients.single()
        assertEquals("waiting", health.overall)
        assertEquals(0L, health.liveTokens)
        assertEquals("unknown", health.collection)
        assertEquals(listOf("no-usage-observed"), health.diagnostics)
    }
    @Test fun missingAndUnknownHealthVersionAreNotHealthy() {
        val snapshot = HubParser.parse("""{"periods":{},"devices":[{"deviceId":"zero","periods":{"today":{"totalTokens":0}}},
          {"deviceId":"future","clientHealth":{"version":2,"clients":{}}}]}""")
        assertEquals(2, snapshot.devices.size)
        assertEquals(0L, snapshot.devices.first().periods.getValue("today").tokens)
        assertTrue(snapshot.devices.all { it.health == null })
    }
}
