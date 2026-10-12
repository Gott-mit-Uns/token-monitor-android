package dev.bing.tokenmonitor

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontFamily
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Real Window metrics on public Dashboard components. No Compose clock, endpoint or Hub read. */
class DashboardRuntimeBenchmarkTest {
    private val today=LocalDate.of(2026,10,11)
    private data class Workload(val name:String,val days:Int,val keys:Int,val mode:DashboardTrendMode,val range:String)
    private data class Sample(val run:Int,val vsync:Long,val intended:Long,val total:Long,val deadline:Long,
        val unknown:Long,val layout:Long,val draw:Long)
    private data class Ready(val width:Double,val height:Double,val overviewWidth:Double,val daily:Int,
        val bars:Int,val segments:Int,val candles:Int,val legend:Int,val bucketDays:Int,val committed:Boolean)
    private class Probe {
        val ready=AtomicReference<Ready?>()
        var chart:State<DashboardChartFrame>?=null
        var overview:DashboardOverviewMotionState?=null
        val ownerCommits=AtomicInteger()
        val committedRevision=AtomicLong(-1L)
        val targetCommitNanos=AtomicLong(-1L)
    }

    private fun history(days:Int,keys:Int,variant:Int):HubHistory {
        fun cost(tokens:Long)=BigDecimal.valueOf(tokens).movePointLeft(6).stripTrailingZeros().toPlainString()
        val tools=listOf("codex","claude","gemini","opencode","cursor","antigravity","copilot","public-tool-7")
        val daily=List(days) {day->
            val values=List(keys) {key->
                val base=(day%13+1L)*(key+1L)*1000L
                base*if(variant==1 && (day+key)%3==0) 4L else 1L
            }
            val tokens=values.sum()
            val clients=linkedMapOf<String,HistoryAttribution>()
            val models=linkedMapOf<String,HistoryAttribution>()
            values.forEachIndexed {key,value->
                val tool=if(keys==8) tools[key] else "public-tool-${key.toString().padStart(2,'0')}"
                val attribution=HistoryAttribution(value,cost(value),0)
                clients[tool]=attribution
                models["public-model-${key.toString().padStart(2,'0')}"]=attribution
            }
            HistoryRow(today.minusDays((days-day-1).toLong()).toString(),tokens,cost(tokens),0,
                clients,models,3_600_000L,keys*2L)
        }
        val monthly=daily.groupBy {it.date.take(7)}.map {(month,rows)->
            val sum=rows.sumOf {it.tokens!!}
            HistoryRow(month,sum,cost(sum),0)
        }.sortedByDescending {it.date}
        val total=daily.sumOf {it.tokens!!}
        return HubHistory(daily.reversed(),monthly,HistorySummary(total,cost(total),days.toLong(),days.toLong(),
            days.toLong(),days*3_600_000L,daily.maxOf {it.tokens!!},"public-model-00",days*keys*2L,0))
    }

