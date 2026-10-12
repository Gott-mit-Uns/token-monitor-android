package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.Locale

/** Entry-only targets drive the exact shared production owner used by Home. */
class HomeQuotaEntryPolicyTest {
    private fun render(model:QuotaWindowMotion,hasMeters:Boolean,time:Long=0,view:String="HOME",shown:Boolean=true,
        token:Any=Any(),animate:Boolean=true)=model.render(HomeQuotaEntryPolicy.targets(hasMeters).takeIf {shown},1,view,token,
        time*1_000_000,shown,animate)
    private fun step(model:QuotaWindowMotion,time:Long)=model.advance(time*1_000_000)

    @Test fun initialHomeIsStaticAndTrueReturnHasOnly420msEntryWithoutCurrentViewReplay() {
        val model=QuotaWindowMotion();val token=Any()
        render(model,true,token=token)
        assertNull(model.frame.entryFraction);assertFalse(model.running)
        render(model,false,view="MODELS",shown=false)
        render(model,true,time=100,token=token)
        assertEquals(0f,HomeQuotaEntryPolicy.fill(80.0,model.frame.entryFraction),0f)
        step(model,310);val middle=HomeQuotaEntryPolicy.fill(80.0,model.frame.entryFraction)
        assertTrue(middle>0f && middle<.8f);assertTrue(model.frame.meters.isEmpty())
        render(model,true,time=310,token=token)
        assertEquals(middle,HomeQuotaEntryPolicy.fill(80.0,model.frame.entryFraction),0f)
        step(model,520);assertFalse(model.running);assertNull(model.frame.entryFraction)
        render(model,true,time=600,token=token)
        assertEquals(.8f,HomeQuotaEntryPolicy.fill(80.0,model.frame.entryFraction),0f)
    }

    @Test fun realHomeContentReplacementCancelsEntryAndCannotCreateResetCountOrGlow() {
        val model=QuotaWindowMotion()
        render(model,false,view="MODELS",shown=false);render(model,true)
        step(model,120);assertNotNull(model.frame.entryFraction)
        render(model,true,time=120,token=Any()) // Actual new Home DOM replaces the old fill.
        assertFalse(model.running);assertNull(model.frame.entryFraction)
        assertEquals(1f,HomeQuotaEntryPolicy.fill(100.0,model.frame.entryFraction),0f)
        assertTrue(model.frame.meters.isEmpty());assertEquals(0,model.retainedMotionCount)
        val target=HomeQuotaEntryPolicy.targets(true).single()
        assertNull(target.remainingPercent);assertNull(target.resetsAt);assertNull(target.percentSuffix)
        // Even repeated changes in actual 20→100 Home data cannot add a reset target.
        render(model,true,time=200,token=Any());step(model,2000)
        assertFalse(model.running);assertTrue(model.frame.meters.isEmpty())
    }

    @Test fun stopSettingsEmptyModuleAndReductionConsumeEntryWithoutDelayedReplay() {
        val model=QuotaWindowMotion()
        render(model,false,view="MODELS",shown=false);render(model,true);step(model,80)
        model.stop();render(model,true,time=100)
        assertFalse(model.running);assertNull(model.frame.entryFraction) // No hidden render between STOP/resume.
        render(model,true,time=200,shown=false);render(model,true,time=300)
        assertFalse(model.running) // Settings preserves mainViewKey HOME.
        render(model,false,time=400,view="MODELS",shown=false)
        render(model,false,time=500);render(model,true,time=600)
        assertFalse(model.running) // Empty/text-only Home consumes the route entry.
        render(model,false,time=700,view="MODELS",shown=false)
        render(model,true,time=800,animate=false);render(model,true,time=900)
        assertFalse(model.running);assertTrue(model.frame.meters.isEmpty())
    }

    @Test fun nativeHomeSelectionValuesUsedModeAndMeterEligibilityRemainActualBusinessOutput() {
        val now=Instant.parse("2026-10-11T00:00:00Z")
        val session=Limit("codex","ok",80.0,null,"Public account","session",null,null,null,null,null,null)
        val credits=Limit("codex","ok",50.0,null,"Public account","billing","credits","50","USD",null,null,null)
        val prefs=LimitDisplayPreferences(homeBars=true,showUsed=true)
        val accounts=HomeQuotaLayout.project(listOf(session,credits),prefs,locale=Locale.US,now=now)
        assertEquals(listOf("20% used","$50.00"),accounts.single().windows.map {it.value})
        assertEquals(listOf(20.0,50.0),accounts.single().windows.map {it.percent})
        assertTrue(HomeQuotaEntryPolicy.hasMeters(accounts,true));assertFalse(HomeQuotaEntryPolicy.hasMeters(accounts,false))
        val zero=HomeQuotaLayout.project(listOf(session.copy(remaining=0.0)),prefs.copy(showUsed=false),locale=Locale.US,now=now)
        assertFalse(HomeQuotaEntryPolicy.hasMeters(zero,true))
        assertEquals(.0005f,HomeQuotaEntryPolicy.fill(.05,0f),.0000001f) // Source's small-displacement guard.
        assertEquals(.2f,HomeQuotaEntryPolicy.fill(20.0,null),0f)
    }
}
