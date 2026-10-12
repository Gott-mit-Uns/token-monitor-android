package dev.bing.tokenmonitor

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Real Android frame clock and Window metrics; no Compose test clock or sampled screenshots. */
class HeadlineRuntimeBenchmarkTest {
    private class Actions : HubUiActions {
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
    private fun fixture(tokens:Long):HubState {
        val models=JSONObject(); val costs=JSONObject(); val sessions=JSONObject()
        repeat(80) {models.put("public-model-$it",tokens/80);costs.put("public-model-$it",1.0)}
        repeat(120) {sessions.put("public-session-$it",JSONObject().put("sessionId","public-session-$it").put("title","Public sample $it").put("client","codex").put("totalTokens",1000).put("turnEnded",true).put("lastUsedAt","2026-10-09T08:00:00Z"))}
        val period=JSONObject().put("totalTokens",tokens).put("costUsd",80).put("models",models).put("modelCosts",costs).put("sessions",sessions).put("clients",JSONObject().put("codex",tokens)).put("clientCosts",JSONObject().put("codex",80))
        val json=JSONObject().put("updatedAt","2026-10-09T08:00:00Z").put("periods",JSONObject().put("today",period))
        return HubState(snapshot=HubParser.parse(json.toString()),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"))
    }
    private data class Sample(val vsync:Long,val intendedVsync:Long,val total:Long,val deadline:Long,val unknownDelay:Long,val layout:Long,val draw:Long)
    @Test fun realClockPublicWorkloadRecordsFrameCadenceAndCompositionScope() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val target=instrumentation.targetContext
        val samples=Collections.synchronizedList(mutableListOf<Sample>())
        val hub=AtomicInteger(); val total=AtomicInteger(); val dropped=AtomicInteger()
        val handlerThread=HandlerThread("public-frame-metrics").apply {start()}
        val metricsHandler=Handler(handlerThread.looper)
        fun drainMetrics() {
            val barrier=CountDownLatch(1)
            metricsHandler.post {barrier.countDown()}
            assertTrue("Frame metrics callback queue must drain",barrier.await(5,TimeUnit.SECONDS))
        }
        val listener=Window.OnFrameMetricsAvailableListener {_,frame,lost->
            samples.add(Sample(frame.getMetric(FrameMetrics.VSYNC_TIMESTAMP),frame.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP),frame.getMetric(FrameMetrics.TOTAL_DURATION),frame.getMetric(FrameMetrics.DEADLINE),frame.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION),frame.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),frame.getMetric(FrameMetrics.DRAW_DURATION)))
            dropped.addAndGet(lost)
        }
        val state=mutableStateOf(fixture(1_000_000_000L))
        val preferences=DisplayPreferences(theme=ThemeChoice.DARK,language=LanguageChoice.ENGLISH,appearance=AppearancePreferences(reduceMotion="off"),homeModules=listOf("models","sessions"))
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        val runs=JSONArray();var refresh=60f
        try {
            scenario.onActivity {activity->
                refresh=activity.windowManager.defaultDisplay.refreshRate
                activity.window.addOnFrameMetricsAvailableListener(listener,metricsHandler)
                activity.setContent {
                    CompositionLocalProvider(LocalHeadlineRenderObserver provides {scope->if(scope=="hub") hub.incrementAndGet() else total.incrementAndGet()},LocalHubCalendarClock provides Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"),ZoneId.of("Asia/Shanghai"))) {
                        TokenTheme(true,LanguageChoice.ENGLISH,preferences.appearance) {HubScreen(state.value,Actions(),preferences=preferences)}
                    }
                }
            }
            SystemClock.sleep(1600)
            // Two unrecorded updates warm text/layout/JIT before the three recorded transitions.
            repeat(2) {index->val next=fixture(if(index%2==0) 2_000_000_000L else 1_000_000_000L);scenario.onActivity {state.value=next};SystemClock.sleep(1400)}
            repeat(3) {index->
                drainMetrics()
                hub.set(0);total.set(0);dropped.set(0)
                val next=fixture(if(index%2==0) 2_000_000_000L else 1_000_000_000L)
                var begin=0L
                scenario.onActivity {begin=System.nanoTime();state.value=next}
                SystemClock.sleep(1400)
                drainMetrics()
                val frames=synchronized(samples) {samples.filter {it.vsync>=begin && it.vsync<begin+1_200_000_000L}}
                assertTrue("Window must render the actual public transition",frames.size>=5)
                val ordered=frames.sortedBy {it.vsync};val gaps=ordered.zipWithNext {a,b->(b.vsync-a.vsync)/1e6}
                fun stats(values:List<Double>):JSONObject {
                    val sorted=values.sorted();fun at(p:Double)=sorted[((sorted.size-1)*p).toInt()]
                    return JSONObject().put("p50",at(.5)).put("p95",at(.95)).put("max",sorted.last())
                }
                val span=ordered.last().vsync-ordered.first().vsync
                runs.put(JSONObject().put("frames",frames.size).put("windowDrawCadenceHz",(frames.size-1)*1e9/span).put("observedVsyncSpanMs",span/1e6).put("sampleWindowMs",1200).put("compositionObservationMs",1400).put("vsyncGapMs",stats(gaps)).put("lateVsyncMs",stats(frames.map {(it.vsync-it.intendedVsync)/1e6})).put("totalMs",stats(frames.map {it.total/1e6})).put("deadlineMs",stats(frames.map {it.deadline/1e6})).put("unknownDelayMs",stats(frames.map {it.unknownDelay/1e6})).put("layoutMs",stats(frames.map {it.layout/1e6})).put("drawMs",stats(frames.map {it.draw/1e6})).put("framesOverDeadline",frames.count {it.deadline>0 && it.total>=it.deadline}).put("hubCompositions",hub.get()).put("totalCompositions",total.get()).put("metricsCallbacksDropped",dropped.get()).put("metricsComplete",dropped.get()==0))
                if(InstrumentationRegistry.getArguments().getString("expectIsolated")=="true") {
                    assertTrue("Per-frame counter updates must not recompose the whole Hub",hub.get()<=5)
                    assertTrue("The number region must continue rendering its transition",total.get()>=10)
                }
            }
            val apkHash=MessageDigest.getInstance("SHA-256").digest(File(target.applicationInfo.sourceDir).readBytes()).joinToString("") {"%02x".format(it)}
            val report=JSONObject().put("apkSha256",apkHash).put("clock","real Android Choreographer; no Compose test clock").put("fixture",JSONObject().put("models",80).put("sessions",120)).put("refreshRateHz",refresh).put("runs",runs).put("scope","Owned emulator public workload; not physical-device presentation FPS")
            File(target.getExternalFilesDir(null),"headline-runtime-public.json").writeText(report.toString(2))
        } finally {
            scenario.onActivity {it.window.removeOnFrameMetricsAvailableListener(listener)}
            scenario.close();handlerThread.quitSafely();handlerThread.join(5000)
        }
    }
}
