package dev.bing.tokenmonitor

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class WidgetAlignmentTest {
    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private val snapshot = HubParser.parse("""{"updatedAt":"2026-10-14T11:59:00Z","periods":{
        "today":{"totalTokens":27800000,"costUsd":14.86,"clients":{"codex":22300000,"claude":5500000},"models":{"GPT-5.6":20900000,"MiMo":2900000}},
        "month":{"totalTokens":61200000,"costUsd":237.42},"allTime":{"totalTokens":180000000,"costUsd":620.15}}}""")
    @Test fun exactMacPeriodMappingCompactUnitsAndCurrencyRemainIndependentFromAppMonthMode() {
        val preferences = DisplayPreferences(monthMode = "last30", currency = "CNY", currencyRates = mapOf("CNY" to "7"))
        assertEquals(27_800_000L, widgetUsage(snapshot, 1, "today").tokens)
        assertEquals(61_200_000L, widgetUsage(snapshot, 1, "month").tokens)
        assertEquals(180_000_000L, widgetUsage(snapshot, 1, "allTime").tokens)
        assertNull(widgetUsage(snapshot, 1, "last30").tokens)
        assertEquals("27.8M", widgetCompactTokens(27_800_000))
        assertEquals("2780万", widgetCompactTokens(27_800_000, true, Locale.SIMPLIFIED_CHINESE))
        assertEquals("1亿", widgetCompactTokens(99_999_999, true, Locale.SIMPLIFIED_CHINESE))
        assertEquals("¥104.02", widgetCost(widgetUsage(snapshot, 1), preferences))
        assertEquals("$0.00", widgetCost(WidgetUsage(0, "0", false, null, 1), DisplayPreferences()))
        assertEquals("—", widgetCost(WidgetUsage(0, "0", true, null, 1), preferences))
        assertEquals("—", widgetCompactTokens(null))
    }
    @Test fun sevenLocalDaysAndNaturalMonthUseMaximumHistoryLiveWithoutDoubleCounting() {
        val history = HistoryParser.parse("""{"daily":[{"date":"2026-10-14","tokens":20000000},{"date":"2026-10-13","tokens":9000000}],"monthly":[{"month":"2026-10","tokens":50000000}]}""")
        val day = widgetTrend(snapshot, history, "today", now, ZoneId.of("Asia/Shanghai"))
        assertEquals(7, day.size)
        assertEquals("2026-10-08", day.first().date)
        assertNull(day.first().tokens)
        assertEquals(27_800_000L, day.last().tokens)
        val month = widgetTrend(snapshot, history, "month", now, ZoneId.of("Asia/Shanghai"))
        assertEquals(14, month.size); assertEquals("2026-10-01", month.first().date)
        val total = widgetTrend(snapshot, history, "allTime", now, ZoneId.of("Asia/Shanghai"))
        assertEquals(61_200_000L, total.single().tokens)
        assertEquals("2026-10", total.single().date)
    }
    @Test fun midnightUsesLocalZoneAndSourceFreshnessNeverReplacesUnknownWithZero() {
        val at = Instant.parse("2026-10-14T23:59:00Z")
        assertEquals("2026-10-15", widgetTrend(snapshot, null, "today", at, ZoneId.of("Asia/Shanghai")).last().date)
        assertEquals("2026-10-14", widgetTrend(snapshot, null, "today", at, ZoneId.of("America/Los_Angeles")).last().date)
        assertFalse(widgetStale(widgetUsage(snapshot, now.minusSeconds(60).toEpochMilli()), now))
        assertTrue(widgetStale(widgetUsage(snapshot, now.minusSeconds(1201).toEpochMilli()), now))
        assertTrue(widgetStale(WidgetUsage(0, "0", false, null, now.toEpochMilli()), now))
        assertTrue(widgetStale(widgetUsage(snapshot, now.plusSeconds(1).toEpochMilli()), now))
    }
    @Test fun breakdownPercentUsesSameDimensionDenominatorAndKeepsFieldsSeparate() {
        val rows = widgetBreakdown(snapshot.periods["today"], "tools")
        assertEquals(listOf("codex", "claude"), rows.map { it.id })
        assertEquals(22_300_000L, rows.first().tokens)
        assertEquals(22_300_000.0 / 27_800_000 * 100, rows.first().share!!, 0.000001)
        val models = widgetBreakdown(snapshot.periods["today"], "models")
        assertEquals(20_900_000.0 / 23_800_000 * 100, models.first().share!!, 0.000001)
        assertTrue(widgetBreakdown(null, "tools").isEmpty())
    }
    @Test fun automaticMacRankingAndCustomAccountsCannotSilentlySubstituteAnotherAccount() {
        val data = HubParser.parse("""{"periods":{},"limits":{"providers":[
        {"provider":"claude","status":"notConfigured","windows":[]},
        {"provider":"codex","status":"ok","accountEmail":"old@example.invalid","updatedAt":"2026-10-14T10:00:00Z","windows":[{"kind":"session","remainingPercent":0}]},
        {"provider":"codex","status":"ok","accountEmail":"fresh@example.invalid","updatedAt":"2026-10-14T11:59:00Z","windows":[{"kind":"session","remainingPercent":90}]},
        {"provider":"gemini","status":"ok","updatedAt":"2026-10-14T11:59:00Z","windows":[{"kind":"daily","remainingPercent":50}]}]}}""")
        val picked = widgetQuotaGroups(data, WidgetConfiguration(), now)
        assertEquals("fresh@example.invalid", picked[0].first().presentation.identity.email)
        assertEquals("gemini", picked[1].first().provider)
        val selected = widgetQuotaAccountId(LimitDisplay.groups(data.limits)[1])
        assertEquals(64, selected.length); assertFalse(selected.contains("old"))
        val custom = WidgetConfiguration(quotaAutomatic = false, quotaAccounts = listOf(selected, "0".repeat(64)))
        assertEquals(1, widgetQuotaGroups(data, custom, now).size)
        val vanished = data.copy(limits = data.limits.filter { it.presentation.identity.email != "old@example.invalid" })
        assertTrue(widgetQuotaGroups(vanished, custom, now).isEmpty())
        assertEquals(2, WidgetConfiguration(quotaAccounts = listOf(selected, "0".repeat(64), "1".repeat(64))).normalized().quotaAccounts.size)
    }
    @Test fun eachModeOpensItsActualPageWithoutInferringDeviceScope() {
        assertEquals(HubPage.HOME, WidgetOpenRequest(mode = "dashboard").page)
        assertEquals(HubPage.LIMITS, WidgetOpenRequest(mode = "quota").page)
        assertEquals(HubPage.TOOLS, WidgetOpenRequest(mode = "tools").page)
        assertEquals(HubPage.MODELS, WidgetOpenRequest(mode = "models").page)
        assertEquals(HubPage.TRENDS, WidgetOpenRequest(mode = "activity").page)
        assertEquals("today", WidgetConfiguration(period = "last30").normalized().period)
    }
    @Test fun cachedAliasesAndUnsafeLabelsUseActualHubSemanticsWithoutExposingPaths() {
        val aliases = ModelNames(mapOf("GPT" to "GPT-6.1"), "off")
        val source = HubParser.parse("""{"periods":{"today":{"models":{"GPT":10,"GPT-6.1":20,"https://private.invalid/path":99}}}}""")
        val rows = widgetBreakdown(source.periods["today"], "models", aliases)
        assertEquals(1, rows.size); assertEquals("GPT-6.1", rows.single().label)
        assertEquals(30L, rows.single().tokens)
        assertNull(widgetSafeLabel("/private/path")); assertNull(widgetSafeLabel("person@example.invalid"))
        val freshRootStaleDevices = HubParser.parse("""{"updatedAt":"2026-10-14T11:59:00Z","periods":{"today":{"totalTokens":10}},"devices":[{"deviceId":"one","receivedAt":"2026-10-14T11:58:00Z","stale":true}]}""")
        assertTrue(widgetStale(widgetUsage(freshRootStaleDevices, now.toEpochMilli()), now))
        assertEquals("2026-10-14T11:58:00Z", widgetUsage(freshRootStaleDevices, now.toEpochMilli()).updated)
    }
    @Test fun macSameDisplayNameNormalizationMergesCaseAndNfkcAfterAliasesWithoutDoubleCounting() {
        val source = HubParser.parse("""{"periods":{"today":{"models":{"GPT":10,"gpt":20,"ＧＰＴ":30,"other":40}}}}""")
        val rows = widgetBreakdown(source.periods["today"], "models")
        assertEquals(2, rows.size)
        assertEquals(60L, rows.first().tokens)
        assertEquals(60.0, rows.first().share!!, 0.000001)
        assertEquals("gpt", java.text.Normalizer.normalize(rows.first().label, java.text.Normalizer.Form.NFKC).lowercase(Locale.US))
        val alias = ModelNames(mapOf("GPT" to "Canonical"), "off")
        val aliased = widgetBreakdown(source.periods["today"], "models", alias)
        assertEquals(100L, aliased.sumOf { it.tokens!! })
        assertEquals(30L, aliased.first { it.label == "Canonical" }.tokens)
        assertEquals(30L, aliased.first { it.label == "ＧＰＴ" }.tokens)
    }
    @Test fun overflowingNormalizedModelCountStaysUnavailableAndNeverWrapsOrClamps() {
        val source = HubParser.parse("""{"periods":{"today":{"models":{"GPT":9223372036854775807,"gpt":1,"other":10}}}}""")
        val rows = widgetBreakdown(source.periods["today"], "models")
        assertEquals(2, rows.size)
        assertNull(rows.first().tokens); assertNull(rows.first().share)
        assertEquals(10L, rows[1].tokens); assertNull(rows[1].share)
        assertEquals("—", widgetCompactTokens(rows.first().tokens))
    }
    @Test fun unavailableQuotaAccountsTieBySnapshotOrderRegardlessOfFreshTimestamp() {
        val data = HubParser.parse("""{"periods":{},"limits":{"providers":[
          {"provider":"codex","status":"unauthorized","accountEmail":"first@example.invalid","updatedAt":"2026-10-14T10:00:00Z","windows":[{"kind":"weekly","remainingPercent":10}]},
          {"provider":"codex","status":"unauthorized","accountEmail":"second@example.invalid","updatedAt":"2026-10-14T11:59:00Z","windows":[{"kind":"weekly","remainingPercent":90}]}]}}""")
        val rows = widgetQuotaGroups(data, WidgetConfiguration(), now)
        assertEquals("first@example.invalid", rows.first().first().presentation.identity.email)
    }
}
