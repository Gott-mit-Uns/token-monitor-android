package dev.bing.tokenmonitor

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Real ViewModel, HTTPS/SSE and authenticated atomic store, using loopback-only public data. */
class HistoryReadReceiptModelTest {
    companion object {
        private val certificate by lazy { HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build() }
        private val serverTls by lazy { HandshakeCertificates.Builder().heldCertificate(certificate).build() }
        private val clientTls by lazy { HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build() }
        private fun history(tokens: Long = 10) = """{"daily":[{"date":"2026-10-09","tokens":$tokens,"cost":0.125}],"monthly":[{"month":"2026-10","tokens":$tokens,"cost":0.125}]}"""
        private const val EMPTY = """{"daily":[],"monthly":[]}"""
        private fun stats(revision: String? = "public-r1", preview: Boolean = true, tokens: Long = 10, updated: String = "first"): String {
            val revisionField = revision?.let { "\"historyRevision\":\"$it\"," }.orEmpty()
            val previewField = if (preview) ",\"historyPreview\":${history(tokens)}" else ""
            return """{$revisionField"updatedAt":"$updated","periods":{"today":{"totalTokens":$tokens}},"devices":[]$previewField}"""
        }
        private fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
        private fun await(message: String, condition: () -> Boolean) {
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
            while (!condition() && System.nanoTime() < end) Thread.sleep(10)
            assertTrue(message, condition())
        }
    }

