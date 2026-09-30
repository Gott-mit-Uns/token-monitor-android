package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.domain.DeviceUsage
import io.github.theminionooo.tokenmonitor.domain.HubHealth
import io.github.theminionooo.tokenmonitor.domain.HubHistory
import io.github.theminionooo.tokenmonitor.domain.HistoryAttribution
import io.github.theminionooo.tokenmonitor.domain.HubLimits
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.domain.HubStats
import io.github.theminionooo.tokenmonitor.domain.HubSubscriptions
import io.github.theminionooo.tokenmonitor.domain.HistoryPoint
import io.github.theminionooo.tokenmonitor.domain.LimitAccount
import io.github.theminionooo.tokenmonitor.domain.LimitWindow
import io.github.theminionooo.tokenmonitor.domain.ProjectUsage
import io.github.theminionooo.tokenmonitor.domain.SessionUsage
import io.github.theminionooo.tokenmonitor.domain.Subscription
import io.github.theminionooo.tokenmonitor.domain.UsagePeriod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Compatibility parser for the external Hub contract. It reads only fields the
 * Android client renders and intentionally ignores unknown fields.
 */
object HubProtocolParser {
    const val SUPPORTED_UPSTREAM_VERSION = "v0.64.0"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun decodeHealth(raw: String): HubHealth = decodeHealthDto(parseObject(raw)).toDomain()

    // Early releases cached the SSE envelope instead of its stats object. Accept both
    // here so an installed app can recover that cache without losing its pairing.
    fun decodeStats(raw: String): HubStats = statsObject(raw).toStatsDto().toDomain()

    fun normalizeStatsJson(raw: String): String = statsObject(raw).toString()

    private fun statsObject(raw: String): JsonObject = parseObject(raw).let { it.objectField("stats") ?: it }

    fun decodeDevices(raw: String): List<DeviceUsage> {
        val root = parseObject(raw)
        return root.array("devices").mapNotNull { it.objectOrNull()?.toDeviceDto()?.toDomain() }
    }

    fun decodeHistory(raw: String): HubHistory = parseObject(raw).toHistoryDto().toDomain()

    fun decodeSubscriptions(raw: String): HubSubscriptions = parseObject(raw).toSubscriptionsDto().toDomain()

    fun decodeSnapshot(
        healthRaw: String,
        statsRaw: String,
        devicesRaw: String?,
        historyRaw: String?,
        subscriptionsRaw: String?,
        capturedAt: Long,
        fromCache: Boolean = false,
    ): HubSnapshot {
        val parsedStats = decodeStats(statsRaw)
        val devices = devicesRaw?.let(::decodeDevices).orEmpty()
        // The stats record advances with SSE. /devices is retained only for its
        // detailed history, never to overwrite newer device counters or removals.
        val stats = parsedStats.copy(devices = parsedStats.devices.map { current ->
            val saved = devices.firstOrNull { it.id == current.id }
            if (saved != null && current.history.daily.isEmpty() && current.history.monthly.isEmpty()) {
                current.copy(history = saved.history, historyAvailable = current.historyAvailable ?: saved.historyAvailable)
            } else current
        })
        val savedHistory = historyRaw?.let(::decodeHistory) ?: HubHistory()
        val history = HubHistory(
            daily = mergeHistory(savedHistory.daily, stats.historyPreview.daily),
            monthly = mergeHistory(savedHistory.monthly, stats.historyPreview.monthly),
        )
        return HubSnapshot(
            health = decodeHealth(healthRaw),
            stats = stats,
            history = history,
            subscriptions = subscriptionsRaw?.let(::decodeSubscriptions) ?: HubSubscriptions(),
            capturedAt = capturedAt,
            fromCache = fromCache,
            stale = fromCache,
        )
    }

    fun mergeHistory(saved: List<HistoryPoint>, incoming: List<HistoryPoint>): List<HistoryPoint> =
        io.github.theminionooo.tokenmonitor.domain.mergeUsageHistory(saved, incoming)

    /** Returns null for comments, malformed events, and future SSE event types. */
    fun decodeStatsStreamEvent(data: String): HubStats? = runCatching {
        val root = parseObject(data)
        val stats = root.objectField("stats") ?: return null
        stats.toStatsDto().toDomain()
    }.getOrNull()

    private fun parseObject(raw: String): JsonObject = json.parseToJsonElement(raw).objectOrNull()
        ?: throw HubProtocolException("The Hub returned a JSON value where an object was expected.")

