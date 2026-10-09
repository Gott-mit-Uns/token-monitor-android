package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.storage.DesktopPreferences
import io.github.theminionooo.tokenmonitor.data.storage.DesktopOptions
import io.github.theminionooo.tokenmonitor.domain.*
import io.github.theminionooo.tokenmonitor.widget.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class UnpricedAlignmentTest {
    private fun usage() = HubProtocolParser.decodeStats("""{"updatedAt":"2026-10-09T12:00:00Z","periods":{"today":{"totalTokens":100,"costUsd":1.25,"unpricedTokens":40,"clients":{"codex":100},"clientCosts":{"codex":1.25},"clientUnpricedTokens":{"codex":40},"models":{"gpt-test":100},"modelUnpricedTokens":{"gpt-test":40},"projects":{"p":{"totalTokens":100,"costUsd":1.25,"unpricedTokens":40}},"sessions":{"s":{"client":"codex","totalTokens":100,"usageSource":"codex-dots-local","usageCoverage":"observed-only"}}}}}""")
    @Test fun `project price coverage survives parsing and privacy export`() {
        val stats = usage(); val project = stats.periods.getValue("today").projects.single()
        assertEquals(40L, project.unpricedTokens)
        val export = exportUsage(HubSnapshot(stats = stats), "json")
        assertTrue(export.contains("\"unpricedTokens\":40"))
        assertFalse(export.contains("projectLabel"))
    }
    @Test fun `legacy zero cost is priced unless Hub explicitly reports missing prices`() {
        val p = HubProtocolParser.decodeStats("""{"periods":{"today":{"totalTokens":100,"costUsd":0}}}""").periods.getValue("today")
        assertEquals(0L,p.unpricedTokens);assertEquals(formatMoney(0.0), formatUsageCost(0.0,p.unpricedTokens))
        assertEquals("— (?)",formatUsageCost(0.0,100,true))
        assertEquals("${formatMoney(1.25)} + ?",formatUsageCost(1.25,40,true))
        assertTrue(formatUsageCost(1.25,123456).contains("123,456"))
    }
    @Test fun `classified missing prices never exceed total or own token counts`() {
        val p=HubProtocolParser.decodeStats("""{"periods":{"today":{"totalTokens":100,"unpricedTokens":999,"clients":{"codex":60,"claude":40},"clientUnpricedTokens":{"codex":999,"claude":999,"ghost":999},"models":{"m":100},"modelUnpricedTokens":{"m":-1},"clientModels":{"codex":{"m":60}},"clientModelUnpricedTokens":{"codex":{"m":999},"ghost":{"m":999}}}}}""").periods.getValue("today")
        assertEquals(100L,p.unpricedTokens);assertEquals(mapOf("codex" to 60L,"claude" to 40L),p.clientUnpricedTokens)
        assertTrue(p.modelUnpricedTokens.isEmpty());assertEquals(mapOf("m" to 60L),p.clientModelUnpricedTokens["codex"])
        assertTrue(p.clientModelUnpricedTokens["ghost"].orEmpty().isEmpty())
    }
    @Test fun `same token and cost with changed pricing drops stale history attribution`() {
        val old=HistoryPoint("2026-10-09",100,1.25,unpricedTokens=40,perClient=mapOf("codex" to HistoryAttribution(100,1.25,unpricedTokens=40)))
        val changed=old.copy(unpricedTokens=0,perClient=emptyMap())
        assertTrue(mergeUsageHistory(listOf(old),listOf(changed)).single().perClient.isEmpty())
        assertEquals(old.perClient,mergeUsageHistory(listOf(old),listOf(old.copy(perClient=emptyMap()))).single().perClient)
    }
    @Test fun `current history rolling range trends and deck retain missing pricing`() {
        val snapshot=HubSnapshot(stats=usage());val history=usageHistory(snapshot)
        assertEquals(40L,history.single().unpricedTokens);assertEquals(40L,history.single().perModel.getValue("gpt-test").unpricedTokens)
        assertEquals(40L,aggregateHistory(snapshot,LocalDate.of(2026,10,3)).unpricedTokens)
        assertEquals(40L,prepareTrends(history,true,null,LocalDate.of(2026,10,9)).days.single().unpricedTokens)
        val deck=prepareWidgetDeck(snapshot,WidgetSession(),0,ZoneOffset.UTC,Locale.US)
        assertEquals(40L,deck.tools.single().unpricedTokens);assertEquals(40L,deck.models.single().unpricedTokens)
        assertTrue(widgetDeckPageSummary(WidgetDeckPage.Activity,deck).contains("40"))
    }
    @Test fun `Dots requires exact tool source and coverage while generic observed remains possible`() {
        val s=usage().periods.getValue("today").sessions.single()
        assertTrue(isDotsObservedOnly(s));assertTrue(sessionMetricLabels(s,0).contains("Dots"))
        assertFalse(isDotsObservedOnly(s.copy(client="claude")));assertFalse(isDotsObservedOnly(s.copy(usageSource="")));assertFalse(isDotsObservedOnly(s.copy(usageCoverage="")))
        assertFalse(sessionMetricLabels(s.copy(client="claude"),0).contains("Dots"))
    }
}
