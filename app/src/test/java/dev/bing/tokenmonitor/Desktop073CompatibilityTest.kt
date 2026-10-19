package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Public Hub wire examples. Producer fixes stay on producers; these checks protect consumption. */
class Desktop073CompatibilityTest {
    private val now=1792137600000L
    private fun wire(tokens:Int=200,timed:Int=100,duration:Int=1000,title:String="Plan",client:String="omp")="""{"periods":{"today":{"totalTokens":$tokens,"outputTokens":$tokens,"timedTokens":100,"timedOutputTokens":$timed,"timedDurationMs":$duration,"sessions":{"$client:s":{"client":"$client","sessionId":"s","title":"$title","totalTokens":$tokens,"outputTokens":$tokens,"timedOutputTokens":$timed,"timedDurationMs":$duration}}}}}"""
    private fun row(body:String)=HubParser.parse(body).periods.getValue("today").sessions.single()
    @Test fun ompTitlePresentationMatchesFixedDesktopAndRetainsRawMetadata() {
        val reference=Json.parseToJsonElement(javaClass.getResource("/desktop-073-session-titles.json")!!.readText()).jsonObject
        assertEquals("1190260afdcacd8998938de311e0435c306a18ed",reference.getValue("sourceCommit").jsonPrimitive.content)
        for(c in reference.getValue("cases").jsonArray.map {it.jsonObject}) {
            val title=c.getValue("title").jsonPrimitive.content
            val client=c.getValue("client").jsonPrimitive.content
            val source=HubParser.parse(buildJsonObject {put("periods",buildJsonObject {put("today",buildJsonObject {
                put("sessions",buildJsonObject {put("$client:s",buildJsonObject {
                    put("client",client);put("sessionId","s");put("title",title);put("totalTokens",42)
                })})
            })})}.toString()).periods.getValue("today").sessions.single()
            val expected=c.getValue("display").jsonPrimitive.content
            assertEquals("$client: $title",expected,SessionDisplay.title(source))
            assertEquals(expected,SessionListDisplay.raw(listOf(source),now).single().name)
            assertEquals(title.trim(),source.session!!.title)
            assertFalse(SessionListDisplay.raw(listOf(source),now,hideTitles=true).single().name.contains("PLAN:"))
        }
    }
    @Test fun wholeHubRegistryMatchesFixedDesktopIncludingKnownFingerprintChecks() {
        val registry=Json.parseToJsonElement(javaClass.getResource("/desktop-074-hub-build-registry.json")!!.readText()).jsonObject
        val expected=registry.getValue("components").jsonObject.mapValues {(_,rows)->rows.jsonArray.associate {
            it.jsonObject.getValue("revision").jsonPrimitive.long to it.jsonObject.getValue("buildId").jsonPrimitive.content
        }}
        assertEquals(expected,HubBuildRegistry.entries)
        for(rev in listOf(84L,85L)) {
            val build=HubBuildInfo("node-hub",1,rev,expected.getValue("core").getValue(rev),5,expected.getValue("node-hub").getValue(5))
            assertEquals("updateAvailable",build.status)
            assertEquals("unknown",build.copy(coreBuildId="sha256:"+"0".repeat(64)).status)
        }
    }
    @Test fun matchedTimingUsesOnlyTimedOutputIncludingExplicitZero() {
        for((output,expected) in listOf(100 to 100.0,0 to 0.0)) {
            val snapshot=HubParser.parse(wire(timed=output))
            val source=snapshot.periods.getValue("today").sessions.single()
            assertEquals(200L,source.tokens)
            assertEquals(expected,SessionListDisplay.rate(source),0.0)
            assertEquals(expected,snapshot.periods.getValue("today").averageRate!!.speed,0.0)
            val display=SessionListDisplay.raw(listOf(source),now)
            assertEquals(expected.takeIf {it>0},SessionFamilyMetrics.from(display).tokensPerSecond)
        }
        val old=row("""{"periods":{"today":{"sessions":{"omp:s":{"client":"omp","totalTokens":200,"outputTokens":200}}}}}""")
        assertFalse(old.session!!.accounting.timingKnown);assertEquals(0.0,SessionListDisplay.rate(old),0.0)
        assertEquals(0.0,SessionListDisplay.rate(row(wire(duration=0))),0.0)
    }
    @Test fun fixedProducerHubSamplesMatchPeriodSessionAndModelReadings() {
        val reference=Json.parseToJsonElement(javaClass.getResource("/desktop-073-matched-timing.json")!!.readText()).jsonObject
        assertEquals("1190260afdcacd8998938de311e0435c306a18ed",reference.getValue("sourceCommit").jsonPrimitive.content)
        for(c in reference.getValue("cases").jsonArray.map {it.jsonObject}) {
            val wire=c.getValue("wire").toString()
            val snapshot=HubParser.parse(HubJsonProjection.stats(wire.byteInputStream()).body)
            val period=snapshot.periods.getValue("today")
            val expected=c.getValue("speed").jsonPrimitive.double
            assertEquals(c.getValue("id").toString(),expected,period.averageRate!!.speed,0.0)
            assertEquals(c.getValue("sessionSpeed").jsonPrimitive.double,SessionListDisplay.rate(period.sessions.single()),0.0)
            val counters=ThroughputParser.parse(c.getValue("wire").jsonObject.getValue("periods").jsonObject.getValue("today").jsonObject)!!
            assertEquals(expected,if(counters.counters.timedDurationMs>0) counters.counters.timedOutputTokens*1000/counters.counters.timedDurationMs else 0.0,0.0)
            counters.models!!.values.forEach {model->
                assertEquals(expected,if(model.timedDurationMs>0) model.timedOutputTokens*1000/model.timedDurationMs else 0.0,0.0)
            }
        }
    }
    @Test fun correctedFullSnapshotAndFreshnessNeverResurrectOlderUsage() {
        val prior=Json.parseToJsonElement(wire()).jsonObject
        val correction=Json.parseToJsonElement(wire(tokens=0,timed=0)).jsonObject
        val full=StreamMerge.applyStats(prior,"stats",correction)!!
        val freshness=Json.parseToJsonElement("""{"updatedAt":"2026-10-16T09:00:00Z","periods":{"today":{"totalTokens":999,"sessions":{"omp:s":{"totalTokens":999}}}}}""").jsonObject
        val current=StreamMerge.applyStats(full,"freshness",freshness)!!
        for(value in listOf(full,current)) {
            val body=HubJsonProjection.stats(value.toString().byteInputStream()).body
            val snapshot=HubParser.parse(body)
            assertEquals(0L,snapshot.periods.getValue("today").tokens)
            assertEquals(0L,snapshot.periods.getValue("today").sessions.single().tokens)
            assertTrue(SessionListDisplay.raw(snapshot.periods.getValue("today").sessions,now).isEmpty())
            assertTrue(SessionDisplay.source(snapshot).isEmpty())
        }
    }
}
