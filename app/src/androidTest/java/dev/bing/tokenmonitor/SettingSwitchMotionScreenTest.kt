package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingSwitchMotionScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun position()=compose.onNodeWithTag("switch").fetchSemanticsNode().config[SettingSwitchPosition]
    private fun playing()=compose.onNodeWithTag("switch").fetchSemanticsNode().config[SettingSwitchPlaying]
    @Test fun immediateValueRapidReverseAndReductionSettle() {
        compose.mainClock.autoAdvance=false
        val value=mutableStateOf(false);val reduced=mutableStateOf("off")
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(false,LanguageChoice.ENGLISH,AppearancePreferences(reduceMotion=reduced.value)) {Surface {Column {DesktopSettingSwitch("Setting",value.value,"switch") {value.value=it}}}}}}
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("switch").performClick();assertTrue(value.value)
        compose.mainClock.advanceTimeBy(64);val forward=position();assertTrue(forward>0f && forward<1f)
        saveVisualEvidence(compose.activity,compose.onRoot(),"setting-switch-middle.png")
        compose.onNodeWithTag("switch").performClick();assertFalse(value.value)
        compose.mainClock.advanceTimeBy(32);val reverseStart=position();assertTrue(reverseStart>0f && reverseStart<1f)
        compose.mainClock.advanceTimeBy(48);val backwards=position();assertTrue(backwards<reverseStart)
        compose.onNodeWithTag("switch").performClick();assertTrue(value.value)
        compose.mainClock.advanceTimeBy(240);assertEquals(1f,position(),0f);assertFalse(playing())
        compose.runOnIdle {reduced.value="on";value.value=false};compose.mainClock.advanceTimeBy(32)
        assertEquals(0f,position(),0f);assertFalse(playing())
        saveVisualEvidence(compose.activity,compose.onRoot(),"setting-switch-reduced.png")
        compose.mainClock.autoAdvance=true
    }
    @Test fun zeroSystemScaleAndBackgroundSettleCurrentSetting() {
        compose.mainClock.autoAdvance=false
        val value=mutableStateOf(false);val scale=mutableFloatStateOf(1f)
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(true,LanguageChoice.ENGLISH,AppearancePreferences(reduceMotion="off")) {CompositionLocalProvider(LocalHeadlineMotionScale provides 1f,LocalDesktopSystemMotionScale provides scale.floatValue) {Surface {DesktopSettingSwitch("Setting",value.value,"switch") {value.value=it}}}}}}
        compose.mainClock.advanceTimeBy(32);compose.onNodeWithTag("switch").performClick();compose.mainClock.advanceTimeBy(64);assertTrue(playing())
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);compose.mainClock.advanceTimeBy(32)
        assertEquals(1f,position(),0f);assertFalse(playing())
        compose.runOnIdle {scale.floatValue=0f;value.value=false};compose.mainClock.advanceTimeBy(32)
        assertEquals(0f,position(),0f);assertFalse(playing());compose.mainClock.autoAdvance=true
    }
}
