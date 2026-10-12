package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Public-only fields; actual drafts, cancellation and saved-credential choices. */
class ConnectionSettingsScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private val snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":1000}},"devices":[]}""")
    private class Actions:HubUiActions {
        var urls=emptyList<String>();var calls=0;var replacement:String?=null
        var accepted:()->Unit={}
        override fun connectionEndpoints()=urls
        override fun connect(address:String,credential:String,alternate:String) {calls++;replacement=credential;urls=listOf(address,alternate);accepted()}
        override fun editConnection(address:String,alternate:String,replacementCredential:String?) {calls++;replacement=replacementCredential;urls=listOf(address,alternate);accepted()}
        override fun refreshSaved() {}
        override fun switchEndpoint() {calls++}
        override fun hasAlternate()=urls.size>1
        override fun endpointType()="https"
        override fun loadHistory() {}
        override fun loadModelNames() {}
        override fun loadSubscriptions() {}
        override fun forget() {calls++;urls=emptyList()}
        override fun renameDevice(id:String,input:String) {}
    }
    private fun save(page:String)=saveVisualEvidence(compose.activity,compose.onRoot(),"connection-$page-${if(dark) "dark" else "light"}.png")
    private fun click(tag:String) {compose.onNodeWithTag(tag).performScrollTo().performClick()}
    @Test fun firstConnectionThenCancelRetainAndReplaceHaveSeparateActions() {
        val state=mutableStateOf(HubState());val draft=HubConnectionDraft();val actions=Actions()
        actions.accepted={state.value=HubState(snapshot=snapshot,live=true,connectionAttempt=actions.calls.toLong(),connectionRevision=actions.calls.toLong())}
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,LanguageChoice.ENGLISH) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(20.dp).imePadding().verticalScroll(rememberScrollState())) {HubConnectionSettings(state.value,actions,true,null,null,{},draft)}
        }}}
        compose.onNodeWithTag("connection-address").assertExists();compose.onNodeWithTag("connection-status").assertDoesNotExist()
        save("initial")
        compose.onNodeWithTag("connection-address").performTextReplacement("https://public.example.invalid")
        compose.onNodeWithTag("connection-alternate").performTextReplacement("http://192.168.1.1:17321")
        compose.onNodeWithTag("connection-secret").performTextReplacement("public-fixture-value")
        compose.onNodeWithTag("connection-secret").assert(SemanticsMatcher.expectValue(SemanticsProperties.Password,Unit))
        click("connection-save")
        compose.waitUntil {actions.calls==1 && !draft.editing && draft.secret.isEmpty()}
        compose.onNodeWithTag("connection-status").assertTextEquals("Connected")
        compose.onNodeWithTag("connection-endpoint").assertTextEquals("public.example.invalid")
        compose.onNodeWithTag("connection-secret").assertDoesNotExist();save("connected")
        click("connection-edit");compose.onNodeWithTag("connection-address").assertTextEquals("https://public.example.invalid")
        compose.onNodeWithTag("connection-secret").assertDoesNotExist()
        compose.onNodeWithTag("connection-alternate").performTextReplacement("http://192.168.1.2:17321")
        click("connection-cancel");assertEquals(1,actions.calls);assertEquals("http://192.168.1.1:17321",actions.urls[1])
        click("connection-edit");save("editing")
        compose.onNodeWithTag("connection-alternate").performTextReplacement("http://192.168.1.2:17321")
        click("connection-save");compose.waitUntil {actions.calls==2 && !draft.editing};assertNull(actions.replacement)
        assertEquals("http://192.168.1.2:17321",actions.urls[1])
        click("connection-edit");click("connection-credential-mode-replace")
        compose.onNodeWithTag("connection-secret").performTextReplacement("public-replacement")
        click("connection-cancel");assertEquals("",draft.secret);assertEquals(2,actions.calls)
        click("connection-edit");click("connection-credential-mode-replace")
        compose.onNodeWithTag("connection-secret").performTextReplacement("public-replacement")
        click("connection-save");compose.waitUntil {actions.calls==3 && !draft.editing}
        assertEquals("public-replacement",actions.replacement);assertEquals("",draft.secret)
    }
    @Test fun actualSettingsKeepsFailedDraftAndShowsSummaryAfterSuccessfulConnection() {
        val state=mutableStateOf(HubState());val actions=Actions()
        var success=false
        actions.accepted={state.value=if(success) HubState(snapshot=snapshot,live=true,history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"),connectionAttempt=actions.calls.toLong(),connectionRevision=actions.calls.toLong()) else HubState(error=R.string.error_read,connectionAttempt=actions.calls.toLong())}
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,LanguageChoice.ENGLISH) {HubScreen(state.value,actions,preferences=DisplayPreferences(language=LanguageChoice.ENGLISH))}}}
        compose.onNodeWithTag("hub-settings-toggle").performClick()
        fun field(tag:String,value:String) {compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performTextReplacement(value)}
        fun submit() {compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("connection-save"));compose.onNodeWithTag("connection-save").performClick()}
        field("connection-address","https://public.example.invalid");field("connection-secret","public-fixture-value")
        submit();compose.waitUntil {state.value.error!=null}
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("connection-secret"))
        val masked=compose.onNodeWithTag("connection-secret").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertTrue("Failed password remains present and masked",masked.length=="public-fixture-value".length)
        assertTrue(actions.replacement=="public-fixture-value")
        compose.onAllNodesWithTag("connection-secret").assertCountEquals(1)
        success=true;submit();compose.waitUntil {state.value.snapshot!=null && compose.onAllNodesWithTag("hub-total-tokens").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"sync")
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("connection-status"))
        compose.onNodeWithTag("connection-status").assertTextEquals("Connected")
        compose.onNodeWithTag("connection-secret").assertDoesNotExist();assertEquals(2,actions.calls);assertTrue(actions.replacement=="public-fixture-value");save("host-connected")
        compose.onNodeWithTag("connection-edit").performClick()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("connection-credential-mode-replace"))
        compose.onNodeWithTag("connection-credential-mode-replace").performClick()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("connection-secret"))
        compose.onNodeWithTag("connection-secret").assertTextEquals("");save("host-editing")
    }
    @Test fun pendingFailurePermissionAndForgottenConnectionUseActualState() {
        val state=mutableStateOf(HubState(snapshot=snapshot,loading=true));val draft=HubConnectionDraft();val actions=Actions()
        actions.urls=listOf("https://public.example.invalid","http://192.168.1.1:17321")
        var permission=0;var system=0;var forgot=0
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,LanguageChoice.CHINESE) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(20.dp).verticalScroll(rememberScrollState())) {HubConnectionSettings(state.value,actions,false,{permission++},{system++},{forgot++},draft)}
        }}}
        compose.onNodeWithTag("connection-status").assertTextEquals("连接中…");compose.onNodeWithTag("connection-edit").assertIsNotEnabled()
        compose.onNodeWithTag("connection-switch").assertIsNotEnabled();save("pending")
        compose.runOnIdle {state.value=state.value.copy(loading=false,error=R.string.error_read)}
        compose.onNodeWithTag("connection-status").assertTextEquals("无法连接到 Hub");save("failed")
        click("connection-permission");click("connection-system-settings");assertEquals(1,permission);assertEquals(1,system);assertEquals(0,actions.calls)
        click("connection-edit");assertTrue(draft.editing)
        compose.onNodeWithTag("connection-save").performScrollTo().assertIsNotEnabled()
        assertEquals(0,actions.calls)
        compose.onNodeWithTag("connection-alternate").performScrollTo().performTextReplacement("http://192.168.1.2:17321")
        compose.onNodeWithTag("connection-save").performScrollTo().assertIsEnabled()
        actions.accepted={state.value=state.value.copy(error=R.string.error_read,connectionAttempt=actions.calls.toLong())}
        click("connection-save");compose.waitUntil {actions.calls==1 && !draft.submitted}
        assertTrue(draft.editing);compose.onNodeWithTag("connection-address").assertExists()
        save("permission")
        click("connection-cancel");click("connection-forget");assertEquals(1,forgot);assertTrue(actions.urls.isEmpty());assertEquals("",draft.secret)
    }
}
