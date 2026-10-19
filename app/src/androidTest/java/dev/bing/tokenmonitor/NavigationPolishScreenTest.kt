package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Public fixture journeys in real emulator window, font and locale configurations. */
class NavigationPolishScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val args get()=InstrumentationRegistry.getArguments()
    private val dark get()=args.getString("visualDark")=="true"
    private val language get()=if(args.getString("visualLanguage")=="en") LanguageChoice.ENGLISH else LanguageChoice.CHINESE
    private fun save(kind:String)=saveVisualEvidence(compose.activity,compose.onRoot(),"polish-${args.getString("matrixCase") ?: "current"}-$kind.png")
    @Test fun recordAndAnalysisActionsShareOneFullWidthRowAndOpenSeparateContent() {
        val history=HubHistory(listOf(HistoryRow("2026-10-16",123,"1"),HistoryRow("2026-08-01",9,"0")),emptyList())
        val ui=DashboardUiState();var refreshes=0
        val prefs=DisplayPreferences(language=language,appearance=AppearancePreferences(reduceMotion="on"))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,language,prefs.appearance) {
            DesktopDashboardScreen(history,false,null,ui,null,LocalDate.of(2026,10,16),prefs,null,{}, {refreshes++},{})
        }}}
        compose.onNodeWithTag("dashboard-open-records").performScrollTo()
        val row=compose.onNodeWithTag("dashboard-detail-actions").getUnclippedBoundsInRoot()
        val a=compose.onNodeWithTag("dashboard-open-records").getUnclippedBoundsInRoot()
        val b=compose.onNodeWithTag("dashboard-open-analysis").getUnclippedBoundsInRoot()
        assertEquals(a.width.value,b.width.value,.5f);assertEquals(a.top.value,b.top.value,.5f)
        assertEquals(a.bottom.value,b.bottom.value,.5f);assertTrue(a.height.value>=47.5f)
        assertEquals(row.left.value,a.left.value,.5f);assertEquals(row.right.value,b.right.value,.5f)
        save("actions")
        for(tag in listOf("dashboard-open-records","dashboard-open-analysis")) {
            val label=compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)) and hasText("",substring=true),useUnmergedTree=true)
            label.assertCountEquals(1)
            val layouts=mutableListOf<TextLayoutResult>()
            label.onFirst().performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
            assertFalse("$tag: size=${layouts.single().size}, constraints=${layouts.single().layoutInput.constraints}, widthOverflow=${layouts.single().didOverflowWidth}, heightOverflow=${layouts.single().didOverflowHeight}",layouts.single().hasVisualOverflow)
        }
        save("actions")
        compose.onNodeWithTag("dashboard-open-records").performClick()
        compose.onNodeWithTag("records-day-grid").assertExists()
        compose.onNodeWithTag("records-analysis").assertDoesNotExist()
        compose.onNodeWithTag("records-analysis-toggle").assertDoesNotExist()
        val context=compose.activity
        listOf(R.string.range_7_days,R.string.range_30_days,R.string.range_90_days,R.string.range_365_days,R.string.range_all).forEach {id->
            val label=if(language==LanguageChoice.CHINESE) android.content.res.Configuration(context.resources.configuration).apply {setLocale(java.util.Locale.SIMPLIFIED_CHINESE)}.let {context.createConfigurationContext(it).getString(id)} else android.content.res.Configuration(context.resources.configuration).apply {setLocale(java.util.Locale.ENGLISH)}.let {context.createConfigurationContext(it).getString(id)}
            compose.onNodeWithText(label).assertDoesNotExist()
        }
        save("calendar")
        compose.onNodeWithTag("dashboard-records-back").performScrollTo().performClick()
        compose.onNodeWithTag("dashboard-open-analysis").performScrollTo().performClick()
        compose.onNodeWithTag("records-analysis").assertExists()
        compose.onNodeWithTag("records-day-grid").assertDoesNotExist()
        assertTrue(ui.recordsAnalysis)
        save("analysis")
        compose.onNodeWithTag("dashboard-records-back").performScrollTo().performClick()
        assertEquals(DashboardTab.OVERVIEW,ui.tab)
        assertEquals(0,refreshes)
    }
    @Test fun quotaListHidesOnlyUnusedCatalogRows() {
        val state=HubState(snapshot=HubParser.parse("""{"periods":{"today":{}},"limits":{"providers":[
            {"provider":"codex","status":"ok","windows":[{"kind":"session","remainingPercent":100}]},
            {"provider":"claude","status":"notConfigured","windows":[]},
            {"provider":"gemini","status":"disabled","windows":[]},
            {"provider":"grok","status":"noSyncedData","windows":[]},
            {"provider":"cursor","status":"unauthorized","windows":[]}]}}"""),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"),subscriptions=emptyList())
        val actions=object:HubUiActions {
            override fun connect(address:String,credential:String,alternate:String) {}
            override fun refreshSaved() {};override fun switchEndpoint() {};override fun hasAlternate()=false
            override fun endpointType()="https";override fun loadHistory() {};override fun loadModelNames() {}
            override fun loadSubscriptions() {};override fun forget() {};override fun renameDevice(id:String,input:String) {}
        }
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,language,AppearancePreferences(reduceMotion="on")) {
            HubScreen(state,actions,preferences=DisplayPreferences(language=language,appearance=AppearancePreferences(reduceMotion="on")))
        }}}
        compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-limits").performScrollTo().performClick()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("limit-account-0"))
        compose.onNodeWithTag("limit-account-0").assertExists()
        for(index in 1..3) compose.onNodeWithTag("limit-account-$index").assertDoesNotExist()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("limit-account-4"))
        compose.onNodeWithTag("limit-account-4").assertExists()
        save("limits")
    }
}
