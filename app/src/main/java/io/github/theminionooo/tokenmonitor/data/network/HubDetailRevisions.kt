package io.github.theminionooo.tokenmonitor.data.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Missing revision fields on older Hubs deliberately disable reuse on snapshot reads. */
internal object HubDetailRevisions {
    private val json = Json { ignoreUnknownKeys = true }
    fun revision(raw: String, field: String): String? = runCatching {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching null
        val stats = root["stats"] as? JsonObject ?: root
        (stats[field] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun canReuse(previous: WireHubSnapshot?, currentStats: String, field: String, detail: String?): Boolean {
        if (previous == null || detail == null) return false
        val current = revision(currentStats, field) ?: return false
        return current == revision(previous.stats, marker(field))
    }

    private fun marker(field: String) = "_androidCached_$field"

    /** Keep detail versions separate from changing stream stats, including across disk caching. */
    fun retainMarkers(previous: String, current: String): String {
        val next = json.parseToJsonElement(current) as JsonObject
        val old = json.parseToJsonElement(previous) as JsonObject
        return JsonObject(next + old.filterKeys { it.startsWith("_androidCached_") }).toString()
    }

    fun retainHistoryMarkers(loaded: String, current: String): String {
        val next = json.parseToJsonElement(current) as JsonObject
        val markers = (json.parseToJsonElement(loaded) as JsonObject).filterKeys {
            it == marker("historyRevision") || it == marker("deviceHistoryRevision")
        }
        return JsonObject(next + markers).toString()
    }

    fun markVersion(stats: String, field: String, value: String): String {
        val root = json.parseToJsonElement(stats) as JsonObject
        return JsonObject(root + buildJsonObject { if (value.isNotBlank()) put(marker(field), value) }).toString()
    }

    fun markLoaded(stats: String, fields: List<String> = listOf("historyRevision", "deviceHistoryRevision", "subscriptionsUpdatedAt")): String {
        val root = json.parseToJsonElement(stats) as JsonObject
        val markers = buildJsonObject { fields.forEach { field -> revision(stats, field)?.let { put(marker(field), it) } } }
        return JsonObject(root + markers).toString()
    }

    fun historyChanged(previous: WireHubSnapshot, currentStats: String): Boolean =
        listOf("historyRevision", "deviceHistoryRevision").any { field ->
            val next = revision(currentStats, field)
            next != null && (next != revision(previous.stats, marker(field)) ||
                (if (field == "historyRevision") previous.history else previous.devices) == null)
        }
}
