package dev.bing.tokenmonitor

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class WidgetQuotaAccount(val title: String, val lines: List<String>, val stale: Boolean, val omittedPeriods: Int, val compactTitle: String, val compactLines: List<String>, val id: String = "")
internal sealed interface QuotaWidgetCache {
    data object Empty : QuotaWidgetCache
    data object Unreadable : QuotaWidgetCache
    class Ready(snapshot: Snapshot, savedAt: Long) : QuotaWidgetCache {
        val snapshot: Snapshot = snapshot
        val savedAt: Long = savedAt
    }
}

/** Presentation from the same account grouping, compact windows and row formatter as the app.
 * Never averages accounts or periods. Email masking is always enabled on the home screen. */
internal fun quotaWidgetAccounts(snapshot: Snapshot, preferences: LimitDisplayPreferences, locale: Locale, now: Instant): List<WidgetQuotaAccount> {
    val groups = widgetQuotaSelections(snapshot).groupBy { it.windows.first().provider }
    val order = LimitPreferences.order(preferences.homeProviderOrder.ifEmpty { preferences.providerOrder }) + groups.keys.filter { it !in LimitPreferences.providers }
    return order.filter { it !in preferences.hiddenHomeProviders }.flatMap { provider ->
        val accounts = groups[provider].orEmpty()
        val peers = accounts.map { it.windows.first() }
        accounts.mapIndexedNotNull { index, selection ->
            val windows = selection.windows
            val first = windows.first()
            val rows = LimitPreferences.filter(
                LimitProviderLayout.project(LimitProviderLayout.compactRecord(windows), preferences.showUsed, locale, now),
                preferences.hiddenItems[provider].orEmpty())
            // A deliberately hidden account contributes no empty placeholder.
            if (rows.isEmpty() && preferences.hiddenItems[provider].orEmpty().isNotEmpty() && windows.any { it.kind != null }) return@mapIndexedNotNull null
            val providerTitle = HomeQuotaLayout.providerTitle(provider)
            val account = if (accounts.size > 1) LimitDisplay.accountTitle(first, peers, index, true) else ""
            val title = listOf(providerTitle, account).filter { it.isNotBlank() }.joinToString(" · ")
            val lines = rows.take(2).map { row ->
                val label = listOfNotNull(row.group, row.label.takeIf { it.isNotBlank() }).joinToString(" · ")
                val value = if (row.value == "—" || row.value.isBlank()) { if (locale.language == "zh") "额度缺失" else "Quota unavailable" } else row.value
                LimitDisplay.text("$label: $value" + row.detail.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(), true)
            }.ifEmpty { listOf(if (locale.language == "zh") "额度缺失 · 打开应用查看" else "Quota unavailable · Open app") }
            val staleSource = windows.any { window ->
                val sourceTime = window.updated?.let { runCatching { Instant.parse(it) }.getOrNull() }
                val boundary = window.resets?.let { runCatching { Instant.parse(it) }.getOrNull() }
                window.stale == true || sourceTime == null || sourceTime > now ||
                    now.toEpochMilli() - sourceTime.toEpochMilli() > HubQuotaWidget.STALE_AFTER_MS || (boundary != null && boundary <= now)
            }
            val compactTitle = providerTitle + if (accounts.size > 1) " · ${if(locale.language == "zh") "账户" else "Account "}${index + 1}" else ""
            val compactLines = rows.take(2).map { row ->
                val shortLabel = row.label
                val label = listOfNotNull(row.group, shortLabel.takeIf { it.isNotBlank() }).joinToString(" · ")
                val value = row.value.takeIf { it.isNotBlank() && it != "—" } ?: if(locale.language == "zh") "缺失" else "Unavailable"
                LimitDisplay.text("$label: $value", true)
            }.ifEmpty { listOf(if(locale.language == "zh") "额度缺失" else "Quota unavailable") }
            WidgetQuotaAccount(title, lines, staleSource, (rows.size - 2).coerceAtLeast(0), compactTitle, compactLines, selection.id)
        }
    }.take(preferences.homeAccountCount)
}

