package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class LimitProviderLayoutTest {
    @Test fun motionIdentityFieldsRetainOnlyNonNullScalarsAndLeaveExistingDisplayFieldsUntouched() {
        val scalar=Json.parseToJsonElement("""{"profileId":"public-profile","accountLabel":"Public account","windows":[{"id":7,"quotaId":"public-quota","model":"public-model","group":true,"remainingPercent":80}]}""").jsonObject
        val projected=LimitProviderLayout.displayRecord(scalar)
        assertEquals(scalar["profileId"],projected["profileId"])
        val window=projected.getValue("windows").jsonArray.single().jsonObject
        listOf("id","quotaId","model","group","remainingPercent").forEach { key ->
            assertEquals(scalar.getValue("windows").jsonArray.single().jsonObject[key],window[key])
        }
        val nested=Json.parseToJsonElement("""{"profileId":{"nestedIgnored":true},"accountLabel":"Public account","windows":[{"id":{},"quotaId":[],"model":null,"group":{"nestedIgnored":true},"label":"Daily","remainingPercent":80}]}""").jsonObject
        val filtered=LimitProviderLayout.displayRecord(nested)
        assertFalse(filtered.containsKey("profileId"))
        val filteredWindow=filtered.getValue("windows").jsonArray.single().jsonObject
        listOf("id","quotaId","model","group").forEach { assertFalse(filteredWindow.containsKey(it)) }
        assertEquals(JsonPrimitive("Public account"),filtered["accountLabel"])
        assertEquals(JsonPrimitive("Daily"),filteredWindow["label"])
        assertEquals(JsonPrimitive(80),filteredWindow["remainingPercent"])
    }
    @Test fun actualDesktopHidingRemovesEmptyGroupsAndRepairsOnlyAffectedWindowPairs() {
        val data=javaClass.classLoader!!.getResourceAsStream("limit-layout-parity.json")!!.bufferedReader().use {Json.parseToJsonElement(it.readText()).jsonObject}
        val now=Instant.parse(data.getValue("now").jsonPrimitive.content);val failures=mutableListOf<String>()
        data.getValue("hiddenCases").jsonArray.forEachIndexed {caseIndex,raw->
            val case=raw.jsonObject;val provider=case.getValue("provider").jsonObject
            val original=LimitProviderLayout.project(provider,false,Locale.forLanguageTag(case.getValue("locale").jsonPrimitive.content),now,ZoneId.of(data.getValue("zone").jsonPrimitive.content))
            val actual=LimitPreferences.filter(original,case.getValue("hidden").jsonArray.map {it.jsonPrimitive.content});val expected=case.getValue("windows").jsonArray
            try {
                assertEquals("hidden case $caseIndex rows",expected.size,actual.size)
                expected.forEachIndexed {index,row->val wanted=row.jsonObject;val found=actual[index];val scope="hidden case $caseIndex row $index"
                    assertEquals("$scope item",wanted.getValue("item").jsonPrimitive.contentOrNull,found.item)
                    assertEquals("$scope group",wanted.getValue("group").jsonPrimitive.contentOrNull,found.group)
                    assertEquals("$scope span",wanted.getValue("wide").jsonPrimitive.boolean,found.wide)
                    assertEquals("$scope label",wanted.getValue("label").jsonPrimitive.content,found.label)
                    assertEquals("$scope value",wanted.getValue("value").jsonPrimitive.content,found.value)
                }
            } catch(error:AssertionError) {failures+=error.message.orEmpty()}
        }
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }
    @Test fun presentationAdmitsOnlyTheNamedSchemaAndSharesOneRecordAcrossWindows() {
        val raw=Json.parseToJsonElement("""{"periods":{},"limits":{"providers":[{"provider":"codex","internalConfig":{"extra":"discarded"},"balance":{"amount":10,"currency":"USD","extra":"discarded"},"windows":[{"kind":"session","remainingPercent":75,"extra":"discarded"},{"kind":"weekly","remainingPercent":60}]}]}}""").jsonObject
        val limits=HubParser.parse(raw.toString()).limits
        val record=limits.first().presentation.providerRecord!!
        assertFalse(record.containsKey("internalConfig"));assertFalse(record.getValue("balance").jsonObject.containsKey("extra"))
        assertFalse(record.getValue("windows").jsonArray[0].jsonObject.containsKey("extra"))
        assertSame(record,limits[1].presentation.providerRecord)
        assertTrue(raw.getValue("limits").jsonObject.getValue("providers").jsonArray[0].jsonObject.containsKey("internalConfig"))
    }
    @Test fun everyProviderRowMatchesTheActualFixedDesktopDomInBothModesAndLanguages() {
        val data=javaClass.classLoader!!.getResourceAsStream("limit-layout-parity.json")!!.bufferedReader().use {Json.parseToJsonElement(it.readText()).jsonObject}
        val now=Instant.parse(data.getValue("now").jsonPrimitive.content)
        val differences=mutableListOf<String>()
        data.getValue("cases").jsonArray.forEachIndexed {caseIndex,raw ->
            val case=raw.jsonObject;val provider=case.getValue("provider").jsonObject
            val actual=LimitProviderLayout.project(provider,case.getValue("used").jsonPrimitive.boolean,Locale.forLanguageTag(case.getValue("locale").jsonPrimitive.content),now,ZoneId.of(data.getValue("zone").jsonPrimitive.content))
            val expected=case.getValue("windows").jsonArray
            val scope="Public fixed desktop case $caseIndex ${provider.getValue("provider").jsonPrimitive.content}"
            assertEquals(scope+" row count",expected.size,actual.size)
            val limits=HubParser.parse(buildJsonObject {put("periods",buildJsonObject {});put("limits",buildJsonObject {put("providers",buildJsonArray {add(provider)})})}.toString()).limits
            try {
                assertEquals(scope+" plan",case.getValue("plan").jsonPrimitive.content,LimitProviderLayout.plan(limits.first(),false,false))
                assertEquals(scope+" metadata",case.getValue("meta").jsonPrimitive.content,LimitProviderLayout.meta(limits.first(),now,LimitDisplayPreferences()))
            } catch(failure:AssertionError) {differences+=failure.message.orEmpty()}

            expected.forEachIndexed {index,row -> try {
                val wanted=row.jsonObject;val found=actual[index];val context="$scope row $index"
                assertEquals(context+" reset summary",wanted.getValue("resetSummary").jsonPrimitive.contentOrNull,found.resetSummary)
                assertEquals(context+" label",wanted.getValue("label").jsonPrimitive.content,found.label)
                assertEquals(context+" value",wanted.getValue("value").jsonPrimitive.content,found.value)
                assertEquals(context+" group",wanted.getValue("group").jsonPrimitive.contentOrNull,found.group)
                assertEquals(context+" span",wanted.getValue("wide").jsonPrimitive.boolean,found.wide)
                assertEquals(context+" reset visibility",wanted.getValue("noReset").jsonPrimitive.boolean,found.noReset)
                assertEquals(context+" boundary",wanted.getValue("reset").jsonPrimitive.content,found.boundary)
                assertEquals(context+" detail",wanted.getValue("detail").jsonPrimitive.content,found.detail)
                val percent=wanted.getValue("percent").jsonPrimitive.doubleOrNull
                if(percent==null) assertNull(context+" percent",found.percent) else {assertNotNull(context+" percent",found.percent);assertEquals(context+" percent",percent,found.percent!!,0.000001)}
                wanted.getValue("tone").jsonPrimitive.doubleOrNull?.let {assertEquals(context+" tone",it,found.tone,0.000001)}
                assertEquals(context+" item",wanted.getValue("item").jsonPrimitive.contentOrNull,found.item)
                assertEquals(context+" tooltip",wanted.getValue("details").jsonArray.map {cells->cells.jsonArray.map {it.jsonPrimitive.content}},found.details)
            } catch(failure:AssertionError) { differences+=failure.message.orEmpty() } }
        }
        assertTrue(differences.joinToString("\n"),differences.isEmpty())
    }
}