    private fun decodeHealthDto(root: JsonObject) = HubHealthDto(
        ok = root.boolean("ok") ?: false,
        role = root.string("role"),
        runtime = root.string("runtime"),
        hubBuild = root.objectField("hubBuild")?.string("runtimeBuildId")
            ?: root.string("hubBuild"),
        deviceCount = root.long("deviceCount").toIntSafely(),
        secretRequired = root.boolean("secretRequired") ?: false,
        now = root.string("now"),
    )

    private fun JsonObject.toStatsDto(): HubStatsDto {
        val limitsObject = objectField("limits")
        return HubStatsDto(
            updatedAt = string("updatedAt"),
            periods = objectField("periods")?.mapValues { (_, value) ->
                value.objectOrNull()?.toPeriodDto() ?: HubPeriodDto()
            }.orEmpty(),
            devices = array("devices").mapNotNull { it.objectOrNull()?.toDeviceDto() },
            limitsUpdatedAt = limitsObject?.string("updatedAt").orEmpty(),
            limits = limitsObject?.array("providers")
                ?.mapNotNull { it.objectOrNull()?.toLimitAccountDto() }
                .orEmpty(),
            historyPreview = objectField("historyPreview")?.toHistoryDto() ?: HubHistoryDto(),
            subscriptionsUpdatedAt = string("subscriptionsUpdatedAt"),
            staleAfterMs = long("staleAfterMs"),
        )
    }

    private fun JsonObject.toPeriodDto(): HubPeriodDto {
        val sessions = objectField("sessions")?.mapNotNull { (key, value) ->
            value.objectOrNull()?.toSessionDto(key)
        }.orEmpty()
        val projects = objectField("projects")?.mapNotNull { (key, value) ->
            value.objectOrNull()?.toProjectDto(key)
        }.orEmpty().map { project ->
            if (project.sessionCount > 0) project else project.copy(
                sessionCount = sessions.count { session ->
                    session.projectLabel.equals(project.label, ignoreCase = true)
                },
            )
        }
        val throughputCapability = objectField("capabilities")?.boolean("throughput")
        val hasThroughputShape = listOf(
            "timedTokens" to "timed_tokens",
            "timedOutputTokens" to "timed_output_tokens",
            "timedDurationMs" to "timed_duration_ms",
        ).all { (camel, snake) -> long(camel) != null || long(snake) != null }
        return HubPeriodDto(
            throughputAvailable = throughputCapability ?: hasThroughputShape,
            timedTokens = (long("timedTokens") ?: long("timed_tokens") ?: 0).coerceAtLeast(0),
            timedOutputTokens = (long("timedOutputTokens") ?: long("timed_output_tokens") ?: 0).coerceAtLeast(0),
            timedDurationMs = (long("timedDurationMs") ?: long("timed_duration_ms") ?: 0).coerceAtLeast(0),
            totalTokens = long("totalTokens") ?: long("total_tokens") ?: 0,
            costUsd = double("costUsd") ?: double("cost_usd") ?: double("cost") ?: 0.0,
            clients = longMap("clients"),
            clientCosts = doubleMap("clientCosts"),
            models = longMap("models"),
            modelCosts = doubleMap("modelCosts"),
            clientModels = objectField("clientModels")?.mapValues { (_, value) -> JsonObject(mapOf("values" to value)).longMap("values") }.orEmpty(),
            clientModelCosts = objectField("clientModelCosts")?.mapValues { (_, value) -> JsonObject(mapOf("values" to value)).doubleMap("values") }.orEmpty(),
            clientCacheReads = longMap("clientCacheReads"),
            clientCacheWrites = longMap("clientCacheWrites"),
            clientOutputs = longMap("clientOutputs"),
            clientUnclassifiedTokens = longMap("clientUnclassifiedTokens"),
            modelCacheReads = longMap("modelCacheReads"),
            modelCacheWrites = longMap("modelCacheWrites"),
            modelOutputs = longMap("modelOutputs"),
            modelUnclassifiedTokens = longMap("modelUnclassifiedTokens"),
            projects = projects,
            sessions = sessions,
        )
    }

    private fun JsonObject.toProjectDto(key: String) = HubProjectDto(
        id = string("projectId").ifBlank { string("id").ifBlank { key } },
        label = string("projectLabel").ifBlank { string("label") },
        totalTokens = long("totalTokens") ?: long("tokens") ?: 0,
        costUsd = double("costUsd") ?: 0.0,
        sessionCount = (long("sessionCount") ?: 0).toIntSafely(),
        clients = longMap("clients"),
    )

