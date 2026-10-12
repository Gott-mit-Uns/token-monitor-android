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
import java.time.Duration
import java.time.Instant

/** Actual HubScreen fed the exact public records captured from the fixed desktop DOM. */
class LimitLayoutScreenTest {
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
    private val fixture by lazy {InstrumentationRegistry.getInstrumentation().context.assets.open("limit-layout-parity.json").bufferedReader().use {Json.parseToJsonElement(it.readText()).jsonObject}}
    private val cases get()=fixture.getValue("cases").jsonArray.takeWhile {it.jsonObject.getValue("locale").jsonPrimitive.content=="en" && !it.jsonObject.getValue("used").jsonPrimitive.boolean}.map {it.jsonObject}
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private fun show(tag:String)=compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))
    private fun save(name:String)=saveVisualEvidence(compose.activity,compose.onRoot(),"layout-$name-${if(dark) "dark" else "light"}.png")
    private fun select() {compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-limits").performScrollTo().performClick()}
    private fun snapshot(providers:List<JsonObject>):Snapshot {
        // Shift only timestamps, so the source's fixed reset durations remain reproducible.
        val offset=Duration.between(Instant.parse(fixture.getValue("now").jsonPrimitive.content),Instant.now())
        fun shift(element:JsonElement):JsonElement=when(element) {
            is JsonObject->JsonObject(element.mapValues {(key,value)->if(key in setOf("resetsAt","expiresAt","updatedAt","checkedAt") && value is JsonPrimitive) runCatching {JsonPrimitive(Instant.parse(value.content).plus(offset).toString())}.getOrDefault(value) else shift(value)})
            is JsonArray->JsonArray(element.map {shift(it)})
            else->element
        }
        return HubParser.parse(buildJsonObject {put("periods",buildJsonObject {put("today",buildJsonObject {put("totalTokens",1000)})});put("limits",buildJsonObject {put("providers",JsonArray(providers.map(::shift)))})}.toString())
    }
    @Test fun providerOrderingGroupsMoneyAndMeterlessRowsMatchOriginalDesktopOnTheActualPage() {
        val initial=snapshot(listOf(cases.first().getValue("provider").jsonObject));val state=mutableStateOf(HubState(snapshot=initial,history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off")));val actions=Actions()
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark) {HubScreen(state.value,actions)}}};select()
        for(id in listOf("codex","factory","antigravity","openrouter","kiro")) {
            val case=cases.first {it.getValue("provider").jsonObject.getValue("provider").jsonPrimitive.content==id}
            val current=snapshot(listOf(case.getValue("provider").jsonObject))
            compose.runOnIdle {state.value=state.value.copy(snapshot=current)}
            case.getValue("windows").jsonArray.forEachIndexed {index,raw->
                val expected=raw.jsonObject;val tag="limit-value-0-$index";show(tag)
                compose.onNodeWithTag(tag).assertTextEquals(expected.getValue("value").jsonPrimitive.content).assertIsDisplayed()
                expected.getValue("group").jsonPrimitive.contentOrNull?.let {show("limit-group-0-$it");compose.onNodeWithTag("limit-group-0-$it").assertTextEquals(it)}
            }
            if(id=="factory") {show("limit-account-0");compose.onNodeWithTag("vendor-icon-factory",useUnmergedTree=true).assertExists()}
            show("limit-account-0");save(id)
            compose.runOnIdle {assertEquals(current,state.value.snapshot);assertEquals(0,actions.reads)}
        }
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(listOf(cases[11].getValue("provider").jsonObject)))}
        compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-home").performScrollTo().performClick()
        show("limit-value-0-1");compose.onNodeWithTag("limit-value-0-1").assertTextEquals("Unlimited").assertIsDisplayed()
    }
    @Test fun inferredBalancesExpiringGrantsDetailsAndRecoveryStatesRemainAvailableWithoutNetworkReads() {
        val state=mutableStateOf(HubState(snapshot=snapshot(listOf(cases[29].getValue("provider").jsonObject)),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off")));val actions=Actions()
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark) {HubScreen(state.value,actions)}}};select()
        show("limit-value-0-0");compose.onNodeWithTag("limit-value-0-0").assertTextEquals("$35.00")
        show("limit-detail-0-0");compose.onNodeWithTag("limit-detail-0-0").assertTextEquals("$5.00")
        show("limit-value-0-1");compose.onNodeWithTag("limit-value-0-1").assertTextEquals("Today 4500 · Month 28000");save("typesafe")
        val thirdparty=cases[6];compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(listOf(thirdparty.getValue("provider").jsonObject)))}
        show("limit-account-0");compose.onNodeWithTag("limit-account-0").performClick()
        show("limit-expanded-account-0");compose.onNodeWithText("Sub2API · Account").assertExists()
        compose.onAllNodesWithText("15,000").onFirst().assertExists();compose.onAllNodesWithText("1.5 s").onFirst().assertExists();save("thirdparty")
        val errors=cases.filter {it.getValue("provider").jsonObject.getValue("windows").jsonArray.isEmpty() && it.getValue("provider").jsonObject.getValue("status").jsonPrimitive.content in setOf("disabled","notConfigured","noSyncedData","unauthorized","rateLimited","sourceRateLimited","unavailable","error")}
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(errors.map {it.getValue("provider").jsonObject}))}
        errors.forEachIndexed {index,case->show("limit-account-$index");compose.onAllNodesWithText(case.getValue("plan").jsonPrimitive.content).onFirst().assertExists();compose.onNodeWithTag("limit-window-$index-0").assertDoesNotExist()}
        show("limit-account-0");save("status")
        compose.runOnIdle {assertEquals(0,actions.reads)}
    }
}