    /** This owner matches the root data/geometry/holders/container shape, with no Hub state or reads. */
    @Composable private fun DashboardOwner(history:HubHistory,revision:Long,ui:DashboardUiState,
        preferences:DisplayPreferences,probe:Probe) {
        val model=remember(history,ui.range,ui.byModel,ui.mode,ui.chartWidth,ui.chartHeight) {
            DashboardTrendProjection.project(history,ui.range,ui.byModel,ui.mode,ui.chartWidth,ui.chartHeight,"en")
        }
        val renderKey=listOf("public-dashboard-runtime",history,revision,today)
        val chart=rememberDashboardChartMotion(ui.committedChart,ui.committedChartEvent,ui.committedChartRevision,ui.committedChartReason,
            ui.tab==DashboardTab.TRENDS && ui.committedChart!=null,preferences.appearance.reduceMotion)
        val overview=rememberDashboardOverviewMotion(history,today,ui.overviewWidth,renderKey,ui.motionRevision,
            ui.motionReason,ui.tab==DashboardTab.OVERVIEW && ui.overviewWidth>0,ui.metricCost,
            preferences.appearance.reduceMotion,false)
        SideEffect {
            // No frame value is read here. State is sampled outside Compose for start/end validation.
            probe.chart=chart;probe.overview=overview;probe.ownerCommits.incrementAndGet()
            val displayed=ui.committedChart ?: model
            val committed=ui.committedChart!=null && displayed.range==ui.range && displayed.mode==ui.mode &&
                ui.committedChartEvent==renderKey && ui.committedChartRevision==ui.motionRevision
            if(committed && probe.committedRevision.getAndSet(revision)!=revision)
                probe.targetCommitNanos.set(System.nanoTime())
            probe.ready.set(Ready(ui.chartWidth,ui.chartHeight,ui.overviewWidth,displayed.daily.size,displayed.bars.size,
                displayed.bars.sumOf {it.segments.size},displayed.candles.size,displayed.legend.size,displayed.bucketDays,
                committed))
        }
        val font=nativeFont(preferences.appearance.interfaceFont,FontFamily.SansSerif)
        val typography=MaterialTheme.typography
        MaterialTheme(typography=typography.copy(
            bodySmall=typography.bodySmall.copy(fontFamily=font),bodyMedium=typography.bodyMedium.copy(fontFamily=font),
            bodyLarge=typography.bodyLarge.copy(fontFamily=font),titleMedium=typography.titleMedium.copy(fontFamily=font),
            titleLarge=typography.titleLarge.copy(fontFamily=font),labelSmall=typography.labelSmall.copy(fontFamily=font),
            labelMedium=typography.labelMedium.copy(fontFamily=font),labelLarge=typography.labelLarge.copy(fontFamily=font))) {
            CompositionLocalProvider(LocalDesktopDisplayFont provides nativeFont(preferences.appearance.displayFont,font)) {
                DesktopDashboardScreen(history,false,null,ui,model,today,preferences,null,{}, {}, {},chart,overview,chartEventKey=renderKey)
            }
        }
    }

    private fun stats(values:List<Double>):JSONObject {
        require(values.isNotEmpty() && values.all {it.isFinite() && it>=0})
        val sorted=values.sorted()
        fun at(p:Double)=sorted[((sorted.size-1)*p).toInt()]
        return JSONObject().put("p50",at(.5)).put("p95",at(.95)).put("max",sorted.last())
    }
    private fun waitBounded(message:String,timeoutMs:Long=8000,condition:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+timeoutMs
        while(!condition() && SystemClock.elapsedRealtime()<end) SystemClock.sleep(20)
        assertTrue(message,condition())
    }
    private fun isEmulator()=Build.HARDWARE in listOf("goldfish","ranchu","cutf_cvm") ||
        Build.FINGERPRINT.startsWith("generic") || Build.PRODUCT.startsWith("sdk")

