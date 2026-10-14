package dev.bing.tokenmonitor

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetHostView
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.LocaleList
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Only business projection fields enter RemoteViews. Connection addresses and credentials never do. */
data class WidgetUsage(val tokens: Long?, val cost: String?, val unpriced: Boolean, val updated: String?, val savedAt: Long, val sourceStale: Boolean = false)

fun widgetUsage(snapshot: Snapshot, savedAt: Long, period: String = "today"): WidgetUsage {
    val value = snapshot.periods[period]
    val cost = value?.cost?.takeIf { it.length <= 128 }?.let { text -> runCatching {
        BigDecimal(text).takeIf { it.signum() >= 0 && it.precision() <= 100 && it.scale() in -100..100 }?.toPlainString()
    }.getOrNull() }
    fun sourceTime(value: String?): Instant? = value?.takeIf { it.length <= 80 }?.let { runCatching { Instant.parse(it) }.getOrNull() }
    val newestDevice = snapshot.devices.mapNotNull { device ->
        sourceTime(device.received) ?: sourceTime(device.updated)
    }.maxOrNull()?.toString()
    return WidgetUsage(value?.tokens, cost, (value?.unpriced ?: 0) > 0, newestDevice ?: snapshot.updated, savedAt,
        snapshot.devices.isNotEmpty() && snapshot.devices.all { it.stale == true })
}

sealed interface WidgetCache {
    data object Empty : WidgetCache
    data object Unreadable : WidgetCache
    data class Ready(val usage: WidgetUsage, val snapshot: Snapshot? = null, val history: HubHistory? = null, val modelNames: ModelNames? = null) : WidgetCache
}

