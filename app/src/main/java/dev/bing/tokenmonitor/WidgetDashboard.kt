package dev.bing.tokenmonitor

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Normal launcher sizes mirror the Mac's wide metric/quota rows and two-column lower section. */
internal fun renderWidgetDashboard(context: Context, preferences: DisplayPreferences, cache: WidgetCache.Ready, id: Int,
    config: WidgetConfiguration, width: Int, now: Instant): RemoteViews {
    val snapshot = requireNotNull(cache.snapshot)
    val views = RemoteViews(context.packageName, R.layout.hub_dashboard_widget)
    val dark = HubWidget.dark(context, preferences)
    val text = context.getColor(if (dark) R.color.widget_dark_text else R.color.widget_light_text)
    val secondary = context.getColor(if (dark) R.color.widget_dark_secondary else R.color.widget_light_secondary)
    val accent = context.getColor(if (dark) R.color.widget_dark_accent else R.color.widget_light_accent)
    views.setInt(R.id.widget_root, "setBackgroundResource", if (dark) R.drawable.widget_dark_background else R.drawable.widget_light_background)
    listOf(R.id.widget_tokens, R.id.widget_quota_title, R.id.widget_quota_name_1, R.id.widget_quota_name_2,
        R.id.widget_quota_1, R.id.widget_quota_2, R.id.widget_activity_title, R.id.widget_detail_title,
        R.id.widget_detail_1, R.id.widget_detail_2, R.id.widget_detail_3, R.id.widget_detail_4).forEach { views.setTextColor(it, text) }
    listOf(R.id.widget_period, R.id.widget_cost, R.id.widget_time, R.id.widget_activity_detail, R.id.widget_trend_change).forEach { views.setTextColor(it, secondary) }
    views.setTextColor(R.id.widget_configure, accent)
    val usage = widgetUsage(snapshot, cache.usage.savedAt, config.period)
    val locale = context.resources.configuration.locales[0]
    val localized = preferences.main.compactUnits == "localized"
    val tokens = widgetCompactTokens(usage.tokens, localized, locale)
    views.setTextViewText(R.id.widget_period, context.getString(HubWidget.periodTitle(config.period)))
    views.setTextViewText(R.id.widget_tokens, tokens)
    // Long astronomical values retain the exact accessible value; ordinary values keep the large Mac hierarchy.
    views.setTextViewTextSize(R.id.widget_tokens, android.util.TypedValue.COMPLEX_UNIT_SP, if (tokens.length > 10) 23f else 32f)
    views.setContentDescription(R.id.widget_tokens, context.getString(R.string.widget_tokens_accessible, usage.tokens?.let { NumberFormat.getIntegerInstance(locale).format(it) } ?: context.getString(R.string.unknown)))
    views.setTextViewText(R.id.widget_cost, widgetCost(usage, preferences))
    val trend = widgetTrend(snapshot, cache.history, config.period, now)
    views.setTextViewText(R.id.widget_trend_change, widgetTrendChange(snapshot, trend, config.period))
    views.setImageViewBitmap(R.id.widget_trend, WidgetCharts.trend(trend, width, dark))
    if (trend.isNotEmpty()) views.setContentDescription(R.id.widget_trend, context.getString(R.string.widget_align_trend_accessible, trend.first().date, trend.last().date))
    val accounts = selectedQuotaWidgetAccounts(snapshot, preferences.limits, locale, now, config)
    val nameIds = listOf(R.id.widget_quota_name_1, R.id.widget_quota_name_2)
    val accountIds = listOf(R.id.widget_quota_1, R.id.widget_quota_2)
    accounts.take(2).forEachIndexed { index, account ->
        views.setTextViewText(nameIds[index], account.compactTitle)
        views.setTextViewText(accountIds[index], account.compactLines.joinToString("\n") + if (account.stale) " *" else "")
        views.setContentDescription(accountIds[index], (listOf(account.title) + account.lines + if (account.stale) listOf(context.getString(R.string.quota_widget_source_stale)) else emptyList()).joinToString(". "))
    }
    if (accounts.isEmpty()) views.setTextViewText(R.id.widget_quota_1, context.getString(if (config.quotaAutomatic) R.string.quota_widget_missing else R.string.widget_align_account_missing))
    views.setViewVisibility(R.id.widget_quota_row_2, if (accounts.size >= 2) View.VISIBLE else View.GONE)
    val history = cache.history
    if (history == null || history.daily.isEmpty()) {
        views.setTextViewText(R.id.widget_activity_detail, context.getString(R.string.widget_align_missing_history))
        views.setViewVisibility(R.id.widget_activity, View.GONE)
    } else {
        val known = history.daily.sortedBy { it.date }.takeLast(182).filter { it.tokens != null }
        val active = known.count { (it.tokens ?: 0) > 0 }
        views.setTextViewText(R.id.widget_activity_detail, context.getString(R.string.widget_align_activity_known, active, known.size))
        views.setImageViewBitmap(R.id.widget_activity, WidgetCharts.activity(history, now.atZone(ZoneId.systemDefault()).toLocalDate(), width, dark, 16))
        views.setContentDescription(R.id.widget_activity, context.getString(R.string.widget_align_activity_accessible, active, known.size))
    }
    val breakdown = widgetBreakdown(snapshot.periods[config.period], config.breakdown, cache.modelNames, snapshot.observedModelIds)
    views.setTextViewText(R.id.widget_detail_title, context.getString(if (breakdown.isEmpty()) R.string.widget_align_no_breakdown else HubWidget.modeTitle(config.breakdown)))
    val detailIds = listOf(R.id.widget_detail_1, R.id.widget_detail_2, R.id.widget_detail_3, R.id.widget_detail_4)
    val barIds = listOf(R.id.widget_bar_1, R.id.widget_bar_2, R.id.widget_bar_3, R.id.widget_bar_4)
    val palette = DesktopVendorPalette.resolve(preferences.appearance.vendorColors)
    detailIds.indices.forEach { index ->
        val row = breakdown.getOrNull(index)
        if (row == null) { views.setViewVisibility(detailIds[index], View.GONE); views.setViewVisibility(barIds[index], View.GONE) }
        else {
            views.setTextViewText(detailIds[index], "${row.label}  ${widgetCompactTokens(row.tokens, localized, locale)}")
            views.setContentDescription(detailIds[index], "${row.label}: ${row.tokens?.let { NumberFormat.getIntegerInstance(locale).format(it) } ?: context.getString(R.string.unknown)} Tokens. ${row.share?.let { "${kotlin.math.round(it).toInt()}%" } ?: "—"}")
            val vendor = if (config.breakdown == "tools") toolId(row.id) else modelVendor(row.id).orEmpty()
            val color = (palette[vendor] ?: palette.getValue("default")).toInt()
            views.setImageViewBitmap(barIds[index], WidgetCharts.percentage(row.share, color, dark))
        }
    }
    val saved = if (usage.savedAt in 1..now.toEpochMilli()) DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(usage.savedAt)) else null
    val status = saved?.let { context.getString(if (widgetStale(usage, now)) R.string.widget_align_stale else R.string.widget_align_cache, it) } ?: context.getString(R.string.widget_align_age_unknown)
    views.setTextViewText(R.id.widget_time, "$status\n${context.getString(R.string.widget_align_scope)}")
    views.setContentDescription(R.id.widget_root, context.getString(R.string.widget_align_dashboard))
    views.setOnClickPendingIntent(R.id.widget_root, HubWidget.openPending(context, id, config))
    views.setOnClickPendingIntent(R.id.widget_metric_section, HubWidget.openPending(context, id, config.copy(mode = "summary")))
    views.setOnClickPendingIntent(R.id.widget_quota_section, HubWidget.openPending(context, id, config.copy(mode = "quota")))
    views.setOnClickPendingIntent(R.id.widget_activity_section, HubWidget.openPending(context, id, config.copy(mode = "activity")))
    views.setOnClickPendingIntent(R.id.widget_breakdown_section, HubWidget.openPending(context, id, config.copy(mode = config.breakdown)))
    views.setOnClickPendingIntent(R.id.widget_configure, HubWidget.configurationPending(context, id))
    return views
}
