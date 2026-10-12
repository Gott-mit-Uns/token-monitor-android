package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class ProjectDeviceTest {
    private val fixture=Json.parseToJsonElement(javaClass.getResource("/project-device-parity.json")!!.readText()).jsonObject
    private val labels=mapOf("codex" to "Codex","claude" to "Claude Code","gemini" to "Gemini CLI")
    private fun label(id:String)=labels[id] ?: id
    private fun period(input:JsonElement)=HubParser.parse(buildJsonObject {put("periods",buildJsonObject {put("today",input)})}.toString()).periods.getValue("today")
    private fun devices(input:JsonElement)=HubParser.parse(buildJsonObject {put("periods",buildJsonObject {});put("devices",input)}.toString()).devices
    private fun list(key:String)=fixture[key]!!.jsonArray
    @Test fun originalProjectCanonicalMergingCostOrderAttributionAndGradients() {
        list("projectCases").forEachIndexed {index,item->
            val case=item.jsonObject;val rows=ProjectDisplay.rows(period(case["input"]!!),::label)
            val expected=case["expected"]!!.jsonArray;assertEquals("case $index",expected.size,rows.size)
            rows.zip(expected).forEach {(row,e)->val r=e.jsonObject
                assertEquals(r["id"]!!.jsonPrimitive.content,row.id);assertEquals(r["name"]!!.jsonPrimitive.content,row.name)
                assertEquals(r["tokens"]!!.jsonPrimitive.long,row.tokens.toLong());assertEquals(r["cost"]!!.jsonPrimitive.double,row.cost.toDouble(),1e-9)
                assertEquals(r["unpriced"]!!.jsonPrimitive.long,row.unpriced.toLong())
                assertEquals(JsonArray(r["clients"]!!.jsonArray.map {JsonObject(it.jsonObject+("percent" to JsonPrimitive(it.jsonObject["percent"]!!.jsonPrimitive.double)))}),JsonArray(row.clients.map {buildJsonObject {put("id",it.id);put("label",it.label);put("tokens",it.tokens.toLong());put("percent",it.percent)}}))
                val gradient=r["gradient"]!!.jsonPrimitive.content
                val actual=ProjectDisplay.gradient(row.clients)
                if(!gradient.startsWith("linear-gradient")) {assertTrue(actual.isEmpty() || actual.all {(if(it.client.isEmpty() || it.client !in labels) "<unknown>" else it.client)==gradient})}
                else {
                    val stops=Regex("([^ ,]+) ([0-9.]+)%").findAll(gradient).map {it.groupValues[1] to it.groupValues[2].toFloat()/100}.toList()
                    assertEquals(stops.size,actual.size)
                    actual.zip(stops).forEach {(a,b)->assertEquals(b.first,if(a.client.isEmpty() || a.client !in labels) "<unknown>" else a.client);assertEquals(b.second,a.position,1e-6f)}
                }
            }
        }
    }
    @Test fun originalDeviceToolModelAndOperatingSystemProjections() {
        list("deviceCases").forEach {item->val case=item.jsonObject
            val device=devices(JsonArray(listOf(case["input"]!!))).single()
            val rows=DeviceDisplay.tools(device,"today",::label)
            val actual=JsonArray(rows.map {buildJsonObject {put("id",it.id);put("label",it.label);put("tokens",it.tokens.toLong());put("percent",it.percent);put("models",JsonArray(it.models.map {(name,tokens)->buildJsonObject {put("name",name);put("tokens",tokens)}}))}})
            assertEquals(JsonArray(case["expected"]!!.jsonArray.map {JsonObject(it.jsonObject+("percent" to JsonPrimitive(it.jsonObject["percent"]!!.jsonPrimitive.double)))}),actual)
        }
        list("platforms").forEach {item->val case=item.jsonObject;val raw=case["platform"]?.takeUnless {it==JsonNull}?.jsonPrimitive?.content
            val os=case["os"]!!.jsonArray
            val d=Device("public","Public",false,null,raw,null,rawPlatform=raw,osName=os.getOrNull(0)?.jsonPrimitive?.content,osVersion=os.getOrNull(1)?.jsonPrimitive?.content)
            assertEquals(case["expected"]!!.jsonPrimitive.content,DeviceDisplay.platform(d))
        }
        assertEquals("apple",DeviceDisplay.icon("darwin-arm64"));assertEquals("windows",DeviceDisplay.icon("win32-x64"));assertEquals("linux",DeviceDisplay.icon("openbsd"));assertNull(DeviceDisplay.icon("android"))
    }
    @Test fun originalHomeFourPositiveDevicesLocalStaleTiesAndSourceNames() {
        list("homeCases").forEach {item->val c=item.jsonObject;val rows=DeviceDisplay.home(devices(c["input"]!!),"today",localId=c["local"]!!.jsonPrimitive.content,cap=c["cap"]!!.jsonPrimitive.int)
            val actual=JsonArray(rows.map {buildJsonObject {put("key",it.id);put("name",it.name);put("value",it.tokens);put("platform",it.platform);put("isLocal",it.local);put("isStale",it.stale)}})
            assertEquals(c["expected"],actual)
        }
        val d=Device("public","Host",false,null,null,null,mapOf("today" to DeviceUsage(1,null,emptyMap(),null,null,null)))
        assertEquals("Local alias",DeviceDisplay.home(listOf(d),"today",mapOf("public" to "Local alias")).single().name)
    }
    @Test fun omissionScopeAndExactLargeProjectSumsKeepWireDataReadOnly() {
        list("incomplete").forEach {item->val c=item.jsonObject;val name=c["period"]!!.jsonPrimitive.content
            val input=JsonObject(c["input"]!!.jsonObject+mapOf("periods" to buildJsonObject {put(name,buildJsonObject {})}))
            val result=HubParser.parse(input.toString()).periods.getValue(name);assertEquals(c["expected"]!!.jsonPrimitive.boolean,result.projectsIncomplete)
        }
        val row=UsageRow("public","Public",Long.MAX_VALUE,"1","codex",null,null)
        val p=Period(null,null,emptyMap(),emptyMap(),listOf(row,row.copy(id="other",label="PUBLIC")),emptyList(),null,TokenComponents(null,null,null,null,null))
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE)*BigInteger.TWO,ProjectDisplay.rows(p).single().tokens)
        val copy=p.copy();ProjectDisplay.rows(p);assertEquals(copy,p)
    }
    @Test fun sourceDeviceSyncAgeAndExactNextLabelBoundary() {
        list("ages").forEach {item->val c=item.jsonObject;val value=c["value"]!!.jsonPrimitive.content;val now=c["now"]!!.jsonPrimitive.long;val expected=c["expected"]!!.jsonObject
            val actual=DeviceDisplay.age(value,now)!!
            assertEquals(expected["unit"]!!.jsonPrimitive.content,actual.first);assertEquals(expected["count"]!!.jsonPrimitive.long,actual.second)
            val next=c["next"]!!.jsonPrimitive.long;assertEquals(next,DeviceDisplay.nextAgeAt(value,now))
            assertEquals(actual,DeviceDisplay.age(value,next-1));assertNotEquals(actual,DeviceDisplay.age(value,next))
        }
        assertNull(DeviceDisplay.age("invalid",0));assertNull(DeviceDisplay.nextAgeAt(null,0))
    }

}
