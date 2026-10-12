package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference

/** Actual target pixels plus cancellation/publication boundaries for background atlas preparation. */
class DashboardPreparationScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    private fun model():DashboardTrendModel {
        val end=LocalDate.of(2026,10,11)
        val history=HubHistory(List(64) {day->
            val clients=(0 until 80).associate {key->
                "public-client-$key" to HistoryAttribution((day%9+1L)*(key+1L)*1000,null)
            }
            HistoryRow(end.minusDays(63L-day).toString(),clients.values.sumOf {it.tokens!!},null,perClient=clients)
        },emptyList())
        return DashboardTrendProjection.project(history,range="all",width=320.0,height=200.0)
    }
    private fun palette(rotate:Float)=desktopVendorColors+(0 until 80).associate {key->
        "public-client-$key" to (android.graphics.Color.HSVToColor(if(key==0) 184 else 255,
            floatArrayOf((key*4.5f+rotate)%360f,.78f,.85f)).toLong() and 0xffffffffL)
    }
    private fun pixels()=compose.onNodeWithTag("dashboard-chart").captureToImage().toPixelMap()
    private fun samePixels(expected:PixelMap,actual:PixelMap) {
        assertEquals(expected.width,actual.width);assertEquals(expected.height,actual.height)
        for(y in 0 until expected.height) for(x in 0 until expected.width)
            if(expected[x,y].toArgb()!=actual[x,y].toArgb())
                fail("Prepared pixels differ from the direct renderer at $x,$y")
    }

    @Test fun preparedTargetsPreservePixelsPaletteAndDiscardCancelledOrObsoleteWork() {
        val model=model()
        val firstPalette=palette(0f);val nextPalette=palette(45f)
        val palette=mutableStateOf(firstPalette)
        val prepared=mutableStateOf<DashboardPreparedChart?>(null)
        compose.activity.runOnUiThread {compose.activity.setContent {
            TokenTheme(true,LanguageChoice.ENGLISH) {
                CompositionLocalProvider(LocalDesktopVendorPalette provides palette.value) {
                    Surface(Modifier.fillMaxSize(),color=Color(0xFF0B0F14)) {
                        Box(Modifier.fillMaxSize()) {
                            DesktopDashboardChart(model,Modifier.width(320.dp).height(200.dp),{_,_->},{},prepared=prepared.value)
                        }
                    }
                }
            }
        }}
        val direct=pixels()
        val pixelSize=IntSize(direct.width,direct.height)
        val first=runBlocking {prepareDashboardChart(model,firstPalette,pixelSize)}
        assertNotNull("The 5,120-segment target must use the bounded dense atlas",first.atlas)
        assertSame(model,first.model)
        assertEquals(pixelSize,first.pixelSize)
        compose.runOnIdle {prepared.value=first;palette.value=nextPalette}
        samePixels(direct,pixels())

        // The next palette is published only with its completed target. A direct
        // renderer at the same pixel size remains the pixel-for-pixel reference.
        compose.runOnIdle {prepared.value=null}
        val nextDirect=pixels()
        assertNotEquals(first.colors,model.keys.associateWith {dashboardColor(it,false,nextPalette)})
        val next=runBlocking {prepareDashboardChart(model,nextPalette,pixelSize)}
        compose.runOnIdle {prepared.value=next}
        samePixels(nextDirect,pixels())

        val published=AtomicReference<DashboardPreparedChart?>()
        runBlocking {
            // UNDISPATCHED reaches the Default preparation, then returns to this
            // event loop. Cancellation precedes result dispatch/publication even
            // if the worker happens to finish before cancelAndJoin is entered.
            val job=launch(start=CoroutineStart.UNDISPATCHED) {
                published.set(prepareDashboardChart(model,firstPalette,pixelSize))
            }
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
        }
        assertNull("A superseded worker cannot publish prepared pixels",published.get())

        val ui=DashboardUiState().apply {selectTab(DashboardTab.TRENDS)}
        val oldRevision=ui.motionRevision
        ui.commitChart(model,"first",oldRevision,DashboardMotionReason.ENTRY,first)
        ui.selectRange("7")
        ui.commitChart(model,"obsolete",oldRevision,DashboardMotionReason.UPDATE,next)
        assertEquals("first",ui.committedChartEvent)
        assertSame("A late revision leaves the previous pixels/event together",first,ui.committedChartPreparation)
        ui.commitChart(model,"latest",ui.motionRevision,DashboardMotionReason.UPDATE,next)
        assertEquals("latest",ui.committedChartEvent)
        assertSame(next,ui.committedChartPreparation)
        assertSame(model,ui.committedChart)
        ui.discardChart()
        assertNull(ui.committedChartPreparation)
        assertNull(ui.committedChart)
    }
}
