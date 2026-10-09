package io.github.theminionooo.tokenmonitor.widget

import io.github.theminionooo.tokenmonitor.localization.localizedContext
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import io.github.theminionooo.tokenmonitor.MainActivity
import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.storage.DisplayPreferences
import io.github.theminionooo.tokenmonitor.data.storage.ReduceMotionMode
import io.github.theminionooo.tokenmonitor.data.storage.SnapshotCache
import io.github.theminionooo.tokenmonitor.domain.HistoryPoint
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.domain.LimitAccount
import io.github.theminionooo.tokenmonitor.domain.LimitWindow
import io.github.theminionooo.tokenmonitor.domain.snapshotDate
import io.github.theminionooo.tokenmonitor.domain.usageHistory
import io.github.theminionooo.tokenmonitor.ui.InterfaceTheme
import io.github.theminionooo.tokenmonitor.ui.Palette
import io.github.theminionooo.tokenmonitor.ui.currentStreak
import io.github.theminionooo.tokenmonitor.ui.displayName
import io.github.theminionooo.tokenmonitor.ui.formatActiveDuration
import io.github.theminionooo.tokenmonitor.ui.formatCompactTokens
import io.github.theminionooo.tokenmonitor.ui.formatUsageCost
import io.github.theminionooo.tokenmonitor.ui.formatBoundary
import io.github.theminionooo.tokenmonitor.ui.formatTokens
import io.github.theminionooo.tokenmonitor.ui.originalToolColor
import io.github.theminionooo.tokenmonitor.ui.providerLabel
import io.github.theminionooo.tokenmonitor.ui.resolveInterfaceTheme
import io.github.theminionooo.tokenmonitor.ui.upstreamToolAsset
import io.github.theminionooo.tokenmonitor.ui.windowTitle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/**
 * Ordered by information density. [width] and [height] are the content space the launcher must
 * supply in dp, and [padding] is the inset each layout keeps from the card's sides and bottom.
 */
internal enum class WidgetLayout(val width: Int, val height: Int, val resource: Int, val padding: Int) {
    Compact(110, 110, R.layout.usage_widget_compact, 10),
    Portrait(110, 240, R.layout.usage_widget_portrait, 10),
    Wide(240, 116, R.layout.usage_widget_wide, 10),
    Overview(240, 190, R.layout.usage_widget_overview, 12),
    Large(250, 384, R.layout.usage_widget, 12),
}

internal fun WidgetLayout.requiredHeight(fontScale: Float): Float =
    height * if (this == WidgetLayout.Compact || this == WidgetLayout.Wide) 1f else fontScale.coerceAtLeast(1f)

internal fun widgetLayout(width: Int, height: Int, fontScale: Float = 1f): WidgetLayout =
    WidgetLayout.entries.lastOrNull { width >= it.width && height >= it.requiredHeight(fontScale) } ?: WidgetLayout.Compact

/** The stats column beside the figure grows the row past the figure once it holds three entries. */
private const val LARGE_THREE_STATS_DP = 400
private const val OVERVIEW_CHART_DP = 301
/** Below these heights the overview drops its second window and then the tool legend. */
private const val OVERVIEW_SECOND_ROW_DP = 221
private const val OVERVIEW_LEGEND_DP = 200
private const val OVERVIEW_CHART_BARS_DP = 36f

/**
 * Everything in the detailed layout except the chart bars has a fixed dp height, so the bar area is
 * whatever the launcher height leaves over. Drawing the bitmap at exactly that size keeps it sharp.
 */
internal fun largeChartBarsDp(heightDp: Float, providerBlocks: Int, threeStats: Boolean = heightDp >= LARGE_THREE_STATS_DP): Float {
    val limits = 22 + if (providerBlocks == 0) 18 else 57 * providerBlocks
    val figure = if (threeStats) 79 else 62
    val fixed = 16 + 48 + figure + 9 + 34 + 9 + limits + 9 + 6 + 16 + 4 + 16
    return (heightDp - fixed).coerceAtLeast(16f)
}

/** The overview chart has fixed bars; the figure takes the spare height instead. */
internal fun overviewChartBarsDp(heightDp: Float, quotaRows: Int): Float = OVERVIEW_CHART_BARS_DP

internal fun widgetTokens(value: Long): String = formatTokens(value)
internal data class CounterFrame(val value: Long?, val previous: Long? = value, val child: Int = 0, val changed: Boolean = false)
internal fun counterFrame(old: CounterFrame?, value: Long?): CounterFrame = when {
    old == null -> CounterFrame(value)
    old.value == value -> old.copy(changed = false)
    else -> CounterFrame(value, old.value, 1 - old.child, true)
}

/** The lowest remaining window a provider reports, which is what the dashboard's Home module leads with. */
internal data class QuotaRow(val provider: String, val window: LimitWindow, val remainingPercent: Double, val siblings: List<LimitWindow> = listOf(window))

internal fun remainingPercent(window: LimitWindow): Double? =
    (window.remainingPercent ?: window.usedPercent?.let { 100.0 - it })?.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0)

