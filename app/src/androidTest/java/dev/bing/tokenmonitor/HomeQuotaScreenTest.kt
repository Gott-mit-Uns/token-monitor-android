package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant

class HomeQuotaScreenTest {
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
    private val home by lazy {asset("home-quota-parity.json")}
    private val layout by lazy {asset("limit-layout-parity.json")}
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private fun asset(name:String)=InstrumentationRegistry.getInstrumentation().context.assets.open(name).bufferedReader().use {Json.parseToJsonElement(it.readText()).jsonObject}
    private fun show(tag:String)=compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))
    private fun click(tag:String) {show(tag);compose.onNodeWithTag(tag).performClick()}
    private fun select(page:String) {compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-$page").performScrollTo().performClick()}
    private fun save(name:String)=saveVisualEvidence(compose.activity,compose.onRoot(),"quotas-$name-${if(dark) "dark" else "light"}.png")
    private fun snapshot(providers:JsonArray):Snapshot {
        val offset=Duration.between(Instant.parse(home.getValue("now").jsonPrimitive.content),Instant.now())
        fun shift(element:JsonElement):JsonElement=when(element) {
            is JsonObject->JsonObject(element.mapValues {(key,value)->
                if(key=="expirations" && value is JsonArray) JsonArray(value.map {date->runCatching {JsonPrimitive(Instant.parse(date.jsonPrimitive.content).plus(offset).toString())}.getOrDefault(date)})
                else if(key in setOf("resetsAt","expiresAt","endsAt","updatedAt","checkedAt","nextExpiresAt") && value is JsonPrimitive) runCatching {JsonPrimitive(Instant.parse(value.content).plus(offset).toString())}.getOrDefault(value) else shift(value)
            })
            is JsonArray->JsonArray(element.map(::shift));else->element
        }
        return HubParser.parse(buildJsonObject {put("periods",buildJsonObject {put("today",buildJsonObject {put("totalTokens",1000)})});put("limits",buildJsonObject {put("providers",shift(providers))})}.toString())
    }
    @Test fun antigravityDualPeriodsKeepCompleteTextAndSeparateReadingsAcrossThemesAndFontSizes() {
        val providers=buildJsonArray {add(buildJsonObject {
            put("provider","antigravity");put("windows",buildJsonArray {
                add(buildJsonObject {put("kind","weekly");put("label","Gemini 3 Pro weekly");put("remainingPercent",31);put("resetsAt","2026-10-15T15:00:00Z")})
                add(buildJsonObject {put("kind","session");put("label","Gemini 3 Pro 5-hour");put("remainingPercent",72);put("resetsAt","2026-10-12T15:00:00Z")})
            })
        })}
        val all=snapshot(providers).limits
        val now=Instant.parse("2026-10-12T12:00:00Z")
        // Synthetic font/theme matrix on a real Compose Activity; no phone claim.
        val theme=mutableStateOf(false);val font=mutableStateOf(1f);val size=mutableStateOf("standard");val bars=mutableStateOf(false)
        val prefs=LimitDisplayPreferences(homeBars=false)
        val account=HomeQuotaLayout.project(all,prefs,now=now).single()
        compose.activity.runOnUiThread {compose.activity.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,font.value)) {
                TokenTheme(theme.value,LanguageChoice.ENGLISH,AppearancePreferences(textSize=size.value,reduceMotion="on")) {
                    CompositionLocalProvider(LocalLimitDisplay provides prefs.copy(homeBars=bars.value)) {
                        androidx.compose.material3.Surface(color=androidx.compose.material3.MaterialTheme.colorScheme.background) {
                            Column(Modifier.widthIn(max=360.dp).fillMaxWidth().safeDrawingPadding().padding(12.dp)) {HomeQuotaRow(account) {}}
                        }
                    }
                }
            }
        }}
        for(dark in listOf(false,true)) for(scale in listOf(1f,2f)) for(mode in listOf(false,true)) {
            compose.runOnIdle {theme.value=dark;font.value=scale;size.value=if(scale==1f) "standard" else "largest";bars.value=mode}
            val windows=account.windows
            assertEquals(listOf("Gemini 3 Pro 5-hour","Gemini 3 Pro Weekly"),windows.map {it.label})
            val bounds=windows.indices.map {index ->
                val tag="0-$index"
                val parent=compose.onNodeWithTag("limit-window-$tag",useUnmergedTree=true).assertIsDisplayed().getUnclippedBoundsInRoot()
                for(text in listOf(windows[index].label,windows[index].value,windows[index].boundary)) {
                    val node=compose.onNodeWithText(text,useUnmergedTree=true).assertIsDisplayed()
                    val result=mutableListOf<TextLayoutResult>()
                    node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(result)}
                    // Match HomeQuotaEntryScreenTest's glyph-bound contract:
                    // paragraph width may retain the available constraint while
                    // wrap-content Text measures exactly to its visible glyphs.
                    val measured=result.single()
                    assertFalse("$dark/$scale/$mode $text height overflow",measured.didOverflowHeight)
                    for(line in 0 until measured.lineCount) {
                        assertFalse("$text must not be ellipsized",measured.isLineEllipsized(line))
                        assertTrue("$text glyphs must fit left",measured.getLineLeft(line)>=-.5f)
                        assertTrue("$text glyphs must fit right",measured.getLineRight(line)<=measured.size.width+.5f)
                        assertTrue("$text glyphs must fit bottom",measured.getLineBottom(line)<=measured.size.height+.5f)
                    }
                    val child=node.getUnclippedBoundsInRoot()
                    assertTrue(child.left>=parent.left-.5.dp && child.right<=parent.right+.5.dp)
                    assertTrue(child.top>=parent.top-.5.dp && child.bottom<=parent.bottom+.5.dp)
                }
                compose.onNodeWithTag("limit-value-$tag").assertTextEquals(if(index==0) "72% left" else "31% left")
                parent
            }
            assertTrue("period windows must not overlap",bounds[0].right<=bounds[1].left || bounds[0].bottom<=bounds[1].top)
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            saveVisualEvidence(compose.activity,compose.onRoot(),"antigravity-dual-${if(dark) "dark" else "light"}-${scale}-${if(mode) "bars" else "text"}.png")
        }
        compose.onAllNodesWithTag("home-quota-0").assertCountEquals(1)
        assertEquals(2,all.size)
    }
    @Test fun actualHomeUsesSourceUrgencyTextModeAndCustomSettingsAndRestoresThemInTheActivity() {
        val sourceCase=home.getValue("cases").jsonArray[10].jsonObject;val snapshot=snapshot(sourceCase.getValue("input").jsonArray)
        val prefs=mutableStateOf(DisplayPreferences(theme=if(dark) ThemeChoice.DARK else ThemeChoice.LIGHT,language=LanguageChoice.ENGLISH,homeModules=listOf("limits")))
        val state=HubState(snapshot=snapshot,history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"));val actions=Actions()
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(state,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}
        show("home-quota-name-1");compose.onNodeWithTag("home-quota-name-1").assertTextEquals("public-b@example.test")
        show("limit-value-1-0");compose.onNodeWithTag("limit-value-1-0").assertTextEquals("12% left")
        compose.onNodeWithTag("home-quota-meter-1-0").assertDoesNotExist();compose.onNodeWithTag("home-quota-0").assertDoesNotExist();save("text")
        compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"main");click("main-view-home-settings");click("home-quota-settings-toggle")
        click("home-quota-mode-select");compose.onNodeWithTag("home-quota-mode-bars").performClick();show("home-quota-count");compose.onNodeWithTag("home-quota-count").performTextReplacement("4");compose.onNodeWithTag("home-quota-count").performImeAction();click("home-quota-names");click("home-quota-provider-codex")
        select("home");show("home-quota-name-2");compose.onNodeWithTag("home-quota-name-2").assertTextContains("MiMo · Public profile",substring=true)
        show("home-quota-meter-2-0");compose.onNodeWithTag("home-quota-meter-2-0").assertIsDisplayed();compose.onNodeWithTag("home-quota-1").assertDoesNotExist();save("bars")
        compose.runOnIdle {prefs.value=DisplayChange.LimitProviderMove("zed",-1,home=true).apply(prefs.value)}
        assertTrue(prefs.value.limits.homeProviderOrder.isNotEmpty())
        compose.runOnIdle {prefs.value=DisplayChange.RestoreLimitOrder(home=true).apply(prefs.value)}
        assertTrue(prefs.value.limits.homeProviderOrder.isEmpty());assertEquals(snapshot,state.snapshot);assertEquals(0,actions.reads)
        val store=DisplayPreferenceStore(compose.activity);val before=store.load()
        try {
            val selected=prefs.value
            compose.activityRule.scenario.onActivity {ViewModelProvider(it)[HubViewModel::class.java].updateDisplay(selected)}
            compose.waitUntil(5000) {store.load()==selected};compose.activityRule.scenario.recreate()
            openSettingsSection(compose,"main");click("main-view-home-settings");click("home-quota-settings-toggle");show("home-quota-mode-select");compose.onNodeWithTag("home-quota-mode-select").assertTextContains("Progress bars")
            show("home-quota-count");compose.onNodeWithTag("home-quota-count").assertTextEquals("4");show("home-quota-provider-codex");compose.onNodeWithTag("home-quota-provider-codex").assertIsOff()
            assertEquals(selected,store.load())
        } finally {store.save(before)}
    }
    @Test fun itemChecklistHidesRepairedRowsRestoresAbsentItemsAndKeepsMimoProductsAndResetGrants() {
        val cases=layout.getValue("cases").jsonArray.filter {it.jsonObject.getValue("locale").jsonPrimitive.content=="en"&&!it.jsonObject.getValue("used").jsonPrimitive.boolean}.map {it.jsonObject}
        val factory=cases.first {it.getValue("provider").jsonObject.getValue("provider").jsonPrimitive.content=="factory"}
        val initial=snapshot(buildJsonArray {add(factory.getValue("provider"))});val state=mutableStateOf(HubState(snapshot=initial,history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off")))
        val prefs=mutableStateOf(DisplayPreferences(theme=if(dark) ThemeChoice.DARK else ThemeChoice.LIGHT,language=LanguageChoice.ENGLISH));val actions=Actions()
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(state.value,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}
        compose.onNodeWithTag("hub-settings-toggle").performClick();click("limit-settings-toggle");click("limit-items-factory");show("limit-item-factory-0");save("items");click("limit-item-factory-0")
        val key=factory.getValue("windows").jsonArray[0].jsonObject.getValue("item").jsonPrimitive.content
        assertEquals(listOf(key),prefs.value.limits.hiddenItems["factory"])
        select("limits");show("limit-value-0-0");compose.onNodeWithTag("limit-value-0-0").assertTextEquals("70% left");save("hidden")
        // A missing reading must leave a way to restore the stored hidden item.
        compose.runOnIdle {state.value=state.value.copy(snapshot=initial.copy(limits=emptyList()))}
        compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"limits")
        if(compose.onAllNodesWithTag("limit-item-factory-0").fetchSemanticsNodes().isEmpty()) click("limit-items-factory")
        click("limit-item-factory-0");assertFalse(prefs.value.limits.hiddenItems.containsKey("factory"))
        compose.runOnIdle {state.value=state.value.copy(snapshot=initial)};select("limits");show("limit-value-0-0");compose.onNodeWithTag("limit-value-0-0").assertTextEquals("80% left")
        val extras=home.getValue("cases").jsonArray[10].jsonObject.getValue("input").jsonArray
        val products=extras.filter {it.jsonObject["provider"]?.jsonPrimitive?.content=="mimo"}.take(2)
        val coupon=cases.first {it.getValue("provider").jsonObject["resetCredits"]?.jsonObject?.get("availableCount")?.jsonPrimitive?.doubleOrNull==4.8}
        val grants=cases.first {it.getValue("provider").jsonObject["resetCredits"]?.jsonObject?.get("grants")?.jsonArray?.size==2}
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(JsonArray(products+listOf(coupon.getValue("provider"),grants.getValue("provider")))))}
        show("limit-account-0");compose.onNodeWithTag("mimo-account-0").assertDoesNotExist();save("mimo")
        show("limit-value-2-1");compose.onNodeWithTag("limit-value-2-1").assertTextEquals("4 resets")
        show("limit-account-3");compose.onNodeWithTag("limit-account-3").performClick();show("limit-expanded-account-3");compose.onNodeWithText("Public scoped reset").assertExists();save("grants")
        assertEquals(0,actions.reads)
    }
}
