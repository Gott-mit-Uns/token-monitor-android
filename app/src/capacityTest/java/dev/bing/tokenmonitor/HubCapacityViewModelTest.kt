package dev.bing.tokenmonitor

import android.app.Application
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
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
import java.util.Collections
import java.util.Locale
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
 * Opt-in emulator acceptance of the production ViewModel and screen, using the unmodified
 * large public Hub source data. TLS is a loopback test relay; it is not a Lucky certificate test.
 */
class HubCapacityViewModelTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application
    private val port get() = InstrumentationRegistry.getArguments().getString("capacityPort")?.toIntOrNull() ?: 18748
    private val base get() = "http://127.0.0.1:$port/capacity/$SHAPE"

    @Test fun fullSourceViewModelScreenNavigationBackgroundAndColdRestore() = report("capacity-viewmodel-ui-public.json") { metrics ->
        // The Compose rule obtains its Activity through an active scenario callback. Keep
        // the native reference before ON_STOP for disposal and the later background checks.
        val activity = compose.activity
        Fixture(burst = false).use { fixture ->
            metrics.put("originalStatsUtf8Bytes", fixture.expected.getValue("sizes").jsonObject.getValue("statsUtf8Bytes").jsonPrimitive.long)
            fixture.connect()
            await("Complete large Hub did not become live") { fixture.model.state.value.live && !fixture.model.state.value.loading }
            verifySnapshot(fixture.model.state.value.snapshot ?: error("Missing large source snapshot"))
            assertTrue("History read must enter the real foreground queue", onMain { fixture.model.loadHistory(true) })
            await("Complete source history did not reach the ViewModel") {
                !fixture.model.state.value.loading && fixture.model.state.value.history?.daily?.size == 31
            }
            onMain { fixture.model.loadModelNames() }
            await("Shared model names did not finish") { !fixture.model.state.value.loading && fixture.model.state.value.modelNames != null }
            onMain { fixture.model.loadDeviceHistory() }
            await("All source device histories did not finish") {
                !fixture.model.state.value.loading && fixture.model.state.value.deviceHistories?.devices?.size == 16
            }
            val frozenHash = exerciseActualScreen(fixture, metrics, activity)
            fixture.dropModel()
            System.gc()
            val coldRequests = fixture.relay.totalRequests.get()
            val coldStart = SystemClock.elapsedRealtime()
            fixture.restoreBackgroundModel()
            verifySnapshot(fixture.model.state.value.snapshot ?: error("Cold VM did not restore full stats"))
            verifyHistory(fixture.model.state.value)
            assertFalse(fixture.model.state.value.live)
            assertEquals(coldRequests, fixture.relay.totalRequests.get())
            assertEquals(frozenHash, hash(fixture.file))
            metrics.put("coldBackgroundVmRestoreMs", SystemClock.elapsedRealtime() - coldStart)
                .put("coldVmStartedNetworkReads", false).put("cacheUnchangedByColdVmRestore", true)
                .put("sourceStatsDigestChecked", true).put("completeSessionsProjectsAndModelsChecked", true)
                .put("deviceHistoryRowsChecked", 16 * 31).put("historyDailyRowsChecked", 31).put("historyMonthlyRowsChecked", 2)
                .put("completedCacheWrites", fixture.finishedWrites.get()).put("cipherBytes", fixture.file.length())
            onMain { activity.setContent { Box {} } }
        }
    }

    private fun exerciseActualScreen(fixture: Fixture, metrics: JSONObject, activity: MainActivity): String {
        val preferences = mutableStateOf(DisplayPreferences(language = LanguageChoice.ENGLISH, homeModules = emptyList(),
            appearance = AppearancePreferences(reduceMotion = "on")))
        val viewModel = fixture.model
        onMain {
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
                CompositionLocalProvider(LocalHubCalendarClock provides FIXED_CLOCK) {
                    HubScreen(state, viewModel, preferences = preferences.value,
                        updateDisplay = { preferences.value = it.apply(preferences.value) })
                }
            }
    }
        compose.waitForIdle()
        val timings = JSONObject()
        fun timed(name: String, work: () -> Unit) {
            val start = SystemClock.elapsedRealtime(); work(); timings.put(name, SystemClock.elapsedRealtime() - start)
    }
        fun headline(tokens: Long) = compose.onNodeWithTag("hub-total-tokens", true)
            .assertTextEquals(NumberFormat.getIntegerInstance(Locale.US).format(tokens))
        headline(TODAY_TOKENS)
        timed("allTimePeriodMs") {
            compose.onNodeWithTag("hub-period-allTime").performClick()
            headline(TODAY_TOKENS * 31)
    }
        timed("monthPeriodMs") {
            compose.onNodeWithTag("hub-period-month").performClick()
            headline(TODAY_TOKENS * 9)
    }
        compose.onNodeWithTag("hub-period-today").performClick()
        timed("sessionsAndPageChangeMs") {
            select("sessions")
            compose.onNodeWithTag("session-pager").assertExists()
            compose.onNodeWithTag("session-page-status").assertTextEquals("1–100 of 4800")
            compose.onNodeWithTag("session-page-next").performClick()
            compose.onNodeWithTag("session-page-status").assertTextEquals("101–200 of 4800")
            headline(TODAY_TOKENS)
    }
        timed("projectsAndSourceRowMs") {
            select("projects")
            val row = "project-row-public project public-d00-s000"
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(row))
            compose.onNodeWithTag(row).assertIsDisplayed().performClick()
            compose.onNodeWithTag("project-tokens-public project public-d00-s000", true)
                .assertTextEquals("5,000,000,123")
            headline(TODAY_TOKENS)
    }
        timed("devicesAndLastSourceRowMs") {
            select("devices")
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("device-row-public-device-15"))
            compose.onNodeWithTag("device-row-public-device-15").assertIsDisplayed().performClick()
            headline(TODAY_TOKENS)
    }
        timed("trendsAndDashboardMs") {
            select("trends")
            compose.onNodeWithTag("trends-main-open-history").performScrollTo().performClick()
            compose.onNodeWithTag("dashboard-tab-overview").assertIsSelected()
            compose.onNodeWithTag("dashboard-card-tokens-value", true).assertExists()
            compose.onNodeWithTag("dashboard-tab-trends").performClick()
            compose.onNodeWithTag("dashboard-chart").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("trends-history-back").performClick()
    }
        metrics.put("navigationMs", timings).put("actualHubScreenNavigated", true)
        verifySnapshot(fixture.model.state.value.snapshot ?: error("Large screen lost its source snapshot"))
        verifyHistory(fixture.model.state.value)
        fixture.verifyOriginalStatsPayload()
        val beforeBackgroundRequests = fixture.relay.restRequests.get()
        val pauseStart = SystemClock.elapsedRealtime()
        // This delivers real activity ON_STOP through the same adapter used above.
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        fixture.awaitStreamClosed()
        metrics.put("activityStopToSourceStreamClosedMs", SystemClock.elapsedRealtime() - pauseStart)
        await("Accepted cache write did not settle") { fixture.startedWrites.get() == fixture.finishedWrites.get() }
        val frozenUpdated = fixture.model.state.value.snapshot?.updated
        val frozenHash = hash(fixture.file)
        Thread.sleep(1_100)
        assertFalse(fixture.model.state.value.live)
        assertEquals(frozenUpdated, fixture.model.state.value.snapshot?.updated)
        assertEquals(frozenHash, hash(fixture.file))
        assertEquals(beforeBackgroundRequests, fixture.relay.restRequests.get())
        // Remove the Compose collector before dropping the old model, so cold restore does
        // not artificially retain a second live Snapshot in this 192 MiB process.
        onMain {
            // Detaching the old ComposeView disposes its collector even while the activity
            // is stopped, instead of leaving a suspended old Snapshot during cold restore.
            activity.setContent { Box {} }
            activity.findViewById<ViewGroup>(android.R.id.content).removeAllViews()
        }
        return frozenHash
    }


    @Test fun fullSourceBurstCommitsWhileSaveIsBlockedAndBackgroundFencesQueuedWork() = report("capacity-viewmodel-burst-public.json") { metrics ->
        Fixture(burst = true, blockSecondSave = true).use { fixture ->
            fixture.connect()
            assertTrue("The first large SSE save did not enter the public test gate", fixture.saveEntered.await(180, TimeUnit.SECONDS))
            val committedAtGate = fixture.revisions.size
            await("A blocked disk save stopped later large SSE commits") {
                fixture.distinctRevisions().size >= 5 && fixture.model.state.value.live
            }
            verifySnapshot(fixture.model.state.value.snapshot ?: error("Large burst lost native data"))
            assertEquals("A second disk save started while the first was blocked", 2, fixture.startedWrites.get())
            assertEquals(1, fixture.finishedWrites.get())
            val pausedRevision = fixture.model.state.value.snapshot?.updated
            val pauseStart = SystemClock.elapsedRealtime()
            onMain { fixture.model.setForeground(false) }
            fixture.awaitStreamClosed()
            metrics.put("foregroundFalseToSourceStreamClosedMs", SystemClock.elapsedRealtime() - pauseStart)
            fixture.saveRelease.countDown()
            assertTrue("The already-started cache write did not finish", fixture.saveFinished.await(120, TimeUnit.SECONDS))
            await("Started cache writes did not settle") { fixture.startedWrites.get() == fixture.finishedWrites.get() }
            val settledHash = hash(fixture.file)
            val settledWrites = fixture.startedWrites.get()
            Thread.sleep(1_100)
            assertEquals(pausedRevision, fixture.model.state.value.snapshot?.updated)
            assertFalse(fixture.model.state.value.live)
            assertEquals("Queued cache work started after foreground cancellation", settledWrites, fixture.startedWrites.get())
            assertEquals(2, settledWrites)
            assertEquals(settledHash, hash(fixture.file))
            val revisions = fixture.distinctRevisions()
            assertTrue("Full burst revisions were not ordered", revisions.zipWithNext().all { (a, b) -> Instant.parse(a) <= Instant.parse(b) })
            metrics.put("distinctCommittedPublicRevisions", revisions.size).put("commitsObservedAtSaveGate", committedAtGate)
                .put("startedCacheWrites", settledWrites).put("completedCacheWrites", fixture.finishedWrites.get())
                .put("commitsContinuedWhileCacheBlocked", true).put("queuedWritesFencedAfterBackground", true)
                .put("lateSnapshotCommitAfterBackground", false).put("settledCiphertextChangedDuringQuietPeriod", false)
                .put("cacheGateScope", "One already-started accepted write may finish; newer queued saves are canceled")
                .put("cipherBytes", fixture.file.length()).put("completeSessionsProjectsAndModelsChecked", true)
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
                        synchronized(revisions) { if (revisions.lastOrNull() != updated) revisions += updated }
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

    /** No request header is forwarded or recorded; upstream is the public source-only loopback server. */
    private inner class Relay(private val burst: Boolean) : AutoCloseable {
        private val server = serverTls.sslContext().serverSocketFactory.createServerSocket(0, 20, InetAddress.getByName("127.0.0.1"))
        val address = "https://127.0.0.1:${server.localPort}"
        val activeStreams = AtomicInteger()
        val totalRequests = AtomicInteger()
        val restRequests = AtomicInteger()
        val badMethods = AtomicInteger()
        val completedPayloads = ConcurrentHashMap<String, Pair<Long, String>>()
        private val sockets = ConcurrentLinkedQueue<Socket>()
        private val calls = ConcurrentLinkedQueue<Call>()
        private val workers = ConcurrentLinkedQueue<Thread>()
        private val upstream = OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS).callTimeout(0, TimeUnit.SECONDS).build()
        private val accepting = thread(name = "public-capacity-tls-accept", isDaemon = true) {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: IOException) { break }
                sockets += socket
                workers += thread(name = "public-capacity-tls-relay", isDaemon = true) {
                    var streaming = false
                    var call: Call? = null
                    try {
                        socket.use {
                            socket.soTimeout = 15000
                            val input = socket.getInputStream().bufferedReader()
                            val request = input.readLine().orEmpty()
                            val fields = request.split(' ')
                            if (fields.firstOrNull() != "GET") { badMethods.incrementAndGet(); return@use }
                            val path = fields.getOrNull(1).orEmpty()
                            while (!input.readLine().isNullOrEmpty()) { /* Discard credentials and all other headers. */ }
                            if (!path.startsWith("/api/")) return@use
                            totalRequests.incrementAndGet()
                            val resource = if (path == "/api/stats/stream") if (burst) "sse-burst" else "sse" else path.removePrefix("/api/")
                            streaming = resource.startsWith("sse")
                            if (streaming) activeStreams.incrementAndGet() else restRequests.incrementAndGet()
                            call = upstream.newCall(Request.Builder().url("$base/$resource").build())
                            calls += call!!
                            call!!.execute().use { response ->
                                val output = socket.getOutputStream().buffered(8192)
                                val body = response.body
                                val length = body.contentLength()
                                val type = if (streaming) "text/event-stream" else "application/json"
                                output.write(("HTTP/1.1 ${response.code} Public\r\nContent-Type: $type\r\n" +
                                    (if (length >= 0) "Content-Length: $length\r\n" else "") + "Connection: close\r\n\r\n").toByteArray())
                                output.flush()
                                var bytes = 0L
                                val digest = MessageDigest.getInstance("SHA-256")
                                val buffer = ByteArray(8192)
                                body.byteStream().use { source ->
                                    while (true) {
                                        val read = source.read(buffer)
                                        if (read < 0) break
                                        if (read == 0) continue
                                        output.write(buffer, 0, read)
                                        if (streaming) output.flush()
                                        if (!streaming) { bytes += read; digest.update(buffer, 0, read) }
                                    }
                                }
                                output.flush()
                                if (!streaming) completedPayloads[resource] = bytes to digest.digest().joinToString("") { "%02x".format(it) }
                            }
                        }
                    } catch (_: IOException) { /* Production cancellation closes the local relay and upstream response. */ }
                    finally {
                        call?.cancel(); call?.let(calls::remove)
                        if (streaming) activeStreams.decrementAndGet()
                        sockets.remove(socket)
                    }
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
        assertEquals(320, snapshot.observedModelIds.size)
        assertFalse(snapshot.detailIncomplete)
        for ((name, multiplier) in listOf("today" to 1L, "month" to 9L, "allTime" to 31L)) {
            val period = snapshot.periods.getValue(name)
            assertEquals(TODAY_TOKENS * multiplier, period.tokens)
            assertEquals(0, BigDecimal("75").multiply(multiplier.toBigDecimal()).compareTo(BigDecimal(period.cost!!)))
            assertEquals(4800, period.sessions.size); assertEquals(4800, period.projects.size)
            assertEquals(320, period.models.size); assertEquals(320, period.modelDetails.size)
            assertEquals(0L, period.sessionsOmitted); assertFalse(period.projectsIncomplete)
            val sessions = HashSet<String>(4800)
            period.sessions.forEach { row ->
                val id = row.session?.sessionId ?: error("Source session metadata was lost")
                assertTrue(sessions.add(id)); assertTrue(SESSION_ID.matches(id))
                assertEquals(SESSION_TOKENS * multiplier, row.tokens)
                assertEquals(17L * multiplier, row.unpriced)
                assertEquals(0, BigDecimal("0.015625").multiply(multiplier.toBigDecimal()).compareTo(BigDecimal(row.cost!!)))
            }
            val projects = HashSet<String>(4800)
            period.projects.forEach { row ->
                val id = row.project?.label?.removePrefix("Public project ") ?: error("Source project metadata was lost")
                assertTrue(projects.add(id)); assertTrue(SESSION_ID.matches(id))
                assertEquals(SESSION_TOKENS * multiplier, row.tokens)
                assertEquals(row.tokens, row.project?.clients?.get("codex"))
            }
            assertEquals(sessions, projects)
            assertEquals(TODAY_TOKENS * multiplier, period.models.values.sum())
            snapshot.devices.forEach { device ->
                assertEquals(300L * SESSION_TOKENS * multiplier, device.periods.getValue(name).tokens)
                if (name == "today") {
                    assertEquals(setOf("today"), device.periodDetails.keys)
                    assertTrue(device.periodDetails.getValue(name).sessions.isEmpty())
                    assertTrue(device.periodDetails.getValue(name).projects.isEmpty())
                }
            }
        }
    }

    private fun verifyHistory(state: HubState) {
        val history = state.history ?: error("Cold ViewModel lost the complete source history")
        assertEquals(31, history.daily.size); assertEquals(2, history.monthly.size)
        history.daily.forEach { row ->
            assertEquals(TODAY_TOKENS, row.tokens); assertEquals(320, row.perModel.size)
            assertEquals(TODAY_TOKENS, row.perModel.values.sumOf { it.tokens ?: 0 })
        }
        assertEquals(TODAY_TOKENS * 31, history.summary?.totalTokens)
        val devices = state.deviceHistories ?: error("Cold ViewModel lost per-device source history")
        assertEquals(16, devices.devices.size)
        devices.devices.forEach { device ->
            assertTrue(device.available)
            assertEquals(31, device.history?.daily?.size)
        }
        assertNotNull(state.modelNames)
    }

    private fun report(filename: String, work: (JSONObject) -> Unit) {
        val started = SystemClock.elapsedRealtime()
        val report = JSONObject().put("schemaVersion", 1).put("shape", SHAPE)
            .put("fixtureReduced", false).put("physicalDeviceMeasured", false).put("testUsesLargeHeap", false)
        val samples = MemorySamples()
        var passed = false
        samples.start()
        try {
            require(port in 1..65535)
            assertEquals("This acceptance requires the actual 192 MiB heap", 192L * 1024 * 1024, Runtime.getRuntime().maxMemory())
            work(report)
            passed = true
        } finally {
            samples.close()
            val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
            report.put("passed", passed).put("elapsedMs", SystemClock.elapsedRealtime() - started)
                .put("javaHeapMaxBytes", Runtime.getRuntime().maxMemory()).put("sampledJavaUsedPeakBytes", samples.javaPeak.get())
                .put("sampledNativeAllocatedPeakBytes", samples.nativePeak.get()).put("sampleIntervalMs", 10)
                .put("finalTotalPssKiB", memory.totalPss).put("mainHeartbeatSamples", samples.heartbeats.get())
                .put("largestMainHeartbeatDelayMs", samples.mainDelay.get())
                .put("measurementScope", "192 MiB emulator Java/native allocation samples and main queue heartbeat; no frame-rate, phone, or public Lucky TLS measurement")
            File(app.getExternalFilesDir(null), filename).writeText(report.toString(2))
        }
    }

    private class MemorySamples : AutoCloseable {
        val javaPeak = AtomicLong(); val nativePeak = AtomicLong(); val mainDelay = AtomicLong(); val heartbeats = AtomicInteger()
        private val running = AtomicBoolean()
        private var sampler: Thread? = null
        private val handler = Handler(Looper.getMainLooper())
        private var due = 0L
        private val pulse = object : Runnable {
            override fun run() {
                if (!running.get()) return
                val now = SystemClock.elapsedRealtime()
                mainDelay.accumulateAndGet((now - due).coerceAtLeast(0), ::maxOf)
                heartbeats.incrementAndGet(); due = now + 100; handler.postDelayed(this, 100)
            }
        }
        fun start() {
            running.set(true); due = SystemClock.elapsedRealtime(); handler.post(pulse)
            sampler = thread(name = "public-capacity-memory", isDaemon = true) {
                while (running.get()) {
                    val runtime = Runtime.getRuntime()
                    javaPeak.accumulateAndGet(runtime.totalMemory() - runtime.freeMemory(), ::maxOf)
                    nativePeak.accumulateAndGet(Debug.getNativeHeapAllocatedSize(), ::maxOf)
                    try { Thread.sleep(10) } catch (_: InterruptedException) { break }
                }
            }
        }
        override fun close() { running.set(false); handler.removeCallbacks(pulse); sampler?.interrupt(); sampler?.join(1000) }
    }

    private fun <T> onMain(work: () -> T): T {
        var value: T? = null
        instrumentation.runOnMainSync { value = work() }
        @Suppress("UNCHECKED_CAST") return value as T
    }
    private fun await(message: String, timeoutMs: Long = 180_000, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeoutMs
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
