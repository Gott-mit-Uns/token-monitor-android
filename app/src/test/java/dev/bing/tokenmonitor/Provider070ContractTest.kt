package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.Locale

/** Fixed v0.70 producer contract; never derives accounting or provider credentials. */
class Provider070ContractTest {
    private val now = Instant.parse("2026-10-14T08:00:00Z")
    private fun obj(value: String) = Json.parseToJsonElement(value).jsonObject
    private fun provider(amount: String = "12.34", currency: String = "USD") = obj("""{
      "provider":"minimax","status":"ok","updatedAt":"2026-10-14T07:59:00Z",
      "accountLabel":"Pay-as-you-go","source":"api",
      "windows":[{"kind":"billing","metric":"credits","label":"Balance","remaining":$amount,"currency":"$currency"}]
    }""")
    private fun stats(providers: List<JsonObject>) = buildJsonObject {
        put("periods", obj("""{"today":{"totalTokens":900,"costUsd":"0.125","unpricedTokens":7,
          "clients":{"catpaw":400,"codex":300,"future-client":100},
          "models":{"public-model":500},"clientModels":{"catpaw":{"public-model":400}},
          "sessions":{"public-catpaw":{"client":"catpaw","sessionId":"public-catpaw","totalTokens":400,"model":"public-model","title":"Public CatPaw title"}}}}"""))
        put("limits", buildJsonObject { put("providers", JsonArray(providers)) })
        put("futureContract", obj("""{"schema":"future-public-version","amount":4.567800}"""))
    }

    @Test fun nativeBalancesAreSeparateAmountsWithoutInventedQuotaOrCurrencyConversion() {
        val source = stats(listOf(provider("12.34", "CNY"), provider("56.78", "USD")))
        val parsed = HubParser.parse(source.toString())
        for (used in listOf(false, true)) {
            val rows = LimitDisplay.groups(parsed.limits).map { LimitProviderLayout.project(LimitProviderLayout.record(it), used).single() }
            assertEquals(listOf("¥12.34", "$56.78"), rows.map { it.value })
            assertTrue(rows.all { it.percent == null && it.motion == null && it.noReset && it.item == "credits" })
            assertEquals(listOf("12.34", "56.78"), parsed.limits.map { it.amount })
        }
        val widget = quotaWidgetAccounts(parsed, LimitDisplayPreferences(showUsed = true), Locale.US, now)
        assertEquals(2, widget.size)
        assertEquals(listOf("Balance: ¥12.34", "Balance: $56.78"), widget.map { it.lines.single() })
        assertTrue(widget.none { it.stale })
        assertEquals(900L, parsed.periods.getValue("today").tokens)
        assertEquals("0.125", parsed.periods.getValue("today").cost)
        assertEquals(7L, parsed.periods.getValue("today").unpriced)
    }

    @Test fun missingInvalidAndUnknownMoneyNeverBecomeZeroOrHealthyPercentages() {
        val windows = listOf(
            provider("null"), provider("\"not-a-number\""), provider("\"NaN\""), provider("\"Infinity\""),
            provider("12.34", ""), provider("12.34", "future_money"), provider("12.34", "EUR")
        )
        assertEquals(listOf("—", "—", "—", "—", "—", "—", "EUR 12.34"),
            windows.map { LimitProviderLayout.project(it).single().value })
        windows.forEach { assertNull(LimitProviderLayout.project(it).single().percent) }
        val missing = obj("""{"provider":"minimax","status":"ok","windows":[{"kind":"billing","metric":"credits","label":"Balance","currency":"CNY"}]}""")
        assertEquals("—", LimitProviderLayout.project(missing).single().value)
        val widget = quotaWidgetAccounts(HubParser.parse(stats(listOf(missing)).toString()), LimitDisplayPreferences(), Locale.US, now)
        assertEquals(listOf("Balance: Quota unavailable"), widget.single().lines)
        assertEquals("$0.00", LimitProviderLayout.project(provider("0")).single().value)
        val unknown = obj("""{"provider":"minimax","status":"future-status","windows":[{"kind":"future-window","metric":"future-metric"}]}""")
        assertTrue(LimitProviderLayout.project(unknown).isEmpty())
        assertEquals("Error", LimitProviderLayout.status("future-status"))
    }

