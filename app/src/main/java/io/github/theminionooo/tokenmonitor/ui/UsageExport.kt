package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import kotlinx.serialization.json.*

/** Explicit allowlist: never serialize the connection, credentials, titles or transcripts. */
internal fun exportUsage(snapshot: HubSnapshot, format: String): String {
    data class Row(val period: String, val kind: String, val name: String, val tokens: Long, val cost: Double)
    val rows = buildList {
        snapshot.stats.periods.forEach { (period, usage) ->
            add(Row(period, "total", "all", usage.totalTokens, usage.costUsd))
            usage.clients.forEach { (name, tokens) -> add(Row(period, "tool", name, tokens, usage.clientCosts[name] ?: 0.0)) }
            usage.models.forEach { (name, tokens) -> add(Row(period, "model", name, tokens, usage.modelCosts[name] ?: 0.0)) }
            usage.projects.forEach { add(Row(period, "project", it.id, it.totalTokens, it.costUsd)) }
        }
        snapshot.stats.devices.forEach { device -> device.periods.forEach { (period, usage) -> add(Row(period, "device", device.id, usage.totalTokens, usage.costUsd)) } }
        snapshot.history.daily.forEach { add(Row(it.label, "daily", "all", it.tokens, it.costUsd)) }
        snapshot.history.monthly.forEach { add(Row(it.label, "monthly", "all", it.tokens, it.costUsd)) }
    }
    if (format == "csv") {
        fun cell(value: String): String {
            // Avoid spreadsheet formula execution, including leading whitespace/control prefixes.
            val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
            return "\"" + safe.replace("\"", "\"\"") + "\""
        }
        return "period,kind,name,tokens,cost_usd\r\n" + rows.joinToString("\r\n") {
            listOf(cell(it.period), cell(it.kind), cell(it.name), it.tokens.toString(), it.cost.toString()).joinToString(",")
        }
    }
    return buildJsonObject {
        put("schema", 1); put("currency", "USD"); put("capturedAt", snapshot.capturedAt)
        put("fromCache", snapshot.fromCache); put("stale", snapshot.stale)
        put("rows", JsonArray(rows.map { row -> buildJsonObject {
            put("period", row.period); put("kind", row.kind); put("name", row.name)
            put("tokens", row.tokens); put("costUsd", row.cost)
        } }))
    }.toString()
}