/** Reads authenticated encrypted cache only; system updatePeriodMillis refreshes cache age, not the network. */
object HubWidget {
    const val OPEN_AND_UPDATE = "dev.bing.tokenmonitor.widget.OPEN_AND_UPDATE"
    const val INSTANCE = "dev.bing.tokenmonitor.widget.INSTANCE"
    const val MODE = "dev.bing.tokenmonitor.widget.MODE"
    const val PERIOD = "dev.bing.tokenmonitor.widget.PERIOD"
    private val updateLock = Any()
    fun load(store: HubStore): WidgetCache = runCatching {
        store.load()?.let { saved ->
            val snapshot = saved.parsedSnapshot ?: HubParser.parse(saved.snapshot, saved.observedModelIds)
            val history = saved.history?.let { runCatching { HistoryParser.parse(it) }.getOrNull() } ?: snapshot.historyPreview
            WidgetCache.Ready(widgetUsage(snapshot, saved.savedAt), snapshot, history,
                saved.modelNames?.let { runCatching { ModelNamesParser.parse(it) }.getOrNull() })
        } ?: WidgetCache.Empty
    }.getOrDefault(WidgetCache.Unreadable)

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        update(context, manager, manager.getAppWidgetIds(ComponentName(context, HubWidgetProvider::class.java)))
        HubQuotaWidget.updateAll(context)
    }

    fun update(context: Context, manager: AppWidgetManager, ids: IntArray) = synchronized(updateLock) {
        if (ids.isEmpty()) return@synchronized
        val cache = load(HubStore(context))
        val preferences = runCatching { DisplayPreferenceStore(context).load() }.getOrDefault(DisplayPreferences())
        val localized = localizedContext(context, preferences.language)
        val configurations = WidgetConfigurationStore(context)
        ids.forEach { id -> manager.updateAppWidget(id, render(localized, preferences, cache, id, manager.getAppWidgetOptions(id), configurations.load(id))) }
    }

    fun localizedContext(context: Context, language: LanguageChoice): Context {
        val tag = when (language) {
            LanguageChoice.CHINESE -> "zh"
            LanguageChoice.ENGLISH -> "en"
            LanguageChoice.SYSTEM -> if (context.resources.configuration.locales[0].language == "zh") "zh" else "en"
        }
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) })
    }

    internal fun dark(context: Context, preferences: DisplayPreferences): Boolean = when (preferences.theme) {
        ThemeChoice.DARK -> true
        ThemeChoice.LIGHT -> false
        ThemeChoice.SYSTEM -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
    internal fun modeTitle(mode: String): Int = when (mode) {
        "dashboard" -> R.string.widget_align_dashboard; "activity" -> R.string.widget_align_activity
        "tools" -> R.string.widget_align_tools; "models" -> R.string.widget_align_models
        "quota" -> R.string.widget_align_quota; else -> R.string.widget_align_summary
    }
    internal fun periodTitle(period: String): Int = when (period) { "month" -> R.string.widget_align_month; "allTime" -> R.string.widget_align_total; else -> R.string.widget_align_today }

    internal fun openIntent(context: Context, id: Int, configuration: WidgetConfiguration): Intent = Intent(context, MainActivity::class.java)
        .setAction(if (configuration.mode == "quota") HubQuotaWidget.OPEN_QUOTA_AND_UPDATE else OPEN_AND_UPDATE)
        .setData(Uri.parse("token-monitor-widget://open/$id/${configuration.mode}/${configuration.period}"))
        .putExtra(if (configuration.mode == "quota") HubQuotaWidget.OPEN_QUOTA_AND_UPDATE else OPEN_AND_UPDATE, true)
        .putExtra(INSTANCE, id).putExtra(MODE, configuration.mode).putExtra(PERIOD, configuration.period)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    internal fun openPending(context: Context, id: Int, configuration: WidgetConfiguration): PendingIntent = PendingIntent.getActivity(
        context, id, openIntent(context, id, configuration), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    internal fun configurationPending(context: Context, id: Int): PendingIntent = PendingIntent.getActivity(context, id,
        Intent(context, WidgetConfigurationActivity::class.java).setAction("dev.bing.tokenmonitor.widget.CONFIGURE")
            .setData(Uri.parse("token-monitor-widget://configure/$id")).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    internal fun render(context: Context, preferences: DisplayPreferences, cache: WidgetCache, id: Int, options: Bundle = Bundle(),
                        configuration: WidgetConfiguration = WidgetConfiguration(), now: Instant = Instant.now()): RemoteViews {
        val config = configuration.normalized()
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 360)
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280)
        if (config.mode == "dashboard" && width >= 280 && height >= 320 && context.resources.configuration.fontScale < 1.5f && cache is WidgetCache.Ready && cache.snapshot != null) {
            return renderWidgetDashboard(context, preferences, cache, id, config, width, now)
        }
        val dark = dark(context, preferences)
        val views = RemoteViews(context.packageName, R.layout.hub_widget)
        val text = context.getColor(if (dark) R.color.widget_dark_text else R.color.widget_light_text)
        val secondary = context.getColor(if (dark) R.color.widget_dark_secondary else R.color.widget_light_secondary)
        val accent = context.getColor(if (dark) R.color.widget_dark_accent else R.color.widget_light_accent)
        views.setInt(R.id.widget_root, "setBackgroundResource", if (dark) R.drawable.widget_dark_background else R.drawable.widget_light_background)
        listOf(R.id.widget_title, R.id.widget_tokens, R.id.widget_detail_title, R.id.widget_detail_1, R.id.widget_detail_2,
            R.id.widget_detail_3, R.id.widget_detail_4, R.id.widget_activity_title, R.id.widget_quota_title, R.id.widget_quota_1, R.id.widget_quota_2).forEach { views.setTextColor(it, text) }
        listOf(R.id.widget_period, R.id.widget_cost, R.id.widget_time, R.id.widget_value_1, R.id.widget_value_2, R.id.widget_value_3, R.id.widget_value_4).forEach { views.setTextColor(it, secondary) }
        views.setTextColor(R.id.widget_open, accent); views.setTextColor(R.id.widget_configure, accent)
        views.setTextViewText(R.id.widget_title, context.getString(modeTitle(config.mode)))
        views.setTextViewText(R.id.widget_period, context.getString(periodTitle(config.period)))
        views.setTextViewText(R.id.widget_tokens, "—")
        views.setTextViewText(R.id.widget_cost, context.getString(if (cache == WidgetCache.Unreadable) R.string.widget_unreadable else R.string.widget_empty))
        views.setTextViewText(R.id.widget_time, context.getString(R.string.widget_align_scope))
        views.setTextViewText(R.id.widget_open, context.getString(R.string.widget_open))
        val scale = context.resources.configuration.fontScale.coerceAtLeast(1f)
        val effectiveHeight = height / scale
        val large = effectiveHeight >= 510 && width >= 280
        val small = effectiveHeight < 300
        val compactHeight = effectiveHeight < 220
        val compactFont = (scale >= 1.5f && height < 480) || compactHeight
        if (compactFont) {
            views.setViewVisibility(R.id.widget_period, View.GONE)
            views.setViewVisibility(R.id.widget_open, View.GONE)
            views.setTextViewText(R.id.widget_title, if (config.mode in listOf("summary", "dashboard")) context.getString(periodTitle(config.period)) else context.getString(modeTitle(config.mode)))
            views.setContentDescription(R.id.widget_root, "${context.getString(modeTitle(config.mode))}. ${context.getString(R.string.widget_open)}")
        }
        val metricSize = if (width < 220 || scale >= 1.5f || (compactHeight && height < 180)) 26f else if (compactHeight) 30f else 37f
        val contentInsetDp = if (compactHeight) 6 else 12
        if (compactHeight) {
            val inset = (contentInsetDp * context.resources.displayMetrics.density).roundToInt()
            views.setViewPadding(R.id.widget_root, inset, inset, inset, inset)
        }
        views.setTextViewTextSize(R.id.widget_tokens, TypedValue.COMPLEX_UNIT_SP, metricSize)
        if (compactHeight && cache !is WidgetCache.Ready) {
            views.setTextViewText(R.id.widget_cost, context.getString(if (cache == WidgetCache.Unreadable) R.string.widget_align_unreadable_short else R.string.widget_align_empty_short))
            views.setContentDescription(R.id.widget_cost, context.getString(if (cache == WidgetCache.Unreadable) R.string.widget_unreadable else R.string.widget_empty))
            views.setTextViewText(R.id.widget_time, "")
            views.setContentDescription(R.id.widget_time, context.getString(R.string.widget_align_scope))
        }
        // Old launcher instances can retain pre-update sizes below today's minimum.
        // At font 2 and <240dp there is no room for core values plus a 48dp edit target.
        // Show a truthful state and enlarge hint instead of squeezing unreadable text.
        if (scale >= 1.5f && height < 240) {
            views.setViewVisibility(R.id.widget_tokens, View.GONE)
            views.setViewVisibility(R.id.widget_cost, View.GONE)
            val state = if (cache is WidgetCache.Ready) {
                val usage = cache.snapshot?.let { widgetUsage(it, cache.usage.savedAt, config.period) } ?: cache.usage
                val saved = usage.savedAt.takeIf { it in 1..now.toEpochMilli() }?.let {
                    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))
                }
                saved?.let { context.getString(if(widgetStale(usage, now)) R.string.widget_align_compact_stale else R.string.widget_align_compact_cache, it) }
                    ?: context.getString(R.string.widget_align_compact_unknown)
            } else context.getString(if(cache == WidgetCache.Unreadable) R.string.widget_align_unreadable_short else R.string.widget_align_empty_short)
            views.setTextViewText(R.id.widget_time, state + context.getString(R.string.widget_align_expand_short))
            views.setContentDescription(R.id.widget_time, "$state. ${context.getString(R.string.widget_align_expand)}. ${context.getString(R.string.widget_align_scope)}")
            views.setOnClickPendingIntent(R.id.widget_root, openPending(context, id, config))
            views.setOnClickPendingIntent(R.id.widget_configure, configurationPending(context, id))
            return views
        }
        if (cache is WidgetCache.Ready) {
            val snapshot = cache.snapshot
            val usage = snapshot?.let { widgetUsage(it, cache.usage.savedAt, config.period) } ?: cache.usage
            val locale = context.resources.configuration.locales[0]
            val exact = usage.tokens?.let { NumberFormat.getIntegerInstance(locale).format(it) } ?: context.getString(R.string.unknown)
            val compactTokens = widgetCompactTokens(usage.tokens, preferences.main.compactUnits == "localized", locale)
            views.setTextViewText(R.id.widget_tokens, compactTokens)
            val nominal = metricSize
            val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, nominal, context.resources.displayMetrics)
            val paint = android.graphics.Paint().apply { textSize = px; typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD) }
            val measured = paint.measureText(compactTokens).coerceAtLeast(1f)
            val density = context.resources.displayMetrics.density
            val hostPadding = AppWidgetHostView.getDefaultPaddingForWidget(context, ComponentName(context, HubWidgetProvider::class.java), null)
            val insetPx = (contentInsetDp * density).roundToInt()
            val available = ((width * density).toInt() - hostPadding.left - hostPadding.right - insetPx * 2 - 2 * density).coerceAtLeast(1f)
            views.setTextViewTextSize(R.id.widget_tokens, TypedValue.COMPLEX_UNIT_PX, px * (available / measured).coerceAtMost(1f))
            views.setContentDescription(R.id.widget_tokens, context.getString(R.string.widget_tokens_accessible, exact))
            views.setTextViewText(R.id.widget_cost, widgetCost(usage, preferences))
            views.setContentDescription(R.id.widget_cost, context.getString(if (usage.unpriced) R.string.widget_cost_partial_formatted else R.string.widget_cost_formatted, widgetCost(usage, preferences)))
            val saved = if (usage.savedAt in 1..now.toEpochMilli()) DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(usage.savedAt)) else null
            var status = saved?.let { context.getString(if (widgetStale(usage, now)) R.string.widget_align_stale else R.string.widget_align_cache, it) }
                ?: context.getString(R.string.widget_align_age_unknown)
            val metricMode = config.mode in listOf("summary", "dashboard")
            views.setViewVisibility(R.id.widget_tokens, if (metricMode) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_cost, if (metricMode) View.VISIBLE else View.GONE)
            if (snapshot != null && metricMode && (!small || config.mode == "summary") && effectiveHeight >= 220) {
                val points = widgetTrend(snapshot, cache.history, config.period, now)
                if (points.any { it.tokens != null }) {
                    views.setViewVisibility(R.id.widget_trend, View.VISIBLE)
                    views.setImageViewBitmap(R.id.widget_trend, WidgetCharts.trend(points, width * 2, dark))
                    views.setContentDescription(R.id.widget_trend, context.getString(R.string.widget_align_trend_accessible, points.first().date, points.last().date))
                }
            }
            if (snapshot != null && config.mode == "dashboard" && effectiveHeight >= 340) {
                views.setViewVisibility(R.id.widget_quota_section, View.VISIBLE)
                val accounts = selectedQuotaWidgetAccounts(snapshot, preferences.limits, locale, now, config)
                val shown = accounts.take(if (large) 2 else 1)
                views.setTextViewText(R.id.widget_quota_1, shown.firstOrNull()?.let { (listOf(it.compactTitle) + it.compactLines).joinToString(" · ") } ?: context.getString(if (!config.quotaAutomatic) R.string.widget_align_account_missing else R.string.quota_widget_missing))
                views.setTextViewText(R.id.widget_quota_2, shown.getOrNull(1)?.let { (listOf(it.compactTitle) + it.compactLines).joinToString(" · ") }.orEmpty())
                views.setViewVisibility(R.id.widget_quota_2, if (shown.size > 1) View.VISIBLE else View.GONE)
                views.setOnClickPendingIntent(R.id.widget_quota_section, openPending(context, id, config.copy(mode = "quota")))
            }
            if (snapshot != null && (config.mode in listOf("tools", "models") || (config.mode == "dashboard" && effectiveHeight >= 440))) {
                val mode = if (config.mode == "dashboard") config.breakdown else config.mode
                val rows = widgetBreakdown(snapshot.periods[config.period], mode, cache.modelNames, snapshot.observedModelIds)
                views.setViewVisibility(R.id.widget_detail_title, View.VISIBLE)
                views.setTextViewText(R.id.widget_detail_title, context.getString(if (rows.isEmpty()) R.string.widget_align_no_breakdown else modeTitle(mode)))
                val rowIds = listOf(R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3, R.id.widget_row_4)
                val textIds = listOf(R.id.widget_detail_1, R.id.widget_detail_2, R.id.widget_detail_3, R.id.widget_detail_4)
                val valueIds = listOf(R.id.widget_value_1, R.id.widget_value_2, R.id.widget_value_3, R.id.widget_value_4)
                val iconIds = listOf(R.id.widget_icon_1, R.id.widget_icon_2, R.id.widget_icon_3, R.id.widget_icon_4)
                val capacity = if (config.mode == "dashboard") 2 else ((effectiveHeight - 150) / 42).toInt().coerceIn(1, 4)
                rows.take(capacity).forEachIndexed { index, row ->
                    views.setViewVisibility(rowIds[index], View.VISIBLE)
                    views.setTextViewText(textIds[index], row.label)
                    views.setTextViewText(valueIds[index], "${widgetCompactTokens(row.tokens, preferences.main.compactUnits == "localized", locale)}  ${row.share?.let { "${kotlin.math.round(it).toInt()}%" } ?: "—"}")
                    views.setContentDescription(textIds[index], "${row.label}: ${row.tokens?.let { NumberFormat.getIntegerInstance(locale).format(it) } ?: context.getString(R.string.unknown)} Tokens")
                    val icon = brandResource(if (mode == "tools") row.id else modelVendor(row.id).orEmpty())
                    if (icon == null) views.setViewVisibility(iconIds[index], View.GONE) else { views.setImageViewResource(iconIds[index], icon); views.setInt(iconIds[index], "setColorFilter", text) }
                    views.setOnClickPendingIntent(rowIds[index], openPending(context, id, config.copy(mode = mode)))
                }
            }
            if (config.mode == "activity" || (config.mode == "dashboard" && large)) {
                views.setViewVisibility(R.id.widget_activity_title, View.VISIBLE)
                val history = cache.history
                if (history == null || history.daily.isEmpty()) views.setTextViewText(R.id.widget_activity_title, context.getString(R.string.widget_align_missing_history))
                else {
                    val known = history.daily.sortedBy { it.date }.takeLast(182).filter { it.tokens != null }
                    val active = known.count { (it.tokens ?: 0) > 0 }
                    views.setTextViewText(R.id.widget_activity_title, context.getString(R.string.widget_align_activity_known, active, known.size))
                    views.setViewVisibility(R.id.widget_activity, View.VISIBLE)
                    views.setImageViewBitmap(R.id.widget_activity, WidgetCharts.activity(history, now.atZone(ZoneId.systemDefault()).toLocalDate(), width * 2, dark, if (config.mode == "dashboard") 16 else 26))
                    views.setContentDescription(R.id.widget_activity, context.getString(R.string.widget_align_activity_accessible, active, known.size))
                    views.setOnClickPendingIntent(R.id.widget_activity, openPending(context, id, config.copy(mode = "activity")))
                }
            }
            if (config.mode == "dashboard" && !large) status += "\n" + context.getString(R.string.widget_align_expand)
            val compactStatus = if (compactFont) saved?.let { context.getString(if (widgetStale(usage, now)) R.string.widget_align_compact_stale else R.string.widget_align_compact_cache, it.takeLast(5)) }
                ?: context.getString(R.string.widget_align_compact_unknown) else status
            views.setTextViewText(R.id.widget_time, compactStatus + if (compactFont && config.mode == "dashboard" && !large)
                (if (compactHeight) context.getString(R.string.widget_align_expand_short) else "\n" + context.getString(R.string.widget_align_expand)) else "")
            val exactSaved = usage.savedAt.takeIf { it in 1..now.toEpochMilli() }?.let { Instant.ofEpochMilli(it).toString() }
            views.setContentDescription(R.id.widget_time, "$status. ${exactSaved.orEmpty()}. ${context.getString(R.string.widget_align_scope)}")
        }
        val open = openPending(context, id, config)
        views.setOnClickPendingIntent(R.id.widget_root, open); views.setOnClickPendingIntent(R.id.widget_open, open)
        views.setOnClickPendingIntent(R.id.widget_configure, configurationPending(context, id))
        return views
    }
}

class HubWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray): Unit { updateAsync(context, manager, ids) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle): Unit { updateAsync(context, manager, intArrayOf(id)) }
    override fun onDeleted(context: Context, ids: IntArray) { ids.forEach { WidgetConfigurationStore(context).delete(it) } }
    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) { WidgetConfigurationStore(context).restore(oldIds, newIds, false); updateAsync(context, AppWidgetManager.getInstance(context), newIds) }
    override fun onReceive(context: Context, intent: Intent): Unit {
        if (intent.action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch { try { HubWidget.updateAll(context.applicationContext) } finally { pending.finish() } }
        } else super.onReceive(context, intent)
    }
    private fun updateAsync(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { runCatching { HubWidget.update(context.applicationContext, manager, ids) } }
            finally { pending.finish() }
        }
    }
}
