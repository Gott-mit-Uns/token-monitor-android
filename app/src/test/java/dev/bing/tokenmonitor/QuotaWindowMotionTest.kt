package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.Locale

class QuotaWindowMotionTest {
    private val old="2026-10-11T00:00:00Z"
    private val next="2026-10-12T00:00:00Z"
    private fun target(key:String="a",remaining:Double=20.0,display:Double=remaining,at:String?=old,suffix:String?="left")=
        QuotaWindowTarget(key,remaining,display,at,suffix)
    private fun render(model:QuotaWindowMotion,rows:List<QuotaWindowTarget>?,time:Long=0,view:String="LIMITS",
        shown:Boolean=true,animate:Boolean=true,token:Any=Any(),source:Long=1)=
        model.render(rows,source,view,token,time*1_000_000,shown,animate)
    private fun step(model:QuotaWindowMotion,time:Long)=model.advance(time*1_000_000)

    @Test fun resetThresholdAndAdvancedCycleRejectDataCorrectionsButPermitMissingBoundaries() {
        assertTrue(QuotaResetPolicy.shouldReset(target(remaining=99.499),target(remaining=99.5,at=next)))
        assertFalse(QuotaResetPolicy.shouldReset(target(remaining=99.5),target(remaining=100.0,at=next)))
        assertFalse(QuotaResetPolicy.shouldReset(target(),target(remaining=99.499,at=next)))
        assertFalse(QuotaResetPolicy.shouldReset(target(),target(remaining=100.0,at=old)))
        assertFalse(QuotaResetPolicy.shouldReset(target(at=next),target(remaining=100.0,at=old)))
        assertTrue(QuotaResetPolicy.shouldReset(target(at=null),target(remaining=100.0,at=next)))
        assertTrue(QuotaResetPolicy.shouldReset(target(),target(remaining=100.0,at="not a date")))
        assertFalse(QuotaResetPolicy.shouldReset(target().copy(remainingPercent=null),target(remaining=100.0,at=next)))
        assertEquals(1460L,QuotaResetPolicy.duration(20.0,100.0))
        assertEquals(1530L,QuotaResetPolicy.duration(90.0,0.0))
        assertEquals(1100L,QuotaResetPolicy.duration(null,100.0))
    }

    @Test fun stableIdentityDoesNotUseIndexEmailCaseFreshnessOrCycleAndAmbiguityIsRejected() {
        fun provider(vararg values:Pair<String,JsonElement>)=JsonObject(mapOf("provider" to JsonPrimitive("Codex"),*values))
        val email=provider("accountEmail" to JsonPrimitive(" USER@EXAMPLE.TEST "))
        assertEquals(QuotaResetPolicy.providerKey(email),QuotaResetPolicy.providerKey(provider("accountEmail" to JsonPrimitive("user@example.test"))))
        val account=provider("accountKey" to JsonPrimitive("fixture-account"),"accountEmail" to JsonPrimitive("a@example.test"))
        assertEquals(QuotaResetPolicy.providerKey(account),QuotaResetPolicy.providerKey(JsonObject(account+mapOf("accountEmail" to JsonPrimitive("changed@example.test"),"updatedAt" to JsonPrimitive(next)))))
        assertNotEquals(QuotaResetPolicy.providerKey(account),QuotaResetPolicy.providerKey(provider("accountKey" to JsonPrimitive("other-account"))))
        val window=buildJsonObject {put("kind","session");put("limitId","stable-window");put("remainingPercent",20);put("resetsAt",old)}
        val changed=JsonObject(window+mapOf("remainingPercent" to JsonPrimitive(100),"resetsAt" to JsonPrimitive(next)))
        assertEquals(QuotaResetPolicy.windowKey("Session",window),QuotaResetPolicy.windowKey("Session",changed))
        val model=QuotaWindowMotion()
        render(model,listOf(target(),target()),animate=false)
        render(model,listOf(target(remaining=100.0,at=next),target(remaining=100.0,at=next)))
        assertFalse(model.running);assertEquals(0,model.retainedMotionCount)
    }

