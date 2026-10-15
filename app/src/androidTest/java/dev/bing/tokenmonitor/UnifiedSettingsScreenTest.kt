package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UnifiedSettingsScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private val tags=listOf("android-update-check","about-desktop-latest","android-update-current-notes","about-open-licenses")
    @Test fun fourOriginalAboutActionsShareOneRowAndKeepEveryDestination() = aboutActions(353,1f,false)
    @Test fun narrowLargeTextUsesTwoColumnsWithoutShrinkingOrClipping() = aboutActions(240,2f,true)
    @Test fun narrowLargeTextKeepsLongAddressAndDeviceNameCompleteAndMissingFieldsUnknown() {
        val address="https://very-long-public-host-for-sync-acceptance.example.invalid:9443"
        val name="Fixture office workstation with a deliberately long uploaded display name 长设备名称完整显示"
        val snapshot=HubParser.parse("""{"periods":{"today":{}},"devices":[{"deviceId":"long-fixture","displayName":"$name"}]}""")
        val state=HubState(snapshot=snapshot,lastSuccessfulEndpoint=address,error=R.string.error_no_network)
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,LanguageChoice.CHINESE) {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                Column(Modifier.width(240.dp).background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(8.dp)) {
                    SyncEndpointAddresses(listOf(address),state)
                    HubSyncDevices(state,java.time.Instant.parse("2026-10-14T04:00:00Z").toEpochMilli())
                }
            }
        }}}
        fun complete(tag:String,expected:String,wrap:Boolean=false) {
            val node=compose.onNodeWithTag(tag).performScrollTo().assertTextEquals(expected)
            val layouts=mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
            val layout=layouts.single()
            assertEquals(expected,layout.layoutInput.text.text)
            assertFalse("$tag paints complete natural text",layout.hasVisualOverflow)
            if(wrap) assertTrue("$tag wraps rather than shrinking a long label",layout.lineCount>1)
        }
        complete("sync-endpoint-address-https",address,true)
        compose.onNodeWithTag("sync-endpoint-https").assertIsNotSelected()
        compose.onNodeWithTag("sync-endpoint-status-https").assertTextEquals("上次连接")
        complete("sync-endpoint-address-http","未配置")
        saveVisualEvidence(compose.activity,compose.onRoot(),"settings064-sync-long-address-font2-${if(dark) "dark" else "light"}.png")
        complete("sync-device-name-long-fixture",name,true)
        val grid=compose.onNodeWithTag("sync-device-grid").getUnclippedBoundsInRoot()
        val card=compose.onNodeWithTag("sync-device-long-fixture").getUnclippedBoundsInRoot()
        assertEquals("A narrow odd collector still keeps one of two columns",(grid.width.value-CompactUiTokens.SectionSpacing.value)/2,card.width.value,.5f)
        assertTrue("The empty peer is not replaced by a full-width card",card.right<grid.right)
        complete("sync-device-platform-long-fixture","未提供")
        complete("sync-device-version-long-fixture","采集版本未提供")
        complete("sync-device-status-long-fixture","在线状态未知 · 同步时间未提供")
        saveVisualEvidence(compose.activity,compose.onRoot(),"settings064-sync-long-device-font2-${if(dark) "dark" else "light"}.png")
    }
    private fun aboutActions(width:Int,font:Float,twoColumns:Boolean) {
        val opened=mutableListOf<String>();var checks=0;var licenses=0
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,LanguageChoice.CHINESE) {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,font)) {
                Column(Modifier.width(width.dp).background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
                    AndroidUpdateContent("0.2.62",AndroidUpdateState.Idle,{checks++},opened::add,{licenses++},DesktopReleaseState.Ready(DesktopRelease("0.72.0","${DesktopReleases.repository}/releases/tag/v0.72.0")))
                }
            }
        }}}
        val bounds=tags.map {compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot}
        val pixels=compose.activity.resources.displayMetrics.density
        bounds.forEach {assertTrue("Every action retains a 48dp touch area",it.height>=48*pixels-.1f && it.width>=48*pixels-.1f)}
        assertEquals(bounds[0].top,bounds[1].top,.1f)
        if(twoColumns) {
            assertTrue(bounds[2].top>=bounds[0].bottom)
            assertEquals(bounds[2].top,bounds[3].top,.1f)
        } else {
            assertEquals(bounds[0].top,bounds[2].top,.1f);assertEquals(bounds[0].top,bounds[3].top,.1f)
            assertEquals(bounds[0].height,bounds[3].height,.1f)
        }
        saveVisualEvidence(compose.activity,compose.onRoot(),"settings064-about-${if(twoColumns) "large" else "regular"}-${if(dark) "dark" else "light"}.png")
        tags.forEach {tag->compose.onNodeWithTag(tag).performScrollTo().performClick()}
        assertEquals(1,checks);assertEquals(1,licenses)
        assertEquals(listOf("${DesktopReleases.repository}/releases/tag/v0.72.0",AndroidReleases.currentNotes("0.2.62")),opened)
    }
    @Test fun actualAboutOpensCompleteSelectableLicenses() {
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,LanguageChoice.CHINESE) {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(20.dp).verticalScroll(rememberScrollState())) {AboutSettings()}
        }}}
        compose.onNodeWithTag("about-open-licenses").performScrollTo().performClick()
        compose.onNodeWithText("来源与许可").assertExists()
        compose.onNodeWithTag("about-licenses-content").performScrollToNode(hasText("MIT License"))
        compose.onNodeWithText("MIT License").assertExists()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("about-open-licenses").assertExists()
        compose.onNodeWithTag("about-android-version").performScrollTo()
        saveVisualEvidence(compose.activity,compose.onRoot(),"settings064-about-full-${if(dark) "dark" else "light"}.png")
    }
    @Test fun dualEndpointsUseReceiptAndMissingDeviceMetadataStaysUnknown() {
        val secure="https://public.example.invalid";val local="http://192.168.1.1:17321"
        val now=java.time.Instant.parse("2026-10-14T04:00:00Z").toEpochMilli()
        val snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":183500000}},"devices":[{"deviceId":"mac-fixture","displayName":"Fixture Mac","platform":"darwin","osName":"macOS","osVersion":"27.0.1","agentRuntime":"electron-widget","agentVersion":"0.70.0","stale":false,"updatedAt":"2026-10-14T04:00:00Z"},{"deviceId":"agent-fixture","displayName":"Fixture Agent","platform":"linux","osName":"Debian GNU/Linux","osVersion":"12","agentRuntime":"headless-agent","agentVersion":"0.70.0-01","stale":false,"updatedAt":"2026-10-14T03:57:00Z"},{"deviceId":"old-fixture"}]}""")
        val state=mutableStateOf(HubState(snapshot=snapshot,lastSuccessfulEndpoint=local,connectedEndpoint=local))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,LanguageChoice.CHINESE) {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(20.dp).verticalScroll(rememberScrollState())) {
                SyncEndpointAddresses(listOf(secure,local),state.value);HubSyncDevices(state.value,now)
            }
        }}}
        compose.onNodeWithTag("sync-endpoint-https").assertIsNotSelected()
        compose.onNodeWithTag("sync-endpoint-http").assertIsSelected()
        compose.onNodeWithTag("sync-endpoint-status-http").assertTextEquals("当前使用")
        compose.onNodeWithTag("sync-endpoint-address-https").assertTextEquals(secure)
        compose.onNodeWithTag("sync-endpoint-address-http").assertTextEquals(local)
        saveVisualEvidence(compose.activity,compose.onRoot(),"settings064-sync-live-${if(dark) "dark" else "light"}.png")
        compose.onNodeWithTag("sync-device-platform-mac-fixture").performScrollTo().assertTextEquals("macOS 27.0.1")
        compose.onNodeWithTag("sync-device-version-mac-fixture").assertTextEquals("Widget v0.70.0")
        compose.onNodeWithTag("sync-device-status-agent-fixture").performScrollTo().assertTextContains("3 分钟前同步",substring=true)
        compose.onNodeWithTag("sync-device-platform-old-fixture").performScrollTo().assertTextEquals("未提供")
        compose.onNodeWithTag("sync-device-version-old-fixture").assertTextEquals("采集版本未提供")
        compose.onNodeWithTag("sync-device-status-old-fixture").assertTextEquals("在线状态未知 · 同步时间未提供")
        compose.runOnIdle {state.value=state.value.copy(connectedEndpoint=null,error=R.string.error_no_network)}
        compose.onNodeWithTag("sync-endpoint-http").performScrollTo().assertIsNotSelected()
        compose.onNodeWithTag("sync-endpoint-status-http").assertTextEquals("上次连接")
        compose.onNodeWithTag("sync-device-status-mac-fixture").performScrollTo().assertTextContains("上次报告",substring=true)
        saveVisualEvidence(compose.activity,compose.onRoot(),"settings064-sync-offline-${if(dark) "dark" else "light"}.png")
    }
}
