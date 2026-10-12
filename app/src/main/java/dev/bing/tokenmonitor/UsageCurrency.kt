package dev.bing.tokenmonitor

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale

/** Desktop v0.68.0 currency display. Source USD amounts are never rewritten. */
class UsageCurrency(currency: String = "USD", private val overrides: Map<String, String> = emptyMap()) {
    companion object {
        val codes = listOf("USD", "TWD", "HKD", "CNY")
        val defaultRates = mapOf("USD" to "1", "TWD" to "31.5", "HKD" to "7.8", "CNY" to "6.8")
        private val symbols = mapOf("USD" to "$", "TWD" to "NT$", "HKD" to "HK$", "CNY" to "¥")
        fun normalize(value: String) = value.trim().uppercase(Locale.ROOT).takeIf { it in codes } ?: "USD"
        fun validRate(value: String): Boolean = value.length in 1..128 && runCatching {
            val amount = BigDecimal(value)
            amount.signum() > 0 && amount.precision() <= 100 && amount.scale() in -100..100 && amount.toDouble().isFinite()
        }.getOrDefault(false)
        // Number.toFixed rounds the exact binary value, rather than its shortest
        // decimal spelling. This retains desktop edge cases such as 10.005.
        private fun fixed(value: Double, digits: Int): String = if (value >= 1e21) scientific(value) else BigDecimal(value).setScale(digits, RoundingMode.HALF_UP).toPlainString()
        private fun scientific(value: Double): String {
            val exact = BigDecimal(value)
            val shortest = (1..17).asSequence().map { exact.round(MathContext(it, RoundingMode.HALF_EVEN)) }
                .first { it.toDouble() == value }.stripTrailingZeros()
            return shortest.toString().replace('E', 'e')
        }
        fun remainderCost(total: String?, parts: List<String?>): String? {
            val target = total?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return null
            val attributed = parts.fold(0.0) { sum, value -> sum + (value?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: 0.0) }
            return fixed((target - attributed).coerceAtLeast(0.0), 6)
        }
    }
    val code = normalize(currency)
    fun rateFor(code:String):String = normalize(code).let {normalized -> overrides[normalized]?.takeIf(::validRate) ?: defaultRates.getValue(normalized)}
    val rate: String get() = rateFor(code)
    private fun amount(value: String?): Double? = value?.takeIf { it.length <= 128 }?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    fun converted(value: String?): Double? {
        val source = amount(value) ?: return null
        val result = source * rate.toDouble()
        if (!result.isFinite()) return null
        return fixed(result, 6).toDouble()
    }
    fun format(value: String?): String {
        val value = converted(value) ?: return "—"
        val digits = if (value >= if (code == "USD") 10.0 else 1.0) 2 else 4
        return symbols.getValue(code) + fixed(value, digits)
    }
    fun headline(value: String?, unpriced: Long?): String = if ((unpriced ?: 0) > 0 && (amount(value) ?: 0.0) <= 0) "—" else format(value)
    fun label(value: String?, unpriced: BigInteger? = null, compact: Boolean = false,
        formatTokens: (BigInteger) -> String = { it.toString() }, unpricedLabel: String = "unpriced tokens"): String {
        if (unpriced == null || unpriced.signum() <= 0) return format(value)
        val known = (amount(value) ?: 0.0) > 0
        if (compact) return if (known) format(value) + " + ?" else "—"
        val missing = formatTokens(unpriced) + " " + unpricedLabel
        return if (known) format(value) + " + " + missing else "— ($missing)"
    }
}
