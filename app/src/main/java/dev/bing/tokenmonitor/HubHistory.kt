package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.YearMonth
import java.math.BigInteger

data class HistoryAttribution(val tokens: Long?, val cost: String?, val unpricedTokens: Long? = null,
    val components: TokenComponents = TokenComponents(null, null, null, null, null), val exact: Boolean? = null, val messages: Long? = null,
    internal val displayTokens: BigInteger? = null)
internal fun HistoryAttribution.presentationTokens(): BigInteger? = displayTokens?.takeIf { it.signum() >= 0 }
    ?: tokens?.takeIf { it >= 0 }?.let(BigInteger::valueOf)
data class HistoryRow(val date: String, val tokens: Long?, val cost: String?, val unpricedTokens: Long? = null,
    val perClient: Map<String, HistoryAttribution> = emptyMap(), val perModel: Map<String, HistoryAttribution> = emptyMap(),
    val activeTimeMs: Long? = null, val messages: Long? = null,
    val components: TokenComponents = TokenComponents(null, null, null, null, null), val exact: Boolean? = null)
data class HistorySummary(val totalTokens: Long?, val totalCost: String?, val activeDays: Long?, val currentStreak: Long?,
    val longestStreak: Long?, val activeTimeMs: Long?, val peakDayTokens: Long?, val favoriteModel: String?, val messages: Long?,
    val unpricedTokens: Long? = null)
data class HubHistory(val daily: List<HistoryRow>, val monthly: List<HistoryRow>, val summary: HistorySummary? = null,
    val observedModelIds: List<String> = emptyList(), val dailyProvided: Boolean = true, val monthlyProvided: Boolean = true)
object HistoryParser {
    fun parse(body: String): HubHistory {
        val root = Json.parseToJsonElement(body).jsonObject
        require(root["daily"] is JsonArray || root["monthly"] is JsonArray)
        fun count(row: JsonObject, key: String) = (row[key] as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 }
        fun money(row: JsonObject, key: String) = (row[key] as? JsonPrimitive)?.contentOrNull?.takeIf {
            it.length <= 128 && runCatching { java.math.BigDecimal(it).let { amount -> amount.signum() >= 0 && amount.precision() <= 100 && amount.scale() in -100..100 } }.getOrDefault(false)
        }
        fun components(row: JsonObject) = TokenComponents(count(row, "cacheReadTokens"), count(row, "cacheWriteTokens"), count(row, "outputTokens"), count(row, "unclassifiedTokens"), null)
        fun exact(row: JsonObject) = (row["tokenComponentsAvailable"] as? JsonPrimitive)?.booleanOrNull
        fun attribution(row: JsonObject, key: String) = (row[key] as? JsonObject).orEmpty().mapNotNull { (name, raw) ->
            val value = raw as? JsonObject ?: return@mapNotNull null
            if (name.isBlank() || name.length > 512) return@mapNotNull null
            name to HistoryAttribution(count(value, "tokens"), money(value, "cost"), count(value, "unpricedTokens"), components(value), exact(value), count(value, "messages"))
        }.toMap()
        fun rows(key: String, dateKey: String) = (root[key] as? JsonArray).orEmpty().mapNotNull { value ->
            val row = value as? JsonObject ?: return@mapNotNull null
            val date = (row[dateKey] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            if (runCatching { if (key == "daily") LocalDate.parse(date) else YearMonth.parse(date) }.isFailure) return@mapNotNull null
            HistoryRow(date, count(row, "tokens"), money(row, "cost"), count(row, "unpricedTokens"),
                attribution(row, "perClient"), attribution(row, "perModel"), count(row, "activeTimeMs"), count(row, "messages"), components(row), exact(row))
        }.sortedByDescending { it.date }
        val summary = (root["summary"] as? JsonObject)?.let { s -> HistorySummary(count(s, "totalTokens"), money(s, "totalCost"), count(s, "activeDays"),
            count(s, "currentStreak"), count(s, "longestStreak"), count(s, "activeTimeMs"), count(s, "peakDayTokens"),
            (s["favoriteModel"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it.length <= 512 }, count(s, "messages"), count(s, "unpricedTokens")) }
        // The desktop IPC discovers aliases before selecting a chart range. Retain
        // cost-only model keys as well; the encrypted cache still stores this raw body.
        val observed = linkedSetOf<String>()
        fun addModel(raw: String?) { raw?.trim()?.takeIf { it.isNotEmpty() && it.length <= 256 }
            ?.let { if (observed.size < 16384) observed.add(it) } }
        fun objectKeys(value: JsonElement?) = (value as? JsonObject)?.keys.orEmpty().sortedWith { a, b ->
            fun index(key: String) = key.toLongOrNull()?.takeIf { it in 0..4294967294L && it.toString() == key }
            val left = index(a); val right = index(b)
            when { left != null && right != null -> left.compareTo(right); left != null -> -1; right != null -> 1; else -> 0 }
        }
        fun clientModels(value: JsonElement?) { objectKeys(value).forEach { client ->
            objectKeys((value as? JsonObject)?.get(client)).forEach(::addModel)
        } }
        for (field in listOf("daily", "monthly")) (root[field] as? JsonArray).orEmpty().forEach { raw ->
            val row = raw as? JsonObject ?: return@forEach
            objectKeys(row["perModel"]).forEach(::addModel)
            clientModels(row["clientModelCosts"])
        }
        val summaryObject = root["summary"] as? JsonObject
        addModel((summaryObject?.get("favoriteModel") as? JsonPrimitive)?.contentOrNull)
        clientModels(summaryObject?.get("clientModelCosts"))
        return HubHistory(rows("daily", "date"), rows("monthly", "month"), summary, observed.toList(),
            dailyProvided = root["daily"] is JsonArray, monthlyProvided = root["monthly"] is JsonArray)
    }
}

data class HistoryRangeSummary(
    val start: LocalDate, val end: LocalDate, val calendarDays: Long,
    val recordedDays: Int, val knownTokenDays: Int, val knownCostDays: Int,
    val tokens: java.math.BigInteger, val cost: java.math.BigDecimal,
    val unpricedTokens: java.math.BigInteger, val rows: List<HistoryRow>
)

object HistoryRanges {
    fun summarize(history: HubHistory, days: Int?): HistoryRangeSummary? {
        require(days == null || days in 1..366)
        val grouped = history.daily.groupBy { it.date }
        val end = grouped.keys.maxOrNull()?.let(LocalDate::parse) ?: return null
        val start = if (days == null) LocalDate.parse(grouped.keys.minOrNull()!!) else end.minusDays(days.toLong() - 1)
        // A duplicated calendar key is ambiguous; never double count or silently choose one row.
        val rows = grouped.map { (date, values) ->
            if (values.size == 1) values.single() else HistoryRow(date, null, null)
        }.filter { LocalDate.parse(it.date) >= start }.sortedByDescending { it.date }
        return HistoryRangeSummary(start, end, java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1,
            rows.size, rows.count { it.tokens != null }, rows.count { it.cost != null },
            rows.fold(java.math.BigInteger.ZERO) { sum, row -> sum + java.math.BigInteger.valueOf(row.tokens ?: 0) },
            rows.fold(java.math.BigDecimal.ZERO) { sum, row -> sum + (row.cost?.let { java.math.BigDecimal(it) } ?: java.math.BigDecimal.ZERO) },
            rows.fold(java.math.BigInteger.ZERO) { sum, row -> sum + java.math.BigInteger.valueOf(row.unpricedTokens ?: 0) }, rows)
    }
}
