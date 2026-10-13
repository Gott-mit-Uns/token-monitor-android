package dev.bing.tokenmonitor

import java.time.Instant
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class QuotaWidgetTest {
    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private fun snapshot(providers: String) = HubParser.parse("""{"periods":{},"limits":{"providers":$providers}}""")
    private val fixture = """[
      {"provider":"codex","status":"ok","updatedAt":"2026-10-13T11:59:00Z","accountEmail":"one@example.invalid","windows":[{"kind":"session","remainingPercent":0},{"kind":"weekly","remainingPercent":70}]},
      {"provider":"codex","status":"ok","updatedAt":"2026-10-13T11:00:00Z","accountEmail":"two@example.invalid","windows":[{"kind":"session"},{"kind":"weekly","remainingPercent":10}]}
    ]"""
    @Test fun accountsAndDualPeriodsStaySeparateWithZeroMissingAndStaleSource() {
        val rows = quotaWidgetAccounts(snapshot(fixture), LimitDisplayPreferences(), Locale.US, now)
        assertEquals(2, rows.size)
        assertTrue(rows[0].title.contains("o***e@example.invalid"))
        assertTrue(rows[1].title.contains("t***o@example.invalid"))
        assertTrue(rows[0].lines[0].contains("0% left"))
        assertTrue(rows[0].lines[1].contains("70% left"))
        assertTrue(rows[1].lines[0].contains("Quota unavailable"))
        assertFalse(rows[1].lines[0].contains("0%"))
        assertFalse(rows[0].stale); assertTrue(rows[1].stale)
        assertFalse(rows.toString().contains("one@"))
    }
    @Test fun sharedUsedPreferenceAndHiddenProvidersApplyWithoutChangingSource() {
        val snapshot = snapshot(fixture)
        val rows = quotaWidgetAccounts(snapshot, LimitDisplayPreferences(showUsed = true), Locale.US, now)
        assertTrue(rows[0].lines[0].contains("100% used"))
        assertTrue(rows[0].lines[1].contains("30% used"))
        assertTrue(quotaWidgetAccounts(snapshot, LimitDisplayPreferences(hiddenHomeProviders = setOf("codex")), Locale.US, now).isEmpty())
        assertEquals(0.0, snapshot.limits.first().remaining!!, 0.0)
    }
    @Test fun missingProviderIsExplicitAndResponsiveCapacityHonorsLargeFont() {
        val rows = quotaWidgetAccounts(snapshot("""[{"provider":"claude","status":"unavailable","windows":[]}]"""), LimitDisplayPreferences(), Locale.US, now)
        assertEquals(1, rows.size)
        assertEquals(listOf("Quota unavailable · Open app"), rows.single().lines)
        assertTrue(rows.single().stale)
        assertEquals(2, HubQuotaWidget.capacity(360, 1f))
        assertEquals(1, HubQuotaWidget.capacity(360, 2f))
        assertEquals(3, HubQuotaWidget.capacity(600, 1f))
    }
}
