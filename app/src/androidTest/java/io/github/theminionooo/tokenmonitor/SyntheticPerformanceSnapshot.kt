package io.github.theminionooo.tokenmonitor

import io.github.theminionooo.tokenmonitor.data.network.WireHubSnapshot
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Generated test data only. No installed pairing, Hub response, or user history is read. */
internal object SyntheticPerformanceSnapshot {
    val capturedAt: Long = Instant.parse("2026-10-04T12:00:00Z").toEpochMilli()
    private const val timestamp = "2026-10-04T12:00:00Z"
    private val clients = listOf("codex", "claude", "mcode")
    private val models = listOf("gpt-6-sol", "claude-sonnet-4-5", "minimax-m2.5")

    data class Sample(val wire: WireHubSnapshot, val sessionCount: Int)

    fun create(targetStatsBytes: Int): Sample {
        val daily = (0 until 365).map { index ->
            historyPoint("date", LocalDate.of(2026, 10, 4).minusDays((364 - index).toLong()).toString(), index + 1)
        }
        val monthly = (0 until 12).map { index ->
            historyPoint("month", LocalDate.of(2026, 10, 1).minusMonths((11 - index).toLong()).toString().take(7), (index + 1) * 30)
        }
        val history = buildJsonObject {
            put("daily", JsonArray(daily))
            put("monthly", JsonArray(monthly))
        }
        val preview = buildJsonObject {
            put("daily", JsonArray(daily.takeLast(35)))
            put("monthly", JsonArray(monthly.takeLast(3)))
        }
        val todaySessions = (0 until 32).associate { "synthetic-$it" to session(it) }
        val base = stats(emptyMap(), todaySessions, preview)
        val sessions = linkedMapOf<String, JsonObject>()
        var estimatedBytes = base.toString().toByteArray().size
        while (true) {
            val index = sessions.size
            val key = "synthetic-$index"
            val record = session(index)
            val addedBytes = key.length + record.toString().toByteArray().size + 4
            // Reserve room for aggregate counters gaining digits as the session count grows.
            if (estimatedBytes + addedBytes > targetStatsBytes - 1024) break
            sessions[key] = record
            estimatedBytes += addedBytes
        }
        val stats = stats(sessions, todaySessions, preview)
        val device = buildJsonObject {
            put("deviceId", "synthetic-desktop")
            put("hostname", "Synthetic desktop")
            put("platform", "win32")
            put("updatedAt", timestamp)
            put("receivedAt", timestamp)
            put("historyAvailable", true)
            put("history", history)
            put("periods", buildJsonObject { put("today", period(todaySessions)) })
        }
        return Sample(
            WireHubSnapshot(
                health = """{"ok":true,"role":"hub","runtime":"synthetic","deviceCount":1,"secretRequired":true}""",
                stats = stats.toString(),
                devices = buildJsonObject { put("devices", JsonArray(listOf(device))) }.toString(),
                history = history.toString(),
                subscriptions = """{"updatedAt":"$timestamp","subscriptions":[]}""",
                capturedAt = capturedAt,
            ),
            sessionCount = sessions.size,
        )
    }

    private fun stats(month: Map<String, JsonObject>, today: Map<String, JsonObject>, preview: JsonObject) = buildJsonObject {
        put("updatedAt", timestamp)
        put("staleAfterMs", 300_000)
        put("periods", buildJsonObject {
            put("today", period(today))
            put("month", period(month))
            put("total", period(emptyMap()))
        })
        put("devices", JsonArray(listOf(buildJsonObject {
            put("deviceId", "synthetic-desktop")
            put("hostname", "Synthetic desktop")
            put("updatedAt", timestamp)
            put("receivedAt", timestamp)
            put("stale", false)
            put("periods", buildJsonObject { put("today", period(emptyMap())) })
        })))
        put("historyPreview", preview)
        put("limits", buildJsonObject {
            put("updatedAt", timestamp)
            put("providers", JsonArray(clients.take(2).mapIndexed { index, provider -> buildJsonObject {
                put("provider", provider)
                put("accountName", "Synthetic account ${index + 1}")
                put("windows", JsonArray(listOf(buildJsonObject {
                    put("kind", "session")
                    put("remainingPercent", 75 - index * 20)
                    put("resetsAt", "2026-10-04T16:00:00Z")
                    put("metric", "percent")
                })))
            } }))
        })
    }

    private fun period(sessions: Map<String, JsonObject>) = buildJsonObject {
        val count = sessions.size.coerceAtLeast(1)
        put("totalTokens", count * 1000L)
        put("costUsd", count * 0.01)
        put("clients", JsonObject(clients.associateWith { JsonPrimitive(count * 1000L / clients.size) }))
        put("models", JsonObject(models.associateWith { JsonPrimitive(count * 1000L / models.size) }))
        put("sessions", JsonObject(sessions))
        put("projects", JsonObject((0 until 24).associate { index ->
            "project-$index" to buildJsonObject {
                put("projectLabel", "Synthetic project $index")
                put("totalTokens", count * 1000L / 24)
                put("sessionCount", (count / 24).coerceAtLeast(1))
                put("costUsd", count * 0.01 / 24)
            }
        }))
    }

    private fun session(index: Int) = buildJsonObject {
        put("sessionId", "synthetic-$index")
        put("title", "Synthetic capacity sample $index for the dashboard performance baseline")
        put("client", clients[index % clients.size])
        put("projectLabel", "Synthetic project ${index % 24}")
        put("totalTokens", 1000)
        put("costUsd", 0.01)
        put("models", buildJsonObject { put(models[index % models.size], 1000) })
        put("messageCount", 8)
        put("startedAt", "2026-10-04T10:00:00Z")
        put("lastUsedAt", timestamp)
        put("sessionKind", "interactive")
        put("contextTokens", 15_000)
        put("contextWindow", 200_000)
        put("turnEnded", index % 5 != 0)
        put("inputTokens", 600)
        put("outputTokens", 100)
        put("cacheReadTokens", 250)
        put("cacheWriteTokens", 50)
        put("timedOutputTokens", 100)
        put("timedDurationMs", 5000)
    }

    private fun historyPoint(label: String, value: String, multiplier: Int) = buildJsonObject {
        val tokens = multiplier * 30_000L
        put(label, value)
        put("tokens", tokens)
        put("cost", multiplier * 0.6)
        put("messages", multiplier * 15)
        put("activeTimeMs", multiplier * 600_000L)
        put("cacheReadTokens", tokens / 4)
        put("cacheWriteTokens", tokens / 10)
        put("outputTokens", tokens / 5)
        put("unclassifiedTokens", 0)
        put("tokenComponentsAvailable", true)
        fun attribution(names: List<String>) = JsonObject(names.associateWith {
            buildJsonObject {
                put("tokens", tokens / names.size)
                put("cost", multiplier * 0.6 / names.size)
                put("cacheReadTokens", tokens / 4 / names.size)
                put("cacheWriteTokens", tokens / 10 / names.size)
                put("outputTokens", tokens / 5 / names.size)
                put("unclassifiedTokens", 0)
                put("tokenComponentsAvailable", true)
            }
        })
        put("perClient", attribution(clients))
        put("perModel", attribution(models))
    }
}