internal fun quotaRows(snapshot: HubSnapshot): List<QuotaRow> = snapshot.stats.limits.providers.mapNotNull { account ->
    account.windows.mapNotNull { window -> remainingPercent(window)?.let { QuotaRow(account.provider, window, it, account.windows) } }
        .minByOrNull { it.remainingPercent }
}.distinctBy { it.provider }.take(2)

/** Up to two providers, each with its two tightest windows, for the detailed layout. */
internal fun quotaBlocks(snapshot: HubSnapshot): List<Pair<LimitAccount, List<LimitWindow>>> = snapshot.stats.limits.providers
    .map { account -> account to account.windows.filter { remainingPercent(it) != null }.sortedBy { remainingPercent(it) }.take(2) }
    .filter { it.second.isNotEmpty() }
    .sortedBy { (_, windows) -> remainingPercent(windows.first()) }
    .distinctBy { it.first.provider }
    .take(2)

/** Today's split by tool as (name, share) pairs, largest first, at most three. */
internal fun toolShares(snapshot: HubSnapshot): List<Pair<String, Double>> {
    val total = snapshot.today.clients.values.sum().toDouble()
    if (total <= 0) return emptyList()
    return snapshot.today.clients.entries.sortedByDescending { it.value }.take(3).map { (name, tokens) -> name to tokens / total }
}

/** Messages, active time and streak for today; the week total fills in when a day has no such data. */
internal fun widgetStats(history: List<HistoryPoint>, date: LocalDate?): List<Pair<String, String>> {
    val today = history.firstOrNull { it.label.take(10) == date?.toString() }
    val stats = mutableListOf<Pair<String, String>>()
    if (today != null && today.messages > 0) stats += formatTokens(today.messages) to "MESSAGES"
    if (today != null && today.activeTimeMs > 0) stats += formatActiveDuration(today.activeTimeMs) to "ACTIVE TODAY"
    val streak = currentStreak(history, date ?: LocalDate.now())
    if (streak > 0) stats += (if (streak == 1) "1 day" else "$streak days") to "STREAK"
    if (stats.size < 3) {
        val end = date ?: LocalDate.now()
        val week = history.filter { runCatching { LocalDate.parse(it.label.take(10)) }.getOrNull()?.let { day -> !day.isBefore(end.minusDays(6)) && !day.isAfter(end) } == true }
        if (week.isNotEmpty()) stats += formatCompactTokens(week.sumOf { it.tokens }) to "7 DAYS"
    }
    return stats
}

