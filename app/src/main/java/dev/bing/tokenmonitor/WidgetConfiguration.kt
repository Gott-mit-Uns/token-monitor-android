package dev.bing.tokenmonitor

import android.appwidget.AppWidgetManager
import android.content.Context
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Instance metadata only. Business data still comes from the authenticated encrypted Hub cache. */
internal data class WidgetConfiguration(
    val mode: String = "summary",
    val period: String = "today",
    val breakdown: String = "models",
    val quotaAutomatic: Boolean = true,
    val quotaAccounts: List<String> = emptyList()
) {
    fun normalized(quotaProvider: Boolean = false) = copy(
        mode = if (quotaProvider) "quota" else mode.takeIf { it in modes } ?: "summary",
        period = period.takeIf { it in periods } ?: "today",
        breakdown = breakdown.takeIf { it in listOf("tools", "models") } ?: "models",
        quotaAccounts = quotaAccounts.filter { Regex("[0-9a-f]{64}").matches(it) }.distinct().take(2)
    )
    companion object {
        val modes = listOf("summary", "dashboard", "activity", "tools", "models")
        val periods = listOf("today", "month", "allTime")
    }
}

internal class WidgetConfigurationStore(context: Context) {
    private val file = context.getSharedPreferences("widget-instance-v1", Context.MODE_PRIVATE)
    fun load(id: Int, quota: Boolean = false): WidgetConfiguration = WidgetConfiguration(
        file.getString("$id.mode", if (quota) "quota" else "summary").orEmpty(),
        file.getString("$id.period", "today").orEmpty(),
        file.getString("$id.breakdown", "models").orEmpty(),
        file.getBoolean("$id.automatic", true),
        file.getString("$id.accounts", "").orEmpty().split(',')
    ).normalized(quota)
    fun save(id: Int, value: WidgetConfiguration) {
        require(id != AppWidgetManager.INVALID_APPWIDGET_ID)
        val config = value.normalized(value.mode == "quota")
        check(file.edit().putString("$id.mode", config.mode).putString("$id.period", config.period)
            .putString("$id.breakdown", config.breakdown).putBoolean("$id.automatic", config.quotaAutomatic)
            .putString("$id.accounts", config.quotaAccounts.joinToString(",")).commit())
    }
    fun delete(id: Int) { file.edit().apply { listOf("mode", "period", "breakdown", "automatic", "accounts").forEach { remove("$id.$it") } }.apply() }
    fun restore(oldIds: IntArray, newIds: IntArray, quota: Boolean) {
        val moved = oldIds.zip(newIds).map { (old, new) -> new to load(old, quota) }
        oldIds.forEach(::delete)
        moved.forEach { (new, config) -> save(new, config) }
    }
}

internal fun widgetQuotaAccountId(windows: List<Limit>): String {
    val first = windows.first()
    val identity = first.presentation.identity
    val account = identity.key ?: identity.email ?: first.account ?: identity.name ?: "record:${first.presentation.recordIndex ?: 0}"
    return MessageDigest.getInstance("SHA-256").digest("${first.provider}:$account".toByteArray()).joinToString("") { "%02x".format(it) }
}

internal data class WidgetQuotaSelection(val id: String, val windows: List<Limit>, val legacyAliases: Set<String> = emptySet())

/** Record identity contains no readings, timestamps, reset boundaries or credentials.
 * Concrete product/source ids survive reordering. Indistinguishable legacy records
 * use an ordinal plus cardinality, so removing a record cannot substitute a peer. */
