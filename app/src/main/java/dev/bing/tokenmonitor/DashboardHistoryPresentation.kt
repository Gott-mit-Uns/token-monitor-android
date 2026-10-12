package dev.bing.tokenmonitor

import java.math.BigDecimal
import java.math.BigInteger

/** The desktop getDashboardHistory IPC's display projection, after raw Hub history has been parsed. */
internal object DashboardHistoryPresentation {
    fun project(history: HubHistory, names: ModelNames?): HubHistory {
        if (names == null || !names.initialized || (names.aliases.isEmpty() && names.grouping == "off")) return history
        val observed = linkedSetOf<String>().apply {
            addAll(history.observedModelIds)
            // Hub wire rows are chronological; the native parser stores them newest first.
            for (rows in listOf(history.daily, history.monthly)) rows.sortedBy { it.date }.forEach { row ->
                addAll(objectKeys(row.perModel.keys))
            }
            history.summary?.favoriteModel?.takeIf { it.isNotBlank() }?.let(::add)
        }
        val sources = linkedSetOf<String>().apply {
            addAll(observed)
            history.daily.forEach { addAll(it.perModel.keys) }
            history.monthly.forEach { addAll(it.perModel.keys) }
            history.summary?.favoriteModel?.let(::add)
        }
        val labels = names.labels(sources, observed)
        val active = names.aliases.isNotEmpty() || observed.any { labels[it] != it }
        if (!active) return history
        fun label(key: String) = labels[key] ?: key
        fun row(raw: HistoryRow): HistoryRow {
            val keys = objectKeys(raw.perModel.keys)
            if (keys == raw.perModel.keys.toList() && keys.none { label(it) != it }) return raw
            val groups = linkedMapOf<String, MutableList<HistoryAttribution>>()
            keys.forEach { key -> groups.getOrPut(label(key)) { mutableListOf() }.add(raw.perModel.getValue(key)) }
            val folded = groups.mapValues { (_, values) -> if (values.size == 1) values.single() else merge(values) }
            // Object.fromEntries returns an object: canonical integer keys enumerate first.
            return raw.copy(perModel = objectKeys(folded.keys).associateWith { folded.getValue(it) })
        }
        val daily = history.daily.map(::row)
        val monthly = history.monthly.map(::row)
        val summary = history.summary?.let { raw ->
            val original = raw.favoriteModel
            if (!active || original.isNullOrEmpty()) raw else {
                val renamed = label(original)
                val favorite = aggregateLeader(daily) ?: renamed
                if (favorite == original) raw else raw.copy(favoriteModel = favorite)
            }
        }
        if (daily.indices.all { daily[it] === history.daily[it] } &&
            monthly.indices.all { monthly[it] === history.monthly[it] } && summary === history.summary) return history
        return history.copy(daily = daily, monthly = monthly, summary = summary)
    }

    private fun merge(values: List<HistoryAttribution>): HistoryAttribution {
        // The native wire contract treats missing required values as unknown. Unlike
        // JS num(null), this never upgrades absent tokens/cost/components into zero.
        val counts = values.map { it.presentationTokens() }
        val tokens = if (counts.any { it == null }) null else counts.fold(BigInteger.ZERO) { total, value -> total + value!! }
        val costs = values.map { it.cost?.let { text -> runCatching { BigDecimal(text) }.getOrNull() } }
        val cost = if (costs.any { it == null }) null else costs.fold(BigDecimal.ZERO) { total, value -> total + value!! }.toPlainString()
        fun component(select: (TokenComponents) -> Long?) = sumRequired(values.map { value ->
            select(value.components) ?: if (value.exact == true) 0L else null
        })
        fun unclassified(value: HistoryAttribution) = value.components.unclassified ?: if (value.exact == true) 0L
            else value.presentationTokens()?.let(::asLong)
        val exact = when { values.any { it.exact == false } -> false; values.all { it.exact == true } -> true; else -> null }
        return HistoryAttribution(tokens?.let(::asLong), cost, sumOptional(values.map { it.unpricedTokens }),
            TokenComponents(component { it.cacheRead }, component { it.cacheWrite }, component { it.output },
                sumRequired(values.map(::unclassified)), sumRequired(values.map { it.components.inputMiss })),
            exact, sumOptional(values.map { it.messages }), displayTokens = tokens)
    }

    private fun aggregateLeader(rows: List<HistoryRow>): String? {
        val totals = linkedMapOf<String, BigInteger>()
        for (row in rows.sortedBy { it.date }) for (key in objectKeys(row.perModel.keys)) {
            // A malformed/unknown native bucket cannot support a newly invented leader.
            val value = row.perModel.getValue(key).presentationTokens() ?: return null
            totals[key] = (totals[key] ?: BigInteger.ZERO) + value
        }
        var favorite: String? = null
        var most = BigInteger.valueOf(-1)
        for (key in objectKeys(totals.keys)) {
            val value = totals.getValue(key)
            if (value > most) { favorite = key; most = value }
        }
        return favorite
    }

    private fun sumRequired(values: List<Long?>): Long? = if (values.any { it == null }) null else sumOptional(values)
    private fun sumOptional(values: List<Long?>): Long? = values.filterNotNull().takeIf { it.isNotEmpty() }?.let { known ->
        if (known.any { it < 0 }) null else asLong(known.fold(BigInteger.ZERO) { sum, value -> sum + BigInteger.valueOf(value) })
    }
    private fun asLong(value: BigInteger): Long? = runCatching { value.longValueExact() }.getOrNull()
    private fun objectKeys(keys: Collection<String>): List<String> = keys.sortedWith { a, b ->
        fun index(key: String) = key.toLongOrNull()?.takeIf { it in 0..4294967294L && it.toString() == key }
        val left = index(a); val right = index(b)
        when { left != null && right != null -> left.compareTo(right); left != null -> -1; right != null -> 1; else -> 0 }
    }
}
