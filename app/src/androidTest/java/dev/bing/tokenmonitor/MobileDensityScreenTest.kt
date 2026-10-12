package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.platform.app.InstrumentationRegistry
import java.math.BigInteger
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real layout assertions for the user's compact mobile presentation; public fixtures only. */
class MobileDensityScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private val label get()=InstrumentationRegistry.getArguments().getString("evidenceLabel") ?: "standard"
    private fun assertMinimumTarget(tag:String) {
        val node=compose.onNodeWithTag(tag).fetchSemanticsNode()
        val required=(44f*compose.activity.resources.displayMetrics.density).roundToInt()
        assertTrue("$tag retains the 44dp minimum in real pixels: ${node.size.height} >= $required",node.size.height>=required)
    }
    private class Actions:HubUiActions {
        override fun connect(address:String,credential:String,alternate:String) {}
        override fun refreshSaved() {}
        override fun switchEndpoint() {}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadHistory() {}
        override fun loadModelNames() {}
        override fun loadSubscriptions() {}
        override fun forget() {}
        override fun renameDevice(id:String,input:String) {}
    }
    private fun layout(node:SemanticsNodeInteraction):TextLayoutResult {
        val out=mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(out)}
        return out.single().also {assertFalse("No clipped text: ${it.layoutInput.text}, style=${it.layoutInput.style}, size=${it.size}, width=${it.didOverflowWidth}, height=${it.didOverflowHeight}",it.hasVisualOverflow)}
    }
    private fun save(name:String)=saveVisualEvidence(compose.activity,if(name=="navigation") compose.onNodeWithTag("hub-view-menu") else compose.onRoot(),"density-$label-$name.png")
    private val tokens get()=InstrumentationRegistry.getArguments().getString("headlineTokens")?.toLong() ?: 282895038L
    private fun state()=HubState(snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":$tokens,"costUsd":46.79,"clients":{"codex":$tokens},"models":{"gpt-5.5":$tokens}}}}"""),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"))
    @Test fun compactNavigationFooterAndHeadlineStayCompleteAndBaselineAligned() {
        var density=1f;var scale=1f
        compose.activity.runOnUiThread {compose.activity.setContent {
            density=LocalDensity.current.density;scale=LocalDensity.current.fontScale
            val appearance=AppearancePreferences(reduceMotion="on")
            TokenTheme(dark,LanguageChoice.CHINESE,appearance) {HubScreen(state(),Actions(),preferences=DisplayPreferences(appearance=appearance,main=MainDisplayPreferences(compactTotal=true,compactUnits="localized")))}
        }}
        val exact=compose.onNodeWithTag("hub-total-tokens");val compact=compose.onNodeWithTag("hub-total-compact")
        exact.assertTextEquals(if(tokens==2404681706L) "2,404,681,706" else "282,895,038");compact.assertTextEquals(if(tokens==2404681706L) "≈ 24亿" else "≈ 2.83亿")
        val exactLayout=layout(exact);val compactLayout=layout(compact)
        assertEquals(15f,compactLayout.layoutInput.style.fontSize.value,.01f)
        assertTrue(exactLayout.layoutInput.style.fontSize.value>=24f)
        val a=exact.getUnclippedBoundsInRoot();val b=compact.getUnclippedBoundsInRoot()
        if(scale<=1.01f) {
            // Bounds above are DpRect, so compare in their own units below.
            val exactBaseline=a.top.value+exactLayout.firstBaseline/density+(a.height.value-exactLayout.size.height/density)/2f
            val compactBaseline=b.top.value+compactLayout.firstBaseline/density+(b.height.value-compactLayout.size.height/density)/2f
            assertEquals("The exact value and suffix share a text baseline",exactBaseline,compactBaseline,1f/density)
            assertTrue("Same-row suffix",b.left>=a.right)
        }
        val refresh=compose.onNodeWithTag("hub-refresh").getUnclippedBoundsInRoot()
        val settings=compose.onNodeWithTag("hub-settings-toggle").getUnclippedBoundsInRoot()
        assertTrue(refresh.width>=48.dp && settings.width>=48.dp)
        assertTrue(settings.left>=refresh.right)
        assertTrue("No extra space between the two touch targets",settings.left-refresh.right<=.5.dp)
        save("home")
        compose.onNodeWithTag("hub-view-selector").performClick()
        val ids=listOf("home","limits","tools","models","projects","sessions","devices","trends")
        for(id in ids) {
            compose.onNodeWithTag("view-$id").performScrollTo().assertIsDisplayed()
            val bounds=compose.onNodeWithTag("view-$id").getUnclippedBoundsInRoot()
            assertTrue(bounds.height>=36.dp)
            if(scale<=1.01f) assertTrue("Navigation is visibly denser than the old 48dp row",bounds.height<=37.dp)
            layout(compose.onNodeWithTag("view-$id-label",true))
        }
        save("navigation")
        compose.onNodeWithTag("view-home").performClick()
        exact.assertTextEquals(if(tokens==2404681706L) "2,404,681,706" else "282,895,038")
    }
    @Test fun settingsShareTypographyAndCompactControlsWithoutLosingToolActions() {
        var systemScale=1f
        val prefs=mutableStateOf(DisplayPreferences(appearance=AppearancePreferences(reduceMotion="on")))
        compose.activity.runOnUiThread {compose.activity.setContent {systemScale=LocalDensity.current.fontScale;TokenTheme(dark,LanguageChoice.CHINESE,prefs.value.appearance) {HubScreen(state(),Actions(),preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}
        fun show(tag:String) {compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))}
        compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"general")
        show("settings-language-row")
        val language=layout(compose.onNodeWithTag("settings-language-title",true)).layoutInput.style.fontSize
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("Hub 用量"))
        assertEquals(language,layout(compose.onNodeWithText("Hub 用量",useUnmergedTree=true)).layoutInput.style.fontSize)
        show("about-android-version")
        assertEquals(language,layout(compose.onNodeWithTag("about-android-version",true)).layoutInput.style.fontSize)
        save("general")
        openSettingsSection(compose,"main");show("main-view-home-settings")
        val target=compose.onNodeWithTag("main-view-home-settings").getUnclippedBoundsInRoot()
        assertMinimumTarget("main-view-home-settings");if(systemScale<=1.01f) assertTrue("Compact normal-size row: $target",target.height<=45.dp);save("main")
        openSettingsSection(compose,"appearance");show("main-compact-total")
        val row=compose.onNodeWithTag("main-compact-total").getUnclippedBoundsInRoot()
        assertMinimumTarget("main-compact-total");if(systemScale<=1.01f) assertTrue("Compact normal-size switch row: $row",row.height<=45.dp);save("appearance")
        openSettingsSection(compose,"tools");show("tool-setting-codex")
        val tool=compose.onNodeWithTag("tool-setting-codex").getUnclippedBoundsInRoot()
        assertMinimumTarget("tool-setting-codex");if(systemScale<=1.01f) assertTrue("Compact normal-size tool row: $tool",tool.height<=45.dp)
        compose.onNodeWithTag("tool-visible-codex").assertIsDisplayed()
        compose.onNodeWithTag("tool-pin-codex").assertIsDisplayed()
        compose.onNodeWithTag("tool-order-codex").assertIsDisplayed();save("tools")
    }
    @Test fun homeListAndHeadingIconsRespondImmediatelyToAppFontSize() {
        val appearance=mutableStateOf(AppearancePreferences(reduceMotion="on"))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,LanguageChoice.CHINESE,appearance.value) {
            Column(Modifier.fillMaxWidth()) {
                HomeUsageRow(HomeUsage("codex","Codex",BigInteger.valueOf(1000),.5),false)
                HomeUsageRow(HomeUsage("gpt-5.5","GPT",BigInteger.valueOf(1000),.5),true)
                HomeDeviceLine(HomeDeviceRow("demo","Demo Desktop",1000,"win32",false))
                DesktopHomeModule("工具",R.drawable.desktop_view_tool,open={}) { }
            }
        }}}
        val icons=listOf(
            hasTestTag("vendor-icon-codex") and hasAnyAncestor(hasTestTag("home-tool-codex")),
            hasTestTag("vendor-icon-codex") and hasAnyAncestor(hasTestTag("home-model-gpt-5.5")),
            hasTestTag("device-icon-home-demo"),hasTestTag("home-heading-icon-${R.drawable.desktop_view_tool}")
        )
        fun widths()=icons.map {compose.onNode(it,true).getUnclippedBoundsInRoot().width.value}
        val normal=widths();println("Measured standard icon widths dp: $normal");save("icons-standard")
        compose.runOnIdle {appearance.value=appearance.value.copy(textSize="largest")}
        val large=widths();println("Measured largest icon widths dp: $large")
        for(i in normal.indices) assertTrue("All icons grow beyond pixel rounding; index=$i normal=$normal large=$large",large[i]>normal[i]+.5f)
        java.io.File(compose.activity.getExternalFilesDir(null),"density-$label-icon-metrics.txt").writeText("standard=$normal\nlargest=$large\n")
        save("icons-largest")
        compose.runOnIdle {appearance.value=appearance.value.copy(textSize="standard")}
        val restored=widths();for(i in normal.indices) assertEquals(normal[i],restored[i],.01f)
    }
}
