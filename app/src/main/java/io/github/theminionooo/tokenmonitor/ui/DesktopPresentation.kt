package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.data.storage.DesktopOptions
import io.github.theminionooo.tokenmonitor.domain.*
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

internal fun convertedCost(usd: Double, options: DesktopOptions): String {
    val converted = usd * if (options.currency == "USD") 1.0 else options.usdRate
    if (!converted.isFinite()) return "—"
    val amount = java.math.BigDecimal.valueOf(converted)
        .setScale(6, java.math.RoundingMode.HALF_UP)
    val digits = if (kotlin.math.abs(amount.toDouble()) >= if (options.currency == "USD") 10.0 else 1.0) 2 else 4
    val symbol = mapOf("USD" to "$", "CNY" to "¥", "HKD" to "HK$", "TWD" to "NT$")[options.currency] ?: "$"
    return symbol + amount.setScale(digits, java.math.RoundingMode.HALF_UP).toPlainString()
}

/** Resolve once, without chaining. Several IDs can explicitly target the same model. */
private fun modelMatchKey(value: String) = value.trim().lowercase(Locale.ROOT)
    .replace(Regex("[._\\s]+"), "-").replace(Regex("-+"), "-").trim('-')

internal fun modelAlias(name: String, options: DesktopOptions): String = modelResolver(options, emptyList())(name)

internal fun modelResolver(options: DesktopOptions, discovered: List<String>): (String) -> String {
    val explicit = linkedMapOf<String, String>()
    options.modelAliases.forEach { (source, target) ->
        val key = modelMatchKey(source)
        if (explicit.size < 4096 && source.isNotBlank() && target.isNotBlank() && source.length <= 256 && target.length <= 256 && key != modelMatchKey(target)) explicit.putIfAbsent(key, target.trim())
    }
    fun leaf(value: String) = value.trim().split('/').lastOrNull { it.isNotEmpty() } ?: value.trim()
    val automatic = mutableMapOf<String, String>()
    if (options.modelGrouping != "off") discovered.map(String::trim).filter { it.isNotEmpty() && it.length <= 256 }.distinct().take(16384)
        .groupBy { modelMatchKey(leaf(it)) }.forEach { (identity, group) ->
            if (identity.isNotEmpty() && (options.modelGrouping == "prefix" || group.size >= 2)) {
                val chosen = group.minWithOrNull(compareBy<String>(
                    { if (it == leaf(it)) 0 else 1 },
                    { if (leaf(it) == leaf(it).lowercase(Locale.ROOT)) 0 else 1 },
                    { if (Regex("[._\\s]").containsMatchIn(leaf(it))) 1 else 0 },
                    { leaf(it).length }, { leaf(it).lowercase(Locale.ROOT) }, { leaf(it) }
                ))!!
                val canonical = leaf(chosen)
                group.filter { it != canonical }.forEach { if (automatic.size < 4096) automatic[modelMatchKey(it)] = canonical }
            }
        }
    return { model -> explicit[modelMatchKey(model)] ?: automatic[modelMatchKey(model)]?.let { explicit[modelMatchKey(it)] ?: it } ?: model }
}
internal fun presentSnapshot(snapshot: HubSnapshot, options: DesktopOptions): HubSnapshot {
    val discovered = snapshot.stats.periods.values.flatMap { it.models.keys } + snapshot.stats.devices.flatMap { device -> device.periods.values.flatMap { it.models.keys } } + snapshot.history.daily.flatMap { it.perModel.keys }
    val resolve = modelResolver(options, discovered)
    fun longs(values: Map<String, Long>): Map<String, Long> = values.entries.groupBy { resolve(it.key) }
        .mapValues { (_, rows) -> rows.sumOf { it.value } }
    fun costs(values: Map<String, Double>): Map<String, Double> = values.entries.groupBy { resolve(it.key) }
        .mapValues { (_, rows) -> rows.sumOf { it.value } }
    fun period(value: UsagePeriod) = value.copy(
        models = longs(value.models), modelCosts = costs(value.modelCosts),
        modelUnpricedTokens = longs(value.modelUnpricedTokens),
        clientModelUnpricedTokens = value.clientModelUnpricedTokens.mapValues { longs(it.value) },
        modelCacheReads = longs(value.modelCacheReads), modelCacheWrites = longs(value.modelCacheWrites),
        modelOutputs = longs(value.modelOutputs), modelUnclassifiedTokens = longs(value.modelUnclassifiedTokens),
        clientModels = value.clientModels.mapValues { longs(it.value) },
        clientModelCosts = value.clientModelCosts.mapValues { costs(it.value) },
        sessions = value.sessions.map { it.copy(modelNames = it.modelNames.map { name -> resolve(name) }.distinct(), modelTokens = longs(it.modelTokens)) },
    )
    fun history(value: HubHistory): HubHistory {
        fun points(rows: List<HistoryPoint>) = rows.map { row -> row.copy(perModel = row.perModel.entries
            .groupBy { resolve(it.key) }.mapValues { (_, items) -> HistoryAttribution(
                tokens = items.sumOf { it.value.tokens }, costUsd = items.sumOf { it.value.costUsd },
                unpricedTokens = items.sumOf { it.value.unpricedTokens },
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

/** Hub IDs carry the collector's configured device name. Local legacy aliases stay dormant. */
internal fun hubNamedSnapshot(snapshot: HubSnapshot): HubSnapshot = snapshot.copy(
    stats = snapshot.stats.copy(devices = snapshot.stats.devices.map { device ->
        device.copy(hostname = device.id.ifBlank { device.hostname.ifBlank { "device" } })
    }),
)

internal val LocalDesktopOptions = androidx.compose.runtime.staticCompositionLocalOf { DesktopOptions() }
internal fun quotaAccountKey(account: LimitAccount): String = listOf(account.provider, account.accountKey.ifBlank { account.accountEmail.ifBlank { account.accountName } }, account.productLabel).joinToString("|")
internal fun quotaWindowKey(account: LimitAccount, window: LimitWindow): String = quotaAccountKey(account) + "|" + window.kind + "|" + window.label

internal fun rankedUsageNames(tokens: Map<String, Long>, costs: Map<String, Double>, costMetric: Boolean, models: Boolean, options: DesktopOptions): List<String> {
    val pins = if (models) options.pinnedModels else options.pinnedTools
    val order = if (models) options.modelOrder else options.toolOrder
    return tokens.keys.sortedWith(compareBy<String>(
        { if (it in pins) 0 else 1 },
        { order.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE },
    ).thenByDescending { if (costMetric) costs[it] ?: 0.0 else tokens[it]?.toDouble() ?: 0.0 }.thenBy { it })
}
