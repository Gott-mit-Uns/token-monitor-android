package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Measured native Compose geometry, not a layout policy inferred from source. */
class SyncDeviceGridScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private val now=Instant.parse("2026-10-14T04:00:00Z").toEpochMilli()
    private val endpoint="https://sync-fixture.example.invalid"

    @Test fun normal320UsesTwoEqualColumnsInExistingOrderAndKeepsOddItemHalfWidth() = normalGrid(320)
    @Test fun normal393UsesTwoEqualColumnsInExistingOrderAndKeepsOddItemHalfWidth() = normalGrid(393)
    @Test fun large320KeepsCompleteLongFieldsAndGrowsRowsWithoutShrinkingText() = longGrid(320)
    @Test fun large393KeepsCompleteLongFieldsAndGrowsRowsWithoutShrinkingText() = longGrid(393)

    @Test fun emptyCollectorsKeepViewerAndHubRolesWithoutInventingDeviceCards() {
        mount(320,mutableStateOf(1f),HubState())
        compose.onNodeWithTag("sync-viewer-information").assertExists()
        compose.onNodeWithText("本机 · 只读查看手机").assertExists()
        compose.onNodeWithTag("sync-hub-information").assertExists()
        compose.onNodeWithTag("sync-devices-empty").assertTextEquals("尚未提供采集设备。")
        compose.onNodeWithTag("sync-device-row-0").assertDoesNotExist()
        val collection=compose.onNodeWithTag("sync-device-grid").fetchSemanticsNode().config[SemanticsProperties.CollectionInfo]
        assertEquals(0,collection.rowCount)
        assertEquals(2,collection.columnCount)
        screenshot("empty-320-font1")
    }

    private fun mount(width:Int,font:MutableState<Float>,state:HubState) {
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,LanguageChoice.CHINESE) {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,font.value)) {
                Column(Modifier.width(width.dp).background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState()).padding(CompactUiTokens.CardContentPadding)) {
                    HubSyncDevices(state,now)
                }
            }
        }}}
    }

    private fun normalGrid(width:Int) {
        // Deliberately shuffled: online/receipt ordering must survive row-major pairing.
        val snapshot=HubParser.parse("""{"periods":{"today":{}},"devices":[
          {"deviceId":"offline","displayName":"Fixture Offline","stale":true,"updatedAt":"2026-10-14T03:58:00Z"},
          {"deviceId":"unknown"},
          {"deviceId":"windows","displayName":"Fixture Windows","platform":"win32","osName":"Windows 11","osVersion":"26H2","agentRuntime":"electron-widget","agentVersion":"0.70.0","stale":false,"updatedAt":"2026-10-14T03:55:00Z"},
          {"deviceId":"agent","displayName":"Fixture Agent","platform":"linux","osName":"Debian GNU/Linux","osVersion":"12","agentRuntime":"headless-agent","agentVersion":"0.70.0-01","stale":false,"receivedAt":"2026-10-14T03:57:00Z"},
          {"deviceId":"mac","displayName":"Fixture Mac","platform":"darwin","osName":"macOS","osVersion":"27.0.1","agentRuntime":"electron-widget","agentVersion":"0.70.0","stale":false,"receivedAt":"2026-10-14T04:00:00Z","updatedAt":"2026-10-14T01:00:00Z"}
        ]}""")
        mount(width,mutableStateOf(1f),HubState(snapshot=snapshot,connectedEndpoint=endpoint,lastSuccessfulEndpoint=endpoint))
        assertGrid(listOf("mac","agent","windows","unknown","offline"))
        complete("sync-device-platform-mac","macOS 27.0.1")
        complete("sync-device-version-mac","Widget v0.70.0")
        complete("sync-device-status-mac","在线 · 刚刚同步")
        complete("sync-device-status-agent","在线 · 3 分钟前同步")
        complete("sync-device-platform-unknown","未提供")
        complete("sync-device-version-unknown","采集版本未提供")
        complete("sync-device-status-unknown","在线状态未知 · 同步时间未提供")
        compose.onNodeWithTag("sync-device-offline").performScrollTo()
        screenshot("grid-$width-font1-bottom")
        compose.onNodeWithTag("sync-device-mac").performScrollTo()
        screenshot("grid-$width-font1-top")
    }

    private fun longGrid(width:Int) {
        val name="Fixture office workstation with a long uploaded name 长设备名称完整显示"
        val platform="Fixture operating system with a long description"
        val version="0.70.0-01-fixture-long-version-for-natural-wrap"
        val snapshot=HubParser.parse("""{"periods":{"today":{}},"devices":[
          {"deviceId":"long","displayName":"$name","osName":"$platform","osVersion":"27.0.1 fixture edition","agentRuntime":"headless-agent","agentVersion":"$version","stale":false,"receivedAt":"2026-10-14T03:57:00Z"},
          {"deviceId":"peer","displayName":"Fixture Peer","stale":false,"receivedAt":"2026-10-14T03:56:00Z"},
          {"deviceId":"missing"}
        ]}""")
        val font=mutableStateOf(1f)
        mount(width,font,HubState(snapshot=snapshot,lastSuccessfulEndpoint=endpoint,error=R.string.error_no_network))
        assertGrid(listOf("long","peer","missing"))
        val baselineHeight=compose.onNodeWithTag("sync-device-row-0").getUnclippedBoundsInRoot().height.value
        val baselineName=complete("sync-device-name-long",name)
        compose.runOnIdle {font.value=2f}
        assertGrid(listOf("long","peer","missing"))
        val enlargedHeight=compose.onNodeWithTag("sync-device-row-0").getUnclippedBoundsInRoot().height.value
        assertTrue("Font 2 makes the row naturally taller",enlargedHeight>baselineHeight)
        val enlargedName=complete("sync-device-name-long",name,wrap=true)
        assertEquals("Names retain their original sp size",baselineName.layoutInput.style.fontSize,enlargedName.layoutInput.style.fontSize)
        assertEquals(2f,enlargedName.layoutInput.density.fontScale,0f)
        screenshot("grid-$width-font2-long-name")
        complete("sync-device-platform-long","$platform 27.0.1 fixture edition",wrap=true)
        complete("sync-device-version-long","Agent v$version",wrap=true)
        complete("sync-device-status-long","在线（上次报告） · 3 分钟前同步",wrap=true)
        assertFieldsWithinCard("long")
        screenshot("grid-$width-font2-long-details")
        complete("sync-device-platform-missing","未提供")
        complete("sync-device-version-missing","采集版本未提供")
        complete("sync-device-status-missing","在线状态未知 · 同步时间未提供")
        screenshot("grid-$width-font2-odd-missing")
    }

    private fun assertGrid(ids:List<String>) {
        val grid=compose.onNodeWithTag("sync-device-grid").getUnclippedBoundsInRoot()
        val cards=ids.map {compose.onNodeWithTag("sync-device-$it").getUnclippedBoundsInRoot()}
        cards.forEachIndexed {index,card->
            assertEquals("Equal column width",cards.first().width.value,card.width.value,.5f)
            val collection=compose.onNodeWithTag("sync-device-${ids[index]}").fetchSemanticsNode().config[SemanticsProperties.CollectionItemInfo]
            assertEquals(index/2,collection.rowIndex)
            assertEquals(index%2,collection.columnIndex)
            assertTrue("Every card remains inside grid width",card.left>=grid.left && card.right<=grid.right)
            if(index%2==0) assertEquals(grid.left.value,card.left.value,.5f)
            else {
                val previous=cards[index-1]
                assertEquals("Pair shares row top",previous.top.value,card.top.value,.5f)
                assertEquals("Pair grows to the same natural height",previous.height.value,card.height.value,.5f)
                assertEquals("Small shared column gap",CompactUiTokens.SectionSpacing.value,card.left.value-previous.right.value,.5f)
            }
            if(index>=2) assertEquals("Next pair follows the full preceding row",CompactUiTokens.SectionSpacing.value,
                card.top.value-cards[index-2].bottom.value,.5f)
        }
        assertEquals("Odd last collector retains half width",(grid.width.value-CompactUiTokens.SectionSpacing.value)/2,cards.last().width.value,.5f)
        assertTrue("Odd last collector never stretches across both columns",cards.last().right<grid.right)
        val viewer=compose.onNodeWithTag("sync-viewer-information").getUnclippedBoundsInRoot()
        val hub=compose.onNodeWithTag("sync-hub-information").getUnclippedBoundsInRoot()
        assertEquals(grid.width.value,viewer.width.value,.5f)
        assertEquals(grid.width.value,hub.width.value,.5f)
        assertTrue("Phone stays outside the collector grid",viewer.bottom<=grid.top)
        assertTrue("Hub stays outside the collector grid",hub.bottom<=grid.top)
    }

    private fun complete(tag:String,expected:String,wrap:Boolean=false):TextLayoutResult {
        val node=compose.onNodeWithTag(tag).performScrollTo().assertTextEquals(expected)
        val layouts=mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        val layout=layouts.single()
        assertEquals(expected,layout.layoutInput.text.text)
        assertFalse("$tag preserves complete text without clipping or ellipsis",layout.hasVisualOverflow)
        if(wrap) assertTrue("$tag wraps naturally",layout.lineCount>1)
        return layout
    }

    private fun assertFieldsWithinCard(id:String) {
        val card=compose.onNodeWithTag("sync-device-$id").getUnclippedBoundsInRoot()
        listOf("name","platform","version","status").forEach {field->
            val child=compose.onNodeWithTag("sync-device-$field-$id").getUnclippedBoundsInRoot()
            assertTrue("$field stays within natural card width",child.left>=card.left && child.right<=card.right)
            assertTrue("$field stays within natural card height",child.top>=card.top && child.bottom<=card.bottom)
        }
    }

    private fun screenshot(suffix:String) = saveVisualEvidence(compose.activity,compose.onRoot(),"settings064-sync-$suffix-${if(dark) "dark" else "light"}.png")
}
