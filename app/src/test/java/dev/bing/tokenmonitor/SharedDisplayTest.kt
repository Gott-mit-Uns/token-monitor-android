package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.math.BigDecimal

class SharedDisplayTest {
    private fun fixture()=javaClass.classLoader!!.getResourceAsStream("shared-display-parity.json")!!.bufferedReader().use {Json.parseToJsonElement(it.readText()).jsonObject}
    private fun subscription(raw:JsonElement)=SubscriptionParser.parse(buildJsonObject {put("subscriptions",buildJsonArray {add(raw)})}.toString()).single()
    @Test fun priceNumbersAndBothLanguageSummariesMatchTheActualDesktopRenderer() {
        val fixture=fixture()
        fixture.getValue("prices").jsonArray.forEach {raw ->
            val item=raw.jsonObject
            val price=PricingParser.parse("""{"ok":true,"version":1,"revision":1,"value":[${item.getValue("raw").jsonPrimitive.content}]}""").models.single()
            item.getValue("fields").jsonObject.forEach {(key,value) -> assertEquals(value.jsonPrimitive.content,sharedRateText(price.rates.getValue(key)))}
            for(locale in listOf("en","zh")) {
                val labels=mapOf("cacheReadPerM" to "cacheRead","inputPerM" to "input","outputPerM" to "output","cacheWritePerM" to "cacheWrite","cacheWrite1hPerM" to "cacheWrite1h").mapValues {fixture.getValue("labels").jsonObject.getValue(it.value).jsonObject.getValue(locale).jsonPrimitive.content}
                assertEquals(item.getValue("meta").jsonObject.getValue(locale).jsonPrimitive.content,sharedPricingSummary(price,labels))
            }
        }
    }
    @Test fun calendarCoverageMoneyAndElapsedTimeMatchValidDesktopRecords() {
        fixture().getValue("subscriptions").jsonArray.forEach {raw ->
            val item=raw.jsonObject;val record=subscription(item.getValue("input"));val today=LocalDate.parse(item.getValue("today").jsonPrimitive.content);val expected=item.getValue("expected").jsonObject
            assertEquals(expected.getValue("renewal").jsonPrimitive.content,record.nextRenewal(today)?.toString().orEmpty())
            assertEquals(expected.getValue("coverage").jsonPrimitive.content,record.coverageEnd(today)?.toString().orEmpty())
            assertEquals(expected.getValue("stop").jsonPrimitive.content,record.coverageStop()?.toString().orEmpty())
            val paid=expected.getValue("recorded").jsonPrimitive.double
            assertEquals(paid,record.recordedTotal(today)!!.toDouble(),maxOf(1e-8,Math.ulp(paid)*2))
            assertEquals(expected.getValue("month").jsonPrimitive.double,SubscriptionDisplay.monthTotal(record,today)!!.toDouble(),1e-8)
            assertEquals(expected.getValue("amount").jsonPrimitive.content,SubscriptionDisplay.money(record.amount,record.currency))
            val elapsed=expected.getValue("elapsed")
            if(elapsed is JsonNull) assertNull(SubscriptionDisplay.elapsed(record,today))
            else {assertEquals(elapsed.jsonObject.getValue("value").jsonPrimitive.long,SubscriptionDisplay.elapsed(record,today)!!.first);assertEquals(elapsed.jsonObject.getValue("months").jsonPrimitive.boolean,SubscriptionDisplay.elapsed(record,today)!!.second)}
        }
    }
    @Test fun monthlyEquivalentUsesEveryConfiguredRateAndExpiredPlansDropOutWithoutDeletingRecords() {
        fixture().getValue("monthly").jsonArray.forEach {raw ->
            val item=raw.jsonObject;val records=item.getValue("input").jsonArray.map(::subscription);val saved=records.toList()
            val rates=item.getValue("rates").jsonObject.mapValues {it.value.jsonPrimitive.content}
            val wanted=item.getValue("expected").jsonPrimitive.double
            val actual=SubscriptionDisplay.monthlyUsd(records,LocalDate.parse(item.getValue("today").jsonPrimitive.content),UsageCurrency("USD",rates))!!.toDouble()
            assertEquals(wanted,actual,maxOf(1e-8,Math.ulp(wanted)*2));assertEquals(saved,records)
        }
    }
    @Test fun missingAndMalformedAmountsStayUnknownAndNeverBecomeUsdZero() {
        val day=LocalDate.parse("2026-10-10")
        val valid=Subscription("codex","Public","20.00","USD","month",1,"2026-01-01",null,true)
        assertNull(SubscriptionDisplay.monthlyUsd(listOf(valid.copy(amount=null)),day,UsageCurrency()))
        assertNull(SubscriptionDisplay.monthlyUsd(listOf(valid.copy(currency="EUR")),day,UsageCurrency()))
        assertNull(SubscriptionDisplay.monthlyUsd(listOf(valid.copy(autoRenew=null)),day,UsageCurrency()))
        assertEquals("—",SubscriptionDisplay.money(null,"USD"));assertEquals("—",SubscriptionDisplay.money("20",null))
        assertEquals("EUR 20.00",SubscriptionDisplay.money("20","EUR"))
        assertNull(SubscriptionDisplay.monthTotal(valid.copy(kind="topup",topUps=listOf(TopUp("2026-10-01",null))),day))
        assertEquals("0.0",SubscriptionDisplay.monthlyUsd(listOf(valid.copy(autoRenew=false)),day,UsageCurrency()))
    }
    @Test fun idsStableAcrossValueUpdatesAndAliasesDistinguishUninitializedFromExplicitlyEmpty() {
        val records=SubscriptionParser.parse("""{"subscriptions":[{"id":"public-id","provider":"codex","planName":"Public","note":"Public note"},{"provider":"claude","planName":"No id"},{"provider":"claude","planName":"No id"}]}""")
        val keys=SubscriptionDisplay.keyed(records).map {it.first}
        assertEquals(3,keys.distinct().size);assertEquals("public-id",keys.first())
        assertEquals(keys,SubscriptionDisplay.keyed(records.map {it.copy(amount="30")}).map {it.first})
        assertEquals("Public note",records.first().note)
        assertFalse(ModelNamesParser.parse("""{"ok":true,"value":null}""").initialized)
        assertTrue(ModelNamesParser.parse("""{"ok":true,"value":{"modelAliases":{},"modelAliasGrouping":"off"}}""").initialized)
    }
}
