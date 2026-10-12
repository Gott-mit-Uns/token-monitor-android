package dev.bing.tokenmonitor

import java.math.BigDecimal
import java.math.BigInteger

/** Display-only counterpart of desktop usageAttributionRows/modelRowsForTool. */
object UsageAttribution {
    const val UNATTRIBUTED = "__unattributed"
    private fun decimal(value: String?) = value?.takeIf { it.length <= 128 }?.let { runCatching { BigDecimal(it) }.getOrNull() }
        ?.takeIf { it.signum() >= 0 && it.precision() <= 100 && it.scale() in -100..100 }
    fun rows(counts: Map<String, Long>, details: Map<String, UsageBreakdown>, total: Long?, cost: String?, unpriced: Long?, currency: UsageCurrency = UsageCurrency()): Map<String, UsageBreakdown> {
        val all = (counts.keys + details.keys).associateWith { id ->
            val value = details[id] ?: UsageBreakdown(counts[id], null, TokenComponents(null,null,null,null,null))
            value.copy(tokens = counts[id] ?: value.tokens)
        }.filterValues { (it.tokens ?: 0) > 0 || (decimal(it.cost)?.signum() ?: 0) > 0 || (it.unpriced ?: 0) > 0 }
        fun remainder(value: Long?, parts: List<Long?>): Long? = value?.let { target ->
            (BigInteger.valueOf(target) - parts.fold(BigInteger.ZERO) { sum, part -> sum + BigInteger.valueOf(part ?: 0) }).max(BigInteger.ZERO).longValueExact()
        }
        val tokensLeft = remainder(total, all.values.map { it.tokens })
        val unpricedLeft = remainder(unpriced, all.values.map { it.unpriced })
        val costLeft = decimal(cost)?.let { UsageCurrency.remainderCost(cost, all.values.map { it.cost }) }
        val visibleCost = (decimal(costLeft)?.signum() ?: 0) > 0 && currency.format(costLeft) != currency.format("0")
        if ((tokensLeft ?: 0) <= 0 && (unpricedLeft ?: 0) <= 0 && !visibleCost) return all
        return all + (UNATTRIBUTED to UsageBreakdown(tokensLeft, costLeft, TokenComponents(null,null,null,null,null), unpriced = unpricedLeft))
    }
    fun period(value: Period, currency: UsageCurrency): Period {
        val tools = rows(value.tools,value.toolDetails,value.tokens,value.cost,value.unpriced,currency)
        val models = rows(value.models,value.modelDetails,value.tokens,value.cost,value.unpriced,currency)
        return value.copy(tools=tools.mapNotNull { (id,row) -> row.tokens?.let { id to it } }.toMap(),toolDetails=tools,
            models=models.mapNotNull { (id,row) -> row.tokens?.let { id to it } }.toMap(),modelDetails=models)
    }
    fun toolModels(value: UsageBreakdown, currency: UsageCurrency = UsageCurrency()): Map<String, UsageBreakdown> {
        if (!value.modelDataAvailable && value.models.isEmpty() && value.modelCosts.isEmpty() && value.modelUnpriced.isEmpty()) return emptyMap()
        val details=(value.models.keys+value.modelCosts.keys+value.modelUnpriced.keys).associateWith { id ->
            UsageBreakdown(value.models[id],value.modelCosts[id],TokenComponents(null,null,null,null,null),unpriced=value.modelUnpriced[id])
        }
        return rows(value.models,details,value.tokens,value.cost,value.unpriced,currency)
    }
}
