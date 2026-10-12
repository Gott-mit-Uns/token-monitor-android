package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class SettingsStructureScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val animationClock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
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
    private fun click(tag:String) {compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performClick()}
    private fun save(page:String) = saveVisualEvidence(compose.activity,compose.onRoot(),"settings-$page-${if(dark) "dark" else "light"}.png")
    private fun subscriptions():List<Subscription> {
        val today=LocalDate.now()
        return SubscriptionParser.parse("""{"subscriptions":[{"id":"monthly","provider":"claude","planName":"Public monthly","amountMinor":2000,"currency":"USD","interval":"month","intervalCount":1,"startDate":"$today","autoRenew":true},{"id":"annual","provider":"codex","planName":"Public annual","amountMinor":12000,"currency":"USD","interval":"year","intervalCount":1,"startDate":"$today","autoRenew":true},{"id":"old","provider":"gemini","planName":"Public expired","amountMinor":99900,"currency":"USD","interval":"month","intervalCount":1,"startDate":"${today.minusMonths(3)}","endDate":"${today.minusDays(1)}","autoRenew":false}]}""")
    }
    @Test fun originalSectionsCollapseOneAtATimeAndSubscriptionTotalRemainsAtBottom() {
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=listOf("devices")))
        val state=HubState(snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":1100,"costUsd":2.1,"models":{"public-many":1000,"public-expensive":100},"modelCosts":{"public-many":0.1,"public-expensive":2}}}}"""),subscriptions=subscriptions(),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"))
        val actions=Actions()
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(state,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}
        compose.onNodeWithTag("hub-settings-toggle").performClick()
        listOf("general","main","appearance","tools","limits","subscriptions","sync").forEach {compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("settings-section-$it"));compose.onNodeWithTag("settings-details-$it").assertDoesNotExist()}
        compose.onNodeWithTag("hub-content").performScrollToIndex(0)
        save("overview")
        click("main-display-toggle");click("settings-ranking-cost");assertEquals("cost",prefs.value.modelRanking);click("settings-period-select");compose.onNodeWithTag("settings-period-week").performClick()
        compose.onNodeWithTag("main-view-visible-home").assertExists()
        save("main")
        click("appearance-toggle");compose.onNodeWithTag("settings-details-main").assertDoesNotExist()
        click("settings-motion-on");assertEquals("on",prefs.value.appearance.reduceMotion)
        save("appearance")
        openSettingsSection(compose,"tools");compose.onNodeWithTag("settings-details-appearance").assertDoesNotExist()
        compose.onNodeWithTag("tool-list-toggle").assertDoesNotExist();compose.onNodeWithTag("tool-list-search").assertExists();save("tools")
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("shared-model-rules-open"))
        compose.onNodeWithTag("shared-model-rules-open-status",useUnmergedTree=true).assertTextEquals("Off")
        compose.onNodeWithTag("settings-open-pricing").assertExists();compose.onNodeWithTag("settings-open-pricing-status",useUnmergedTree=true).assertTextEquals("—")
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("settings-open-pricing"))
        compose.onNodeWithTag("settings-open-pricing").assertIsDisplayed();compose.onNodeWithTag("shared-model-rules-open").assertIsDisplayed();save("shared-headers")
        click("settings-subscriptions-toggle");compose.onNodeWithTag("settings-details-appearance").assertDoesNotExist()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("subscription-monthly-total"))
        val total=compose.onNodeWithTag("subscription-monthly-total").assertTextEquals("Total $30.00 / month").fetchSemanticsNode().boundsInRoot
        val last=compose.onNodeWithTag("shared-subscription-old").fetchSemanticsNode().boundsInRoot
        assertTrue(total.top>=last.bottom);save("subscriptions")
        compose.onNodeWithTag("hub-settings-toggle").performClick()
        compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-models").performScrollTo().performClick()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("public-expensive"))
        val manyNodes=compose.onAllNodesWithTag("breakdown-model-public-many").fetchSemanticsNodes()
        val expensiveBounds=compose.onNodeWithTag("breakdown-model-public-expensive").fetchSemanticsNode().boundsInRoot
        val manyBounds=manyNodes.singleOrNull()?.boundsInRoot
        val bothVisible=runCatching {compose.onNodeWithTag("breakdown-model-public-expensive").assertIsDisplayed();compose.onNodeWithTag("breakdown-model-public-many").assertIsDisplayed()}.isSuccess
        if(bothVisible) assertTrue(expensiveBounds.top<manyBounds!!.top)
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("breakdown-color-model-public-expensive"))
        assertEquals(1f,compose.onNodeWithTag("breakdown-color-model-public-expensive").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current,0.001f)
        val before=compose.onNodeWithTag("hub-content").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("breakdown-color-model-public-many"))
        val after=compose.onNodeWithTag("hub-content").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        if(!bothVisible) assertTrue(after>before)
        assertEquals(.05f,compose.onNodeWithTag("breakdown-color-model-public-many").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current,0.001f)
        assertEquals("cost",prefs.value.modelRanking);assertEquals("week",prefs.value.monthMode)
        assertEquals(0,actions.reads)
    }
    @Test fun localMotionThreeStatesPersistAndStopAnInFlightHeadline() {
        val name="motion-v13-${UUID.randomUUID()}.bin";val store=DisplayPreferenceStore(compose.activity,name)
        fun source(tokens:Int)=HubParser.parse("""{"periods":{"today":{"totalTokens":$tokens}}}""")
        val state=mutableStateOf(HubState(snapshot=source(1000),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off")))
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=listOf("devices"),appearance=AppearancePreferences(reduceMotion="off")))
        val actions=Actions()
        try {
            store.save(prefs.value)
            compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {CompositionLocalProvider(LocalDesktopAnimationClock provides animationClock) {HubScreen(state.value,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value);store.save(prefs.value)})}}}}
            compose.onNodeWithTag("hub-total-tokens").assertTextEquals("1,000")
            compose.mainClock.autoAdvance=false
            compose.runOnIdle {state.value=state.value.copy(snapshot=source(2000))}
            compose.mainClock.advanceTimeBy(32)
            val first=compose.onNodeWithTag("hub-total-tokens").fetchSemanticsNode().config[SemanticsProperties.Text].single().text.replace(",","").toInt()
            assertTrue("The first source frame includes elapsed time",first in 1001..1200)
            compose.mainClock.advanceTimeBy(250)
            val number=compose.onNodeWithTag("hub-total-tokens").fetchSemanticsNode().config[SemanticsProperties.Text].single().text.replace(",","").toInt()
            assertTrue(number in 1600..1800)
            compose.runOnIdle {prefs.value=DisplayChange.ReduceMotion("on").apply(prefs.value);store.save(prefs.value)}
            compose.mainClock.advanceTimeBy(32);compose.onNodeWithTag("hub-total-tokens").assertTextEquals("2,000")
            compose.mainClock.autoAdvance=true
            compose.onNodeWithTag("hub-settings-toggle").performClick();click("appearance-toggle")
            click("settings-motion-system");assertEquals("system",store.load().appearance.reduceMotion)
            click("settings-motion-off");assertEquals("off",store.load().appearance.reduceMotion)
            click("settings-motion-on");assertEquals("on",store.load().appearance.reduceMotion)
            assertEquals(16,java.io.File(compose.activity.noBackupFilesDir,name).readBytes().first().toInt())
            assertEquals(0,actions.reads)
        } finally {store.clear();compose.mainClock.autoAdvance=true}
    }
}
