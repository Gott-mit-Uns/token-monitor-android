package io.github.theminionooo.tokenmonitor.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HubDeadlineTest {
    @Test fun `expired widget cannot restart network work`() {
        assertEquals(HubWorkMode.WidgetPolling, selectHubWorkMode(false, true, 10_000, 9_999))
        assertEquals(HubWorkMode.Idle, selectHubWorkMode(false, true, 10_000, 10_000))
        assertEquals(HubWorkMode.Idle, selectHubWorkMode(false, true, 10_000, 100_000))
    }

    @Test fun `dashboard ownership survives widget expiry`() {
        assertEquals(HubWorkMode.DashboardStreaming, selectHubWorkMode(true, true, 10_000, 100_000))
        assertEquals(HubWorkMode.Idle, selectHubWorkMode(false, false, null, 100_000))
    }
}