internal fun selectedQuotaWidgetAccounts(snapshot: Snapshot, preferences: LimitDisplayPreferences, locale: Locale, now: Instant,
    configuration: WidgetConfiguration): List<WidgetQuotaAccount> {
    val selected = selectedWidgetQuotaSelections(snapshot, configuration, now)
    val byId = quotaWidgetAccounts(snapshot,
        preferences.copy(showUsed = false, hiddenHomeProviders = emptySet(), hiddenItems = emptyMap(), homeAccountCount = Int.MAX_VALUE), locale, now).associateBy { it.id }
    return selected.mapNotNull { byId[it.id] }.take(2)
}

/** Cache-only widget. No workers, timers, network client or foreground/background service. */
object HubQuotaWidget {
    const val OPEN_QUOTA_AND_UPDATE = "dev.bing.tokenmonitor.widget.OPEN_QUOTA_AND_UPDATE"
    internal const val STALE_AFTER_MS = 20 * 60 * 1000L
    internal fun load(store: HubStore): QuotaWidgetCache = runCatching {
        store.load()?.let { QuotaWidgetCache.Ready(it.parsedSnapshot ?: HubParser.parse(it.snapshot, it.observedModelIds), it.savedAt) } ?: QuotaWidgetCache.Empty
    }.getOrDefault(QuotaWidgetCache.Unreadable)

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        update(context, manager, manager.getAppWidgetIds(ComponentName(context, HubQuotaWidgetProvider::class.java)))
    }
    internal fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val cache = load(HubStore(context))
        val prefs = runCatching { DisplayPreferenceStore(context).load() }.getOrDefault(DisplayPreferences())
        val localized = HubWidget.localizedContext(context, prefs.language)
        val configurations = WidgetConfigurationStore(context)
        ids.forEach { id -> manager.updateAppWidget(id, render(localized, prefs, cache, id, manager.getAppWidgetOptions(id), configuration = configurations.load(id, true))) }
    }
    internal fun capacity(heightDp: Int, fontScale: Float): Int = ((heightDp - 112 * fontScale.coerceAtLeast(1f)) / (86 * fontScale.coerceAtLeast(1f))).toInt().coerceIn(1, 3)
    internal fun render(context: Context, preferences: DisplayPreferences, cache: QuotaWidgetCache, id: Int, options: Bundle = Bundle(), now: Instant = Instant.now(), configuration: WidgetConfiguration = WidgetConfiguration(mode = "quota")): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.hub_quota_widget)
        val dark = when (preferences.theme) { ThemeChoice.DARK -> true; ThemeChoice.LIGHT -> false; ThemeChoice.SYSTEM -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES }
        views.setInt(R.id.quota_widget_root, "setBackgroundResource", if (dark) R.drawable.widget_dark_background else R.drawable.widget_light_background)
        val text = context.getColor(if (dark) R.color.widget_dark_text else R.color.widget_light_text)
        val secondary = context.getColor(if (dark) R.color.widget_dark_secondary else R.color.widget_light_secondary)
        views.setTextColor(R.id.quota_widget_title, text)
        listOf(R.id.quota_widget_time, R.id.quota_widget_more).forEach { views.setTextColor(it, secondary) }
        views.setTextColor(R.id.quota_widget_open, context.getColor(if (dark) R.color.widget_dark_accent else R.color.widget_light_accent))
        views.setTextViewText(R.id.quota_widget_title, context.getString(R.string.quota_widget_title))
        views.setTextViewText(R.id.quota_widget_open, context.getString(R.string.quota_widget_open))
        views.setTextColor(R.id.quota_widget_configure, context.getColor(if (dark) R.color.widget_dark_accent else R.color.widget_light_accent))
        views.setOnClickPendingIntent(R.id.quota_widget_configure, HubWidget.configurationPending(context, id))
        val rowIds = listOf(R.id.quota_widget_account_1, R.id.quota_widget_account_2, R.id.quota_widget_account_3)
        rowIds.forEach { views.setTextColor(it, text); views.setViewVisibility(it, View.GONE) }
        var message = context.getString(if (cache == QuotaWidgetCache.Unreadable) R.string.widget_unreadable else R.string.quota_widget_empty)
        views.setTextViewText(R.id.quota_widget_more, "")
        if (cache is QuotaWidgetCache.Ready) {
            val age = (now.toEpochMilli() - cache.savedAt).coerceAtLeast(0)
            val saved = runCatching { DateTimeFormatter.ofPattern("MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(cache.savedAt)) }.getOrDefault("—")
            val stale = cache.savedAt <= 0 || cache.savedAt > now.toEpochMilli() || age > STALE_AFTER_MS
            // The timestamp remains truthful even while a launcher retains a RemoteViews snapshot.
            message = context.getString(if (stale) R.string.quota_widget_stale_age else R.string.quota_widget_cache_age, age / 60000, saved)
            views.setContentDescription(R.id.quota_widget_time, message)
            val accounts = selectedQuotaWidgetAccounts(cache.snapshot, preferences.limits, context.resources.configuration.locales[0], now, configuration)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 360)
            val compact = context.resources.configuration.fontScale >= 1.5f
            val shown = accounts.take(capacity(height, context.resources.configuration.fontScale))
            if (compact) {
                views.setTextViewText(R.id.quota_widget_title, context.getString(R.string.quota_widget_compact_title))
                views.setTextViewText(R.id.quota_widget_open, context.getString(R.string.quota_widget_compact_open))
                views.setContentDescription(R.id.quota_widget_open, context.getString(R.string.quota_widget_open))
                message = context.getString(if(stale || accounts.any { it.stale }) R.string.quota_widget_compact_stale else R.string.quota_widget_compact_age, age / 60000, saved)
            }
            shown.forEachIndexed { index, account ->
                val extra = buildList {
                    if (account.stale) add(context.getString(R.string.quota_widget_source_stale))
                    if (account.omittedPeriods > 0) add(context.getString(R.string.quota_widget_more_periods, account.omittedPeriods))
                }.joinToString(" · ")
                views.setViewVisibility(rowIds[index], View.VISIBLE)
                val fullText = (listOf(account.title) + account.lines + extra.takeIf { it.isNotEmpty() }).filterNotNull().joinToString("\n")
                views.setTextViewText(rowIds[index], if(compact) (listOf(account.compactTitle) + account.compactLines).joinToString("\n") else fullText)
                views.setContentDescription(rowIds[index], fullText)
            }
            if (accounts.isEmpty()) views.setTextViewText(R.id.quota_widget_more, context.getString(if(configuration.quotaAutomatic) R.string.quota_widget_missing else R.string.widget_align_account_missing))
            else if (accounts.size > shown.size) views.setTextViewText(R.id.quota_widget_more,
                context.getString(if(compact) R.string.quota_widget_compact_more else R.string.quota_widget_more_accounts, accounts.size - shown.size))
            else if (compact && shown.any { it.omittedPeriods > 0 }) views.setTextViewText(R.id.quota_widget_more,
                context.getString(R.string.quota_widget_more_periods, shown.sumOf { it.omittedPeriods }))
        }
        views.setTextViewText(R.id.quota_widget_time, message)
        val open = HubWidget.openPending(context, id, configuration.copy(mode = "quota"))
        views.setOnClickPendingIntent(R.id.quota_widget_root, open)
        views.setOnClickPendingIntent(R.id.quota_widget_open, open)
        rowIds.forEach { views.setOnClickPendingIntent(it, open) }
        return views
    }
}

class HubQuotaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray): Unit { updateAsync(context, manager, ids) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle): Unit { updateAsync(context, manager, intArrayOf(id)) }
    override fun onDeleted(context: Context, ids: IntArray) { ids.forEach { WidgetConfigurationStore(context).delete(it) } }
    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) { WidgetConfigurationStore(context).restore(oldIds, newIds, true); updateAsync(context, AppWidgetManager.getInstance(context), newIds) }
    private fun updateAsync(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { runCatching { HubQuotaWidget.update(context.applicationContext, manager, ids) } } finally { pending.finish() }
        }
    }
}
