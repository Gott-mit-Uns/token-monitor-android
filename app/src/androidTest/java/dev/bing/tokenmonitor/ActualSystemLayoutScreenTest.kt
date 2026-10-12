package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.json.JSONObject
import java.io.File

/** ROOT varies real Android display/font settings; no LocalDensity override. */
class ActualSystemLayoutScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private val pages=MainDisplay.ids.map {id->HubPage.entries.first {it.name.lowercase()==id}}
    private var observedFontScale=0f
    private class Actions:HubUiActions {
        var calls=0
        override fun connect(address:String,credential:String,alternate:String) {calls++}
        override fun refreshSaved() {calls++}
        override fun switchEndpoint() {calls++}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadHistory() {calls++}
        override fun loadModelNames() {calls++}
        override fun loadPricing() {calls++}
        override fun loadSubscriptions() {calls++}
        override fun loadDeviceHistory() {calls++}
        override fun forget() {calls++}
        override fun renameDevice(id:String,input:String) {calls++}
    }
    private fun record(kind:String) {
        val configuration=compose.activity.resources.configuration
        assertEquals("Real Compose density uses the actual system font scale",configuration.fontScale,observedFontScale,.001f)
        val report=JSONObject().put("kind",kind).put("screenWidthDp",configuration.screenWidthDp)
            .put("screenHeightDp",configuration.screenHeightDp).put("systemFontScale",configuration.fontScale)
            .put("composeFontScale",observedFontScale).put("density",compose.activity.resources.displayMetrics.density)
            .put("dark",dark).put("densityOverride",false).put("scope","Owned emulator actual system layout; no physical phone or timing claim")
        File(compose.activity.getExternalFilesDir(null),"actual-system-layout-$kind-public.json").writeText(report.toString(2))
    }
    private fun complete(tag:String,expected:String) {
        val node=compose.onNodeWithTag(tag,true);node.assertTextEquals(expected).assertIsDisplayed()
        val layouts=mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertEquals(1,layouts.size);val layout=layouts.single()
        assertFalse("$tag real system text must be fully painted",layout.hasVisualOverflow)
        repeat(layout.lineCount) {assertFalse(layout.isLineEllipsized(it))}
    }
    private fun inside(child:String,parent:String) {
        val c=compose.onNodeWithTag(child,true).getUnclippedBoundsInRoot()
        val p=compose.onNodeWithTag(parent,true).getUnclippedBoundsInRoot()
        assertTrue(c.left>=p.left-.5.dp && c.right<=p.right+.5.dp && c.top>=p.top-.5.dp && c.bottom<=p.bottom+.5.dp)
    }
    @Test fun realSystemNavigationKeepsAllEightEntriesTogether() {
        val actions=Actions()
        val state=HubState(snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":1000}}}"""),
            history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"))
        val prefs=mutableStateOf(DisplayPreferences(language=LanguageChoice.CHINESE,homeModules=emptyList(),appearance=AppearancePreferences(reduceMotion="on")))
        compose.activityRule.scenario.onActivity {activity->activity.setContent {
            observedFontScale=LocalDensity.current.fontScale
            TokenTheme(dark,prefs.value.language,prefs.value.appearance) {HubScreen(state,actions,preferences=prefs.value)}
        }}
        for(language in listOf(LanguageChoice.CHINESE,LanguageChoice.ENGLISH)) for(size in listOf("standard","largest")) {
            compose.runOnIdle {prefs.value=prefs.value.copy(language=language,appearance=prefs.value.appearance.copy(textSize=size))}
            compose.onNodeWithTag("hub-view-selector").performClick()
            assertNavigationMenuLayout(compose,pages,navigationWindowCanFitDefaultRows(compose.activity),compose.activity.resources.displayMetrics.density)
            saveVisualEvidence(compose.activity,compose.onNodeWithTag("hub-view-menu"),"actual-system-navigation-${language.name}-$size.png")
            compose.onNodeWithTag("view-home").performKeyInput {pressKey(Key.Escape)}
        }
        assertEquals(0,actions.calls);record("navigation")
    }
    @Test fun realSystemLanguageChoicesAndCurrentValuesRemainComplete() {
        val language=mutableStateOf(LanguageChoice.CHINESE)
        val selected=mutableStateOf(LanguageChoice.CHINESE.name)
        val size=mutableStateOf("standard")
        compose.activityRule.scenario.onActivity {activity->activity.setContent {
            observedFontScale=LocalDensity.current.fontScale
            TokenTheme(dark,language.value,AppearancePreferences(textSize=size.value,reduceMotion="on")) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
                    val strings=LocalResources.current
                    DesktopSettingSelect(strings.getString(R.string.language_label),listOf(
                        LanguageChoice.CHINESE.name to "中文",LanguageChoice.ENGLISH.name to "English",
                        LanguageChoice.SYSTEM.name to strings.getString(R.string.follow_system)),selected.value,"actual-language") {selected.value=it}
                }
            }
        }}
        for(display in listOf(LanguageChoice.CHINESE,LanguageChoice.ENGLISH)) for(textSize in listOf("standard","largest")) for(choice in LanguageChoice.entries) {
            compose.runOnIdle {language.value=display;size.value=textSize;selected.value=choice.name}
            val labels=listOf("中文","English",if(display==LanguageChoice.CHINESE) "跟随系统" else "System default")
            complete("actual-language-value",labels[choice.ordinal]);inside("actual-language-value","actual-language-select")
            compose.onNodeWithTag("actual-language-select").performClick()
            val menu=compose.onNodeWithTag("actual-language-menu",true)
            assertEquals(0f,menu.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue(),.01f)
            LanguageChoice.entries.forEach {option->
                complete("actual-language-${option.name}-label",labels[option.ordinal])
                inside("actual-language-${option.name}","actual-language-menu")
                inside("actual-language-${option.name}-label","actual-language-${option.name}")
                assertTrue(compose.onNodeWithTag("actual-language-${option.name}",true).getUnclippedBoundsInRoot().height>=47.5.dp)
            }
            if(choice==LanguageChoice.CHINESE) saveVisualEvidence(compose.activity,menu,"actual-system-language-${display.name}-$textSize.png")
            compose.onNodeWithTag("actual-language-${choice.name}").assertIsSelected().performClick()
            compose.runOnIdle {assertEquals(choice.name,selected.value)}
        }
        record("language")
    }
}
