package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class Desktop078Test {
    private val reference=Json.parseToJsonElement(javaClass.getResource("/desktop-078.json")!!.readText()).jsonObject
    @Test fun fixedDesktopRegistryAndSourceAreExact() {
        assertEquals(DesktopParityMetadata.sourceCommit,reference.getValue("sourceCommit").jsonPrimitive.content)
        val expected=reference.getValue("registry").jsonObject.getValue("components").jsonObject.mapValues {(_,rows)->rows.jsonArray.associate {it.jsonObject.getValue("revision").jsonPrimitive.long to it.jsonObject.getValue("buildId").jsonPrimitive.content}}
        assertEquals(expected,HubBuildRegistry.entries)
    }
    @Test fun zcodeTitlesAndFamiliesMatchDesktopAcrossHttpFullAndFreshness() {
        val now=reference.getValue("now").jsonPrimitive.long
        reference.getValue("cases").jsonArray.forEach {raw->
            val case=raw.jsonObject
            fun frame(key:String)=case.getValue(key).jsonPrimitive.content.substringAfter("data: ").trim()
            val http=HubParser.parse(HubJsonProjection.stats(case.getValue("stats").toString().byteInputStream()))
            val full=HubParser.parse(HubJsonProjection.streamStats(frame("full").byteInputStream()))
            val fresh=HubParser.applyFreshness(full,Json.parseToJsonElement(frame("freshness")).jsonObject.getValue("stats").jsonObject)
            for(snapshot in listOf(http,full,fresh)) {
                val period=snapshot.periods.getValue("today")
                val rows=SessionListDisplay.raw(period.sessions,now)
                val grouped=SessionListDisplay.grouped(rows,now=now)
                val expected=case.getValue("expected").jsonArray.associate {it.jsonObject.getValue("id").jsonPrimitive.content to it.jsonObject}
                assertEquals(case.getValue("name").jsonPrimitive.content,expected.keys,grouped.map {it.id}.toSet())
                grouped.forEach {row->
                    val e=expected.getValue(row.id)
                    assertEquals(BigInteger(e.getValue("tokens").jsonPrimitive.content),row.tokens)
                    assertEquals(0,BigDecimal(e.getValue("cost").jsonPrimitive.content).compareTo(BigDecimal(row.cost)))
                    assertEquals(e.getValue("members").jsonArray.map {it.jsonPrimitive.content}.toSet(),row.reviews.map {it.id}.toSet())
                }
                assertEquals(rows.fold(BigInteger.ZERO) {sum,row->sum+row.tokens},grouped.fold(BigInteger.ZERO) {sum,row->sum+row.tokens})
                if(case.getValue("name").jsonPrimitive.content=="complete") {
                    val family=grouped.single {it.review}
                    assertEquals("Saved ZCode title",family.name)
                    assertEquals(setOf("zcode:parent","zcode:child","zcode:workflow"),family.reviews.map {it.id}.toSet())
                    assertTrue(grouped.any {it.id=="zcode:fork"});assertTrue(grouped.any {it.id=="zcode:side"})
                }
            }
        }
    }
}
