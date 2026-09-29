package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.*
import io.github.theminionooo.tokenmonitor.widget.WidgetLayout
import io.github.theminionooo.tokenmonitor.widget.widgetLayout
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class UsageHistoryTest {
    private val today = LocalDate.of(2026, 9, 7)
    @Test fun `seven calendar days do not mean seven recorded days`() {
        val history = (1L..7).map { HistoryPoint(today.minusDays(it * 3).toString(), 10, 1.0) }
        assertEquals(2, historyInRange(history, today.minusDays(6), today).size)
    }
    @Test fun `comparison excludes today and compares equal complete windows`() {
        val history = (0L..14).map { HistoryPoint(today.minusDays(it).toString(), if (it == 0L) 999 else if (it <= 7) 20 else 10, 1.0) }
        val result = compareUsage(history, today)
        assertTrue(result.complete)
        assertEquals(70L, result.tokenDelta)
        assertEquals(100.0, result.percentChange!!, 0.001)
    }
    @Test fun `incomplete and zero baselines have no percentage estimate`() {
        assertNull(compareUsage(listOf(HistoryPoint("2026-09-06", 10, 1.0)), today).percentChange)
        val history = (1L..14).map { HistoryPoint(today.minusDays(it).toString(), 0, 0.0) }
        assertNull(compareUsage(history, today).percentChange)
    }
    @Test fun `offline yesterday remains yesterday and keeps model attribution`() {
        val snapshot = HubSnapshot(stats = HubStats(updatedAt = "2026-09-06T12:00:00Z", periods = mapOf("today" to UsagePeriod(totalTokens = 42, models = mapOf("gpt-5" to 42)))), fromCache = true)
        val result = usageHistory(snapshot)
        assertEquals("2026-09-06", result.single().label)
        assertEquals(42L, result.single().perModel.getValue("gpt-5").tokens)
        assertTrue(historyInRange(result, today, today).isEmpty())
    }
    @Test fun `widget layout responds to both width and height`() {
        assertEquals(WidgetLayout.Wide, widgetLayout(300, 120))
        assertEquals(WidgetLayout.Overview, widgetLayout(250, 370))
        assertEquals(WidgetLayout.Large, widgetLayout(300, 440))
        assertEquals(WidgetLayout.Portrait, widgetLayout(180, 300))
        assertEquals(WidgetLayout.Wide, widgetLayout(300, 120, 1.3f))
        assertEquals(WidgetLayout.Wide, widgetLayout(240, 240, 1.3f))
        assertEquals(WidgetLayout.Overview, widgetLayout(240, 325, 1.3f))
        assertEquals(WidgetLayout.Compact, widgetLayout(180, 250, 1.3f))
    }

    @Test fun `sparse preview keeps matching attribution but never stale attribution`() {
        val saved = HistoryPoint("2026-09-06", 42, 1.0, outputTokens = 12,
            unclassifiedTokens = 30, tokenComponentsAvailable = true,
            perModel = mapOf("gpt-5" to HistoryAttribution(tokens = 42)))
        val sparse = HistoryPoint("2026-09-06", 42, 1.0)
        val matching = mergeUsageHistory(listOf(saved), listOf(sparse)).single()
        assertEquals(saved.perModel, matching.perModel)
        assertEquals(12L, matching.outputTokens)
        assertEquals(30L, matching.unclassifiedTokens)
        assertTrue(matching.tokenComponentsAvailable)

        val changed = mergeUsageHistory(listOf(saved), listOf(sparse.copy(tokens = 55))).single()
        assertTrue(changed.perModel.isEmpty())
        assertEquals(0L, changed.outputTokens)
        assertFalse(changed.tokenComponentsAvailable)
    }

    @Test fun `device rolling totals do not count an old today twice`() {
        val yesterday = LocalDate.now().minusDays(1)
        val device = DeviceUsage(id = "test", hostname = "test", platform = "windows", osName = "", osVersion = "", receivedAt = "", ageMs = null,
            stale = true, syncUploadIntervalMs = null, historyAvailable = true, trackedClients = emptyList(),
            updatedAt = "${yesterday}T12:00:00Z", periods = mapOf("today" to UsagePeriod(totalTokens = 42)),
            history = HubHistory(daily = listOf(HistoryPoint(yesterday.toString(), 42, 0.0))))
        assertEquals(42L, DashboardPeriod.Last7.usage(device).totalTokens)
    }

    @Test fun `missing attribution cannot invent a model change`() {
        val history = (1L..14).map { HistoryPoint(today.minusDays(it).toString(), 10, 0.0,
            perModel = if (it <= 7) mapOf("model" to HistoryAttribution(tokens = 10)) else emptyMap()) }
        assertTrue(compareUsage(history, today).complete)
        assertTrue(compareUsage(history, today).drivers(byModel = true).isEmpty())
    }
}
