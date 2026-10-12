package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class StatusMotionScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private fun save(page:String)=saveVisualEvidence(compose.activity,compose.onRoot(),"status-motion-$page-${if(dark) "dark" else "light"}.png")
    private fun phase(tag:String)=compose.onNodeWithTag(tag,useUnmergedTree=true).fetchSemanticsNode().config[StatusMotionPhase]
    @Test fun originalSpinnerAdvancesOnlyWhileRunningForegroundAndMotionEnabled() {
        compose.mainClock.autoAdvance=false
        val state=mutableStateOf("running");val preference=mutableStateOf("off")
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,LanguageChoice.ENGLISH,AppearancePreferences(reduceMotion=preference.value)) {Surface(color=MaterialTheme.colorScheme.background) {Column {SessionActivityMark(state.value)}}}}}
        compose.mainClock.advanceTimeBy(32);save("spinner-start")
        compose.mainClock.advanceTimeBy(240);assertTrue(phase("session-state-running")>.2f);save("spinner-middle")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED);compose.mainClock.advanceTimeBy(400)
        assertEquals(Lifecycle.State.CREATED,compose.activityRule.scenario.state)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);compose.mainClock.advanceTimeBy(32)
        assertTrue(phase("session-state-running")<.1f)
        compose.runOnIdle {preference.value="on"};compose.mainClock.advanceTimeBy(32);save("spinner-reduced")
        assertEquals(0f,phase("session-state-running"),0f);compose.mainClock.advanceTimeBy(1000);assertEquals(0f,phase("session-state-running"),0f)
        compose.runOnIdle {preference.value="off";state.value="ended"};compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("session-state-running",useUnmergedTree=true).assertDoesNotExist();assertEquals(0f,phase("session-state-ended"),0f)
        compose.mainClock.autoAdvance=true
    }
    @Test fun freshRateFlashesOnceEqualRevisionAndModeKeepTimingBackgroundAndReductionSettle() {
        compose.mainClock.autoAdvance=false
        val sample=mutableStateOf<RateSample?>(null);val main=mutableStateOf(MainDisplayPreferences(showLiveRate=true));val reduction=mutableStateOf("off")
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,LanguageChoice.ENGLISH,AppearancePreferences(reduceMotion=reduction.value)) {Surface(color=MaterialTheme.colorScheme.background) {LiveTokenRateControl(sample.value,main.value,{main.value=it.apply(DisplayPreferences(main=main.value)).main})}}}}
        fun playing(value:Boolean)=compose.onNodeWithTag("live-rate-flash",useUnmergedTree=true).assert(SemanticsMatcher.expectValue(StatusMotionPlaying,value))
        fun fresh(revision:Long,idle:Boolean=false)=RateSample(10.0,600.0,0,revision,emptyList(),1,8000,idle)
        compose.mainClock.advanceTimeBy(32);playing(false)
        compose.runOnIdle {sample.value=fresh(1)};compose.mainClock.advanceTimeBy(64);playing(true);save("rate-start")
        compose.mainClock.advanceTimeBy(200);assertTrue(phase("live-rate-flash")>1f);save("rate-middle")
        compose.runOnIdle {sample.value=sample.value!!.copy(expiresAt=9000)}
        compose.onNodeWithTag("hub-live-rate").performClick();compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("hub-live-rate").assertTextEquals("600 TPM")
        compose.mainClock.advanceTimeBy(400);playing(false);assertEquals(1f,phase("live-rate-flash"),0f);save("rate-end")
        compose.runOnIdle {sample.value=fresh(2)};compose.mainClock.advanceTimeBy(64);playing(true)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED);compose.mainClock.advanceTimeBy(300)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);compose.mainClock.advanceTimeBy(64);playing(false)
        compose.runOnIdle {reduction.value="on";sample.value=fresh(3)};compose.mainClock.advanceTimeBy(64);playing(false);save("rate-reduced")
        compose.runOnIdle {reduction.value="off";sample.value=fresh(4,true)};compose.mainClock.advanceTimeBy(64);playing(false)
        compose.mainClock.autoAdvance=true
    }
}
