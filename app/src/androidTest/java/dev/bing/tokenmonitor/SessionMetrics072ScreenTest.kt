package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Fixture-only Android rendering and expansion; no production Hub or phone claims. */
class SessionMetrics072ScreenTest {
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
    private fun screenshot(name:String) {
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        val destination=File(compose.activity.getExternalFilesDir(null),"sessions072-$name.png")
        destination.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun check(dark:Boolean) {
        val snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":302,"sessions":{
          "codex:p":{"client":"codex","sessionId":"p","title":"Family Main","totalTokens":100,"messageCount":2,"inputTokens":10,"cacheReadTokens":90,"cacheWriteTokens":0,"outputTokens":10,"timedOutputTokens":1000,"timedDurationMs":1000},
          "codex:c":{"client":"codex","sessionId":"c","title":"Family Child","parentSessionId":"p","totalTokens":200,"messageCount":8,"inputTokens":900,"cacheReadTokens":100,"cacheWriteTokens":0,"outputTokens":100,"timedOutputTokens":100,"timedDurationMs":9000},
          "codex:unknown":{"client":"codex","sessionId":"unknown","title":"Legacy Hub","totalTokens":1},
          "codex:missing":{"client":"codex","sessionId":"missing","parentSessionId":"unknown","totalTokens":1}
        }}}}""")
        val actions=Actions();val state=HubState(snapshot=snapshot,history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"))
        val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList(),appearance=AppearancePreferences(reduceMotion="on"))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.language,prefs.appearance) {HubScreen(state,actions,preferences=prefs)}}}
        compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-sessions").performScrollTo().performClick()
        fun show(tag:String)=compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))
        val id="session-group:subagents:codex:p"
        show("session-row-$id")
        compose.onNodeWithTag("session-activity-$id",true).assertTextEquals("10 calls · 17% · 11 tok/s")
        screenshot(if(dark) "dark-group" else "light-group")
        compose.onNodeWithTag("session-row-$id").performClick()
        show("session-row-codex:p")
        compose.onNodeWithTag("session-activity-codex:p",true).assertTextEquals("2 calls · 90% · 10 tok/s")
        compose.onNodeWithTag("session-role-codex:p",true).assertTextEquals("Main conversation / continuation")
        show("session-row-codex:c")
        compose.onNodeWithTag("session-activity-codex:c",true).assertTextEquals("8 calls · 10% · 11 tok/s")
        screenshot(if(dark) "dark-members" else "light-members")
        show("session-return-list");compose.onNodeWithTag("session-return-list").performClick()
        val unknown="session-group:subagents:codex:unknown"
        show("session-row-$unknown")
        compose.onNodeWithTag("session-activity-$unknown",true).assertTextEquals("Calls unknown · —% · — tok/s")
        screenshot(if(dark) "dark-missing" else "light-missing")
        compose.onNodeWithTag("session-row-$unknown").performClick()
        show("session-row-codex:unknown")
        compose.onNodeWithTag("session-activity-codex:unknown",true).assertTextEquals("Calls unknown · —% · — tok/s")
        screenshot(if(dark) "dark-missing-member" else "light-missing-member")
        assertEquals(0,actions.reads)
        assertEquals(302L,snapshot.periods.getValue("today").tokens)
    }
    @Test fun lightGroupAndExpandedMembersKeepOwnMetrics()=check(false)
    @Test fun darkGroupAndMissingLegacyFieldsStayHonest()=check(true)
}
