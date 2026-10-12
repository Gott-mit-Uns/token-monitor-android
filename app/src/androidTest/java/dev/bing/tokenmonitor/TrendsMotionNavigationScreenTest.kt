package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** Actual main-screen entry decisions, not only a standalone animation holder. */
class TrendsMotionNavigationScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val animationClock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
    private val today=LocalDate.of(2026,10,9)
    private val history=HubHistory(List(7) {index->HistoryRow(today.minusDays((6-index).toLong()).toString(),100,"1")},emptyList(),HistorySummary(700,"7",7,7,7,0,100,"public-model",7))
    private val snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":100,"costUsd":1},"month":{"totalTokens":700,"costUsd":7},"allTime":{"totalTokens":700,"costUsd":7}}}""").copy(historyPreview=history)
    private class Actions:HubUiActions {
        var reads=0
        override fun connect(address:String,credential:String,alternate:String) {reads++}
        override fun refreshSaved() {reads++}
        override fun loadHistory() {reads++}
        override fun loadDeviceHistory() {reads++}
        override fun loadModelNames() {reads++}
        override fun loadSubscriptions() {reads++}
        override fun switchEndpoint() {reads++}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun forget() {}
        override fun renameDevice(id:String,input:String) {}
    }
    private fun choose(view:String) {
        Log.i("PublicMotionTest","choose-$view-open")
        // Menu focus/scroll synchronization requires frames. Finish that work
        // before pausing again, so selecting the main view starts its chart
        // entry under the measured manual clock rather than fast-forwarding it.
        compose.mainClock.autoAdvance=true
        val item=compose.onNodeWithTag("view-$view")
        try {
            compose.onNodeWithTag("hub-view-selector").performClick()
            item.performScrollTo().assertIsDisplayed()
        } finally {compose.mainClock.autoAdvance=false}
        item.performClick()
        compose.mainClock.advanceTimeBy(32)
        Log.i("PublicMotionTest","choose-$view-selected")
    }
    private fun capture(name:String) {
        if(InstrumentationRegistry.getArguments().getString("recordSourceMotion")=="true")
            saveVisualEvidence(compose.activity,compose.onRoot(),"source-motion-$name-dark.png")
    }
    private fun barPixels(index:Int):Int {
        Log.i("PublicMotionTest","bar-$index-capture")
        val image=compose.onNodeWithTag("trends-main-chart").captureToImage().toPixelMap()
        val density=compose.activity.resources.displayMetrics.density
        val inset=(4*density).roundToInt()
        val x=(inset+(image.width-2*inset)*(index+.5)/7).roundToInt()
        val bar=Color(0xFF6AB4F0)
        val expected=if(index==6) bar else bar.copy(alpha=.5f).compositeOver(Color(0xFF2F3437))
        fun near(color:Color)=listOf(color.red-expected.red,color.green-expected.green,color.blue-expected.blue).all {abs(it*255)<=2}
        return (0 until image.height).count {near(image[x,it])}.also {Log.i("PublicMotionTest","bar-$index-captured")}
    }

    @Test fun actualMainViewEntryRetargetsPixelsButDashboardAndSettingsReturnKeepTheCurrentBars() {
        val state=mutableStateOf(HubState(snapshot=snapshot,history=history,modelNames=ModelNames(emptyMap(),"off"),live=true))
        val actions=Actions()
        // HubScreen owns the effective inner theme. Force its local motion
        // preference rather than an outer system scale that the theme replaces.
        val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList(),
            appearance=AppearancePreferences(reduceMotion="off"))
        compose.activity.runOnUiThread {compose.activity.setContent {
            CompositionLocalProvider(LocalHubCalendarClock provides Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"),ZoneId.of("Asia/Shanghai"))) {
                TokenTheme(true,prefs.language,prefs.appearance) {
                    CompositionLocalProvider(LocalDesktopAnimationClock provides animationClock,LocalDesktopSystemMotionScale provides 1f,LocalHeadlineMotionScale provides 0f) {
                        HubScreen(state.value,actions,preferences=prefs)
                    }
                }
            }
        }}
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("100")
        compose.mainClock.autoAdvance=false
        try {
            choose("trends")
            val enteredFirst=barPixels(0)
            val enteredLast=barPixels(6)
            capture("entry")
            compose.mainClock.advanceTimeBy(640)
            val full=barPixels(0)
            capture("steady")
            assertTrue("Actual spark has a substantial plot",full>100)
            assertTrue("A main-view entry grows from zero ($enteredFirst / $full)",enteredFirst in 1 until full)
            assertTrue("The final date starts later than the first date",enteredLast<enteredFirst)
            assertEquals(full.toDouble(),barPixels(6).toDouble(),2.0)

            compose.onNodeWithTag("trends-main-open-history").performClick()
            Log.i("PublicMotionTest","detail-open")
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithTag("trends-history-detail").assertExists()
            compose.onNodeWithTag("trends-history-back").performClick()
            Log.i("PublicMotionTest","detail-return")
            compose.mainClock.advanceTimeBy(32)
            assertEquals("Dashboard return does not replay entry",full.toDouble(),barPixels(0).toDouble(),2.0)
            compose.onNodeWithTag("hub-settings-toggle").performClick()
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithTag("hub-settings-toggle").performClick()
            compose.mainClock.advanceTimeBy(32)
            assertEquals("Settings preserves the desktop breakdown",full.toDouble(),barPixels(0).toDouble(),2.0)

            compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot.copy(periods=snapshot.periods+("today" to snapshot.periods.getValue("today").copy(tokens=200))))}
            compose.mainClock.advanceTimeBy(32)
            val updating=barPixels(0)
            capture("update")
            compose.mainClock.advanceTimeBy(640)
            val half=barPixels(0)
            capture("end")
            assertEquals("Doubling today's maximum halves prior equal bars",full/2.0,half.toDouble(),2.0)
            assertTrue("Existing dates shrink from their displayed height",updating>half && updating<full)
            compose.onNodeWithTag("hub-total-tokens").assertTextEquals("200")

            choose("tools")
            compose.onNodeWithTag("trends-main-chart").assertDoesNotExist()
            choose("trends")
            assertTrue("Returning from a distinct main view starts an entry",barPixels(0)<half)
            compose.mainClock.advanceTimeBy(640)
            assertEquals(half.toDouble(),barPixels(0).toDouble(),2.0)
            assertEquals("Display motion and navigation add no Hub reads",0,actions.reads)
        } finally {compose.mainClock.autoAdvance=true}
    }
}
