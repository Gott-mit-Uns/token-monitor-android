package dev.bing.tokenmonitor

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Actual Hub and UI. All private comparisons stay in memory; no pictures or trees are saved. */
class LiveLimitDisplayTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun realQuotaControlsKeepOriginalAccountsEncryptedCacheAndSseAndRestoreAfterHome() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val port=InstrumentationRegistry.getArguments().getString("liveHubPort")!!.toInt();val store=HubStore(context)
        assertNull(store.load());lateinit var model:HubViewModel
        var expectedLimits=LimitDisplayPreferences(true,true,true)
        val expectedMain=MainDisplayPreferences(viewOrder=listOf("limits","home"),hiddenViews=setOf("projects"),titleIconOnly=false,compactTotal=true,compactUnits="localized",showLiveRate=true,rateMode="burn",swapActions=true,sessionContextMetric="remaining")
        compose.activityRule.scenario.onActivity {model=ViewModelProvider(it)[HubViewModel::class.java]}
        fun await(condition:()->Boolean) {
            val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(25)
            while(!condition() && System.nanoTime()<until) {compose.waitForIdle();Thread.sleep(30)}
            assertTrue("Real quota condition unavailable; private diagnostics withheld",condition())
        }
        fun stage(value:String)=instrumentation.sendStatus(2,Bundle().apply {putString("nativeLiveStage",value)})
        fun focused()=compose.activityRule.scenario.state==Lifecycle.State.RESUMED && run {var focus=false;compose.activityRule.scenario.onActivity {focus=it.hasWindowFocus()};focus}
        fun click(tag:String) {compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performClick()}
        fun verify() {
            await {store.load()?.let {HubParser.parse(it.snapshot)==model.state.value.snapshot && it.history?.let(HistoryParser::parse)==model.state.value.history && it.subscriptions?.let(SubscriptionParser::parse)==model.state.value.subscriptions && it.subscriptions!=null}==true}
            assertTrue("Quota choices persist independently",model.display.value.limits==expectedLimits && model.display.value.main==expectedMain && DisplayPreferenceStore(context).load()==model.display.value)
            assertTrue("Changing quota display keeps foreground stream live",model.state.value.live)
            val accounts=LimitDisplay.groups(model.state.value.snapshot!!.limits)
            val firstAccount=accounts.firstOrNull()
            val rows=firstAccount?.let {LimitPreferences.filter(LimitProviderLayout.project(LimitProviderLayout.record(it),true),expectedLimits.hiddenItems[it.first().provider].orEmpty())}.orEmpty()
            val eligible=rows.withIndex().firstOrNull {it.value.sourceIndex!=null && it.value.value.endsWith("% used") && it.value.percent!=null}
            val candidate=eligible?.value?.sourceIndex?.let {firstAccount?.getOrNull(it)}
            if(candidate!=null && eligible!=null) {
                val tag="limit-value-${candidate.presentation.recordIndex ?: 0}-${eligible.index}"
                compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))
                val expected="${Math.round(100-(candidate.remaining ?: (100-candidate.presentation.usedPercent!!)))}% used"
                assertTrue("Existing actual quota follows used-mode complement",compose.onNodeWithTag(tag).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].any {it.text==expected})
            }
            val emails=accounts.mapNotNull {it.first().presentation.identity.email}.filter {it.isNotBlank() && it.contains('@')}.distinct()
            assertTrue("Visible quota accounts respect email masking",emails.all {compose.onAllNodesWithText(it,substring=true,useUnmergedTree=true).fetchSemanticsNodes().isEmpty()})
        }
        try {
            await {!model.state.value.loading && focused()}
            compose.activityRule.scenario.onActivity {model.connect("http://10.0.2.2:$port",UUID.randomUUID().toString(),"")}
            await {model.state.value.live && model.state.value.history!=null && model.state.value.modelNames!=null && !model.state.value.loading && store.load()?.history!=null && store.load()?.modelNames!=null};stage("sustain")
            compose.onNodeWithTag("hub-settings-toggle").performClick();click("limit-settings-toggle");click("limit-mode-used");click("limit-mask-emails");click("limit-show-source")
            await {model.display.value.limits==LimitDisplayPreferences(true,true,true)}
            // New settings copy reads the same real Hub rows; all private comparisons stay here.
            val reported=LimitDisplay.groups(model.state.value.snapshot!!.limits).groupBy {it.first().provider}
            val selected=reported.entries.firstOrNull {it.key in LimitPreferences.providers && LimitSettingsPresentation.accounts(it.value).isNotEmpty()}
            assertTrue("A real reported quota account is required for this check",selected!=null)
            val providerId=selected!!.key;val groups=selected.value;val reading=LimitSettingsPresentation.record(groups)!!
            val locale=when(model.display.value.language) {LanguageChoice.CHINESE->java.util.Locale.SIMPLIFIED_CHINESE;LanguageChoice.ENGLISH->java.util.Locale.US;LanguageChoice.SYSTEM->context.resources.configuration.locales[0]}
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("limit-provider-row-$providerId"))
            val dot=compose.onAllNodesWithTag("limit-provider-detected-$providerId",useUnmergedTree=true).fetchSemanticsNodes()
            assertTrue("Reported status uses the desktop detection rule",dot.isNotEmpty()==LimitSettingsPresentation.detected(reading))
            val status=LimitSettingsPresentation.status(reading)
            if(status!=null && !LimitSettingsPresentation.detected(reading)) {
                val displayed=compose.onAllNodesWithTag("limit-provider-status-$providerId",useUnmergedTree=true).fetchSemanticsNodes()
                assertTrue("Reported status wording matches original desktop semantics",displayed.any {node->node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].any {it.text==LimitSettingsPresentation.text(locale,status.label)}})
            }
            click("limit-items-$providerId")
            val accounts=LimitSettingsPresentation.accounts(groups);val peers=accounts.map {it.first()}
            assertTrue("Reported account summaries obey existing privacy rules",accounts.withIndex().all {(index,group)->
                val expected=LimitDisplay.accountTitle(group.first(),peers,index,true)
                compose.onAllNodesWithText(expected,useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()
            })
            stage("provider_copy_verified")
            openSettingsSection(compose,"main");click("main-view-home-settings");click("home-quota-settings-toggle");click("home-quota-highlight")
            await {model.display.value.limits.highlightLowHomeLimits && DisplayPreferenceStore(context).load().limits.highlightLowHomeLimits}
            // Existing private readings remain in memory. Presentation choices never write to Hub.
            val candidate=LimitDisplay.groups(model.state.value.snapshot!!.limits).firstOrNull {it.first().provider in LimitPreferences.providers}
            val item=candidate?.let {LimitProviderLayout.project(LimitProviderLayout.record(it)).firstOrNull {row->row.item!=null}?.item}
            val hidden=if(candidate!=null && item!=null) LimitPreferences.setHidden(emptyMap(),candidate.first().provider,item,true) else emptyMap()
            expectedLimits=expectedLimits.copy(hiddenItems=hidden,providerOrder=listOf("mimo","codex"),homeAccountCount=5,homeBars=true,showHomeProviderNames=true,highlightLowHomeLimits=true)
            compose.activityRule.scenario.onActivity {model.updateDisplay(model.display.value.copy(limits=expectedLimits,main=expectedMain))}
            await {model.display.value.limits==expectedLimits && model.display.value.main==expectedMain && DisplayPreferenceStore(context).load()==model.display.value}
            compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-limits").performScrollTo().performClick()
            verify();compose.onNodeWithTag("hub-live-rate").assertExists();stage("limits_verified")
            assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
            await {!model.state.value.live && compose.activityRule.scenario.state!=Lifecycle.State.RESUMED};stage("background");Thread.sleep(6000)
            context.startActivity(Intent(Intent.ACTION_MAIN).setClass(context,MainActivity::class.java).addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            await {model.state.value.live && focused() && !model.state.value.loading};verify();stage("resumed")
        } finally {
            instrumentation.runOnMainSync {model.setForeground(false);model.forget()};await {!model.state.value.loading};store.clear()
        }
    }
}
