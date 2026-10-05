package io.github.theminionooo.tokenmonitor.widget

import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.domain.HubStats
import io.github.theminionooo.tokenmonitor.domain.UsagePeriod
import java.time.ZoneOffset
import java.time.ZoneId
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetNotificationContentTest {
    @Test
    fun `fetch timestamp alone does not repost the notification`() {
        val snapshot = HubSnapshot(
            stats = HubStats(periods = mapOf("today" to UsagePeriod(totalTokens = 42, costUsd = 0.12))),
            capturedAt = 1,
        )
        val first = content(snapshot)
        val second = content(snapshot.copy(capturedAt = 2))

        assertEquals(first, second)
    }

    @Test
    fun `visible token change updates the notification`() {
        val snapshot = HubSnapshot(
            stats = HubStats(periods = mapOf("today" to UsagePeriod(totalTokens = 42, costUsd = 0.12))),
        )
        val changed = snapshot.copy(
            stats = snapshot.stats.copy(periods = mapOf("today" to snapshot.today.copy(totalTokens = 43))),
        )

        assertNotEquals(content(snapshot), content(changed))
    }

    @Test
    fun `losing the Hub marks unchanged totals as saved and shows their age`() {
        val snapshot = HubSnapshot(
            stats = HubStats(periods = mapOf("today" to UsagePeriod(totalTokens = 42, costUsd = 0.12))),
            capturedAt = 60_000,
        )
        val connected = WidgetSession(enabled = true, connected = true, snapshot = snapshot)
        val offline = connected.copy(connected = false, note = "Hub offline · retrying")
        val current = content(connected, now = 180_000)
        val saved = content(offline, now = 180_000)

        assertNotEquals(current, saved)
        assertEquals("Token Monitor · Reconnecting", saved.title)
        assertTrue(saved.headline.startsWith("Saved · 42 tokens"))
        assertTrue(saved.detail.contains("Last updated 2m ago"))
        assertEquals("Offline", saved.shortText)
        assertFalse(current.detail.contains("Last updated"))
    }

    @Test
    fun `reconnecting age does not change on every retry within a minute`() {
        val session = WidgetSession(
            enabled = true,
            snapshot = HubSnapshot(capturedAt = 60_000),
            note = "Hub offline · retrying",
        )

        assertEquals(content(session, now = 180_000), content(session, now = 239_999))
        assertNotEquals(content(session, now = 180_000), content(session, now = 240_000))
    }

    @Test
    fun `restored cache does not claim live data before a fresh read`() {
        val session = WidgetSession(
            enabled = true,
            connected = true,
            snapshot = HubSnapshot(capturedAt = 60_000, fromCache = true),
        )
        val notification = content(session, now = 180_000)

        assertEquals("Token Monitor · Reconnecting", notification.title)
        assertTrue(notification.detail.contains("Last updated 2m ago"))
        assertEquals("Offline", notification.shortText)
    }

    @Test
    fun `a new session without saved data says connecting`() {
        val notification = content(WidgetSession(enabled = true), now = 180_000)

        assertEquals("Token Monitor · Connecting", notification.title)
        assertEquals("Waiting for the Hub", notification.headline)
        assertFalse(notification.detail.contains("Last updated"))
        assertEquals("Waiting", notification.shortText)
    }

    @Test
    fun `reconnection removes saved status without needing a token change`() {
        val session = WidgetSession(enabled = true, snapshot = HubSnapshot(capturedAt = 60_000))
        val saved = content(session, now = 180_000)
        val current = content(session.copy(connected = true), now = 180_000)

        assertNotEquals(saved, current)
        assertEquals("Token Monitor · Live", current.title)
        assertFalse(current.headline.startsWith("Saved"))
        assertFalse(current.detail.contains("Last updated"))
    }

    @Test
    fun `midnight does not relabel the previous days connected snapshot as today`() {
        val now = Instant.parse("2026-10-05T00:00:10Z").toEpochMilli()
        val session = WidgetSession(enabled = true, connected = true, snapshot = HubSnapshot(
            stats = HubStats(updatedAt = "2026-10-04T23:59:50Z"),
            capturedAt = Instant.parse("2026-10-04T23:59:55Z").toEpochMilli(),
        ))

        val notification = content(session, now)

        assertTrue(notification.headline.endsWith("for 2026-10-04"))
        assertFalse(notification.headline.endsWith("today"))
    }

    @Test
    fun `today uses the phones calendar date rather than a UTC boundary`() {
        val session = WidgetSession(enabled = true, connected = true, snapshot = HubSnapshot(
            stats = HubStats(updatedAt = "2026-10-04T23:59:50Z"),
        ))
        val notification = widgetNotificationContent(
            session, live = true, now = Instant.parse("2026-10-05T00:00:10Z").toEpochMilli(),
            zoneId = ZoneId.of("America/Chicago"), locale = Locale.US,
        )

        assertTrue(notification.headline.endsWith("today"))
    }

    private fun content(snapshot: HubSnapshot) = content(
        WidgetSession(enabled = true, connected = true, expiresAt = 3_600_000, snapshot = snapshot),
    )

    private fun content(session: WidgetSession, now: Long = 0) = widgetNotificationContent(
        session = session,
        live = true,
        now = now,
        zoneId = ZoneOffset.UTC,
        locale = Locale.US,
    )
}
