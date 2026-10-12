package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class SubscriptionPresentationTest {
    @Test fun actualDesktopSubscriptionDomTitlesMetadataWarningsAndIconsMatchBothLanguages() {
        val data=javaClass.classLoader!!.getResourceAsStream("subscription-layout-parity.json")!!.bufferedReader().use {Json.parseToJsonElement(it.readText()).jsonObject}
        val today=LocalDate.parse(data.getValue("today").jsonPrimitive.content);val failures=mutableListOf<String>()
        data.getValue("cases").jsonArray.forEachIndexed {caseIndex,raw->
            val c=raw.jsonObject;val accounts=SubscriptionAccounts.dedupe(c.getValue("providers").jsonArray.map {it.jsonObject})
            val records=SubscriptionParser.parse(buildJsonObject {put("subscriptions",c.getValue("input"))}.toString())
            val expected=c.getValue("dom").jsonArray;assertEquals("case $caseIndex rows",expected.size,records.size)
            records.forEachIndexed {index,record->try {
                val actual=SubscriptionPresentation.row(record,accounts,today,Locale.forLanguageTag(c.getValue("locale").jsonPrimitive.content),c.getValue("mask").jsonPrimitive.boolean,c.getValue("icons").jsonPrimitive.boolean)
                val row=expected[index].jsonObject;val scope="case $caseIndex row $index"
                assertEquals("$scope title",row.getValue("title").jsonPrimitive.content,actual.title)
                assertEquals("$scope meta",row.getValue("meta").jsonPrimitive.content,actual.meta)
                assertEquals("$scope warning",row.getValue("warning").jsonPrimitive.content,actual.warning)
                assertEquals("$scope icon",row.getValue("icon").jsonPrimitive.boolean,actual.icon)
            } catch(error:AssertionError) {failures+=error.message.orEmpty()}}
        }
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }
}
