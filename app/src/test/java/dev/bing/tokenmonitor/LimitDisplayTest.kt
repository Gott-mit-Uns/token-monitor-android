package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class LimitDisplayTest {
    @Test fun projectedBalanceAndUsageCachesAreNotEmptyCatalogAccounts() {
        val balances=listOf("giftBalance","cashBalance","planUsed","planLimit","planPercent","todaySpend","weekSpend","allTimeSpend","requestCount")
            .map {key->buildJsonObject {put("balance",buildJsonObject {put(key,0);put("currency","USD")})}}
        val extra=listOf(
            """{"balance":{"tranches":[{"amount":0,"currency":"USD"}]}}""",
            """{"balance":{"planStatus":"expired"}}""",
            """{"balance":{"quotaGroup":"Public group"}}""",
            """{"balance":{"expiresAt":"2026-11-01T00:00:00Z"}}""",
            """{"usageSummary":{"totalTokens":0,"requests":0}}""",
            """{"resetCredits":{"availableCount":0}}""",
            """{"balanceUsd":0}"""
        ).map {Json.parseToJsonElement(it).jsonObject}
        val empty=listOf(
            """{"balance":{"currency":"USD"}}""",
            """{"balance":{"giftBalance":"unknown","cashBalance":null,"planUsed":{"count":1}}}""",
            """{"usageSummary":{"period":"202610"},"resetCredits":{}}"""
        ).map {Json.parseToJsonElement(it).jsonObject}
        val source=(balances+extra+empty).mapIndexed {index,body->JsonObject(body+mapOf("provider" to JsonPrimitive("cached-$index"),"status" to JsonPrimitive("noSyncedData")))}
        val all=parsed(JsonArray(source))
        assertEquals(all.take(balances.size+extra.size),LimitDisplay.usedAccounts(all))
        assertEquals(source.size,all.size)
    }
    @Test fun missingKindDoesNotDiscardLegacyNumericQuotaOrRealZeroReadings() {
        val all=parsed(Json.parseToJsonElement("""[
            {"provider":"legacy-percent","status":"noSyncedData","windows":[{"remainingPercent":0}]},
            {"provider":"legacy-remaining","status":"disabled","windows":[{"remaining":0}]},
            {"provider":"legacy-used","status":"notConfigured","windows":[{"used":1}]},
            {"provider":"legacy-limit","windows":[{"limit":10}]},
            {"provider":"legacy-used-percent","windows":[{"usedPercent":0}]},
            {"provider":"legacy-spend","status":"noSyncedData","balance":{"monthSpend":0},"windows":[]},
            {"provider":"legacy-unlimited","status":"noSyncedData","windows":[{"showMeter":false,"detail":"unlimited"}]},
            {"provider":"empty","status":"noSyncedData","windows":[]}
        ]""").jsonArray)
        val kept=LimitDisplay.usedAccounts(all)
        assertEquals(all.dropLast(1),kept)
        assertTrue(kept.all {it.kind==null})
        assertEquals(0.0,kept.first().remaining!!,0.0)
    }
    @Test fun unusedCatalogEntriesAreHiddenWithoutDroppingConfiguredErrorsZeroQuotaOrCachedData() {
        val all=HubParser.parse("""{"periods":{},"limits":{"providers":[
            {"provider":"claude","status":"notConfigured","windows":[]},
            {"provider":"gemini","status":"disabled","windows":[]},
            {"provider":"grok","status":"noSyncedData","windows":[]},
            {"provider":"codex","status":"ok","windows":[{"kind":"session","remainingPercent":0}]},
            {"provider":"cursor","status":"unauthorized","windows":[]},
            {"provider":"kiro","status":"noSyncedData","stale":true,"windows":[{"kind":"billing","used":1,"remaining":0}]},
            {"provider":"openrouter","status":"ok","balance":{"amount":0,"currency":"USD"},"windows":[]}
        ]}}""").limits
        val selected=LimitDisplay.usedAccounts(all)
        assertEquals(listOf("codex","cursor","kiro","openrouter"),LimitDisplay.groups(selected).map {it.first().provider})
        assertEquals(0.0,selected.first().remaining!!,0.0)
        assertEquals(7,LimitDisplay.groups(all).size)
        assertTrue(LimitDisplay.usedAccounts(all.take(3)).isEmpty())
        assertTrue(LimitDisplay.usedAccounts(emptyList()).isEmpty())
    }
    private fun fixture()=javaClass.classLoader!!.getResourceAsStream("limit-display-parity.json")!!.bufferedReader().use {Json.parseToJsonElement(it.readText()).jsonObject}
    private fun parsed(providers:JsonArray)=HubParser.parse(buildJsonObject {put("periods",buildJsonObject {put("today",buildJsonObject {})});put("limits",buildJsonObject {put("providers",providers)})}.toString()).limits
    private fun row(input:JsonObject):Limit {
        val provider=buildJsonObject {put("provider",input.getValue("provider"));input["balance"]?.let {put("balance",it)};put("windows",buildJsonArray {add(JsonObject(input.filterKeys {it !in setOf("provider","balance")}))})}
        return parsed(buildJsonArray {add(provider)}).single()
    }
    @Test fun providerWindowMoneyCountsDisplayDirectionsAndCreditsMetersMatchDesktopPureFunctions() {
        fixture().getValue("windowCases").jsonArray.forEach {raw ->
            val item=raw.jsonObject;val window=row(item.getValue("input").jsonObject);val showUsed=item.getValue("showUsed").jsonPrimitive.boolean
            val expected=item.getValue("output").jsonObject;val actual=LimitDisplay.windowText(window,showUsed)
            assertEquals(expected.getValue("value").jsonPrimitive.contentOrNull,actual.value)
            assertEquals(expected.getValue("detail").jsonPrimitive.content,actual.detail)
            assertEquals(expected.getValue("percentLeads").jsonPrimitive.boolean,actual.percentLeads)
            item["percent"]?.jsonPrimitive?.doubleOrNull?.let {assertEquals(it,LimitDisplay.percent(window,showUsed)!!,0.0000001)}
            assertEquals(item["creditsPercent"]?.jsonPrimitive?.doubleOrNull,if(window.metric=="credits") LimitDisplay.creditsPercent(window) else null)
            item["creditsMoney"]?.jsonPrimitive?.contentOrNull?.let {assertEquals(it,LimitDisplay.creditsMoney(window))}
        }
    }
    @Test fun accountTitlesAndMaskedCollisionsMatchDesktopAndEqualPlanRowsRemainSeparate() {
        fixture().getValue("accounts").jsonArray.forEach {raw ->
            val item=raw.jsonObject;val accounts=parsed(item.getValue("input").jsonArray)
            assertEquals(accounts.size,LimitDisplay.groups(accounts).size)
            val masked=item.getValue("mask").jsonPrimitive.boolean
            item.getValue("output").jsonArray.forEachIndexed {index,label -> assertEquals(label.jsonPrimitive.content,LimitDisplay.accountTitle(accounts[index],accounts,index,masked))}
        }
    }
    @Test fun unknownBucketRequiresCompleteCodexAttributionBeforeDiscardingMalformedCounterMaps() {
        fixture().getValue("periods").jsonArray.forEach {raw ->
            val item=raw.jsonObject
            val snapshot=HubParser.parse(buildJsonObject {put("periods",buildJsonObject {put("today",item.getValue("input"))})}.toString())
            val source=snapshot.periods.getValue("today").unknownModelSource
            assertEquals(item.getValue("source").jsonPrimitive.contentOrNull,source)
            assertEquals(if(source=="codex") "codex" else "dot",modelMark("unknown",source))
        }
        assertEquals("sigma",modelMark("future-custom-model",null));assertEquals("codex",modelMark("gpt-5",null))
        assertEquals("dot",modelMark(UsageAttribution.UNATTRIBUTED,"codex"))
    }
    @Test fun sourceLabelsAreProviderSpecificAndDoNotEchoArbitraryWireText() {
        fixture().getValue("sourceCases").jsonArray.forEach {raw ->
            val item=raw.jsonObject;val window=parsed(buildJsonArray {add(item)}).single()
            assertEquals(item.getValue("label").jsonPrimitive.content,LimitDisplay.source(window))
        }
    }
    @Test fun homeCompactWindowSelectionKeepsCanonicalCodexAndTightestAntigravityGroupsAndZedUnlimited() {
        fixture().getValue("compactInputs").jsonArray.forEach {raw ->
            val item=raw.jsonObject;val windows=parsed(buildJsonArray {add(item.getValue("input"))})
            val actual=LimitDisplay.compactWindows(windows);val expected=item.getValue("output").jsonArray
            assertEquals(expected.size,actual.size)
            expected.forEachIndexed {index,row -> val values=row.jsonObject
                assertEquals(values.getValue("label").jsonPrimitive.content,actual[index].label)
                assertEquals(values.getValue("value").jsonPrimitive.contentOrNull,actual[index].presentation.compactValue)
                assertEquals(values.getValue("resetDescription").jsonPrimitive.contentOrNull,actual[index].presentation.resetDescription)
            }
        }
    }
    @Test fun absentOrMalformedQuotaNeverBecomesZeroAndPrivacyAndModesLeaveRawRecordsIntact() {
        val raw="""{"periods":{"today":{}},"limits":{"providers":[{"provider":"codex","accountLabel":"Plus","accountEmail":"javis@example.com","windows":[{"kind":"weekly","remainingPercent":"bad","usedPercent":-1}]},{"provider":"codex","accountLabel":"Plus","accountEmail":"jonas@example.com","windows":[{"kind":"weekly","remainingPercent":75}]}]}}"""
        val snapshot=HubParser.parse(raw);assertNull(LimitDisplay.percent(snapshot.limits.first(),true));assertEquals(2,LimitDisplay.groups(snapshot.limits).size)
        val before=DisplayPreferences(currency="CNY",currencyRates=mapOf("CNY" to "7"),appearance=AppearancePreferences(vendorColors=mapOf("codex" to "#ff0000")))
        val changed=DisplayChange.LimitSource(true).apply(DisplayChange.MaskLimitEmails(true).apply(DisplayChange.LimitUsed(true).apply(before)))
        assertEquals(before,changed.copy(limits=LimitDisplayPreferences()));assertEquals(LimitDisplayPreferences(true,true,true),changed.limits)
        assertEquals(snapshot,HubParser.parse(raw));assertEquals("j***s@example.com",LimitDisplay.maskEmail("javis@example.com"))
        assertEquals("a***@example.com",LimitDisplay.maskEmail("a@example.com"));assertEquals("no-address",LimitDisplay.maskEmail("no-address"))
        assertEquals("Contact j***s@example.com · billing",LimitDisplay.text("Contact javis@example.com · billing",true))
        assertEquals("—",LimitDisplay.money(null,"USD"));assertEquals("—",LimitDisplay.money("1",null))
        assertFalse(LimitDisplayPreferences().showUsed);assertFalse(LimitDisplayPreferences().maskEmails);assertFalse(LimitDisplayPreferences().showSource)
    }
}
