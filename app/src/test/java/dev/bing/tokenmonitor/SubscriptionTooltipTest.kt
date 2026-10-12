package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class SubscriptionTooltipTest {
    @Test fun actualOriginalDomPlanCardsMatchBothCurrenciesLocalesAndWholeProviderScopes() {
        val data=javaClass.classLoader!!.getResourceAsStream("subscription-layout-parity.json")!!.bufferedReader().use {Json.parseToJsonElement(it.readText()).jsonObject}
        val cases=data.getValue("cards").jsonArray;assertEquals(144,cases.size)
        val errors=mutableListOf<String>()
        cases.forEachIndexed {index,element->val c=element.jsonObject
            val records=SubscriptionParser.parse(buildJsonObject {put("subscriptions",c.getValue("input"))}.toString())
            val context=SubscriptionReadContext(records,SubscriptionAccounts.dedupe(c.getValue("accounts").jsonArray.map {it.jsonObject}),
                c.getValue("costs").jsonObject.mapValues {it.value.jsonPrimitive.content},LocalDate.parse(data.getValue("today").jsonPrimitive.content),
                Locale.forLanguageTag(c.getValue("locale").jsonPrimitive.content),UsageCurrency(c.getValue("currency").jsonPrimitive.content))
            val actual=context.forRow(c.getValue("provider").jsonObject,c.getValue("includeRollup").jsonPrimitive.boolean)
            val expected=c.getValue("rows").jsonArray.map {raw->val row=raw.jsonObject
                SubscriptionTooltipRow((row["label"] as? JsonPrimitive)?.content.orEmpty(),(row["value"] as? JsonPrimitive)?.content.orEmpty(),(row["separator"] as? JsonPrimitive)?.booleanOrNull==true,(row["title"] as? JsonPrimitive)?.content.orEmpty(),(row["warn"] as? JsonPrimitive)?.booleanOrNull==true)}
            try {assertEquals("source DOM case $index",expected,actual)} catch(e:AssertionError) {errors+=e.message.orEmpty()}
        }
        assertTrue(errors.joinToString("\n"),errors.isEmpty())
    }
    @Test fun missingIdsRemainBoundToTheirOwnAccountWhenRowsReorder() {
        val first=Subscription("codex","Pro","20.00","USD","month",1,"2026-01-01",null,true,account="Public",binding=SubscriptionBinding(accountKey="public-a"))
        val second=first.copy(binding=SubscriptionBinding(accountKey="public-b"))
        val before=SubscriptionDisplay.keyed(listOf(first,second)).associate {it.second.binding!!.accountKey to it.first}
        val after=SubscriptionDisplay.keyed(listOf(second,first)).associate {it.second.binding!!.accountKey to it.first}
        assertEquals(before,after);assertNotEquals(before.getValue("public-a"),before.getValue("public-b"))
    }
}
