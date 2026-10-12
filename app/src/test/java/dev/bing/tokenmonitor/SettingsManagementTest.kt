package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class SettingsManagementTest {
    private fun data()=javaClass.classLoader!!.getResourceAsStream("settings-management-parity.json")!!.bufferedReader().use {Json.parseToJsonElement(it.readText()).jsonObject}
    @Test fun originalProviderSettingsLabelsAndSubstringSearchMatch() {
        val fixture=data()
        fixture.getValue("catalog").jsonArray.forEach {raw->val p=raw.jsonObject
            assertEquals((p["settingsLabel"] ?: p.getValue("label")).jsonPrimitive.content,LimitPreferences.settingsLabel(p.getValue("id").jsonPrimitive.content))
        }
        fixture.getValue("search").jsonArray.forEach {raw->val c=raw.jsonObject
            assertEquals(c.getValue("ids").jsonArray.map {it.jsonPrimitive.content},LimitPreferences.providers.filter {LimitPreferences.matches(it,c.getValue("query").jsonPrimitive.content,Locale.US)})
        }
    }
    @Test fun originalLowLimitClassesAreIndependentOfMeterModeAndUnknownValues() {
        data().getValue("reports").jsonArray.forEach {report->report.jsonObject.getValue("highlights").jsonArray.forEach {raw->val c=raw.jsonObject
            val expected=if(c.getValue("critical").jsonPrimitive.boolean) HomeQuotaTone.CRITICAL else if(c.getValue("low").jsonPrimitive.boolean) HomeQuotaTone.LOW else HomeQuotaTone.NORMAL
            assertEquals(c.toString(),expected,homeQuotaTone(c["remaining"]?.jsonPrimitive?.doubleOrNull,c.getValue("enabled").jsonPrimitive.boolean))
        }}
        assertEquals(HomeQuotaTone.NORMAL,homeQuotaTone(Double.NaN,true))
    }
}
