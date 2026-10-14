package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class LimitPreferencesTest {
    private val data by lazy {javaClass.classLoader!!.getResourceAsStream("limit-preferences-parity.json")!!.bufferedReader().use {Json.parseToJsonElement(it.readText()).jsonObject}}
    private fun JsonElement.strings()=jsonArray.map {it.jsonPrimitive.content}
    private fun JsonElement.hidden()=jsonObject.mapValues {it.value.strings()}
    private fun withNotion(order:List<String>)=order.toMutableList().apply {add(indexOf("thirdparty"),"notion")}
    @Test fun originalDesktopOrderingUsageIdsFallbacksAndBoundsArePreserved() {
        data.getValue("orders").jsonArray.forEach {raw->
            val case=raw.jsonObject;val input=case.getValue("input").strings()
            assertEquals(case.getValue("selection").strings(),LimitPreferences.selection(input));assertEquals(withNotion(case.getValue("order").strings()),LimitPreferences.order(input))
            case.getValue("moves").jsonArray.forEach {move->val m=move.jsonObject;assertEquals(withNotion(m.getValue("value").strings()),LimitPreferences.move(input,m.getValue("id").jsonPrimitive.content,m.getValue("direction").jsonPrimitive.int))}
        }
        data.getValue("items").jsonArray.forEach {raw->
            val case=raw.jsonObject;val input=case.getValue("input").jsonPrimitive.content;val normalized=case.getValue("normalized").jsonPrimitive.contentOrNull
            assertEquals(input,normalized,LimitPreferences.item(input))
            if(normalized!=null) case.getValue("labels").jsonObject.forEach {(provider,label)->assertEquals("$provider $normalized",label.jsonPrimitive.content,LimitPreferences.fallback(provider,normalized))}
        }
        data.getValue("windowItems").jsonArray.forEach {raw->val c=raw.jsonObject;assertEquals(c.getValue("key").jsonPrimitive.content,LimitPreferences.windowItem(c.getValue("provider").jsonPrimitive.content,c.getValue("window").jsonObject).orEmpty())}
        data.getValue("hidden").jsonArray.forEach {raw->val c=raw.jsonObject;assertEquals(c.getValue("normalized").hidden(),LimitPreferences.hidden(c.getValue("input").hidden()))}
    }
    @Test fun originalMimoGroupingCountsLogicalAccountsAcrossProductsAndProfileNames() {
        data.getValue("accounts").jsonArray.forEach {raw->
            val c=raw.jsonObject;val input=c.getValue("input").jsonArray
            val providers=JsonArray(input.map {JsonObject(it.jsonObject+mapOf("provider" to JsonPrimitive("mimo"),"windows" to JsonArray(emptyList())))})
            val rows=HubParser.parse(buildJsonObject {put("periods",buildJsonObject {});put("limits",buildJsonObject {put("providers",providers)})}.toString()).limits
            val actual=LimitPreferences.mimoGroups(LimitDisplay.groups(rows)).map {group->group.map {it.first().presentation.recordIndex!!}}
            assertEquals(c.getValue("groups").jsonArray.map {it.jsonArray.map {v->v.jsonPrimitive.int}},actual)
        }
    }
}
