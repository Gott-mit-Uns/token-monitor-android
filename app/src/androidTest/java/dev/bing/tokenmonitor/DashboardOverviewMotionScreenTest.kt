package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** Same-domain virtual clock plus actual Canvas pixels; never a physical FPS measurement. */
class DashboardOverviewMotionScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private val today=LocalDate.of(2026,10,11)
    private val animationClock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
    private fun fixture(a:Long=100,b:Long=50)=HubHistory(listOf(HistoryRow("2025-10-26",200,"1",
        perModel=linkedMapOf("A" to HistoryAttribution(a,"1"),"B" to HistoryAttribution(b,"0")))),emptyList(),
        HistorySummary(10000,"1",1,1,1,60000,200,"A",1))
    private val commits=AtomicInteger()
    private var motion:DashboardOverviewMotionState?=null
    private class Owner:LifecycleOwner {
        val registry=LifecycleRegistry.createUnsafe(this).apply {currentState=Lifecycle.State.RESUMED}
        override val lifecycle:Lifecycle get()=registry
    }
    private fun mount(history:State<HubHistory>,event:State<String>,revision:State<Int>,visible:State<Boolean>,
        preference:State<String>,busy:State<Boolean>,width:State<Double>,lifecycle:LifecycleOwner?=null) {
        compose.mainClock.autoAdvance=false
        compose.activity.runOnUiThread {compose.activity.setContent {
            TokenTheme(dark,LanguageChoice.CHINESE,AppearancePreferences(interfaceFont=DesktopAppearance.systemFont)) {
                @Composable fun Body() {
                    val state=rememberDashboardOverviewMotion(history.value,today,width.value,event.value,revision.value,
                        DashboardMotionReason.ENTRY,visible.value && width.value>0,false,preference.value,busy.value)
                    SideEffect {motion=state;commits.incrementAndGet()}
                    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                        Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(14.dp)) {
                            if(visible.value) DesktopDashboardOverview(history.value,today,false,{}, {},motion=state)
                        }
                    }
                }
                CompositionLocalProvider(LocalDesktopAnimationClock provides animationClock,LocalDesktopSystemMotionScale provides 1f) {
                    if(lifecycle==null) Body() else CompositionLocalProvider(LocalLifecycleOwner provides lifecycle) {Body()}
                }
            }
        }}
        compose.mainClock.advanceTimeBy(32)
    }
    private fun pixels(tag:String)=compose.onNodeWithTag(tag,useUnmergedTree=true).captureToImage().toPixelMap()
    private fun near(a:Color,b:Color)=listOf(a.red-b.red,a.green-b.green,a.blue-b.blue).all {abs(it*255)<=3}
    private fun inkWidth(image:PixelMap,color:Color):Int=(0 until image.width).count {near(image[it,image.height/2],color)}
    private fun save(name:String)=saveVisualEvidence(compose.activity,compose.onRoot(),"dashboard-overview-motion-$name-${if(dark) "dark" else "light"}.png")

    @Test fun sourceEntryDoubleFrameHeatAnd800RankingPaintWithoutOwnerFrameReads() {
        mount(mutableStateOf(fixture()),mutableStateOf("entry"),mutableStateOf(0),mutableStateOf(true),
            mutableStateOf("off"),mutableStateOf(false),mutableStateOf(292.0))
        val entry=pixels("dashboard-ranking-models-A-bar")
        val heatEntry=pixels("dashboard-heatmap")
        save("entry")
        val initialCommits=commits.get()
        compose.mainClock.advanceTimeBy(368)
        val middle=pixels("dashboard-ranking-models-A-bar")
        val heatMiddle=pixels("dashboard-heatmap")
        save("middle")
        compose.mainClock.advanceTimeBy(432)
        val end=pixels("dashboard-ranking-models-A-bar")
        val color=end[end.width/2,end.height/2]
        val finalWidth=inkWidth(end,color)
        assertTrue("Cold rank must begin at a small fraction",inkWidth(entry,color) in 1..(finalWidth*.5).toInt())
        assertTrue("Source cubic data easing reaches near target at half duration",inkWidth(middle,color)>=finalWidth*.9)
        assertTrue("Actual width increases",inkWidth(entry,color)<inkWidth(middle,color))
        val point=(4*compose.activity.resources.displayMetrics.density).toInt()
        assertFalse("Heat guard precedes first column opacity",near(heatEntry[point,point],heatMiddle[point,point]))
        assertFalse(motion!!.frame.value.running)
        assertEquals("Frame State is read only by Canvas",initialCommits,commits.get())
        save("end")
        compose.mainClock.autoAdvance=true
    }

    @Test fun updateUsesPaintedGeometryAndSettingsReturnDoesNotReplayEntry() {
        val history=mutableStateOf(fixture())
        val event=mutableStateOf("entry")
        val revision=mutableStateOf(0)
        val shown=mutableStateOf(true)
        mount(history,event,revision,shown,mutableStateOf("off"),mutableStateOf(false),mutableStateOf(292.0))
        compose.mainClock.advanceTimeBy(832)
        val old=pixels("dashboard-ranking-models-A-bar")
        val color=old[old.width/2,old.height/2]
        compose.runOnIdle {history.value=fixture(50,100);event.value="actual-data"}
        compose.mainClock.advanceTimeBy(32)
        val start=pixels("dashboard-ranking-models-A-bar")
        assertTrue("Update retains displayed old width",inkWidth(start,color)>old.width*.85)
        assertFalse("Data updates do not replay the heat entry",motion!!.frame.value.heatPending)
        compose.mainClock.advanceTimeBy(832)
        val end=pixels("dashboard-ranking-models-A-bar")
        assertTrue("Updated A has half of the maximum",inkWidth(end,color) in (end.width*.45).toInt()..(end.width*.55).toInt())
        compose.runOnIdle {shown.value=false}
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle {shown.value=true}
        compose.mainClock.advanceTimeBy(32)
        assertFalse(motion!!.frame.value.running)
        assertFalse(motion!!.frame.value.entryPrepared)
        assertEquals(inkWidth(end,color),inkWidth(pixels("dashboard-ranking-models-A-bar"),color))
        // A real Overview tab re-entry is a new source render revision.
        compose.runOnIdle {revision.value++}
        compose.mainClock.advanceTimeBy(32)
        assertTrue(motion!!.frame.value.running)
        save("update-and-reentry")
        compose.mainClock.advanceTimeBy(832)
        compose.mainClock.autoAdvance=true
    }

    @Test fun unknownLayoutBusyBackgroundAndLiveReduceHaveExplicitBarriers() {
        val busy=mutableStateOf(true)
        val width=mutableStateOf(0.0)
        val preference=mutableStateOf("off")
        val revision=mutableStateOf(0)
        val lifecycle=Owner()
        mount(mutableStateOf(fixture()),mutableStateOf("entry"),revision,mutableStateOf(true),preference,busy,width,lifecycle)
        assertTrue(motion!!.frame.value.entryPrepared)
        compose.runOnIdle {width.value=292.0}
        compose.mainClock.advanceTimeBy(48)
        assertTrue("Refresh must retain the source prepaint guard",motion!!.frame.value.heatPending)
        compose.runOnIdle {busy.value=false}
        // Commit the changed Compose input before counting the source's two RAF
        // barriers. A clock tick delivering recomposition is not a scheduled RAF.
        compose.mainClock.advanceTimeBy(16)
        compose.runOnIdle {}
        assertTrue(motion!!.frame.value.heatPending)
        compose.mainClock.advanceTimeBy(16)
        assertTrue(motion!!.frame.value.heatPending)
        compose.mainClock.advanceTimeBy(16)
        assertFalse(motion!!.frame.value.heatPending)
        compose.runOnIdle {lifecycle.registry.currentState=Lifecycle.State.CREATED}
        compose.mainClock.advanceTimeBy(32)
        assertFalse(motion!!.frame.value.running)
        compose.runOnIdle {lifecycle.registry.currentState=Lifecycle.State.RESUMED}
        compose.mainClock.advanceTimeBy(32)
        assertFalse("Foreground restoration preserves a consumed entry",motion!!.frame.value.running)
        // Live system/user reduced motion settles both channels without another data key.
        compose.runOnIdle {revision.value++}
        compose.mainClock.advanceTimeBy(32)
        assertTrue("Live reduction is tested during an active new entry",motion!!.frame.value.running)
        compose.runOnIdle {preference.value="on"}
        compose.mainClock.advanceTimeBy(32)
        assertEquals(1f,motion!!.frame.value.heatAlpha(50,50),0f)
        assertFalse(motion!!.frame.value.running)
        save("background-reduced")
        compose.mainClock.autoAdvance=true
    }
}
