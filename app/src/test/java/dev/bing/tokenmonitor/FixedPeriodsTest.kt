package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class FixedPeriodsTest {
    @Test fun generatedDesktopCasesMatchTotalsComponentsToolsModelsAndDeviceDates() {
        val fixture = javaClass.classLoader!!.getResourceAsStream("fixed-period-parity.json")!!.bufferedReader().use { it.readText() }
        for (raw in Json.parseToJsonElement(fixture).jsonArray) {
            val row = raw.jsonObject
            val name = row.getValue("name").jsonPrimitive.content
            val snapshot = HubParser.parse(row.getValue("stats").toString())
            val histories = DeviceHistoryParser.parse(DeviceHistoryParser.project(row.getValue("response").toString(), FixedPeriods.signature(snapshot)))
            val value = FixedPeriods.evaluate(row.getValue("selection").jsonPrimitive.content, snapshot, histories,
                Instant.parse(row.getValue("now").jsonPrimitive.content), Locale.forLanguageTag(row.getValue("locale").jsonPrimitive.content))
            val expected = row.getValue("expected").jsonObject
            val ready = expected.getValue("status").jsonPrimitive.content == "ready"
            assertEquals(name, ready, value.period != null)
            if (!ready) { assertNotNull(name, value.issue); continue }
            val wanted = HubParser.parse(buildJsonObject { put("periods", buildJsonObject { put("fixed", expected.getValue("period")) }) }.toString()).periods.getValue("fixed")
            assertEquals(name, wanted, value.period)
            assertEquals(name, expected.getValue("range").jsonObject.getValue("start").jsonPrimitive.content, value.range!!.start.toString())
            assertEquals(name, expected.getValue("range").jsonObject.getValue("end").jsonPrimitive.content, value.range.end.toString())
            val summary = expected.getValue("summary").jsonObject
            assertEquals(name, summary.getValue("activeDays").jsonPrimitive.int, value.activity!!.activeDays)
            assertEquals(name, summary.getValue("currentStreak").jsonPrimitive.int, value.activity.currentStreak)
            assertEquals(name, summary.getValue("activeTimeMs").jsonPrimitive.long, value.activity.activeTimeMs)
            assertEquals(name, summary.getValue("peakDayTokens").jsonPrimitive.long, value.activity.peakDayTokens)
            val devices = expected.getValue("devices").jsonArray
            assertEquals(name, devices.size, value.devices.size)
            for (device in devices) {
                val source = device.jsonObject
                val id = source.getValue("deviceId").jsonPrimitive.content
                val target = HubParser.parse(buildJsonObject { put("periods", buildJsonObject { put("fixed", source.getValue("period")) }) }.toString()).periods.getValue("fixed")
                assertEquals("$name/$id", target, value.devices[id])
                assertEquals("$name/$id", source.getValue("range").jsonObject.getValue("start").jsonPrimitive.content, value.deviceRanges[id]!!.start.toString())
            }
        }
    }
    @Test fun projectionStripsUnrelatedFieldsAndPreservesExplicitEmptyVsUnavailable() {
        val projected = DeviceHistoryParser.project("""{"devices":[{"deviceId":"public","historyAvailable":true,"history":{"daily":[]},"sessions":{"fixture":"unrelated"},"hostname":"not-needed"},{"deviceId":"unknown","historyAvailable":false}]}""", "public-signature")
        assertFalse(projected.contains("not-needed")); assertFalse(projected.contains("unrelated"))
        val value = DeviceHistoryParser.parse(projected)
        assertTrue(value.devices.first().available)
        assertEquals(emptyList<HistoryRow>(), value.devices.first().history!!.daily)
        assertFalse(value.devices.last().available)
        assertNull(value.devices.last().history)
    }
    @Test fun inventoryOrRevisionMismatchNeverYieldsPartialTotals() {
        val snapshot = HubParser.parse("""{"periods":{},"devices":[{"deviceId":"public"}],"deviceHistoryRevision":"first"}""")
        val histories = DeviceHistories(FixedPeriods.signature(snapshot), emptyList())
        assertEquals(FixedPeriodIssue.INVENTORY, FixedPeriods.evaluate("last7", snapshot, histories).issue)
        assertEquals(FixedPeriodIssue.HISTORY, FixedPeriods.evaluate("last7", snapshot.copy(deviceHistoryRevision = "second"), histories).issue)
    }
    @Test fun desktopLabelsSlotsAndInclusiveCalendarRanges() {
        assertEquals("DAY", FixedPeriods.label("today")); assertEquals("30D", FixedPeriods.label("last30"))
        assertEquals("month", FixedPeriods.slot("week")); assertEquals("today", FixedPeriods.slot("today"))
        val friday = LocalDate.of(2026,10,9)
        assertEquals(LocalDate.of(2026,10,5), FixedPeriods.range("week",friday,Locale.CHINESE).start)
        assertEquals(LocalDate.of(2026,10,4), FixedPeriods.range("week",friday,Locale.US).start)
        assertEquals(LocalDate.of(2026,10,3), FixedPeriods.range("last7",friday,Locale.UK).start)
        assertEquals(LocalDate.of(2026,9,10), FixedPeriods.range("last30",friday,Locale.UK).start)
    }
}
