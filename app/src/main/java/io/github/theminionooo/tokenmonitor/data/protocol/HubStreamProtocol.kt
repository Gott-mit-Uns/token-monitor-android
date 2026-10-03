package io.github.theminionooo.tokenmonitor.data.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Applies incremental stream frames without teaching the stable snapshot parser about delivery modes. */
internal object HubStreamProtocol {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    fun normalizeComplete(raw: String): String {
        val stats = statsObject(raw)
        if (stats.objectField("periods") == null) {
            throw HubProtocolException("The Hub returned an incomplete statistics response.")
        }
        return stats.toString()
    }

    fun isFreshness(raw: String, eventName: String?): Boolean = eventName == "freshness" || runCatching {
        (json.parseToJsonElement(raw) as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "freshness"
    }.getOrDefault(false)

    /**
     * Stream v2 freshness frames contain only transport timestamps and stale state.
     * Merge those bounded fields into the last complete wire snapshot so a small
     * freshness event can never erase periods, limits, sessions, or history.
     */
    fun mergeFreshness(currentRaw: String, freshnessRaw: String): String {
        val current = statsObject(currentRaw)
        val freshness = statsObject(freshnessRaw)
        val merged = current.toMutableMap()
        listOf("updatedAt", "staleAfterMs").forEach { key -> freshness[key]?.let { merged[key] = it } }

        freshness.objectField("limits")?.let { incoming ->
            merged["limits"] = JsonObject(current.objectField("limits").orEmpty() + incoming.filterKeys { it == "updatedAt" })
        }
        freshness["devices"]?.let { element ->
            val incoming = (element as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val byId = incoming.filter { deviceId(it).isNotBlank() }.associateBy(::deviceId)
            val existing = current.array("devices").mapNotNull { it as? JsonObject }
            val combined = existing.map { device ->
                byId[deviceId(device).takeIf { it.isNotBlank() }]?.let { JsonObject(device + it.filterKeys { field -> field in setOf("updatedAt", "receivedAt", "ageMs", "stale", "syncUploadIntervalMs") }) } ?: device
            }.toMutableList()
            merged["devices"] = JsonArray(combined)
        }
        return JsonObject(merged).toString()
    }

    private fun statsObject(raw: String): JsonObject {
        val root = json.parseToJsonElement(raw) as? JsonObject
            ?: throw HubProtocolException("The Hub returned a JSON value where an object was expected.")
        return root.objectField("stats") ?: root
    }

    private fun JsonObject.objectField(name: String): JsonObject? = this[name] as? JsonObject
    private fun JsonObject.array(name: String): JsonArray = this[name] as? JsonArray ?: JsonArray(emptyList())
    private fun deviceId(device: JsonObject): String = device["deviceId"]?.jsonPrimitive?.contentOrNull
        ?.ifBlank { null } ?: device["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
}
