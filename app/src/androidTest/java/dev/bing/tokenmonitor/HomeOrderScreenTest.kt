package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Public fixture checks source defaults, hidden-row moves and rendered restoration. */
class HomeOrderScreenTest {
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
    @Test fun hiddenModulesMoveWithoutAppearingAndShowAllAndReloadKeepTheirSlots() {
        val dark=InstrumentationRegistry.getArguments().getString("visualDark")!="false";val suffix=if(dark) "dark" else "light"
        val name="home-order-ui-${UUID.randomUUID()}.bin";val store=DisplayPreferenceStore(compose.activity,name)
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH));val actions=Actions()
        val snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":1000,"costUsd":1,"clients":{"claude":1000}}},"devices":[{"deviceId":"public-device","displayName":"Public device","platform":"linux-x64","periods":{"today":{"totalTokens":1000}}}]}""")
        val state=HubState(snapshot=snapshot,history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"))
        fun click(tag:String) {compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performClick()}
        fun save(stage:String)=saveVisualEvidence(compose.activity,compose.onRoot(),"home-order-$stage-$suffix.png")
        try {
            compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(state,actions,preferences=prefs.value,updateDisplay={store.save(it.apply(prefs.value));prefs.value=DisplayPreferenceStore(compose.activity,name).load()})}}}
            compose.onNodeWithTag("home-device-list").assertDoesNotExist();compose.onNodeWithTag("home-tool-claude").assertDoesNotExist()
            compose.onNodeWithTag("hub-total-tokens").assertTextEquals("1,000")
            compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"main");click("main-view-home-settings")
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("home-module-tools"));compose.onNodeWithTag("home-module-tools").assertIsOff()
            compose.onNodeWithTag("home-module-devices").assertIsOff();save("defaults")
            val beforeBoundary=prefs.value
            click("home-module-order-limits")
            compose.onNodeWithTag("home-module-up-limits").assertIsNotEnabled().performClick()
            compose.onNodeWithTag("home-module-down-limits").assertIsEnabled().assertIsFocused()
            assertEquals(beforeBoundary,prefs.value)
            saveVisualEvidence(compose.activity,compose.onNodeWithTag("home-module-order-menu-limits"),"home-order-menu-$suffix.png")
            compose.onNodeWithTag("home-module-down-limits").performKeyInput {pressKey(Key.MoveHome);pressKey(Key.Escape)}
            compose.onNodeWithTag("home-module-order-menu-limits").assertDoesNotExist()
            click("home-module-models");assertEquals(listOf("limits","sessions","activity"),store.load().homeModules);save("hidden")
            click("home-module-order-tools");compose.onNodeWithTag("home-module-down-tools").performClick();click("home-module-order-activity");compose.onNodeWithTag("home-module-up-activity").performClick()
            val custom=listOf("limits","models","tools","sessions","activity","devices")
            assertEquals(custom,store.load().homeModuleOrder);assertEquals(listOf("limits","sessions","activity"),store.load().homeModules)
            save("moved")
            click("home-modules-show-all");assertEquals(custom,store.load().homeModules);assertEquals(custom,store.load().homeModuleOrder);save("all")
            click("home-module-tools");click("home-module-devices")
            compose.runOnIdle {prefs.value=DisplayPreferenceStore(compose.activity,name).load()}
            assertEquals(custom,prefs.value.homeModuleOrder);assertEquals(listOf("limits","models","sessions","activity"),prefs.value.homeModules)
            click("home-module-tools");assertEquals(listOf("limits","models","tools","sessions","activity"),store.load().homeModules)
            save("restored")
            click("home-modules-reset");assertEquals(defaultHomeModules,store.load().homeModuleOrder);assertFalse("devices" in store.load().homeModules)
            compose.onNodeWithTag("hub-settings-toggle").performClick();compose.onNodeWithTag("home-device-list").assertDoesNotExist()
            compose.onNodeWithTag("hub-total-tokens").assertTextEquals("1,000");assertEquals(0,actions.reads)
        } finally {store.clear()}
    }
}
