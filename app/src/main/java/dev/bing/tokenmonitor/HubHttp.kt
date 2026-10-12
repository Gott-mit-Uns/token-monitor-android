package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.CancellationException
import java.io.InputStream

/** Bounded authenticated reads; response bodies and headers never enter diagnostics. */
class HubHttp(private val client: OkHttpClient, private val transport: (String) -> OkHttpClient = { client }) {
    private val lock = Any()
    private val calls = mutableSetOf<Call>()
    private var paused = false
    fun cancel() { synchronized(lock) { calls.toList() }.forEach { it.cancel() } }
    fun pause() {
        val active = synchronized(lock) { paused = true; calls.toList() }
        active.forEach { it.cancel() }
    }
    fun resume() { synchronized(lock) { paused = false } }
    fun read(base: String, path: String, credential: String? = null, valid: () -> Boolean = { true }): String =
        readBody(base, path, credential, valid, 8L * 1024 * 1024, ::readHubText)

    internal fun readStats(base: String, credential: String, valid: () -> Boolean = { true }): ProjectedHubStats =
        readBody(base, "stats", credential, valid, HubJsonProjection.MAX_INPUT_BYTES) { HubJsonProjection.stats(it) }

    internal fun readLarge(base: String, path: String, credential: String, valid: () -> Boolean = { true }): String =
        readBody(base, path, credential, valid,
            if (path in setOf("history", "devices")) HubJsonProjection.MAX_INPUT_BYTES else 8L * 1024 * 1024, ::readHubText)

    internal fun readDeviceHistory(base: String, credential: String, signature: String, valid: () -> Boolean = { true }): String =
        readBody(base, "devices", credential, valid, HubJsonProjection.MAX_INPUT_BYTES) { input ->
            val span = HubWorkTrace.begin(HubWorkStage.JSON_PROJECT)
            var successful = false
            try {
                val result = DeviceHistoryStream.project(input, signature)
                successful = true
                result
            } finally { span.end(successful, (input as? HubCheckedInputStream)?.bytesRead ?: 0L) }
        }

    private fun <T> readBody(base: String, path: String, credential: String?, valid: () -> Boolean,
        maxBytes: Long, consume: (InputStream) -> T): T {
        require(path in setOf("health", "stats", "devices", "history", "subscriptions", "sync/settings/modelAliases", "sync/settings/customPricing", "sync/content"))
        val request = Request.Builder().url("$base/api/$path").get().apply {
            credential?.let { header("Authorization", "Bearer $it") }
        }.build()
        val call = synchronized(lock) {
            if (paused || !valid()) throw CancellationException()
            transport(base).newCall(request).also { calls.add(it) }
        }
        // Includes network wait and nested body projection; units are decoded response bytes.
        val span = HubWorkTrace.begin(HubWorkStage.HTTP_READ)
        var input: HubCheckedInputStream? = null
        var successful = false
        try {
            val result = call.execute().use { response ->
                when (response.code) {
                    401, 403 -> throw HubReadException(R.string.error_auth, true)
                    404 -> throw HubReadException(R.string.error_unsupported)
                    429 -> throw HubReadException(R.string.error_rate_limited)
                }
                if (!response.isSuccessful) throw HubReadException(R.string.error_unavailable)
                if (!response.header("Content-Type").orEmpty().contains("application/json", true)) throw HubReadException(R.string.error_content_type)
                val checked = HubCheckedInputStream(response.body.byteStream(), maxBytes, valid)
                input = checked
                val result = consume(checked)
                if (!valid() || call.isCanceled()) throw CancellationException()
                result
            }
            successful = true
            return result
        } finally {
            synchronized(lock) { calls.remove(call) }
            span.end(successful, input?.bytesRead ?: 0L)
        }
    }
    fun verify(base: String, valid: () -> Boolean = { true }): HubBuildInfo {
        val body = read(base, "health", valid = valid)
        val health = Json.parseToJsonElement(body).jsonObject
        require((health["ok"] as? JsonPrimitive)?.booleanOrNull == true)
        require((health["role"] as? JsonPrimitive)?.contentOrNull == "hub")
        return HubBuildInfo.parse(body) ?: HubBuildInfo("",buildPresent=false)
    }
}
class HubReadException(val display: Int, val unauthorized: Boolean = false) : Exception()
