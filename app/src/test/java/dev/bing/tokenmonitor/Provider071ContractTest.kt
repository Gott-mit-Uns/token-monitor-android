package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class Provider071ContractTest {
    private fun obj(body:String)=Json.parseToJsonElement(body).jsonObject
    private val keyA="sha256:"+"a".repeat(64)
    private val keyB="sha256:"+"b".repeat(64)
    private fun notion(key:String=keyA,status:String="ok")=obj("""{"provider":"notion","status":"$status","accountKey":"$key","planLabel":"Business","source":"web","windows":[
        {"kind":"session","label":"Rolling","windowMinutes":360,"used":25,"limit":50,"usedPercent":50,"remaining":25,"showMeter":true},
        {"kind":"billing","label":"Monthly","used":100,"limit":400,"usedPercent":25,"remaining":300,"resetDescription":"Monthly","showMeter":true}]}""")
    private fun buddy(key:String=keyA,status:String="ok",stale:Boolean=false,currency:String="CREDITS",end:String="2026-11-01T00:00:00Z",region:String="cn")=obj("""{"provider":"workbuddy","accountKey":"$key","accountName":"Public same name","accountEmail":"same@example.test","region":"$region","status":"$status","stale":$stale,"windows":[{"kind":"billing","metric":"credits","currency":"$currency","used":20,"remaining":80,"limit":100,"resetsAt":"$end"}]}""")
    private fun snapshot(vararg providers:JsonObject)=HubParser.parse(buildJsonObject {
        put("periods",obj("""{"today":{"totalTokens":120,"costUsd":"1.25"}}"""))
        put("limits",buildJsonObject {put("providers",JsonArray(providers.toList()))})
    }.toString())
    @Test fun notionRollingAndMonthlyAreAllowanceNotMoneyAndUnknownResetStaysUnknown() {
        val snap=snapshot(notion())
        val rows=LimitProviderLayout.project(LimitProviderLayout.record(snap.limits))
        assertEquals(listOf("Rolling 6 hours","Monthly"),rows.map {it.label})
        assertEquals(listOf("50% left","75% left"),rows.map {it.value})
        assertEquals(listOf("25/50","300/400"),rows.map {it.detail})
        assertTrue(rows.all {it.boundary=="Reset time unknown" && it.item !in setOf("credits","spend")})
        assertTrue(snap.limits.all {it.currency==null && it.metric==null && it.presentation.balanceAmount==null})
        assertEquals(120L,snap.periods.getValue("today").tokens);assertEquals("1.25",snap.periods.getValue("today").cost)
        val home=HomeQuotaLayout.project(snap.limits,LimitDisplayPreferences(),now=Instant.parse("2026-10-15T00:00:00Z")).single()
        assertEquals(listOf("Rolling 6 hours","Monthly"),home.windows.map {it.label});assertTrue(home.windows.all {it.boundary=="Reset time unknown"})
    }
    @Test fun notionDistinctWorkspacesStayDistinctWithoutRawIdentityOrWorkspaceNames() {
        val extra=JsonObject(notion().toMutableMap().apply {put("workspaceId",JsonPrimitive("public-workspace"));put("accountName",JsonPrimitive("Public workspace name"));put("accountEmail",JsonPrimitive("same@example.test"));put("balance",obj("""{"amount":999,"currency":"USD"}"""))})
        val snap=snapshot(extra,notion(keyB));val groups=LimitDisplay.groups(snap.limits)
        assertEquals(2,groups.size);assertEquals(listOf(keyA,keyB),groups.map {it.first().presentation.identity.key})
        assertEquals(listOf("Account 1","Account 2"),groups.mapIndexed {i,w->LimitDisplay.accountTitle(w.first(),groups.map {it.first()},i,false)})
        val display=groups.first().first().presentation.providerRecord.toString()
        assertFalse(display.contains("public-workspace"));assertFalse(display.contains("workspace name"));assertFalse(display.contains("same@example.test"));assertFalse(display.contains("balance"))
    }
    @Test fun unknownNotionResponsesAndStatusesNeverBecomeFullAllowance() {
        val invalid=listOf(notion(status="unavailable"),notion(status="future"),JsonObject(notion().toMutableMap().apply {put("windows",JsonArray(listOf(obj("""{"kind":"session","used":0,"limit":100,"windowMinutes":60,"remainingPercent":100}"""))))}))
        invalid.forEach {raw ->
            val snap=snapshot(raw);val record=LimitProviderLayout.record(snap.limits)
            assertTrue(LimitProviderLayout.project(record).isEmpty());assertTrue(HomeQuotaLayout.project(snap.limits,LimitDisplayPreferences()).isEmpty());assertNull(snap.limits.single().remaining)
        }
    }
    @Test fun notionOverLimitClampsDisplayAndRetainsActualCountsAndKnownReset() {
        val raw=JsonObject(notion().toMutableMap().apply {put("windows",JsonArray(listOf(obj("""{"kind":"session","windowMinutes":360,"used":120,"limit":100,"usedPercent":120,"remaining":0,"resetsAt":"2026-10-15T06:00:00Z"}"""))))})
        val windows=snapshot(raw).limits;assertEquals(0.0,LimitDisplay.percent(windows.single(),false)!!,0.0)
        val rows=LimitProviderLayout.project(LimitProviderLayout.record(windows),now=Instant.parse("2026-10-15T00:00:00Z"));assertEquals("Reset 6h 0m",rows.single().boundary);assertEquals("0/100",rows.single().detail)
    }
    @Test fun workbuddyFailureNeverRebindsToAnotherSameEmailOrName() {
        val subscription=SubscriptionParser.parse("""{"subscriptions":[{"provider":"workbuddy","binding":{"accountKey":"$keyA","accountEmail":"same@example.test","profileName":"Public same name"}}]}""").single()
        assertNull(SubscriptionAccounts.match(subscription,listOf(buddy(keyB))))
        val failed=buddy(status="unauthorized")
        assertEquals("unauthorized",SubscriptionAccounts.match(subscription,listOf(failed,buddy(keyB)))!!["status"]!!.jsonPrimitive.content)
        val emailOnly=subscription.copy(binding=SubscriptionBinding(accountEmail="same@example.test"))
        assertNull(SubscriptionAccounts.match(emailOnly,listOf(failed,buddy(keyB))))
        assertEquals(2,SubscriptionAccounts.records(snapshot(failed,buddy(keyB)).limits).size)
    }
    @Test fun workbuddyTotalRequiresAllHealthyAccountsAndIdenticalEntitlements() {
        val a=buddy();val b=buddy(keyB)
        val total=Provider071.workbuddyTotals(listOf(a,b))!!.single()
        assertEquals(160.0,total["remaining"]!!.jsonPrimitive.double,0.0)
        assertEquals(200.0,total["limit"]!!.jsonPrimitive.double,0.0)
        for(invalid in listOf(buddy(keyB,"unauthorized"),buddy(keyB,"unavailable"),buddy(keyB,stale=true),buddy(keyB,currency="USD"),buddy(keyB,end="2026-11-02T00:00:00Z"),buddy(keyB,region="ai"),buddy(keyA),JsonObject(b.toMutableMap().apply {remove("accountKey")}))) assertNull(Provider071.workbuddyTotals(listOf(a,invalid)))
        assertNull(Provider071.workbuddyTotals(listOf(a)))
        val unlimited=JsonObject(b.toMutableMap().apply {put("windows",JsonArray(listOf(obj("""{"kind":"billing","metric":"credits","currency":"CREDITS","detail":"unlimited"}"""))))})
        assertNull(Provider071.workbuddyTotals(listOf(a,unlimited)))
    }
    @Test fun gooseUsesAuthoritativeCreationDayTotalsWithoutAddingCacheOrReasoningOrFakeCalls() {
        val snap=HubParser.parse("""{"periods":{"today":{"totalTokens":0},"month":{"totalTokens":1500,"inputTokens":1000,"cacheReadTokens":400,"outputTokens":500,"reasoningTokens":200,"clients":{"goose":1500},"sessions":{"goose:public":{"client":"goose","sessionId":"public","startedAt":"2026-10-14T01:00:00Z","lastUsedAt":"2026-10-15T01:00:00Z","totalTokens":1500,"inputTokens":1000,"cacheReadTokens":400,"outputTokens":500,"reasoningTokens":200,"messageCount":1}}}}}""")
        assertEquals(0L,snap.periods.getValue("today").tokens);val month=snap.periods.getValue("month")
        assertEquals(1500L,month.tokens);assertEquals(1500L,month.tools["goose"]);assertEquals(1500L,month.sessions.single().tokens)
        val row=month.sessions.single();assertFalse(row.session!!.accounting.callsKnown);assertEquals(0.0,row.session!!.accounting.calls,0.0)
        assertEquals(40.0,SessionListDisplay.cachePercent(row)!!,0.0)
        val display=SessionListDisplay.raw(listOf(row),Instant.parse("2026-10-15T03:00:00Z").toEpochMilli()).single()
        assertTrue((display.subtitle+display.activity).contains("Calls unknown"));assertFalse((display.subtitle+display.activity).contains("1 call"))
        assertEquals("Goose",toolLabel("goose"));assertTrue("goose" in desktopManagedToolIds)
    }
    @Test fun gooseSourceChecksAdmitOnlyPublicDbTypeAndUnknownEnumsFallBack() {
        val snap=HubParser.parse("""{"periods":{},"devices":[{"deviceId":"public","agentRuntime":"electron-widget","clientHealth":{"version":1,"clients":{"goose":{"overall":"healthy","source":{"state":"detected","checks":[{"id":"goose-db","exists":true},{"id":"public-path","exists":true}]},"collection":{"state":"future"},"data":{"liveTokens":1500}}}}}]}""")
        val device=snap.devices.single();val health=device.health!!.clients.single()
        assertEquals(listOf(ClientSourceCheck("goose-db",true)),health.sourceChecks);assertEquals("unknown",health.collection);assertEquals("electron-widget",device.agentRuntime)
        assertTrue(HubParser.parse("""{"periods":{"today":{"totalTokens":12}}}""").limits.isEmpty())
    }
    @Test fun combinedVersionComparesAndroidSequenceAndRetainsFullReleaseIdentity() {
        val candidate=AndroidReleaseVersion.parse("0.71.0-android.0.2.65")!!
        assertEquals("0.71.0-android.0.2.65",candidate.toString());assertTrue(candidate>AndroidReleaseVersion.parse("0.2.64")!!)
        assertTrue(AndroidReleaseVersion.parse("0.71.0-android.0.2.66")!!>candidate)
        assertEquals(null,AndroidReleaseVersion.parse("0.71.0-android.future"))
        assertEquals("${AndroidReleases.repository}/releases/tag/v0.2.65",AndroidReleases.currentNotes(candidate.toString()))
    }
}
