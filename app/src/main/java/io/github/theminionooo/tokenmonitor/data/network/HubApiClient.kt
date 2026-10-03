package io.github.theminionooo.tokenmonitor.data.network

import io.github.theminionooo.tokenmonitor.BuildConfig
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.protocol.HubStreamProtocol
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException

internal data class WireHubSnapshot(
    val health: String,
    val stats: String,
    val devices: String?,
    val history: String?,
    val subscriptions: String?,
    val capturedAt: Long,
)

internal data class HubStreamEvent(
    val type: String,
    val data: String,
)

internal class HubApiException(
    val statusCode: Int,
    val presentation: String,
) : Exception(presentation)

/** A deliberately small GET-only client for the Token Monitor Hub. */
internal fun isEventStream(contentType: String?): Boolean =
    contentType?.substringBefore(";")?.trim()?.equals("text/event-stream", ignoreCase = true) == true

internal class HubApiClient : Closeable {
    private val requests = mutableSetOf<HttpURLConnection>()
    private var closed = false

    override fun close() {
        val pending = synchronized(requests) {
            closed = true
            requests.toList().also { requests.clear() }
        }
        pending.forEach { it.disconnect() }
    }

    private fun release(http: HttpURLConnection) {
        synchronized(requests) { requests.remove(http) }
        http.disconnect()
    }
    fun loadSnapshot(connection: HubConnection, previous: WireHubSnapshot? = null): WireHubSnapshot {
        val health = get(connection, "/api/health", authenticated = false)
        val identity = runCatching { HubProtocolParser.decodeHealth(health) }.getOrNull()
        if (identity?.ok != true || identity.role != "hub") {
            throw HubApiException(200, "The address is not a Token Monitor Hub.")
        }
        val stats = get(connection, "/api/stats")
        return WireHubSnapshot(
            health = health,
            stats = HubDetailRevisions.markLoaded(HubStreamProtocol.normalizeComplete(stats)),
            devices = if (HubDetailRevisions.canReuse(previous, stats, "deviceHistoryRevision", previous?.devices)) previous?.devices else getOptional(connection, "/api/devices"),
            history = if (HubDetailRevisions.canReuse(previous, stats, "historyRevision", previous?.history)) previous?.history else getOptional(connection, "/api/history"),
            subscriptions = if (HubDetailRevisions.canReuse(previous, stats, "subscriptionsUpdatedAt", previous?.subscriptions)) previous?.subscriptions else getOptional(connection, "/api/subscriptions"),
            capturedAt = System.currentTimeMillis(),
        )
    }

    fun getSubscriptions(connection: HubConnection): String = get(connection, "/api/subscriptions")

    /** Reads the changing aggregate only; detailed history remains in the initial snapshot. */
    fun getStats(connection: HubConnection): String = get(connection, "/api/stats").also { raw ->
        if (runCatching { HubStreamProtocol.normalizeComplete(raw); HubProtocolParser.decodeStats(raw) }.isFailure) {
            throw HubApiException(200, "The Hub did not return valid statistics. Showing the last successful snapshot.")
        }
    }

    /** A quick unauthenticated identity check used while looking for a Hub on the home network. */
    fun probeHub(baseUrl: String, timeoutMs: Int): Boolean = runCatching {
        val http = (URL("$baseUrl/api/health").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs * 2
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Token-Monitor-Android/${BuildConfig.VERSION_NAME}")
        }
        try {
            http.responseCode in 200..299 && HubProtocolParser.decodeHealth(readBounded(http.inputStream)).let { it.ok && it.role == "hub" }
        } finally {
            http.disconnect()
        }
    }.getOrDefault(false)

