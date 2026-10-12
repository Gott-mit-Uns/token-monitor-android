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

class HomeSessionScreenTest {
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
    @Test fun originalHomeShowsFiveRecentPlusAllRunningModelsContextRevocationAndDirectHiddenView() {
        val now=Instant.now();val actions=Actions();val dark=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
        fun snapshot(title:Boolean=true)=HubParser.parse(buildJsonObject {
            put("periods",buildJsonObject {
                val sessions=buildJsonObject {
                    for(i in 0..8) put("public:$i",buildJsonObject {
                        put("sessionId","public-session-$i");if(title) put("title","Public title $i");put("projectLabel","Public fallback $i")
                        put("client","codex");put("lastUsedAt",now.minusSeconds(i*5L).toString());put("turnEnded",i<6);put("totalTokens",1000)
                        put("models",buildJsonObject {put("public-alpha",500);put("public-beta",400)})
                        if(i>=6) {put("contextTokens",75000);put("contextWindow",100000);put("promptCache",buildJsonObject {put("observedAt",now.minusSeconds(20).toString());put("ttlSeconds",1800)})}
                    })
                }
                put("month",buildJsonObject {put("totalTokens",9000);put("sessions",sessions)})
                put("today",buildJsonObject {put("totalTokens",1000);put("sessions",buildJsonObject {put("public:0",buildJsonObject {put("title","Duplicate today loses");put("lastUsedAt",now.toString())})})})
                put("allTime",buildJsonObject {put("sessions",buildJsonObject {put("ignored",buildJsonObject {put("title","Wrong all-time source");put("lastUsedAt",now.toString())})})})
            })
        }.toString())
        val state=mutableStateOf(HubState(snapshot=snapshot(),live=true,history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off")))
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=listOf("sessions"),main=MainDisplayPreferences(hiddenViews=setOf("sessions"))))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(state.value,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}
        fun show(id:String)=compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(id))
        show("home-sessions-jump");compose.onNodeWithTag("home-sessions-running",useUnmergedTree=true).assertTextEquals("3 running")
        compose.onNodeWithText("Duplicate today loses").assertDoesNotExist();compose.onNodeWithText("Wrong all-time source").assertDoesNotExist()
        show("home-session-public:6");compose.onNodeWithTag("home-session-title-public:6",useUnmergedTree=true).assertTextEquals("Public title 6")
        compose.onNodeWithTag("home-session-value-public:6",useUnmergedTree=true).assertTextEquals("1K")
        compose.onNodeWithTag("home-session-context-public:6").assertTextEquals("75%")
        saveVisualEvidence(compose.activity,compose.onRoot(),"main-home-sessions-${if(dark) "dark" else "light"}.png")
        compose.onNodeWithTag("home-session-models-public:6").performClick()
        compose.onNodeWithText("public-alpha").assertIsDisplayed();compose.onNodeWithText("public-beta").assertIsDisplayed();compose.onNodeWithText("Unclassified").assertIsDisplayed()
        compose.onNodeWithText("50%").assertIsDisplayed();compose.onNodeWithText("40%").assertIsDisplayed();compose.onNodeWithText("10%").assertIsDisplayed()
        saveVisualEvidence(compose.activity,compose.onNode(isDialog()),"main-session-models-${if(dark) "dark" else "light"}.png")
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("home-session-context-public:6").performClick();compose.onNodeWithText("75K / 100K").assertIsDisplayed()
        compose.onNodeWithText("Cache ~30m left").assertIsDisplayed();compose.onNodeWithText("Close").performClick()
        compose.runOnIdle {prefs.value=DisplayChange.SessionContextMetric("remaining").apply(prefs.value)}
        compose.onNodeWithTag("home-session-context-public:6").assertTextEquals("25%")
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(false))}
        compose.onNodeWithTag("home-session-title-public:6",useUnmergedTree=true).assertTextEquals("Public fallback 6")
        compose.runOnIdle {prefs.value=DisplayChange.HideTitles(true).apply(prefs.value)}
        compose.onNodeWithTag("home-session-title-public:6",useUnmergedTree=true).assertTextEquals("Sessions summary")
        compose.onNodeWithText("Public fallback 6").assertDoesNotExist()
        show("home-session-public:8");compose.onNodeWithTag("home-session-public:8").assertIsDisplayed()
        compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-sessions").assertDoesNotExist();compose.onNodeWithTag("view-home").performScrollTo().performClick()
        show("home-sessions-jump");compose.onNodeWithTag("home-sessions-jump").performClick()
        compose.onNodeWithTag("hub-view-current").assertTextContains("Sessions")
        compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-sessions").assertExists();compose.onNodeWithTag("view-home").performScrollTo().performClick()
        compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-sessions").assertDoesNotExist();compose.onNodeWithTag("view-home").performScrollTo().performClick()
        assertEquals(0,actions.reads)
    }
}
