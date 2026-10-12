package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import okhttp3.*
import java.io.IOException

internal data class HubStreamSnapshot(val body: String, val root: JsonObject, val modelIndex: HubModelIndex,
    val isFreshness: Boolean = false)

class StreamFrames(private val limit: Int = 8 * 1024 * 1024) {
    private var event = "message"
    private var size = 0
    private val data = StringBuilder()
    fun line(line: String): Pair<String, String>? {
        if (line.isEmpty()) {
            val frame = if (data.isNotEmpty()) event to data.toString().removeSuffix("\n") else null
            event = "message"; size = 0; data.clear()
            return frame
        }
        size += line.length
        require(size <= limit)
        if (line.startsWith(':')) return null
        val value = line.substringAfter(':', "").removePrefix(" ")
        when (line.substringBefore(':')) { "event" -> event = value; "data" -> data.append(value).append('\n') }
        return null
    }
}

object StreamMerge {
    fun apply(old: JsonObject?, name: String, body: String): JsonObject? {
        if (name !in setOf("snapshot", "stats", "freshness")) return old
        val next = HubWorkTrace.trace(HubWorkStage.JSON_TREE, units = body.length.toLong()) {
            Json.parseToJsonElement(body).jsonObject["stats"]?.jsonObject ?: error("Missing stats")
        }
        return applyStats(old, name, next)
    }
    internal fun applyStats(old: JsonObject?, name: String, next: JsonObject): JsonObject? {
        if (name != "freshness") { require(next["periods"] is JsonObject); return next }
        if (old == null) return null
        val merged = old.toMutableMap()
        for (key in listOf("updatedAt", "staleAfterMs")) next[key]?.let { merged[key] = it }
        val updated = (next["limits"] as? JsonObject)?.get("updatedAt")
        val limits = old["limits"] as? JsonObject
        if (updated != null && limits != null) merged["limits"] = JsonObject(limits + ("updatedAt" to updated))
        val devices = (next["devices"] as? JsonArray).orEmpty().associateBy { it.jsonObject["deviceId"] }
        (old["devices"] as? JsonArray)?.let { original ->
            merged["devices"] = JsonArray(original.map { value ->
                val device = value.jsonObject
                val update = devices[device["deviceId"]]?.jsonObject
                JsonObject(device + update.orEmpty().filterKeys { it in setOf("updatedAt", "receivedAt", "ageMs", "stale") })
            })
        }
        return JsonObject(merged)
    }
}

data class StreamEnd(val unauthorized: Boolean = false, val retryAfterMs: Long? = null, val tlsFailure: Boolean = false, val recoverable: Boolean = true)

object HubRetryAfter {
    fun parse(value: String?, now: java.time.Instant = java.time.Instant.now()): Long? {
        val raw = value?.trim()?.takeIf { it.length <= 128 } ?: return null
        val seconds = raw.toLongOrNull()
        if (seconds != null) return seconds.takeIf { it >= 0 }?.coerceAtMost(Long.MAX_VALUE / 1000L)?.times(1000L)
        return runCatching {
            val date = java.time.ZonedDateTime.parse(raw, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            java.time.Duration.between(now, date).toMillis().coerceAtLeast(0L)
        }.getOrNull()
    }
}

class HubStream(private val client: OkHttpClient) {
    fun open(address: String, credential: String, snapshot: (String) -> Unit, ended: (StreamEnd) -> Unit): Call =
        openFrames(address, credential, { true }, { snapshot(it.body) }, ended,
            maxBytes = 8L * 1024 * 1024, preserveDeviceDetails = true)

    internal fun openProjected(address: String, credential: String, valid: () -> Boolean = { true },
        snapshot: (HubStreamSnapshot) -> Unit, ended: (StreamEnd) -> Unit, released: () -> Unit = {}): Call =
        openFrames(address, credential, valid, snapshot, ended, HubJsonProjection.MAX_INPUT_BYTES, false, released)

    private fun openFrames(address: String, credential: String, valid: () -> Boolean,
        snapshot: (HubStreamSnapshot) -> Unit, ended: (StreamEnd) -> Unit, maxBytes: Long,
        preserveDeviceDetails: Boolean, released: () -> Unit = {}): Call {
        val call = client.newCall(Request.Builder().url("$address/api/stats/stream")
            .header("Authorization", "Bearer $credential").header("x-token-monitor-stream", "2").get().build())
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                try { if (!call.isCanceled() && valid()) ended(StreamEnd(tlsFailure = HubConnectionPolicy.classify(e) == HubConnectionPolicy.Failure.TLS)) }
                finally { released() }
            }
            override fun onResponse(call: Call, response: Response) {
                var ending = StreamEnd()
                try {
                    response.use {
                        ending = StreamEnd(it.code == 401 || it.code == 403, if (it.code == 429 || it.code == 503) HubRetryAfter.parse(it.header("Retry-After")) else null)
                        if (!it.isSuccessful || !it.header("Content-Type").orEmpty().contains("text/event-stream")) {
                            ending = ending.copy(recoverable = it.code == 429 || it.code == 503)
                            return@use
                        }
                        val source = it.body.source()
                        val frames = HubSseFramer(maxBytes)
                        var current: JsonObject? = null
                        var currentIndex = HubModelIndex.EMPTY
                        frames.read(source, { call.isCanceled() || !valid() }) { name, input ->
                            if (name in setOf("snapshot", "stats", "freshness")) {
                                val projected = HubJsonProjection.streamStats(HubCheckedInputStream(input,
                                    maxBytes) { !call.isCanceled() && valid() }, maxBytes, preserveDeviceDetails)
                                val next = HubWorkTrace.trace(HubWorkStage.JSON_TREE, units = projected.body.length.toLong()) {
                                    Json.parseToJsonElement(projected.body).jsonObject
                                }
                                current = HubWorkTrace.trace(HubWorkStage.JSON_MERGE,
                                    units = ((current ?: next)["devices"] as? JsonArray)?.size?.toLong() ?: 0L) {
                                    StreamMerge.applyStats(current, name, next)
                                }
                                if (name != "freshness") currentIndex = projected.modelIndex
                                current?.let { root ->
                                    if (!call.isCanceled() && valid()) {
                                        val body = if (name == "freshness") {
                                            val span = HubWorkTrace.begin(HubWorkStage.JSON_STRINGIFY)
                                            var units = 0L
                                            var successful = false
                                            try {
                                                val text = root.toString()
                                                units = text.length.toLong()
                                                successful = true
                                                text
                                            } finally { span.end(successful, units) }
                                        } else projected.body
                                        snapshot(HubStreamSnapshot(body, root, currentIndex, isFreshness = name == "freshness"))
                                    }
                                }
                            }
                        }
                    }
                } catch (error: Exception) {
                    // Never return private response text. EOF/socket failures can retry; malformed frames cannot.
                    if (error !is IOException && ending.retryAfterMs == null && ending.recoverable) ending = ending.copy(recoverable = false)
                    if (HubConnectionPolicy.classify(error) == HubConnectionPolicy.Failure.TLS) ending = ending.copy(tlsFailure = true)
                }
                try { if (!call.isCanceled() && valid()) ended(ending) } finally { released() }
            }
        })
        return call
    }
}
