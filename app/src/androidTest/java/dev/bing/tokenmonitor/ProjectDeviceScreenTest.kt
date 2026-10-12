package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class ProjectDeviceScreenTest {
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
    private fun select(id:String) {compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-$id").performScrollTo().performClick()}
    private fun textIn(tag:String,text:String)=compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)),useUnmergedTree=true).assertExists()
    private fun show(tag:String)=compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))
    @Test fun originalProjectCostOrderNfcMergeToolGradientPrivacyAndPinnedTotal() {
        val actions=Actions();val now=Instant.now().toString()
        val snapshot=HubParser.parse("""{"periodProjectsOmitted":{"today":2},"periods":{"today":{"totalTokens":103000,"costUsd":30,"projects":{"priority":{"label":"Priority","tokens":1000,"costUsd":20,"clients":{"codex":1000}},"big":{"label":"Public project","tokens":60000,"costUsd":1,"clients":{"codex":36000,"claude":18000}},"merged":{"label":" public PROJECT ","tokens":40000,"costUsd":1,"clients":{"codex":24000,"claude":12000}},"last":{"label":"Last","tokens":2000,"costUsd":0.1,"clients":{"codex":2000}}}}},"updatedAt":"$now"}""")
        val state=mutableStateOf(HubState(snapshot=snapshot,history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off")))
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList()))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(state.value,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}
        select("projects");show("project-row-public project")
        compose.onNodeWithTag("project-title-public project",true).assertTextEquals("Public project")
        compose.onNodeWithTag("project-tokens-public project",true).assertTextEquals("100,000")
        compose.onNodeWithTag("project-row-public project").performClick()
        textIn("project-tool-public project-0","Codex")
        textIn("project-tool-public project-1","Claude Code")
        textIn("project-tool-public project-2","Unknown tool")
        val pixels=compose.onNodeWithTag("project-bar-public project",true).captureToImage().toPixelMap()
        assertEquals(Color(desktopVendorColors.getValue("codex")).toArgb(),pixels[pixels.width/4,pixels.height/2].toArgb())
        assertEquals(Color(desktopVendorColors.getValue("claude")).toArgb(),pixels[pixels.width*3/4,pixels.height/2].toArgb())
        saveVisualEvidence(compose.activity,compose.onRoot(),"project-device-projects-$suffix.png")
        compose.runOnIdle {prefs.value=prefs.value.copy(language=LanguageChoice.CHINESE)}
        textIn("project-tool-public project-2","未知工具")
        saveVisualEvidence(compose.activity,compose.onRoot(),"project-device-projects-zh-$suffix.png")
        compose.runOnIdle {prefs.value=prefs.value.copy(language=LanguageChoice.ENGLISH,hideTitles=true)}
        compose.onNodeWithTag("project-title-public project",true).assertTextEquals("Projects summary")
        compose.onNodeWithTag("project-row-public project").performTouchInput {longClick()}
        compose.onNodeWithText("Usage summary").assertIsDisplayed();compose.onNodeWithText("Public project").assertDoesNotExist();compose.onNodeWithText("Close").performClick()
        compose.runOnIdle {prefs.value=prefs.value.copy(hideTitles=false)}
        show("project-row-last");compose.onNodeWithTag("hub-total-tokens").assertIsDisplayed()
        // Order remains cost first, even when the cheaper project has vastly more tokens.
        fun position(tag:String):Float {
            show(tag)
            val scroll=compose.onNodeWithTag("hub-content").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
            return scroll+compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top
        }
        // A short landscape viewport virtualizes the next row; compare visible scroll positions.
        val priority=position("project-row-priority")
        val big=position("project-row-public project")
        assertTrue(priority<big);assertEquals(0,actions.reads)
    }
    @Test fun originalHomeFourDevicesAndInlineToolModelsOsSyncAndMetadataHold() {
        val now=Instant.now().plusSeconds(60).toString();val actions=Actions()
        val snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":6000,"costUsd":6}},"devices":[
          {"deviceId":"low","hostname":"Private host label","platform":"linux-x64","periods":{"today":{"totalTokens":100}}},
          {"deviceId":"stale","platform":"openbsd","stale":true,"periods":{"today":{"totalTokens":1000,"clients":{"codex":1000}}}},
          {"deviceId":"mac","displayName":"Public Mac preview","platform":"darwin-arm64","osName":"macOS","osVersion":"27.0","updatedAt":"$now","agentRuntime":"headless-agent","agentVersion":"0.68.0","periods":{"today":{"totalTokens":1000,"costUsd":1,"clients":{"codex":600,"claude":300},"clientModels":{"codex":{"public-alpha":500,"public-beta":100}}}}},
          {"deviceId":"win","displayName":"Public Windows preview","platform":"win32-x64","periods":{"today":{"totalTokens":1000}}},
          {"deviceId":"fourth","platform":"linux-x64","periods":{"today":{"totalTokens":999}}},
          {"deviceId":"zero","platform":"linux","periods":{"today":{"totalTokens":0}}}]}
        """)
        val state=mutableStateOf(HubState(snapshot=snapshot,history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"),deviceAliases=mapOf("mac" to "Local Mac alias")))
        val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=listOf("devices"))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.language,prefs.appearance) {HubScreen(state.value,actions,preferences=prefs)}}}
        show("home-device-list")
        listOf("mac","win","stale","fourth").forEach {compose.onNodeWithTag("home-device-$it",true).assertExists()}
        compose.onNodeWithTag("home-device-low",true).assertDoesNotExist();compose.onNodeWithTag("home-device-zero",true).assertDoesNotExist()
        saveVisualEvidence(compose.activity,compose.onRoot(),"project-device-home-$suffix.png")
        select("devices");show("device-row-mac");compose.onNodeWithTag("device-row-mac").performClick()
        textIn("device-tool-mac-codex","60%")
        textIn("device-tool-mac-__unattributed","10%")
        textIn("device-model-mac-codex-public-alpha","500")
        compose.onNodeWithTag("device-meta-mac",true).assertTextContains("macOS 27.0 · Agent v0.68.0 · Synced just now")
        compose.onNodeWithTag("device-icon-mac",true).assertExists()
        saveVisualEvidence(compose.activity,compose.onRoot(),"project-device-devices-$suffix.png")
        compose.onNodeWithTag("device-row-mac").performTouchInput {longClick()}
        compose.onNode(hasText("Local Mac alias") and hasAnyAncestor(isDialog())).assertIsDisplayed();compose.onNodeWithText("Close").performClick()
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot.copy(devices=snapshot.devices.map {it.copy(periods=mapOf("today" to DeviceUsage(0,"0",emptyMap(),null,null,null)))}))}
        select("home");compose.onNodeWithText("No devices yet").assertIsDisplayed();assertEquals(0,actions.reads)
    }
}
