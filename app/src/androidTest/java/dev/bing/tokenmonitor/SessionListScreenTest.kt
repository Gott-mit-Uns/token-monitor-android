package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class SessionListScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private class Actions:HubUiActions {
        var reads=0
        override fun connect(address:String,credential:String,alternate:String) {}
        override fun refreshSaved() {reads++}
        override fun switchEndpoint() {}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadHistory() {reads++}
        override fun loadModelNames() {reads++}
        override fun loadSubscriptions() {reads++}
        override fun forget() {}
        override fun renameDevice(id:String,input:String) {}
    }
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private val suffix get()=if(dark) "dark" else "light"
    private fun record(index:Int,now:Instant,review:Boolean=false,titled:Boolean=true)=buildJsonObject {
        put("sessionId","public-session-$index");if(titled) put("title","Public session $index")
        put("client","codex");put("totalTokens",1000);put("costUsd",0.125);put("lastUsedAt",now.minusSeconds(index*3L).toString())
        put("turnEnded",index!=0);put("messageCount",12);put("inputTokens",100);put("cacheReadTokens",300);put("cacheWriteTokens",100)
        put("outputTokens",500);put("timedOutputTokens",450);put("timedDurationMs",9000)
        put("models",buildJsonObject {put("public-alpha",500);put("public-beta",400)})
        if(index==0) {put("contextTokens",75000);put("contextWindow",100000);put("promptCache",buildJsonObject {put("observedAt",now.minusSeconds(20).toString());put("ttlSeconds",1800)})}
        if(review) put("sessionKind","background-review")
    }
    private fun snapshot(now:Instant,count:Int=8,reviews:Int=3,titled:Boolean=true,omitted:Int=2)=HubParser.parse(buildJsonObject {
        put("sessionDetailsOmitted",buildJsonObject {put("today",omitted)})
        put("periods",buildJsonObject {put("today",buildJsonObject {
            put("totalTokens",(count+reviews)*1000);put("costUsd",1.25)
            put("sessions",buildJsonObject {
                repeat(count) {put("public:$it",record(it,now,titled=titled))}
                repeat(reviews) {put("review:$it",record(it+count,now,review=true,titled=titled))}
            })
        })})
    }.toString())
    private fun show(id:String)=compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(id))
    private fun sessions() {compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-sessions").performScrollTo().performClick()}
    @Test fun sourceRowsModelsContextPrivacyAndBackgroundReviewGroup() {
        val now=Instant.now();val actions=Actions();val state=mutableStateOf(HubState(snapshot=snapshot(now),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off")))
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=listOf("sessions")))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(state.value,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}
        sessions();show("session-row-public:0")
        compose.onNodeWithTag("session-title-public:0",true).assertTextEquals("Public session 0")
        compose.onNodeWithTag("session-subtitle-public:0",true).assertTextEquals("Codex · 2 models")
        compose.onNodeWithTag("session-activity-public:0",true).assertTextContains("12 calls · 60% · 50 tok/s",substring=true)
        compose.onNodeWithTag("session-tokens-public:0",true).assertTextEquals("1,000")
        compose.onNodeWithTag("session-running-public:0",true).assertExists();compose.onNodeWithTag("session-running-public:1",true).assertDoesNotExist()
        compose.onNodeWithTag("session-context-public:0").assertTextEquals("75%")
        saveVisualEvidence(compose.activity,compose.onRoot(),"session-list-$suffix.png")
        compose.onNodeWithTag("session-models-public:0").performClick();compose.onNodeWithText("public-alpha").assertIsDisplayed()
        compose.onNodeWithText("Unclassified").assertIsDisplayed();compose.onNodeWithText("50%").assertIsDisplayed()
        saveVisualEvidence(compose.activity,compose.onNode(isDialog()),"session-models-$suffix.png")
        compose.onNodeWithText("Close").performClick();compose.onNodeWithTag("session-context-public:0").performClick()
        compose.onNodeWithText("75K / 100K").assertIsDisplayed();compose.onNodeWithText("Cache ~30m left").assertIsDisplayed()
        saveVisualEvidence(compose.activity,compose.onNode(isDialog()),"session-context-$suffix.png")
        compose.onNodeWithText("Close").performClick()
        compose.runOnIdle {prefs.value=DisplayChange.SessionContextMetric("remaining").apply(prefs.value)}
        compose.onNodeWithTag("session-context-public:0").assertTextEquals("25%")
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(now,titled=false))}
        compose.onNodeWithTag("session-title-public:0",true).assertTextEquals("Codex · 2 models");compose.onNodeWithText("Public session 0").assertDoesNotExist()
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(now));prefs.value=prefs.value.copy(hideTitles=true)}
        compose.onNodeWithTag("session-title-public:0",true).assertTextEquals("Codex · 2 models")
        show("session-row-session-group:codex-auto-review");compose.onNodeWithTag("session-row-session-group:codex-auto-review").performClick()
        show("session-return-list");compose.onNodeWithTag("session-return-list").assertTextContains("Codex Auto Review")
        compose.onNodeWithText("3 background runs").assertIsDisplayed();compose.onNodeWithTag("session-pager").assertDoesNotExist()
        show("session-row-review:0");compose.onNodeWithTag("session-title-review:0",true).assertTextContains("2 models ·",substring=true)
        compose.onNodeWithTag("session-subtitle-review:0",true).assertTextEquals("public-session-8")
        saveVisualEvidence(compose.activity,compose.onRoot(),"session-reviews-$suffix.png")
        show("session-return-list");compose.onNodeWithTag("session-return-list").performClick()
        show("session-row-session-group:codex-auto-review");compose.onNodeWithTag("session-row-session-group:codex-auto-review").assertIsDisplayed()
        compose.onNodeWithTag("session-row-session-group:codex-auto-review").performClick()
        compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithTag("hub-view-current").assertTextContains("Sessions")
        show("session-row-session-group:codex-auto-review");compose.onNodeWithTag("session-row-session-group:codex-auto-review").assertIsDisplayed()
        assertEquals(0,actions.reads)
    }
    @Test fun sourceHundredRowPagesClampOnSseAndReturnHome() {
        val now=Instant.now();val actions=Actions();val state=mutableStateOf(HubState(snapshot=snapshot(now,201,0),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off")))
        val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=listOf("sessions"))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.language,prefs.appearance) {HubScreen(state.value,actions,preferences=prefs)}}}
        show("home-sessions-jump");compose.onNodeWithTag("home-sessions-jump").performClick()
        compose.onNodeWithTag("session-page-status").assertTextEquals("1–100 of 201")
        compose.onNodeWithTag("session-page-previous").assertIsNotEnabled();compose.onNodeWithTag("session-page-next").performClick()
        compose.onNodeWithTag("session-page-status").assertTextEquals("101–200 of 201")
        show("session-row-public:100");compose.onNodeWithTag("session-row-public:100").assertIsDisplayed()
        saveVisualEvidence(compose.activity,compose.onRoot(),"session-pages-$suffix.png")
        compose.onNodeWithTag("session-page-next").performClick();compose.onNodeWithTag("session-page-status").assertTextEquals("201–201 of 201")
        compose.onNodeWithTag("session-page-next").assertIsNotEnabled()
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(now,101,0))}
        compose.onNodeWithTag("session-page-status").assertTextEquals("101–101 of 101")
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(now,8,0))}
        compose.onNodeWithTag("session-pager").assertDoesNotExist();show("session-return-home")
        compose.onNodeWithTag("session-return-home").performClick();compose.onNodeWithTag("hub-view-current").assertTextContains("Home")
        assertEquals(0,actions.reads)
    }
}
