package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Public fixtures under externally configured real system font scale and window size. */
class ScopedUiQualityScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val args get()=InstrumentationRegistry.getArguments()
    private val dark get()=args.getString("visualDark")=="true"
    private val language get()=if(args.getString("visualLanguage")=="en") LanguageChoice.ENGLISH else LanguageChoice.CHINESE
    private val label get()=args.getString("evidenceLabel") ?: "scope"
    private class Actions : HubUiActions {
        var reads=0
        override fun connect(address:String,credential:String,alternate:String) {reads++}
        override fun refreshSaved() {reads++}
        override fun switchEndpoint() {reads++}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadHistory() {reads++}
        override fun loadModelNames() {reads++}
        override fun loadSubscriptions() {reads++}
        override fun forget() {}
        override fun renameDevice(id:String,input:String) {}
    }
    private fun complete(tag:String):TextLayoutResult {
        val layouts=mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag,true).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        return layouts.single().also {result->assertFalse("$tag overflows: size=${result.size} width=${result.didOverflowWidth} height=${result.didOverflowHeight} text=${result.layoutInput.text} style=${result.layoutInput.style}",result.hasVisualOverflow);repeat(result.lineCount) {assertFalse(result.isLineEllipsized(it))}}
    }
    private fun save(name:String,node:SemanticsNodeInteraction=compose.onRoot())=saveVisualEvidence(compose.activity,node,"quality-$label-$name.png")
    @Test fun headlineFontFamiliesStayInlineAtStandardSizeAndCompleteAtLargeSize() {
        val appearance=mutableStateOf(AppearancePreferences(reduceMotion="on",textSize=args.getString("appTextSize") ?: "standard"));val actions=Actions()
        val snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":199470859,"costUsd":32.78,"components":{"input":100000000,"output":99470859}}}}""")
        val state=HubState(snapshot=snapshot,history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"))
        var systemScale=0f
        compose.activity.runOnUiThread {compose.activity.setContent {
            systemScale=LocalDensity.current.fontScale
            val prefs=DisplayPreferences(language=language,homeModules=emptyList(),main=MainDisplayPreferences(compactTotal=true,compactUnits="localized"),appearance=appearance.value)
            TokenTheme(dark,language,appearance.value) {HubScreen(state,actions,preferences=prefs)}
        }}
        val separated=mutableListOf<String>()
        for(font in listOf("sans-serif","monospace","serif","sans-serif-condensed")) {
            compose.runOnIdle {appearance.value=appearance.value.copy(displayFont=font)}
            compose.onNodeWithTag("hub-total-tokens").assertTextEquals("199,470,859")
            save("headline-$font");complete("hub-total-tokens");complete("hub-total-compact")
            if(systemScale<=1.01f && appearance.value.textSize=="standard") {
                val number=compose.onNodeWithTag("hub-total-tokens").getUnclippedBoundsInRoot()
                val compact=compose.onNodeWithTag("hub-total-compact").getUnclippedBoundsInRoot()
                if(compact.top>=number.bottom) separated.add(font)
                assertEquals("Exact number stays on one line",1,complete("hub-total-tokens").lineCount)
            }
        }
        assertTrue("Standard-size number and approximation separated for $separated",separated.isEmpty())
        compose.onNodeWithTag("hub-total-tokens").performClick()
        compose.onNodeWithTag("hub-token-components").assertIsDisplayed()
        save("token-dialog",compose.onNodeWithTag("hub-token-components"))
        compose.onNodeWithText(if(language==LanguageChoice.CHINESE) "关闭" else "Close").performClick()
        compose.onNodeWithTag("hub-token-components").assertDoesNotExist();assertEquals(0,actions.reads)
    }
    @Test fun upwardAndDownwardMenusKeepCompactSpacingTouchTargetsAndSelection() {
        val expanded=mutableStateOf(true);val above=mutableStateOf(false);val selected=mutableStateOf("a")
        val labels=if(language==LanguageChoice.CHINESE) listOf("跟随系统","浅色","深色") else listOf("System default","Light","Dark")
        compose.activity.runOnUiThread {compose.activity.setContent {
            TokenTheme(dark,language,AppearancePreferences(reduceMotion="on")) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                    Box(Modifier.align(if(above.value) Alignment.BottomStart else Alignment.TopStart).size(48.dp)) {
                        DesktopOptionMenu(expanded.value,labels.mapIndexed {i,text->DesktopMenuOption(('a'+i).toString(),text,"quality-option-$i")},selected.value,"quality-menu",above=above.value,maxHeight=160.dp,dismiss={expanded.value=false}) {selected.value=it;expanded.value=false}
                    }
                }
            }
        }}
        for(direction in listOf(false,true)) {
            compose.runOnIdle {above.value=direction;expanded.value=true}
            compose.onNodeWithTag("quality-option-2").performScrollTo().assertIsDisplayed()
            save("menu-${if(direction) "up" else "down"}",compose.onNodeWithTag("quality-menu"))
            for(i in 0..2) {
                val row=compose.onNodeWithTag("quality-option-$i").getUnclippedBoundsInRoot()
                assertTrue(row.height>=48.dp);complete("quality-option-$i-label")
                if(i>0) {val previous=compose.onNodeWithTag("quality-option-${i-1}").getUnclippedBoundsInRoot();assertTrue("No extra gap between touch rows",row.top-previous.bottom<=.5.dp)}
            }
            compose.onNodeWithTag("quality-option-1").performScrollTo().performClick();assertEquals("b",selected.value)
            compose.onNodeWithTag("quality-menu").assertDoesNotExist()
            compose.runOnIdle {expanded.value=true}
            compose.onNodeWithTag("quality-option-1").assertIsSelected().performKeyInput {pressKey(Key.Escape)}
            compose.onNodeWithTag("quality-menu").assertDoesNotExist()
        }
    }
    @Test fun aboutVersionExplainsMissingServerMetadataWithoutInventingVersion() {
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,language) {Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(16.dp)) {AboutSettings()}}}}
        save("about-pending");complete("about-hub-version");complete("about-android-version");complete("about-desktop-reference")
        compose.onNodeWithTag("about-hub-version").assertTextEquals(if(language==LanguageChoice.CHINESE) "Hub 构建：等待连接验证" else "Hub build: waiting for connection verification")
        val core=HubBuildRegistry.entries.getValue("core").getValue(74L)
        val adapter=HubBuildRegistry.entries.getValue("node-hub").getValue(5L)
        val info=HubBuildInfo("node-hub",1,74,core,5,adapter)
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,language) {Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(16.dp)) {AboutSettings(info)}}}}
        complete("about-hub-version");complete("about-hub-build-status");save("about-build")
        compose.onNodeWithTag("about-hub-version").assertTextEquals(if(language==LanguageChoice.CHINESE) "Hub 构建：Node Hub · 核心 74 · 运行时 5" else "Hub build: Node Hub · core 74 · runtime 5")
        @Suppress("DEPRECATION") val packageInfo=compose.activity.packageManager.getPackageInfo(compose.activity.packageName,0)
        compose.onNodeWithTag("about-android-version").assertTextContains(packageInfo.versionName!!,substring=true)
    }
}
