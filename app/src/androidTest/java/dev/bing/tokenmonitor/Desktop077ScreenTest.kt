package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class Desktop077ScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private fun reference()=InstrumentationRegistry.getInstrumentation().context.assets.open("desktop-077.json").bufferedReader().use {Json.parseToJsonElement(it.readText()).jsonObject}
    private val actions=object:HubUiActions {
        override fun connect(address:String,credential:String,alternate:String) {};override fun refreshSaved() {};override fun switchEndpoint() {};override fun hasAlternate()=false;override fun endpointType()="https";override fun loadHistory() {};override fun loadModelNames() {};override fun loadSubscriptions() {};override fun forget() {};override fun renameDevice(id:String,input:String) {}
    }
    @Test fun accountSpendAndIdentitySurviveRealDesktopFramesAndEncryptedColdRestore() {
        val transport=reference().getValue("transport").jsonObject
        fun frame(key:String)=transport.getValue(key).jsonPrimitive.content.substringAfter("data: ").trim()
        val wire=transport.getValue("stats").toString()
        val http=HubParser.parse(HubJsonProjection.stats(wire.byteInputStream()))
        val full=HubParser.parse(HubJsonProjection.streamStats(frame("full").byteInputStream()))
        val legacy=HubParser.parse(HubJsonProjection.streamStats(frame("legacy").byteInputStream()))
        val fresh=HubParser.applyFreshness(full,Json.parseToJsonElement(frame("freshness")).jsonObject.getValue("stats").jsonObject)
        val name="desktop077-${UUID.randomUUID()}.bin";val store=HubStore(compose.activity,name)
        try {
            store.save(SavedHub("https://example.invalid","public-fixture",wire,1,null))
            val cold=HubParser.parse(HubStore(compose.activity,name).load()!!.snapshot)
            for(snapshot in listOf(http,full,legacy,fresh,cold)) {
                assertEquals(4,LimitDisplay.groups(snapshot.limits).size)
                val cursor=snapshot.limits.single {it.provider=="cursor"}
                assertEquals("unavailable",cursor.status);assertNull(cursor.remaining)
                val unavailable=LimitProviderLayout.project(cursor.presentation.providerRecord!!).single()
                assertNull(unavailable.percent);assertEquals("Allowance unavailable",unavailable.value)
                assertEquals(listOf("public-a","public-b"),snapshot.limits.filter {it.provider=="cline"}.map {it.presentation.identity.key})
                val spend=LimitProviderLayout.project(snapshot.limits.first().presentation.providerRecord!!).single()
                assertEquals("Today $1.00 · Month $12.00",spend.value)
                assertEquals(listOf("Today","Week","Month","All time"),spend.details.takeLast(4).map {it.first()})
            }
            val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH,appearance=AppearancePreferences(reduceMotion="on"))
            compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.language,prefs.appearance) {HubScreen(HubState(snapshot=cold,live=true,modelNames=ModelNames(emptyMap(),"off")),actions,preferences=prefs)}}}
            compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-limits").performScrollTo().performClick()
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("limit-value-0-0"))
            compose.onNodeWithText("Today $1.00 · Month $12.00",true).assertIsDisplayed()
            compose.onNodeWithTag("limit-account-0").performClick()
            compose.onNodeWithTag("hub-content").performScrollToNode(hasText("All time"))
            compose.onNodeWithText("Week",true).assertIsDisplayed();compose.onNodeWithText("$40.00",true).assertIsDisplayed()
            saveVisualEvidence(compose.activity,compose.onRoot(),"desktop077-spend-${if(dark) "dark" else "light"}.png")
        } finally {store.clear()}
    }
    @Test fun concurrentModelDetailsRetainBothThenExpireSeparately() {
        var clock=0L
        val events=reference().getValue("scenarios").jsonArray[12].jsonObject.getValue("events").jsonArray
        fun snapshot(index:Int):Snapshot {
            val event=events[index].jsonObject
            val period=(event["entries"] ?: event.getValue("reset")).jsonArray[0].jsonObject.getValue("period")
            return HubParser.parse(buildJsonObject {put("periods",buildJsonObject {put("today",buildJsonObject {})});put("devices",buildJsonArray {add(buildJsonObject {put("deviceId","public");put("hostname","Public device");put("periods",buildJsonObject {put("today",period)})})})}.toString())
        }
        val state=mutableStateOf(HubState(snapshot=snapshot(0),live=true,modelNames=ModelNames(emptyMap(),"off")))
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=listOf("sessions"),main=MainDisplayPreferences(showLiveRate=true),appearance=AppearancePreferences(reduceMotion="on")))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {CompositionLocalProvider(LocalRateNow provides {clock}) {HubScreen(state.value,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}}
        compose.waitForIdle()
        compose.runOnIdle {clock=100;state.value=state.value.copy(snapshot=snapshot(1))}
        compose.onNodeWithTag("hub-live-rate").assertTextEquals("40 tok/s")
        compose.runOnIdle {clock=200;state.value=state.value.copy(snapshot=snapshot(2))}
        compose.onNodeWithTag("hub-live-rate").assertTextEquals("20 tok/s")
        compose.onNodeWithTag("hub-live-rate").performTouchInput {longClick()}
        compose.onNodeWithText("alpha",true).assertIsDisplayed();compose.onNodeWithText("beta",true).assertIsDisplayed()
        saveVisualEvidence(compose.activity,compose.onNode(isDialog()),"desktop077-rate-${if(dark) "dark" else "light"}.png")
        compose.onNodeWithText("Close").performClick()
        compose.runOnIdle {clock=8100;state.value=state.value.copy(snapshot=snapshot(2))}
        compose.waitUntil(12000) {compose.onAllNodesWithText("10 tok/s").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("hub-live-rate").performTouchInput {longClick()}
        compose.onNodeWithText("alpha",true).assertDoesNotExist();compose.onNodeWithText("beta",true).assertIsDisplayed()
    }
}
