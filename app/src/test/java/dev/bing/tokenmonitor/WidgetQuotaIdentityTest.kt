package dev.bing.tokenmonitor

import java.time.Instant
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class WidgetQuotaIdentityTest {
    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private fun snapshot(records: String) = HubParser.parse("""{"periods":{},"limits":{"providers":$records}}""")
    private fun record(value: Int, source: String = "") = """{"provider":"codex","status":"ok","accountKey":"fixture-same-account","accountEmail":"public@example.invalid","updatedAt":"2026-10-14T11:59:00Z"${if(source.isNotEmpty()) ",\"sourceDeviceId\":\"$source\"" else ""},"windows":[{"kind":"session","remainingPercent":$value}]}"""
    @Test fun automaticDoesNotDuplicateTheLastLegacyRecordAndCustomChoosesTheRightRecord() {
        val source = snapshot("[${record(10)},${record(90)}]")
        val selections = widgetQuotaSelections(source)
        assertEquals(2, selections.map { it.id }.distinct().size)
        assertEquals(widgetQuotaAccountId(selections[0].windows), widgetQuotaAccountId(selections[1].windows))
        val auto = selectedQuotaWidgetAccounts(source, LimitDisplayPreferences(), Locale.US, now, WidgetConfiguration())
        assertEquals(2, auto.size)
        assertTrue(auto[0].lines.single().contains("10% left"))
        assertTrue(auto[1].lines.single().contains("90% left"))
        val custom = WidgetConfiguration(quotaAutomatic = false, quotaAccounts = listOf(selections[1].id))
        assertEquals(90.0, widgetQuotaGroups(source, custom, now).single().first().remaining!!, 0.0)
        assertTrue(selectedQuotaWidgetAccounts(source, LimitDisplayPreferences(), Locale.US, now, custom).single().lines.single().contains("90% left"))
        assertEquals(selections.map { it.id }, widgetQuotaSelections(snapshot("[${record(20)},${record(80)}]")).map { it.id })
        assertTrue(widgetQuotaGroups(snapshot("[${record(10)}]"), custom, now).isEmpty())
    }
    @Test fun sourceDisambiguationSurvivesReorderingAndChangesInReadings() {
        val original = snapshot("[${record(10,"fixture-source-a")},${record(90,"fixture-source-b")}] ")
        val selected = widgetQuotaSelections(original).first { it.windows.first().presentation.sourceDevice == "fixture-source-b" }.id
        val custom = WidgetConfiguration(quotaAutomatic = false, quotaAccounts = listOf(selected))
        val reordered = snapshot("[${record(70,"fixture-source-b")},${record(30,"fixture-source-a")}] ")
        val row = widgetQuotaGroups(reordered, custom, now).single().first()
        assertEquals("fixture-source-b", row.presentation.sourceDevice)
        assertEquals(70.0, row.remaining!!, 0.0)
        assertEquals(selected, widgetQuotaSelections(reordered).first().id)
        assertTrue(widgetQuotaGroups(snapshot("[${record(30,"fixture-source-a")}]"), custom, now).isEmpty())
    }
    @Test fun actualMimoProductLabelsSeparateOneAccountAndPreserveUnavailableSelectedLane() {
        val console = """{"provider":"mimo","status":"ok","accountKey":"fixture-mimo-account","accountLabel":"Console","accountEmail":"public@example.invalid","updatedAt":"2026-10-14T11:59:00Z","balance":{"amount":10,"currency":"CNY"},"windows":[{"kind":"billing","metric":"credits","remaining":10,"currency":"CNY"}]}"""
        val membership = """{"provider":"mimo","status":"ok","accountKey":"fixture-mimo-account","accountLabel":"Desktop Membership","accountEmail":"public@example.invalid","updatedAt":"2026-10-14T11:59:00Z","windows":[{"kind":"session","remainingPercent":90},{"kind":"weekly","remainingPercent":60}]}"""
        val source = snapshot("[$console,$membership]")
        val selections = widgetQuotaSelections(source)
        assertNotEquals(selections[0].id, selections[1].id)
        val custom = WidgetConfiguration(quotaAutomatic = false, quotaAccounts = listOf(selections[1].id))
        val reordered = snapshot("[$membership,$console]")
        assertEquals("Desktop Membership", widgetQuotaGroups(reordered, custom, now).single().first().account)
        val auto = selectedQuotaWidgetAccounts(source, LimitDisplayPreferences(), Locale.US, now, WidgetConfiguration())
        assertTrue(auto[0].lines.any { it.contains("¥10.00") })
        assertTrue(auto[1].lines.any { it.contains("90% left") })
        val unavailable = membership.replace("\"status\":\"ok\"", "\"status\":\"unavailable\"").replace("\"windows\":[{\"kind\":\"session\",\"remainingPercent\":90},{\"kind\":\"weekly\",\"remainingPercent\":60}]", "\"windows\":[]")
        assertEquals("Desktop Membership", widgetQuotaGroups(snapshot("[$console,$unavailable]"), custom, now).single().first().account)
    }
    @Test fun existingUniqueHashRemainsAnAliasOnlyWhileItIsUnambiguous() {
        val unique = snapshot("[${record(10,"fixture-source-a")}] ")
        val originalId = widgetQuotaAccountId(LimitDisplay.groups(unique.limits).single())
        val custom = WidgetConfiguration(quotaAutomatic = false, quotaAccounts = listOf(originalId))
        assertEquals(10.0, widgetQuotaGroups(unique, custom, now).single().first().remaining!!, 0.0)
        val duplicate = snapshot("[${record(10,"fixture-source-a")},${record(90,"fixture-source-b")}] ")
        assertTrue(widgetQuotaGroups(duplicate, custom, now).isEmpty())
        assertNull(resolveWidgetQuotaSelection(widgetQuotaSelections(duplicate), originalId))
    }
    @Test fun differingWindowSchemaCanDisambiguateLegacyRecordsWhenSourceIdentityIsAbsent() {
        val daily = record(10).replace("\"kind\":\"session\"", "\"kind\":\"daily\"")
        val weekly = record(90).replace("\"kind\":\"session\"", "\"kind\":\"weekly\"")
        val source = snapshot("[$daily,$weekly]")
        val selected = widgetQuotaSelections(source)[1].id
        val custom = WidgetConfiguration(quotaAutomatic = false, quotaAccounts = listOf(selected))
        assertEquals("weekly", widgetQuotaGroups(snapshot("[$weekly,$daily]"), custom, now).single().first().kind)
        assertEquals("weekly", widgetQuotaGroups(snapshot("[$weekly]"), custom, now).single().first().kind)
        assertTrue(widgetQuotaGroups(snapshot("[$daily]"), custom, now).isEmpty())
    }
    @Test fun exactlyIdenticalLegacyRecordsRetainTwoIdsAndTwoIndependentCustomChoices() {
        val source = snapshot("[${record(70)},${record(70)}]")
        val selections = widgetQuotaSelections(source)
        assertEquals(2, selections.size); assertNotEquals(selections[0].id, selections[1].id)
        val custom = WidgetConfiguration(quotaAutomatic = false, quotaAccounts = selections.map { it.id })
        val chosen = selectedWidgetQuotaSelections(source, custom, now)
        assertEquals(selections.map { it.id }, chosen.map { it.id })
        val displayed = selectedQuotaWidgetAccounts(source, LimitDisplayPreferences(), Locale.US, now, custom)
        assertEquals(2, displayed.size); assertNotEquals(displayed[0].id, displayed[1].id)
        assertTrue(displayed.all { it.lines.single().contains("70% left") })
        val unique = snapshot("[${record(70)}]")
        assertTrue(widgetQuotaGroups(unique, custom, now).isEmpty())
    }
}
