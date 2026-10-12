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

/** Only original public DOM fixtures are rendered or saved. */
class SubscriptionScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val data by lazy {InstrumentationRegistry.getInstrumentation().context.assets.open("subscription-layout-parity.json").bufferedReader().use {Json.parseToJsonElement(it.readText()).jsonObject}}
    private val fixtureClock get()=java.time.Clock.fixed(java.time.LocalDate.parse(data.getValue("today").jsonPrimitive.content).atTime(12,0).atZone(java.time.ZoneId.systemDefault()).toInstant(),java.time.ZoneId.systemDefault())
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private class Actions:HubUiActions {
        var reads=0
        override fun connect(address:String,credential:String,alternate:String) {}
        override fun refreshSaved() {reads++}
        override fun switchEndpoint() {}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadHistory() {reads++}
        override fun loadSubscriptions() {reads++}
        override fun loadModelNames() {reads++}
        override fun loadPricing() {reads++}
        override fun forget() {}
        override fun renameDevice(id:String,input:String) {}
    }
    private fun subscriptions(c:JsonObject)=SubscriptionParser.parse(buildJsonObject {put("subscriptions",c.getValue("input"))}.toString())
    private fun snapshot(accounts:JsonArray,costs:JsonObject=buildJsonObject {})=HubParser.parse(buildJsonObject {
        put("periods",buildJsonObject {put("today",buildJsonObject {put("totalTokens",1000)});put("month",buildJsonObject {put("clientCosts",costs)})})
        put("limits",buildJsonObject {put("providers",accounts)})
    }.toString())
    private fun select(page:String) {compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-$page").performScrollTo().performClick()}
    private fun show(tag:String)=compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))
    private fun click(tag:String) {show(tag);compose.onNodeWithTag(tag).performClick()}
    private fun save(name:String,dialog:Boolean=false)=saveVisualEvidence(compose.activity,if(dialog) compose.onNodeWithTag("subscription-plan-details") else compose.onRoot(),"subscription-$name-${if(dark) "dark" else "light"}.png")
    private fun verifySourceRows(c:JsonObject) {
        c.getValue("rows").jsonArray.forEach {raw->val row=raw.jsonObject
            if((row["separator"] as? JsonPrimitive)?.booleanOrNull!=true) {
                compose.onNode(hasText(row.getValue("label").jsonPrimitive.content) and hasAnyAncestor(hasTestTag("subscription-plan-table"))).performScrollTo().assertIsDisplayed()
                compose.onNode(hasText(row.getValue("value").jsonPrimitive.content) and hasAnyAncestor(hasTestTag("subscription-plan-table"))).performScrollTo().assertIsDisplayed()
            }
        }
    }
    @Test fun sourceListResolvesCurrentAccountsWarnsOnAmbiguityAndRespectsPrivacyAndIconChanges() {
        val c=data.getValue("cases").jsonArray.map {it.jsonObject}.first {it.getValue("locale").jsonPrimitive.content=="en" && !it.getValue("mask").jsonPrimitive.boolean && it.getValue("icons").jsonPrimitive.boolean && it.getValue("input").jsonArray.size==3 && it.getValue("providers").jsonArray.size==2}
        val providers=c.getValue("providers").jsonArray
        val initial=HubState(snapshot=snapshot(providers),subscriptions=subscriptions(c))
        val state=mutableStateOf(initial);val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH));val actions=Actions()
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {CompositionLocalProvider(LocalHubCalendarClock provides fixtureClock) {HubScreen(state.value,actions,preferences=prefs.value)}}}}
        openSharedSettings(compose,"subscriptions")
        c.getValue("dom").jsonArray.forEach {raw->val row=raw.jsonObject;val id=row.getValue("id").jsonPrimitive.content;show("shared-subscription-$id")
            compose.onNodeWithTag("shared-subscription-$id-title",useUnmergedTree=true).assertTextEquals(row.getValue("title").jsonPrimitive.content)
            compose.onNodeWithTag("shared-subscription-$id-meta",useUnmergedTree=true).assertTextEquals(row.getValue("meta").jsonPrimitive.content)
            if(row.getValue("warning").jsonPrimitive.content.isNotEmpty()) compose.onNodeWithTag("shared-subscription-$id-warning",useUnmergedTree=true).assertTextEquals(row.getValue("warning").jsonPrimitive.content)
        }
        save("list");click("shared-subscription-public-exact")
        compose.onNodeWithTag("shared-subscription-details-title").assertTextEquals("Codex · second@example.test")
        val changed=JsonArray(providers.map {raw->val obj=raw.jsonObject
            if(obj["accountKey"]?.jsonPrimitive?.content=="public-b") JsonObject(obj+mapOf("email" to JsonPrimitive("changed@example.test"),"accountEmail" to JsonPrimitive("changed@example.test"))) else obj})
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(changed));prefs.value=prefs.value.copy(limits=prefs.value.limits.copy(maskEmails=true),appearance=prefs.value.appearance.copy(showToolIcons=false))}
        compose.onNodeWithTag("shared-subscription-details-title").assertTextEquals("Codex · c***d@example.test")
        compose.onAllNodesWithText("second@example.test",substring=true,useUnmergedTree=true).assertCountEquals(0)
        compose.onAllNodesWithText("changed@example.test",substring=true,useUnmergedTree=true).assertCountEquals(0)
        compose.runOnIdle {state.value=state.value.copy(subscriptions=emptyList())};compose.onNodeWithTag("shared-subscription-details").assertDoesNotExist()
        compose.runOnIdle {state.value=state.value.copy(subscriptions=initial.subscriptions)}
        compose.onNodeWithTag("shared-subscription-details").assertDoesNotExist();show("shared-subscription-public-exact")
        compose.onNodeWithTag("vendor-icon-codex",useUnmergedTree=true).assertDoesNotExist();save("masked");assertEquals(0,actions.reads)
    }
    @Test fun quotaPlanCardsMatchOriginalAccountGroupLedgerAndClientAttributionAndWithdrawCleanly() {
        val cards=data.getValue("cards").jsonArray.map {it.jsonObject}.filter {it.getValue("locale").jsonPrimitive.content=="en" && it.getValue("currency").jsonPrimitive.content=="USD"}
        val group=cards.first {c->c.getValue("provider").jsonObject["accountGroup"]?.jsonPrimitive?.booleanOrNull==true && c.getValue("input").jsonArray.size==2 && c.getValue("provider").jsonObject["groupAccounts"]?.jsonArray?.size==2}
        val state=mutableStateOf(HubState(snapshot=snapshot(group.getValue("accounts").jsonArray,group.getValue("costs").jsonObject),subscriptions=subscriptions(group)))
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH));val actions=Actions()
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {CompositionLocalProvider(LocalHubCalendarClock provides fixtureClock) {HubScreen(state.value,actions,preferences=prefs.value)}}}}
        select("limits");click("limit-plan-group-codex");verifySourceRows(group);save("group",true)
        compose.onNodeWithText("Close").performClick()
        val account=cards.first {it.getValue("input")==group.getValue("input") && it.getValue("accounts")==group.getValue("accounts") && it.getValue("provider").jsonObject["accountGroup"]==null && !it.getValue("includeRollup").jsonPrimitive.boolean}
        click("limit-plan-0");verifySourceRows(account);compose.onNodeWithText("This month").assertDoesNotExist();save("account",true)
        compose.runOnIdle {state.value=state.value.copy(subscriptions=emptyList())};compose.onNodeWithTag("subscription-plan-details").assertDoesNotExist()
        compose.runOnIdle {state.value=state.value.copy(subscriptions=subscriptions(group))};compose.onNodeWithTag("subscription-plan-details").assertDoesNotExist()
        val factory=cards.first {it.getValue("provider").jsonObject["provider"]?.jsonPrimitive?.content=="factory" && it.getValue("includeRollup").jsonPrimitive.boolean && it.getValue("provider").jsonObject["accountGroup"]==null}
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(factory.getValue("accounts").jsonArray,factory.getValue("costs").jsonObject),subscriptions=subscriptions(factory))}
        click("limit-plan-0");verifySourceRows(factory);save("factory",true)
        compose.runOnIdle {prefs.value=prefs.value.copy(currency="CNY")}
        compose.onNodeWithText("≈ ¥510.00").performScrollTo().assertIsDisplayed();compose.onNodeWithText("$20.00 / mo").performScrollTo().assertIsDisplayed();compose.onNodeWithText("Close").performClick()
        val top=cards.first {it.getValue("provider").jsonObject["provider"]?.jsonPrimitive?.content=="openrouter" && it.getValue("includeRollup").jsonPrimitive.boolean && it.getValue("rows").jsonArray.any {r->r.jsonObject["value"]?.jsonPrimitive?.content=="$0.64/day"}}
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(top.getValue("accounts").jsonArray,top.getValue("costs").jsonObject),subscriptions=subscriptions(top))}
        click("limit-plan-0");verifySourceRows(top);save("ledger",true);compose.onNodeWithText("Close").performClick()
        assertEquals(0,actions.reads)
    }
}
