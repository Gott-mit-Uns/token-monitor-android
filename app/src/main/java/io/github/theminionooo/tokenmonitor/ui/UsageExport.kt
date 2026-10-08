package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import kotlinx.serialization.json.*

/** Explicit allowlist: never serialize the connection, credentials, titles or transcripts. */
internal fun exportUsage(snapshot: HubSnapshot, format: String): String {
    data class Row(val period: String, val kind: String, val name: String, val tokens: Long, val cost: Double)
    val rows = buildList {
        snapshot.stats.periods.forEach { (period, usage) ->
            usage.clients.forEach { (name, tokens) -> add(Row(period, "tool", name, tokens, usage.clientCosts[name] ?: 0.0)) }
            usage.models.forEach { (name, tokens) -> add(Row(period, "model", name, tokens, usage.modelCosts[name] ?: 0.0)) }
        }

    }
    if (format == "csv") {
        fun cell(value: String): String {
            // Avoid spreadsheet formula execution, including leading whitespace/control prefixes.
            val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
            return "\"" + safe.replace("\"", "\"\"") + "\""
        }
        return "\uFEFFperiod,dimension,name,tokens,cost_usd\r\n" + rows.joinToString("\r\n") {
            listOf(cell(it.period), cell(it.kind), cell(it.name), it.tokens.toString(), it.cost.toString()).joinToString(",")
        }
    }
    return desktopExportJson(snapshot)

}

/** Daily CSV companions use the same raw USD values as the Hub, never display conversion. */
internal fun exportDailyUsage(snapshot: HubSnapshot, models: Boolean): String {
    fun cell(value: String): String {
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
    val header = if (models) "date,model,input_tokens,output_tokens,cache_read_tokens,cache_write_tokens,unclassified_tokens,total_tokens,cost_usd" else "date,tool,tokens,cost_usd"
    val rows = snapshot.history.daily.flatMap { day ->
        (if (models) day.perModel else day.perClient).map { (name, value) ->
            if (models) {
                val total = value.tokens.coerceAtLeast(0)
                val output = value.outputTokens.coerceAtLeast(0)
                val read = value.cacheReadTokens.coerceAtLeast(0)
                val write = value.cacheWriteTokens.coerceAtLeast(0)
                val invalid = output.toDouble() + read + write > total
                val remaining = if (invalid) 0 else total - output - read - write
                val unknown = if (invalid) total else value.unclassifiedTokens.coerceIn(0, remaining)
                listOf(cell(day.label.take(10)), cell(name), (remaining - if (invalid) 0 else unknown).toString(), (if (invalid) 0 else output).toString(), (if (invalid) 0 else read).toString(), (if (invalid) 0 else write).toString(), unknown.toString(), total.toString(), value.costUsd.toString()).joinToString(",")
            }
            else listOf(cell(day.label), cell(name), value.tokens.toString(), value.costUsd.toString()).joinToString(",")
        }
    }
    return "\uFEFF" + header + "\r\n" + rows.joinToString("\r\n")
}

internal fun exportUsageBundle(snapshot: HubSnapshot): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    java.util.zip.ZipOutputStream(output).use { zip ->
        val files = linkedMapOf(
            "token-monitor-export.json" to exportUsage(snapshot, "json"),
            "token-monitor-snapshot.csv" to exportUsage(snapshot, "csv"),
            "token-monitor-daily.csv" to exportDailyUsage(snapshot, false),
            "token-monitor-daily-models.csv" to exportDailyUsage(snapshot, true),
        )
        files.forEach { (name, text) ->
            zip.putNextEntry(java.util.zip.ZipEntry(name))
            zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
        }
    }
    return output.toByteArray()
}

