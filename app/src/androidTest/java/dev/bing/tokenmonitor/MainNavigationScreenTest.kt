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
import java.io.File

class MainNavigationScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private fun save(page:String,tag:String?=null)=saveVisualEvidence(compose.activity,if(tag==null) compose.onRoot() else compose.onNodeWithTag(tag),"navigation-$page-${if(dark) "dark" else "light"}.png")
    private class Actions:HubUiActions {
        var reads=0
        override fun connect(address:String,credential:String,alternate:String) {}
        override fun refreshSaved() {reads++}
        override fun switchEndpoint() {}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadHistory() {reads++}
        override fun loadModelNames() {reads++}
        override fun loadPricing() {reads++}
        override fun loadSubscriptions() {reads++}
        override fun forget() {}
        override fun renameDevice(id:String,input:String) {}
    }
    @Test fun sharedPagesStayOutsideMainMenuAndBackReturnsThroughSettings() {
        val actions=Actions()
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,appearance=AppearancePreferences(reduceMotion="on"),main=MainDisplayPreferences(viewOrder=listOf("pricing","sessions","subscriptions","home"),hiddenViews=setOf("subscriptions","pricing"))))
        val state=HubState(snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":1000}}}"""),history=HubHistory(emptyList(),emptyList()),pricing=PricingParser.parse("""{"ok":true,"version":1,"revision":1,"value":[]}"""),subscriptions=emptyList(),modelNames=ModelNames(emptyMap(),"off"))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(state,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}
        compose.onNodeWithTag("hub-view-selector").performClick()
        compose.onNodeWithTag("view-pricing").assertDoesNotExist()
        compose.onNodeWithTag("view-subscriptions").assertDoesNotExist()
        compose.onNodeWithTag("view-home").assertIsSelected()
        compose.onNodeWithTag("view-models").assertIsNotSelected()
        MainDisplay.ids.forEach {compose.onNodeWithTag("view-$it").assertExists()}
        assertNavigationMenuLayout(compose,MainDisplay.order(prefs.value.main.viewOrder).map {id->HubPage.entries.first {it.name.lowercase()==id}},requireNoScroll=navigationWindowCanFitDefaultRows(compose.activity),density=compose.activity.resources.displayMetrics.density)
        compose.onNodeWithTag("view-home").assertIsFocused()
        save("menu","hub-view-menu")
        compose.onNodeWithTag("view-home").performKeyInput {pressKey(Key.MoveEnd)}
        compose.onNodeWithTag("view-trends").assertIsFocused()
        compose.onNodeWithTag("view-trends").performKeyInput {pressKey(Key.MoveHome)}
        compose.onNodeWithTag("view-sessions").assertIsFocused()
        compose.onNodeWithTag("view-sessions").performKeyInput {pressKey(Key.Escape)}
        compose.onNodeWithTag("hub-view-menu").assertDoesNotExist()
        compose.onNodeWithTag("hub-view-selector").performClick()
        selectNavigationOptionWithoutConcealingOverflow(compose,"models",navigationWindowCanFitDefaultRows(compose.activity))
        openSharedSettings(compose,"pricing")
        compose.onNodeWithTag("shared-pricing-summary").assertExists()
        save("pricing")
        compose.activity.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("settings-details-tools"))
        compose.onNodeWithTag("settings-details-tools").assertExists()
        openSettingsSection(compose,"main")
        compose.onNodeWithTag("main-view-visible-pricing").assertDoesNotExist()
        compose.onNodeWithTag("main-view-visible-subscriptions").assertDoesNotExist()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("main-views-header"))
        save("settings")
        compose.onNodeWithTag("hub-settings-toggle").performClick()
        compose.onNodeWithTag("hub-pinned-total").assertExists()
        openSharedSettings(compose,"subscriptions")
        save("subscriptions")
        compose.activity.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("settings-details-subscriptions"))
        compose.onNodeWithTag("settings-details-subscriptions").assertExists()
        compose.onNodeWithTag("hub-view-selector").performClick()
        compose.onNodeWithTag("view-pricing").assertDoesNotExist()
        compose.onNodeWithTag("view-subscriptions").assertDoesNotExist()
        selectNavigationOptionWithoutConcealingOverflow(compose,"home",navigationWindowCanFitDefaultRows(compose.activity))
        assertEquals(0,actions.reads)
    }
    @Test fun loadingLegacyViewChoicesPreservesExactStoredBytesAndOtherPreferences() {
        val name="public-navigation-compatibility.bin"
        val file=File(compose.activity.noBackupFilesDir,name)
        val store=DisplayPreferenceStore(compose.activity,name)
        try {
            val legacy=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=listOf("trends","tools","devices"),homeModuleOrder=listOf("trends","tools","devices","limits","models","sessions","activity"),main=MainDisplayPreferences(viewOrder=listOf("pricing","sessions","subscriptions","home","limits","tools","models","projects","devices","trends"),hiddenViews=setOf("pricing","subscriptions","home"),showLiveRate=true,rateMode="burn"))
            store.save(legacy);val bytes=file.readBytes()
            assertEquals(legacy,store.load())
            assertEquals("sessions",MainDisplay.preferred(store.load().main,"pricing"))
            assertArrayEquals(bytes,file.readBytes())
            assertEquals(6,HomeModulePreferences.order(store.load()).size)
            assertEquals(listOf("activity","tools","devices"),HomeModulePreferences.visible(store.load()))
            store.save(store.load());assertArrayEquals(bytes,file.readBytes())
            store.save(DisplayChange.TitleIconOnly(false).apply(store.load()))
            assertEquals(legacy.main.viewOrder,store.load().main.viewOrder)
            assertEquals(legacy.main.hiddenViews,store.load().main.hiddenViews)
            assertEquals(legacy.homeModules,store.load().homeModules)
            assertEquals(legacy.homeModuleOrder,store.load().homeModuleOrder)
        } finally {file.delete()}
    }
}
