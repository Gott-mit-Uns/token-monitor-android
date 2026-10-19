package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

/** Fixed v0.74.0 renderer output, plus defensive checks at the remote viewer boundary. */
class Desktop074CompatibilityTest {
    private val reference=Json.parseToJsonElement(javaClass.getResource("/desktop-074-project-models.json")!!.readText()).jsonObject
    @Test fun fixedDesktopModelSharesSurviveWireProjectionAndFreshness() {
        assertEquals(DesktopParityMetadata.sourceCommit,reference.getValue("sourceCommit").jsonPrimitive.content)
        for(raw in reference.getValue("cases").jsonArray) {
            val c=raw.jsonObject;val wire=c.getValue("wire").toString()
            val full=HubParser.parse(HubJsonProjection.stats(wire.byteInputStream()))
            val fresh=HubParser.applyFreshness(full,Json.parseToJsonElement("""{"updatedAt":"2026-10-18T09:00:00Z","devices":[],"periods":{"today":{"projects":{"fake":{"tokens":999}}}}}""").jsonObject)
            for(snapshot in listOf(full,fresh)) {
                val rows=ProjectDisplay.rows(snapshot.periods.getValue("today"))
                val expected=c.getValue("expected").jsonArray
                assertEquals(c["id"].toString(),expected.size,rows.size)
                rows.zip(expected).forEach {(row,value)->
                    val e=value.jsonObject
                    assertEquals(e.getValue("id").jsonPrimitive.content,row.id)
                    assertEquals(e.getValue("tokens").jsonPrimitive.long,row.tokens.toLong())
                    val modelRows=ProjectDisplay.modelShares(row)
                    val expectedModels=e.getValue("models").jsonArray
                    assertEquals(c["id"].toString(),expectedModels.size,modelRows.size)
                    modelRows.zip(expectedModels).forEach {(m,v)->val model=v.jsonObject
                        assertEquals(model.getValue("id").jsonPrimitive.content,m.id)
                        assertEquals(model.getValue("label").jsonPrimitive.content,m.label)
                        assertEquals(model.getValue("tokens").jsonPrimitive.long,m.tokens.toLong())
                        assertEquals(model.getValue("percent").jsonPrimitive.double,m.percent,1e-9)
                    }
                    assertEquals(row.tokens,modelRows.fold(BigInteger.ZERO) {a,b->a+b.tokens})
                }
            }
        }
    }
    @Test fun aliasesMergeWithoutChangingSourceTotalsOrCostsAndUnknownCannotCollide() {
        val p=HubParser.parse("""{"periods":{"today":{"projects":{"a":{"label":"App","tokens":100,"costUsd":2,"unpricedTokens":10,"models":{"a":20,"b":30,"unknown":10}}}}}}""").periods.getValue("today")
        val row=ProjectDisplay.rows(p).single()
        val names=ModelNames(mapOf("a" to "Shared","b" to "Shared"),"off")
        val models=ProjectDisplay.modelShares(row,names)
        assertEquals(listOf("Shared","Unknown model","unknown"),models.map {it.label})
        assertEquals(listOf(50L,40L,10L),models.map {it.tokens.toLong()})
        assertEquals("2",row.cost);assertEquals(BigInteger.TEN,row.unpriced)
        assertEquals(mapOf("a" to 20L,"b" to 30L,"unknown" to 10L),p.projects.single().project!!.models)
    }
    @Test fun projectModelAggregationDoesNotOverflowLongOrBorrowOtherPeriods() {
        val wire="""{"periods":{"today":{"projects":{"a":{"label":"App","tokens":9223372036854775807,"models":{"alpha":9223372036854775807}},"b":{"label":"APP","tokens":9223372036854775807,"models":{"alpha":9223372036854775807}}}},"month":{"projects":{"a":{"label":"App","tokens":1,"models":{"beta":1}}}}}}"""
        val parsed=HubParser.parse(wire)
        val today=ProjectDisplay.rows(parsed.periods.getValue("today")).single()
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE)*BigInteger.TWO,ProjectDisplay.modelShares(today).single().tokens)
        assertEquals("beta",ProjectDisplay.modelShares(ProjectDisplay.rows(parsed.periods.getValue("month")).single()).single().label)
    }
    @Test fun newRegistryIncludesAllThreeExactCoreFingerprints() {
        for(rev in 86L..88L) {
            val info=HubBuildInfo("node-hub",1,rev,HubBuildRegistry.entries.getValue("core").getValue(rev),5,HubBuildRegistry.entries.getValue("node-hub").getValue(5))
            assertEquals(if(rev==88L) "current" else "updateAvailable",info.status)
            assertEquals("unknown",info.copy(coreBuildId="sha256:"+"0".repeat(64)).status)
        }
    }
    @Test fun projectSwitchDefaultsAndRejectsUnsupportedModes() {
        val original=DisplayPreferences()
        assertEquals("tool",original.projectBreakdownMode)
        assertEquals("model",DisplayChange.ProjectBreakdownMode("model").apply(original).projectBreakdownMode)
        assertEquals(original,DisplayChange.ProjectBreakdownMode("other").apply(original))
    }
    @Test fun correctedProducerGrokOutputIsConsumedOnceAndProjectModelsReachHub() {
        val f=Json.parseToJsonElement(javaClass.getResource("/desktop-074-producer.json")!!.readText()).jsonObject
        assertEquals(DesktopParityMetadata.sourceCommit,f.getValue("sourceCommit").jsonPrimitive.content)
        val grok=HubParser.parse(buildJsonObject {put("periods",buildJsonObject {put("today",f.getValue("grok"))})}.toString()).periods.getValue("today")
        assertEquals(117L,grok.tokens);assertEquals(7L,grok.components.output)
        assertEquals(117L,grok.sessions.single().tokens)
        assertEquals(7.0,grok.sessions.single().session!!.accounting.output,0.0)
        assertEquals("0.25",grok.cost)
        val projects=HubParser.parse(HubJsonProjection.stats(f.getValue("projectWire").toString().byteInputStream())).periods.getValue("today")
        val row=ProjectDisplay.rows(projects).single()
        assertEquals(mapOf("alpha" to BigInteger.valueOf(70),"beta" to BigInteger.valueOf(50)),row.models)
        assertEquals(listOf(70L,50L,30L),ProjectDisplay.modelShares(row).map {it.tokens.toLong()})
    }
    @Test fun correctedHistoryKeepsExplicitZeroClassificationInAllFixedWindows() {
        val f=Json.parseToJsonElement(javaClass.getResource("/desktop-074-producer.json")!!.readText()).jsonObject
        val history=HistoryParser.parse(f.getValue("history").toString())
        assertEquals(0L,history.daily.single().perClient.getValue("grok").components.unclassified)
        val device=Device("public","Public",false,null,"linux",null,periods=mapOf("today" to DeviceUsage(0,"0",emptyMap(),"2026-10-19","2026-10-20T00:00:00Z","UTC")))
        val snapshot=Snapshot(null,emptyMap(),listOf(device),emptyList(),false)
        val histories=DeviceHistories(FixedPeriods.signature(snapshot),listOf(DeviceHistory("public",true,history)))
        for(mode in listOf("week","last7","last30")) {
            val view=FixedPeriods.evaluate(mode,snapshot,histories,java.time.Instant.parse("2026-10-19T00:00:00Z"),java.util.Locale.US)
            // US weeks start on Sunday, so the 18th belongs to all three selected windows.
            val tool=view.period!!.toolDetails.getValue("grok")
            assertEquals(200L,tool.tokens);assertEquals(0L,tool.components.unclassified)
            assertFalse(ToolDetails.tokenRows(tool.components).any {it.key=="unclassified"})
        }
    }

}