internal fun widgetQuotaSelections(snapshot: Snapshot): List<WidgetQuotaSelection> {
    data class Record(val windows: List<Limit>, val base: String, val primary: String, val secondary: String)
    fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    fun identityHints(windows: List<Limit>): Pair<String, String> {
        val first = windows.first()
        val record = first.presentation.providerRecord
        fun scalar(value: kotlinx.serialization.json.JsonElement?): String? = (value as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it.length <= 512 }
        val fields = linkedMapOf<String, String>()
        listOf("profileId", "adapterId", "source", "sourceDetail", "sourceDeviceId", "workspaceLabel", "workspaceKind").forEach { key ->
            scalar(record?.get(key))?.let { fields[key] = it }
        }
        // These are actual product labels used by the existing Hub/Mac contract.
        if ((first.provider == "mimo" && first.account in listOf("Console", "Desktop Membership", "Membership")) ||
            (first.provider == "opencode" && first.account in listOf("Go", "Zen"))) fields["product"] = first.account!!
        val rawWindows = record?.get("windows") as? JsonArray
        val signatures = (rawWindows?.mapNotNull { it as? JsonObject }?.map { window ->
            listOf("kind", "metric", "limitId", "quotaId", "id", "model", "group").mapNotNull { key -> scalar(window[key])?.let { "$key=${it.length}:$it" } }.joinToString("|")
        } ?: windows.map { window -> listOfNotNull(window.kind?.let { "kind=${it.length}:$it" }, window.metric?.let { "metric=${it.length}:$it" }, window.presentation.limitId?.let { "limitId=${it.length}:$it" }).joinToString("|") })
            .filter(String::isNotEmpty).distinct().sorted()
        return fields.entries.joinToString("|") { (key, value) -> "$key=${value.length}:$value" } to signatures.joinToString(";")
    }
    val records = LimitDisplay.groups(snapshot.limits).map { windows -> identityHints(windows).let { (primary, secondary) -> Record(windows, widgetQuotaAccountId(windows), primary, secondary) } }
    val counts = records.groupingBy { it.base }.eachCount()
    val primaryCounts = records.groupingBy { it.base to it.primary }.eachCount()
    fun hint(record: Record): String = when {
        record.primary.isNotEmpty() && primaryCounts.getValue(record.base to record.primary) == 1 -> record.primary
        record.primary.isNotEmpty() && record.secondary.isNotEmpty() -> "${record.primary}|windows:${record.secondary}"
        record.primary.isNotEmpty() -> record.primary
        record.secondary.isNotEmpty() -> "windows:${record.secondary}"
        else -> ""
    }
    val hints = records.groupingBy { it.base to hint(it) }.eachCount()
    val ordinals = mutableMapOf<Pair<String, String>, Int>()
    return records.map { record ->
        val identity = hint(record)
        val group = record.base to identity
        val ordinal = (ordinals[group] ?: 0) + 1
        ordinals[group] = ordinal
        val identical = hints.getValue(group)
        val id = when {
            identity.isNotEmpty() && identical == 1 -> hash("${record.base}:record:$identity")
            counts.getValue(record.base) == 1 -> record.base
            else -> hash("${record.base}:legacy:$identity:$identical:$ordinal")
        }
        val aliases = buildSet {
            if (counts.getValue(record.base) == 1 && id != record.base) add(record.base)
            // A disambiguated surviving record keeps its prior id after its peer disappears.
            if (record.primary.isNotEmpty() && record.secondary.isNotEmpty() && primaryCounts.getValue(record.base to record.primary) == 1)
                add(hash("${record.base}:record:${record.primary}|windows:${record.secondary}"))
        } - id
        WidgetQuotaSelection(id, record.windows, aliases)
    }
}

internal fun resolveWidgetQuotaSelection(selections: List<WidgetQuotaSelection>, id: String): WidgetQuotaSelection? =
    selections.singleOrNull { it.id == id || id in it.legacyAliases }

/** Stable identity selection; missing custom accounts stay missing, never replaced by another account. */
internal fun widgetQuotaGroups(snapshot: Snapshot, configuration: WidgetConfiguration, now: Instant): List<List<Limit>> {
    return selectedWidgetQuotaSelections(snapshot, configuration, now).map { it.windows }
}

internal fun selectedWidgetQuotaSelections(snapshot: Snapshot, configuration: WidgetConfiguration, now: Instant): List<WidgetQuotaSelection> {
    val selections = widgetQuotaSelections(snapshot)
    if (!configuration.quotaAutomatic) {
        return configuration.quotaAccounts.mapNotNull { resolveWidgetQuotaSelection(selections, it) }.distinctBy { it.id }.take(2)
    }
    // Match the Mac automatic resolver: usable data, provider status, freshness;
    // stable input order breaks ties. Never rank unrelated accounts by a mean.
    return selections.sortedBy { selection ->
        val rows = selection.windows
        val first = rows.first()
        val hasData = rows.any { it.kind != null || it.amount != null || it.presentation.balanceAmount != null }
        val stamp = first.updated?.let { runCatching { Instant.parse(it) }.getOrNull() }
        (if (hasData) 0 else 4) + (if (first.status == "ok") 0 else 2) +
            (if (first.status != "ok" || first.stale == true || stamp == null || stamp > now || now.toEpochMilli() - stamp.toEpochMilli() > 20 * 60 * 1000) 1 else 0)
    }.take(2)
}

data class WidgetOpenRequest(val revision: Long = 0, val mode: String = "summary", val period: String = "today", val instance: Int = 0) {
    val page: HubPage get() = when (mode) { "quota" -> HubPage.LIMITS; "tools" -> HubPage.TOOLS; "models" -> HubPage.MODELS; "activity" -> HubPage.TRENDS; else -> HubPage.HOME }
}

internal fun widgetCompactTokens(value: Long?, localizedUnits: Boolean = false, locale: Locale = Locale.US): String {
    if (value == null || value < 0) return "—"
    if (localizedUnits && locale.language == "zh") {
        var divisor = if (value >= 100_000_000) 100_000_000.0 else if (value >= 10_000) 10_000.0 else return value.toString()
        fun formatted(): String { val scaled = value / divisor; return String.format(Locale.US, if (scaled < 10) "%.2f" else "%.1f", scaled) }
        var raw = formatted()
        if (divisor == 10_000.0 && raw.toDouble() >= 10_000) { divisor = 100_000_000.0; raw = formatted() }
        val text = raw.trimEnd('0').trimEnd('.')
        return text + if (divisor == 100_000_000.0) "亿" else "万"
    }
    val (divisor, suffix) = when { value >= 1_000_000_000 -> 1_000_000_000.0 to "B"; value >= 1_000_000 -> 1_000_000.0 to "M"; value >= 1_000 -> 1_000.0 to "K"; else -> return value.toString() }
    return String.format(Locale.US, "%.1f%s", value / divisor, suffix)
}