    @Test fun oldTokenPlanRetainsBothPeriodsAndNewCreditsVisibilityIsLocalOnly() {
        val old = obj("""{"provider":"minimax","status":"ok","accountLabel":"Token Plan","windows":[
          {"kind":"session","remainingPercent":62,"label":"5-hour"},{"kind":"weekly","remainingPercent":84,"label":"Weekly"}]}""")
        assertEquals(listOf("62% left", "84% left"), LimitProviderLayout.project(old).map { it.value })
        assertEquals(listOf("38% used", "16% used"), LimitProviderLayout.project(old, true).map { it.value })
        val current = provider()
        assertTrue(LimitPreferences.filter(LimitProviderLayout.project(current), listOf("credits")).isEmpty())
        assertEquals("12.34", current.getValue("windows").jsonArray.single().jsonObject.getValue("remaining").jsonPrimitive.content)
        assertTrue(HubParser.parse("""{"periods":{},"limits":{"providers":[]}}""").limits.isEmpty())
    }

    @Test fun fullHttpSseAndFreshnessPreserveNewProviderOptionalAndUnknownFields() {
        val source = stats(listOf(provider("12.3400", "CNY")))
        val http = HubJsonProjection.stats(source.toString().byteInputStream())
        val stream = HubJsonProjection.streamStats("""{"stats":$source}""".byteInputStream())
        val fromHttp = HubParser.parse(http)
        assertEquals(fromHttp, HubParser.parse(stream))
        val root = obj(http.body)
        assertEquals(source["futureContract"], root["futureContract"])
        assertEquals("12.3400", fromHttp.limits.single().amount)
        val merged = StreamMerge.applyStats(root, "freshness", obj("""{
          "updatedAt":"2026-10-14T08:00:00Z","limits":{"updatedAt":"later","providers":[]},"periods":{}
        }"""))!!
        val fresh = HubParser.applyFreshness(fromHttp, merged)
        assertSame(fromHttp.limits, fresh.limits)
        assertSame(fromHttp.periods, fresh.periods)
        assertEquals(source["futureContract"], merged["futureContract"])
        assertEquals(fresh, HubParser.parse(merged.toString()))
        val restored = HubParser.parse(merged.toString())
        assertEquals("¥12.34", LimitProviderLayout.project(LimitProviderLayout.record(restored.limits)).single().value)
    }

    @Test fun catPawUsesHubCountersAndExistingChoicesWithoutRecalculatingTotals() {
        val period = HubParser.parse(stats(emptyList()).toString()).periods.getValue("today")
        assertEquals(900L, period.tokens)
        assertEquals(400L, period.tools["catpaw"])
        assertEquals(100L, period.tools["future-client"])
        assertEquals("catpaw", period.sessions.single().tool)
        assertEquals("Public CatPaw title", period.sessions.single().label)
        assertEquals("CatPaw", desktopToolLabels["catpaw"])
        val previous = DisplayPreferences(hiddenTools = setOf("claude"), pinnedTools = listOf("codex"), toolOrder = listOf("codex", "hermes"))
        val changed = ToolDisplay.toggleHidden(previous, "CatPaw")
        assertEquals(previous.hiddenTools + "catpaw", changed.hiddenTools)
        assertEquals(previous.pinnedTools, changed.pinnedTools)
        assertEquals(previous.toolOrder, changed.toolOrder)
        assertTrue(ToolDisplay.usageRows(period, changed).none { it.first == "catpaw" })
        assertEquals(900L, period.tokens)
        assertEquals("catpaw", ToolDisplay.normalizedOrder(emptyList()).last())
    }
}
