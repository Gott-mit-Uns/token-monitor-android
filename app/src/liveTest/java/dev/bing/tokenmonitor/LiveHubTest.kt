package dev.bing.tokenmonitor

import android.app.Application
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import java.util.concurrent.TimeUnit
import android.os.Bundle
import kotlinx.serialization.json.*
import android.os.SystemClock
import kotlinx.coroutines.*

/** Opt-in integration test. Real authentication stays in upstream bridge process memory. */
@RunWith(AndroidJUnit4::class)
class LiveHubTest {
    @get:Rule val activity = androidx.test.ext.junit.rules.ActivityScenarioRule(MainActivity::class.java)
    @Test fun readsRealNasHubThroughCredentialPreservingBridge() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val port = arguments.getString("liveHubPort")?.toIntOrNull() ?: error("Live Hub bridge port required")
        val observeSeconds = arguments.getString("observeLiveSeconds")?.toLongOrNull() ?: 75L
        require(observeSeconds in 65L..110L)
        val app = instrumentation.targetContext.applicationContext as Application
        val store = HubStore(app)
        assertNull("Real Activity test requires an empty emulator app profile", store.load())
        lateinit var model: HubViewModel
        activity.scenario.onActivity { model = ViewModelProvider(it)[HubViewModel::class.java] }
        fun await(condition: () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25)
            while (!condition() && System.nanoTime() < deadline) Thread.sleep(20)
            assertTrue("Live Hub condition not reached; private response not logged", condition())
        }
        fun checkpoint(stage: String) = instrumentation.sendStatus(2, Bundle().apply { putString("nativeLiveStage", stage) })
        fun focused() = if (activity.scenario.state != Lifecycle.State.RESUMED) false else {
            var value = false
            activity.scenario.onActivity { value = it.hasWindowFocus() }
            value
        }
        // Test-only trace: retain identities in memory, export only run-local ordinals/numbers.
        // Unconfined collection observes synchronous STARTED/terminal updates without polling.
        val historyEvents = mutableListOf<String>()
        var previousReceipt: HistoryReadReceipt? = null
        var previousKey: HistoryReadKey? = null
        var targetOrdinal = 0
        val traceScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        lateinit var historyTrace: Job
        instrumentation.runOnMainSync {
            historyTrace = traceScope.launch(start = CoroutineStart.UNDISPATCHED) {
                model.state.collect { state ->
                    val receipt = state.historyReadReceipt ?: return@collect
                    if (receipt == previousReceipt) return@collect
                    val changed = previousKey != null && previousKey != receipt.key
                    val revisionChanged = previousKey?.let { it.signature != receipt.key.signature } == true
                    val bindingChanged = previousKey?.let { it.connectionRevision != receipt.key.connectionRevision } == true
                    if (previousKey != receipt.key) targetOrdinal++
                    previousKey = receipt.key
                    previousReceipt = receipt
                    val event = buildJsonObject {
                        put("atMs", SystemClock.elapsedRealtime())
                        put("target", targetOrdinal)
                        put("changed", changed)
                        put("revisionChanged", revisionChanged)
                        put("bindingChanged", bindingChanged)
                        put("cycle", receipt.cycleId)
                        put("request", receipt.requestId ?: 0)
                        put("attempt", receipt.attempt)
                        put("phase", receipt.phase.name)
                        put("retryEligible", receipt.allowsAutomaticRead)
                    }.toString()
                    synchronized(historyEvents) { historyEvents.add(event) }
                }
            }
        }
        try {
            await { !model.state.value.loading }
            await { focused() }
            // Real emulator default Wi-Fi scope and Network.socketFactory; no scope/transport override.
            activity.scenario.onActivity { model.connect("http://10.0.2.2:$port", UUID.randomUUID().toString(), "") }
            await { model.state.value.live }
            assertTrue(model.state.value.snapshot!!.devices.isNotEmpty())
            assertTrue("Device period details should be available", model.state.value.snapshot!!.devices.any { "allTime" in it.periods })
            assertTrue(model.state.value.snapshot!!.periods.keys.containsAll(listOf("today", "month", "allTime")))
            await { store.load()?.snapshot != null }
            val cached = store.load()!!.snapshot
            LiveV5ContractProof.verify(cached,HubParser.parse(cached))
            val sourceZones = (Json.parseToJsonElement(cached).jsonObject["devices"] as JsonArray).associate { value ->
                val source = value.jsonObject
                source["deviceId"]!!.jsonPrimitive.content to
                    (source["periodWindows"] as? JsonObject)?.get("timeZone")?.jsonPrimitive?.contentOrNull
            }
            assertTrue("Source time zones should survive device-period parsing without logging them",
                HubParser.parse(cached).devices.all { device ->
                    val zone = sourceZones[device.id]
                    zone != null && device.periods.values.all { it.zone == zone }
                })
            checkpoint("sustain")
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(observeSeconds)
            while (System.nanoTime() < until) {
                assertTrue("Real foreground stream must remain live", model.state.value.live)
                assertTrue("Real Activity must stay foreground during observation", activity.scenario.state == Lifecycle.State.RESUMED)
                Thread.sleep(100)
            }
            checkpoint("observed")
            // The desktop Home now loads its charts automatically while foreground.
            if (model.state.value.history == null) instrumentation.runOnMainSync { model.loadHistory() }
            await { !model.state.value.loading }
            assertTrue("History read was not available", model.state.value.history != null)
            await { store.load()?.history != null }
            instrumentation.runOnMainSync { model.loadSubscriptions() }
            await { !model.state.value.loading }
            assertTrue("Subscription read was not available", model.state.value.subscriptions != null)
            if (model.state.value.modelNames == null) instrumentation.runOnMainSync { model.loadModelNames() }
            await { !model.state.value.loading }
            assertTrue("Shared model rules were not available", model.state.value.modelNames != null)
            instrumentation.runOnMainSync { model.loadPricing() }
            await { !model.state.value.loading }
            assertTrue("Shared pricing read was not available", model.state.value.pricing != null)
            await { store.load()?.pricing != null }
            assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
            await { !model.state.value.live && activity.scenario.state != Lifecycle.State.RESUMED }
            checkpoint("background")
            // Host independently verifies upstream EOF and zero new reads during this interval.
            Thread.sleep(6000)
            assertFalse(model.state.value.live)
            assertTrue("Private snapshot should restore without logging it", store.load()?.snapshot != null)
            app.startActivity(Intent(Intent.ACTION_MAIN).setClass(app, MainActivity::class.java)
                .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            await { model.state.value.live && focused() }
            checkpoint("resumed")
        } finally {
            instrumentation.runOnMainSync { model.setForeground(false); model.forget() }
            await { !model.state.value.loading }
            store.clear()
            instrumentation.runOnMainSync { historyTrace.cancel(); traceScope.cancel() }
            synchronized(historyEvents) { historyEvents.toList() }.forEach { event ->
                instrumentation.sendStatus(2, Bundle().apply { putString("nativeHistoryEvent", event) })
            }
        }
    }
}
