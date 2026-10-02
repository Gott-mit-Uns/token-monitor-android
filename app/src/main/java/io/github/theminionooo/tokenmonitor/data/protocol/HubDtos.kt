package io.github.theminionooo.tokenmonitor.data.protocol

/**
 * Wire DTOs for the v0.65.0 Hub protocol. They deliberately have defaults because
 * the Hub may add fields or an older Hub may omit optional fields.
 */
internal data class HubHealthDto(
    val ok: Boolean = false,
    val role: String = "",
    val runtime: String = "",
    val hubBuild: String = "",
    val deviceCount: Int = 0,
    val secretRequired: Boolean = false,
    val now: String = "",
)

internal data class HubPeriodDto(
    val throughputAvailable: Boolean = false,
    val timedTokens: Long = 0,
    val timedOutputTokens: Long = 0,
    val timedDurationMs: Long = 0,
    val totalTokens: Long = 0,
    val costUsd: Double = 0.0,
    val clients: Map<String, Long> = emptyMap(),
    val clientCosts: Map<String, Double> = emptyMap(),
    val models: Map<String, Long> = emptyMap(),
    val modelCosts: Map<String, Double> = emptyMap(),
    val clientModels: Map<String, Map<String, Long>> = emptyMap(),
    val clientModelCosts: Map<String, Map<String, Double>> = emptyMap(),
    val clientCacheReads: Map<String, Long> = emptyMap(),
    val clientCacheWrites: Map<String, Long> = emptyMap(),
    val clientOutputs: Map<String, Long> = emptyMap(),
    val clientUnclassifiedTokens: Map<String, Long> = emptyMap(),
    val modelCacheReads: Map<String, Long> = emptyMap(),
    val modelCacheWrites: Map<String, Long> = emptyMap(),
    val modelOutputs: Map<String, Long> = emptyMap(),
    val modelUnclassifiedTokens: Map<String, Long> = emptyMap(),
    val projects: List<HubProjectDto> = emptyList(),
    val sessions: List<HubSessionDto> = emptyList(),
)

internal data class HubProjectDto(
    val id: String = "",
    val label: String = "",
    val totalTokens: Long = 0,
    val costUsd: Double = 0.0,
    val sessionCount: Int = 0,
    val clients: Map<String, Long> = emptyMap(),
)

internal data class HubSessionDto(
    val id: String = "",
    val title: String = "",
    val client: String = "",
    val projectLabel: String = "",
    val totalTokens: Long = 0,
    val costUsd: Double = 0.0,
    val modelNames: List<String> = emptyList(),
    val messageCount: Int = 0,
    val startedAt: String = "",
    val lastUsedAt: String = "",
    val sessionKind: String = "",
    val contextTokens: Long = 0,
    val contextWindow: Long = 0,
    val turnEnded: Boolean? = null,
    val archived: Boolean = false,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
    val timedOutputTokens: Long = 0,
    val timedDurationMs: Long = 0,
    val promptCache: HubPromptCacheDto? = null,
)

internal data class HubPromptCacheDto(val observedAt: String, val ttlSeconds: Long)

internal data class HubDeviceDto(
    val id: String = "",
    val hostname: String = "",
    val platform: String = "",
    val osName: String = "",
    val osVersion: String = "",
    val updatedAt: String = "",
    val receivedAt: String = "",
    val ageMs: Long? = null,
    val stale: Boolean = false,
    val syncUploadIntervalMs: Long? = null,
    val historyAvailable: Boolean? = null,
    val history: HubHistoryDto = HubHistoryDto(),
    val trackedClients: List<String> = emptyList(),
    val periods: Map<String, HubPeriodDto> = emptyMap(),
)

internal data class HubLimitWindowDto(
    val kind: String = "",
    val label: String = "",
    val usedPercent: Double? = null,
    val remainingPercent: Double? = null,
    val remaining: Double? = null,
    val resetsAt: String = "",
    val metric: String = "",
    val currency: String = "",
    val detail: String = "",
    val showMeter: Boolean? = null,
    val boundaryKind: String = "",
)

internal data class HubLimitAccountDto(
    val provider: String = "",
    val accountName: String = "",
    val accountEmail: String = "",
    val plan: String = "",
    val status: String = "",
    val sourceDeviceId: String = "",
    val updatedAt: String = "",
    val windows: List<HubLimitWindowDto> = emptyList(),
)

internal data class HubStatsDto(
    val updatedAt: String = "",
    val periods: Map<String, HubPeriodDto> = emptyMap(),
    val devices: List<HubDeviceDto> = emptyList(),
    val limitsUpdatedAt: String = "",
    val limits: List<HubLimitAccountDto> = emptyList(),
    val historyPreview: HubHistoryDto = HubHistoryDto(),
    val subscriptionsUpdatedAt: String = "",
    val staleAfterMs: Long? = null,
)

internal data class HubHistoryPointDto(
    val label: String = "",
    val tokens: Long = 0,
    val costUsd: Double = 0.0,
    val messages: Long = 0,
    val activeTimeMs: Long = 0,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
    val outputTokens: Long = 0,
    val unclassifiedTokens: Long = 0,
    val tokenComponentsAvailable: Boolean = false,
    val perClient: Map<String, HubHistoryAttributionDto> = emptyMap(),
    val perModel: Map<String, HubHistoryAttributionDto> = emptyMap(),
)

internal data class HubHistoryAttributionDto(
    val tokens: Long = 0,
    val costUsd: Double = 0.0,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
    val outputTokens: Long = 0,
    val unclassifiedTokens: Long = 0,
)

internal data class HubHistoryDto(
    val daily: List<HubHistoryPointDto> = emptyList(),
    val monthly: List<HubHistoryPointDto> = emptyList(),
)

internal data class HubSubscriptionDto(
    val id: String = "",
    val provider: String = "",
    val planName: String = "",
    val amountMinor: Long = 0,
    val currency: String = "USD",
    val startDate: String = "",
    val interval: String = "month",
    val autoRenew: Boolean = true,
)

internal data class HubSubscriptionsDto(
    val updatedAt: String = "",
    val entries: List<HubSubscriptionDto> = emptyList(),
)