    /** Blocks until the server closes the stream or [SseSession.close] is called. */
    suspend fun streamStats(
        connection: HubConnection,
        onOpen: suspend (SseSession) -> Unit,
        onEvent: suspend (HubStreamEvent) -> Unit,
    ) {
        val http = open(
            connection = connection,
            path = "/api/stats/stream",
            authenticated = true,
            accept = "text/event-stream",
            headers = mapOf("x-token-monitor-stream" to "2"),
        )
        try {
            if (http.responseCode !in 200..299) throw responseException(http)
            if (!isEventStream(http.contentType)) {
                throw HubApiException(200, "The Hub did not return an event stream. Using periodic updates instead.")
            }
            val session = SseSession(http)
            onOpen(session)
            http.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                readEvents(reader, session, onEvent)
            }
        } finally {
            release(http)
        }
    }

    private fun get(connection: HubConnection, path: String, authenticated: Boolean = true): String {
        val http = open(connection, path, authenticated)
        try {
            if (http.responseCode !in 200..299) throw responseException(http)
            return readBounded(http.inputStream)
        } finally {
            release(http)
        }
    }

    private fun getOptional(connection: HubConnection, path: String): String? = try {
        get(connection, path)
    } catch (error: HubApiException) {
        if (error.statusCode == 404) null else throw error
    }

    private fun open(
        connection: HubConnection,
        path: String,
        authenticated: Boolean,
        accept: String = "application/json",
        headers: Map<String, String> = emptyMap(),
    ): HttpURLConnection {
        val http = (URL(connection.baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = if (accept == "text/event-stream") 75_000 else 35_000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "Token-Monitor-Android/${BuildConfig.VERSION_NAME}")
            headers.forEach(::setRequestProperty)
            if (authenticated) setRequestProperty("Authorization", "Bearer ${connection.secret}")
        }
        synchronized(requests) {
            if (closed) {
                http.disconnect()
                throw CancellationException("Hub request cancelled")
            }
            requests.add(http)
        }
        return http
    }

    private fun responseException(http: HttpURLConnection): HubApiException = when (http.responseCode) {
        401, 403 -> HubApiException(http.responseCode, "The Hub rejected the secret. Check the connection settings.")
        404 -> HubApiException(404, "This Hub endpoint is not available. Update the desktop Token Monitor Hub.")
        else -> HubApiException(http.responseCode, "The Hub returned HTTP ${http.responseCode}. Try again when it is online.")
    }

    private suspend fun readEvents(reader: BufferedReader, session: SseSession, onEvent: suspend (HubStreamEvent) -> Unit) {
        val data = StringBuilder()
        var eventType = "message"
        while (!session.closed.get()) {
            val line = readSseLine(reader) ?: break
            if (line.isEmpty()) {
                if (data.isNotEmpty()) {
                    onEvent(HubStreamEvent(eventType, data.toString()))
                    data.clear()
                }
                eventType = "message"
            } else if (line.startsWith("event:")) {
                eventType = line.removePrefix("event:").trim().ifBlank { "message" }
            } else if (line.startsWith("data:")) {
                val payload = line.removePrefix("data:").removePrefix(" ")
                if (data.length + payload.length + (if (data.isEmpty()) 0 else 1) > maxSseEventChars) {
                    throw HubApiException(200, "The Hub stream event is too large for this phone dashboard.")
                }
                if (data.isNotEmpty()) data.append('\n')
                data.append(payload)
            }
        }
    }

    private fun readSseLine(reader: BufferedReader): String? {
        val line = StringBuilder()
        while (true) {
            when (val character = reader.read()) {
                -1 -> return if (line.isEmpty()) null else line.toString()
                '\n'.code -> return line.toString()
                '\r'.code -> Unit
                else -> {
                    if (line.length >= maxSseLineChars) {
                        throw HubApiException(200, "The Hub stream line is too large for this phone dashboard.")
                    }
                    line.append(character.toChar())
                }
            }
        }
    }

    private fun readBounded(stream: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var totalBytes = 0
        while (true) {
            val read = stream.read(buffer)
            if (read == -1) break
            totalBytes += read
            if (totalBytes > maxResponseBytes) {
                throw HubApiException(200, "The Hub response is too large for this phone dashboard.")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray().toString(StandardCharsets.UTF_8)
    }

    internal class SseSession(private val connection: HttpURLConnection) : Closeable {
        internal val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) connection.disconnect()
        }
    }

    private companion object {
        const val maxResponseBytes = 32 * 1024 * 1024
        const val maxSseLineChars = 8 * 1024 * 1024
        const val maxSseEventChars = 8 * 1024 * 1024
    }
}
