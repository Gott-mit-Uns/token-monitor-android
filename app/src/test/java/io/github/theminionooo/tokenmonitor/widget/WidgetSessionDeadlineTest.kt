package io.github.theminionooo.tokenmonitor.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSessionDeadlineTest {
    @Test
    fun `live expires at the elapsed deadline even when the phone slept`() {
        val deadline = widgetSessionDeadline(null, false, true, 12_000, 1_800_000_000_000)

        assertEquals(3_612_000, deadline.elapsedRealtime)
        assertFalse(deadline.hasExpired(3_611_999))
        assertTrue(deadline.hasExpired(3_612_000))
        // No callback ran during sleep; the first wake callback must still see an expired lease.
        assertTrue(deadline.hasExpired(12_000 + 8 * 60 * 60_000L))
        assertEquals(0, deadline.remainingMillis(12_000 + 8 * 60 * 60_000L))
    }

    @Test
    fun `refresh during live preserves the original deadline and display time`() {
        val initial = widgetSessionDeadline(null, false, true, 1_000, 1_800_000_000_000)
        val refreshed = widgetSessionDeadline(initial, true, true, 2_700_000, 1_800_002_700_000)

        assertSame(initial, refreshed)
        assertEquals(901_000, refreshed.remainingMillis(2_700_000))
    }

    @Test
    fun `changing the wall clock cannot extend a live lease`() {
        val initial = widgetSessionDeadline(null, false, true, 1_000, 1_800_000_000_000)
        val refreshed = widgetSessionDeadline(initial, true, true, 3_601_000, 1_799_990_000_000)

        assertSame(initial, refreshed)
        assertTrue(refreshed.hasExpired(3_601_000))
    }

    @Test
    fun `one snapshot refresh expires after forty five seconds`() {
        val deadline = widgetSessionDeadline(null, false, false, 5_000, 1_800_000_000_000)

        assertEquals(0, deadline.displayExpiresAt)
        assertFalse(deadline.hasExpired(49_999))
        assertTrue(deadline.hasExpired(50_000))
    }

    @Test
    fun `explicit live request upgrades a one shot refresh to one hour`() {
        val refresh = widgetSessionDeadline(null, false, false, 5_000, 1_800_000_000_000)
        val live = widgetSessionDeadline(refresh, false, true, 10_000, 1_800_000_005_000)

        assertEquals(3_610_000, live.elapsedRealtime)
        assertEquals(1_800_003_605_000, live.displayExpiresAt)
    }
}
