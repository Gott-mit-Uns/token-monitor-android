package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class DesktopSettingsDisplayTest {
    private val reference=Json.parseToJsonElement(javaClass.getResource("/settings-semantics-parity.json")!!.readText()).jsonObject
    @Test fun originalMotionTruthTableAndOffOverridesSystem() {
        reference.getValue("motions").jsonArray.forEach {item->val r=item.jsonObject
            val value=r.getValue("value").jsonPrimitive.content;val system=r.getValue("system").jsonPrimitive.boolean
            assertEquals(r.getValue("normalized").jsonPrimitive.content,DesktopMotionPreference.normalize(value))
            assertEquals(r.getValue("reduced").jsonPrimitive.boolean,DesktopMotionPreference.reduced(value,system))
        }
        assertEquals(1f,DesktopMotionPreference.scale("off",0f),0f)
        assertEquals(0f,DesktopMotionPreference.scale("on",1f),0f)
        assertEquals(0.5f,DesktopMotionPreference.scale("system",0.5f),0f)
    }
    @Test fun originalCostFallbackTiesAndBarValuesDoNotChangeTokenValues() {
        reference.getValue("cases").jsonArray.forEach {item->val r=item.jsonObject;val input=r.getValue("input").jsonArray
            val counts=input.associate {it.jsonObject.getValue("key").jsonPrimitive.content to BigInteger.valueOf(it.jsonObject.getValue("value").jsonPrimitive.long)}
            val details=input.associate {it.jsonObject.getValue("key").jsonPrimitive.content to UsageBreakdown(tokens=it.jsonObject.getValue("value").jsonPrimitive.long,cost=it.jsonObject.getValue("cost").jsonPrimitive.content,components=TokenComponents(null,null,null,null,null))}
            val result=DesktopModelRanking.rows(counts,details,r.getValue("metric").jsonPrimitive.content)
            val expected=r.getValue("expected").jsonArray;assertEquals(expected.size,result.size)
            result.zip(expected).forEach {(row,e)->val x=e.jsonObject
                assertEquals(x.getValue("id").jsonPrimitive.content,row.id)
                assertEquals(BigInteger.valueOf(x.getValue("tokens").jsonPrimitive.long),row.tokens)
                assertEquals(x.getValue("barValue").jsonPrimitive.double,row.barValue,1e-12)
            }
        }
    }
    @Test fun localChangesKeepExistingPresentationAndRejectInvalidStoredChoices() {
        val old=DisplayPreferences(appearance=AppearancePreferences(colors=mapOf("bg" to "#0b0c0e")),main=MainDisplayPreferences(compactTotal=true),currency="CNY")
        val updated=DisplayChange.ModelRanking("cost").apply(DisplayChange.ReduceMotion("on").apply(old))
        assertEquals("cost",updated.modelRanking);assertEquals("on",updated.appearance.reduceMotion)
        assertEquals(old.main,updated.main);assertEquals(old.appearance.colors,updated.appearance.colors);assertEquals(old.currency,updated.currency)
        assertFalse(DesktopAppearance.valid(updated.appearance.copy(reduceMotion="invalid")))
    }
}
