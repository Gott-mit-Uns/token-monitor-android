package io.github.theminionooo.tokenmonitor.widget

import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

class WidgetDeckAccessibilityTest {
    private val now = Instant.parse("2026-09-04T15:35:10Z").toEpochMilli()
    private fun data(): WidgetDeckData {
        fun asset(name: String) = checkNotNull(javaClass.classLoader?.getResourceAsStream("protocol/v0.54.0/$name")).bufferedReader().use { it.readText() }
        val snapshot = HubProtocolParser.decodeSnapshot(asset("health.json"), asset("stats.json"), asset("devices.json"), asset("history.json"), asset("subscriptions.json"), now)
        return prepareWidgetDeck(snapshot, WidgetSession(), now, ZoneOffset.UTC, Locale.US)
    }

    @Test fun `spoken pages expose their quota breakdown and calendar content`() {
        val data = data()
        val descriptions = WidgetDeckPage.entries.map { widgetDeckPageSummary(it, data) }
        assertEquals(4, descriptions.distinct().size)
        assertTrue(descriptions[1].contains("% left"))
        assertTrue(descriptions[2].contains("Codex:"))
        assertTrue(descriptions[2].contains(data.models.first().name))
        assertTrue(descriptions[3].contains("3 recorded days; 4 days have no observation"))
        data.snapshot!!.stats.limits.providers.forEach { account ->
            if (account.accountEmail.isNotBlank()) descriptions.forEach { assertFalse(it.contains(account.accountEmail)) }
        }
    }

    @Test fun `empty and unavailable page content is described without invented totals`() {
        val empty = prepareWidgetDeck(null, WidgetSession(), now, ZoneOffset.UTC, Locale.US)
        WidgetDeckPage.entries.forEach { assertEquals(empty.emptyMessage, widgetDeckPageSummary(it, empty)) }
        assertEquals("No quota windows reported.", widgetDeckPageSummary(WidgetDeckPage.Limits, data().copy(limitGroups = emptyList())))
        assertEquals("No activity history reported.", widgetDeckPageSummary(WidgetDeckPage.Activity, data().copy(history = emptyList())))
    }
}
