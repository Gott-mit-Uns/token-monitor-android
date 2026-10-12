package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AverageRateScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private class Actions:HubUiActions {
        var reads=0
        override fun connect(address:String,credential:String,alternate:String) {reads++}
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
    private fun snapshot(output:Int=40)=HubParser.parse("""{"periods":{"today":{"totalTokens":1000,"timedTokens":100,"timedOutputTokens":$output,"timedDurationMs":1000},"month":{"totalTokens":1000,"timedTokens":10000000,"timedOutputTokens":1000000,"timedDurationMs":500},"allTime":{"totalTokens":1000}}}""")
    private fun show()=compose.onNodeWithTag("hub-title-control").performTouchInput {longClick()}
    private fun save(name:String)=saveVisualEvidence(compose.activity,compose.onNode(isDialog()),"average-rate-$name-${if(dark) "dark" else "light"}.png")
    @Test fun cachedPeriodAverageUnitsAndLanguageStayIndependentOfFooterAndHomeClick() {
        val actions=Actions();val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList()))
        val state=HubState(snapshot=snapshot(),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(state,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}
        show();compose.onNodeWithTag("period-average-rate-value").assertTextEquals("≈ 40 tok/s");save("speed")
        compose.onNodeWithTag("period-average-rate-toggle").performClick();compose.onNodeWithTag("period-average-rate-value").assertTextEquals("≈ 6K tok/min");save("burn")
        assertEquals("burn",prefs.value.main.rateMode);compose.onNodeWithTag("hub-live-rate").assertDoesNotExist()
        compose.onNodeWithText("Close").performClick();compose.onNodeWithTag("hub-period-month").performClick();show()
        compose.onNodeWithTag("period-average-rate-toggle").performClick();compose.onNodeWithTag("period-average-rate-value").assertTextEquals("≈ 2M tok/s");save("month")
        compose.onNodeWithText("Close").performClick()
        compose.runOnIdle {prefs.value=prefs.value.copy(language=LanguageChoice.CHINESE,main=prefs.value.main.copy(compactUnits="localized"))}
        show();compose.onNodeWithTag("period-average-rate-value").assertTextEquals("≈ 200万 tok/s");save("chinese")
        compose.onNodeWithText("关闭").performClick();compose.onNodeWithTag("hub-period-allTime").performClick();show()
        compose.onNodeWithTag("period-average-rate-value").assertTextEquals("当前周期暂无平均性能读数");save("unavailable")
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-tools").performScrollTo().performClick();compose.onNodeWithTag("hub-title-control").performClick()
        compose.onNodeWithTag("hub-view-current").assertTextContains("主页")
        assertEquals(0,actions.reads)
    }
    @Test fun currentCachedCountersUpdateOpenReadingAndBackgroundClosesItWithoutReads() {
        val actions=Actions();val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList())
        val state=mutableStateOf(HubState(snapshot=snapshot(),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off")))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.language,prefs.appearance) {HubScreen(state.value,actions,preferences=prefs)}}}
        show();compose.onNodeWithTag("period-average-rate-value").assertTextEquals("≈ 40 tok/s")
        compose.runOnIdle {state.value=state.value.copy(snapshot=snapshot(80))}
        compose.onNodeWithTag("period-average-rate-value").assertTextEquals("≈ 80 tok/s");save("updated")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED);compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("period-average-rate").assertDoesNotExist();assertEquals(0,actions.reads)
    }
}
