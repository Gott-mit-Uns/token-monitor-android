package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class Desktop078ScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val actions=object:HubUiActions {
        override fun connect(address:String,credential:String,alternate:String) {};override fun refreshSaved() {};override fun switchEndpoint() {};override fun hasAlternate()=false;override fun endpointType()="https";override fun loadHistory() {};override fun loadModelNames() {};override fun loadSubscriptions() {};override fun forget() {};override fun renameDevice(id:String,input:String) {}
    }
    @Test fun zcodeColdRestoreAndFamilyDetailsKeepDisjointAccounting() {
        val reference=InstrumentationRegistry.getInstrumentation().context.assets.open("desktop-078.json").bufferedReader().use {Json.parseToJsonElement(it.readText()).jsonObject}
        val wire=reference.getValue("cases").jsonArray[0].jsonObject.getValue("stats").toString()
        val name="desktop078-${UUID.randomUUID()}.bin";val store=HubStore(compose.activity,name)
        try {
            store.save(SavedHub("https://example.invalid","public-fixture",wire,1,null))
            val snapshot=HubParser.parse(HubStore(compose.activity,name).load()!!.snapshot)
            assertEquals(1500L,snapshot.periods.getValue("today").tokens)
            assertEquals("parent",snapshot.periods.getValue("today").sessions.single {it.id=="zcode:child"}.session!!.parentSessionId)
            val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList(),appearance=AppearancePreferences(reduceMotion="on"))
            val dark=InstrumentationRegistry.getArguments().getString("visualDark")=="true"
            compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.language,prefs.appearance) {HubScreen(HubState(snapshot=snapshot,live=true,modelNames=ModelNames(emptyMap(),"off")),actions,preferences=prefs)}}}
            compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-sessions").performScrollTo().performClick()
            fun show(tag:String) {compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))}
            val parent="session-row-session-group:subagents:zcode:parent"
            show(parent);compose.onNodeWithTag(parent).assertIsDisplayed().performClick()
            show("session-row-zcode:parent");compose.onNodeWithTag("session-title-zcode:parent",true).assertTextEquals("Saved ZCode title")
            show("session-row-zcode:child");compose.onNodeWithTag("session-role-zcode:child",true).assertTextEquals("Subagent")
            show("session-row-zcode:workflow");compose.onNodeWithTag("session-tokens-zcode:workflow",true).assertTextEquals("300")
            saveVisualEvidence(compose.activity,compose.onRoot(),"desktop078-zcode-${if(dark) "dark" else "light"}.png")
        } finally {store.clear()}
    }
}
