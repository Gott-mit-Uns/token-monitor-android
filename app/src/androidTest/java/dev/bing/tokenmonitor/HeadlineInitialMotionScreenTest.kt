package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Initial/source early-return behavior, using only public synthetic values. */
class HeadlineInitialMotionScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val animationClock = DesktopAnimationClock { compose.mainClock.currentTime * 1_000_000L }
    private class Actions : HubUiActions {
        var reads = 0
        override fun connect(address:String,credential:String,alternate:String) {}
        override fun refreshSaved() { reads++ }
        override fun switchEndpoint() {}
        override fun hasAlternate() = false
        override fun endpointType() = "https"
        override fun loadHistory() { reads++ }
        override fun loadDeviceHistory() { reads++ }
        override fun loadModelNames() { reads++ }
        override fun loadSubscriptions() { reads++ }
        override fun forget() {}
        override fun renameDevice(id:String,input:String) {}
    }
    private fun text(tag:String="source-headline") = compose.onNodeWithTag(tag).fetchSemanticsNode()
        .config[SemanticsProperties.Text].single().text
    private fun count(tag:String="source-headline") = text(tag).replace(",","").toLong()
    private fun mount(content:@Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.activity.runOnUiThread { compose.activity.setContent {
            TokenTheme(false) { CompositionLocalProvider(
                LocalDesktopAnimationClock provides animationClock,
                LocalHeadlineMotionScale provides 1f,
            ) { content() } }
        } }
        compose.mainClock.advanceTimeBy(32)
    }
    @Composable private fun Headline(target:Long?,period:String="today",suppressInitial:Boolean=false,detailUnavailable:Boolean=false) {
        val state = rememberHeadlineNumber(target,period,true,suppressInitial=suppressInitial,detailUnavailable=detailUnavailable)
        // Keep reads in a child just like DesktopTotal; the holder owns no per-frame read.
        HeadlineText(state)
    }
    @Composable private fun HeadlineText(state:State<HeadlineFrame>) {
        val frame = state.value
        Text(frame.value?.toString() ?: "—",Modifier.testTag("source-headline"))
    }

    @Test fun actualHomeColdValidCountUsesElapsedFirstFrameAndSourceQuart() {
        val snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":1000}}}""")
        val actions=Actions()
        val preferences=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList())
        mount { HubScreen(HubState(snapshot=snapshot,history=HubHistory(emptyList(),emptyList()),
            modelNames=ModelNames(emptyMap(),"off")),actions,preferences=preferences) }
        // Source first RAF includes elapsed time. Neither the private zero nor the
        // pre-RAF widest endpoint is required to occupy a rendered Android frame.
        assertTrue(count("hub-total-tokens") in 1L..999L)
        compose.mainClock.advanceTimeBy(224)
        assertTrue(count("hub-total-tokens") in 600L..800L)
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("1,000")
        assertEquals(0,actions.reads)
        compose.mainClock.autoAdvance=true
    }

    @Test fun firstZeroIsStaticAndNullDoesNotInventASettledTarget() {
        val target=mutableStateOf<Long?>(0L)
        mount { Headline(target.value) }
        compose.onNodeWithTag("source-headline").assertTextEquals("0")
        compose.mainClock.advanceTimeBy(1100)
        compose.onNodeWithTag("source-headline").assertTextEquals("0")
        compose.runOnIdle { target.value=null }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("source-headline").assertTextEquals("—")
        compose.mainClock.advanceTimeBy(1100)
        compose.onNodeWithTag("source-headline").assertTextEquals("—")
        compose.runOnIdle { target.value=1000L }
        compose.mainClock.advanceTimeBy(32)
        assertTrue(count() in 1L..999L)
        compose.mainClock.advanceTimeBy(1100)
        compose.onNodeWithTag("source-headline").assertTextEquals("1000")
        compose.mainClock.autoAdvance=true
    }

    @Test fun actualFixedUnreadyThenAsyncReadyUsesNormalThousandMilliseconds() {
        val snapshot=HubParser.parse("""{"deviceHistoryRevision":"public-motion","periods":{"today":{"totalTokens":500}},"devices":[{"deviceId":"public","hostname":"Public Desktop","periodWindows":{"timeZone":"Etc/UTC","today":{"key":"2026-10-09","endsAt":"2999-10-10T00:00:00Z"}},"periods":{"today":{"totalTokens":500}}}]}""")
        val payload="""{"devices":[{"deviceId":"public","historyAvailable":true,"history":{"daily":[{"date":"2026-10-03","tokens":300}]}}]}"""
        val ready=DeviceHistoryParser.parse(DeviceHistoryParser.project(payload,FixedPeriods.signature(snapshot)))
        val state=mutableStateOf(HubState(snapshot=snapshot,history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off")))
        val actions=Actions()
        val preferences=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList(),lastPeriod="last7",monthMode="last7")
        mount { HubScreen(state.value,actions,preferences=preferences) }
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("—")
        compose.runOnIdle { state.value=state.value.copy(deviceHistories=ready) }
        compose.mainClock.advanceTimeBy(32)
        assertTrue(count("hub-total-tokens") in 1L..799L)
        compose.mainClock.advanceTimeBy(768)
        // An 800ms period-click tween would already be exactly 800. The async
        // ready render happens after the source's synchronous period flag cleared.
        assertTrue(count("hub-total-tokens") in 700L..799L)
        compose.mainClock.advanceTimeBy(250)
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("800")
        // Exercise the source's other early return through the real main view:
        // fixed totals are known, but this period has no project breakdown.
        compose.onNodeWithTag("hub-view-selector").performClick()
        compose.mainClock.advanceTimeBy(240)
        compose.onNodeWithTag("view-projects").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle {
            val device=snapshot.devices.single()
            // Parsing a real response updates the compact window and full live
            // detail together. Keep this synthetic single-device response coherent.
            state.value=state.value.copy(snapshot=snapshot.copy(
                periods=snapshot.periods+("today" to snapshot.periods.getValue("today").copy(tokens=900)),
                devices=listOf(device.copy(
                    periods=device.periods+("today" to device.periods.getValue("today").copy(tokens=900)),
                    periodDetails=device.periodDetails+("today" to device.periodDetails.getValue("today").copy(tokens=900))
                ))))
        }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("1,200")
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("1,200")
        compose.onNodeWithTag("hub-view-selector").performClick()
        compose.mainClock.advanceTimeBy(240)
        compose.onNodeWithTag("view-home").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("1,200")
        assertEquals(0,actions.reads)
        compose.mainClock.autoAdvance=true
    }

    @Test fun explicitInitialSuppressionSurvivesAnUnavailableFixedPeriod() {
        val target=mutableStateOf<Long?>(null)
        mount { Headline(target.value,"last7",suppressInitial=true) }
        compose.onNodeWithTag("source-headline").assertTextEquals("—")
        compose.runOnIdle { target.value=1000L }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("source-headline").assertTextEquals("1000")
        compose.runOnIdle { target.value=2000L }
        compose.mainClock.advanceTimeBy(32)
        assertTrue(count() in 1001L..1999L)
        compose.mainClock.advanceTimeBy(1100)
        compose.onNodeWithTag("source-headline").assertTextEquals("2000")
        compose.mainClock.autoAdvance=true
    }

    @Test fun knownUnavailableDetailKeepsItsBaselineAndDoesNotConsumeSuppression() {
        val target=mutableStateOf<Long?>(1000L)
        val unavailable=mutableStateOf(true)
        mount { Headline(target.value,suppressInitial=true,detailUnavailable=unavailable.value) }
        compose.onNodeWithTag("source-headline").assertTextEquals("1000")
        compose.runOnIdle { target.value=2000L;unavailable.value=false }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("source-headline").assertTextEquals("2000")
        compose.runOnIdle { target.value=2400L;unavailable.value=true }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("source-headline").assertTextEquals("2400")
        compose.runOnIdle { target.value=3400L;unavailable.value=false }
        compose.mainClock.advanceTimeBy(32)
        assertTrue(count() in 2401L..3399L)
        compose.mainClock.advanceTimeBy(250)
        assertTrue(count() in 3000L..3200L)
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("source-headline").assertTextEquals("3400")
        compose.mainClock.autoAdvance=true
    }
}