    @Test fun realClockPublicDashboardUpdatesRecordWindowCadenceAndOwnerCommits() {
        assertTrue("Window deadline diagnostics require API31+",Build.VERSION.SDK_INT>=31)
        assertTrue("This benchmark is restricted to the root runner's owned emulator",isEmulator())
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val target=instrumentation.targetContext
        val apkHash=MessageDigest.getInstance("SHA-256").digest(File(target.applicationInfo.sourceDir).readBytes())
            .joinToString("") {"%02x".format(it)}
        val scenes=listOf(Workload("normal-bars-30x8",30,8,DashboardTrendMode.BARS,"30"),
            Workload("all-bars-366x80",366,80,DashboardTrendMode.BARS,"all"),
            Workload("all-kline-366",366,80,DashboardTrendMode.KLINE,"all"))
        val samples=Collections.synchronizedList(mutableListOf<Sample>())
        val activeRun=AtomicInteger(-1)
        val windowStart=AtomicLong(Long.MAX_VALUE)
        val windowEnd=AtomicLong(Long.MIN_VALUE)
        val lostCallbacks=AtomicInteger()
        val invalidSamples=AtomicInteger()
        val metricsThread=HandlerThread("public-dashboard-frame-metrics").apply {start()}
        val metricsHandler=Handler(metricsThread.looper)
        fun drainMetrics() {
            val barrier=CountDownLatch(1)
            metricsHandler.post {barrier.countDown()}
            assertTrue("Frame metrics callback queue must drain",barrier.await(5,TimeUnit.SECONDS))
        }
        val listener=Window.OnFrameMetricsAvailableListener {_,frame,lost->
            val run=activeRun.get()
            val vsync=frame.getMetric(FrameMetrics.VSYNC_TIMESTAMP)
            if(run>=0 && vsync>=windowStart.get() && vsync<windowEnd.get()) {
                val sample=Sample(run,vsync,frame.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP),
                    frame.getMetric(FrameMetrics.TOTAL_DURATION),frame.getMetric(FrameMetrics.DEADLINE),
                    frame.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION),frame.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),
                    frame.getMetric(FrameMetrics.DRAW_DURATION))
                if(sample.intended<=0 || sample.vsync<sample.intended || sample.total<0 || sample.deadline<=0 ||
                    sample.unknown<0 || sample.layout<0 || sample.draw<0) invalidSamples.incrementAndGet()
                else samples.add(sample)
                lostCallbacks.addAndGet(lost)
            }
        }
        val preferences=DisplayPreferences(theme=ThemeChoice.DARK,language=LanguageChoice.ENGLISH,
            appearance=AppearancePreferences(reduceMotion="off"),homeModules=emptyList())
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        val reports=JSONArray()
        var refreshRate=0f
        var windowWidth=0
        var windowHeight=0
        try {
            scenario.onActivity {activity->
                refreshRate=activity.windowManager.defaultDisplay.refreshRate
                windowWidth=activity.window.decorView.width;windowHeight=activity.window.decorView.height
                activity.window.addOnFrameMetricsAvailableListener(listener,metricsHandler)
            }
            scenes.forEachIndexed {sceneIndex,scene->
                // Allocate public payloads outside the measured UI interval. Nonuniform
                // changes preserve keys but alter normalized geometry, unlike a uniform multiplier.
                val variants=listOf(history(scene.days,scene.keys,0),history(scene.days,scene.keys,1))
                val state=mutableStateOf(variants[0])
                val renderRevision=mutableLongStateOf(0L)
                val ui=DashboardUiState()
                val probe=Probe()
                scenario.onActivity {activity->
                    ui.selectTab(DashboardTab.TRENDS)
                    ui.selectRange(scene.range)
                    ui.selectMode(scene.mode)
                    activity.setContent {
                        // Default LocalDesktopAnimationClock is System.nanoTime and
                        // Compose's actual Android frame clock supplies withFrameNanos.
                        TokenTheme(true,LanguageChoice.ENGLISH,preferences.appearance) {
                            DashboardOwner(state.value,renderRevision.longValue,ui,preferences,probe)
                        }
                    }
                }
                waitBounded("Real Dashboard layout must finish measuring before warmup") {
                    probe.ready.get()?.let {it.committed && it.width>0 && it.height>0 && it.overviewWidth>0 && it.daily==scene.days &&
                        (if(scene.mode==DashboardTrendMode.BARS) it.bars==scene.days && it.segments==scene.days*scene.keys else it.candles>0)} ?: false
                }
                SystemClock.sleep(1400) // Cold entry and layout converge before unrecorded updates.
                repeat(2) {index->
                    scenario.onActivity {state.value=variants[if(index%2==0) 1 else 0];renderRevision.longValue++}
                    SystemClock.sleep(1200)
                }
                scenario.onActivity {
                    assertFalse("Warm Dashboard chart must settle",probe.chart!!.value.running)
                    assertFalse("Hidden Overview holder must settle",probe.overview!!.frame.value.running)
                }
                val ready=probe.ready.get()!!
                val baseDuration=if(scene.mode==DashboardTrendMode.BARS) 800 else 560
                val lastDelay=if(scene.mode==DashboardTrendMode.KLINE) minOf((ready.candles-1).coerceAtLeast(0),18)*10 else 0
                val runs=JSONArray()
                repeat(3) {index->
                    drainMetrics()
                    val id=sceneIndex*3+index
                    lostCallbacks.set(0);invalidSamples.set(0)
                    var begin=0L
                    val wallBegin=SystemClock.elapsedRealtime()
                    scenario.onActivity {
                        probe.ownerCommits.set(0)
                        begin=System.nanoTime()
                        windowStart.set(begin);windowEnd.set(begin+1_000_000_000L);activeRun.set(id)
                        state.value=variants[if(index%2==0) 1 else 0];renderRevision.longValue++
                    }
                    var started=false
                    waitBounded("$scene run$index must commit and start the real chart UPDATE",3000) {
                        scenario.onActivity {started=probe.ready.get()?.committed==true && probe.chart!!.value.running}
                        started
                    }
                    // Input projection and the native layout transaction belong to this
                    // observation interval; a fixed80ms probe could race their commit.
                    val remaining=1200-(SystemClock.elapsedRealtime()-wallBegin)
                    if(remaining>0) SystemClock.sleep(remaining)
                    drainMetrics()
                    var settled=false
                    scenario.onActivity {settled=!probe.chart!!.value.running && !probe.overview!!.frame.value.running}
                    activeRun.set(-1)
                    val frames=synchronized(samples) {samples.filter {it.run==id}.sortedBy {it.vsync}}
                    assertTrue("$scene run$index must render the actual UPDATE",frames.size>=5)
                    val gaps=frames.zipWithNext {a,b->(b.vsync-a.vsync)/1e6}
                    val span=frames.last().vsync-frames.first().vsync
                    assertTrue("Window cadence must have a positive observed span",span>0 && gaps.all {it>0 && it.isFinite()})
                    val cadence=(frames.size-1)*1e9/span
                    assertTrue("Observed window cadence must be finite and bounded",cadence.isFinite() && cadence in 1.0..1000.0)
                    val committedAt=probe.targetCommitNanos.get()
                    assertTrue("The current target must commit after this input",committedAt>=begin && probe.committedRevision.get()==renderRevision.longValue)
                    val postCommit=frames.filter {it.vsync>=committedAt}
                    assertTrue("The committed chart must retain actual Window samples",postCommit.size>=5)
                    val postCommitSpan=postCommit.last().vsync-postCommit.first().vsync
                    val postCommitGaps=postCommit.zipWithNext {a,b->(b.vsync-a.vsync)/1e6}
                    val owner=probe.ownerCommits.get()
                    val complete=lostCallbacks.get()==0 && invalidSamples.get()==0
                    runs.put(JSONObject().put("index",index).put("renderEvent","UPDATE").put("frames",frames.size)
                        .put("windowDrawCadenceHz",cadence).put("observedVsyncSpanMs",span/1e6)
                        .put("targetCommitDelayMs",(committedAt-begin)/1e6)
                        .put("postCommitFrames",postCommit.size).put("postCommitVsyncSpanMs",postCommitSpan/1e6)
                        .put("postCommitWindowDrawCadenceHz",(postCommit.size-1)*1e9/postCommitSpan)
                        .put("postCommitVsyncGapMs",stats(postCommitGaps))
                        .put("sampleWindowMs",1000).put("compositionObservationMs",1200)
                        .put("vsyncGapMs",stats(gaps)).put("lateVsyncMs",stats(frames.map {(it.vsync-it.intended)/1e6}))
                        .put("totalMs",stats(frames.map {it.total/1e6})).put("deadlineMs",stats(frames.map {it.deadline/1e6}))
                        .put("unknownDelayMs",stats(frames.map {it.unknown/1e6})).put("layoutMs",stats(frames.map {it.layout/1e6}))
                        .put("drawMs",stats(frames.map {it.draw/1e6})).put("framesOverDeadline",frames.count {it.total>=it.deadline})
                        .put("dashboardOwnerCompositions",owner).put("metricsCallbacksDropped",lostCallbacks.get())
                        .put("invalidMetricSamples",invalidSamples.get()).put("metricsComplete",complete)
                        .put("animationObservedRunning",started).put("animationSettled",settled))
                    // Preserve public timing evidence before an assertion stops the run.
                    // This I/O is outside the measured interval; a partial report never passes the seal.
                    File(target.getExternalFilesDir(null),"dashboard-runtime-partial-public.json").writeText(
                        JSONObject().put("apkSha256",apkHash).put("partial",true)
                            .put("clock","real Android Choreographer; no Compose test clock")
                            .put("scope","Owned emulator public Dashboard diagnostics; incomplete run, not final evidence")
                            .put("scene",scene.name).put("run",runs.getJSONObject(runs.length()-1)).toString(2))
                    assertTrue("$scene run$index must start the actual chart motion",started)
                    assertTrue("$scene run$index must settle after its source duration/stagger",settled)
                    if(InstrumentationRegistry.getArguments().getString("expectIsolated")=="true")
                        assertTrue("Canvas animation must not recompose its Dashboard owner per frame",owner<=5)
                }
                reports.put(JSONObject().put("name",scene.name).put("tab","TRENDS").put("mode",scene.mode.name)
                    .put("range",scene.range).put("byModel",false).put("retainedDailyRows",scene.days)
                    .put("attributionKeysPerDimension",scene.keys).put("attributionDimensions",2)
                    .put("publicVariants",2).put("variantChange","(day+key)%3==0 receives4x; matching raw dates/keys remain")
                    .put("warmupUpdates",2).put("recordedUpdates",3).put("animationBaseMs",baseDuration)
                    .put("maximumUpdateStaggerMs",lastDelay).put("durationScale",1)
                    .put("measuredGeometry",JSONObject().put("widthDp",ready.width).put("heightDp",ready.height)
                        .put("overviewWidthDp",ready.overviewWidth).put("bars",ready.bars).put("segments",ready.segments)
                        .put("candles",ready.candles).put("candleBucketDays",ready.bucketDays).put("legendRows",ready.legend))
                    .put("runs",runs))
            }
            scenario.onActivity {activity->windowWidth=activity.window.decorView.width;windowHeight=activity.window.decorView.height}
            val report=JSONObject().put("schemaVersion",1).put("apkSha256",apkHash)
                .put("clock","real Android Choreographer; no Compose test clock").put("monotonicClock","System.nanoTime")
                .put("metricsSource","Window.OnFrameMetricsAvailableListener; frame timestamp filtered by run window")
                .put("scope","Owned emulator public workload; real DesktopDashboardScreen/container and both motion holders; not HubScreen navigation or physical-device presentation FPS")
                .put("ownershipBoundary","Root runner verifies emulator ownership and clears its saved profile; this test rejects non-emulator Build")
                .put("emulatorBuildObserved",isEmulator()).put("androidApi",Build.VERSION.SDK_INT)
                .put("refreshRateHz",refreshRate).put("windowPixels",JSONObject().put("width",windowWidth).put("height",windowHeight))
                .put("dashboardOwnerScope","Benchmark owner: history projection, geometry, both motion holders and actual DesktopDashboardScreen call; not all child compositions")
                .put("actualRootNavigationMeasured",false).put("physicalFpsMeasured",false).put("dashboardOverviewMotionMeasured",false)
                .put("fixture","Only deterministic public histories. No endpoint, credentials, HubUiActions or actual Hub data.")
                .put("scenarios",reports).put("limitations",JSONArray(listOf(
                    "Window callback cadence and Android timings are current-only; no before comparison or physical FPS claim.",
                    "Full-window cadence includes input projection, layout and preparation. Target delay ends at the owner's first committed-target SideEffect; post-commit cadence uses only later Window timestamps, not presentation FPS.",
                    "Overview holder is mounted but hidden/settled in these Trends workloads; heatmap entry is separately tested.",
                    "Attribution fixtures exist in both perClient and perModel; visible bars use perClient.",
                    "Callback loss in an included callback is conservatively attributed to that run; invalid samples mark it incomplete.",
                    "owner<=5 is optional via expectIsolated; there is no minimum owner-composition count.")))
            File(target.getExternalFilesDir(null),"dashboard-runtime-public.json").writeText(report.toString(2))
        } finally {
            activeRun.set(-1)
            scenario.onActivity {it.window.removeOnFrameMetricsAvailableListener(listener)}
            scenario.close();metricsThread.quitSafely();metricsThread.join(5000)
        }
    }
}
