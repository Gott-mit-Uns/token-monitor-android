package dev.bing.tokenmonitor

import java.math.BigInteger
import java.text.Collator
import java.util.Locale

data class ToolTokenDetail(val key: String, val tokens: Long?, val percent: String? = null)
data class ToolModelDetail(val key: String, val usage: UsageBreakdown, val percent: String?, val unattributed: Boolean = false)
data class ToolDetailProjection(val tokens: List<ToolTokenDetail>, val models: List<ToolModelDetail>, val hasTokens: Boolean) {
    val hasModels get() = models.isNotEmpty()
    val canSwitch get() = hasTokens && hasModels
    val canExpand get() = hasTokens || hasModels
    fun mode(requested: String): String = if (hasModels && (requested == "models" || !hasTokens)) "models" else "tokens"
}

/** Pure desktop toolDetails display projection; never mutates the Hub period. */
object ToolDetails {
    fun percentLabel(value: Double?): String? {
        if (value == null) return null
        val percent = if (value.isFinite()) value.coerceAtLeast(0.0) else 0.0
        return if (percent > 0 && percent < 1) "<1%" else "${Math.round(percent.coerceAtMost(100.0))}%"
    }
    fun inputPercentages(parts: TokenComponents): Pair<Double?, Double?> {
        val read = parts.cacheRead?.takeIf { it >= 0 }?.toDouble()
        val miss = parts.inputMiss?.takeIf { it >= 0 }?.toDouble()
        if (read == null || miss == null) return null to null
        val input = read + miss // Double addition agrees with desktop and cannot overflow Long.
        return (if (input > 0) read / input * 100 else 0.0) to (if (input > 0) miss / input * 100 else 0.0)
    }
    fun tokenRows(parts: TokenComponents): List<ToolTokenDetail> {
        val (hit,miss) = inputPercentages(parts)
        return buildList {
            add(ToolTokenDetail("hit",parts.cacheRead,percentLabel(hit)))
            add(ToolTokenDetail("miss",parts.inputMiss,percentLabel(miss)))
            add(ToolTokenDetail("output",parts.output))
            if (parts.unclassified == null || parts.unclassified > 0) add(ToolTokenDetail("unclassified",parts.unclassified))
        }
    }
    fun project(value: UsageBreakdown?, names: ModelNames? = null, currency: UsageCurrency = UsageCurrency()): ToolDetailProjection {
        val parts = value?.components ?: TokenComponents(null,null,null,null,null)
        val hasTokens = listOf(parts.cacheRead,parts.inputMiss,parts.output,parts.unclassified).any { it != null }
        val source = value?.let { UsageAttribution.toolModels(it,currency) }.orEmpty()
        val actual = source - UsageAttribution.UNATTRIBUTED
        val groups = names?.projectDetails(actual,actual.keys) ?: actual
        val collator = Collator.getInstance(Locale.ENGLISH)
        val rows = groups.map { (key,model) -> Triple(key,model,false) } + source[UsageAttribution.UNATTRIBUTED]?.let { listOf(Triple(UsageAttribution.UNATTRIBUTED,it,true)) }.orEmpty()
        val models = rows.map { (key,model,unattributed) ->
            val total = value?.tokens
            val percent = model.tokens?.takeIf { it > 0 }?.let { count ->
                total?.let { percentLabel(if (it > 0) count.toDouble()/it*100 else 0.0) }
            }
            ToolModelDetail(key,model,percent,unattributed)
        }.sortedWith { a,b ->
            val count = (b.usage.tokens ?: 0).compareTo(a.usage.tokens ?: 0)
            val cost = (b.usage.cost?.toDoubleOrNull() ?: 0.0).compareTo(a.usage.cost?.toDoubleOrNull() ?: 0.0)
            if (count != 0) count else if (cost != 0) cost else collator.compare(a.key,b.key)
        }
        return ToolDetailProjection(tokenRows(parts),models,hasTokens)
    }
}
