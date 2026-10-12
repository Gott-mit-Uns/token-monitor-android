package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class LimitSettingsPresentationTest {
    private fun data()=javaClass.classLoader!!.getResourceAsStream("limit-settings-parity.json")!!.bufferedReader().use {Json.parseToJsonElement(it.readText()).jsonObject}
    private fun parse(rows:JsonArray)=HubParser.parse(buildJsonObject {put("periods",buildJsonObject {});put("limits",buildJsonObject {put("providers",rows)})}.toString()).limits
    @Test fun reportedPlansUseBothHubFormatsWithoutTurningAccountNamesIntoPlans() {
        fun row(provider:String,fields:Map<String,String>)=parse(JsonArray(listOf(buildJsonObject {
            put("provider",provider);put("status","ok");fields.forEach {(key,value)->put(key,value)}
        }))).single()
        val legacy=row("codex",mapOf("accountLabel" to "Plus","accountName" to "Public workspace"))
        assertEquals("Plus",LimitSettingsPresentation.plan(legacy,false))
        assertEquals("Pro",LimitSettingsPresentation.plan(row("codex",mapOf("accountLabel" to "Plus","planLabel" to "Pro")),false))
        assertEquals("",LimitSettingsPresentation.plan(row("codex",mapOf("accountName" to "Public workspace")),false))
        assertEquals("",LimitSettingsPresentation.plan(row("mimo",mapOf("accountLabel" to "Desktop Membership")),false))
        assertEquals("Pro",LimitSettingsPresentation.plan(row("zed",mapOf("accountLabel" to "Zed Pro")),false))
        assertEquals("Lite",LimitSettingsPresentation.plan(row("zai",mapOf("accountLabel" to "GLM Coding Lite")),false))
        assertEquals("Plus",legacy.account)
    }
    @Test fun pinnedDesktopStatusAndSourceCasesMatchAcrossAllProviders() {
        data().getValue("cases").jsonArray.forEach {raw->val c=raw.jsonObject;val input=c.getValue("input");val row=parse(JsonArray(listOf(input))).single()
            val output=c["output"] as? JsonObject
            val actual=LimitSettingsPresentation.status(row)
            assertEquals(input.toString(),output?.get("label")?.jsonPrimitive?.content,actual?.label)
            assertEquals(input.toString(),output?.get("tone")?.jsonPrimitive?.content,actual?.tone)
            assertEquals(input.toString(),c.getValue("source").jsonPrimitive.content,LimitDisplay.source(row))
        }
        data().getValue("wording").jsonObject.forEach {(locale,entries)->entries.jsonObject.forEach {(label,value)->
            assertEquals(value.jsonPrimitive.content,LimitSettingsPresentation.text(Locale.forLanguageTag(locale),label))
        }}
    }
    @Test fun originalRecordSelectionAndReportedIdentityCountingDoNotInventAccounts() {
        data().getValue("selections").jsonArray.forEach {raw->val c=raw.jsonObject;val groups=LimitDisplay.groups(parse(c.getValue("input").jsonArray))
            assertEquals(groups[c.getValue("index").jsonPrimitive.int].first(),LimitSettingsPresentation.record(groups))
        }
        assertNull(LimitSettingsPresentation.record(emptyList()))
        val absent=LimitDisplay.groups(parse(JsonArray(listOf(buildJsonObject {put("provider","codex");put("status","notConfigured")}))))
        assertTrue(LimitSettingsPresentation.accounts(absent).isEmpty())
        val mimo=LimitDisplay.groups(parse(JsonArray(listOf("Console","Desktop Membership").map {product->buildJsonObject {
            put("provider","mimo");put("status","ok");put("accountLabel",product);put("accountEmail","public@example.com")
        }})))
        assertEquals(2,LimitSettingsPresentation.accounts(mimo).size)
        assertEquals(1,LimitSettingsPresentation.accountCount(mimo))
    }
}