    @Test fun standardIsoCycleFormsAndWebAccountFallbackSurviveNativeDisplayProjection() {
        assertFalse(QuotaResetPolicy.shouldReset(target(at="2026-10-11"),target(remaining=100.0,at="2026-10-11")))
        assertFalse(QuotaResetPolicy.shouldReset(target(at="2026-10-12"),target(remaining=100.0,at="2026-10-11")))
        assertTrue(QuotaResetPolicy.shouldReset(target(at="2026-10-11"),target(remaining=100.0,at="2026-10-12")))
        assertFalse(QuotaResetPolicy.shouldReset(target(at="2026-10-11T00:00:00"),target(remaining=100.0,at="2026-10-11T00:00:00")))
        assertTrue(QuotaResetPolicy.shouldReset(target(at="2026-10-11T00:00:00"),target(remaining=100.0,at="2026-10-12T00:00:00")))
        val raw=buildJsonObject {put("provider","codex");put("webAccountKey","fixture-web-account");put("accountName","Display name")
            put("windows",buildJsonArray {add(buildJsonObject {put("kind","session");put("id","window-id");put("remainingPercent",20)})})}
        val retained=LimitProviderLayout.displayRecord(raw)
        assertEquals(QuotaResetPolicy.providerKey(raw),QuotaResetPolicy.providerKey(retained))
        val changedName=JsonObject(retained+mapOf("accountName" to JsonPrimitive("Changed display name")))
        assertEquals(QuotaResetPolicy.providerKey(retained),QuotaResetPolicy.providerKey(changedName))
        assertEquals(QuotaResetPolicy.windowKey("Session",raw.getValue("windows").jsonArray[0].jsonObject),
            QuotaResetPolicy.windowKey("Session",retained.getValue("windows").jsonArray[0].jsonObject))
    }

    @Test fun oneBatchUsesLongestDurationAcrossAccountsAndPercentageIsEaseOutQuad() {
        val model=QuotaWindowMotion()
        render(model,listOf(target("a",20.0),target("b",90.0)),animate=false)
        render(model,listOf(target("a",100.0,at=next),target("b",100.0,at=next)))
        step(model,16);step(model,746) // 730ms / longest 1460ms = one half.
        assertEquals(80.0,model.frame.meters.getValue("a").percent,.00001)
        assertEquals(97.5,model.frame.meters.getValue("b").percent,.00001)
        assertTrue(model.frame.meters.getValue("b").fill<1f)
        step(model,1476)
        assertEquals(1f,model.frame.meters.getValue("a").fill,0f)
        assertEquals(1f,model.frame.meters.getValue("b").fill,0f)
        assertEquals(.52f,model.frame.meters.getValue("a").glow,.00001f)
        assertTrue(model.running)
        step(model,1923);assertTrue(model.running)
        step(model,1924);assertFalse(model.running) // 1460 + 700 - 252 after the first frame.
    }

    @Test fun usedDisplayMovesTowardZeroWhileRemainingStillDeterminesReset() {
        val model=QuotaWindowMotion()
        render(model,listOf(target(remaining=10.0,display=90.0,suffix="used")),animate=false)
        render(model,listOf(target(remaining=100.0,display=0.0,at=next,suffix="used")))
        step(model,10);step(model,775)
        assertEquals(22.5,model.frame.meters.getValue("a").percent,.00001)
        step(model,1540);assertEquals(0f,model.frame.meters.getValue("a").fill,0f)
    }

    @Test fun freshnessReplacementCarriesOriginalTimelineIncludingCompletionGlow() {
        val model=QuotaWindowMotion();val full=target(remaining=100.0,at=next)
        render(model,listOf(target()),animate=false);render(model,listOf(full))
        step(model,16);step(model,416)
        val before=model.frame.meters.getValue("a")
        render(model,listOf(full),time=416,token=Any())
        assertEquals(before,model.frame.meters.getValue("a"))
        step(model,1476);assertEquals(.52f,model.frame.meters.getValue("a").glow,.00001f)
        render(model,listOf(full),time=1576,token=Any())
        assertTrue(model.frame.meters.getValue("a").glow<.52f)
        step(model,1924);assertFalse(model.running)
    }

