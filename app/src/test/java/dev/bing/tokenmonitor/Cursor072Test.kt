package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.Locale

/** Synthetic Hub windows mapped by the pinned 0.72 Cursor collector; no authentication fixture. */
class Cursor072Test {
    private val now=Instant.parse("2026-10-16T00:00:00Z")
    private fun root(providers:String)=Json.parseToJsonElement("""{"periods":{},"limits":{"providers":[$providers]}}""").jsonObject
    private fun provider(windows:String,status:String="ok",stale:Boolean=false,key:String="sha256:aaaaaa")="""{"provider":"cursor","accountKey":"$key","accountEmail":"synthetic@example.invalid","planLabel":"Ultra","status":"$status","stale":$stale,"updatedAt":"2026-10-15T00:00:00Z","windows":[$windows]}"""
    private val included="""{"kind":"weekly","label":"Included usage","usedPercent":25,"windowMinutes":10080,"showMeter":true}"""
    private val prepaid="""{"kind":"billing","label":"Prepaid balance","metric":"credits","currency":"USD","remaining":12.34,"showMeter":false}"""
    private fun parse(raw:JsonObject)=HubParser.parse(raw.toString()).limits

    @Test fun sharedIncludedPoolAndPrepaidAreIndependentInFullAndCompactViews() {
        val limits=parse(root(provider("$included,$prepaid")))
        val record=LimitProviderLayout.record(limits)
        for(used in listOf(false,true)) {
            val rows=LimitProviderLayout.project(record,used,now=now)
            assertEquals(listOf("Included usage","Prepaid balance"),rows.map {it.label})
            assertEquals(if(used) "25% used" else "75% left",rows[0].value)
            assertEquals("Reset time unknown",rows[0].boundary)
            assertEquals("$12.34",rows[1].value)
            assertNull(rows[1].percent);assertEquals("",rows[1].boundary)
            val home=HomeQuotaLayout.project(limits,LimitDisplayPreferences(showUsed=used,homeBars=true),now=now).single()
            assertEquals("Included usage",home.windows[0].label)
            assertEquals("Reset time unknown",home.windows[0].boundary)
            assertEquals("$12.34",home.windows[1].value)
            assertNull(home.windows[1].percent);assertNull(home.windows[1].remaining)
        }
    }
    @Test fun missingUsageIsUnavailableWhileExplicitZeroIsRealAndUnknownCurrencyIsNotUsd() {
        val unknown=included.replace(",\"usedPercent\":25","")
        val record=LimitProviderLayout.record(parse(root(provider(unknown))))
        assertEquals("—",LimitProviderLayout.project(record,now=now).single().value)
        assertTrue(HomeQuotaLayout.project(parse(root(provider(unknown))),LimitDisplayPreferences(),now=now).isEmpty())
        val zero=included.replace("25","0")
        assertEquals("100% left",LimitProviderLayout.project(LimitProviderLayout.record(parse(root(provider(zero)))),now=now).single().value)
        val noCurrency=prepaid.replace(",\"currency\":\"USD\"","")
        assertEquals("—",LimitProviderLayout.project(LimitProviderLayout.record(parse(root(provider(noCurrency)))),now=now).single().value)
    }
    @Test fun prepaidCannotUseHistoricalSpendOrInjectedPercentAsDenominator() {
        val enriched=provider(prepaid.replace("\"showMeter\":false","\"showMeter\":true,\"usedPercent\":90")).replace("\"windows\":","\"balance\":{\"amount\":12.34,\"currency\":\"USD\",\"monthSpend\":300},\"windows\":")
        val limits=parse(root(enriched))
        assertNull(LimitDisplay.creditsPercent(limits.single()))
        val full=LimitProviderLayout.project(LimitProviderLayout.record(limits),now=now).single()
        assertNull(full.percent);assertEquals("$12.34",full.value)
        assertNull(HomeQuotaLayout.project(limits,LimitDisplayPreferences(),now=now).single().windows.single().percent)
    }
    @Test fun legacyPoolsAndSpendRemainSeparateWithoutManufacturingUnifiedData() {
        val windows="""{"kind":"billing","label":"Cursor Models","usedPercent":10},{"kind":"billing","label":"Other Models","usedPercent":30},{"kind":"billing","label":"On-demand spend","metric":"spend","used":3,"limit":10,"currency":"USD","showMeter":false}"""
        val rows=LimitProviderLayout.project(LimitProviderLayout.record(parse(root(provider(windows)))),now=now)
        assertEquals(listOf("Cursor Models","Other Models","On-demand spend"),rows.map {it.label})
        assertEquals("$3.00 / $10.00",rows.last().value)
        val empty=parse(root(provider("",status="unavailable")))
        assertEquals("Allowance unavailable",LimitProviderLayout.project(LimitProviderLayout.record(empty),now=now).single().value)
        val unprovided=parse(root(provider("")))
        assertEquals("额度数据未提供",LimitProviderLayout.project(LimitProviderLayout.record(unprovided),locale=Locale.CHINA,now=now).single().value)
        assertTrue(HomeQuotaLayout.project(unprovided,LimitDisplayPreferences(),now=now).isEmpty())
        assertEquals("Unavailable",LimitProviderLayout.plan(empty.single(),false,false))
    }
    @Test fun accountsAndStaleStateSurviveCacheRoundTripAndFreshnessCannotReplaceWindows() {
        val initial=root(provider("$included,$prepaid",stale=true)+","+provider(included.replace("25","90"),key="sha256:bbbbbb"))
        // Persisted cache stores the complete JSON root; parsing it after a cold
        // restore must retain record identities and provider freshness.
        val restored=Json.parseToJsonElement(initial.toString()).jsonObject
        assertEquals(parse(initial),parse(restored))
        val groups=LimitDisplay.groups(parse(restored));assertEquals(2,groups.size)
        assertNotEquals(LimitDisplay.accountTitle(groups[0][0],groups.map {it.first()},0,false),LimitDisplay.accountTitle(groups[1][0],groups.map {it.first()},1,false))
        assertTrue(LimitProviderLayout.meta(groups[0][0],now,LimitDisplayPreferences()).startsWith("Stale"))
        assertTrue(HomeQuotaLayout.project(groups[0],LimitDisplayPreferences(),now=now).single().plan.endsWith("Stale"))
        assertEquals("Ultra · 数据过期",HomeQuotaLayout.project(groups[0],LimitDisplayPreferences(),locale=Locale.CHINA,now=now).single().plan)
        val updated=StreamMerge.apply(restored,"freshness","""{"stats":{"limits":{"updatedAt":"2026-10-16T00:00:00Z","providers":[]}}}""")!!
        assertEquals(parse(initial),parse(updated))
        val next=root(provider(prepaid,key="sha256:cccccc"))
        val replaced=StreamMerge.apply(updated,"stats",buildJsonObject {put("stats",next)}.toString())!!
        assertEquals(1,LimitDisplay.groups(parse(replaced)).size)
        assertEquals("sha256:cccccc",parse(replaced).single().presentation.identity.key)
        assertEquals(1,parse(replaced).size)
        assertFalse(LimitProviderLayout.meta(parse(replaced).single(),now,LimitDisplayPreferences()).startsWith("Stale"))
    }
    @Test fun monthlyResetUsesSuppliedTimestampAndWeeklyUnknownRemainsExplicitInChinese() {
        val monthly=included.replace("weekly","billing").replace("\"windowMinutes\":10080","\"resetsAt\":\"2026-11-01T00:00:00Z\"")
        assertTrue(LimitProviderLayout.project(LimitProviderLayout.record(parse(root(provider(monthly)))),now=now).single().boundary.startsWith("Reset "))
        for(reset in listOf(null,"invalid-date","2026-10-01T00:00:00Z","2026-11-01T00:00:00Z")) {
            val window=if(reset==null) included else included.replace("\"windowMinutes\":10080","\"windowMinutes\":10080,\"resetsAt\":\"$reset\"")
            val limits=parse(root(provider(window)))
            for(locale in listOf(Locale.US,Locale.CHINA)) {
                val full=LimitProviderLayout.project(LimitProviderLayout.record(limits),locale=locale,now=now).single().boundary
                val home=HomeQuotaLayout.project(limits,LimitDisplayPreferences(),locale=locale,now=now).single().windows.single().boundary
                assertEquals("$reset/$locale reset must match",full,home)
                if(reset!="2026-11-01T00:00:00Z") assertEquals(if(locale.language=="zh") "重置时间未知" else "Reset time unknown",home)
            }
        }
    }
}