    private fun JsonObject.toSessionDto(key: String) = HubSessionDto(
        id = string("sessionId").ifBlank { string("id").ifBlank { key } },
        title = string("title").trim().take(96),
        client = string("client"),
        projectLabel = string("projectLabel"),
        totalTokens = long("totalTokens") ?: 0,
        costUsd = double("costUsd") ?: 0.0,
        modelNames = objectField("models")?.keys?.toList().orEmpty(),
        messageCount = (long("messageCount") ?: 0).toIntSafely(),
        startedAt = string("startedAt"),
        lastUsedAt = string("lastUsedAt"),
        sessionKind = string("sessionKind"),
        contextTokens = long("contextTokens") ?: 0,
        contextWindow = long("contextWindow") ?: 0,
        turnEnded = boolean("turnEnded"),
        archived = boolean("archived") == true || boolean("deleted") == true || boolean("sourceDeleted") == true,
    )

    private fun JsonObject.toDeviceDto() = HubDeviceDto(
        id = string("deviceId").ifBlank { string("id") },
        hostname = string("hostname"),
        platform = string("platform"),
        osName = string("osName"),
        osVersion = string("osVersion"),
        updatedAt = string("updatedAt"),
        receivedAt = string("receivedAt"),
        ageMs = long("ageMs"),
        stale = boolean("stale") ?: false,
        syncUploadIntervalMs = long("syncUploadIntervalMs"),
        historyAvailable = boolean("historyAvailable"),
        history = objectField("history")?.toHistoryDto() ?: HubHistoryDto(),
        trackedClients = array("trackedClients").mapNotNull { it.stringOrNull() },
        periods = objectField("periods")?.mapValues { (_, value) ->
            value.objectOrNull()?.toPeriodDto() ?: HubPeriodDto()
        }.orEmpty(),
    )

    private fun JsonObject.toLimitAccountDto() = HubLimitAccountDto(
        provider = string("provider"),
        accountName = string("accountName"),
        accountEmail = string("accountEmail").ifBlank { string("email") },
        plan = string("plan").ifBlank { string("planLabel") }.ifBlank {
            if (string("provider") in setOf("typesafe", "stepfun")) string("accountLabel") else ""
        },
        status = string("status"),
        sourceDeviceId = string("sourceDeviceId"),
        updatedAt = string("updatedAt"),
        windows = array("windows").mapNotNull { it.objectOrNull()?.toLimitWindowDto() },
    )

    private fun JsonObject.toLimitWindowDto() = HubLimitWindowDto(
        kind = string("kind"),
        label = string("label"),
        usedPercent = double("usedPercent"),
        remainingPercent = double("remainingPercent"),
        remaining = double("remaining"),
        resetsAt = string("resetsAt"),
        metric = string("metric"),
        currency = string("currency"),
        detail = string("detail"),
        showMeter = boolean("showMeter"),
        boundaryKind = string("boundaryKind"),
    )

    private fun JsonObject.toHistoryDto() = HubHistoryDto(
        daily = array("daily").mapNotNull { it.objectOrNull()?.toHistoryPointDto("date") },
        monthly = array("monthly").mapNotNull { it.objectOrNull()?.toHistoryPointDto("month") },
    )

    private fun JsonObject.toHistoryPointDto(labelField: String) = HubHistoryPointDto(
        label = string(labelField),
        tokens = long("tokens") ?: 0,
        costUsd = double("cost") ?: double("costUsd") ?: 0.0,
        messages = long("messages") ?: 0,
        activeTimeMs = long("activeTimeMs") ?: 0,
        cacheReadTokens = long("cacheReadTokens") ?: 0,
        cacheWriteTokens = long("cacheWriteTokens") ?: 0,
        outputTokens = long("outputTokens") ?: 0,
        unclassifiedTokens = long("unclassifiedTokens") ?: 0,
        tokenComponentsAvailable = boolean("tokenComponentsAvailable") ?: false,
        perClient = attributionMap("perClient"),
        perModel = attributionMap("perModel"),
    )

