package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

class Provider071ScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun body()=InstrumentationRegistry.getInstrumentation().context.assets.open("provider-071-public.json").bufferedReader().use {it.readText()}
    @Test fun newAllowanceWindowsAndRefreshKeepAccountExpansionAndScrollInBothThemes() {
        val limits=mutableStateOf(HubParser.parse(body()).limits)
        val dark=mutableStateOf(false)
        lateinit var scroll:androidx.compose.foundation.ScrollState
        val clock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
        compose.activity.runOnUiThread {compose.activity.setContent {
            TokenTheme(dark.value,LanguageChoice.ENGLISH) {
                CompositionLocalProvider(LocalDesktopAnimationClock provides clock) {
                    scroll=rememberScrollState()
                    Column(Modifier.fillMaxSize().verticalScroll(scroll).testTag("provider071-scroll")) {
                        LimitDisplay.groups(limits.value).forEachIndexed {i,windows ->
                            key(windows.first().presentation.identity.key) {DesktopAccount(windows,peers=LimitDisplay.groups(limits.value).map {it.first()},accountIndex=i,now=Instant.parse("2026-10-15T00:00:00Z"))}
                        }
                    }
                }
            }
        }}
        compose.onAllNodesWithText("Rolling 6 hours").assertCountEquals(2)
        compose.onAllNodesWithText("Reset time unknown").assertCountEquals(4)
        compose.onNodeWithTag("limit-account-0").performClick()
        compose.onNodeWithTag("limit-expanded-account-0").assertExists()
        compose.onNodeWithTag("limit-account-1").performScrollTo()
        val before=compose.runOnIdle {scroll.value};assertTrue(before>0)
        compose.runOnIdle {limits.value=HubParser.parse(body().replace("\"used\": 25","\"used\": 30").replace("\"usedPercent\": 50","\"usedPercent\": 60")).limits;dark.value=true}
        compose.onNodeWithTag("limit-expanded-account-0").assertExists()
        assertEquals(before,compose.runOnIdle {scroll.value})
        compose.onNodeWithTag("limit-account-0").performScrollTo()
        assertTrue(compose.onAllNodesWithText("Account 1",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty())
    }
    @Test fun encryptedSnapshotColdRestoreAndFreshnessRetainNewFieldsAndOldHub() {
        val name="provider071-${UUID.randomUUID()}.bin";val store=HubStore(context,name)
        try {
            val initial=body();store.save(SavedHub("https://public.example.invalid","public-fixture",initial,1))
            val restored=HubStore(context,name).load()!!.parsedSnapshot!!
            assertEquals(4,LimitDisplay.groups(restored.limits).size)
            assertEquals(2,HomeQuotaLayout.project(restored.limits,LimitDisplayPreferences()).count {it.provider=="notion"})
            assertFalse(restored.periods.getValue("today").sessions.single().session!!.accounting.callsKnown)
            val totals=Provider071.workbuddyTotals(SubscriptionAccounts.records(restored.limits).filter {it["provider"]?.jsonPrimitive?.content=="workbuddy"})!!
            assertEquals(160.0,totals.single()["remaining"]!!.jsonPrimitive.double,0.0)
            val root=Json.parseToJsonElement(initial).jsonObject
            val fresh=HubParser.applyFreshness(restored,JsonObject(root.toMutableMap().apply {put("updatedAt",JsonPrimitive("2026-10-15T01:00:00Z"))}))
            assertSame(restored.limits,fresh.limits);assertSame(restored.periods,fresh.periods)
            store.save(SavedHub("https://public.example.invalid","public-fixture","""{"periods":{"today":{"totalTokens":12}}}""",2))
            val old=HubStore(context,name).load()!!.parsedSnapshot!!;assertEquals(12L,old.periods.getValue("today").tokens);assertTrue(old.limits.isEmpty())
        } finally {store.clear()}
    }
}