open class UsageWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = updateAsync(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = updateAsync(context)
    override fun onDisabled(context: Context) = WidgetUpdateCoordinator.stopLiveIfUnused(context)
    override fun onDeleted(context: Context, ids: IntArray) { synchronized(frames) { ids.forEach { frames.remove(it) } } }

    private fun updateAsync(context: Context) {
        val result = goAsync()
        thread(name = "usage-widget") {
            try { refresh(context.applicationContext) } finally { result.finish() }
        }
    }

    companion object {
        private val frames = mutableMapOf<Int, CounterFrame>()

        @Synchronized internal fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = usageWidgetProviders.flatMap { manager.getAppWidgetIds(ComponentName(context, it)).toList() }.toIntArray()
            if (ids.isEmpty()) return
            val session = WidgetRuntime.session
            val cached = if ((session.enabled || session.refreshing) && session.snapshot != null) null else SnapshotCache(context).read()?.let { wire ->
                runCatching {
                    HubProtocolParser.decodeSnapshot(wire.health, wire.stats, wire.devices, wire.history, wire.subscriptions, wire.capturedAt, true)
                }.getOrNull()
            }
            val snapshot = listOfNotNull(session.snapshot, cached).maxByOrNull { it.capturedAt }?.let { io.github.theminionooo.tokenmonitor.ui.presentSnapshot(it, io.github.theminionooo.tokenmonitor.data.storage.DesktopPreferences(context).options.value) }
            val display = DisplayPreferences(context).options.value
            val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val theme = resolveInterfaceTheme(display.themeCode, display.followSystemTheme, systemDark)
            val motion = when (display.reduceMotion) {
                ReduceMotionMode.On -> false
                ReduceMotionMode.Off -> true
                else -> Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
            }
            val fontScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
            ids.forEach { id ->
                val frame = synchronized(frames) { counterFrame(frames[id], snapshot?.today?.totalTokens).also { frames[id] = it } }
                val options = manager.getAppWidgetOptions(id)
                val preset = widgetPreset(manager.getAppWidgetInfo(id)?.provider?.className)
                fun select(width: Int, height: Int): WidgetLayout = preset?.takeIf { width >= it.width && height >= it.requiredHeight(fontScale) } ?: widgetLayout(width, height, fontScale)
                fun sized(layout: WidgetLayout, size: SizeF) = render(context, snapshot, theme, layout, frame, motion, session, size)
                val views = if (Build.VERSION.SDK_INT >= 31) {
                    // The launcher lists every size this widget can take; each gets a render drawn for exactly that space.
                    val sizes = widgetHostSizes(options)
                    if (sizes.isEmpty()) {
                        RemoteViews(WidgetLayout.entries.associate { layout ->
                            val size = SizeF(layout.width.toFloat(), layout.requiredHeight(fontScale))
                            size to sized(layout, size)
                        })
                    } else {
                        RemoteViews(sizes.associateWith { size -> sized(select(size.width.toInt(), size.height.toInt()), size) })
                    }
                } else {
                    fun forSize(widthKey: String, heightKey: String): RemoteViews {
                        val width = options.getInt(widthKey)
                        val height = options.getInt(heightKey)
                        val layout = select(width, height)
                        return sized(layout, SizeF(width.coerceAtLeast(layout.width).toFloat(), height.coerceAtLeast(layout.height).toFloat()))
                    }
                    RemoteViews(
                        forSize(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
                        forSize(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
                    )
                }
                manager.updateAppWidget(id, views)
            }
        }

        internal fun render(
            context: Context,
            snapshot: HubSnapshot?,
            theme: InterfaceTheme,
            layout: WidgetLayout,
            frame: CounterFrame = CounterFrame(snapshot?.today?.totalTokens),
            motion: Boolean = false,
            session: WidgetSession = WidgetSession(),
            size: SizeF = SizeF(layout.width.toFloat(), layout.height.toFloat()),
        ): RemoteViews {
            val palette = Palette.from(theme)
            val enabled = session.enabled && session.expiresAt > System.currentTimeMillis()
            if (snapshot == null) return empty(context, layout, session, palette, enabled)
            val density = context.resources.displayMetrics.density
            val contentWidth = ((size.width - 2 * layout.padding) * density).roundToInt().coerceAtLeast(1)
            val ink = palette.ink.toArgb()
            val muted = palette.muted.toArgb()
            val accent = palette.accent.toArgb()
            val narrow = layout == WidgetLayout.Compact || layout == WidgetLayout.Portrait
            val heightDp = size.height
            val now = System.currentTimeMillis()
            val views = RemoteViews(context.packageName, layout.resource)
            val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).withZone(ZoneId.systemDefault())
            val stamp = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault()).withZone(ZoneId.systemDefault())
            val savedAt = clock.format(Instant.ofEpochMilli(snapshot.capturedAt))

            // Figure
            val count = widgetTokens(snapshot.today.totalTokens)
            views.setTextViewText(R.id.widget_tokens, count)
            views.setViewVisibility(R.id.widget_tokens, if (motion) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.widget_counter, if (motion) View.VISIBLE else View.GONE)
            views.setTextViewText(R.id.widget_count_a, if (!frame.changed || frame.child == 0) count else frame.previous?.let(::widgetTokens) ?: count)
            views.setTextViewText(R.id.widget_count_b, if (!frame.changed || frame.child == 1) count else frame.previous?.let(::widgetTokens) ?: count)
            if (frame.changed) views.setDisplayedChild(R.id.widget_counter, frame.child)

            val date = snapshotDate(snapshot)
            val shortDate = date?.format(DateTimeFormatter.ofPattern("MMM d", localizedContext(context).resources.configuration.locales[0]))?.uppercase(Locale.getDefault()) ?: "TODAY"
            val longDate = date?.format(DateTimeFormatter.ofPattern("EEE, MMM d", localizedContext(context).resources.configuration.locales[0]))?.uppercase(Locale.getDefault()) ?: "TODAY"
            if (narrow) views.setTextViewText(R.id.widget_date, widgetText(context, shortDate))
            val history = usageHistory(snapshot)
            val today = history.firstOrNull { it.label.take(10) == date?.toString() }
            val cost = formatUsageCost(snapshot.today.costUsd, snapshot.today.unpricedTokens, compact = true)
            views.setContentDescription(R.id.widget_cost, if (snapshot.today.unpricedTokens > 0) formatUsageCost(snapshot.today.costUsd, snapshot.today.unpricedTokens) else null)
            views.setTextViewText(R.id.widget_cost, widgetText(context, when {
                narrow -> if (today != null && today.messages > 0) "$cost · ${formatCompactTokens(today.messages)} msgs" else "$cost est. cost"
                layout == WidgetLayout.Wide -> "$cost · $longDate · $savedAt"
                else -> "$cost estimated cost · $longDate"
            }))

            // Stats beside the figure
            if (!narrow) {
                val slots = if (layout == WidgetLayout.Large && heightDp >= LARGE_THREE_STATS_DP) 3 else 2
                val stats = widgetStats(history, date)
                val ids = listOf(
                    Triple(R.id.widget_stat_1, R.id.widget_stat_1_value, R.id.widget_stat_1_label),
                    Triple(R.id.widget_stat_2, R.id.widget_stat_2_value, R.id.widget_stat_2_label),
                    Triple(R.id.widget_stat_3, R.id.widget_stat_3_value, R.id.widget_stat_3_label),
                )
                ids.forEachIndexed { index, (block, valueId, labelId) ->
                    val stat = stats.getOrNull(index)?.takeIf { index < slots }
                    views.setViewVisibility(block, if (stat == null) View.GONE else View.VISIBLE)
                    if (stat != null) {
                        views.setTextViewText(valueId, widgetText(context, stat.first))
                        views.setTextViewText(labelId, widgetText(context, stat.second))
                        views.setTextColor(valueId, ink)
                        views.setTextColor(labelId, muted)
                    }
                }
            }

            // Header controls and status
            val status = when {
                enabled && session.connected -> "LIVE"
                enabled -> "CONNECTING"
                session.refreshing -> "UPDATING"
                else -> "SAVED"
            }
            views.setTextViewText(R.id.widget_status, widgetText(context, when {
                // Narrow headers have no room for a label beside the toggle; the glowing knob says Live.
                narrow -> if (status == "SAVED" || status == "LIVE") "" else "…"
                // Narrow brand headers have no room for the time beside the toggle.
                status == "SAVED" -> if (size.width < 300) "SAVED" else "SAVED · $savedAt"
                else -> status
            }))
            views.setContentDescription(R.id.widget_status, widgetText(context, status.lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }))
            views.setTextColor(R.id.widget_status, if (enabled && session.connected) accent else muted)
            val updated = "Updated ${stamp.format(Instant.ofEpochMilli(snapshot.capturedAt))}"
            val detail = updated + when {
                enabled -> " · Live until ${clock.format(Instant.ofEpochMilli(session.expiresAt))}"
                session.note != null -> " · ${session.note}"
                else -> ""
            }
            views.setContentDescription(R.id.widget_header, widgetText(context, "Token Monitor. $count tokens. $detail. Open dashboard"))
            views.setContentDescription(R.id.widget_live, widgetText(context, if (enabled) "Turn widget Live off" else "Turn widget Live on for one hour"))
            views.setImageViewResource(R.id.widget_refresh_image, R.drawable.ic_widget_refresh)
            views.setInt(R.id.widget_refresh_image, "setImageAlpha", if (session.refreshing) 130 else 255)
            views.setContentDescription(R.id.widget_refresh, widgetText(context, if (session.refreshing) "Refreshing widget" else "Refresh widget now"))
            if (narrow) {
                val footer = layout == WidgetLayout.Portrait || heightDp >= 150
                views.setViewVisibility(R.id.widget_footer, if (footer) View.VISIBLE else View.GONE)
                views.setTextViewText(R.id.widget_time, widgetText(context, if (enabled) "Until ${clock.format(Instant.ofEpochMilli(session.expiresAt))}" else "Updated $savedAt"))
            }

            // Tool split
            if (layout != WidgetLayout.Wide) {
                val shares = toolShares(snapshot)
                val showTools = shares.isNotEmpty() && (layout != WidgetLayout.Compact || heightDp >= 170)
                views.setViewVisibility(R.id.widget_tools, if (showTools) View.VISIBLE else View.GONE)
                if (showTools) {
                    val fallbacks = listOf(palette.blue, palette.purple, palette.yellow)
                    val colored = shares.mapIndexed { index, (name, share) -> Triple(name, share, originalToolColor(name, fallbacks[index % fallbacks.size]).toArgb()) }
                    views.setImageViewBitmap(R.id.widget_tools_bar, toolsBar(palette, colored, contentWidth, (6 * density).roundToInt().coerceAtLeast(2)))
                    val legend = SpannableStringBuilder()
                    colored.take(if (narrow) 2 else 3).forEachIndexed { index, (name, share, color) ->
                        if (index > 0) legend.append(if (narrow) "  " else "   ")
                        val start = legend.length
                        legend.append("●")
                        legend.setSpan(ForegroundColorSpan(color), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        legend.append(" ${if (narrow) name.displayName().substringBefore(' ') else name.displayName()} ${(share * 100).roundToInt()}%")
                    }
                    views.setTextViewText(R.id.widget_tools_legend, legend)
                    views.setViewVisibility(R.id.widget_tools_legend, if (layout == WidgetLayout.Overview && heightDp < OVERVIEW_LEGEND_DP) View.GONE else View.VISIBLE)
                    views.setTextViewTextSize(R.id.widget_tools_legend, android.util.TypedValue.COMPLEX_UNIT_SP, if (narrow) 10f else 11f)
                    views.setTextColor(R.id.widget_tools_legend, ink)
                    views.setContentDescription(R.id.widget_tools, widgetText(context, "Today by tool: " + colored.joinToString(", ") { "${it.first.displayName()} ${(it.second * 100).roundToInt()} percent" }))
                }
            }

            // Limits
            var blocks = 0
            var rows = 0
            if (layout == WidgetLayout.Large) {
                val quota = quotaBlocks(snapshot)
                blocks = quota.size
                views.setViewVisibility(R.id.widget_no_limits, if (quota.isEmpty()) View.VISIBLE else View.GONE)
                val blockIds = listOf(
                    LargeBlockIds(R.id.widget_quota_1, R.id.widget_logo_1, R.id.widget_provider_1,
                        intArrayOf(R.id.widget_cell_1_1, R.id.widget_cell_1_1_name, R.id.widget_cell_1_1_pct, R.id.widget_cell_1_1_bar, R.id.widget_cell_1_1_reset),
                        intArrayOf(R.id.widget_cell_1_2, R.id.widget_cell_1_2_name, R.id.widget_cell_1_2_pct, R.id.widget_cell_1_2_bar, R.id.widget_cell_1_2_reset)),
                    LargeBlockIds(R.id.widget_quota_2, R.id.widget_logo_2, R.id.widget_provider_2,
                        intArrayOf(R.id.widget_cell_2_1, R.id.widget_cell_2_1_name, R.id.widget_cell_2_1_pct, R.id.widget_cell_2_1_bar, R.id.widget_cell_2_1_reset),
                        intArrayOf(R.id.widget_cell_2_2, R.id.widget_cell_2_2_name, R.id.widget_cell_2_2_pct, R.id.widget_cell_2_2_bar, R.id.widget_cell_2_2_reset)),
                )
                blockIds.forEachIndexed { index, ids ->
                    val block = quota.getOrNull(index)
                    views.setViewVisibility(ids.block, if (block == null) View.GONE else View.VISIBLE)
                    if (block == null) return@forEachIndexed
                    val (account, windows) = block
                    val label = account.provider.ifBlank { "Provider" }.providerLabel()
                    views.setTextViewText(ids.provider, label)
                    views.setTextColor(ids.provider, ink)
                    mark(views, ids.logo, account.provider, palette)
                    val cellWidth = ((contentWidth - 14 * density) / 2).roundToInt().coerceAtLeast(1)
                    listOf(ids.cell1, ids.cell2).forEachIndexed { cellIndex, cell ->
                        val window = windows.getOrNull(cellIndex)
                        views.setViewVisibility(cell[0], if (window == null) View.GONE else View.VISIBLE)
                        if (window == null) return@forEachIndexed
                        val remaining = remainingPercent(window) ?: 0.0
                        val tone = tone(palette, remaining)
                        val title = windowTitle(window, account.windows)
                        views.setTextViewText(cell[1], widgetText(context, title))
                        views.setTextColor(cell[1], muted)
                        views.setTextViewText(cell[2], widgetText(context, "${remaining.toInt()}% left"))
                        views.setTextColor(cell[2], if (tone == palette.success) ink else tone.toArgb())
                        views.setImageViewBitmap(cell[3], quotaBar(palette, tone.toArgb(), remaining, if (windows.size == 1) contentWidth else cellWidth, (4 * density).roundToInt().coerceAtLeast(2)))
                        val reset = formatBoundary(window.resetsAt, window.boundaryKind, now)
                        views.setTextViewText(cell[4], widgetText(context, reset))
                        views.setTextColor(cell[4], muted)
                        views.setContentDescription(cell[0], widgetText(context, "$label, $title, ${remaining.toInt()} percent remaining, $reset"))
                    }
                }
            } else if (layout != WidgetLayout.Wide) {
                val limits = quotaRows(snapshot).take(if (layout == WidgetLayout.Compact) 1 else 2)
                rows = limits.size
                val showLimits = limits.isNotEmpty() && (layout != WidgetLayout.Compact || heightDp >= 195)
                // An empty limits block still says so, except in a Compact too short to hold the line.
                views.setViewVisibility(R.id.widget_limits, if (showLimits || (limits.isEmpty() && (layout != WidgetLayout.Compact || heightDp >= 195))) View.VISIBLE else View.GONE)
                views.setViewVisibility(R.id.widget_no_limits, if (limits.isEmpty()) View.VISIBLE else View.GONE)
                val rowIds = listOf(
                    intArrayOf(R.id.widget_quota_1, R.id.widget_logo_1, R.id.widget_provider_1, R.id.widget_percent_1, R.id.widget_bar_1),
                    intArrayOf(R.id.widget_quota_2, R.id.widget_logo_2, R.id.widget_provider_2, R.id.widget_percent_2, R.id.widget_bar_2),
                ).take(if (layout == WidgetLayout.Compact) 1 else 2)
                rowIds.forEachIndexed { index, row ->
                    val item = limits.getOrNull(index)?.takeIf { index == 0 || (layout != WidgetLayout.Portrait || heightDp >= 230) && (layout != WidgetLayout.Overview || heightDp >= OVERVIEW_SECOND_ROW_DP) }
                    views.setViewVisibility(row[0], if (item == null) View.GONE else View.VISIBLE)
                    if (item == null) return@forEachIndexed
                    val provider = item.provider.ifBlank { "Provider" }.providerLabel()
                    val window = windowTitle(item.window, item.siblings)
                    val remaining = item.remainingPercent
                    val tone = tone(palette, remaining)
                    views.setTextViewText(row[2], if (narrow) provider else "$provider · $window")
                    views.setTextColor(row[2], ink)
                    views.setTextViewText(row[3], widgetText(context, if (narrow) "${remaining.toInt()}%" else "${remaining.toInt()}% left"))
                    views.setTextColor(row[3], if (tone == palette.success) ink else tone.toArgb())
                    views.setContentDescription(row[0], widgetText(context, "$provider, $window, ${remaining.toInt()} percent remaining"))
                    views.setImageViewBitmap(row[4], quotaBar(palette, tone.toArgb(), remaining, contentWidth, (4 * density).roundToInt().coerceAtLeast(2)))
                    mark(views, row[1], item.provider, palette)
                }
            }

            // Chart
            if (layout == WidgetLayout.Large || layout == WidgetLayout.Overview) {
                val showChart = layout == WidgetLayout.Large || heightDp >= OVERVIEW_CHART_DP
                views.setViewVisibility(R.id.widget_chart, if (showChart) View.VISIBLE else View.GONE)
                if (showChart) {
                    val end = date ?: LocalDate.now()
                    val week = (6 downTo 0).map { end.minusDays(it.toLong()) }
                    val byDay = history.associateBy { it.label.take(10) }
                    val weekPoints = week.mapNotNull { byDay[it.toString()] }
                    views.setTextViewText(R.id.widget_chart_title, widgetText(context, "7 DAYS"))
                    views.setTextColor(R.id.widget_chart_title, ink)
                    views.setTextViewText(R.id.widget_chart_total, "${formatCompactTokens(weekPoints.sumOf { it.tokens })} · ${formatUsageCost(weekPoints.sumOf { it.costUsd }, weekPoints.sumOf { it.unpricedTokens }, compact = true)}")
                    views.setTextColor(R.id.widget_chart_total, muted)
                    views.setContentDescription(R.id.widget_chart_total, if (weekPoints.any { it.unpricedTokens > 0 }) formatUsageCost(weekPoints.sumOf { it.costUsd }, weekPoints.sumOf { it.unpricedTokens }) else null)
                    val barsDp = if (layout == WidgetLayout.Large) largeChartBarsDp(heightDp, blocks) else overviewChartBarsDp(heightDp, rows)
                    // Measure the configured layout: each XML dimension rounds independently,
                    // and quota rows can leave less space than the old fixed-dp estimate.
                    val chartSize = if (layout == WidgetLayout.Large) {
                        val measured = views.apply(context, android.widget.FrameLayout(context))
                        val widthPx = (size.width * density).toInt()
                        val heightPx = (heightDp * density).toInt()
                        measured.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY))
                        measured.layout(0, 0, widthPx, heightPx)
                        val chart = measured.findViewById<View>(R.id.widget_chart_bars)
                        chart.width to chart.height
                    } else contentWidth to (barsDp * density).roundToInt()
                    views.setImageViewBitmap(R.id.widget_chart_bars, trend(week, byDay, palette, chartSize.first, chartSize.second, density))
                    val dayIds = listOf(R.id.widget_day_0, R.id.widget_day_1, R.id.widget_day_2, R.id.widget_day_3, R.id.widget_day_4, R.id.widget_day_5, R.id.widget_day_6)
                    dayIds.forEachIndexed { index, id ->
                        views.setTextViewText(id, week[index].format(DateTimeFormatter.ofPattern("EEE", localizedContext(context).resources.configuration.locales[0])).uppercase(Locale.getDefault()))
                        views.setTextColor(id, if (index == 6) ink else muted)
                    }
                    views.setContentDescription(R.id.widget_chart, widgetText(context, "Seven calendar days of tokens; a dash means no recorded data"))
                }
            }

            fun action(code: Int, intent: Intent) = PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val open = action(0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            views.setOnClickPendingIntent(R.id.widget_root, open)
            views.setOnClickPendingIntent(R.id.widget_header, open)
            views.setOnClickPendingIntent(R.id.widget_refresh, action(1, Intent(context, WidgetControlActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION).setAction(WidgetLiveService.ACTION_REFRESH)))
            views.setOnClickPendingIntent(R.id.widget_live, action(2, Intent(context, WidgetControlActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION).setAction(WidgetLiveService.ACTION_LIVE)))
            views.setTextViewText(R.id.widget_no_limits, widgetText(context, "No quota windows reported"))
            if (layout == WidgetLayout.Large) views.setTextViewText(R.id.widget_limits_title, widgetText(context, "LIMITS"))
            applyTheme(views, palette, layout, enabled, empty = false)
            return views
        }

        private class LargeBlockIds(val block: Int, val logo: Int, val provider: Int, val cell1: IntArray, val cell2: IntArray)

        private fun tone(palette: Palette, remaining: Double): Color {
            val used = (1.0 - remaining / 100.0).toFloat()
            return when {
                used >= 0.85f -> palette.danger
                used >= 0.65f -> palette.orange
                else -> palette.success
            }
        }

        private fun mark(views: RemoteViews, id: Int, provider: String, palette: Palette) {
            val asset = upstreamToolAsset(provider)
            views.setViewVisibility(id, if (asset == null) View.GONE else View.VISIBLE)
            if (asset != null) {
                views.setImageViewResource(id, asset)
                views.setInt(id, "setColorFilter", originalToolColor(provider, palette.blue).toArgb())
            }
        }

        private fun empty(context: Context, layout: WidgetLayout, session: WidgetSession, palette: Palette, enabled: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.usage_widget_empty)
            val small = layout == WidgetLayout.Compact || layout == WidgetLayout.Wide
            val narrow = layout == WidgetLayout.Compact || layout == WidgetLayout.Portrait
            val density = context.resources.displayMetrics.density
            val padding = (layout.padding * density).toInt()
            views.setViewPadding(R.id.widget_root, padding, (4 * density).toInt(), padding, padding)
            views.setTextViewText(R.id.widget_title, widgetText(context, if (narrow) "USAGE" else "TOKEN MONITOR"))
            views.setTextViewText(R.id.widget_status, widgetText(context, if (narrow) "" else if (enabled) "WAITING" else "NO DATA"))
            views.setContentDescription(R.id.widget_status, widgetText(context, if (enabled) "Connecting to Hub" else "No saved data"))
            val waiting = enabled || session.refreshing
            views.setTextViewText(R.id.widget_empty_title, widgetText(context, if (waiting) { if (small) "Connecting" else "Waiting for usage" } else if (small) "No data yet" else "No saved usage"))
            views.setViewVisibility(R.id.widget_empty_detail, if (small) View.GONE else View.VISIBLE)
            views.setTextViewText(R.id.widget_empty_detail, widgetText(context, if (enabled) "Live is connecting to your Hub." else if (narrow) "Check your Hub in the app." else "Open the app to connect or check your Hub."))
            views.setTextViewText(R.id.widget_refresh, widgetText(context, if (narrow || small) "OPEN" else "OPEN APP"))
            views.setContentDescription(R.id.widget_refresh, widgetText(context, "Open app"))
            views.setContentDescription(R.id.widget_live, widgetText(context, "Turn widget Live off"))
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_header, open)
            views.setOnClickPendingIntent(R.id.widget_refresh, open)
            views.setViewVisibility(R.id.widget_live_container, if (enabled) View.VISIBLE else View.GONE)
            views.setOnClickPendingIntent(R.id.widget_live, PendingIntent.getActivity(context, 2,
                Intent(context, WidgetControlActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    .setAction(WidgetLiveService.ACTION_LIVE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            applyTheme(views, palette, layout, enabled, empty = true)
            return views
        }

        /** Colors every view from the dashboard palette; the layouts carry no theme of their own. */
        private fun applyTheme(views: RemoteViews, palette: Palette, layout: WidgetLayout, enabled: Boolean, empty: Boolean) {
            val ink = palette.ink.toArgb()
            val muted = palette.muted.toArgb()
            val accent = palette.accent.toArgb()
            val line = palette.line.compositeOver(palette.shell).toArgb()
            val controlFill = palette.overlay.compositeOver(palette.shell).toArgb()
            val edge = palette.strongLine.copy(alpha = 1f).toArgb()
            val edgeAlpha = (palette.strongLine.alpha * 255).toInt()

            val surface = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            Canvas(surface).drawRect(0f, 0f, 96f, 96f, Paint().apply {
                shader = LinearGradient(0f, 0f, 96f, 96f,
                    intArrayOf(palette.gradientTop.toArgb(), palette.shell.toArgb(), palette.gradientBottom.toArgb()),
                    floatArrayOf(0f, 0.38f, 1f), Shader.TileMode.CLAMP)
            })
            views.setImageViewBitmap(R.id.widget_surface_color, surface)
            views.setInt(R.id.widget_surface_border, "setColorFilter", edge)
            views.setInt(R.id.widget_surface_border, "setImageAlpha", edgeAlpha)

            // Refresh is an icon on an invisible 48 dp target. The empty state keeps its Open app pill.
            if (empty) {
                views.setInt(R.id.widget_refresh_fill, "setColorFilter", controlFill)
                views.setInt(R.id.widget_refresh_border, "setColorFilter", edge)
                views.setInt(R.id.widget_refresh_border, "setImageAlpha", edgeAlpha)
                views.setTextColor(R.id.widget_refresh, ink)
            } else {
                views.setInt(R.id.widget_refresh_image, "setColorFilter", ink)
            }

            // Live: a track with a knob at either end, and a soft glow behind the knob while on.
            views.setInt(R.id.widget_live_fill, "setColorFilter", if (enabled) palette.accent.copy(alpha = 0.22f).compositeOver(palette.shell).toArgb() else controlFill)
            views.setInt(R.id.widget_live_border, "setColorFilter", if (enabled) accent else edge)
            views.setInt(R.id.widget_live_border, "setImageAlpha", if (enabled) 200 else edgeAlpha)
            views.setViewVisibility(R.id.widget_knob_on, if (enabled) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_knob_off, if (enabled) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.widget_live_glow, if (enabled) View.VISIBLE else View.GONE)
            views.setInt(R.id.widget_knob_on, "setColorFilter", accent)
            views.setInt(R.id.widget_knob_off, "setColorFilter", muted)
            if (enabled) views.setImageViewBitmap(R.id.widget_live_glow, glow(accent))
            views.setTextColor(R.id.widget_live, if (enabled) accent else muted)

            if (empty) {
                views.setTextColor(R.id.widget_title, ink)
                views.setTextColor(R.id.widget_status, muted)
                views.setTextColor(R.id.widget_empty_title, ink)
                views.setTextColor(R.id.widget_empty_detail, muted)
                return
            }
            listOf(R.id.widget_tokens, R.id.widget_count_a, R.id.widget_count_b).forEach { views.setTextColor(it, ink) }
            views.setTextColor(R.id.widget_cost, muted)
            when (layout) {
                WidgetLayout.Compact, WidgetLayout.Portrait -> {
                    views.setTextColor(R.id.widget_date, muted)
                    views.setTextColor(R.id.widget_time, muted)
                    views.setTextColor(R.id.widget_no_limits, muted)
                }
                WidgetLayout.Wide -> views.setTextColor(R.id.widget_title, ink)
                WidgetLayout.Overview -> {
                    views.setTextColor(R.id.widget_title, ink)
                    views.setInt(R.id.widget_divider_1, "setBackgroundColor", line)
                    views.setTextColor(R.id.widget_no_limits, muted)
                }
                WidgetLayout.Large -> {
                    views.setTextColor(R.id.widget_title, ink)
                    views.setTextColor(R.id.widget_limits_title, ink)
                    views.setTextColor(R.id.widget_no_limits, muted)
                    listOf(R.id.widget_divider_1, R.id.widget_divider_2, R.id.widget_divider_3).forEach { views.setInt(it, "setBackgroundColor", line) }
                }
            }
        }

        private fun glow(color: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(32f, 32f, 32f, intArrayOf(color and 0x66FFFFFF, color and 0x00FFFFFF), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            }
            Canvas(bitmap).drawCircle(32f, 32f, 32f, paint)
            return bitmap
        }

        /** A track and fill drawn at the exact pixel size the bar occupies, so the launcher never scales it. */
        private fun quotaBar(palette: Palette, fill: Int, remaining: Double, width: Int, height: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val radius = height / 2f
            paint.color = palette.strongLine.compositeOver(palette.shell).toArgb()
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint)
            paint.color = fill
            val filled = (width * remaining / 100.0).toFloat().coerceIn(if (remaining > 0) height.toFloat() else 0f, width.toFloat())
            if (filled > 0f) canvas.drawRoundRect(0f, 0f, filled, height.toFloat(), radius, radius, paint)
            return bitmap
        }

        /** Today's tool split as one rounded bar of colored segments. */
        private fun toolsBar(palette: Palette, shares: List<Triple<String, Double, Int>>, width: Int, height: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val radius = height / 2f
            paint.color = palette.strongLine.compositeOver(palette.shell).toArgb()
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint)
            canvas.clipPath(Path().apply { addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, Path.Direction.CW) })
            var left = 0f
            shares.forEach { (_, share, color) ->
                val right = (left + width * share).toFloat().coerceAtMost(width.toFloat())
                paint.color = color
                canvas.drawRect(left, 0f, right, height.toFloat(), paint)
                left = right + 1.5f
            }
            return bitmap
        }

        /** Seven calendar days ending on the snapshot date; today takes the accent, a missing day a dash. */
        private fun trend(days: List<LocalDate>, byDay: Map<String, HistoryPoint>, palette: Palette, width: Int, height: Int, density: Float): Bitmap {
            val bitmap = Bitmap.createBitmap(width.coerceAtLeast(7), height.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val maximum = days.mapNotNull { byDay[it.toString()]?.tokens }.maxOrNull()?.coerceAtLeast(1) ?: 1L
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val column = bitmap.width / 7f
            val barWidth = column * 0.56f
            val radius = 2f * density
            val floor = bitmap.height.toFloat()
            days.forEachIndexed { index, day ->
                val value = byDay[day.toString()]?.tokens
                val left = index * column + (column - barWidth) / 2
                if (value != null) {
                    paint.color = (if (index == 6) palette.accent else palette.chart).toArgb()
                    val barHeight = (value.toDouble() * (floor - 2f) / maximum).toFloat().coerceAtLeast(2f * density)
                    canvas.drawRoundRect(left, floor - barHeight, left + barWidth, floor, radius, radius, paint)
                } else {
                    paint.color = palette.muted.copy(alpha = 0.6f).toArgb()
                    canvas.drawRoundRect(left, floor - 2f * density, left + barWidth, floor, radius, radius, paint)
                }
            }
            return bitmap
        }
    }
}

/** Explicit picker entries share rendering, cache and update controls with the original responsive widget. */
class CompactWidgetProvider : UsageWidgetProvider()
class WideWidgetProvider : UsageWidgetProvider()
class PortraitWidgetProvider : UsageWidgetProvider()
class OverviewWidgetProvider : UsageWidgetProvider()
class LargeWidgetProvider : UsageWidgetProvider()

internal val usageWidgetProviders = listOf(
    UsageWidgetProvider::class.java, CompactWidgetProvider::class.java, WideWidgetProvider::class.java,
    PortraitWidgetProvider::class.java, OverviewWidgetProvider::class.java, LargeWidgetProvider::class.java,
)

internal fun widgetPreset(className: String?): WidgetLayout? = when (className?.substringAfterLast('.')) {
    "CompactWidgetProvider" -> WidgetLayout.Compact
    "WideWidgetProvider" -> WidgetLayout.Wide
    "PortraitWidgetProvider" -> WidgetLayout.Portrait
    "OverviewWidgetProvider" -> WidgetLayout.Overview
    "LargeWidgetProvider" -> WidgetLayout.Large
    else -> null
}
