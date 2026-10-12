package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

/** Actual overview text, touch callbacks and Canvas pixels, using only public histories. */
class DashboardOverviewScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private val today=LocalDate.of(2026,10,11)
    private fun value(n:Long)=HistoryAttribution(n,"0")
    private fun fixture()=HubHistory(listOf(
        HistoryRow("2026-10-11",1000,"0.000125",perClient=mapOf("antigravity" to value(200)),perModel=mapOf("G" to value(200))),
        HistoryRow("2025-10-26",1000,"0",perClient=linkedMapOf("antigravity-cli" to value(300),"openai" to value(180),"omp" to value(60),"kilocode" to value(40),"unknown tool" to value(20)),
            perModel=linkedMapOf("Z" to value(400),"A" to value(200),"B" to value(150),"E" to value(110),"F" to value(10)))) ,emptyList(),
        HistorySummary(12_345_678,"2500",400,9,10,3_570_000,1_200_000,"favorite<&\"",12_345,2000))
    private fun host(history:State<HubHistory>,cost:MutableState<Boolean>,width:State<Dp> = mutableStateOf(360.dp),
        currency:State<UsageCurrency> = mutableStateOf(UsageCurrency()),display:State<MainDisplayPreferences> = mutableStateOf(MainDisplayPreferences()),
        appearance:State<AppearancePreferences> = mutableStateOf(AppearancePreferences(interfaceFont=DesktopAppearance.systemFont,reduceMotion="on")),
        fontScale:State<Float> = mutableStateOf(1f),select:(HistoryRow)->Unit = {}) {
        compose.activity.runOnUiThread {compose.activity.setContent {
            TokenTheme(dark,LanguageChoice.CHINESE,appearance.value) {
                val density=LocalDensity.current
                CompositionLocalProvider(LocalUsageCurrency provides currency.value,LocalMainDisplay provides display.value,
                    LocalDensity provides Density(density.density,fontScale.value)) {
                    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                            Column(Modifier.width(width.value).fillMaxHeight().verticalScroll(rememberScrollState()).padding(14.dp)) {
                                DesktopDashboardOverview(history.value,today,cost.value,{cost.value=it},select)
                            }
                        }
                    }
                }
            }
        }}
    }
    private fun card(key:String)=compose.onNodeWithTag("dashboard-card-$key-value",useUnmergedTree=true)
    private fun bar(key:String,group:String="models")=compose.onNodeWithTag("dashboard-ranking-$group-$key-bar",useUnmergedTree=true)
    private fun save(name:String)=saveVisualEvidence(compose.activity,compose.onRoot(),"dashboard-overview-$name-${if(dark) "dark" else "light"}.png")

    @Test fun lifetimeCardsAndAllRetainedTopFiveUseSeparateDenominators() {
        host(mutableStateOf(fixture()),mutableStateOf(true))
        listOf("tokens" to "12.3M","cost" to "$2.5K","active-days" to "400","streak" to "9","time" to "1h 0m",
            "peak" to "1.2M","favorite" to "favorite<&\"","messages" to "12.3K").forEach {(id,text)->card(id).assertTextEquals(text)}
        compose.onNodeWithTag("dashboard-card-cost").performClick()
        compose.onNodeWithTag("dashboard-summary-unpriced").assertTextEquals("其中 2,000 Token 尚未定价")
        compose.onNodeWithTag("dashboard-ranking-models-Z-percent",useUnmergedTree=true).assertTextEquals("20.0%")
        compose.onNodeWithTag("dashboard-ranking-tools-antigravity-percent",useUnmergedTree=true).assertTextEquals("25.0%")
        compose.onNodeWithTag("dashboard-ranking-tools-antigravity-cli").assertDoesNotExist()
        compose.onNodeWithTag("dashboard-ranking-models-F").assertDoesNotExist()
        val z=bar("Z").performScrollTo().captureToImage().toPixelMap()
        val a=bar("A").performScrollTo().captureToImage().toPixelMap()
        fun fillEnd(image:androidx.compose.ui.graphics.PixelMap):Int {
            val y=image.height/2
            val track=image[image.width-2,y]
            return (0 until image.width).lastOrNull {x->
                val c=image[x,y];abs(c.red-track.red)+abs(c.green-track.green)+abs(c.blue-track.blue)>.04f
            } ?: image.width-1
        }
        // A's source max share is .5 even though its grand-total share is 10%.
        assertTrue("Half-height ranking uses column maximum",fillEnd(a) in (a.width*.45).toInt()..(a.width*.55).toInt())
        assertTrue("Maximum row paints the full track",z.width>0 && z.height>0)
        compose.onNodeWithTag("dashboard-summary").performScrollTo()
        save("lifetime")
    }

    @Test fun metricRepaintsActualCellsAndDaySelectionPreservesRawTinyCost() {
        val cost=mutableStateOf(true)
        var selected:HistoryRow?=null
        host(mutableStateOf(fixture()),cost,select={selected=it})
        val heat=compose.onNodeWithTag("dashboard-heatmap")
        val before=heat.performScrollTo().captureToImage().toPixelMap()
        val density=compose.activity.resources.displayMetrics.density
        val point=(4*density).toInt()
        val neutral=before[point,point]
        compose.onNodeWithTag("dashboard-heatmap-metric-tokens").performScrollTo().performClick()
        assertFalse(cost.value)
        val after=heat.performScrollTo().captureToImage().toPixelMap()
        val token=after[point,point]
        assertTrue("Metric control must change the first actual cell",abs(neutral.red-token.red)+abs(neutral.green-token.green)+abs(neutral.blue-token.blue)>.1f)
        heat.performSemanticsAction(SemanticsActions.OnClick) {assertTrue(it())} // Accessible default action selects local today.
        assertEquals("2026-10-11",selected?.date)
        assertEquals("0.000125",selected?.cost)
        // A physical touch at the first Sunday reads that original row instead.
        heat.performTouchInput {click(Offset(4*density,4*density))}
        assertEquals("2025-10-26",selected?.date)
        assertEquals("0",selected?.cost)
        compose.onNodeWithTag("dashboard-heatmap-metric-cost").performScrollTo().performClick()
        assertTrue(cost.value)
        compose.onNodeWithTag("dashboard-summary").performScrollTo()
        save("metric")
    }

    @Test fun summaryUnknownFreePartialAndCurrencyChangesRemainVisible() {
        val history=mutableStateOf(fixture())
        val currency=mutableStateOf(UsageCurrency())
        val display=mutableStateOf(MainDisplayPreferences())
        host(history,mutableStateOf(true),currency=currency,display=display)
        compose.runOnIdle {history.value=history.value.copy(summary=history.value.summary!!.copy(totalCost="0",unpricedTokens=2000))}
        card("cost").assertTextEquals("—")
        compose.onNodeWithTag("dashboard-card-cost").performClick()
        compose.onNodeWithTag("dashboard-summary-unpriced").assertIsDisplayed()
        save("unpriced")
        compose.runOnIdle {history.value=history.value.copy(summary=history.value.summary!!.copy(totalCost="0.125"))}
        card("cost").assertTextEquals("$0.1250")
        compose.runOnIdle {
            currency.value=UsageCurrency("CNY",mapOf("CNY" to "7.25"))
            display.value=MainDisplayPreferences(compactUnits="localized")
        }
        card("cost").assertTextEquals("¥0.9063")
        compose.runOnIdle {history.value=history.value.copy(summary=history.value.summary!!.copy(totalCost="2000"))}
        card("cost").assertTextEquals("¥1.45万")
        compose.runOnIdle {history.value=history.value.copy(summary=history.value.summary!!.copy(totalCost="0",unpricedTokens=0))}
        card("cost").assertTextEquals("¥0.0000")
        compose.onNodeWithTag("dashboard-summary-unpriced").assertDoesNotExist()
        compose.runOnIdle {history.value=history.value.copy(summary=null)}
        listOf("tokens","cost","active-days","streak","time","peak","favorite","messages").forEach {card(it).assertTextEquals("—")}
        save("unknown")
    }

    @Test fun summaryAdaptsTwoFourEightColumnsAndNarrowLargeFontKeepsBounds() {
        val width=mutableStateOf(320.dp)
        val appearance=mutableStateOf(AppearancePreferences(interfaceFont=DesktopAppearance.systemFont,reduceMotion="on"))
        val fontScale=mutableStateOf(1f)
        val short=fixture().copy(summary=HistorySummary(1,"0",1,1,1,30_000,1,"A",1))
        host(mutableStateOf(short),mutableStateOf(true),width,appearance=appearance,fontScale=fontScale)
        fun actualBounds(tag:String,unmerged:Boolean=false):Rect {
            val node=compose.onNodeWithTag(tag,useUnmergedTree=unmerged).fetchSemanticsNode()
            // The simulated wide viewport scrolls horizontally. Clipped bounds are
            // empty for off-screen columns; inspect their actual placed geometry.
            return Rect(node.positionInRoot,Size(node.size.width.toFloat(),node.size.height.toFloat()))
        }
        fun bounds(id:String)=actualBounds("dashboard-card-$id")
        assertEquals(bounds("tokens").top,bounds("cost").top,1f)
        assertTrue(bounds("active-days").top>bounds("tokens").top)
        compose.runOnIdle {width.value=720.dp}
        assertEquals(bounds("tokens").top,bounds("streak").top,1f)
        assertTrue(bounds("time").top>bounds("tokens").top)
        compose.runOnIdle {width.value=1800.dp}
        assertEquals(bounds("tokens").top,bounds("messages").top,1f)
        compose.runOnIdle {width.value=320.dp;appearance.value=appearance.value.copy(textSize="largest");fontScale.value=1.4f}
        val summary=actualBounds("dashboard-summary")
        listOf("tokens","cost","active-days","streak","time","peak","favorite","messages").forEach {
            val card=bounds(it)
            assertTrue("$it stays in the responsive strip",card.left>=summary.left-.5 && card.right<=summary.right+.5)
        }
        val value=actualBounds("dashboard-ranking-models-Z-value",true)
        val percent=actualBounds("dashboard-ranking-models-Z-percent",true)
        assertTrue("Large-font value and percent do not overlap",value.right<=percent.left+.5)
        compose.onNodeWithTag("dashboard-summary").performScrollTo()
        save("narrow-large-font")
    }

    @Test fun emptyDailyAndMonthlyOnlyKeepNeutralYearAndNoInventedLifetime() {
        val history=mutableStateOf(HubHistory(emptyList(),listOf(HistoryRow("2026-10",9000,"2"))))
        var selected:HistoryRow?=null
        host(history,mutableStateOf(true),select={selected=it})
        compose.onNodeWithTag("dashboard-heatmap").performScrollTo().assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick) {assertTrue(it())}
        assertEquals(HistoryRow(today.toString(),null,null),selected)
        compose.onNodeWithTag("dashboard-ranking-models").assertDoesNotExist()
        compose.onNodeWithTag("dashboard-ranking-tools").assertDoesNotExist()
        card("tokens").assertTextEquals("—")
        compose.runOnIdle {history.value=HubHistory(emptyList(),emptyList())}
        compose.onNodeWithTag("dashboard-heatmap").assertExists()
        compose.onNodeWithTag("dashboard-summary").performScrollTo()
        save("monthly-only")
    }
}
