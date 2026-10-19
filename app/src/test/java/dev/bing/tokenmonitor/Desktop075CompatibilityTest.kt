package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class Desktop075CompatibilityTest {
    private val fixture=Json.parseToJsonElement(javaClass.getResource("/desktop-075-sessions.json")!!.readText()).jsonObject
    @Test fun fixedDesktopBotActivityAndIdentitySurviveHttpSseFullAndFreshness() {
        assertEquals(DesktopParityMetadata.sourceCommit,fixture.getValue("sourceCommit").jsonPrimitive.content)
        val now=fixture.getValue("now").jsonPrimitive.long
        for(case in fixture.getValue("cases").jsonArray.map {it.jsonObject}) {
            val wire=case.getValue("wire").toString()
            val http=HubParser.parse(HubJsonProjection.stats(wire.byteInputStream()))
            val sse=HubParser.parse(HubJsonProjection.streamStats("""{"stats":$wire}""".byteInputStream()))
            val fresh=HubParser.applyFreshness(http,Json.parseToJsonElement("""{"devices":[],"periods":{"today":{"sessions":{}}}}""").jsonObject)
            for(snapshot in listOf(http,sse,fresh,HubParser.parse(wire))) {
                val row=snapshot.periods.getValue("today").sessions.single()
                assertEquals(case.getValue("id").toString(),case.getValue("activity").jsonPrimitive.content,SessionDisplay.activity(row,now))
                assertEquals(case.getValue("bot").jsonPrimitive.boolean,SessionDisplay.isGrokBot(row))
                assertEquals(10L,row.tokens);assertEquals("0.1",row.cost)
                val grouped=SessionListDisplay.grouped(SessionListDisplay.raw(listOf(row),now,names=ModelNames(mapOf("grok-bot-default" to "Claude"),"off")),now=now).single()
                assertEquals(java.math.BigInteger.TEN,grouped.tokens)
                if(SessionDisplay.isGrokBot(row)) assertEquals("grok",grouped.groupKind)
            }
        }
    }
    @Test fun fullSnapshotSourceReplacementCannotReviveOldBotIdentityOrUsage() {
        val cross=fixture.getValue("cases").jsonArray.last().jsonObject.getValue("wire").jsonObject
        val original=HubParser.parse(cross.toString())
        assertTrue(SessionDisplay.isGrokBot(original.periods.getValue("today").sessions.single()))
        val replacement=HubParser.parse(buildJsonObject {put("periods",buildJsonObject {put("today",cross.getValue("periods").jsonObject.getValue("today"))})}.toString())
        assertFalse(SessionDisplay.isGrokBot(replacement.periods.getValue("today").sessions.single()))
        assertEquals("running",SessionDisplay.activity(replacement.periods.getValue("today").sessions.single(),fixture.getValue("now").jsonPrimitive.long))
    }
    @Test fun antigravityParentLinksAndDshProjectModelsAreConsumedWithoutDoubleCounting() {
        val wire="""{"periods":{"today":{"totalTokens":60,"sessions":{"antigravity:p":{"client":"antigravity","sessionId":"p","totalTokens":10},"antigravity:c":{"client":"antigravity","sessionId":"c","parentSessionId":"p","title":"Worker","totalTokens":20},"dsh:s":{"client":"dsh","sessionId":"s","projectLabel":"App","totalTokens":30,"models":{"deepseek":30}}},"projects":{"app":{"label":"App","tokens":30,"clients":{"dsh":30},"models":{"deepseek":30}}}}}}"""
        val snapshot=HubParser.parse(HubJsonProjection.stats(wire.byteInputStream()))
        val period=snapshot.periods.getValue("today")
        val groups=SessionListDisplay.grouped(SessionListDisplay.raw(period.sessions,0),now=0)
        val family=groups.single {it.groupKind=="relationship"}
        assertEquals(java.math.BigInteger.valueOf(30),family.tokens)
        assertEquals(listOf("main","subagent"),family.reviews.map {it.groupRole})
        assertEquals(java.math.BigInteger.valueOf(60),groups.fold(java.math.BigInteger.ZERO) {a,b->a+b.tokens})
        val project=ProjectDisplay.rows(period).single()
        assertEquals(mapOf("deepseek" to java.math.BigInteger.valueOf(30)),project.models)
        assertEquals(java.math.BigInteger.valueOf(30),project.tokens)
    }
}
