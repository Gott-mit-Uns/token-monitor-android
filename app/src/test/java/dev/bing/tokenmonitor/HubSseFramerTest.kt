package dev.bing.tokenmonitor

import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class HubSseFramerTest {
    @Test fun splitUtf8AndAllLineEndingsPreserveMultilineDataAndLateEventType() {
        val wire = "data: 你好🌍\r\ndata: second\revent: stats\r\n\r\ndata: last\nevent:\n\n".toByteArray()
        for (chunkSize in listOf(1, 2, 3, 7, 8192)) {
            assertEquals(
                listOf("stats" to "你好🌍\nsecond", "message" to "last"),
                frames(wire, chunkSize),
            )
        }
    }

    @Test fun emptyDataIsAnEventButCommentAndEventOnlyFramesAreNot() {
        val wire = "event: stats\n: heartbeat\nid: 1\n\ndata:\n\ndata\n\ndata:\ndata:\n\n"
        assertEquals(listOf("message" to "", "message" to "", "message" to "\n"), frames(wire))
    }

    @Test fun removesOnlyOneSpaceAndKeepsDataWhitespace() {
        val wire = "data:  first \rdata:\tsecond\rdata\r event: ignored\revent:  custom \r\r"
        assertEquals(listOf(" custom " to " first \n\tsecond\n"), frames(wire, chunkSize = 1))
    }

    @Test fun exactWireByteLimitIncludesUtf8AndNonblankCrlfButExcludesFinalOptionalLf() {
        val wire = "event:stats\r\ndata:你好\r\n\r\n".toByteArray()
        val countedBytes = wire.size.toLong() - 1 // Final blank CR already completes the event.
        assertEquals(listOf("stats" to "你好"), frames(wire, chunkSize = 1, limit = countedBytes))
        val delivered = mutableListOf<String>()
        val failure = failure {
            HubSseFramer(countedBytes - 1).read(ChunkSource(wire, 1).buffer()) { _, input ->
                delivered += input.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertEquals("Hub SSE event exceeds byte limit", failure.message)
        assertTrue(delivered.isEmpty())
        assertFalse(failure.message.orEmpty().contains("你好"))
    }

    @Test fun lfAndBareCrBlankLinesKeepTheirCompleteWireByteLimits() {
        for (wire in listOf("data:你好\n\n", "data:你好\r\r")) {
            val bytes = wire.toByteArray()
            assertEquals(listOf("message" to "你好"), frames(bytes, chunkSize = 1, limit = bytes.size.toLong()))
            assertEquals("Hub SSE event exceeds byte limit", failure {
                HubSseFramer(bytes.size.toLong() - 1).read(ChunkSource(bytes, 1).buffer()) { _, _ ->
                    fail("Oversize event published")
                }
            }.message)
        }
    }

    @Test fun hugeCommentsAndUnknownFieldsAreCountedWithoutBecomingData() {
        val ignored = "x".repeat(48 * 1024)
        val wire = ":$ignored\nunknown:$ignored\n$ignored\ndata: retained\n\n".toByteArray()
        assertEquals(listOf("message" to "retained"), frames(wire, chunkSize = 173))
        val source = ChunkSource(wire, 257)
        val failure = failure { HubSseFramer(1024).read(source.buffer()) { _, _ -> fail("Oversize event published") } }
        assertEquals("Hub SSE event exceeds byte limit", failure.message)
        assertTrue("Reader consumed the whole oversized line", source.bytesRead <= 1024 + 257)
    }

    @Test fun byteLimitAndEventTypeResetAfterUnsupportedAndSupportedEvents() {
        val first = "event: other\ndata: old\n\n"
        val second = "data: next\n\n"
        val wire = first + second + second
        assertEquals(
            listOf("other" to "old", "message" to "next", "message" to "next"),
            frames(wire, chunkSize = 2, limit = first.toByteArray().size.toLong()),
        )
    }

    @Test fun lateOversizeEventTypeCannotPublishAlreadyBufferedData() {
        val allowed = "类类".toByteArray().size
        assertEquals(listOf("类类" to "body"), frames("data: body\nevent: 类类\n\n", typeLimit = allowed))
        val failure = failure {
            HubSseFramer(maxEventTypeBytes = allowed).read(ChunkSource("data: body\nevent: 类类类\n\n".toByteArray(), 2).buffer()) { _, _ ->
                fail("Invalid late event type published")
            }
        }
        assertEquals("Hub SSE event type exceeds byte limit", failure.message)
    }

    @Test fun eofDiscardsEventsWithoutCompleteBlankLineButAcceptsFinalBareCr() {
        for (wire in listOf("data: value", "data: value\n", "data: value\r", "data: value\r\n", "data: value\n\nevent: stats\ndata: partial")) {
            val expected = if (wire.startsWith("data: value\n\n")) listOf("message" to "value") else emptyList()
            assertEquals(wire, expected, frames(wire, chunkSize = 1))
        }
        assertEquals(listOf("message" to "value"), frames("data: value\r\r", chunkSize = 1))
    }

    @Test fun cancellationDuringSourceReadPreventsEvenACompleteFrameFromPublishing() {
        val canceled = AtomicBoolean(false)
        val source = ChunkSource("data: value\n\n".toByteArray(), 8192) { canceled.set(true) }
        val framer = HubSseFramer()
        framer.read(source.buffer(), canceled::get) { _, _ -> fail("Canceled frame published") }
        assertEquals(listOf("message" to "fresh"), frames("data: fresh\n\n", framer = framer))
    }

    @Test fun cancellationDropsPartialFrameAndStopsAfterFirstCallback() {
        val canceled = AtomicBoolean(false)
        val partial = ChunkSource("data: incomplete\n\n".toByteArray(), 4) { read -> if (read >= 8) canceled.set(true) }
        val framer = HubSseFramer()
        framer.read(partial.buffer(), canceled::get) { _, _ -> fail("Partial canceled frame published") }
        canceled.set(false)
        val delivered = mutableListOf<String>()
        framer.read(ChunkSource("data: first\n\ndata: second\n\n".toByteArray(), 3).buffer(), canceled::get) { _, input ->
            delivered += input.readBytes().toString(Charsets.UTF_8)
            canceled.set(true)
        }
        assertEquals(listOf("first"), delivered)
    }

    @Test fun callbackInputExpiresAndUnreadBytesDoNotLeakIntoNextEvent() {
        var retained: InputStream? = null
        val second = mutableListOf<String>()
        HubSseFramer().read(ChunkSource("data: first\n\ndata: second\n\n".toByteArray(), 2).buffer()) { _, input ->
            if (retained == null) {
                retained = input
                assertEquals('f'.code, input.read())
            } else second += input.readBytes().toString(Charsets.UTF_8)
        }
        assertEquals(listOf("second"), second)
        assertEquals("Hub SSE event input is closed", failure { retained!!.read() }.message)
    }

    @Test fun cancellationWhileConsumingCallbackInputIsVisibleWithoutReturningPrivateData() {
        val canceled = AtomicBoolean(false)
        var observedFailure: IOException? = null
        HubSseFramer().read(ChunkSource("data: value\n\n".toByteArray(), 2).buffer(), canceled::get) { _, input ->
            canceled.set(true)
            observedFailure = failure { input.read() }
        }
        assertTrue(observedFailure is InterruptedIOException)
        assertEquals("Hub SSE read canceled", observedFailure?.message)
    }

    @Test fun synchronousCallbackAppliesBackpressureBeforeReadingFollowingFrames() {
        val source = ChunkSource(("data: first\n\n" + "data: second\n\n".repeat(100)).toByteArray(), 8)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val done = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val error = AtomicReference<Throwable?>()
        val events = mutableListOf<String>()
        val callbackThread = AtomicReference<Thread?>()
        val worker = thread(name = "sse-framer-backpressure") {
            try {
                HubSseFramer().read(source.buffer()) { _, input ->
                    callbackThread.set(Thread.currentThread())
                    val body = input.readBytes().toString(Charsets.UTF_8)
                    if (body == "first") {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                    } else secondEntered.countDown()
                    events += body
                }
            } catch (failure: Throwable) {
                error.set(failure)
            } finally {
                done.countDown()
            }
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val readsWhileBlocked = source.readCalls.get()
            assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS))
            assertEquals(readsWhileBlocked, source.readCalls.get())
            assertSame(worker, callbackThread.get())
        } finally {
            release.countDown()
            assertTrue(done.await(5, TimeUnit.SECONDS))
        }
        error.get()?.let { throw AssertionError("Framer worker failed", it) }
        assertEquals("first", events.first())
        assertEquals(101, events.size)
        assertTrue(events.drop(1).all { it == "second" })
    }

    @Test fun bareCrBlankLinePublishesWhileLiveConnectionWaitsForMoreBytes() {
        val pipe = PipedInputStream(1024)
        val writer = PipedOutputStream(pipe)
        val delivered = CountDownLatch(1)
        val done = CountDownLatch(1)
        val error = AtomicReference<Throwable?>()
        val body = AtomicReference<String?>()
        val source = object : Source {
            private val bytes = ByteArray(1024)
            override fun read(sink: Buffer, byteCount: Long): Long {
                val size = pipe.read(bytes, 0, minOf(byteCount, bytes.size.toLong()).toInt())
                if (size == -1) return -1
                sink.write(bytes, 0, size)
                return size.toLong()
            }
            override fun timeout(): Timeout = Timeout.NONE
            override fun close() = pipe.close()
        }
        val worker = thread(name = "sse-framer-live-cr") {
            try {
                HubSseFramer().read(source.buffer()) { _, input ->
                    body.set(input.readBytes().toString(Charsets.UTF_8))
                    delivered.countDown()
                }
            } catch (failure: Throwable) {
                error.set(failure)
            } finally {
                done.countDown()
            }
        }
        try {
            writer.write("data: live\r\r".toByteArray())
            writer.flush()
            assertTrue("Bare CR event waited for EOF or another byte", delivered.await(5, TimeUnit.SECONDS))
            assertEquals("live", body.get())
            assertFalse("Connection ended despite remaining open", done.await(100, TimeUnit.MILLISECONDS))
            assertTrue(worker.isAlive)
        } finally {
            writer.close()
            assertTrue(done.await(5, TimeUnit.SECONDS))
            pipe.close()
        }
        error.get()?.let { throw AssertionError("Live CR reader failed", it) }
    }

    private fun frames(
        wire: String,
        chunkSize: Int = 8192,
        limit: Long = 32L * 1024 * 1024,
        typeLimit: Int = 128,
        framer: HubSseFramer = HubSseFramer(limit, typeLimit),
    ): List<Pair<String, String>> = frames(wire.toByteArray(), chunkSize, limit, typeLimit, framer)

    private fun frames(
        wire: ByteArray,
        chunkSize: Int = 8192,
        limit: Long = 32L * 1024 * 1024,
        typeLimit: Int = 128,
        framer: HubSseFramer = HubSseFramer(limit, typeLimit),
    ): List<Pair<String, String>> {
        val events = mutableListOf<Pair<String, String>>()
        framer.read(ChunkSource(wire, chunkSize).buffer()) { name, input ->
            events += name to input.readBytes().toString(Charsets.UTF_8)
        }
        return events
    }

    private fun failure(block: () -> Unit): IOException {
        try {
            block()
        } catch (failure: IOException) {
            return failure
        }
        throw AssertionError("Expected IOException")
    }

    private class ChunkSource(
        private val bytes: ByteArray,
        private val chunkSize: Int,
        private val onRead: (Int) -> Unit = {},
    ) : Source {
        var bytesRead = 0
            private set
        val readCalls = AtomicInteger()

        override fun read(sink: Buffer, byteCount: Long): Long {
            if (bytesRead == bytes.size) return -1
            val size = minOf(byteCount, chunkSize.toLong(), (bytes.size - bytesRead).toLong()).toInt()
            sink.write(bytes, bytesRead, size)
            bytesRead += size
            readCalls.incrementAndGet()
            onRead(bytesRead)
            return size.toLong()
        }

        override fun timeout(): Timeout = Timeout.NONE
        override fun close() = Unit
    }
}
