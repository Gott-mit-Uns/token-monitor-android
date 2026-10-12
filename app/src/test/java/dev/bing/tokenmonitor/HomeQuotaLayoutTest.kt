package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.Locale

class HomeQuotaLayoutTest {
    @Test fun originalHomeApplicationFunctionsAndActualDomMatchEverySelectionIdentityModeAndOrder() {
        val data=javaClass.classLoader!!.getResourceAsStream("home-quota-parity.json")!!.bufferedReader().use {Json.parseToJsonElement(it.readText()).jsonObject}
        val failures=mutableListOf<String>()
        data.getValue("cases").jsonArray.forEachIndexed {caseIndex,raw->
            val case=raw.jsonObject;val settings=case.getValue("settings").jsonObject
            fun flag(key:String)=settings[key]?.jsonPrimitive?.booleanOrNull==true
            fun order(key:String)=settings[key]?.jsonPrimitive?.contentOrNull?.split(',').orEmpty()
            val prefs=LimitDisplayPreferences(showUsed=flag("showLimitUsed"),maskEmails=flag("maskLimitAccountEmails"),providerOrder=order("limitProviderOrder"),homeProviderOrder=order("homeLimitProviderOrder"),
                hiddenHomeProviders=settings["hiddenHomeLimitProviders"]?.jsonArray?.map {it.jsonPrimitive.content}?.toSet().orEmpty(),hiddenItems=settings["limitProviderHiddenItems"]?.jsonObject?.mapValues {it.value.jsonArray.map {it.jsonPrimitive.content}}.orEmpty(),
                homeAccountCount=settings.getValue("homeLimitAccountCount").jsonPrimitive.int,homeBars=settings.getValue("homeLimitDisplayMode").jsonPrimitive.content=="bars",showHomeProviderNames=flag("showHomeLimitProviderNames"))
            val snapshot=HubParser.parse(buildJsonObject {put("periods",buildJsonObject {});put("limits",buildJsonObject {put("providers",case.getValue("input"))})}.toString())
            val actual=HomeQuotaLayout.project(snapshot.limits,prefs,settings["showToolIcons"]?.jsonPrimitive?.booleanOrNull!=false,Locale.forLanguageTag(case.getValue("locale").jsonPrimitive.content),Instant.parse(data.getValue("now").jsonPrimitive.content))
            val rows=case.getValue("rows").jsonArray;val dom=case.getValue("dom").jsonArray
            try {
                assertEquals("case $caseIndex accounts",rows.size,actual.size)
                actual.forEachIndexed {index,account->
                    val expected=rows[index].jsonObject;val visible=dom[index].jsonObject
                    val scope="case $caseIndex account $index ${account.provider}"
                    assertEquals("$scope provider",expected.getValue("providerId").jsonPrimitive.content,account.provider)
                    assertEquals("$scope name",visible.getValue("name").jsonPrimitive.content,account.name)
                    assertEquals("$scope plan",visible.getValue("plan").jsonPrimitive.content,account.plan)
                    assertEquals("$scope urgency",expected.getValue("lowestRemaining").jsonPrimitive.double,account.lowest,0.000001)
                    assertEquals("$scope windows",visible.getValue("windows").jsonArray.size,account.windows.size)
                    account.windows.forEachIndexed {windowIndex,w->
                        val value=visible.getValue("windows").jsonArray[windowIndex].jsonObject;val meter=expected.getValue("windows").jsonArray[windowIndex].jsonObject
                        assertEquals("$scope $windowIndex caption",value.getValue("label").jsonPrimitive.content,w.label)
                        assertEquals("$scope $windowIndex value",value.getValue("value").jsonPrimitive.content,w.value)
                        assertEquals("$scope $windowIndex boundary",value.getValue("boundary").jsonPrimitive.content,w.boundary)
                        assertEquals("$scope $windowIndex remaining",meter.getValue("remainingPercent").jsonPrimitive.doubleOrNull,w.remaining)
                        if(prefs.homeBars) {val wanted=value.getValue("percent").jsonPrimitive.doubleOrNull;if(wanted==null) assertNull(w.percent) else {assertNotNull(w.percent);assertEquals("$scope $windowIndex bar",wanted,w.percent!!,0.000001)}}
                    }
                }
            } catch(error:AssertionError) {failures+=error.message.orEmpty()}
        }
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }
}
