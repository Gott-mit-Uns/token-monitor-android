package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import java.io.File
import android.graphics.Bitmap
import java.math.BigInteger

class Provider071ScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun body()=InstrumentationRegistry.getInstrumentation().context.assets.open("provider-071-public.json").bufferedReader().use {it.readText()}
    private fun capture(name:String) {
        compose.waitForIdle()
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null),"provider071-$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    @Test fun newAllowanceWindowsAndRefreshKeepAccountExpansionAndScrollInBothThemes() {
        val limits=mutableStateOf(HubParser.parse(body()).limits)
        val dark=mutableStateOf(false)
        lateinit var scroll:androidx.compose.foundation.ScrollState
        val clock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
        compose.activity.runOnUiThread {compose.activity.setContent {
            TokenTheme(dark.value,LanguageChoice.ENGLISH) {
                CompositionLocalProvider(LocalDesktopAnimationClock provides clock) {
                    scroll=rememberScrollState()
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                        Column(Modifier.fillMaxWidth().height(400.dp).verticalScroll(scroll).testTag("provider071-scroll")) {
                            LimitDisplay.groups(limits.value).forEachIndexed {i,windows ->
                                key(windows.first().presentation.identity.key) {DesktopAccount(windows,peers=LimitDisplay.groups(limits.value).map {it.first()},accountIndex=i,now=Instant.parse("2026-10-15T00:00:00Z"))}
                            }
                        }
                    }
                }
            }
        }}
        compose.onAllNodesWithText("Rolling 6 hours").assertCountEquals(2)
        compose.onAllNodesWithText("Reset time unknown").assertCountEquals(4)
        capture("light")
        compose.onNodeWithTag("limit-account-0").performClick()
        compose.onNodeWithTag("limit-expanded-account-0").assertExists()
        compose.onNodeWithTag("limit-account-1").performScrollTo()
        val before=compose.runOnIdle {scroll.value};assertTrue(before>0)
        compose.runOnIdle {limits.value=HubParser.parse(body().replace("\"used\": 25","\"used\": 30").replace("\"usedPercent\": 50","\"usedPercent\": 60").replace("\"remaining\": 25","\"remaining\": 20")).limits;dark.value=true}
        compose.onNodeWithTag("limit-expanded-account-0").assertExists()
        assertEquals(before,compose.runOnIdle {scroll.value})
        compose.onNodeWithTag("limit-account-0").performScrollTo()
        assertTrue(compose.onAllNodesWithText("Account 1",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty())
        capture("dark")
    }
    @Test fun piAndCodebuddySummaryRefreshKeepsOpenDialogAndScrollWithoutChatContent() {
        val snapshot=HubParser.parse(body())
        val source=snapshot.periods.getValue("today").sessions.single()
        val tokens=mutableStateOf(1500L)
        lateinit var scroll:androidx.compose.foundation.ScrollState
        val clock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
        compose.activity.runOnUiThread {compose.activity.setContent {
            TokenTheme(false,LanguageChoice.ENGLISH) {
                CompositionLocalProvider(LocalDesktopAnimationClock provides clock) {
                    scroll=rememberScrollState()
                    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                        repeat(18) {index ->
                            val client=if(index%2==0) "pi" else "codebuddy"
                            val row=source.copy(id="public-$index",tool=client,tokens=tokens.value)
                            val display=SessionListDisplay.raw(listOf(row),Instant.parse("2026-10-15T03:00:00Z").toEpochMilli()).single()
                            key(display.id) {DesktopSessionRow(display,BigInteger.valueOf(3000),Instant.parse("2026-10-15T03:00:00Z").toEpochMilli(),openReview={})}
                        }
                    }
                }
            }
        }}
        compose.onNodeWithTag("session-row-public-15").performScrollTo().performClick()
        compose.onNodeWithTag("session-summary-details").assertExists()
        val before=compose.runOnIdle {scroll.value};assertTrue(before>0)
        compose.runOnIdle {tokens.value=2000}
        compose.onNodeWithTag("session-summary-details").assertExists()
        assertTrue(compose.onAllNodesWithText("2,000 Token",substring=true).fetchSemanticsNodes().isNotEmpty())
        assertEquals(before,compose.runOnIdle {scroll.value})
        capture("session-summary")
    }
    @Test fun fullLimitsPageShowsOnlyCompatibleWorkbuddyDisplayTotalAndRemovesItOnAccountFailure() {
        val state=mutableStateOf(HubState(snapshot=HubParser.parse(body()),history=HubHistory(emptyList(),emptyList())))
        val actions=object:HubUiActions {
            override fun connect(address:String,credential:String,alternate:String) {}
            override fun refreshSaved() {}
            override fun switchEndpoint() {}
            override fun hasAlternate()=false
            override fun endpointType()="https"
            override fun loadHistory() {}
            override fun loadModelNames() {}
            override fun loadSubscriptions() {}
            override fun forget() {}
            override fun renameDevice(id:String,input:String) {}
        }
        val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH,appearance=AppearancePreferences(reduceMotion="on"))
        val clock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(false,prefs.language,prefs.appearance) {
            CompositionLocalProvider(LocalDesktopAnimationClock provides clock) {HubScreen(state.value,actions,preferences=prefs)}
        }}}
        compose.onNodeWithTag("hub-view-selector").performClick()
        compose.onNodeWithTag("view-limits").performScrollTo().performClick()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("workbuddy-display-total"))
        compose.onNodeWithTag("workbuddy-display-total").assertExists()
        compose.onNodeWithText("Independent account balances; cannot be shared").assertExists()
        compose.onNodeWithText("160.00").assertExists()
        capture("workbuddy-total")
        val root=Json.parseToJsonElement(body()).jsonObject
        val limits=root["limits"]!!.jsonObject
        val providers=limits["providers"]!!.jsonArray.mapIndexed {i,p ->if(i==3) JsonObject(p.jsonObject.toMutableMap().apply {put("status",JsonPrimitive("unauthorized"))}) else p}
        val changed=JsonObject(root.toMutableMap().apply {put("limits",JsonObject(limits.toMutableMap().apply {put("providers",JsonArray(providers))}))})
        compose.runOnIdle {state.value=state.value.copy(snapshot=HubParser.parse(changed.toString()))}
        compose.onNodeWithTag("workbuddy-display-total").assertDoesNotExist()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("limit-account-3"))
        compose.onNodeWithText("Sign in again").assertExists()
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