    private fun JsonObject.attributionMap(name: String): Map<String, HubHistoryAttributionDto> =
        objectField(name)?.mapNotNull { (key, value) ->
            value.objectOrNull()?.let { entry ->
                key to HubHistoryAttributionDto(
                    tokens = entry.long("tokens") ?: 0,
                    costUsd = entry.double("cost") ?: entry.double("costUsd") ?: 0.0,
                    cacheReadTokens = entry.long("cacheReadTokens") ?: 0,
                    cacheWriteTokens = entry.long("cacheWriteTokens") ?: 0,
                    outputTokens = entry.long("outputTokens") ?: 0,
                    unclassifiedTokens = entry.long("unclassifiedTokens")
                        ?: if (entry.boolean("tokenComponentsAvailable") == true) 0 else entry.long("tokens") ?: 0,
                )
            }
        }?.toMap().orEmpty()

    private fun JsonObject.toSubscriptionsDto() = HubSubscriptionsDto(
        updatedAt = string("updatedAt"),
        entries = array("subscriptions").mapNotNull { it.objectOrNull()?.toSubscriptionDto() },
    )

    private fun JsonObject.toSubscriptionDto() = HubSubscriptionDto(
        id = string("id"),
        provider = string("provider"),
        planName = string("planName"),
        amountMinor = long("amountMinor") ?: 0,
        currency = string("currency").ifBlank { "USD" },
        startDate = string("startDate"),
        interval = string("interval").ifBlank { "month" },
        autoRenew = boolean("autoRenew") ?: true,
    )

    private fun HubHealthDto.toDomain() = HubHealth(
        ok = ok,
        role = role,
        runtime = runtime,
        hubBuild = hubBuild,
        deviceCount = deviceCount,
        secretRequired = secretRequired,
        now = now,
    )

    private fun HubStatsDto.toDomain() = HubStats(
        updatedAt = updatedAt,
        periods = periods.mapValues { it.value.toDomain() },
        devices = devices.map { it.toDomain() },
        limits = HubLimits(limitsUpdatedAt, limits.map { it.toDomain() }),
        historyPreview = historyPreview.toDomain(),
        subscriptionsUpdatedAt = subscriptionsUpdatedAt,
        staleAfterMs = staleAfterMs,
    )

    private fun HubPeriodDto.toDomain() = UsagePeriod(
        throughputAvailable = throughputAvailable,
        timedTokens = timedTokens,
        timedOutputTokens = timedOutputTokens,
        timedDurationMs = timedDurationMs,
        totalTokens = totalTokens.coerceAtLeast(0),
        costUsd = costUsd.coerceAtLeast(0.0),
        clients = clients,
        clientCosts = clientCosts,
        models = models,
        modelCosts = modelCosts,
        clientModels = clientModels,
        clientModelCosts = clientModelCosts,
        clientCacheReads = clientCacheReads,
        clientCacheWrites = clientCacheWrites,
        clientOutputs = clientOutputs,
        clientUnclassifiedTokens = clientUnclassifiedTokens,
        modelCacheReads = modelCacheReads,
        modelCacheWrites = modelCacheWrites,
        modelOutputs = modelOutputs,
        modelUnclassifiedTokens = modelUnclassifiedTokens,
        projects = projects.map { it.toDomain() },
        sessions = sessions.map { it.toDomain() },
    )

    private fun HubProjectDto.toDomain() = ProjectUsage(
        id = id,
        label = label,
        totalTokens = totalTokens.coerceAtLeast(0),
        costUsd = costUsd.coerceAtLeast(0.0),
        sessionCount = sessionCount.coerceAtLeast(0),
        clients = clients,
    )

    private fun HubSessionDto.toDomain() = SessionUsage(
        id = id,
        title = title,
        client = client,
        projectLabel = projectLabel,
        totalTokens = totalTokens.coerceAtLeast(0),
        costUsd = costUsd.coerceAtLeast(0.0),
        modelNames = modelNames,
        messageCount = messageCount.coerceAtLeast(0),
        startedAt = startedAt,
        lastUsedAt = lastUsedAt,
        sessionKind = sessionKind,
        contextTokens = contextTokens.coerceAtLeast(0),
        contextWindow = contextWindow.coerceAtLeast(0),
        turnEnded = turnEnded,
        archived = archived,
    )

    private fun HubDeviceDto.toDomain() = DeviceUsage(
        id = id,
        hostname = hostname,
        platform = platform,
        osName = osName,
        osVersion = osVersion,
        updatedAt = updatedAt,
        receivedAt = receivedAt,
        ageMs = ageMs,
        stale = stale,
        syncUploadIntervalMs = syncUploadIntervalMs,
        historyAvailable = historyAvailable,
        history = history.toDomain(),
        trackedClients = trackedClients,
        periods = periods.mapValues { it.value.toDomain() },
    )

