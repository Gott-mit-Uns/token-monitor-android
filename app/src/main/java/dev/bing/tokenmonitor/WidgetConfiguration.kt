package dev.bing.tokenmonitor

import android.appwidget.AppWidgetManager
import android.content.Context
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale

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

/** Stable identity selection; missing custom accounts stay missing, never replaced by another account. */
internal fun widgetQuotaGroups(snapshot: Snapshot, configuration: WidgetConfiguration, now: Instant): List<List<Limit>> {
    val groups = LimitDisplay.groups(snapshot.limits)
    if (!configuration.quotaAutomatic) {
        val byId = groups.associateBy(::widgetQuotaAccountId)
        return configuration.quotaAccounts.mapNotNull(byId::get).take(2)
    }
    // Match the Mac automatic resolver: usable data, provider status, freshness;
    // stable input order breaks ties. Never rank unrelated accounts by a mean.
    return groups.sortedBy { rows ->
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
