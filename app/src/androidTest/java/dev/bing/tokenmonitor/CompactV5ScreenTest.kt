package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.compose.ui.unit.height
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.math.BigInteger
import java.time.LocalDate

/** Same public fixture and actual Android window for both sides of the V5 comparison. */
class CompactV5ScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val args get() = InstrumentationRegistry.getArguments()
    private val baseline get() = args.getString("v5Baseline") == "true"
    private val language get() = if (args.getString("v5English") == "true") LanguageChoice.ENGLISH else LanguageChoice.CHINESE
    private val dark get() = args.getString("visualDark") != "false"
    private val appearance get() = AppearancePreferences(reduceMotion="on",textSize=if(args.getString("v5Largest")=="true") "largest" else "standard")
    private val today = LocalDate.of(2026, 10, 9)
    private val history = HubHistory(List(30) { i ->
        HistoryRow(today.minusDays((29-i).toLong()).toString(), (i%7+1)*1000L, "1",
            perClient = linkedMapOf("codex" to HistoryAttribution((i%7+1)*700L, ".7"),
                "claude" to HistoryAttribution((i%7+1)*300L, ".3")))
    }, listOf(HistoryRow("2026-10", 100000L, "30")),
        HistorySummary(100000L, "30", 30, 7, 30, 3600000, 7000, "public-model", 120))

    private fun record(scene: String, tags: List<String>) {
        compose.waitForIdle()
        val rows = tags.map { tag ->
            val b = compose.onNodeWithTag(tag, useUnmergedTree=true).getUnclippedBoundsInRoot()
            "$scene\t$tag\t${b.width.value}\t${b.height.value}"
        }
        File(compose.activity.getExternalFilesDir(null), "v5-layout.tsv").appendText(rows.joinToString("\n", postfix="\n"))
        saveVisualEvidence(compose.activity, compose.onRoot(), "v5-$scene.png")
    }
    private fun completeText(tag: String) {
        val layouts=mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertTrue("$tag has real text layout", layouts.isNotEmpty())
        assertTrue("$tag remains complete", layouts.all {!it.didOverflowWidth && !it.didOverflowHeight})
    }
    private fun completeAxes() {
        if(baseline) return
        val node=compose.onNodeWithTag("dashboard-chart").fetchSemanticsNode()
        val bounds=node.config[DashboardAxisBounds]
        assertTrue("Five y ticks and at least two dates",bounds.size>=7)
        bounds.forEach {
            assertTrue("Native text stays inside chart pixels",it.left>=0 && it.top>=0 && it.right<=node.size.width+.5f && it.bottom<=node.size.height+.5f)
        }
        bounds.take(5).zipWithNext().forEach {(a,b)-> assertTrue("Y labels do not overlap",b.bottom<=a.top)}
        bounds.drop(5).zipWithNext().forEach {(a,b)-> assertTrue("Date labels do not overlap",a.right<=b.left)}
        bounds.take(5).forEach {tick-> bounds.drop(5).forEach {date->
            assertFalse("Numeric and date axes stay distinct",tick.overlaps(date))
        }}
        val dates=node.config[SemanticsProperties.StateDescription]
        assertTrue("First and last raw dates remain on axis",dates.startsWith("9/10") && dates.endsWith("10/9"))
    }

    @Test fun mainPreviewDoesNotGrowWithTallViewportAndCardsRemainScrollable() {
        val available=mutableStateOf(600.dp)
        val model=mutableStateOf(TrendsMainModel(List(7) {TrendsMainPoint(today.minusDays((6-it).toLong()).toString(), if(it==2) 0 else (it+1)*1000L)},
            TrendsMainRange.WEEK, TrendsMainStats(BigInteger.valueOf(30), BigInteger.valueOf(7), BigInteger.valueOf(3600000), BigInteger.valueOf(7000)), true))
        var opens=0
        compose.activity.runOnUiThread {compose.activity.setContent {
            TokenTheme(dark, language, appearance) {Surface {
                Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(12.dp)) {
                    DesktopTrendsMain(model.value, {opens++}, availableHeight=available.value)
                }
            }}
        }}
        record("main-tall", listOf("trends-main", "trends-main-chart", "trends-main-active-days-card"))
        val tall=compose.onNodeWithTag("trends-main-chart").getUnclippedBoundsInRoot().height
        compose.runOnIdle {available.value=1000.dp}
        val taller=compose.onNodeWithTag("trends-main-chart").getUnclippedBoundsInRoot().height
        if(!baseline) assertEquals("Tall viewport does not stretch plot", tall.value, taller.value, .5f)
        compose.runOnIdle {available.value=160.dp}
        listOf("active-days", "streak", "time", "peak").forEach { id ->
            compose.onNodeWithTag("trends-main-$id").performScrollTo().assertIsDisplayed()
            completeText("trends-main-$id"); completeText("trends-main-$id-label")
        }
        completeText("trends-main-axis-start"); completeText("trends-main-axis-end")
        compose.onNodeWithTag("trends-main-open-history").performScrollTo().performClick()
        compose.onNodeWithTag("trends-main-chart").performScrollTo().performClick()
        assertEquals(2, opens)
        compose.runOnIdle {model.value=model.value.copy(points=emptyList())}
        compose.onNodeWithTag("trends-main-chart").assertDoesNotExist()
    }

    @Test fun dashboardModesOverviewAndRecordChartsKeepFullData() {
        val ui=DashboardUiState().apply {selectTab(DashboardTab.TRENDS)}
        val preferences=DisplayPreferences(language=language,appearance=appearance)
        compose.activity.runOnUiThread {compose.activity.setContent {
            TokenTheme(dark, language, preferences.appearance) {
                val model=remember(ui.range,ui.byModel,ui.mode,ui.chartWidth,ui.chartHeight) {
                    DashboardTrendProjection.project(history,ui.range,ui.byModel,ui.mode,ui.chartWidth,ui.chartHeight)
                }
                DesktopDashboardScreen(history,false,null,ui,model,today,preferences,null,{}, {}, {})
            }
        }}
        compose.waitUntil(10000) {ui.committedChart!=null}
        assertEquals(30, ui.committedChart!!.daily.size)
        val rangeBounds=compose.onNodeWithTag("dashboard-range-30").getUnclippedBoundsInRoot()
        assertTrue("Range keeps 48dp touch target",rangeBounds.height.value>=47.5f && rangeBounds.width.value>=47.5f)
        assertEquals(5,ui.committedChart!!.axisTicks.size)
        record("dashboard-bars",listOf("dashboard-chart","dashboard-legend"))
        completeAxes()
        compose.onNodeWithTag("dashboard-mode-kline").performScrollTo().performClick()
        compose.waitUntil(10000) {ui.committedChart?.mode==DashboardTrendMode.KLINE}
        assertEquals(30,ui.committedChart!!.daily.size)
        record("dashboard-kline",listOf("dashboard-chart"))
        completeAxes()
        compose.onNodeWithTag("dashboard-tab-overview").performClick()
        record("overview",listOf("dashboard-card-tokens","dashboard-card-active-days"))
        if(!baseline) listOf("tokens","cost","active-days","streak","time","peak","favorite","messages").forEach {
            completeText("dashboard-card-$it-value"); completeText("dashboard-card-$it-label")
        }
        compose.onNodeWithTag("dashboard-open-records").performScrollTo().performClick()
        compose.onNodeWithTag("desktop-history-chart").performScrollTo()
        record("record-bars",listOf("desktop-history-chart"))
        val kline=if(language==LanguageChoice.CHINESE) "K 线" else "K-line"
        val line=if(language==LanguageChoice.CHINESE) "折线" else "Line"
        compose.onNodeWithText(kline).performScrollTo().performClick()
        record("record-kline",listOf("desktop-history-chart"))
        compose.onNodeWithText(line).performScrollTo().performClick()
        record("record-line",listOf("desktop-history-chart"))
        val row=compose.onNode(hasText(today.toString()) and hasClickAction()).performScrollTo()
        val bounds=row.getUnclippedBoundsInRoot()
        File(compose.activity.getExternalFilesDir(null),"v5-layout.tsv").appendText("record-row\ttoday\t${bounds.width.value}\t${bounds.height.value}\n")
        assertTrue("Record action retains 48dp",bounds.height.value>=47.5f)
        saveVisualEvidence(compose.activity,compose.onRoot(),"v5-record-row.png")
        row.performClick()
        compose.onNodeWithText("Token 2,000").assertExists()
    }
}
