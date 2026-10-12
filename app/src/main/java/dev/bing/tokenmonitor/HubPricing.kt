package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import java.math.BigDecimal

/** Read-only shared overrides. These never replace Hub costs or feed a local calculator. */
data class ModelPrice(val model: String, val rates: Map<String, BigDecimal>)
data class HubPricing(val initialized: Boolean, val revision: Long, val updated: String?, val models: List<ModelPrice>)
object PricingParser {
    val rateKeys = listOf("inputPerM", "outputPerM", "cacheReadPerM", "cacheWritePerM", "cacheWrite1hPerM")
    fun parse(body: String): HubPricing {
        val root = Json.parseToJsonElement(body).jsonObject
        require((root["ok"] as? JsonPrimitive)?.booleanOrNull == true)
        require((root["version"] as? JsonPrimitive)?.intOrNull == 1)
        val revision = (root["revision"] as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 } ?: error("Missing revision")
        val updated = (root["updatedAt"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        val value = root["value"] ?: error("Missing pricing value")
        if (value is JsonNull) return HubPricing(false, revision, updated, emptyList())
        val rows = value as? JsonArray ?: error("Missing pricing array")
        require(rows.size <= 4096)
        val seen = mutableSetOf<String>()
        val models = rows.map { element ->
            val row = element.jsonObject
            val model = (row["modelId"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim() ?: error("Missing model")
            require(model.isNotEmpty() && model.length <= 256 && seen.add(model))
            val rates = rateKeys.mapNotNull { key ->
                val raw = row[key]
                if (raw == null || raw is JsonNull || (raw is JsonPrimitive && raw.isString && raw.content.isEmpty())) null
                else {
                    val number = raw as? JsonPrimitive ?: error("Invalid rate")
                    require(!number.isString && number.doubleOrNull?.let { it.isFinite() && it >= 0 } == true)
                    val decimal = BigDecimal(number.content)
                    require(decimal.signum() >= 0 && decimal.precision() <= 100 && decimal.scale() in -400..400)
                    key to decimal
                }
            }.toMap()
            require("inputPerM" in rates || "outputPerM" in rates)
            ModelPrice(model, rates)
        }.sortedBy { it.model }
        return HubPricing(true, revision, updated, models)
    }
}
