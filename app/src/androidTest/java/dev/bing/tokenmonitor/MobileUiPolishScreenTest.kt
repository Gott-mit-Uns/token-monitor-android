package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MobileUiPolishScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun navigationHasNoInterRowGapsAndFullTouchTargets() {
        val expanded=mutableStateOf(true)
        compose.activity.runOnUiThread {compose.activity.setContent {
            TokenTheme(true,LanguageChoice.CHINESE,AppearancePreferences(reduceMotion="on")) {
                Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.BottomStart) {
                    Box(Modifier.size(126.dp,48.dp)) {DesktopViewMenu(expanded.value,HubPage.HOME,MainDisplay.ids.map {HubPage.valueOf(it.uppercase())},dismiss={expanded.value=false}) {}}
                }
            }
        }}
        val rows=MainDisplay.ids.map {HubPage.valueOf(it.uppercase())}.map {
            compose.onNodeWithTag("view-${it.name.lowercase()}").assertIsDisplayed().getUnclippedBoundsInRoot()
        }
        rows.forEach {assertTrue("48dp touch height retained",it.bottom-it.top>=48.dp)}
        rows.zipWithNext().forEach {(a,b)->assertEquals("No vertical row gap",a.bottom.value,b.top.value,.6f)}
        saveVisualEvidence(compose.activity,compose.onNodeWithTag("hub-view-menu"),"mobile053-navigation.png")
    }

    @Test fun currentViewPressDoesNotPaintAnOversizedRectangle() {
        var cycles=0
        compose.activity.runOnUiThread {compose.activity.setContent {
            TokenTheme(true) {
                Box(Modifier.fillMaxSize().padding(16.dp)) {
                    DesktopViewSwitcher(HubPage.HOME,HubPage.LIMITS,{cycles++},{})
                }
            }
        }}
        val node=compose.onNodeWithTag("hub-view-current")
        val before=node.captureToImage().toPixelMap()
        node.performTouchInput {down(center);advanceEventTime(100)}
        val during=node.captureToImage().toPixelMap()
        var changed=0
        for(y in 0 until before.height) for(x in 0 until before.width) if(before[x,y]!=during[x,y]) changed++
        node.performTouchInput {up()}
        assertEquals("Pressed touch area must not add a rectangular highlight",0,changed)
        assertEquals(1,cycles)
    }

    @Test fun quotaDetailUsesReadableLocalTimeAndSharedCompactType() {
        val raw="2026-10-12T08:16:41.764Z"
        val snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":1}},"limits":{"providers":[{"provider":"codex","status":"ok","updatedAt":"$raw","windows":[{"kind":"weekly","metric":"percent","remainingPercent":89,"resetsAt":"2026-10-19T01:17:35.000Z"}]}]}}""")
        compose.activity.runOnUiThread {compose.activity.setContent {
            TokenTheme(true,LanguageChoice.CHINESE) {Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {LimitWindow(snapshot.limits.single())}}
        }}
        compose.onNodeWithText("额度更新时间 ${LimitDetailTime.format(raw)}").assertIsDisplayed()
        compose.onAllNodesWithText(".764Z",substring=true).assertCountEquals(0)
        compose.onAllNodesWithText(".000Z",substring=true).assertCountEquals(0)
        compose.onNodeWithTag("limit-window-details").assertIsDisplayed()
        saveVisualEvidence(compose.activity,compose.onRoot(),"mobile053-quota-detail.png")
    }
}
