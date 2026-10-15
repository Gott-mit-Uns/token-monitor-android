package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LimitSettingsScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val animationClock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private class Actions:HubUiActions {
        var reads=0
        override fun connect(address:String,credential:String,alternate:String) {reads++}
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
    private fun show(tag:String)=compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))
    private fun child(tag:String)=compose.onNodeWithTag(tag,useUnmergedTree=true)
    private fun save(page:String)=saveVisualEvidence(compose.activity,compose.onRoot(),"limit-settings-$page-${if(dark) "dark" else "light"}.png")
    @Test fun reportedStatusSourcesAndAccountsFollowDesktopSelectionAndLocalPrivacyWithoutReads() {
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH));val actions=Actions()
        val snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":123}},"limits":{"providers":[
          {"provider":"claude","status":"ok","stale":true,"source":"web","sourceDeviceId":"Public desktop","accountName":"Public team","accountEmail":"public@example.com","windows":[]},
          {"provider":"codex","status":"unauthorized","accountEmail":"first@example.com","accountKey":"public-first","accountLabel":"Plus","windows":[]},
          {"provider":"codex","status":"ok","source":"rpc","sourceDetail":"app","sourceDeviceId":"Public workstation","accountEmail":"second@example.com","accountKey":"public-second","planLabel":"Pro","windows":[{"kind":"session","remainingPercent":72}]}
        ]}}""")
        val state=HubState(snapshot=snapshot,history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"))
        compose.activity.runOnUiThread {compose.activity.setContent {CompositionLocalProvider(LocalDesktopAnimationClock provides animationClock) {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(state,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}}
        compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"limits")
        show("limit-provider-row-claude")
        child("limit-provider-status-claude").assertTextEquals("Stale");child("limit-provider-source-claude").assertTextEquals("Web")
        child("limit-provider-device-claude").assertTextEquals("From Public desktop")
        child("limit-provider-detected-claude").assertDoesNotExist()
        child("limit-provider-detected-codex").assertExists()
        compose.onNodeWithTag("limit-items-codex").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Options collapsed · Live"))
        child("limit-provider-source-codex").assertTextEquals("App")
        child("limit-provider-account-count-codex").assertTextEquals("2 accounts")
        child("limit-provider-status-opencode").assertDoesNotExist();child("limit-provider-account-count-opencode").assertDoesNotExist()
        save("overview")
        show("limit-items-codex");compose.onNodeWithTag("limit-items-codex").performClick();show("limit-provider-accounts-codex")
        compose.onNodeWithText("first@example.com",useUnmergedTree=true).assertExists();compose.onNodeWithText("second@example.com",useUnmergedTree=true).assertExists()
        child("limit-provider-account-status-codex-0").assertTextEquals("Sign in again");child("limit-provider-account-status-codex-1").assertTextEquals("Live")
        child("limit-provider-account-plan-codex-0").assertTextEquals("Plus");child("limit-provider-account-plan-codex-1").assertTextEquals("Pro")
        fun textColor(tag:String):Color {
            val layouts=mutableListOf<TextLayoutResult>()
            child(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
            return layouts.single().layoutInput.style.color
        }
        assertEquals(if(dark) Color(0xFFB7EAD4) else Color(0xFF18794E),textColor("limit-provider-account-status-codex-1"))
        val originalAppearance=prefs.value.appearance
        compose.runOnIdle {prefs.value=prefs.value.copy(appearance=originalAppearance.copy(colors=mapOf("bg" to "#ffffff","chart" to "#ff0000","accent" to "#000000")))}
        assertEquals(Color(0xFF18794E),textColor("limit-provider-account-status-codex-1"))
        assertEquals(Color(0xFF73BDF5),textColor("limit-provider-account-source-codex-1"))
        compose.runOnIdle {prefs.value=prefs.value.copy(appearance=originalAppearance)}
        save("accounts")
        val original=snapshot
        compose.runOnIdle {prefs.value=prefs.value.copy(limits=prefs.value.limits.copy(maskEmails=true))}
        compose.onNodeWithText("first@example.com",useUnmergedTree=true).assertDoesNotExist();compose.onNodeWithText("f***t@example.com",useUnmergedTree=true).assertExists()
        assertEquals(original,state.snapshot);save("masked")
        compose.runOnIdle {prefs.value=prefs.value.copy(language=LanguageChoice.CHINESE)}
        child("limit-provider-account-status-codex-0").assertTextEquals(LimitSettingsPresentation.text(java.util.Locale.CHINESE,"Sign in again"));save("chinese")
        assertEquals(0,actions.reads)
    }
}
