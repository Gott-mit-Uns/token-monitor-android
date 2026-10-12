package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class TopUp(val date: String, val amount: BigDecimal?)
data class SubscriptionBinding(val profileName:String="",val accountKey:String="",val accountEmail:String="")

data class Subscription(val provider: String, val plan: String?, val amount: String?, val currency: String?, val interval: String?, val count: Long?, val start: String?, val end: String?, val autoRenew: Boolean?,
    val kind: String? = null, val override: String? = null, val account: String? = null, val topUps: List<TopUp> = emptyList(),
    val id:String? = null,val note:String? = null,val binding:SubscriptionBinding? = null) {
    private fun date(value: String?) = value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    fun intervalMonths(): Long? = count?.takeIf { it in 1..24 }?.let { when (interval) { "month" -> it; "year" -> it * 12; else -> null } }
    fun nextRenewal(today: LocalDate = LocalDate.now()): LocalDate? {
        if (kind == "topup" || autoRenew != true) return null
        date(override)?.takeIf { !it.isBefore(today) }?.let { return it }
        val anchor = date(start) ?: return null
        val months = intervalMonths() ?: return null
        var n = (ChronoUnit.MONTHS.between(java.time.YearMonth.from(anchor), java.time.YearMonth.from(today)) / months).coerceAtLeast(0)
        while (anchor.plusMonths(n * months).isBefore(today)) n++
        return anchor.plusMonths(n * months)
    }
    fun coverageStop(): LocalDate? = if(kind=="topup") null else date(end) ?: if (autoRenew == false) date(start)?.let {anchor -> intervalMonths()?.let {anchor.plusMonths(it)}} else null
    fun coverageEnd(today:LocalDate=LocalDate.now()):LocalDate? = if(kind=="topup") null else coverageStop() ?: nextRenewal(today)
    fun recordedTotal(today: LocalDate = LocalDate.now()): BigDecimal? {
        if (kind == "topup") {
            val applicable = topUps.filter { date(it.date)?.let { day -> !day.isAfter(today) } == true }
            if (applicable.any { it.amount == null }) return null
            return applicable.fold(BigDecimal.ZERO) { sum, row -> sum + row.amount!! }
        }
        if (autoRenew == null) return null
        val anchor = date(start) ?: return null
        val months = intervalMonths() ?: return null
        val price = amount?.toBigDecimalOrNull() ?: return null
        val until = coverageStop()?.minusDays(1)?.let { if (it.isBefore(today)) it else today } ?: today
        if (until.isBefore(anchor)) return BigDecimal.ZERO
        var n = (ChronoUnit.MONTHS.between(java.time.YearMonth.from(anchor), java.time.YearMonth.from(until)) / months).coerceAtLeast(0)
        while (anchor.plusMonths(n * months).isAfter(until) && n > 0) n--
        while (!anchor.plusMonths((n + 1) * months).isAfter(until)) n++
        return price.multiply(BigDecimal.valueOf(n + 1))
    }
}
object SubscriptionParser {
    fun parse(body: String): List<Subscription> {
        val root = Json.parseToJsonElement(body).jsonObject
        val rows = root["subscriptions"] as? JsonArray ?: error("Missing subscription list")
        return rows.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            fun text(key: String) = (row[key] as? JsonPrimitive)?.contentOrNull
            val minor = (row["amountMinor"] as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 }
            val topUps = (row["topUps"] as? JsonArray).orEmpty().mapNotNull { element ->
                val top = element as? JsonObject ?: return@mapNotNull null
                val day = (top["date"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                if (runCatching { LocalDate.parse(day) }.isFailure) return@mapNotNull null
                val value = (top["amountMinor"] as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 }?.let { BigDecimal.valueOf(it, 2) }
                TopUp(day, value)
            }.sortedByDescending { it.date }
            Subscription(text("provider") ?: "__unknown_provider__", text("planName"), minor?.let { BigDecimal.valueOf(it, 2).toPlainString() },
                text("currency"), text("interval"), (row["intervalCount"] as? JsonPrimitive)?.longOrNull, text("startDate"), text("endDate"),
                (row["autoRenew"] as? JsonPrimitive)?.booleanOrNull, text("kind"), text("nextRenewalOverride"),
                (row["binding"] as? JsonObject)?.let { binding ->
                    listOf("profileName", "accountEmail").firstNotNullOfOrNull { key -> (binding[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } }
                }, topUps,text("id")?.trim()?.takeIf {it.isNotEmpty() && it.length<=256},text("note")?.takeIf {it.isNotBlank()},
                (row["binding"] as? JsonObject)?.let {binding->
                    fun field(key:String)=(binding[key] as? JsonPrimitive)?.takeIf {it.isString}?.content?.trim().orEmpty()
                    SubscriptionBinding(field("profileName"),field("accountKey"),field("accountEmail").lowercase(java.util.Locale.ROOT))
                })
        }
    }
}
