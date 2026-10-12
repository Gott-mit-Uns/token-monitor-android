package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import java.math.BigInteger
import java.util.Locale

/** Display projection only. Source counters and cost fields are never rewritten. */
data class ModelNames(val aliases: Map<String, String>, val grouping: String,val initialized:Boolean=true) {
    internal fun labels(sources: Collection<String>, observed: Collection<String>): Map<String, String> =
        displayLabels(sources, observed)
    fun projectDetails(details: Map<String, UsageBreakdown>, observed: Collection<String>): Map<String, UsageBreakdown> {
        val labels = displayLabels(details.keys, observed)
        val groups = details.entries.groupBy { labels.getValue(it.key) }
        fun sum(values: List<Long?>): Long? = if (values.any { it == null }) null else runCatching {
            values.fold(BigInteger.ZERO) { total, value -> total + BigInteger.valueOf(value!!) }.longValueExact()
        }.getOrNull()
        return groups.mapValues { (_, entries) ->
            val values = entries.map { it.value }
            val cost = if (values.any { it.cost == null }) null else values.fold(java.math.BigDecimal.ZERO) { total, value -> total + java.math.BigDecimal(value.cost!!) }.toPlainString()
            UsageBreakdown(sum(values.map { it.tokens }), cost, TokenComponents(
                sum(values.map { it.components.cacheRead }), sum(values.map { it.components.cacheWrite }), sum(values.map { it.components.output }),
                sum(values.map { it.components.unclassified }), sum(values.map { it.components.inputMiss })),
                unpriced = values.mapNotNull { it.unpriced }.takeIf { it.isNotEmpty() }?.let { sum(it) })
        }
    }
    private fun displayLabels(sources: Collection<String>, observed: Collection<String>): Map<String, String> = HubWorkTrace.trace(HubWorkStage.MODEL_ALIAS_PLAN,units=sources.size.toLong()) {
        val explicit = aliases.entries.associate { modelMatchKey(it.key) to it.value }
        val automatic = mutableMapOf<String, String>()
        if (grouping != "off") {
            val comparator = compareBy<String>({ if (it == modelLeaf(it)) 0 else 1 },
                { if (modelLeaf(it) == modelLeaf(it).lowercase(Locale.ROOT)) 0 else 1 },
                { if (Regex("[._\\s]").containsMatchIn(modelLeaf(it))) 1 else 0 },
                { modelLeaf(it).length }, { modelLeaf(it).lowercase(Locale.ROOT) }, { modelLeaf(it) })
            observed.distinct().filter { it.isNotBlank() && it.length <= 256 }.take(16384)
                .groupBy { modelMatchKey(modelLeaf(it)) }.forEach { (identity, members) ->
                    if (identity.isNotEmpty() && (grouping == "prefix" || members.size > 1)) {
                        val canonical = modelLeaf(members.minWith(comparator))
                        members.forEach { source -> if (source != canonical && automatic.size < 4096) automatic[modelMatchKey(source)] = canonical }
                    }
                }
        }
        sources.associateWith { source ->
            val inferred = automatic[modelMatchKey(source)]
            explicit[modelMatchKey(source)] ?: inferred?.let { explicit[modelMatchKey(it)] ?: it } ?: source
        }
    }
    fun project(models: Map<String, Long>, observed: Collection<String> = models.keys): Map<String, BigInteger> {
        val labels = displayLabels(models.keys, observed)
        val result = linkedMapOf<String, BigInteger>()
        models.forEach { (source, tokens) ->
            val label = labels.getValue(source)
            result[label] = (result[label] ?: BigInteger.ZERO) + BigInteger.valueOf(tokens)
        }
        return result
    }
}
private fun modelLeaf(value: String) = value.trim().split('/').lastOrNull { it.isNotEmpty() } ?: value.trim()
private fun modelMatchKey(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("[._\\s]+"), "-").replace(Regex("-+"), "-").trim('-')

object ModelNamesParser {
    fun parse(body: String): ModelNames {
        val root = Json.parseToJsonElement(body).jsonObject
        require((root["ok"] as? JsonPrimitive)?.booleanOrNull == true)
        val value = root["value"]
        if (value == null || value is JsonNull) return ModelNames(emptyMap(), "off",false)
        val settings = value.jsonObject
        val raw = settings["modelAliases"] as? JsonObject ?: error("Missing aliases")
        val seen = mutableSetOf<String>()
        val aliases = raw.entries.mapNotNull { (source, value) ->
            val target = (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim() ?: return@mapNotNull null
            val alias = source.trim()
            val key = modelMatchKey(alias)
            if (alias.isBlank() || target.isBlank() || alias.length > 256 || target.length > 256 || key == modelMatchKey(target) || !seen.add(key)) null
            else alias to target
        }.take(4096).toMap()
        val mode = (settings["modelAliasGrouping"] as? JsonPrimitive)?.contentOrNull?.lowercase(Locale.ROOT)
        return ModelNames(aliases, mode?.takeIf { it in listOf("off", "duplicates", "prefix") } ?: "off")
    }
}
