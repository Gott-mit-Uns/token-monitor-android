package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.Locale

class HomeQuotaLayoutTest {
    private fun parsed(providers:JsonArray)=HubParser.parse(buildJsonObject {
        put("periods",buildJsonObject {});put("limits",buildJsonObject {put("providers",providers)})
    }.toString()).limits

    @Test fun antigravitySelectionAndLabelsMatchPinned069OracleWithoutChangingWireReadings() {
        val data=javaClass.classLoader!!.getResourceAsStream("antigravity-dual-window-parity.json")!!.bufferedReader().use {Json.parseToJsonElement(it.readText()).jsonObject}
        assertEquals("3f106ae3d4712b26cced8272f0d488eaab0de1e6",data.getValue("sourceCommit").jsonPrimitive.content)
        data.getValue("cases").jsonArray.forEach {raw ->
            val case=raw.jsonObject;val scope=case.getValue("name").jsonPrimitive.content
            val input=buildJsonArray {add(case.getValue("input"))};val all=parsed(input)
            // Empty providers retain a status-only Limit in HubParser. It is
            // not a quota window and Home already excludes it by missing kind.
            val readings=all.take(case.getValue("input").jsonObject.getValue("windows").jsonArray.size)
            val selected=LimitDisplay.compactWindows(readings)
            val indices=case.getValue("selected").jsonArray.map {it.jsonPrimitive.int}
            assertEquals(scope,indices.map(readings::get),selected)
            val expected=case.getValue("home").jsonArray
            for(used in listOf(false,true)) {
                val accounts=HomeQuotaLayout.project(all,LimitDisplayPreferences(showUsed=used),now=Instant.parse("2026-10-12T12:00:00Z"))
                val windows=accounts.flatMap {it.windows}
                assertEquals("$scope used=$used",expected.size,windows.size)
                windows.forEachIndexed {index,w ->
                    val oracle=expected[index].jsonObject;val source=all[oracle.getValue("index").jsonPrimitive.int]
                    assertSame("$scope retains reading",source,w.source)
                    assertEquals("$scope caption",oracle.getValue("label").jsonPrimitive.content,w.label)
                    assertEquals("$scope remaining",LimitDisplay.percent(source,false),w.remaining)
                    assertEquals("$scope value", "${Math.round(w.remaining!!.let {if(used) 100-it else it})}% ${if(used) "used" else "left"}",w.value)
                    val boundary=if(source.resets.isNullOrEmpty()) "" else LimitProviderLayout.boundary(w.raw,Instant.parse("2026-10-12T12:00:00Z"))
                    val period=oracle.getValue("periodLabel").jsonPrimitive.content
                    assertEquals("$scope reset",if(period.isNotEmpty() && boundary.isNotEmpty()) "$period · $boundary" else boundary,w.boundary)
                }
            }
            assertEquals("$scope original wire unchanged",all,parsed(input))
        }
    }

    @Test fun hiddenModelsAreFilteredBeforeSingleGroupPickAndOtherProvidersKeepTheirWindows() {
        fun window(kind:String,label:String,remaining:Int)=buildJsonObject {put("kind",kind);put("label",label);put("remainingPercent",remaining)}
        val gemini=listOf(window("session","Gemini 5-hour",70),window("weekly","Gemini weekly",30))
        val claude=listOf(window("session","Claude 5-hour",60),window("weekly","Claude weekly",10))
        val all=parsed(buildJsonArray {
            add(buildJsonObject {put("provider","antigravity");put("windows",JsonArray(gemini+claude))})
            add(buildJsonObject {put("provider","codex");put("windows",buildJsonArray {add(window("session","",80));add(window("weekly","",40));add(buildJsonObject {put("kind","weekly");put("remainingPercent",99);put("additional",true)})})})
        })
        val hidden=claude.map {requireNotNull(LimitPreferences.windowItem("antigravity",it))}
        val prefs=LimitDisplayPreferences(hiddenItems=mapOf("antigravity" to hidden),homeAccountCount=4)
        val accounts=HomeQuotaLayout.project(all,prefs)
        val anti=accounts.single {it.provider=="antigravity"}
        assertEquals(listOf("Gemini 5-hour","Gemini Weekly"),anti.windows.map {it.label})
        assertEquals(listOf(70.0,30.0),anti.windows.map {it.remaining})
        val codex=accounts.single {it.provider=="codex"}
        assertEquals(listOf("Session","Weekly"),codex.windows.map {it.label})
        assertEquals(listOf(80.0,40.0),codex.windows.map {it.remaining})
        val hideWeekly=prefs.copy(hiddenItems=mapOf("antigravity" to hidden+requireNotNull(LimitPreferences.windowItem("antigravity",gemini[1]))))
        assertEquals(listOf("Gemini 5-hour"),HomeQuotaLayout.project(all,hideWeekly).single {it.provider=="antigravity"}.windows.map {it.label})
        val missing=parsed(buildJsonArray {add(buildJsonObject {put("provider","antigravity");put("windows",buildJsonArray {add(buildJsonObject {put("kind","session");put("label","Gemini 5-hour")})})})})
        assertNull(missing.single().remaining)
        assertTrue(HomeQuotaLayout.project(missing,LimitDisplayPreferences()).isEmpty())
    }

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
