package dev.bing.tokenmonitor

import okio.Buffer
import okio.BufferedSource
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException

/**
 * Reads one SSE event at a time without first materializing its lines as strings.
 * [onEvent] runs on the reader's thread and must consume its input before returning.
 * Raw event bytes, including ignored fields and line endings, share one limit.
 * The optional LF after a terminating blank-line CR is excluded: CR completes and
 * publishes that event immediately, even if the connection then remains idle.
 */
internal class HubSseFramer(
    private val maxEventBytes: Long = MAX_EVENT_BYTES,
    private val maxEventTypeBytes: Int = 128,
) {
    init {
        require(maxEventBytes in 1..MAX_EVENT_BYTES) { "Invalid Hub SSE event byte limit" }
        require(maxEventTypeBytes in 0..128) { "Invalid Hub SSE event type byte limit" }
    }

    fun read(
        source: BufferedSource,
        isCanceled: () -> Boolean = { false },
        onEvent: (String, InputStream) -> Unit,
    ) {
        // This is the only event-sized allocation; the remaining storage is fixed size.
        val data = Buffer()
        val chunk = ByteArray(8 * 1024)
        val eventType = ByteArray(maxEventTypeBytes)
        val field = ByteArray(5)
        var eventBytes = 0L
        var eventRead: HubWorkTrace.Span? = null
        var eventTypeSize = 0
        var hasData = false
        var lineHasContent = false
        var fieldSize = 0
        var mode = FIELD
        var skipSpace = false
        var pendingCr = false
        var countPendingLf = false

        fun countBytes(count: Int) {
            if (eventBytes == 0L) eventRead = HubWorkTrace.begin(HubWorkStage.SSE_READ_FRAME)
            if (count.toLong() > maxEventBytes - eventBytes) {
                throw IOException("Hub SSE event exceeds byte limit")
            }
            eventBytes += count
        }

        fun fieldIs(name: String): Boolean {
            if (fieldSize != name.length) return false
            for (index in name.indices) if (field[index].toInt() != name[index].code) return false
            return true
        }

        fun beginValue() {
            mode = when {
                fieldIs("data") -> {
                    if (hasData) data.writeByte(LF)
                    hasData = true
                    DATA
                }
                fieldIs("event") -> {
                    eventTypeSize = 0
                    EVENT
                }
                else -> IGNORE
            }
            skipSpace = true
        }

        fun finishLine() {
            if (lineHasContent) {
                // A recognized field without a colon has an empty value.
                if (mode == FIELD) beginValue()
            } else {
                eventRead?.end(units = eventBytes)
                eventRead = null
                if (hasData && !isCanceled()) {
                    val name = if (eventTypeSize == 0) "message"
                    else String(eventType, 0, eventTypeSize, Charsets.UTF_8)
                    val input = EventInput(data, isCanceled)
                    try {
                        onEvent(name, input)
                    } finally {
                        input.close()
                        data.clear()
                    }
                }
                data.clear()
                eventBytes = 0L
                eventTypeSize = 0
                hasData = false
            }
            lineHasContent = false
            fieldSize = 0
            mode = FIELD
            skipSpace = false
        }

        try {
            while (!isCanceled()) {
                val read = source.read(chunk, 0, chunk.size)
                if (isCanceled()) return
                if (read == -1) {
                    // A line without a terminator, or data without a following blank
                    // line, is never published. CR-terminated lines already finished.
                    return
                }
                var index = 0
                while (index < read) {
                    if (isCanceled()) return
                    val byte = chunk[index].toInt() and 0xff
                    if (pendingCr) {
                        pendingCr = false
                        if (byte == LF) {
                            if (countPendingLf) countBytes(1)
                            index++
                            continue
                        }
                    }
                    if (byte == CR || byte == LF) {
                        countBytes(1)
                        if (byte == CR) {
                            pendingCr = true
                            // A nonblank line still belongs to the current event, so
                            // its optional LF counts too. A blank CR ends that event.
                            countPendingLf = lineHasContent
                        }
                        finishLine()
                        index++
                        continue
                    }

                    lineHasContent = true
                    if (mode != FIELD && !skipSpace) {
                        var end = index + 1
                        while (end < read && chunk[end].toInt() != CR && chunk[end].toInt() != LF) end++
                        val size = end - index
                        countBytes(size)
                        when (mode) {
                            DATA -> data.write(chunk, index, size)
                            EVENT -> {
                                if (size > maxEventTypeBytes - eventTypeSize) {
                                    throw IOException("Hub SSE event type exceeds byte limit")
                                }
                                chunk.copyInto(eventType, eventTypeSize, index, end)
                                eventTypeSize += size
                            }
                        }
                        index = end
                        continue
                    }

                    countBytes(1)
                    if (mode == FIELD) {
                        if (byte == COLON) beginValue()
                        else if (fieldSize < field.size) field[fieldSize++] = chunk[index]
                        else mode = IGNORE
                    } else {
                        val ignoreSpace = skipSpace && byte == SPACE
                        skipSpace = false
                        if (!ignoreSpace) {
                            when (mode) {
                                DATA -> data.writeByte(byte)
                                EVENT -> {
                                    if (eventTypeSize == maxEventTypeBytes) {
                                        throw IOException("Hub SSE event type exceeds byte limit")
                                    }
                                    eventType[eventTypeSize++] = chunk[index]
                                }
                            }
                        }
                    }
                    index++
                }
            }
        } finally {
            eventRead?.end(success = false, units = eventBytes)
            // Also discard a partial frame on cancellation, EOF, or a callback failure.
            data.clear()
        }
    }

    /** Prevents retained callback inputs from observing bytes belonging to later events. */
    private class EventInput(
        private val data: Buffer,
        private val isCanceled: () -> Boolean,
    ) : InputStream() {
        private var closed = false

        private fun checkReadable() {
            if (closed) throw IOException("Hub SSE event input is closed")
            if (isCanceled()) throw InterruptedIOException("Hub SSE read canceled")
        }

        override fun read(): Int {
            checkReadable()
            return if (data.exhausted()) -1 else data.readByte().toInt() and 0xff
        }

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            checkReadable()
            if (offset < 0 || length < 0 || offset > bytes.size - length) throw IndexOutOfBoundsException()
            if (length == 0) return 0
            return data.read(bytes, offset, length)
        }

        override fun available(): Int {
            checkReadable()
            return data.size.toInt()
        }

        override fun close() {
            closed = true
        }
    }

    private companion object {
        const val MAX_EVENT_BYTES = 32L * 1024L * 1024L
        const val FIELD = 0
        const val DATA = 1
        const val EVENT = 2
        const val IGNORE = 3
        const val CR = 13
        const val LF = 10
        const val COLON = 58
        const val SPACE = 32
    }
}
