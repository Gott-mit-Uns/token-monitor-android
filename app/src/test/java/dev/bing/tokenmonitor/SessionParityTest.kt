package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class SessionParityTest {
    private fun data()=javaClass.classLoader!!.getResourceAsStream("session-parity.json")!!.bufferedReader().use {Json.parseToJsonElement(it.readText()).jsonObject}
    private fun names(c:JsonObject)=ModelNames(c.getValue("aliases").jsonObject.mapValues {it.value.jsonPrimitive.content},c.getValue("grouping").jsonPrimitive.content)
    @Test fun originalFallbackRankingAliasesAndRemaindersMatchWithoutSourceMutation() {
        data().getValue("fallback").jsonArray.forEach {raw->val c=raw.jsonObject;val original=c.getValue("input").toString();val snapshot=HubParser.parse(original)
            val rows=SessionFallbackDisplay.rows(snapshot.periods["today"],names(c),snapshot.observedModelIds,DisplayPreferences(modelRanking=c.getValue("metric").jsonPrimitive.content))
            val expected=c.getValue("rows").jsonArray
            assertEquals(c.toString(),expected.map {it.jsonObject.getValue("id").jsonPrimitive.content},rows.map {it.id})
            rows.zip(expected).forEach {(row,value)->val e=value.jsonObject
                assertEquals(e.getValue("tokens").jsonPrimitive.long.toString(),row.tokens?.toString())
                assertEquals(e.getValue("cost").jsonPrimitive.double,row.detail?.cost?.toDouble() ?: 0.0,.000001)
                assertEquals(e.getValue("barValue").jsonPrimitive.double,row.barValue,.000001)
            }
            assertEquals(snapshot,HubParser.parse(original))
        }
    }
    @Test fun originalUpstreamAliasProjectionMatchesRealSessionLabelsSharesAndGlobalObservedContexts() {
        data().getValue("aliasCases").jsonArray.forEach {raw->val c=raw.jsonObject;val snapshot=HubParser.parse(c.getValue("input").toString());val period=snapshot.periods.getValue("today")
            assertEquals(c.getValue("models").jsonObject.mapValues {BigInteger(it.value.jsonPrimitive.content)},names(c).project(period.models,snapshot.observedModelIds))
            c.getValue("sessions").jsonArray.forEach {value->val e=value.jsonObject;val row=period.sessions.single {it.id==e.getValue("id").jsonPrimitive.content}
                assertEquals(e.getValue("label").jsonPrimitive.content,SessionListDisplay.modelLabel(row,names(c),snapshot.observedModelIds))
                assertEquals(e.getValue("shares").jsonArray.map {it.jsonArray.map {v->v.jsonPrimitive.content.replace("__fixture_unclassified__","")}},SessionDisplay.modelShares(row,names(c),snapshot.observedModelIds).map {listOf(it.name,it.tokens.toString(),it.share)})
            }
        }
        val row=UsageRow("public","",Long.MAX_VALUE,null,"codex",null,false,session=SessionMetadata(models=mapOf("a" to Long.MAX_VALUE,"vendor/a" to Long.MAX_VALUE,"b" to 1)))
        val projected=SessionDisplay.displayModels(row,ModelNames(mapOf("vendor/a" to "a"),"off"))
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE)*BigInteger.TWO,projected.getValue("a"))
        assertEquals(Long.MAX_VALUE,row.session!!.models.getValue("vendor/a"))
    }
    @Test fun originalTimestampRuleDoesNotTreatHeartbeatTokenChangesOrFirstPaintAsMovement() {
        data().getValue("sequences").jsonArray.forEach {sequence->val memory=SessionPulseMemory()
            sequence.jsonArray.forEach {value->val c=value.jsonObject
                assertEquals(c.toString(),c.getValue("pulse").jsonPrimitive.boolean,memory.observe(c.getValue("at").jsonPrimitive.long,c.getValue("state").jsonPrimitive.content=="running"))
            }
            memory.clear();assertFalse(memory.observe(999999,true))
        }
    }
    @Test fun originalCssGlowKeyframesAndSegmentEasingMatch() {
        data().getValue("frames").jsonArray.forEach {raw->val c=raw.jsonObject;val frame=SessionRowPulse.frame(c.getValue("ms").jsonPrimitive.float/1200)
            assertEquals(c.toString(),c.getValue("blur").jsonPrimitive.float,frame.blur,.06f)
            assertEquals(c.toString(),c.getValue("opacity").jsonPrimitive.float,frame.opacity,.006f)
        }
    }
}
