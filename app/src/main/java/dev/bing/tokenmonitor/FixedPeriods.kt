package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class DeviceHistory(val id: String, val available: Boolean, val history: HubHistory?)
data class DeviceHistories(val signature: String, val devices: List<DeviceHistory>) {
    override fun toString() = "DeviceHistories(private retained history)"
}

/** Project /api/devices into retained History only. Do not cache unrelated device payload fields. */
object DeviceHistoryParser {
    private val fields = setOf("tokens", "cost", "unpricedTokens", "cacheReadTokens", "cacheWriteTokens", "outputTokens", "unclassifiedTokens", "tokenComponentsAvailable", "activeTimeMs", "messages")
    fun project(body: String, signature: String): String {
        val root = Json.parseToJsonElement(body)
        val devices = (root as? JsonArray) ?: ((root as? JsonObject)?.get("devices") as? JsonArray)
            ?: error("Device history response unavailable")
        return buildJsonObject {
            put("signature", signature)
            put("devices", JsonArray(devices.mapNotNull { raw ->
                val record = raw as? JsonObject ?: return@mapNotNull null
                val id = ((record["deviceId"] ?: record["id"]) as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                if (id.isEmpty()) return@mapNotNull null
                val declared = (record["historyAvailable"] as? JsonPrimitive)?.booleanOrNull == true &&
                    record.containsKey("history") && record["history"] != JsonNull
                buildJsonObject {
                    put("id", id); put("available", declared)
                    if (declared) {
                        val history = record["history"] as? JsonObject
                        put("history", buildJsonObject {
                            put("daily", JsonArray((history?.get("daily") as? JsonArray).orEmpty().mapNotNull { rawRow ->
                                val row = rawRow as? JsonObject ?: return@mapNotNull null
                                buildJsonObject {
                                    row["date"]?.let { put("date", it) }
                                    fields.forEach { key -> row[key]?.let { put(key, it) } }
                                    for (key in listOf("perClient", "perModel")) (row[key] as? JsonObject)?.let { values ->
                                        put(key, buildJsonObject {
                                            values.forEach { (name, rawValue) -> (rawValue as? JsonObject)?.let { value ->
                                                put(name, buildJsonObject { fields.forEach { field -> value[field]?.let { put(field, it) } } })
                                            } }
                                        })
                                    }
                                }
                            }))
                        })
                    }
                }
            }))
        }.toString()
    }
    fun parse(body: String): DeviceHistories {
        val root = Json.parseToJsonElement(body).jsonObject
        val signature = root.getValue("signature").jsonPrimitive.content
        val devices = root.getValue("devices").jsonArray.map { raw ->
            val row = raw.jsonObject
            val id = row.getValue("id").jsonPrimitive.content
            require(id.isNotBlank())
            val available = row.getValue("available").jsonPrimitive.boolean
            DeviceHistory(id, available, if (available) HistoryParser.parse(row.getValue("history").toString()) else null)
        }
        require(devices.map { it.id }.distinct().size == devices.size)
        return DeviceHistories(signature, devices)
    }
}

data class FixedRange(val start: LocalDate, val end: LocalDate)
data class FixedActivity(val activeDays: Int, val currentStreak: Int, val activeTimeMs: Long, val peakDayTokens: Long)
enum class FixedPeriodIssue { HISTORY, WINDOW, INVENTORY, SIZE }
data class FixedPeriodView(val period: Period? = null, val range: FixedRange? = null,
    val daily: List<HistoryRow> = emptyList(), val devices: Map<String, Period> = emptyMap(),
    val deviceRanges: Map<String, FixedRange> = emptyMap(), val issue: FixedPeriodIssue? = null, val activity: FixedActivity? = null)

/** Independent translation of desktop fixedPeriodRanges; same per-device calendars and live overlay. */
object FixedPeriods {
    val modes = listOf("month", "week", "last7", "last30")
    fun derived(selection: String) = selection in setOf("week", "last7", "last30")
    fun slot(selection: String) = if (selection in modes) "month" else selection
    fun label(selection: String) = when (selection) { "month" -> "MONTH"; "allTime" -> "TOTAL"; "week" -> "WEEK"; "last7" -> "7D"; "last30" -> "30D"; else -> "DAY" }
    fun signature(snapshot: Snapshot) = snapshot.deviceHistoryRevision.orEmpty() + "|" +
        JsonArray(snapshot.devices.map { it.id.trim() }.filter { it.isNotEmpty() }.distinct().sorted().map(::JsonPrimitive))
    fun range(selection: String, today: LocalDate, locale: Locale): FixedRange {
        val firstDay = if (locale.country.isNotEmpty()) desktopRegionWeekStarts[locale.country.uppercase(Locale.ROOT)] ?: 1
            else desktopLanguageWeekStarts[locale.language.lowercase(Locale.ROOT)] ?: 1
        val days = when (selection) {
            "week" -> (today.dayOfWeek.value - firstDay + 7) % 7
            "last7" -> 6
            "last30" -> 29
            else -> error("Derived range required")
        }
        return FixedRange(today.minusDays(days.toLong()), today)
    }
    fun evaluate(selection: String, snapshot: Snapshot, histories: DeviceHistories?, now: Instant = Instant.now(), locale: Locale = Locale.getDefault()): FixedPeriodView {
        require(derived(selection))
        if (histories == null || histories.signature != signature(snapshot)) return FixedPeriodView(issue = FixedPeriodIssue.HISTORY)
        val ids = snapshot.devices.map { it.id.trim() }.filter { it.isNotEmpty() }.toSet()
        if (ids != histories.devices.map { it.id }.toSet()) return FixedPeriodView(issue = FixedPeriodIssue.INVENTORY)
        if (histories.devices.any { !it.available || it.history == null }) return FixedPeriodView(issue = FixedPeriodIssue.HISTORY)
        return try {
            val selected = mutableListOf<HistoryRow>()
            val periods = linkedMapOf<String, Period>()
            val ranges = linkedMapOf<String, FixedRange>()
            for (device in snapshot.devices) {
                val history = histories.devices.firstOrNull { it.id == device.id.trim() }?.history ?: continue
                val participating = device.periods.values.any { (it.tokens ?: 0) > 0 || decimal(it.cost).signum() > 0 } ||
                    history.daily.any { (it.tokens ?: 0) > 0 || decimal(it.cost).signum() > 0 }
                if (!participating) continue
                val window = device.periods["today"] ?: return FixedPeriodView(issue = FixedPeriodIssue.WINDOW)
                val key = runCatching { LocalDate.parse(window.window) }.getOrNull() ?: return FixedPeriodView(issue = FixedPeriodIssue.WINDOW)
                val end = runCatching { Instant.parse(window.ends) }.getOrNull() ?: return FixedPeriodView(issue = FixedPeriodIssue.WINDOW)
                val today = if (now < end) key else runCatching { now.atZone(ZoneId.of(window.zone)).toLocalDate() }.getOrNull()
                    ?: return FixedPeriodView(issue = FixedPeriodIssue.WINDOW)
                val range = range(selection, today, locale)
                val rows = history.daily.associateBy { it.date }.toMutableMap()
                val live = device.periodDetails["today"]
                if (live != null) {
                    val previous = rows[key.toString()]
                    if (previous == null || (live.tokens ?: 0) >= (previous.tokens ?: 0)) rows[key.toString()] = liveRow(key.toString(), live, previous)
                }
                val daily = generateSequence(range.start) { it.plusDays(1).takeIf { date -> date <= range.end } }
                    .map { date -> rows[date.toString()] ?: HistoryRow(date.toString(), 0, "0", components = TokenComponents(0, 0, 0, 0, 0), exact = true) }.toList()
                periods[device.id] = derive(daily)
                ranges[device.id] = range
                selected.addAll(daily)
            }
            val daily = selected.groupBy { it.date }.map { (date, rows) -> mergeDay(date, rows) }.sortedBy { it.date }
            val range = if (ranges.isEmpty()) range(selection, now.atZone(ZoneId.systemDefault()).toLocalDate(), locale)
                else FixedRange(ranges.values.minOf { it.start }, ranges.values.maxOf { it.end })
            val active = FixedActivity(daily.count { (it.tokens ?: 0) > 0 }, daily.asReversed().takeWhile { (it.tokens ?: 0) > 0 }.size,
                plus(daily.map { it.activeTimeMs }), daily.maxOfOrNull { it.tokens ?: 0 } ?: 0)
            FixedPeriodView(derive(daily), range, daily, periods, ranges, activity = active)
        } catch (_: ArithmeticException) { FixedPeriodView(issue = FixedPeriodIssue.SIZE) }
    }
    private fun decimal(value: String?) = value?.takeIf { it.length <= 128 }?.let { raw -> runCatching {
        BigDecimal(raw).takeIf { it.signum() >= 0 && it.precision() <= 100 && it.scale() in -100..100 }
    }.getOrNull() } ?: BigDecimal.ZERO
    private fun plus(values: Iterable<Long?>) = values.fold(0L) { sum, value -> Math.addExact(sum, value ?: 0) }
    private fun money(values: Iterable<String?>) = values.fold(BigDecimal.ZERO) { sum, value -> sum + decimal(value) }.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    private fun unknown(tokens: Long?, components: TokenComponents, exact: Boolean?) = components.unclassified ?: if (exact == true) 0L else tokens ?: 0L
    private fun sumParts(values: List<Pair<TokenComponents, Pair<Long?, Boolean?>>>) = TokenComponents(
        plus(values.map { it.first.cacheRead }), plus(values.map { it.first.cacheWrite }), plus(values.map { it.first.output }),
        plus(values.map { unknown(it.second.first, it.first, it.second.second) }), null)
    private fun attribution(rows: List<HistoryRow>, client: Boolean): Map<String, HistoryAttribution> {
        val values = rows.flatMap { (if (client) it.perClient else it.perModel).entries }.groupBy { entry ->
            if (client && entry.key == "antigravity-cli") "antigravity" else entry.key
        }
        return values.mapValues { (_, entries) ->
            val parts = entries.map { it.value }
            HistoryAttribution(plus(parts.map { it.tokens }), money(parts.map { it.cost }), plus(parts.map { (it.unpricedTokens ?: 0).coerceAtMost(it.tokens ?: 0) }),
                sumParts(parts.map { it.components to (it.tokens to it.exact) }), parts.all { it.exact == true }, plus(parts.map { it.messages }))
        }
    }
    private fun mergeDay(date: String, rows: List<HistoryRow>) = HistoryRow(date, plus(rows.map { it.tokens }), money(rows.map { it.cost }),
        plus(rows.map { (it.unpricedTokens ?: 0).coerceAtMost(it.tokens ?: 0) }), attribution(rows, true), attribution(rows, false),
        plus(rows.map { it.activeTimeMs }), plus(rows.map { it.messages }), sumParts(rows.map { it.components to (it.tokens to it.exact) }), rows.all { it.exact == true })
    private fun liveParts(tokens: Long?, value: TokenComponents, exact: Boolean): TokenComponents {
        var remaining = tokens ?: 0
        fun take(count: Long?): Long { val result = (count ?: 0).coerceIn(0, remaining); remaining -= result; return result }
        val read = take(value.cacheRead); val write = take(value.cacheWrite); val output = take(value.output)
        val unclassified = if (exact) 0 else value.unclassified?.coerceIn(0, remaining) ?: remaining
        return TokenComponents(read, write, output, unclassified, null)
    }
    private fun liveRow(date: String, period: Period, previous: HistoryRow?): HistoryRow {
        fun values(counts: Map<String, Long>, details: Map<String, UsageBreakdown>, old: Map<String, HistoryAttribution>) =
            (counts.keys + details.keys).associateWith { key ->
                val detail = details[key]
                HistoryAttribution(counts[key] ?: 0, detail?.cost ?: "0", detail?.unpriced, components = liveParts(counts[key], detail?.components ?: TokenComponents(null, null, null, null, null), period.exact), exact = period.exact, messages = old[key]?.messages)
            }
        return HistoryRow(date, period.tokens ?: 0, period.cost ?: "0", period.unpriced,
            values(period.tools, period.toolDetails, previous?.perClient.orEmpty()), values(period.models, period.modelDetails, previous?.perModel.orEmpty()),
            previous?.activeTimeMs, previous?.messages, liveParts(period.tokens, period.components, period.exact), period.exact)
    }
    private fun derive(rows: List<HistoryRow>): Period {
        val sum = mergeDay("", rows)
        val body = buildJsonObject { put("periods", buildJsonObject { put("fixed", buildJsonObject {
            put("totalTokens", sum.tokens); put("costUsd", sum.cost)
            if ((sum.unpricedTokens ?: 0) > 0) put("unpricedTokens", sum.unpricedTokens)
            put("cacheReadTokens", sum.components.cacheRead); put("cacheWriteTokens", sum.components.cacheWrite)
            put("outputTokens", sum.components.output); put("unclassifiedTokens", sum.components.unclassified)
            put("capabilities", buildJsonObject { put("tokenComponents", sum.exact == true) })
            for ((prefix, map) in listOf("client" to sum.perClient, "model" to sum.perModel)) {
                put(if (prefix == "client") "clients" else "models", buildJsonObject { map.forEach { (id, value) -> put(id, value.tokens) } })
                put(prefix + "Costs", buildJsonObject { map.forEach { (id, value) -> put(id, value.cost) } })
                if (map.values.any { (it.unpricedTokens ?: 0) > 0 }) put(prefix + "UnpricedTokens", buildJsonObject { map.forEach { (id, value) -> if ((value.unpricedTokens ?: 0) > 0) put(id, value.unpricedTokens) } })
                for ((suffix, property) in listOf("CacheReads" to { value: HistoryAttribution -> value.components.cacheRead }, "CacheWrites" to { value: HistoryAttribution -> value.components.cacheWrite }, "Outputs" to { value: HistoryAttribution -> value.components.output }, "UnclassifiedTokens" to { value: HistoryAttribution -> value.components.unclassified }))
                    put(prefix + suffix, buildJsonObject { map.forEach { (id, value) -> put(id, property(value)) } })
            }
        }) }) }.toString()
        return HubParser.parse(body).periods.getValue("fixed")
    }
}