    private class Reply(val body: String, val code: Int = 200, held: Boolean = false) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(if (held) 1 else 0)
        val finished = CountDownLatch(1)
        fun awaitEntered() = assertTrue("Public reply did not enter", entered.await(5, TimeUnit.SECONDS))
    }

    private class SaveGate(val fail: Boolean) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
    }

    /** Each peer binds only 127.0.0.1; headers and generated credentials never enter diagnostics. */
    private class Peer(initialStats: String, blockedStream: Reply? = null) : AutoCloseable {
        private val server = serverTls.sslContext().serverSocketFactory.createServerSocket(0, 20, InetAddress.getByName("127.0.0.1"))
        val address = "https://127.0.0.1:${server.localPort}"
        val statsBody = AtomicReference(initialStats)
        val statsStatus = AtomicInteger(200)
        val historyBody = AtomicReference(history(99))
        val historyStatus = AtomicInteger(200)
        val historyReads = AtomicInteger()
        val statsReads = AtomicInteger()
        val badMethods = AtomicInteger()
        val streamReads = AtomicInteger()
        private val streamReply = AtomicReference(blockedStream)
        private val statsReply = AtomicReference<Reply?>()
        private val historyReplies = ConcurrentLinkedQueue<Reply>()
        private val allReplies = ConcurrentLinkedQueue<Reply>()
        private val sockets = ConcurrentLinkedQueue<Socket>()
        private val workers = ConcurrentLinkedQueue<Thread>()
        private val streams = CopyOnWriteArrayList<OutputStream>()
        private val streamSockets = CopyOnWriteArrayList<Socket>()
        val hasOpenStream get() = streams.isNotEmpty()
        private val restActive = AtomicInteger()
        init { blockedStream?.let { allReplies += it } }
        private val accepting = thread(name = "public-history-accept", isDaemon = true) {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: IOException) { break }
                sockets += socket
                workers += thread(name = "public-history-response", isDaemon = true) {
                    var activeRest = false
                    var reply: Reply? = null
                    try {
                        socket.use {
                            socket.soTimeout = 15000
                            val input = socket.getInputStream().bufferedReader()
                            val requestLine = input.readLine().orEmpty()
                            if (!requestLine.startsWith("GET ")) badMethods.incrementAndGet()
                            val path = requestLine.split(' ').getOrNull(1).orEmpty()
                            while (!input.readLine().isNullOrEmpty()) { /* Discard every header. */ }
                            val output = socket.getOutputStream()
                            if (path == "/api/stats/stream") {
                                streamReads.incrementAndGet()
                                reply = streamReply.getAndSet(null)
                                if (reply == null) {
                                    output.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n".toByteArray())
                                    writeEvent(output, "snapshot", statsBody.get())
                                    streams += output
                                    streamSockets += socket
                                    try { input.read() } finally { streams -= output; streamSockets -= socket }
                                    return@use
                                }
                            } else {
                                restActive.incrementAndGet(); activeRest = true
                                reply = when (path) {
                                    "/api/health" -> Reply("""{"ok":true,"role":"hub"}""")
                                    "/api/stats" -> { statsReads.incrementAndGet(); statsReply.getAndSet(null) ?: Reply(statsBody.get(), statsStatus.get()) }
                                    "/api/history" -> { historyReads.incrementAndGet(); historyReplies.poll() ?: Reply(historyBody.get(), historyStatus.get()) }
                                    "/api/subscriptions" -> Reply("""{"subscriptions":[]}""")
                                    else -> Reply("{}", 404)
                                }
                            }
                            val selected = checkNotNull(reply)
                            selected.entered.countDown()
                            check(selected.release.await(12, TimeUnit.SECONDS)) { "Public response gate expired" }
                            val bytes = selected.body.toByteArray()
                            output.write("HTTP/1.1 ${selected.code} Public\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            output.write(bytes); output.flush()
                        }
                    } catch (_: IOException) { /* Client cancellation is an asserted path. */ }
                    finally {
                        if (activeRest) restActive.decrementAndGet()
                        reply?.finished?.countDown()
                    }
                }
            }
        }
        private fun writeEvent(output: OutputStream, name: String, body: String) = synchronized(output) {
            output.write("event: $name\ndata: {\"stats\":$body}\n\n".toByteArray()); output.flush()
        }
        fun push(body: String) {
            statsBody.set(body)
            assertTrue("Expected an open public SSE connection", streams.isNotEmpty())
            streams.forEach { output -> runCatching { writeEvent(output, "stats", body) } }
        }
        fun freshness() = streams.forEach { output ->
            runCatching { writeEvent(output, "freshness", """{"updatedAt":"later","devices":[]}""") }
        }
        fun holdHistory(body: String = history(99), code: Int = 200): Reply = Reply(body, code, true).also {
            historyReplies += it; allReplies += it
        }
        fun holdStats(code: Int = 200): Reply = Reply(statsBody.get(), code, true).also {
            statsReply.set(it); allReplies += it
        }
        fun holdNextStream(code: Int = 401): Reply = Reply("{}", code, true).also {
            streamReply.set(it); allReplies += it
        }
        fun closeStreams() = streamSockets.forEach { runCatching { it.close() } }
        fun awaitRestIdle() = await("Loopback history transport still active") { restActive.get() == 0 }
        override fun close() {
            allReplies.forEach { it.release.countDown() }
            server.close(); sockets.forEach { runCatching { it.close() } }
            accepting.join(1000); workers.forEach { it.join(1000) }
        }
    }

    private class Fixture(
        initialStats: String = stats(), initialHistory: String? = history(),
        metadata: String? = homeHistorySignature(HubParser.parse(initialStats)),
        blockedStream: Reply? = null, startForeground: Boolean = true
    ) : AutoCloseable {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val peer = Peer(initialStats, blockedStream)
        private val name = "test-history-receipt-${UUID.randomUUID()}.bin"
        val file = File(app.noBackupFilesDir, name)
        private val credential = UUID.randomUUID().toString()
        val failWrites = AtomicBoolean()
        val failedWrites = AtomicInteger()
        val saveCalls = AtomicInteger()
        val completedWrites = AtomicInteger()
        private val nextSaveGate = AtomicReference<SaveGate?>()
        private val saveGates = ConcurrentLinkedQueue<SaveGate>()
        val store = object : HubStore(app, name) {
            override fun save(record: SavedHub) {
                saveCalls.incrementAndGet()
                val gate = nextSaveGate.getAndSet(null)
                try {
                    if (gate != null) {
                        gate.entered.countDown()
                        check(gate.release.await(12, TimeUnit.SECONDS)) { "Public persistence gate expired" }
                    }
                    if (failWrites.get() || gate?.fail == true) {
                        failedWrites.incrementAndGet(); throw IOException("Public synthetic save failure")
                    }
                    super.save(record)
                } finally { completedWrites.incrementAndGet(); gate?.finished?.countDown() }
            }
        }
        private val owner = ViewModelStore()
        val client = HubViewModel.defaultClient().newBuilder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
        lateinit var model: HubViewModel
        init {
            store.save(SavedHub(peer.address, credential, initialStats, 1, history = initialHistory, historySignature = metadata))
            main { model = HubViewModel(app, store, client); owner.put("history-receipt", model) }
            await("Cold public cache did not load") { !model.state.value.loading && model.state.value.snapshot != null }
            if (startForeground) {
                main { model.setForeground(true) }
                if (blockedStream != null) {
                    blockedStream.awaitEntered()
                    await("Connection validation did not finish") { !model.state.value.loading && completedWrites.get() >= 2 }
                } else {
                    awaitReady()
                    await("Cold seed, stats and initial SSE saves did not all finish") { completedWrites.get() >= 3 }
                }
            }
        }
        val receipt get() = model.state.value.historyReadReceipt!!
        fun read(manual: Boolean): Boolean {
            var accepted = false
            main { accepted = model.loadHistory(manual) }
            return accepted
        }
        fun awaitReady() = await("Validated public Hub did not become live") {
            peer.hasOpenStream && model.state.value.live && !model.state.value.loading && store.load()?.savedAt == model.state.value.savedAt
        }
        fun holdNextSave(fail: Boolean = true): SaveGate = SaveGate(fail).also {
            nextSaveGate.set(it); saveGates += it
        }
        fun awaitPhase(phase: HistoryReadPhase) = await("Expected history receipt $phase") {
            !model.state.value.loading && model.state.value.historyReadReceipt?.phase == phase
        }
        fun awaitHistorySaved(body: String) = await("Accepted public history not atomically saved") { store.load()?.history == body }
        fun assertOld(body: String = history(), signature: String? = "public-r1") {
            assertEquals(HistoryParser.parse(body), model.state.value.history)
            assertEquals(body, store.load()!!.history)
            assertEquals(signature, store.load()!!.historySignature)
        }
        fun settleLateReply(reply: Reply) {
            reply.release.countDown()
            assertTrue("Held public reply did not finish", reply.finished.await(5, TimeUnit.SECONDS))
            peer.awaitRestIdle()
            await("Cancelled history call still active") { client.dispatcher.runningCalls().none { it.request().url.encodedPath == "/api/history" } }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            // Give the IO continuation a Main turn after its network call has retired.
            Thread.sleep(100)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        override fun close() {
            saveGates.forEach { it.release.countDown() }
            main { model.setForeground(false); owner.clear() }
            peer.close(); client.dispatcher.cancelAll(); client.connectionPool.evictAll(); store.clear()
            assertEquals("Every public request must remain read-only", 0, peer.badMethods.get())
        }
    }

    @Test fun manualFailureRetainsOldNonemptyDisplayButHasFailedReceiptAndCanRetry() {
        Fixture().use { f ->
            val cached = f.receipt
            assertEquals(HistoryReadPhase.CACHED, cached.phase)
            assertFalse(f.read(false))
            f.peer.historyStatus.set(503)
            assertTrue(f.read(true)); f.awaitPhase(HistoryReadPhase.FAILED)
            val failed = f.receipt
            assertEquals(1, failed.attempt); assertTrue(failed.cycleId > cached.cycleId)
            assertTrue(failed.previewHadDays); assertNull(failed.fetchedDays)
            assertFalse(failed.accepted); assertTrue(failed.allowsAutomaticRead)
            f.assertOld()
            assertTrue(f.read(false)); f.awaitPhase(HistoryReadPhase.FAILED)
            assertEquals(failed.cycleId, f.receipt.cycleId)
            assertEquals(2, f.receipt.attempt); assertEquals(2, f.peer.historyReads.get())
            assertNotEquals(failed.requestId, f.receipt.requestId)
            f.assertOld()
        }
    }

    @Test fun previewBackedEmptyReplyRetainsBodyAndSignatureUsingPreviewFrozenAtStart() {
        Fixture().use { f ->
            val render = f.model.state.value.historyRenderRevision
            val held = f.peer.holdHistory(EMPTY)
            assertTrue(f.read(true)); held.awaitEntered()
            val started = f.receipt
            assertEquals(HistoryReadPhase.STARTED, started.phase); assertTrue(started.previewHadDays)
            f.peer.push(stats(preview = false))
            await("Same-revision preview did not disappear") { f.model.state.value.snapshot?.historyPreview == null }
            assertEquals(started, f.receipt)
            held.release.countDown(); f.awaitPhase(HistoryReadPhase.RESOLVED)
            assertEquals(0, f.receipt.fetchedDays); assertTrue(f.receipt.previewHadDays)
            assertFalse(f.receipt.accepted); assertTrue(f.receipt.allowsAutomaticRead)
            assertEquals(render, f.model.state.value.historyRenderRevision)
            f.assertOld()
            assertTrue(f.read(false)); f.awaitPhase(HistoryReadPhase.RESOLVED)
            assertTrue(f.receipt.accepted); assertEquals(2, f.receipt.attempt)
            f.awaitHistorySaved(history(99))
        }
    }

    @Test fun emptyReplyWithoutInitialPreviewIsAcceptedEvenWhenPreviewAppearsDuringTheRead() {
        Fixture(initialStats = stats(preview = false)).use { f ->
            val held = f.peer.holdHistory(EMPTY)
            assertTrue(f.read(true)); held.awaitEntered()
            assertFalse(f.receipt.previewHadDays)
            f.peer.push(stats(preview = true))
            await("Same-revision preview did not appear") { f.model.state.value.snapshot?.historyPreview?.daily?.isNotEmpty() == true }
            held.release.countDown(); f.awaitPhase(HistoryReadPhase.RESOLVED)
            assertEquals(0, f.receipt.fetchedDays); assertFalse(f.receipt.previewHadDays)
            assertTrue(f.receipt.accepted); assertFalse(f.receipt.allowsAutomaticRead)
            assertTrue(f.model.state.value.history!!.daily.isEmpty())
            f.awaitHistorySaved(EMPTY)
            assertEquals("public-r1", f.store.load()!!.historySignature)
            assertFalse(f.read(false)); assertEquals(1, f.peer.historyReads.get())
        }
    }

    @Test fun networkSuccessWithSaveFailureRemainsResolvedAndPreservesOldDiskRecord() {
        Fixture().use { f ->
            val before = f.file.readBytes()
            f.failWrites.set(true)
            assertTrue(f.read(true))
            await("Successful network read did not expose local save failure") {
                f.model.state.value.error == R.string.error_details_save && f.failedWrites.get() > 0
            }
            f.awaitPhase(HistoryReadPhase.RESOLVED)
            assertTrue(f.receipt.accepted); assertEquals(1, f.receipt.fetchedDays)
            assertEquals(HistoryParser.parse(history(99)), f.model.state.value.history)
            assertTrue(before.contentEquals(f.file.readBytes()))
            assertEquals(history(), f.store.load()!!.history)
            assertEquals("public-r1", f.store.load()!!.historySignature)
            assertFalse(f.read(false)); assertEquals(1, f.peer.historyReads.get())
        }
    }

    @Test fun actualStartsStopAtFourAndRejectedLoadingOrBackgroundManualActionsCannotResetTheCycle() {
        Fixture().use { f ->
            f.peer.historyStatus.set(503)
            val held = f.peer.holdHistory("{}", 503)
            assertTrue(f.read(true)); held.awaitEntered()
            val first = f.receipt
            assertFalse(f.read(true)); assertFalse(f.read(false)); assertEquals(first, f.receipt)
            held.release.countDown(); f.awaitPhase(HistoryReadPhase.FAILED)
            repeat(3) { index ->
                assertTrue(f.read(false)); f.awaitPhase(HistoryReadPhase.FAILED)
                assertEquals(index + 2, f.receipt.attempt); assertEquals(first.cycleId, f.receipt.cycleId)
            }
            val exhausted = f.receipt
            assertFalse(f.read(false)); assertEquals(4, f.peer.historyReads.get())
            main { f.model.setForeground(false) }
            assertFalse(f.read(true)); assertEquals(exhausted, f.receipt)
            main { f.model.setForeground(true) }; f.awaitReady()
            assertEquals(exhausted, f.receipt); assertFalse(f.read(false))
            assertTrue(f.read(true)); f.awaitPhase(HistoryReadPhase.FAILED)
            assertEquals(1, f.receipt.attempt); assertTrue(f.receipt.cycleId > exhausted.cycleId)
            repeat(3) { assertTrue(f.read(false)); f.awaitPhase(HistoryReadPhase.FAILED) }
            assertFalse(f.read(false)); assertEquals(8, f.peer.historyReads.get())
        }
    }

    @Test fun sameTargetLifecycleCancellationWithoutPreviewKeepsAttemptAndCanResume() {
        Fixture(initialStats = stats(preview = false)).use { f ->
            val held = f.peer.holdHistory()
            assertTrue(f.read(true)); held.awaitEntered()
            val start = f.receipt
            main { f.model.setForeground(false) }
            f.awaitPhase(HistoryReadPhase.CANCELLED)
            assertEquals(start.cycleId, f.receipt.cycleId); assertEquals(1, f.receipt.attempt)
            assertFalse(f.receipt.previewHadDays); assertTrue(f.receipt.allowsAutomaticRead)
            assertFalse(f.read(true)); assertEquals(1, f.receipt.attempt)
            f.settleLateReply(held); f.assertOld()
            main { f.model.setForeground(true) }; f.awaitReady()
            assertEquals(start.cycleId, f.receipt.cycleId); assertEquals(1, f.receipt.attempt)
            assertTrue(f.read(false)); f.awaitPhase(HistoryReadPhase.RESOLVED)
            assertEquals(2, f.receipt.attempt); assertTrue(f.receipt.accepted)
            f.awaitHistorySaved(history(99))
        }
    }

    @Test fun sameKeyFreshnessStatsValidationAndOtherLoadingDoNotResetHistoryBudget() {
        Fixture().use { f ->
            f.peer.historyStatus.set(503)
            assertTrue(f.read(true)); f.awaitPhase(HistoryReadPhase.FAILED)
            val failed = f.receipt
            f.peer.freshness()
            await("Freshness frame not applied") { f.model.state.value.snapshot?.updated == "later" }
            assertEquals(failed, f.receipt)
            val statsGate = f.peer.holdStats()
            main { f.model.refreshSaved() }; statsGate.awaitEntered()
            assertFalse(f.read(true)); assertFalse(f.read(false)); assertEquals(failed, f.receipt)
            statsGate.release.countDown(); f.awaitReady()
            assertEquals(failed, f.receipt)
            main { f.model.editConnection(f.peer.address, "", null) }; f.awaitReady()
            assertEquals("Same logical Hub validation must preserve history target and cycle", failed, f.receipt)
            assertTrue(f.read(false)); f.awaitPhase(HistoryReadPhase.FAILED)
            assertEquals(2, f.receipt.attempt); assertEquals(failed.cycleId, f.receipt.cycleId)
        }
    }

    @Test fun forceHistoryQueuesOnlyAfterSuccessfulStatsAndNeverStartsHistoryOutsideTheRootQueue() {
        Fixture().use { f ->
            f.peer.historyStatus.set(503)
            assertTrue(f.read(true)); f.awaitPhase(HistoryReadPhase.FAILED)
            val failed = f.receipt
            val failure = f.peer.holdStats(503)
            main { f.model.refreshSaved(true) }; failure.awaitEntered()
            assertEquals(failed, f.receipt); assertFalse(f.read(true))
            failure.release.countDown()
            await("Failed stats refresh did not end") { !f.model.state.value.loading && f.model.state.value.error != null }
            assertEquals(failed, f.receipt)
            val success = f.peer.holdStats()
            main { f.model.refreshSaved(true) }; success.awaitEntered()
            assertEquals(failed, f.receipt)
            success.release.countDown(); f.awaitReady()
            val queued = f.receipt
            assertEquals(HistoryReadPhase.QUEUED, queued.phase); assertEquals(0, queued.attempt)
            assertNull(queued.requestId); assertTrue(queued.cycleId > failed.cycleId)
            assertEquals(1, f.peer.historyReads.get()); f.assertOld()
            assertTrue(f.read(false)); f.awaitPhase(HistoryReadPhase.FAILED)
            assertEquals(queued.cycleId, f.receipt.cycleId); assertEquals(1, f.receipt.attempt)
        }
    }

    @Test fun legacyEmptySignatureForceRefreshKeepsBindingRequestedAndCannotIntroducePolling() {
        Fixture(initialStats = stats(revision = null, preview = false), initialHistory = null, metadata = null).use { f ->
            f.peer.historyBody.set(EMPTY)
            assertTrue(f.read(false)); f.awaitPhase(HistoryReadPhase.RESOLVED)
            f.awaitHistorySaved(EMPTY)
            assertTrue(f.receipt.accepted); assertEquals("", f.receipt.key.signature)
            assertEquals("", f.store.load()!!.historySignature)
            main { f.model.refreshSaved(true) }; f.awaitReady()
            assertEquals(HistoryReadPhase.QUEUED, f.receipt.phase)
            assertEquals(0, f.receipt.attempt); assertTrue(f.receipt.bindingRequested)
            assertFalse(f.receipt.allowsAutomaticRead); assertFalse(f.read(false))
            assertEquals(1, f.peer.historyReads.get())
            assertTrue(f.read(true)); f.awaitPhase(HistoryReadPhase.RESOLVED)
            assertEquals(2, f.peer.historyReads.get()); assertTrue(f.receipt.accepted)
        }
    }

    @Test fun delayedSse401BlocksHeldHistoryWithoutApplyingOrPersistingItsLaterSuccess() {
        val streamAuth = Reply("{}", 401, true)
        Fixture(blockedStream = streamAuth).use { f ->
            val held = f.peer.holdHistory(history(777))
            assertTrue(f.read(true)); held.awaitEntered()
            val started = f.receipt
            val before = f.file.readBytes()
            streamAuth.release.countDown()
            await("SSE authorization failure did not stop the history request") {
                !f.model.state.value.live && f.model.state.value.historyReadReceipt?.phase == HistoryReadPhase.AUTH_BLOCKED
            }
            val blocked = f.receipt
            assertEquals(started.requestId, blocked.requestId); assertEquals(started.cycleId, blocked.cycleId)
            assertEquals(1, blocked.attempt); assertNull(blocked.fetchedDays)
            assertFalse(f.read(true)); assertFalse(f.read(false)); assertEquals(blocked, f.receipt)
            f.settleLateReply(held)
            assertEquals(blocked, f.receipt); assertTrue(before.contentEquals(f.file.readBytes())); f.assertOld()
            assertEquals(1, f.peer.historyReads.get())
            main { f.model.refreshSaved() }; f.awaitReady()
            assertEquals(started.cycleId, f.receipt.cycleId); assertEquals(1, f.receipt.attempt)
            assertTrue(f.read(false)); f.awaitPhase(HistoryReadPhase.RESOLVED)
            assertEquals(2, f.receipt.attempt); assertTrue(f.receipt.accepted)
        }
    }

    @Test fun changedRevisionAndLegacyPreviewRejectHeldReplyWithoutCompletingTheNewTarget() {
        for (revision in listOf("public-r1", null)) {
            val initial = stats(revision = revision)
            Fixture(initialStats = initial).use { f ->
                val held = f.peer.holdHistory(history(777))
                assertTrue(f.read(true)); held.awaitEntered()
                val started = f.receipt
                val nextStats = stats(revision = if (revision == null) null else "public-r2", tokens = 20)
                val expectedSignature = homeHistorySignature(HubParser.parse(nextStats))
                f.peer.push(nextStats)
                await("New public history target did not invalidate old display and metadata") {
                    f.model.state.value.history == null && f.store.load()?.history == null &&
                        f.store.load()?.historySignature == null && f.model.state.value.historyReadReceipt?.key?.signature == expectedSignature
                }
                val next = f.receipt
                assertTrue(next.cycleId > started.cycleId); assertEquals(0, next.attempt); assertNull(next.requestId)
                f.settleLateReply(held)
                assertEquals(next, f.receipt); assertNull(f.model.state.value.history)
                assertNull(f.store.load()!!.history); assertNull(f.store.load()!!.historySignature)
                assertTrue(f.read(false)); f.awaitPhase(HistoryReadPhase.RESOLVED)
                assertEquals(1, f.receipt.attempt); assertEquals(next.cycleId, f.receipt.cycleId)
                f.awaitHistorySaved(history(99)); assertEquals(expectedSignature, f.store.load()!!.historySignature)
            }
        }
    }

    @Test fun changedBindingRejectsCancelledOldReplyAndCannotRestoreItsHistoryOrSignature() {
        Fixture().use { f ->
            Peer(stats(revision = "public-other", tokens = 555)).use { next ->
                val held = f.peer.holdHistory(history(777))
                assertTrue(f.read(true)); held.awaitEntered()
                val started = f.receipt
                main { f.model.setForeground(false) }; f.awaitPhase(HistoryReadPhase.CANCELLED)
                main { f.model.setForeground(true) }; f.awaitReady()
                main { f.model.connect(next.address, UUID.randomUUID().toString(), "") }
                await("Second public binding was not committed") {
                    !f.model.state.value.loading && f.model.state.value.live && f.store.load()?.address == next.address
                }
                val rebound = f.receipt
                assertNotEquals(started.key.connectionRevision, rebound.key.connectionRevision)
                assertEquals("public-other", rebound.key.signature); assertEquals(0, rebound.attempt)
                assertNull(f.model.state.value.history); assertNull(f.store.load()!!.historySignature)
                f.settleLateReply(held)
                assertEquals(rebound, f.receipt); assertNull(f.model.state.value.history)
                assertEquals(next.address, f.store.load()!!.address)
                assertNull(f.store.load()!!.history); assertNull(f.store.load()!!.historySignature)
                assertEquals(0, next.badMethods.get())
            }
        }
    }

    @Test fun coldCacheNeedsMatchingSignatureWhileLegacyAndMismatchedMetadataAreDisplayOnly() {
        for (metadata in listOf("public-r1", null, "public-obsolete")) {
            Fixture(metadata = metadata, startForeground = false).use { f ->
                f.assertOld(signature = metadata)
                if (metadata == "public-r1") {
                    assertEquals(HistoryReadPhase.CACHED, f.receipt.phase)
                    assertTrue(f.receipt.accepted); assertEquals(1, f.receipt.fetchedDays)
                    assertNull(f.receipt.requestId); assertEquals(0, f.receipt.attempt)
                } else {
                    assertEquals(HistoryReadPhase.QUEUED, f.receipt.phase)
                    assertFalse(f.receipt.accepted); assertTrue(f.receipt.allowsAutomaticRead)
                }
                main { f.model.setForeground(true) }; f.awaitReady()
                assertEquals(metadata != "public-r1", f.read(false))
                if (metadata != "public-r1") {
                    f.awaitPhase(HistoryReadPhase.RESOLVED); f.awaitHistorySaved(history(99))
                    assertEquals("public-r1", f.store.load()!!.historySignature)
                }
            }
        }
    }

    @Test fun coldExplicitEmptySignatureAndEmptyHistoryAreTrustedButAbsentHistoryIsNot() {
        val noPreview = stats(revision = null, preview = false)
        Fixture(initialStats = noPreview, initialHistory = EMPTY, metadata = "", startForeground = false).use { f ->
            assertEquals(HistoryReadPhase.CACHED, f.receipt.phase)
            assertTrue(f.receipt.accepted); assertEquals(0, f.receipt.fetchedDays)
            assertEquals("", f.store.load()!!.historySignature)
        }
        Fixture(initialHistory = null, metadata = "public-r1", startForeground = false).use { f ->
            assertEquals(HistoryReadPhase.QUEUED, f.receipt.phase)
            assertFalse(f.receipt.accepted); assertNull(f.model.state.value.history)
        }
    }

    @Test fun queuedSnapshotAndAcceptedHistorySavesCannotCrossSseAuthenticationFailure() {
        for (acceptHistory in listOf(false, true)) Fixture().use { f ->
            val before = f.file.readBytes()
            val callsBefore = f.saveCalls.get()
            val saveGate = f.holdNextSave()
            f.peer.push(stats(updated = "holding-mutex"))
            assertTrue("Public save did not hold the persistence mutex", saveGate.entered.await(5, TimeUnit.SECONDS))
            f.peer.push(stats(updated = "queued-after-mutex"))
            await("Second SSE snapshot did not queue behind persistence") {
                f.model.state.value.snapshot?.updated == "queued-after-mutex"
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val streamAuth = f.peer.holdNextStream()
            f.peer.closeStreams()
            streamAuth.awaitEntered()
            if (acceptHistory) {
                assertTrue(f.read(true))
                await("History did not reach accepted network result before waiting for storage") {
                    f.model.state.value.history == HistoryParser.parse(history(99)) &&
                        f.model.state.value.historyReadReceipt?.phase == HistoryReadPhase.RESOLVED && f.model.state.value.loading
                }
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            streamAuth.release.countDown()
            await("SSE authentication failure did not end the serial wait") {
                !f.model.state.value.loading && !f.model.state.value.live && f.model.state.value.error == R.string.error_stream_auth
            }
            assertFalse(f.read(true)); assertFalse(f.read(false))
            saveGate.release.countDown()
            assertTrue("Public held save did not retire", saveGate.finished.await(5, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(250)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals("Queued snapshot/history saves must be fenced before entering the store", callsBefore + 1, f.saveCalls.get())
            assertTrue(before.contentEquals(f.file.readBytes()))
            assertEquals(history(), f.store.load()!!.history)
            assertEquals("public-r1", f.store.load()!!.historySignature)
            assertEquals("Save completion cannot overwrite the authentication error", R.string.error_stream_auth, f.model.state.value.error)
            assertEquals(if (acceptHistory) 1 else 0, f.peer.historyReads.get())
        }
    }

    @Test fun ordinaryEofReconnectKeepsAcceptedFinalSnapshotQueuedBehindSlowSave() {
        Fixture().use { f ->
            val callsBefore = f.saveCalls.get()
            val saveGate = f.holdNextSave(fail = false)
            f.peer.push(stats(tokens = 20, updated = "slow-save-in-flight"))
            assertTrue("Public slow save did not enter", saveGate.entered.await(5, TimeUnit.SECONDS))
            f.peer.push(stats(tokens = 30, updated = "final-accepted"))
            await("Final public snapshot was not accepted before EOF") {
                f.model.state.value.snapshot?.updated == "final-accepted"
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals("Final snapshot must still be queued behind the slow save", callsBefore + 1, f.saveCalls.get())

            // Hold the ordinary retry before its unsuccessful HTTP response. No new
            // valid SSE frame can replace or re-save the final accepted snapshot.
            val retry = f.peer.holdNextStream(code = 503)
            f.peer.closeStreams()
            retry.awaitEntered()
            assertEquals("Reconnect should remain independent of disk latency", 1L, saveGate.release.count)
            assertEquals("Retry cannot consume the pending final save", callsBefore + 1, f.saveCalls.get())

            saveGate.release.countDown()
            assertTrue("Public slow save did not retire", saveGate.finished.await(5, TimeUnit.SECONDS))
            await("Ordinary EOF discarded the final accepted snapshot before it was cached") {
                f.store.load()?.parsedSnapshot?.updated == "final-accepted"
            }
            val saved = f.store.load()!!
            assertEquals(30L, saved.parsedSnapshot!!.periods.getValue("today").tokens)
            assertEquals(f.model.state.value.savedAt, saved.savedAt)
            assertEquals(history(), saved.history)
            assertEquals("public-r1", saved.historySignature)
            assertEquals("No successful retry frame was delivered", 1L, retry.release.count)
            assertEquals("Exactly the in-flight and pending final snapshots should save", callsBefore + 2, f.saveCalls.get())
        }
    }

    @Test fun historyCompletingAfterSseReconnectTimerWasBlockedRestoresTheStream() {
        Fixture().use { f ->
            val streamsBefore = f.peer.streamReads.get()
            val held = f.peer.holdHistory()
            assertTrue(f.read(true)); held.awaitEntered()
            val started = f.receipt
            f.peer.closeStreams()
            await("Public SSE EOF did not reach the retry state") {
                !f.model.state.value.live && f.model.state.value.error == R.string.error_stream_retry
            }
            // Production retry is 2,000 ms plus less than 500 ms of jitter.
            Thread.sleep(2700)
            assertEquals(streamsBefore, f.peer.streamReads.get())
            assertTrue(f.model.state.value.loading); assertEquals(started, f.receipt)
            held.release.countDown(); f.awaitPhase(HistoryReadPhase.RESOLVED); f.awaitReady()
            assertEquals(streamsBefore + 1, f.peer.streamReads.get())
            assertEquals(1, f.peer.historyReads.get()); assertEquals(1, f.receipt.attempt)
            assertEquals(started.cycleId, f.receipt.cycleId); assertTrue(f.receipt.accepted)
            f.awaitHistorySaved(history(99))
        }
    }
}
