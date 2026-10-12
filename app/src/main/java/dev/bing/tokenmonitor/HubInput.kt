package dev.bing.tokenmonitor

import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.util.concurrent.CancellationException

/** Limits decompressed UTF-8 bytes and checks the original read identity between chunks. */
internal class HubCheckedInputStream(input: InputStream, private val maxBytes: Long,
    private val valid: () -> Boolean = { true }) : FilterInputStream(input) {
    private var bytes = 0L
    internal val bytesRead: Long get() = bytes
    private fun checkRead(count: Int): Int {
        if (!valid() || Thread.currentThread().isInterrupted) throw CancellationException()
        if (count > 0) {
            bytes += count
            if (bytes > maxBytes) throw HubReadException(R.string.error_response_size)
        }
        return count
    }
    override fun read(): Int {
        checkRead(0)
        val value = `in`.read()
        checkRead(if (value < 0) -1 else 1)
        return value
    }
    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        checkRead(0)
        return checkRead(`in`.read(target, offset, minOf(length, 8192)))
    }
}

internal fun hubUtf8Reader(input: InputStream) = InputStreamReader(input,
    Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))

internal fun readHubText(input: InputStream): String {
    val reader = hubUtf8Reader(input)
    val result = StringBuilder()
    val chunk = CharArray(8192)
    while (true) {
        val count = reader.read(chunk)
        if (count < 0) break
        result.append(chunk, 0, count)
    }
    return result.toString()
}