    private fun HubLimitAccountDto.toDomain() = LimitAccount(
        provider = provider,
        accountName = accountName,
        accountEmail = accountEmail,
        plan = plan,
        status = status,
        sourceDeviceId = sourceDeviceId,
        updatedAt = updatedAt,
        windows = windows.map { it.toDomain() },
    )

    private fun HubLimitWindowDto.toDomain() = LimitWindow(
        kind = kind,
        label = label,
        usedPercent = usedPercent,
        remainingPercent = remainingPercent,
        remaining = remaining,
        resetsAt = resetsAt,
        metric = metric,
        currency = currency,
        detail = detail,
        showMeter = showMeter,
        boundaryKind = boundaryKind,
    )

    private fun HubHistoryDto.toDomain() = HubHistory(
        daily = daily.map { it.toDomain() },
        monthly = monthly.map { it.toDomain() },
    )

    private fun HubHistoryPointDto.toDomain() = HistoryPoint(
        label = label,
        tokens = tokens.coerceAtLeast(0),
        costUsd = costUsd.coerceAtLeast(0.0),
        messages = messages.coerceAtLeast(0),
        activeTimeMs = activeTimeMs.coerceAtLeast(0),
        cacheReadTokens = cacheReadTokens.coerceAtLeast(0),
        cacheWriteTokens = cacheWriteTokens.coerceAtLeast(0),
        outputTokens = outputTokens.coerceAtLeast(0),
        unclassifiedTokens = unclassifiedTokens.coerceAtLeast(0),
        tokenComponentsAvailable = tokenComponentsAvailable,
        perClient = perClient.mapValues { it.value.toDomain() },
        perModel = perModel.mapValues { it.value.toDomain() },
    )

    private fun HubHistoryAttributionDto.toDomain() = HistoryAttribution(
        tokens = tokens.coerceAtLeast(0),
        costUsd = costUsd.coerceAtLeast(0.0),
        cacheReadTokens = cacheReadTokens.coerceAtLeast(0),
        cacheWriteTokens = cacheWriteTokens.coerceAtLeast(0),
        outputTokens = outputTokens.coerceAtLeast(0),
        unclassifiedTokens = unclassifiedTokens.coerceAtLeast(0),
    )

    private fun HubSubscriptionsDto.toDomain() = HubSubscriptions(
        updatedAt = updatedAt,
        entries = entries.map { it.toDomain() },
    )

    private fun HubSubscriptionDto.toDomain() = Subscription(
        id = id,
        provider = provider,
        planName = planName,
        amountMinor = amountMinor.coerceAtLeast(0),
        currency = currency,
        startDate = startDate,
        interval = interval,
        autoRenew = autoRenew,
    )

    private fun JsonObject.objectField(name: String): JsonObject? = this[name].objectOrNull()

    private fun JsonObject.array(name: String): JsonArray = this[name] as? JsonArray ?: JsonArray(emptyList())

    private fun JsonObject.string(name: String): String = this[name].stringOrNull().orEmpty()

    private fun JsonObject.long(name: String): Long? = this[name].numberOrNull()?.longOrNull

    private fun JsonObject.double(name: String): Double? = this[name].numberOrNull()?.doubleOrNull

    private fun JsonObject.boolean(name: String): Boolean? = this[name].numberOrNull()?.booleanOrNull

    private fun JsonObject.longMap(name: String): Map<String, Long> = objectField(name)?.mapNotNull { (key, value) ->
        value.numberOrNull()?.longOrNull?.let { key to it.coerceAtLeast(0) }
    }?.toMap().orEmpty()

    private fun JsonObject.doubleMap(name: String): Map<String, Double> = objectField(name)?.mapNotNull { (key, value) ->
        value.numberOrNull()?.doubleOrNull?.let { key to it.coerceAtLeast(0.0) }
    }?.toMap().orEmpty()

    private fun JsonElement?.objectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

    private fun JsonElement?.numberOrNull(): JsonPrimitive? = this as? JsonPrimitive

    private fun Long?.toIntSafely(): Int = when {
        this == null -> 0
        this > Int.MAX_VALUE -> Int.MAX_VALUE
        this < Int.MIN_VALUE -> Int.MIN_VALUE
        else -> toInt()
    }
}

class HubProtocolException(message: String) : IllegalArgumentException(message)
