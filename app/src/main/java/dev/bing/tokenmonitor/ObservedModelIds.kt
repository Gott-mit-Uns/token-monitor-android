package dev.bing.tokenmonitor

import kotlinx.serialization.json.*

/** Only model identifiers from the pinned alias collector's known stats/history scopes. */
internal object ObservedModelIds {
    const val MAX_IDS = 16384
    const val MAX_ID_LENGTH = 256
    internal val modelFields = listOf("models","modelCosts","modelUnpricedTokens","modelCacheReads","modelCacheWrites","modelOutputs","modelUnclassifiedTokens","modelThroughput")
    internal val clientModelFields = listOf("clientModels","clientModelCosts","clientModelUnpricedTokens")
    internal val detailFields = listOf("sessions", "projects")

    /** Authenticated cache metadata is accepted only in the collector's canonical form. */
    fun validate(ids: List<String>): List<String> = HubWorkTrace.trace(HubWorkStage.MODEL_IDS, units = ids.size.toLong()) {
        require(ids.size <= MAX_IDS && ids.all { it.isNotEmpty() && it.length <= MAX_ID_LENGTH && it == it.trim() } && ids.toSet().size == ids.size) { "Invalid observed model metadata" }
        ids.toList()
    }

    fun collect(root:JsonObject, modelIndex: HubModelIndex = HubModelIndex.EMPTY):List<String> {
        val span = HubWorkTrace.begin(HubWorkStage.MODEL_IDS)
        var count = 0L
        var successful = false
        try {
            val ids=linkedSetOf<String>()
            fun add(raw:String?) {raw?.trim()?.takeIf {it.isNotEmpty() && it.length<=MAX_ID_LENGTH && ids.size<MAX_IDS}?.let(ids::add)}
            fun maps(raw:JsonElement?) {(raw as? JsonObject)?.keys?.forEach(::add)}
            fun clientMaps(raw:JsonElement?) {(raw as? JsonObject)?.values?.forEach(::maps)}
            fun usage(raw:JsonElement?, projectedBranch: ((String) -> List<String>)? = null) {
                val row=raw as? JsonObject ?: return
                add((row["model"] as? JsonPrimitive)?.contentOrNull)
                modelFields.forEach {maps(row[it])}
                clientModelFields.forEach {clientMaps(row[it])}
                detailFields.forEach {key->
                    (row[key] as? JsonObject)?.values?.forEach {usage(it)}
                    projectedBranch?.invoke(key)?.forEach(::add)
                }
            }
            fun history(raw:JsonElement?) {
                val h=raw as? JsonObject ?: return
                listOf("daily","monthly").forEach {key->(h[key] as? JsonArray)?.forEach {value->(value as? JsonObject)?.let {maps(it["perModel"]);clientMaps(it["clientModelCosts"])}}}
                (h["summary"] as? JsonObject)?.let {add((it["favoriteModel"] as? JsonPrimitive)?.contentOrNull);clientMaps(it["clientModelCosts"])}
                (h["deviceHistories"] as? JsonArray)?.forEach {value->(value as? JsonObject)?.let {d->(d["periods"] as? JsonObject)?.values?.forEach {usage(it)};history(d["history"])}}
            }
            (root["periods"] as? JsonObject)?.values?.forEach {usage(it)}
            listOf("today","month","allTime").forEach {usage(root[it])}
            (root["devices"] as? JsonArray)?.forEachIndexed {index,value->(value as? JsonObject)?.let {d->
                (d["periods"] as? JsonObject)?.forEach { (name, value) -> usage(value) {branch -> modelIndex.ids(index, name, branch)} }
                listOf("today","month","allTime").forEach {usage(d[it])}
                history(d["history"]);history(d["historyPreview"])
            }}
            listOf("nativeSessions","nativeProjects").forEach {key->(root[key] as? JsonObject)?.values?.forEach {period->(period as? JsonObject)?.values?.forEach {usage(it)}}}
            history(root["history"]);history(root["historyPreview"])
            val result = ids.toList()
            count = result.size.toLong()
            successful = true
            return result
        } finally { span.end(successful, count) }
    }
}
