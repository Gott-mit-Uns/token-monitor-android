package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.data.storage.DesktopOptions
import io.github.theminionooo.tokenmonitor.domain.*
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

internal fun convertedCost(usd: Double, options: DesktopOptions): String = NumberFormat.getCurrencyInstance(Locale.US).apply {
    currency = Currency.getInstance(options.currency)
}.format(usd * if (options.currency == "USD") 1.0 else options.usdRate)

/** Resolve once, without chaining. Several IDs can explicitly target the same model. */
internal fun modelAlias(name: String, options: DesktopOptions): String = options.modelAliases[name] ?: name
internal fun presentSnapshot(snapshot: HubSnapshot, options: DesktopOptions): HubSnapshot {
    fun longs(values: Map<String, Long>): Map<String, Long> = values.entries.groupBy { modelAlias(it.key, options) }
        .mapValues { (_, rows) -> rows.sumOf { it.value } }
    fun costs(values: Map<String, Double>): Map<String, Double> = values.entries.groupBy { modelAlias(it.key, options) }
        .mapValues { (_, rows) -> rows.sumOf { it.value } }
    fun period(value: UsagePeriod) = value.copy(
        models = longs(value.models), modelCosts = costs(value.modelCosts),
        modelCacheReads = longs(value.modelCacheReads), modelCacheWrites = longs(value.modelCacheWrites),
        modelOutputs = longs(value.modelOutputs), modelUnclassifiedTokens = longs(value.modelUnclassifiedTokens),
        clientModels = value.clientModels.mapValues { longs(it.value) },
        clientModelCosts = value.clientModelCosts.mapValues { costs(it.value) },
        sessions = value.sessions.map { it.copy(modelNames = it.modelNames.map { name -> modelAlias(name, options) }.distinct()) },
    )
    fun history(value: HubHistory): HubHistory {
        fun points(rows: List<HistoryPoint>) = rows.map { row -> row.copy(perModel = row.perModel.entries
            .groupBy { modelAlias(it.key, options) }.mapValues { (_, items) -> HistoryAttribution(
                tokens = items.sumOf { it.value.tokens }, costUsd = items.sumOf { it.value.costUsd },
                cacheReadTokens = items.sumOf { it.value.cacheReadTokens }, cacheWriteTokens = items.sumOf { it.value.cacheWriteTokens },
                outputTokens = items.sumOf { it.value.outputTokens }, unclassifiedTokens = items.sumOf { it.value.unclassifiedTokens },
            ) }) }
        return value.copy(daily = points(value.daily), monthly = points(value.monthly))
    }
    return snapshot.copy(stats = snapshot.stats.copy(
        periods = snapshot.stats.periods.mapValues { period(it.value) },
        devices = snapshot.stats.devices.map { it.copy(periods = it.periods.mapValues { row -> period(row.value) }, history = history(it.history)) },
        historyPreview = history(snapshot.stats.historyPreview),
    ), history = history(snapshot.history))
}

/** Hide rows, never subtract usage from totals or remove device records. */
internal fun visibleUsage(value: UsagePeriod, options: DesktopOptions) = value.copy(
    clients = value.clients.filterKeys { it !in options.hiddenTools },
    models = value.models.filterKeys { it !in options.hiddenModels },
    clientModels = value.clientModels.mapValues { it.value.filterKeys { key -> key !in options.hiddenModels } },
)

internal fun desktopText(chinese: String, english: String): String = if (uiLocale().language == "zh") chinese else english