    @Test fun actualViewEntryOnlyChangesBarsAndCurrentViewOrdinaryCompositionCannotReplayIt() {
        val model=QuotaWindowMotion();val homeToken=Any();val limitsToken=Any();val row=target(remaining=80.0)
        render(model,null,view="HOME",shown=false,token=homeToken)
        render(model,listOf(row),view="LIMITS",token=limitsToken)
        assertEquals(0f,model.frame.fill(row),0f)
        assertTrue(model.frame.meters.isEmpty()) // Entry does not count the percentage from zero.
        step(model,210);val halfway=model.frame.fill(row)
        assertTrue(halfway>0f && halfway<.8f)
        render(model,listOf(row),time=210,token=limitsToken)
        assertEquals(halfway,model.frame.fill(row),0f)
        step(model,420);assertFalse(model.running);assertEquals(.8f,model.frame.fill(row),0f)
        render(model,listOf(row),time=500,token=limitsToken);assertFalse(model.running)
        render(model,null,time=510,view="MODELS",shown=false)
        render(model,listOf(row),time=600,view="LIMITS",token=Any())
        assertEquals(0f,model.frame.fill(row),0f)
    }

    @Test fun ordinaryConsumptionAccountReplacementAndMeterlessRowsStayStaticWithNoFortyRowGate() {
        val model=QuotaWindowMotion()
        render(model,List(64) {target("account-$it",20.0)},animate=false)
        render(model,List(64) {target("account-$it",100.0,at=next)})
        step(model,16);assertEquals(64,model.retainedMotionCount)
        model.settle()
        render(model,listOf(target("new-account",100.0,at=next)));assertFalse(model.running)
        render(model,listOf(target("new-account",90.0,at=next)));assertFalse(model.running)
        render(model,listOf(target("new-account",100.0,at=next)));assertFalse(model.running)
        render(model,listOf(target("new-account",10.0,at=next).copy(displayPercent=null)),animate=false)
        render(model,listOf(target("new-account",100.0,at="2026-10-13T00:00:00Z").copy(displayPercent=null)))
        assertFalse(model.running)
    }

    @Test fun stopHiddenReductionAndNewSourceConsumePendingEntryWithoutReplay() {
        val model=QuotaWindowMotion();val full=target(remaining=100.0,at=next)
        render(model,listOf(target()),animate=false);render(model,listOf(full));step(model,16)
        assertTrue(model.running)
        model.stop();assertFalse(model.running)
        // No intermediate hidden render: lifecycle events can coalesce.
        render(model,listOf(full),time=100);assertFalse(model.running)
        render(model,null,time=200,shown=false);render(model,listOf(target(remaining=70.0,at=next)),time=300)
        assertFalse(model.running)
        render(model,listOf(target(remaining=100.0,at="2026-10-13T00:00:00Z")),time=400,animate=false)
        assertFalse(model.running)
        render(model,listOf(target(remaining=100.0,at="2026-10-13T00:00:00Z")),time=500)
        assertFalse(model.running)
        render(model,null,time=600,view="MODELS",shown=false);model.stop()
        render(model,listOf(full),time=700,view="LIMITS",source=2);assertFalse(model.running)
    }

    @Test fun projectionPreservesNativeValueOverridesAndHasOpaqueStableWindowMetadata() {
        val provider=buildJsonObject {put("provider","codex");put("accountKey","fixture-account");put("windows",buildJsonArray {
            add(buildJsonObject {put("kind","session");put("limitId","session");put("remainingPercent",20);put("resetsAt",old)})
            add(buildJsonObject {put("kind","weekly");put("limitId","weekly");put("remainingPercent",40);put("resetsAt",old)})
        })}
        val rows=LimitProviderLayout.project(provider,false,Locale.US,Instant.parse(old))
        assertEquals("20% left",rows[0].value);assertEquals("left",rows[0].motion!!.percentSuffix)
        assertNotEquals(rows[0].motion!!.key,rows[1].motion!!.key)
        assertFalse(rows[0].motion!!.key.contains("fixture-account"))
        val reversed=JsonObject(provider+mapOf("windows" to JsonArray(provider.getValue("windows").jsonArray.reversed())))
        val again=LimitProviderLayout.project(reversed,false,Locale.US,Instant.parse(old))
        assertEquals(rows[0].motion!!.key,again.first {it.label=="Session"}.motion!!.key)
        val credits=buildJsonObject {put("provider","openrouter");put("balance",buildJsonObject {put("amount",50);put("currency","USD")});put("windows",buildJsonArray {
            add(buildJsonObject {put("kind","billing");put("metric","credits");put("label","Credits");put("remainingPercent",50);put("remaining",50);put("limit",100)})
        })}
        val credit=LimitProviderLayout.project(credits).first()
        assertEquals("$50.00",credit.value)
        assertNotNull(credit.motion);assertNull(credit.motion!!.percentSuffix)
    }
}
