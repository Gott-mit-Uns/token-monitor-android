package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

class Session069ScreenTest {
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
    private fun body(title:String="Parent")="""{"periods":{"today":{"totalTokens":60,"sessions":{
        "codex:p":{"client":"codex","sessionId":"p","title":"$title","totalTokens":10,"costUsd":0.125,"waitingForInput":true,"lastUsedAt":"${Instant.now()}","models":{"parent-model":10}},
        "codex:c":{"client":"codex","sessionId":"c","title":"Child","totalTokens":20,"costUsd":0.25,"parentSessionId":"p","turnEnded":true,"lastUsedAt":"${Instant.now()}","models":{"child-model":20}},
        "cursor:sand-subagent-b":{"client":"cursor","sessionId":"sand-subagent-b","totalTokens":30,"costUsd":0.5,"models":{"grok-bot-fast":30}}
    }}}}"""
    @Test fun independentGroupsDetailsBackAndFullSnapshotTitleUpdateDoNotReadAgain() {
        val state=mutableStateOf(HubState(snapshot=HubParser.parse(body()),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off")))
        val actions=Actions()
        val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList(),appearance=AppearancePreferences(reduceMotion="on"))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(false,prefs.language,prefs.appearance) {HubScreen(state.value,actions,preferences=prefs)}}}
        compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-sessions").performScrollTo().performClick()
        fun show(tag:String) {compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))}
        val parent="session-row-session-group:subagents:codex:p"
        show(parent);compose.onNodeWithTag("session-tokens-session-group:subagents:codex:p",true).assertTextEquals("30")
        compose.onNodeWithTag(parent).performClick()
        show("session-row-codex:p");compose.onNodeWithTag("session-waiting-codex:p",true).assertTextEquals("Waiting for your reply")
        compose.onNodeWithTag("session-role-codex:p",true).assertTextEquals("Main conversation / continuation")
        show("session-row-codex:c");compose.onNodeWithTag("session-role-codex:c",true).assertTextEquals("Subagent")
        compose.runOnIdle {state.value=state.value.copy(snapshot=HubParser.parse(body("Renamed")))}
        show("session-row-codex:p");compose.onNodeWithTag("session-title-codex:p",true).assertTextEquals("Renamed")
        show("session-return-list");compose.onNodeWithTag("session-return-list").performClick()
        val grok="session-row-session-group:cursor-grok-bot";show(grok);compose.onNodeWithTag(grok).performClick()
        show("session-row-cursor:sand-subagent-b");compose.onNodeWithTag("session-tokens-cursor:sand-subagent-b",true).assertTextEquals("30")
        compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()}
        show(parent);compose.onNodeWithTag(parent).assertExists()
        assertEquals(0,actions.reads)
        assertEquals(60L,state.value.snapshot!!.periods.getValue("today").tokens)
    }
    @Test fun encryptedColdRestoreKeepsOptionalMetadataAndDoesNotExtendExpiredLease() {
        val context=compose.activity
        val name="test-session069-${UUID.randomUUID()}.bin";val store=HubStore(context,name)
        try {
            val expired=Instant.now().minusSeconds(60).toString()
            val snapshot="""{"periods":{"today":{"sessions":{"codex:a":{"client":"codex","sessionId":"a","totalTokens":10,"lastUsedAt":"${Instant.now().minusSeconds(1200)}","nativeSessionId":"11111111-1111-1111-1111-111111111111","parentSessionId":"missing","liveActivity":{"state":"waiting","observedAt":"$expired"}}}}}}"""
            store.save(SavedHub("https://example.invalid",UUID.randomUUID().toString(),snapshot,1,null))
            val loaded=HubStore(context,name).load()!!
            val row=HubParser.parse(loaded.snapshot).periods.getValue("today").sessions.single()
            assertNotNull(row.session!!.nativeSessionId);assertEquals("missing",row.session.parentSessionId)
            assertEquals("idle",SessionDisplay.activity(row,System.currentTimeMillis()))
        } finally {store.clear()}
    }
}
