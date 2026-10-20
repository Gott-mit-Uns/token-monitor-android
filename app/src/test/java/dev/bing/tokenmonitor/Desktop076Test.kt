package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.util.Locale

class Desktop076Test {
    private val reference=Json.parseToJsonElement(javaClass.getResource("/desktop-076.json")!!.readText()).jsonObject
    private val ids=mapOf("tool" to "tools","model" to "models","session" to "sessions","device" to "devices")
    @Test fun countsMatchFixedDesktopDefaultsBoundsAndInvalidInputs() {
        reference.getValue("counts").jsonArray.forEach {raw->
            val c=raw.jsonObject;val input=c["input"] as? JsonObject;val expected=c.getValue("expected").jsonObject
            ids.forEach {(source,id)->assertEquals(expected.getValue(source).jsonPrimitive.int,HomeModuleCounts.normalize(id,(input?.get(source) as? JsonPrimitive)?.contentOrNull))}
        }
        val p=DisplayPreferences(currency="CNY",projectBreakdownMode="model")
        val changed=DisplayChange.HomeModuleCount("tools","9").apply(p)
        assertEquals(mapOf("tools" to 9,"models" to 5,"sessions" to 5,"devices" to 4),changed.homeModuleCounts)
        assertEquals(p,changed.copy(homeModuleCounts=emptyMap()))
        assertEquals(changed,DisplayChange.HomeModuleCount("unknown","40").apply(changed))
        assertEquals(9,DisplayChange.HomeModuleCount("devices","8").apply(changed).homeModuleCounts["tools"])
    }
    @Test fun additionalUsageRowsRetainSharesAndSessionCapRetainsActiveRows() {
        val values=(1..15).map {"public-$it" to BigInteger.valueOf((100-it).toLong())}
        val more=HomeUsageDisplay.rows(values,2000,emptyMap(),true,Locale.US,9)
        assertEquals(9,more.size);assertEquals(99.0/2000,more.first().share,0.00001)
        assertEquals(5,HomeUsageDisplay.rows(values,2000,emptyMap(),false,Locale.US).size)
        val now=java.time.Instant.parse("2026-10-20T01:00:00Z").toEpochMilli()
        val sessions=buildJsonObject {(1..9).forEach {n->put("public:$n",buildJsonObject {put("client","codex");put("sessionId","$n");put("totalTokens",100);put("lastUsedAt",java.time.Instant.ofEpochMilli(now-n*1000).toString());put("turnEnded",n!=9)})}}
        val snapshot=HubParser.parse(buildJsonObject {put("periods",buildJsonObject {put("today",buildJsonObject {put("sessions",sessions)})})}.toString())
        assertEquals(3,SessionDisplay.recent(snapshot,now,2).size)
        assertEquals(8,SessionDisplay.recent(snapshot,now,7).size)
    }
    @Test fun groupedArgonDisabledAndLegacyQuotaVisibilityMatchDesktop() {
        reference.getValue("quotaCases").jsonArray.forEach {raw->
            val c=raw.jsonObject;val windows=c.getValue("windows").jsonArray
            val provider=buildJsonObject {put("provider","antigravity");put("status","ok");put("windows",windows)}
            val snapshot=HubParser.parse(buildJsonObject {put("periods",buildJsonObject {});put("limits",buildJsonObject {put("providers",buildJsonArray {add(provider)})})}.toString())
            assertEquals(windows.size,snapshot.limits.size)
            assertEquals(c.getValue("visible").jsonArray.map {it.jsonPrimitive.boolean},snapshot.limits.map(AntigravityQuota::visible))
            assertEquals(c.getValue("compact").jsonArray.map {it.jsonPrimitive.content},LimitDisplay.compactWindows(snapshot.limits).map {it.label})
        }
        val windows=reference.getValue("quotaCases").jsonArray[0].jsonObject.getValue("windows")
        val rows=LimitProviderLayout.project(buildJsonObject {put("provider","antigravity");put("windows",windows)},false,Locale.US)
        assertEquals(listOf("Gemini","Gemini","Claude/GPT","Claude/GPT","Gemini 4 Argon"),rows.map {it.group})
        assertTrue(rows.last().value.contains("74"))
    }
}