private fun desktopExportJson(source: HubSnapshot): String {
    fun counts(map: Map<String, Long>) = buildJsonObject { map.forEach { (k, v) -> put(k, v) } }
    fun costs(map: Map<String, Double>) = buildJsonObject { map.forEach { (k, v) -> put(k, v) } }
    fun period(p: io.github.theminionooo.tokenmonitor.domain.UsagePeriod) = buildJsonObject {
        put("totalTokens", p.totalTokens); put("costUsd", p.costUsd); put("unpricedTokens", p.unpricedTokens)
        put("timedTokens", p.timedTokens); put("timedOutputTokens", p.timedOutputTokens); put("timedDurationMs", p.timedDurationMs)
        put("clients", counts(p.clients)); put("clientCosts", costs(p.clientCosts)); put("models", counts(p.models)); put("modelCosts", costs(p.modelCosts))
        put("clientModels", buildJsonObject { p.clientModels.forEach { (k, v) -> put(k, counts(v)) } })
        put("clientModelCosts", buildJsonObject { p.clientModelCosts.forEach { (k, v) -> put(k, costs(v)) } })
        put("clientCacheReads", counts(p.clientCacheReads)); put("clientCacheWrites", counts(p.clientCacheWrites)); put("clientOutputs", counts(p.clientOutputs)); put("clientUnclassifiedTokens", counts(p.clientUnclassifiedTokens)); put("clientUnpricedTokens", counts(p.clientUnpricedTokens))
        put("modelCacheReads", counts(p.modelCacheReads)); put("modelCacheWrites", counts(p.modelCacheWrites)); put("modelOutputs", counts(p.modelOutputs)); put("modelUnclassifiedTokens", counts(p.modelUnclassifiedTokens)); put("modelUnpricedTokens", counts(p.modelUnpricedTokens))
        put("clientModelUnpricedTokens", buildJsonObject { p.clientModelUnpricedTokens.forEach { (k, v) -> put(k, counts(v)) } })
        put("projects", buildJsonObject { p.projects.forEach { project -> put(project.id, buildJsonObject {
            put("projectId", project.id); put("totalTokens", project.totalTokens); put("costUsd", project.costUsd); put("sessionCount", project.sessionCount); put("clients", counts(project.clients))
        }) } })
        put("sessions", buildJsonObject { p.sessions.forEach { session -> put(session.id, buildJsonObject {
            put("sessionId", session.id); put("client", session.client); put("totalTokens", session.totalTokens); put("costUsd", session.costUsd)
            put("models", counts(session.modelTokens)); put("messageCount", session.messageCount); put("startedAt", session.startedAt); put("lastUsedAt", session.lastUsedAt)
            put("sessionKind", session.sessionKind); put("contextTokens", session.contextTokens); put("contextWindow", session.contextWindow); session.turnEnded?.let { put("turnEnded", it) }
            put("archived", session.archived); put("inputTokens", session.inputTokens); put("outputTokens", session.outputTokens); put("cacheReadTokens", session.cacheReadTokens); put("cacheWriteTokens", session.cacheWriteTokens)
            put("timedOutputTokens", session.timedOutputTokens); put("timedDurationMs", session.timedDurationMs); put("usageSource", session.usageSource); put("usageCoverage", session.usageCoverage); put("unpricedTokens", session.unpricedTokens)
        }) } })
    }
    fun history(rows: List<io.github.theminionooo.tokenmonitor.domain.HistoryPoint>, dateKey: String) = JsonArray(rows.map { day -> buildJsonObject {
        put(dateKey, day.label); put("tokens", day.tokens); put("cost", day.costUsd); put("messages", day.messages); put("activeTimeMs", day.activeTimeMs)
        put("cacheReadTokens", day.cacheReadTokens); put("cacheWriteTokens", day.cacheWriteTokens); put("outputTokens", day.outputTokens); put("unclassifiedTokens", day.unclassifiedTokens); put("unpricedTokens", day.unpricedTokens); put("tokenComponentsAvailable", day.tokenComponentsAvailable)
        fun attribution(values: Map<String, io.github.theminionooo.tokenmonitor.domain.HistoryAttribution>) = buildJsonObject { values.forEach { (name, value) -> put(name, buildJsonObject {
            put("tokens", value.tokens); put("cost", value.costUsd); put("cacheReadTokens", value.cacheReadTokens); put("cacheWriteTokens", value.cacheWriteTokens); put("outputTokens", value.outputTokens); put("unclassifiedTokens", value.unclassifiedTokens); put("unpricedTokens", value.unpricedTokens)
        }) } }
        put("perClient", attribution(day.perClient)); put("perModel", attribution(day.perModel))
    } })
    return buildJsonObject {
        put("generatedAt", java.time.Instant.ofEpochMilli(source.capturedAt).toString())
        put("app", buildJsonObject { put("name", "token-monitor-android"); put("privacyFiltered", true); put("fromCache", source.fromCache); put("stale", source.stale) })
        put("snapshot", buildJsonObject { listOf("today", "month", "allTime").forEach { put(it, period(source.stats.periods[it] ?: io.github.theminionooo.tokenmonitor.domain.UsagePeriod())) } })
        put("daily", history(source.history.daily, "date")); put("monthly", history(source.history.monthly, "month"))
    }.toString()
}
