package dev.bing.tokenmonitor

import android.app.Application
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.net.InetAddress
import java.net.Socket
import java.security.MessageDigest
import java.text.NumberFormat
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.ArrayDeque
import java.util.Collections
import java.util.EnumMap
import java.util.Locale
import java.util.PriorityQueue
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Opt-in phase attribution of the production ViewModel and screen, using complete
 * large public Hub source data and two explicitly different display preferences. TLS is a loopback test relay; it is not a Lucky certificate test.
 */
class HubCapacityPhaseTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application
    private val port get() = InstrumentationRegistry.getArguments().getString("capacityPort")?.toIntOrNull() ?: 18748
    private val base get() = "http://127.0.0.1:$port/capacity/$SHAPE"
    private val animationClock = DesktopAnimationClock { compose.mainClock.currentTime * 1_000_000L }

    private val runIndex get() = InstrumentationRegistry.getArguments().getString("capacityRunIndex")?.toIntOrNull() ?: 0
    private var deadline = 0L

    @Test fun emptyHomeReducedMotionReference() = runScenario(defaultHome = false)
    @Test fun defaultHomeNormalMotion() = runScenario(defaultHome = true)

    /** One scenario per invocation; ROOT supplies a fresh instrumentation process for each repeat. */
    private fun runScenario(defaultHome: Boolean) {
        val scenario = if (defaultHome) "default-home-normal-motion" else "empty-home-reduced-motion-reference"
        report("capacity-phase50-$scenario-run$runIndex-public.json", scenario) { metrics, probe ->
            val activity = compose.activity
            Fixture(burst = true).use { fixture ->
                metrics.put("originalStatsUtf8Bytes", fixture.expected.getValue("sizes").jsonObject.getValue("statsUtf8Bytes").jsonPrimitive.long)
                val freshPreferences = DisplayPreferences()
                val preferences = mutableStateOf(freshPreferences.copy(language = LanguageChoice.ENGLISH,
                    homeModules = if (defaultHome) freshPreferences.homeModules else emptyList(),
                    appearance = AppearancePreferences(reduceMotion = if (defaultHome) "off" else "on")))
                probe.action(Action.SOURCE_CONNECT_AND_FULL_BURST) {
                    fixture.connect()
                    await("All four complete public source frames did not commit") {
                        fixture.model.state.value.live && !fixture.model.state.value.loading &&
                            fixture.model.state.value.snapshot?.updated == BURST_FINAL && fixture.relay.completeFullFrames.get() == 4
                    }
                    verifySnapshot(fixture.model.state.value.snapshot ?: error("Missing complete source snapshot"))
                    fixture.verifyOriginalStatsPayload()
                    assertEquals(4, fixture.relay.verifiedFullDigests.get())
                    assertTrue("Four full SSE snapshot accepts were not traced", probe.completed(HubWorkStage.MAIN_ACCEPT) >= 4)
                    assertTrue("Separate health and original stats HTTP reads were not traced", probe.completed(HubWorkStage.HTTP_READ) >= 2)
                }
                probe.action(Action.HISTORY_READ) {
                    assertTrue("History read was not admitted", onMain { fixture.model.loadHistory(true) })
                    await("Complete source history was not retained") {
                        !fixture.model.state.value.loading && fixture.model.state.value.history?.daily?.size == 31
                    }
                }
                probe.action(Action.MODEL_NAMES_READ) {
                    onMain { fixture.model.loadModelNames() }
                    await("Shared model names did not finish") { !fixture.model.state.value.loading && fixture.model.state.value.modelNames != null }
                }
                probe.action(Action.DEVICE_HISTORY_READ) {
                    onMain { fixture.model.loadDeviceHistory() }
                    await("Complete device history did not finish") {
                        !fixture.model.state.value.loading && fixture.model.state.value.deviceHistories?.devices?.size == 16
                    }
                    verifyHistory(fixture.model.state.value)
                }
                await("Complete read workflow did not settle its source stream") {
                    fixture.model.state.value.live && fixture.model.state.value.snapshot?.updated == BURST_FINAL
                }
                probe.action(Action.HOME_ATTACH) { attachScreen(fixture.model, preferences, activity) }
                metrics.put("homeAttachedAfterCompleteReadWorkflow", true)
                probe.action(Action.STABLE_HOME) {
                    headline(TODAY_TOKENS)
                    if ("sessions" in preferences.value.homeModules) {
                        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("home-sessions-jump"))
                        compose.onNodeWithTag("home-sessions-jump").assertExists()
                    }
                    if ("devices" in preferences.value.homeModules) {
                        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("home-device-list"))
                        compose.onNodeWithTag("home-device-list").assertExists()
                    }
                    Thread.sleep(300)
                    assertEquals(BURST_FINAL, fixture.model.state.value.snapshot?.updated)
                }
                probe.action(Action.FULL_BURST_WITH_HOME) {
                    val beforeFrames = fixture.relay.completeFullFrames.get()
                    val beforeAccepts = probe.completed(HubWorkStage.MAIN_ACCEPT)
                    onMain { fixture.model.setForeground(false) }
                    fixture.awaitStreamClosed()
                    onMain { fixture.model.setForeground(true) }
                    await("Complete source burst did not commit with the actual Home attached") {
                        fixture.relay.completeFullFrames.get() >= beforeFrames + 4 &&
                            probe.completed(HubWorkStage.MAIN_ACCEPT) >= beforeAccepts + 4 && fixture.model.state.value.live
                    }
                    headline(TODAY_TOKENS)
                    verifySnapshot(fixture.model.state.value.snapshot ?: error("Home full burst lost its data"))
                }
                // long reads close/reopen the stream; each relay generation sends the same four
                // source frames, then holds only tiny freshness events until an explicit credit.
                await("Read workflow did not return to a live held stream") { fixture.model.state.value.live }
                probe.action(Action.PERIOD_ALL_TIME) {
                    compose.onNodeWithTag("hub-period-allTime").performClick(); headline(TODAY_TOKENS * 31)
                }
                probe.action(Action.PERIOD_MONTH) {
                    compose.onNodeWithTag("hub-period-month").performClick(); headline(TODAY_TOKENS * 9)
                }
                probe.action(Action.PERIOD_TODAY) { compose.onNodeWithTag("hub-period-today").performClick(); headline(TODAY_TOKENS) }
                probe.action(Action.SESSIONS_FIRST_PAGE) {
                    select("sessions")
                    compose.onNodeWithTag("session-pager").assertExists()
                    compose.onNodeWithTag("session-page-status").assertTextEquals("1–100 of 4800")
                }
                probe.action(Action.SESSION_NEXT_PAGE) {
                    compose.onNodeWithTag("session-page-next").performClick()
                    compose.onNodeWithTag("session-page-status").assertTextEquals("101–200 of 4800")
                }
                probe.action(Action.PROJECTS_PREPARE) { select("projects") }
                probe.action(Action.PROJECT_SOURCE_ROW) {
                    val row = "project-row-public project public-d00-s000"
                    compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(row))
                    compose.onNodeWithTag(row).assertIsDisplayed().performClick()
                    compose.onNodeWithTag(row).assert(SemanticsMatcher.expectValue(
                        androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Collapse details"))
                    compose.onNodeWithTag("project-tool-public project public-d00-s000-0", useUnmergedTree = true).assertExists()
                    compose.onNodeWithTag("project-tokens-public project public-d00-s000", true).assertTextEquals("5,000,000,123")
                }
                probe.action(Action.DEVICES_PREPARE) { select("devices") }
                probe.action(Action.DEVICE_SOURCE_ROW) {
                    compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("device-row-public-device-15"))
                    compose.onNodeWithTag("device-row-public-device-15").assertIsDisplayed().performClick()
                    compose.onNodeWithTag("device-row-public-device-15").assert(SemanticsMatcher.expectValue(
                        androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Collapse details"))
                    compose.onNodeWithTag("device-expanded-public-device-15", useUnmergedTree = true).assertExists()
                    compose.onNodeWithTag("device-tool-public-device-15-codex", useUnmergedTree = true).assertExists()
                }
                probe.action(Action.DASHBOARD) {
                    select("trends")
                    compose.onNodeWithTag("trends-main-open-history").performScrollTo().performClick()
                    compose.onNodeWithTag("dashboard-tab-overview").assertIsSelected()
                    compose.onNodeWithTag("dashboard-card-tokens-value", true).assertTextEquals(
                        DashboardOverviewProjection.count(TODAY_TOKENS * 31, preferences.value.main.compactUnits,
                            DesktopCompactTokens.localeTag(Locale.ENGLISH)))
                    compose.onNodeWithTag("dashboard-tab-trends").performClick()
                    compose.onNodeWithTag("dashboard-chart").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithTag("trends-history-back").performClick()
                }
                var acceptedFreshnessRevision: String? = null
                probe.action(Action.CONTROLLED_FRESHNESS) {
                    val before = fixture.model.state.value.snapshot ?: error("Freshness has no complete source basis")
                    val parsesBefore = probe.completed(HubWorkStage.SNAPSHOT_PARSE)
                    val modelsBefore = probe.completed(HubWorkStage.MODEL_IDS)
                    val patchesBefore = probe.completed(HubWorkStage.SNAPSHOT_FRESHNESS)
                    fixture.relay.freshnessCredits.incrementAndGet()
                    await("Controlled complete-device freshness did not commit") {
                        fixture.model.state.value.snapshot?.updated != before.updated && fixture.model.state.value.live
                    }
                    val after = fixture.model.state.value.snapshot ?: error("Freshness dropped complete business data")
                    assertSame(before.periods, after.periods)
                    assertSame(before.limits, after.limits)
                    assertSame(before.historyPreview, after.historyPreview)
                    assertSame(before.observedModelIds, after.observedModelIds)
                    assertEquals(before.devices.size, after.devices.size)
                    before.devices.indices.forEach { index ->
                        assertSame(before.devices[index].periods, after.devices[index].periods)
                        assertSame(before.devices[index].periodDetails, after.devices[index].periodDetails)
                        assertSame(before.devices[index].health, after.devices[index].health)
                    }
                    assertEquals(0L, probe.completed(HubWorkStage.SNAPSHOT_PARSE) - parsesBefore)
                    assertEquals(1L, probe.completed(HubWorkStage.SNAPSHOT_FRESHNESS) - patchesBefore)
                    metrics.put("freshnessBusinessReferencesReused", true)
                        .put("controlledFreshnessFullSnapshotParses", 0)
                        .put("controlledFreshnessObservedIdsReferenceReused", true)
                        // MODEL_IDS also includes authenticated cache metadata validation after accept.
                        .put("controlledFreshnessModelIdStageCalls", probe.completed(HubWorkStage.MODEL_IDS) - modelsBefore)
                        .put("modelIdStageIncludesSaveValidation", true)
                        .put("controlledFreshnessTypedPatchCalls", 1)
                    verifySnapshot(after)
                    verifyHistory(fixture.model.state.value)
                    acceptedFreshnessRevision = fixture.model.state.value.snapshot?.updated
                }
                // Stop cancels pending saves by design. Wait for this test's accepted
                // freshness to be durably saved as a separately measured disk phase.
                probe.action(Action.CACHE_SETTLE) {
                    await("Accepted controlled freshness was not durably saved before cold restore") {
                        fixture.startedWrites.get() == fixture.finishedWrites.get() &&
                            fixture.lastCompletedSaveRevision.get() == acceptedFreshnessRevision
                    }
                }
                probe.action(Action.ACTIVITY_STOP) {
                    compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
                    fixture.awaitStreamClosed()
                    await("Accepted writer did not settle") { fixture.startedWrites.get() == fixture.finishedWrites.get() }
                    assertFalse(fixture.model.state.value.live)
                }
                val frozenRevision = fixture.model.state.value.snapshot?.updated
                val frozenHash = hash(fixture.file)
                val stoppedRequests = fixture.relay.restRequests.get()
                probe.action(Action.BACKGROUND_QUIET) {
                    Thread.sleep(550)
                    assertEquals(frozenRevision, fixture.model.state.value.snapshot?.updated)
                    assertEquals(frozenHash, hash(fixture.file)); assertEquals(stoppedRequests, fixture.relay.restRequests.get())
                }
                onMain { activity.setContent { Box {} }; activity.findViewById<ViewGroup>(android.R.id.content).removeAllViews() }
                fixture.dropModel()
                // A deliberately separate lifecycle transition, outside navigation/parse samples.
                // It releases the old Compose and VM model before the real cold restore.
                probe.action(Action.EXPLICIT_COLD_GC) { System.gc() }
                val coldRequests = fixture.relay.totalRequests.get()
                probe.action(Action.COLD_BACKGROUND_RESTORE) {
                    fixture.restoreBackgroundModel()
                    verifySnapshot(fixture.model.state.value.snapshot ?: error("Cold VM lost complete stats"))
                    verifyHistory(fixture.model.state.value)
                    assertEquals(frozenRevision, fixture.model.state.value.snapshot?.updated)
                    assertFalse(fixture.model.state.value.live)
                    assertEquals(coldRequests, fixture.relay.totalRequests.get()); assertEquals(frozenHash, hash(fixture.file))
                }
                metrics.put("sourceStatsDigestChecked", true).put("normalizedFullFrameDigestsChecked", fixture.relay.verifiedFullDigests.get())
                    .put("completeSessionsProjectsModelsAndPartsChecked", true).put("actualHubScreenNavigated", true)
                    .put("historyDailyRowsChecked", 31).put("historyMonthlyRowsChecked", 2).put("deviceHistoryRowsChecked", 16 * 31)
                    .put("coldVmStartedNetworkReads", false).put("cacheUnchangedByColdVmRestore", true)
                    .put("coldFreshnessRestoredAfterExplicitCacheSettle", true)
                    .put("completedCacheWrites", fixture.finishedWrites.get()).put("cipherBytes", fixture.file.length())
                    .put("liveHomeModuleCount", preferences.value.homeModules.size).put("normalMotionForcedByPreference", defaultHome)
            }
        }
    }

    private fun headline(tokens: Long) {
        val text = NumberFormat.getIntegerInstance(Locale.US).format(tokens)
        // Keep the production 800/1000 ms timelines intact, in the Compose test clock's
        // domain. A virtual 1100 ms advance also prevents the upward prefit endpoint from
        // being mistaken for a completed transition. This is not real motion cadence.
        compose.mainClock.advanceTimeBy(1100)
        compose.waitForIdle()
        compose.onNodeWithTag("hub-total-tokens", true).assertTextEquals(text)
    }

    private fun attachScreen(viewModel: HubViewModel, preferences: androidx.compose.runtime.MutableState<DisplayPreferences>, activity: MainActivity) = onMain {
        activity.setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
            DisposableEffect(lifecycle, viewModel) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> viewModel.setForeground(true)
                        Lifecycle.Event.ON_STOP -> viewModel.setForeground(false)
                        else -> Unit
                    }
                }
                lifecycle.addObserver(observer)
                viewModel.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                onDispose { lifecycle.removeObserver(observer) }
            }
            CompositionLocalProvider(LocalHubCalendarClock provides FIXED_CLOCK,
                LocalDesktopAnimationClock provides animationClock) {
                HubScreen(state, viewModel, preferences = preferences.value,
                    updateDisplay = { preferences.value = it.apply(preferences.value) })
            }
        }
    }

    private fun select(page: String) {
        compose.onNodeWithTag("hub-view-selector").performClick()
        compose.onNodeWithTag("view-$page").performScrollTo().performClick()
    }

    private inner class Fixture(burst: Boolean, blockSecondSave: Boolean = false) : AutoCloseable {
        val relay = Relay(burst)
        private val name = "test-capacity-vm-${UUID.randomUUID()}.bin"
        private val displayName = "test-capacity-display-${UUID.randomUUID()}.bin"
        val file = File(app.noBackupFilesDir, name)
        private val credential = UUID.randomUUID().toString()
        val expected = control("expected")
        val startedWrites = AtomicInteger()
        val finishedWrites = AtomicInteger()
        val lastCompletedSaveRevision = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val saveEntered = CountDownLatch(1)
        val saveRelease = CountDownLatch(1)
        val saveFinished = CountDownLatch(1)
        val revisions = Collections.synchronizedList(mutableListOf<String>())
        private var current: HubViewModel? = null
        val model get() = current ?: error("Public fixture model is absent")
        private var owner = ViewModelStore()
        private var observer = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val store = object : HubStore(app, name) {
            override fun save(record: SavedHub) {
                val number = startedWrites.incrementAndGet()
                val blocked = blockSecondSave && number == 2
                try {
                    if (blocked) {
                        saveEntered.countDown()
                        check(saveRelease.await(180, TimeUnit.SECONDS)) { "Public cache gate expired" }
                    }
                    super.save(record)
                    lastCompletedSaveRevision.set(ROOT_UPDATED.find(record.snapshot.take(256))?.groupValues?.get(1))
                } finally {
                    finishedWrites.incrementAndGet()
                    if (blocked) saveFinished.countDown()
                }
            }
        }
        private val client = HubViewModel.defaultClient().newBuilder()
            .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            .readTimeout(90, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).build()

        init { restoreBackgroundModel() }
        fun restoreBackgroundModel() {
            assertNull(current)
            onMain {
                current = HubViewModel(app, store, client, DisplayPreferenceStore(app, displayName))
                owner.put("capacity", model)
                observer.launch { model.state.collect { value ->
                    value.snapshot?.updated?.let { updated ->
                        synchronized(revisions) {
                            if (revisions.lastOrNull() != updated) {
                                check(revisions.size < 64) { "Public fixture revision observer bound exceeded" }
                                revisions += updated
                            }
                        }
                    }
                } }
            }
            await("Cold public VM did not finish cache initialization") { !model.state.value.loading }
        }
        fun distinctRevisions(): List<String> = synchronized(revisions) { revisions.toList().distinct() }
        fun connect() = onMain {
            model.setForeground(true)
            model.connect(relay.address, credential, "")
        }
        fun verifyOriginalStatsPayload() {
            val measurement = relay.completedPayloads["stats"] ?: error("Original public stats transfer did not complete")
            assertEquals(21_667_533L, measurement.first)
            assertEquals(expected.getValue("publicPayloadDigests").jsonObject.getValue("statsSha256").jsonPrimitive.content, measurement.second)
            assertEquals(0, relay.badMethods.get())
        }
        fun awaitStreamClosed() {
            await("Loopback HTTPS relay stayed active after backgrounding", 30_000) { relay.activeStreams.get() == 0 }
            await("Source Hub stream stayed active after backgrounding", 30_000) { control("status").getValue("activeStreams").jsonPrimitive.int == 0 }
        }
        fun dropModel() {
            observer.cancel()
            onMain { current?.setForeground(false); owner.clear(); current = null }
            owner = ViewModelStore()
            observer = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        }
        override fun close() {
            saveRelease.countDown()
            dropModel()
            relay.close()
            await("Public capacity cache writer outlived cleanup", 30_000) { startedWrites.get() == finishedWrites.get() }
            client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown()
            store.clear()
            File(app.noBackupFilesDir, displayName).delete()
        }
    }

    /**
     * No request headers are forwarded or recorded. Large source frames stream in 8 KiB
     * chunks. Only their first 256 public metadata bytes and <=64 KiB freshness are held.
     */
    private inner class Relay(private val burst: Boolean) : AutoCloseable {
        private val server = serverTls.sslContext().serverSocketFactory.createServerSocket(0, 20, InetAddress.getByName("127.0.0.1"))
        val address = "https://127.0.0.1:${server.localPort}"
        val activeStreams = AtomicInteger(); val totalRequests = AtomicInteger(); val restRequests = AtomicInteger(); val badMethods = AtomicInteger()
        val completeFullFrames = AtomicInteger(); val verifiedFullDigests = AtomicInteger(); val freshnessCredits = AtomicInteger()
        val completedPayloads = ConcurrentHashMap<String, Pair<Long, String>>()
        private val sockets = ConcurrentLinkedQueue<Socket>(); private val calls = ConcurrentLinkedQueue<Call>(); private val workers = ConcurrentLinkedQueue<Thread>()
        private val upstream = OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS).callTimeout(0, TimeUnit.SECONDS).build()
        private val sourceEventBytes = control("expected").getValue("sizes").jsonObject.getValue("sseEventUtf8Bytes").jsonPrimitive.long
        private val sourceEventDigest = control("expected").getValue("publicPayloadDigests").jsonObject.getValue("sseSha256").jsonPrimitive.content
        private val accepting = thread(name = "public-capacity-phase-tls-accept", isDaemon = true) {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: IOException) { break }
                check(workers.size < 64) { "Public relay worker record bound exceeded" }
                sockets += socket
                workers += thread(name = "public-capacity-phase-tls-relay", isDaemon = true) {
                    var streaming = false; var call: Call? = null
                    try {
                        socket.use {
                            socket.soTimeout = 15000
                            val input = socket.getInputStream().bufferedReader()
                            val fields = input.readLine().orEmpty().split(' ')
                            if (fields.firstOrNull() != "GET") { badMethods.incrementAndGet(); return@use }
                            val path = fields.getOrNull(1).orEmpty()
                            while (!input.readLine().isNullOrEmpty()) { /* Discard all credentials and headers. */ }
                            if (!path.startsWith("/api/")) return@use
                            totalRequests.incrementAndGet()
                            val resource = if (path == "/api/stats/stream") if (burst) "sse-burst" else "sse" else path.removePrefix("/api/")
                            streaming = resource.startsWith("sse")
                            if (streaming) activeStreams.incrementAndGet() else restRequests.incrementAndGet()
                            call = upstream.newCall(Request.Builder().url("$base/$resource").build()); calls += call!!
                            call!!.execute().use { response ->
                                val output = socket.getOutputStream().buffered(8192)
                                val body = response.body; val length = body.contentLength()
                                output.write(("HTTP/1.1 ${response.code} Public\r\nContent-Type: ${if (streaming) "text/event-stream" else "application/json"}\r\n" +
                                    (if (length >= 0) "Content-Length: $length\r\n" else "") + "Connection: close\r\n\r\n").toByteArray())
                                output.flush()
                                var bytes = 0L; val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(8192)
                                body.byteStream().use { source ->
                                    if (streaming) {
                                        val repeats = if (burst) 4 else 1
                                        repeat(repeats) { frame ->
                                            var remaining = sourceEventBytes
                                            val prefix = ByteArray(256); var prefixCount = 0
                                            val normalized = MessageDigest.getInstance("SHA-256")
                                            while (remaining > 0) {
                                                val read = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                                if (read < 0) throw IOException("Public source full frame was truncated")
                                                if (read == 0) continue
                                                output.write(buffer, 0, read); output.flush()
                                                val toPrefix = minOf(read, prefix.size - prefixCount)
                                                if (toPrefix > 0) { buffer.copyInto(prefix, prefixCount, 0, toPrefix); prefixCount += toPrefix }
                                                if (prefixCount == prefix.size && toPrefix > 0) {
                                                    val original = prefix.toString(Charsets.UTF_8)
                                                    val match = ROOT_UPDATED.find(original) ?: error("Public source frame root timestamp missing")
                                                    val timestamp = if (burst) "2026-10-09T08:00:0${frame + 1}.000Z" else FIXED_ISO
                                                    check(match.groupValues[1] == timestamp)
                                                    val restored = original.replaceRange(match.groups[1]!!.range, FIXED_ISO).toByteArray(Charsets.UTF_8)
                                                    check(restored.size == prefix.size); normalized.update(restored)
                                                }
                                                if (read > toPrefix) normalized.update(buffer, toPrefix, read - toPrefix)
                                                remaining -= read
                                            }
                                            check(prefixCount == prefix.size)
                                            check(hex(normalized.digest()) == sourceEventDigest) { "Public source full frame digest changed beyond root freshness" }
                                            verifiedFullDigests.incrementAndGet(); completeFullFrames.incrementAndGet()
                                        }
                                        val tiny = ByteArrayOutputStream(4096)
                                        var previous = -1
                                        while (true) {
                                            val read = source.read(buffer)
                                            if (read < 0) break
                                            for (index in 0 until read) {
                                                val value = buffer[index].toInt() and 255
                                                check(tiny.size() < 64 * 1024) { "Held public freshness event exceeded its tiny bound" }
                                                tiny.write(value)
                                                if (previous == 10 && value == 10) {
                                                    if (takeCredit(freshnessCredits)) tiny.writeTo(output)
                                                    else output.write(HELD_COMMENT)
                                                    // A harmless protocol comment keeps the held connection
                                                    // observable, so a canceled peer closes its upstream too.
                                                    output.flush()
                                                    tiny.reset()
                                                }
                                                previous = value
                                            }
                                        }
                                    } else while (true) {
                                        val read = source.read(buffer); if (read < 0) break; if (read == 0) continue
                                        output.write(buffer, 0, read); bytes += read; digest.update(buffer, 0, read)
                                    }
                                }
                                output.flush()
                                if (!streaming) completedPayloads[resource] = bytes to hex(digest.digest())
                            }
                        }
                    } catch (_: IOException) { /* Foreground cancellation closes the loopback relay and upstream. */ }
                    finally { call?.cancel(); call?.let(calls::remove); if (streaming) activeStreams.decrementAndGet(); sockets.remove(socket) }
                }
            }
        }
        override fun close() {
            server.close(); sockets.forEach { runCatching { it.close() } }; calls.forEach(Call::cancel)
            accepting.join(1000); workers.forEach { it.join(1000) }
            upstream.connectionPool.evictAll(); upstream.dispatcher.executorService.shutdown()
        }
    }

    private fun control(resource: String): JsonObject = CONTROL.newCall(Request.Builder().url("$base/$resource").build()).execute().use { response ->
        assertTrue("Public source control endpoint failed", response.isSuccessful)
        Json.parseToJsonElement(readHubText(HubCheckedInputStream(response.body.byteStream(), 128L * 1024))).jsonObject
    }

    private fun verifySnapshot(snapshot: Snapshot) {
        assertEquals(16, snapshot.devices.size)
        val models = (0 until 16).flatMap { device -> (0 until 20).map { model -> "public-d%02d-m%02d".format(Locale.ROOT, device, model) } }
        assertEquals(320, snapshot.observedModelIds.size)
        assertTrue("Observed source model identities changed", models.toSet() == snapshot.observedModelIds.toSet())
        assertTrue("Source device identities changed", snapshot.devices.map { it.id }.toSet() == (0 until 16).map { "public-device-%02d".format(Locale.ROOT, it) }.toSet())
        assertFalse(snapshot.historyRevision.isNullOrBlank()); assertFalse(snapshot.deviceHistoryRevision.isNullOrBlank())
        assertFalse(snapshot.detailIncomplete)
        for ((name, multiplier) in listOf("today" to 1L, "month" to 9L, "allTime" to 31L)) {
            val period = snapshot.periods.getValue(name)
            assertEquals(TODAY_TOKENS * multiplier, period.tokens)
            assertEquals(0, BigDecimal("75").multiply(multiplier.toBigDecimal()).compareTo(BigDecimal(period.cost!!)))
            assertEquals(4800, period.sessions.size); assertEquals(4800, period.projects.size)
            assertEquals(320, period.models.size); assertEquals(320, period.modelDetails.size)
            assertEquals(4800L * 17 * multiplier, period.unpriced)
            assertTrue(period.exact)
            assertEquals(4800L * 16 * multiplier, period.components.cacheRead)
            assertEquals(4800L * 8 * multiplier, period.components.cacheWrite)
            assertEquals(4800L * 7 * multiplier, period.components.output)
            assertEquals(0L, period.components.unclassified)
            // Desktop tokenComponentBreakdown includes cache writes inside cache miss.
            // Only cache reads and output are removed from classified total input.
            assertEquals(TODAY_TOKENS * multiplier - 4800L * 23 * multiplier, period.components.inputMiss)
            models.forEach { model ->
                assertEquals(15L * SESSION_TOKENS * multiplier, period.models.getValue(model))
                assertMoney(BigDecimal("0.234375").multiply(multiplier.toBigDecimal()), period.modelDetails.getValue(model).cost)
            }
            assertEquals(0L, period.sessionsOmitted); assertFalse(period.projectsIncomplete)
            val sessions = HashSet<String>(4800)
            period.sessions.forEach { row ->
                val id = row.session?.sessionId ?: error("Source session metadata was lost")
                assertTrue(sessions.add(id)); assertTrue(SESSION_ID.matches(id))
                assertEquals(SESSION_TOKENS * multiplier, row.tokens)
                assertEquals(17L * multiplier, row.unpriced)
                assertMoney(BigDecimal("0.015625").multiply(multiplier.toBigDecimal()), row.cost)
                val device = id.substring(8, 10).toInt(); val index = id.substring(12).toInt()
                val expectedModel = "public-d%02d-m%02d".format(Locale.ROOT, device, index % 20)
                assertTrue("Source session model attribution changed", row.session!!.models == mapOf(expectedModel to SESSION_TOKENS * multiplier))
                assertEquals("codex", row.tool)
                assertEquals(multiplier.toDouble(), row.session!!.accounting.calls, 0.0)
                assertEquals(((SESSION_TOKENS - 31) * multiplier).toDouble(), row.session!!.accounting.input, 0.0)
                assertEquals(16.0 * multiplier, row.session!!.accounting.cacheRead, 0.0)
                assertEquals(8.0 * multiplier, row.session!!.accounting.cacheWrite, 0.0)
                assertEquals(7.0 * multiplier, row.session!!.accounting.output, 0.0)
            }
            val projects = HashSet<String>(4800)
            period.projects.forEach { row ->
                val id = row.project?.label?.removePrefix("Public project ") ?: error("Source project metadata was lost")
                assertTrue(projects.add(id)); assertTrue(SESSION_ID.matches(id))
                assertEquals(SESSION_TOKENS * multiplier, row.tokens)
                assertEquals(row.tokens, row.project?.clients?.get("codex"))
                assertEquals(17L * multiplier, row.unpriced)
                assertMoney(BigDecimal("0.015625").multiply(multiplier.toBigDecimal()), row.cost)
            }
            assertEquals(sessions, projects)
            assertEquals(TODAY_TOKENS * multiplier, period.models.values.sum())
            snapshot.devices.forEach { device ->
                val usage = device.periods.getValue(name)
                assertEquals(300L * SESSION_TOKENS * multiplier, usage.tokens)
                assertMoney(BigDecimal("4.6875").multiply(multiplier.toBigDecimal()), usage.cost)
                assertEquals(300L * 17 * multiplier, usage.unpriced)
                assertEquals(300L * SESSION_TOKENS * multiplier, usage.tools.getValue("codex"))
                assertEquals(20, usage.clientModels.getValue("codex").size)
                usage.clientModels.getValue("codex").values.forEach { assertEquals(15L * SESSION_TOKENS * multiplier, it) }
                if (name == "today") {
                    assertEquals("2026-10-09", usage.window); assertEquals("Etc/UTC", usage.zone)
                    assertEquals("2026-10-10T00:00:00.000Z", usage.ends)
                }
                if (name == "today") {
                    assertEquals(setOf("today"), device.periodDetails.keys)
                    assertTrue(device.periodDetails.getValue(name).sessions.isEmpty())
                    assertTrue(device.periodDetails.getValue(name).projects.isEmpty())
                }
            }
        }
    }

    private fun assertMoney(expected: BigDecimal, actual: String?) {
        assertNotNull("Exact source money was lost", actual)
        assertEquals(0, expected.compareTo(BigDecimal(actual!!)))
    }

    private fun verifyHistory(state: HubState) {
        val history = state.history ?: error("Cold ViewModel lost the complete source history")
        val dates = (0 until 31).map { java.time.LocalDate.parse("2026-10-09").minusDays(it.toLong()).toString() }
        assertEquals(31, history.daily.size); assertEquals(2, history.monthly.size)
        assertEquals(dates, history.daily.map { it.date })
        val expectedDeviceIds = (0 until 16).associate { "public-device-%02d".format(Locale.ROOT, it) to it }
        fun modelsFor(device: Int) = (0 until 20).map { "public-d%02d-m%02d".format(Locale.ROOT, device, it) }.toSet()
        val allModels = (0 until 16).flatMap { modelsFor(it) }.toSet()
        fun row(row: HistoryRow, devices: Int, multiplier: Long, expectedModels: Set<String>) {
            assertEquals(devices * 300L * SESSION_TOKENS * multiplier, row.tokens)
            assertMoney(BigDecimal("4.6875").multiply(devices.toBigDecimal()).multiply(multiplier.toBigDecimal()), row.cost)
            assertEquals(devices * 300L * 17L * multiplier, row.unpricedTokens)
            assertEquals(row.tokens, row.perClient.getValue("codex").tokens)
            assertMoney(BigDecimal(row.cost!!), row.perClient.getValue("codex").cost)
            assertEquals(devices * 20, row.perModel.size)
            assertTrue("History model identity set changed", expectedModels == row.perModel.keys)
            row.perModel.values.forEach { value ->
                assertEquals(15L * SESSION_TOKENS * multiplier, value.tokens)
                assertMoney(BigDecimal("0.234375").multiply(multiplier.toBigDecimal()), value.cost)
            }
        }
        history.daily.forEach { row(it, 16, 1, allModels) }
        assertEquals(listOf("2026-10", "2026-09"), history.monthly.map { it.date })
        history.monthly.forEach { row(it, 16, if (it.date == "2026-10") 9 else 22, allModels) }
        assertEquals(TODAY_TOKENS * 31, history.summary?.totalTokens)
        assertMoney(BigDecimal("2325"), history.summary?.totalCost)
        assertEquals(4800L * 17 * 31, history.summary?.unpricedTokens)
        assertEquals(31L, history.summary?.activeDays); assertEquals(4800L * 31, history.summary?.messages)
        val devices = state.deviceHistories ?: error("Cold ViewModel lost per-device source history")
        assertEquals(16, devices.devices.size)
        assertTrue("Device history identities changed", expectedDeviceIds.keys == devices.devices.map { it.id }.toSet())
        assertEquals(FixedPeriods.signature(state.snapshot ?: error("Missing snapshot for device history signature")), devices.signature)
        devices.devices.forEach { device ->
            assertTrue(device.available); assertEquals(dates, device.history?.daily?.map { it.date })
            device.history!!.daily.forEach { row(it, 1, 1, modelsFor(expectedDeviceIds.getValue(device.id))) }
        }
        assertNotNull(state.modelNames)
    }

    private fun report(filename: String, scenario: String, work: (JSONObject, PhaseProbe) -> Unit) {
        assertFalse("A previous diagnostic lease survived", HubWorkTrace.isActive)
        assertEquals(41, HubWorkTrace.trace(HubWorkStage.CACHE_LOAD) { 41 })
        // Diagnostic callbacks cannot throw through an otherwise successful Hub operation.
        HubWorkTrace.observe { throw IllegalStateException("Public observer test failure") }.use {
            assertEquals(42, HubWorkTrace.trace(HubWorkStage.CACHE_LOAD) { 42 })
        }
        val started = SystemClock.elapsedRealtime()
        deadline = started + 180_000
        val metrics = JSONObject().put("schemaVersion", 1).put("shape", SHAPE).put("scenario", scenario).put("runIndex", runIndex)
            .put("fixtureReduced", false).put("physicalDeviceMeasured", false).put("testUsesLargeHeap", false)
            .put("independentProcessRepeatProvidedByCaller", true).put("observerFailureIsolationChecked", true)
            .put("composeTestClockAutoAdvance", compose.mainClock.autoAdvance)
            .put("normalMotionClockDomain", "Compose test mainClock; production durations, virtual frames")
            .put("headlineSettlingAdvanceVirtualMs", 1100).put("heldFreshnessUsesFixedProtocolComment", true)
            .put("initialHttpStatePublishHasMainAcceptSpan", false)
            .put("sessionQueryControlAvailable", false).put("sessionQueryUiMeasured", false)
        val probe = PhaseProbe()
        var passed = false
        val lease = HubWorkTrace.observe(probe)
        probe.start()
        try {
            require(port in 1..65535); require(runIndex in 0..1000)
            assertEquals("Phase baseline requires the actual 192 MiB heap", 192L * 1024 * 1024, Runtime.getRuntime().maxMemory())
            work(metrics, probe)
            assertTrue("The full scenario exceeded its 180 second budget", SystemClock.elapsedRealtime() <= deadline)
            passed = true
        } finally {
            probe.close(); lease.close()
            val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
            metrics.put("passed", passed).put("elapsedMs", SystemClock.elapsedRealtime() - started)
                .put("javaHeapMaxBytes", Runtime.getRuntime().maxMemory()).put("finalTotalPssKiB", memory.totalPss)
                .put("phaseProbe", probe.json())
                .put("measurementScope", "Instrumented 192 MiB emulator; coarse nested work spans, 10 ms Java/native samples, 100 ms Main queue heartbeat, Choreographer callback scheduling gaps, approximate ART aggregate GC statistics, and normal motion through virtual Compose frames. No render FPS, real animation cadence, exact GC pause, physical phone, or public Lucky TLS measurement.")
            File(app.getExternalFilesDir(null), filename).writeText(metrics.toString(2))
            instrumentation.sendStatus(2, Bundle().apply {
                putString("nativeCapacityStage", "phase50/$scenario/run$runIndex/complete")
                putBoolean("nativeCapacityPassed", passed)
            })
            deadline = 0L
        }
    }

    private enum class Action {
        IDLE, HOME_ATTACH, SOURCE_CONNECT_AND_FULL_BURST, STABLE_HOME, HISTORY_READ,
        MODEL_NAMES_READ, DEVICE_HISTORY_READ, FULL_BURST_WITH_HOME, PERIOD_ALL_TIME,
        PERIOD_MONTH, PERIOD_TODAY, SESSIONS_FIRST_PAGE, SESSION_NEXT_PAGE,
        PROJECTS_PREPARE, PROJECT_SOURCE_ROW, DEVICES_PREPARE, DEVICE_SOURCE_ROW,
        DASHBOARD, CONTROLLED_FRESHNESS, CACHE_SETTLE, ACTIVITY_STOP, BACKGROUND_QUIET,
        EXPLICIT_COLD_GC, COLD_BACKGROUND_RESTORE
    }

    /** All storage is bounded independently of frame count and per-row alias calls. */
    private class PhaseProbe : HubWorkTrace.Observer, AutoCloseable {
        private val origin = System.nanoTime()
        private val anchorNanoBefore = System.nanoTime()
        private val anchorElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        private val anchorNanoAfter = System.nanoTime()
        private val running = AtomicBoolean()
        private val action = java.util.concurrent.atomic.AtomicReference(Action.IDLE)
        private val handler = Handler(Looper.getMainLooper())
        private var sampler: Thread? = null
        private var dueNanos = 0L
        private var choreographer: Choreographer? = null
        private var previousFrame = 0L
        private var previousDelivery = 0L
        private val lock = Any()
        private val stages = EnumMap<HubWorkStage, StageStats>(HubWorkStage::class.java)
        private val recent = ArrayDeque<TaggedSpan>()
        private val longest = PriorityQueue<TaggedSpan>(compareBy { it.span.durationNanos })
        private val actions = mutableListOf<ActionSpan>()
        private val actionSamples = Array(Action.entries.size) { ActionSamples() }
        private val javaPeak = AtomicLong(); private val nativePeak = AtomicLong()
        private val javaPeakAt = AtomicLong(); private val nativePeakAt = AtomicLong()
        private val totalSamples = AtomicInteger()
        private val firstGc = runtimeStats()
        private var finalGc = firstGc
        private var frameCallbacks = 0L
        private val frameGaps = DurationHistogram()
        private val deliveryGaps = DurationHistogram()
        private val pulse = object : Runnable {
            override fun run() {
                if (!running.get()) return
                val now = System.nanoTime()
                val sample = actionSamples[action.get().ordinal]
                sample.mainDelay.accumulateAndGet((now - dueNanos).coerceAtLeast(0), ::maxOf)
                sample.heartbeats.incrementAndGet()
                dueNanos = now + 100_000_000L
                handler.postDelayed(this, 100)
            }
        }
        private val frame = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!running.get()) return
                val now = System.nanoTime()
                val active = action.get()
                val sample = actionSamples[active.ordinal]
                sample.frames.incrementAndGet()
                sample.firstFrameAt.compareAndSet(0L, now)
                synchronized(lock) {
                    frameCallbacks++
                    if (previousFrame > 0) frameGaps.add((frameTimeNanos - previousFrame).coerceAtLeast(0))
                    if (previousDelivery > 0) deliveryGaps.add((now - previousDelivery).coerceAtLeast(0))
                }
                if (previousDelivery > 0) sample.frameGap.accumulateAndGet((now - previousDelivery).coerceAtLeast(0), ::maxOf)
                previousFrame = frameTimeNanos; previousDelivery = now
                choreographer?.postFrameCallback(this)
            }
        }

        override fun completed(span: HubWorkSpan) {
            val tagged = TaggedSpan(span, action.get())
            synchronized(lock) {
                stages.getOrPut(span.stage) { StageStats() }.add(span)
                if (recent.size == RECENT_CAP) recent.removeFirst()
                recent.addLast(tagged)
                if (longest.size < LONGEST_CAP) longest.add(tagged)
                else {
                    if (span.durationNanos > longest.peek().span.durationNanos) { longest.poll(); longest.add(tagged) }
                }
            }
        }
        fun completed(stage: HubWorkStage): Long = synchronized(lock) { stages[stage]?.count ?: 0L }

        fun <T> action(value: Action, work: () -> T): T {
            check(action.compareAndSet(Action.IDLE, value)) { "Overlapping public action measurements" }
            val start = System.nanoTime(); val before = runtimeStats()
            var passed = false
            try { val result = work(); passed = true; return result }
            finally {
                val end = System.nanoTime(); val after = runtimeStats()
                synchronized(lock) {
                    check(actions.size < Action.entries.size * 2) { "Public action record bound exceeded" }
                    actions += ActionSpan(value, start, end, passed, before, after)
                }
                action.set(Action.IDLE)
            }
        }

        fun start() {
            running.set(true); dueNanos = System.nanoTime(); handler.post(pulse)
            handler.post {
                if (running.get()) { choreographer = Choreographer.getInstance(); choreographer?.postFrameCallback(frame) }
            }
            sampler = thread(name = "public-capacity-phase-memory", isDaemon = true) {
                while (running.get()) {
                    val now = System.nanoTime(); val runtime = Runtime.getRuntime()
                    // These two Runtime reads are not an atomic heap snapshot.
                    val java = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0)
                    val native = Debug.getNativeHeapAllocatedSize().coerceAtLeast(0)
                    if (updatePeak(javaPeak, java)) javaPeakAt.set(now)
                    if (updatePeak(nativePeak, native)) nativePeakAt.set(now)
                    val sample = actionSamples[action.get().ordinal]
                    sample.javaPeak.accumulateAndGet(java, ::maxOf); sample.nativePeak.accumulateAndGet(native, ::maxOf)
                    sample.memorySamples.incrementAndGet(); totalSamples.incrementAndGet()
                    try { Thread.sleep(10) } catch (_: InterruptedException) { break }
                }
            }
        }
        override fun close() {
            running.set(false); handler.removeCallbacks(pulse)
            handler.post { choreographer?.removeFrameCallback(frame) }
            sampler?.interrupt(); sampler?.join(1000)
            finalGc = runtimeStats()
        }
        fun json(): JSONObject = synchronized(lock) {
            val stageJson = JSONObject()
            stages.forEach { (key, value) -> stageJson.put(key.name, value.json()) }
            val actionJson = JSONArray()
            actions.forEach { span ->
                val samples = actionSamples[span.action.ordinal]
                actionJson.put(JSONObject().put("action", span.action.name).put("startNanos", span.start - origin)
                    .put("endNanos", span.end - origin).put("durationNanos", span.end - span.start).put("success", span.passed)
                    .put("gcCounterDelta", statsDelta(span.beforeGc, span.afterGc)).put("memorySampleCount", samples.memorySamples.get())
                    .put("sampledJavaUsedPeakBytes", samples.javaPeak.get()).put("sampledNativeAllocatedPeakBytes", samples.nativePeak.get())
                    .put("mainHeartbeatCount", samples.heartbeats.get()).put("largestMainHeartbeatDelayNanos", samples.mainDelay.get())
                    .put("choreographerCallbackCount", samples.frames.get()).put("largestCallbackDeliveryGapNanos", samples.frameGap.get())
                    .put("actionStartToFirstCallbackNanos", samples.firstFrameAt.get().takeIf { it > 0 }?.let { (it - span.start).coerceAtLeast(0) } ?: JSONObject.NULL))
            }
            fun spans(values: Collection<TaggedSpan>) = JSONArray().apply { values.forEach { value ->
                val span = value.span
                put(JSONObject().put("id", span.id).put("stage", span.stage.name).put("units", span.units)
                    .put("startNanos", span.startNanos - origin).put("endNanos", span.endNanos - origin)
                    .put("durationNanos", span.durationNanos).put("success", span.success).put("onMain", span.onMain)
                    .put("endedOnMain", span.endedOnMain).put("actionAtCompletion", value.action.name))
            } }
            JSONObject().put("monotonicClock", "System.nanoTime relative to probe origin").put("sampleIntervalMs", 10)
                .put("probeOriginSystemNanoTime", origin)
                .put("clockAnchorSystemNanoBefore", anchorNanoBefore).put("clockAnchorSystemNanoAfter", anchorNanoAfter)
                .put("clockAnchorElapsedRealtimeNanos", anchorElapsedRealtimeNanos)
                .put("clockAnchorSequentialReadUncertaintyNanos", anchorNanoAfter - anchorNanoBefore)
                .put("memorySampleCount", totalSamples.get()).put("sampledJavaUsedPeakBytes", javaPeak.get())
                .put("sampledNativeAllocatedPeakBytes", nativePeak.get()).put("javaPeakAtNanos", javaPeakAt.get() - origin)
                .put("nativePeakAtNanos", nativePeakAt.get() - origin).put("gcCounterDelta", statsDelta(firstGc, finalGc))
                .put("mainHeartbeatCount", actionSamples.sumOf { it.heartbeats.get().toLong() })
                .put("largestMainHeartbeatDelayNanos", actionSamples.maxOf { it.mainDelay.get() })
                .put("gcStatisticsApproximate", true).put("blockingGcTimeIsAggregateRunDurationNotPause", true)
                .put("stageAggregates", stageJson).put("actions", actionJson).put("recentSpanCap", RECENT_CAP)
                .put("longestSpanCap", LONGEST_CAP).put("recentCompletedSpans", spans(recent.toList()))
                .put("longestCompletedSpans", spans(longest.sortedByDescending { it.span.durationNanos }))
                .put("choreographerCallbackCount", frameCallbacks).put("choreographerFrameTimestampGaps", frameGaps.json())
                .put("choreographerCallbackDeliveryGaps", deliveryGaps.json())
                .put("sseReadFrameScope", "First event byte through delimiter; includes subsequent source-read waits and ignored events, excludes first read wait and projection/callback")
                .put("nestedStageDurationsAreNotAdditive", true).put("spanRecordsDoNotMeasureObjectLiveness", true)
                .put("actionStartsIncludeTestInteractionAndWaits", true).put("callbackGapIsNotRenderedFrameRate", true)
        }

        private data class TaggedSpan(val span: HubWorkSpan, val action: Action)
        private data class ActionSpan(val action: Action, val start: Long, val end: Long, val passed: Boolean,
            val beforeGc: Map<String, Long>, val afterGc: Map<String, Long>)
        private class ActionSamples {
            val javaPeak = AtomicLong(); val nativePeak = AtomicLong(); val memorySamples = AtomicInteger()
            val mainDelay = AtomicLong(); val heartbeats = AtomicInteger(); val frames = AtomicInteger()
            val frameGap = AtomicLong(); val firstFrameAt = AtomicLong()
        }
        private class StageStats {
            var count = 0L; private var failures = 0L; private var mainCount = 0L; private var mainDuration = 0L
            private var units = 0L; private var maxUnits = 0L; private val durations = DurationHistogram()
            fun add(span: HubWorkSpan) {
                count++; if (!span.success) failures++
                if (span.onMain) { mainCount++; mainDuration += span.durationNanos }
                units += span.units; maxUnits = maxOf(maxUnits, span.units); durations.add(span.durationNanos)
            }
            fun json() = JSONObject().put("count", count).put("failureCount", failures).put("startedOnMainCount", mainCount)
                .put("startedOnMainDurationNanos", mainDuration).put("inputUnitsSum", units).put("maximumInputUnits", maxUnits)
                .put("duration", durations.json())
        }
        private class DurationHistogram {
            private val bins = LongArray(64)
            private var count = 0L; private var sum = 0L; private var max = 0L
            fun add(value: Long) {
                val nanos = value.coerceAtLeast(0L)
                val index = if (nanos == 0L) 0 else 64 - java.lang.Long.numberOfLeadingZeros(nanos)
                bins[index.coerceAtMost(63)]++; count++; sum += nanos; max = maxOf(max, nanos)
            }
            private fun upperBound(percent: Int): Any {
                if (count == 0L) return JSONObject.NULL
                val wanted = (count * percent + 99) / 100; var seen = 0L
                for (index in bins.indices) {
                    seen += bins[index]
                    if (seen >= wanted) return if (index == 63) Long.MAX_VALUE else (1L shl index) - 1
                }
                return max
            }
            fun json() = JSONObject().put("count", count).put("sumNanos", sum).put("maximumNanos", max)
                .put("p50HistogramUpperBoundNanos", upperBound(50)).put("p95HistogramUpperBoundNanos", upperBound(95))
                .put("powerOfTwoHistogram", JSONArray(bins.toList()))
        }
        companion object {
            private const val RECENT_CAP = 256
            private const val LONGEST_CAP = 64
            // Debug.getRuntimeStats values outside this documented numeric whitelist are discarded.
            private val GC_KEYS = listOf("art.gc.gc-count", "art.gc.gc-time", "art.gc.blocking-gc-count",
                "art.gc.blocking-gc-time", "art.gc.bytes-allocated", "art.gc.bytes-freed")
            private fun runtimeStats(): Map<String, Long> = runCatching {
                val raw = Debug.getRuntimeStats()
                GC_KEYS.mapNotNull { key -> raw[key]?.toLongOrNull()?.takeIf { it >= 0 }?.let { key to it } }.toMap()
            }.getOrDefault(emptyMap())
            private fun statsDelta(before: Map<String, Long>, after: Map<String, Long>) = JSONObject().apply {
                GC_KEYS.forEach { key -> if (before.containsKey(key) && after.containsKey(key)) put(key, (after.getValue(key) - before.getValue(key)).coerceAtLeast(0)) }
            }
            private fun updatePeak(peak: AtomicLong, value: Long): Boolean {
                while (true) { val old = peak.get(); if (value <= old) return false; if (peak.compareAndSet(old, value)) return true }
            }
        }
    }

    private fun <T> onMain(work: () -> T): T {
        var value: T? = null
        instrumentation.runOnMainSync { value = work() }
        @Suppress("UNCHECKED_CAST") return value as T
    }
    private fun await(message: String, timeoutMs: Long = 180_000, condition: () -> Boolean) {
        val end = minOf(SystemClock.elapsedRealtime() + timeoutMs, deadline.takeIf { it > 0 } ?: Long.MAX_VALUE)
        while (!condition() && SystemClock.elapsedRealtime() < end) Thread.sleep(50)
        assertTrue(message, condition())
    }
    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { source ->
            val buffer = ByteArray(8192)
            while (true) { val read = source.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val FIXED_ISO = "2026-10-09T08:00:00.000Z"
        private const val BURST_FINAL = "2026-10-09T08:00:04.000Z"
        private val HELD_COMMENT = ": public phase hold\n\n".toByteArray(Charsets.UTF_8)
        private val ROOT_UPDATED = Regex("\"updatedAt\":\"([^\"]+)\"")
        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
        private fun takeCredit(value: AtomicInteger): Boolean {
            while (true) { val before = value.get(); if (before <= 0) return false; if (value.compareAndSet(before, before - 1)) return true }
        }
        private const val SHAPE = "transport-16-devices"
        private const val SESSION_TOKENS = 5_000_000_123L
        private const val TODAY_TOKENS = 24_000_000_590_400L
        private val SESSION_ID = Regex("public-d(?:0[0-9]|1[0-5])-s(?:[0-2][0-9]{2})")
        private val FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"), ZoneId.of("UTC"))
        private val certificate by lazy { HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build() }
        private val serverTls by lazy { HandshakeCertificates.Builder().heldCertificate(certificate).build() }
        private val clientTls by lazy { HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build() }
        private val CONTROL = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    }
}
