package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class Desktop076ScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun countSettingsCommitNormalizeReloadAndRenderMoreModels() {
        val dark=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=listOf("models"),appearance=AppearancePreferences(reduceMotion="on")))
        val name="desktop076-${UUID.randomUUID()}.bin";val store=DisplayPreferenceStore(compose.activity,name)
        val snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":2000,"models":{"public-1":100,"public-2":99,"public-3":98,"public-4":97,"public-5":96,"public-6":95,"public-7":94,"public-8":93}}}}""")
        val actions=object:HubUiActions {
            override fun connect(address:String,credential:String,alternate:String) {};override fun refreshSaved() {};override fun switchEndpoint() {};override fun hasAlternate()=false;override fun endpointType()="https";override fun loadHistory() {};override fun loadModelNames() {};override fun loadSubscriptions() {};override fun forget() {};override fun renameDevice(id:String,input:String) {}
        }
        try {
            store.save(prefs.value);val original=File(compose.activity.noBackupFilesDir,name).readBytes()
            compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(HubState(snapshot=snapshot,live=true,modelNames=ModelNames(emptyMap(),"off")),actions,preferences=prefs.value,updateDisplay={store.save(it.apply(prefs.value));prefs.value=DisplayPreferenceStore(compose.activity,name).load()})}}}
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("home-model-public-5"))
            compose.onNodeWithTag("home-model-public-6").assertDoesNotExist()
            compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"main");compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("main-view-home-settings"));compose.onNodeWithTag("main-view-home-settings").performClick()
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("home-count-settings-models"));compose.onNodeWithTag("home-count-settings-models").performClick()
            val field=compose.onNodeWithTag("home-count-models")
            field.performScrollTo().performTextReplacement("8");field.performImeAction()
            compose.waitUntil(5000) {prefs.value.homeModuleCounts["models"]==8}
            assertArrayEquals(original,File(compose.activity.noBackupFilesDir,name).readBytes())
            assertEquals(8,HomeModuleCounts.count(DisplayPreferenceStore(compose.activity,name).load(),"models"))
            saveVisualEvidence(compose.activity,compose.onRoot(),"desktop076-count-${if(dark) "dark" else "light"}.png")
            compose.onNodeWithTag("hub-settings-toggle").performClick()
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("home-model-public-8"));compose.onNodeWithTag("home-model-public-8").assertIsDisplayed()
            saveVisualEvidence(compose.activity,compose.onRoot(),"desktop076-models-${if(dark) "dark" else "light"}.png")
        } finally {store.clear()}
    }
    @Test fun argonAndDisabledQuotaSurviveHttpFullFreshnessAndEncryptedColdRestore() {
        val fixture=InstrumentationRegistry.getInstrumentation().context.assets.open("desktop-076.json").bufferedReader().use {Json.parseToJsonElement(it.readText()).jsonObject}
        val windows=fixture.getValue("quotaCases").jsonArray[0].jsonObject.getValue("windows")
        val wire=buildJsonObject {put("periods",buildJsonObject {put("today",buildJsonObject {})});put("limits",buildJsonObject {put("providers",buildJsonArray {add(buildJsonObject {put("provider","antigravity");put("status","ok");put("windows",windows)})})})}.toString()
        val name="argon-${UUID.randomUUID()}.bin";val store=HubStore(compose.activity,name)
        try {
            val http=HubParser.parse(HubJsonProjection.stats(wire.byteInputStream()))
            val full=HubParser.parse(HubJsonProjection.streamStats("{\"stats\":$wire}".byteInputStream()))
            val fresh=HubParser.applyFreshness(full,buildJsonObject {put("devices",buildJsonArray {});put("periods",buildJsonObject {})})
            store.save(SavedHub("https://example.invalid","public-fixture",wire,1,null))
            val cold=HubParser.parse(HubStore(compose.activity,name).load()!!.snapshot)
            for(snapshot in listOf(http,full,fresh,cold)) {
                assertEquals(5,snapshot.limits.size)
                val rows=LimitProviderLayout.project(LimitProviderLayout.record(snapshot.limits))
                assertEquals(listOf("Gemini","Gemini","Claude/GPT","Claude/GPT","Gemini 4 Argon"),rows.map {it.group})
                assertEquals("74% left",rows.last().value)
            }
            val dark=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
            compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,LanguageChoice.ENGLISH,AppearancePreferences(reduceMotion="on")) {DesktopAccount(cold.limits)}}}
            compose.onNodeWithTag("limit-group-0-Gemini 4 Argon",true).assertExists()
            compose.onNodeWithText("74% left",true).assertExists()
            saveVisualEvidence(compose.activity,compose.onRoot(),"desktop076-argon-${if(dark) "dark" else "light"}.png")
        } finally {store.clear()}
    }
    @Test fun optionalCountsKeepV16BytesAndCorruptSidecarFallsBack() {
        val name="counts-${UUID.randomUUID()}.bin";val store=DisplayPreferenceStore(compose.activity,name);val file=File(compose.activity.noBackupFilesDir,name);val side=File(compose.activity.noBackupFilesDir,name+".home-counts")
        try {
            val p=DisplayPreferences(projectBreakdownMode="model")
            store.save(p);val old=file.readBytes();assertFalse(side.exists())
            val next=DisplayChange.HomeModuleCount("devices","50").apply(DisplayChange.HomeModuleCount("sessions","2").apply(p));store.save(next)
            assertArrayEquals(old,file.readBytes());assertEquals(next,DisplayPreferenceStore(compose.activity,name).load())
            side.writeBytes(byteArrayOf(1,5,5,0,4));assertEquals(emptyMap<String,Int>(),DisplayPreferenceStore(compose.activity,name).load().homeModuleCounts)
            side.writeBytes(byteArrayOf(1,5,5,2,4,9));assertEquals(emptyMap<String,Int>(),store.load().homeModuleCounts)
            store.save(p);assertFalse(side.exists());assertEquals(p,store.load())
        } finally {store.clear();assertFalse(side.exists())}
    }
}
