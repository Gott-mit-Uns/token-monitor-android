package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ConnectionTransactionScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val animationClock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private fun snapshot(tokens:Int)=HubParser.parse("""{"periods":{"today":{"totalTokens":$tokens}},"devices":[]}""")
    private fun click(tag:String) {compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performClick()}
    private fun save(page:String)=saveVisualEvidence(compose.activity,compose.onRoot(),"transaction-$page-${if(dark) "dark" else "light"}.png")
    private open class Actions:HubUiActions {
        var submits=0;var reads=0
        val old=listOf("https://public-old.example.invalid")
        override fun connectionEndpoints()=old
        override fun connect(address:String,credential:String,alternate:String) {submits++}
        override fun editConnection(address:String,alternate:String,replacementCredential:String?) {submits++}
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
    @Test fun failedDraftSurvivesHealthyOldStreamUpdatesAndCancelRestoresOldSummary() {
        val state=mutableStateOf(HubState(snapshot=snapshot(123),live=true,history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"),lastSuccessfulEndpoint="https://public-old.example.invalid",connectedEndpoint="https://public-old.example.invalid"))
        val actions=object:Actions() {
            override fun editConnection(address:String,alternate:String,replacementCredential:String?) {super.editConnection(address,alternate,replacementCredential);state.value=state.value.copy(loading=true,live=false,connectedEndpoint=null,connectionAttempt=state.value.connectionAttempt+1)}
            override fun dismissConnectionFailure() {state.value=state.value.copy(connectionFailure=null,error=null)}
        }
        val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH)
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.language,prefs.appearance) {CompositionLocalProvider(LocalDesktopAnimationClock provides animationClock) {HubScreen(state.value,actions,preferences=prefs)}}}}
        compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"sync");click("connection-edit")
        compose.onNodeWithTag("connection-address").performTextReplacement("https://public-new.example.invalid")
        // This test verifies transaction retention rather than IME touch timing.
        // Invoke the exposed accessible action and establish its selected state
        // before editing the conditional field; keyboard/touch have separate tests.
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("connection-credential-mode-replace"))
        compose.onNodeWithTag("connection-credential-mode-replace").performSemanticsAction(SemanticsActions.OnClick) {it()}.assertIsSelected()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("connection-secret"));compose.onNodeWithTag("connection-secret").performTextReplacement("public-fixture-replacement")
        click("connection-save");assertEquals(1,actions.submits)
        compose.runOnIdle {state.value=state.value.copy(loading=false,error=R.string.error_connection_change,connectionFailure=R.string.error_auth)}
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("connection-change-error"));compose.onNodeWithTag("connection-change-error").assertExists()
        save("failed")
        // A healthy previous stream or detail read can update data and clear its own error.
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(124),live=true,error=null,savedAt=2,connectedEndpoint=actions.old.first())}
        assertEquals(R.string.error_auth,state.value.connectionFailure)
        compose.onNodeWithTag("settings-details-sync").assertExists()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("connection-secret"))
        val mask=compose.onNodeWithTag("connection-secret").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertEquals("public-fixture-replacement".length,mask.length)
        compose.onNodeWithTag("connection-address").assertTextEquals("https://public-new.example.invalid")
        compose.onNodeWithTag("connection-change-error").assertExists();save("old-live")
        click("connection-cancel")
        compose.onNodeWithTag("connection-secret").assertDoesNotExist();compose.onNodeWithTag("connection-change-error").assertDoesNotExist()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("sync-endpoint-address-https"));compose.onNodeWithTag("sync-endpoint-address-https").assertTextEquals("https://public-old.example.invalid")
        compose.onNodeWithTag("sync-endpoint-https").assertIsSelected()
        save("cancel");assertEquals(actions.old,actions.connectionEndpoints());assertEquals(0,actions.reads)
    }
    @Test fun committedSourceRevisionReloadsSameRevisionHistoryWithoutRetainingOldReadGuard() {
        val coordinator=HistoryReadCoordinator()
        val initial=snapshot(123)
        val state=mutableStateOf(HubState(snapshot=initial,live=true,modelNames=ModelNames(emptyMap(),"off"),
            historyReadReceipt=coordinator.target(HistoryReadKey(0,homeHistorySignature(initial)))))
        var historyTokens=123L
        var requestId=0L
        val actions=object:Actions() {
            override fun loadHistory() {loadHistory(false)}
            override fun loadHistory(manual:Boolean):Boolean {
                if(state.value.loading || !state.value.live) return false
                val key=HistoryReadKey(state.value.connectionRevision,homeHistorySignature(state.value.snapshot))
                val start=coordinator.begin(key,++requestId,manual,state.value.snapshot?.historyPreview?.daily?.isNotEmpty()==true) ?: return false
                super.loadHistory()
                state.value=state.value.copy(loading=true,historyReadReceipt=start)
                val history=HubHistory(listOf(HistoryRow(LocalDate.now().toString(),historyTokens,"1")),emptyList())
                state.value=state.value.copy(loading=false,history=history,
                    historyReadReceipt=coordinator.finish(start,HistoryReadPhase.RESOLVED,history.daily.size))
                return true
            }
        }
        val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=listOf("activity"))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.language,prefs.appearance) {CompositionLocalProvider(LocalDesktopAnimationClock provides animationClock) {HubScreen(state.value,actions,preferences=prefs)}}}}
        compose.waitUntil(5000) {actions.reads==1 && state.value.history!=null}
        historyTokens=777
        compose.runOnIdle {
            val next=snapshot(777)
            state.value=state.value.copy(snapshot=next,history=null,connectionRevision=1,
                historyReadReceipt=coordinator.target(HistoryReadKey(1,homeHistorySignature(next))))
        }
        compose.waitUntil(5000) {actions.reads==2 && state.value.history?.daily?.single()?.tokens==777L}
        assertNull(state.value.snapshot?.historyRevision)
        save("new-source")
        compose.runOnIdle {state.value=state.value.copy(history=null,error=R.string.error_connection_change,connectionFailure=R.string.error_auth,connectionRevision=2,
            historyReadReceipt=coordinator.target(HistoryReadKey(2,homeHistorySignature(state.value.snapshot))))}
        compose.waitUntil(5000) {actions.reads==3 && state.value.history!=null}
        assertEquals(0,actions.submits)
    }
}
