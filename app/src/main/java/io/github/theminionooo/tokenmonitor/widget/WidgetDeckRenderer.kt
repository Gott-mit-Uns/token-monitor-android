package io.github.theminionooo.tokenmonitor.widget

import io.github.theminionooo.tokenmonitor.localization.localizedContext
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.SizeF
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.domain.HistoryPoint
import io.github.theminionooo.tokenmonitor.ui.InterfaceTheme
import io.github.theminionooo.tokenmonitor.ui.Palette
import io.github.theminionooo.tokenmonitor.ui.displayName
import io.github.theminionooo.tokenmonitor.ui.formatCompactTokens
import io.github.theminionooo.tokenmonitor.ui.formatMoney
import io.github.theminionooo.tokenmonitor.ui.formatTokens
import io.github.theminionooo.tokenmonitor.ui.originalToolColor
import io.github.theminionooo.tokenmonitor.ui.providerLabel
import io.github.theminionooo.tokenmonitor.ui.upstreamToolAsset
import io.github.theminionooo.tokenmonitor.ui.vendorOf
import io.github.theminionooo.tokenmonitor.widget.WidgetDeckGrid.Type
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Draws the selected deck page into one immutable frame. The launcher receives a
 * single ImageView, so it cannot add collection-card depth or resize each page.
 *
 * Every coordinate comes from [WidgetDeckGrid], which mirrors `docs/WIDGET_SPEC.md`.
 * The canvas is scaled once so the grid's reference units fill the fitted card.
 */
internal object WidgetDeckRenderer {
    internal const val CARD_ASPECT = WidgetDeckGrid.ASPECT
    // RemoteViews is displayed in the launcher's density, not at one bitmap pixel
    // per dp. Keep the single card sharp while remaining well below Android's
    // 1.5-screen aggregate bitmap budget for App Widgets.
    private const val MAX_BITMAP_WIDTH = 1200

    internal fun frameSize(size: SizeF): SizeF {
        val height = min(size.height, size.width / CARD_ASPECT).coerceAtLeast(1f)
        val width = min(size.width, height * CARD_ASPECT).coerceAtLeast(1f)
        return SizeF(width, height)
    }

    internal fun bitmapScale(frameWidth: Float, density: Float): Float {
        val safeWidth = frameWidth.coerceAtLeast(1f)
        return min(density, MAX_BITMAP_WIDTH / safeWidth).coerceAtLeast(1f / safeWidth)
    }

    fun render(
        context: Context,
        page: WidgetDeckPage,
        data: WidgetDeckData,
        theme: InterfaceTheme,
        size: SizeF,
    ): RemoteViews {
        val bitmap = renderBitmap(context, page, data, Palette.from(theme), size)
        return RemoteViews(context.packageName, R.layout.usage_widget_deck).apply {
            setImageViewBitmap(R.id.swipe_page_bitmap, bitmap)
            setContentDescription(R.id.swipe_page_bitmap, description(context, page, data))
            setContentDescription(R.id.swipe_page_open, description(context, page, data))
            setContentDescription(R.id.swipe_page_previous, widgetText(context, "Previous Token Monitor page"))
            setContentDescription(R.id.swipe_page_next, widgetText(context, "Next Token Monitor page"))
            setContentDescription(R.id.widget_refresh, widgetText(context, if (data.status == "UPDATING") "Refreshing widget" else "Refresh widget now"))
            setContentDescription(R.id.widget_live, widgetText(context, if (data.liveEnabled) "Turn widget Live off" else "Turn widget Live on for one hour"))
        }
    }

    internal fun renderBitmap(
        context: Context,
        page: WidgetDeckPage,
        data: WidgetDeckData,
        palette: Palette,
        size: SizeF,
    ): Bitmap {
        val frame = frameSize(size)
        val renderScale = bitmapScale(frame.width, context.resources.displayMetrics.density)
        val width = (frame.width * renderScale).roundToInt().coerceAtLeast(1)
        val height = (frame.height * renderScale).roundToInt().coerceAtLeast(1)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.scale(width / WidgetDeckGrid.WIDTH, height / WidgetDeckGrid.HEIGHT)
            DeckCanvas(localizedContext(context), canvas, palette).draw(page, data)
        }
    }

    private fun description(context: Context, page: WidgetDeckPage, data: WidgetDeckData): String {
        val summary = widgetDeckPageSummary(page, data)
        return "Token Monitor ${context.getString(page.subtitle)}. $summary ${data.statusDescription}. Tap the left or right edge to change page, or tap the content to open."
    }
}

/** A text style from the specification's type table. */
private class DeckType(val typeface: Typeface, val size: Float, val tracking: Float = 0f, val uppercase: Boolean = false, val tabular: Boolean = false)

private class DeckCanvas(
    private val context: Context,
    private val canvas: Canvas,
    private val palette: Palette,
) {
    private val width = WidgetDeckGrid.WIDTH
    private val height = WidgetDeckGrid.HEIGHT
    private val left = WidgetDeckGrid.LEFT
    private val right = WidgetDeckGrid.RIGHT

    private val mono = font(R.font.jetbrains_mono_regular, Typeface.MONOSPACE)
    private val monoBold = font(R.font.jetbrains_mono_bold, Typeface.create(Typeface.MONOSPACE, Typeface.BOLD))
    private val sansBold = Typeface.create("sans-serif", Typeface.BOLD)

    private val brand = DeckType(monoBold, Type.BRAND, tracking = 0.04f, uppercase = true)
    private val pageType = DeckType(mono, Type.PAGE, tracking = 0.06f, uppercase = true)
    private val section = DeckType(monoBold, Type.SECTION, tracking = 0.06f, uppercase = true)
    private val status = DeckType(monoBold, Type.STATUS, tracking = 0.04f, uppercase = true)
    private val display = DeckType(sansBold, Type.DISPLAY, tabular = true)
    private val figure = DeckType(sansBold, Type.FIGURE, tabular = true)
    private val stat = DeckType(sansBold, Type.STAT, tabular = true)
    private val body = DeckType(mono, Type.BODY)
    private val bodyStrong = DeckType(monoBold, Type.BODY_STRONG)
    private val legend = DeckType(monoBold, Type.LEGEND)
    private val secondary = DeckType(mono, Type.SECONDARY)
    private val caption = DeckType(mono, Type.CAPTION, tracking = 0.03f, uppercase = true)
    private val axis = DeckType(mono, Type.AXIS, uppercase = true)

    private val ink = palette.ink.toArgb()
    private val muted = palette.muted.toArgb()
    private val label = lerp(palette.muted, palette.ink, 0.30f).toArgb()
    private val accent = palette.accent.toArgb()
    private val line = palette.line.compositeOver(palette.shell).toArgb()
    private val strongLine = palette.strongLine.compositeOver(palette.shell).toArgb()
    private val fallbackColors = listOf(palette.blue, palette.orange, palette.purple, palette.yellow)

    private fun font(resource: Int, fallback: Typeface): Typeface =
        runCatching { ResourcesCompat.getFont(context, resource) }.getOrNull() ?: fallback

    fun draw(page: WidgetDeckPage, data: WidgetDeckData) {
        drawSurface()
        drawHeader(page, data)
        if (!data.hasData) drawCentered(widgetText(context, data.emptyMessage)) else when (page) {
            WidgetDeckPage.Overview -> drawOverview(data)
            WidgetDeckPage.Limits -> drawLimits(data)
            WidgetDeckPage.Breakdown -> drawBreakdown(data)
            WidgetDeckPage.Activity -> drawActivity(data)
        }
        drawPager(page)
    }

    // Surface and chrome

    private fun drawSurface() {
        val inset = WidgetDeckGrid.EDGE_STROKE / 2f
        val card = RectF(inset, inset, width - inset, height - inset)
        val radius = WidgetDeckGrid.RADIUS
        canvas.drawRoundRect(card, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width, height,
                intArrayOf(palette.gradientTop.toArgb(), palette.shell.toArgb(), palette.gradientBottom.toArgb()),
                floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP,
            )
        })
        if (!palette.isLight) {
            glow(width * 0.12f, 0f, width * 0.46f, palette.blue.copy(alpha = 0.22f), card, radius)
            glow(width * 0.88f, height * 0.05f, width * 0.34f, palette.accent.copy(alpha = 0.18f), card, radius)
        }
        canvas.drawRoundRect(card, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (palette.isLight) strongLine else lerp(palette.strongLine.compositeOver(palette.shell), palette.blue, 0.38f).toArgb()
            style = Paint.Style.STROKE
            strokeWidth = WidgetDeckGrid.EDGE_STROKE
        })
    }

    private fun glow(x: Float, y: Float, radius: Float, color: Color, card: RectF, cornerRadius: Float) {
        canvas.drawRoundRect(card, cornerRadius, cornerRadius, Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            shader = RadialGradient(x, y, radius, intArrayOf(color.toArgb(), color.copy(alpha = 0f).toArgb()), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        })
    }

    private fun drawHeader(page: WidgetDeckPage, data: WidgetDeckData) {
        val h = WidgetDeckGrid.Header
        drawDrawable(R.drawable.token_monitor_icon, h.ICON_X, h.ICON_Y, h.ICON_SIZE, h.ICON_SIZE)
        text(widgetText(context, "Token Monitor"), h.TEXT_X, h.BRAND_BASELINE, brand, ink, maxWidth = h.REFRESH_X - h.TEXT_X - 8f)
        text(context.getString(page.subtitle), h.TEXT_X, h.PAGE_BASELINE, pageType, muted, maxWidth = h.REFRESH_X - h.TEXT_X - 8f)
        drawDrawable(R.drawable.ic_widget_refresh, h.REFRESH_X, h.REFRESH_Y, h.REFRESH_SIZE, h.REFRESH_SIZE, ink)
        val live = data.liveEnabled && data.status == "LIVE"
        text(widgetText(context, data.status), h.STATUS_RIGHT, h.STATUS_BASELINE, status, if (live) accent else muted, Paint.Align.RIGHT, h.STATUS_RIGHT - h.REFRESH_X - h.REFRESH_SIZE - 6f)
        val toggle = RectF(h.TOGGLE_LEFT, h.TOGGLE_TOP, h.TOGGLE_LEFT + h.TOGGLE_WIDTH, h.TOGGLE_TOP + h.TOGGLE_HEIGHT)
        val trackRadius = h.TOGGLE_HEIGHT / 2f
        val track = if (data.liveEnabled) palette.accent.copy(alpha = 0.18f).compositeOver(palette.shell) else palette.overlay.compositeOver(palette.shell)
        fillRoundRect(toggle, trackRadius, track.toArgb())
        strokeRoundRect(toggle, trackRadius, if (data.liveEnabled) accent else strongLine, 1f)
        val knobX = if (data.liveEnabled) toggle.right - trackRadius else toggle.left + trackRadius
        circle(knobX, toggle.centerY(), h.KNOB_RADIUS, if (data.liveEnabled) accent else muted)
    }

    private fun drawPager(current: WidgetDeckPage) {
        val h = WidgetDeckGrid.Header
        val chevron = palette.muted.copy(alpha = 0.78f).compositeOver(palette.shell).toArgb()
        drawChevron(h.CHEVRON_LEFT_X, h.CHEVRON_Y, pointsLeft = true, color = chevron)
        drawChevron(h.CHEVRON_RIGHT_X, h.CHEVRON_Y, pointsLeft = false, color = chevron)
        val startX = width / 2f - h.DOTS_GAP * (WidgetDeckPage.entries.size - 1) / 2f
        WidgetDeckPage.entries.forEachIndexed { index, candidate ->
            circle(startX + index * h.DOTS_GAP, h.DOTS_Y, if (candidate == current) 2f else 1.5f, if (candidate == current) accent else strongLine)
        }
    }

    private fun drawChevron(centerX: Float, centerY: Float, pointsLeft: Boolean, color: Int) {
        val h = WidgetDeckGrid.Header
        val pointX = centerX + if (pointsLeft) -h.CHEVRON_HALF_WIDTH else h.CHEVRON_HALF_WIDTH
        val outerX = centerX + if (pointsLeft) h.CHEVRON_HALF_WIDTH else -h.CHEVRON_HALF_WIDTH
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawLine(outerX, centerY - h.CHEVRON_HALF_HEIGHT, pointX, centerY, paint)
        canvas.drawLine(pointX, centerY, outerX, centerY + h.CHEVRON_HALF_HEIGHT, paint)
    }

    // Overview

    private fun drawOverview(data: WidgetDeckData) {
        val g = WidgetDeckGrid.Overview
        val snapshot = requireNotNull(data.snapshot)
        text(widgetText(context, "Total tokens"), left, g.SECTION_BASELINE, section, label)
        fitText(formatTokens(snapshot.today.totalTokens), left, g.TOTAL_BASELINE, display, Type.DISPLAY_MIN, ink, g.TOTAL_RIGHT - left)
        text(widgetText(context, "${formatMoney(snapshot.today.costUsd)} estimated cost"), left, g.COST_BASELINE, body, muted, maxWidth = g.TOTAL_RIGHT - left)
        vline(g.DIVIDER_X, g.DIVIDER_TOP, g.DIVIDER_BOTTOM, line)
        val statsWidth = right - g.STATS_X
        data.stats.take(3).forEachIndexed { index, (value, name) ->
            val top = g.STATS_TOP + index * g.STATS_PITCH
            text(widgetText(context, value), g.STATS_X, top + g.STAT_VALUE_OFFSET, stat, ink, maxWidth = statsWidth)
            text(widgetText(context, name), g.STATS_X, top + g.STAT_CAPTION_OFFSET, caption, muted, maxWidth = statsWidth)
        }
        val tools = data.tools.take(3).mapIndexed { index, row -> row to vendorColor(row.name, index) }
        drawSegmentedBar(RectF(left, g.BAR_TOP, right, g.BAR_BOTTOM), tools.map { it.first.share to it.second })
        val slot = (right - left) / 3f
        tools.forEachIndexed { index, (row, color) ->
            val x = left + index * slot
            circle(x + g.LEGEND_DOT_INSET, g.LEGEND_DOT_Y, g.LEGEND_DOT_RADIUS, color)
            text(widgetText(context, "${row.name.displayName()} ${sharePercent(row.share)}"), x + g.LEGEND_TEXT_INSET, g.LEGEND_BASELINE, legend, ink, maxWidth = slot - g.LEGEND_TEXT_INSET - 6f)
        }
        hline(left, right, g.RULE_Y, line)
        val weekTokens = data.week.sumOf { it.tokens }
        val weekCost = data.week.sumOf { it.costUsd }
        text(widgetText(context, "This week"), left, g.WEEK_BASELINE, caption, muted)
        if (weekTokens > 0) {
            val advance = text(formatCompactTokens(weekTokens), g.WEEK_VALUE_X, g.WEEK_BASELINE, stat, ink)
            text(widgetText(context, "· ${formatMoney(weekCost)}"), g.WEEK_VALUE_X + advance + 8f, g.WEEK_BASELINE, body, muted, maxWidth = right - g.WEEK_VALUE_X - advance - 8f)
        } else {
            text(widgetText(context, "No history yet"), g.WEEK_VALUE_X, g.WEEK_BASELINE, body, muted)
        }
    }

    // Limits

    private fun drawLimits(data: WidgetDeckData) {
        val g = WidgetDeckGrid.Limits
        if (data.limitGroups.isEmpty()) {
            drawCentered(context.getString(R.string.widget_no_limits))
            return
        }
        val groups = data.limitGroups.take(g.ROW_TOPS.size)
        if (groups.size > 1) hline(left, right, g.RULE_Y, line)
        groups.forEachIndexed { index, group ->
            val top = g.ROW_TOPS[index]
            drawMark(group.provider, left, top + g.MARK_OFFSET, g.MARK_SIZE, vendorColor(group.provider, 0), R.drawable.view_limits)
            group.windows.take(2).forEachIndexed { column, window ->
                val cellLeft = if (column == 0) g.LEFT_TEXT_X else g.RIGHT_TEXT_X
                val cellRight = if (column == 0) g.LEFT_RIGHT_EDGE else right
                val cellWidth = cellRight - cellLeft
                val tone = WidgetDeckDrawing.tone(palette, window.remainingPercent).toArgb()
                val titleType = DeckType(monoBold, g.TITLE_SIZE, tracking = 0.04f, uppercase = true)
                val fullTitle = "${group.provider.providerLabel()} · ${window.title}"
                text(if (measure(fullTitle, titleType) <= cellWidth) fullTitle else window.title, cellLeft, top + g.TITLE_OFFSET, titleType, label, maxWidth = cellWidth)
                val percent = text(widgetText(context, "${window.remainingPercent.roundToInt()}%"), cellLeft, top + g.VALUE_OFFSET, figure, if (window.remainingPercent > 35) ink else tone)
                text(widgetText(context, "left"), cellLeft + percent + 6f, top + g.VALUE_OFFSET, DeckType(mono, g.LEFT_LABEL_SIZE), ink, maxWidth = cellWidth - percent - 6f)
                drawBar(RectF(cellLeft, top + g.BAR_TOP_OFFSET, cellRight, top + g.BAR_BOTTOM_OFFSET), window.remainingPercent / 100.0, tone)
                text(widgetText(context, window.reset.ifBlank { "Reset not reported" }), cellLeft, top + g.RESET_OFFSET, secondary, muted, maxWidth = cellWidth)
            }
        }
    }

    // Breakdown

    private fun drawBreakdown(data: WidgetDeckData) {
        val g = WidgetDeckGrid.Breakdown
        if (data.tools.isEmpty() && data.models.isEmpty()) {
            drawCentered(widgetText(context, "No tool or model breakdown reported"))
            return
        }
        vline(g.DIVIDER_X, g.DIVIDER_TOP, g.DIVIDER_BOTTOM, line)
        text(widgetText(context, "Tools"), left, g.SECTION_BASELINE, section, label)
        text(widgetText(context, "Models"), g.MODELS_X, g.SECTION_BASELINE, section, label)
        if (data.tools.size <= 1 && data.models.size <= 1) {
            data.tools.firstOrNull()?.let { row ->
                val color = vendorColor(row.name, 0)
                drawMark(row.name, left, g.SPARSE_MARK_TOP, g.SPARSE_MARK_SIZE, color, R.drawable.view_tool)
                text(row.name.displayName(), g.SPARSE_TOOL_NAME_X, g.SPARSE_IDENTITY_BASELINE, bodyStrong, ink, maxWidth = g.TOOL_RIGHT - g.SPARSE_TOOL_NAME_X)
                fitText(compactFigure(row.tokens), left, g.SPARSE_VALUE_BASELINE, figure, Type.STAT, ink, 72f)
                text(sharePercent(row.share), g.TOOL_RIGHT, g.SPARSE_VALUE_BASELINE, figure, ink, Paint.Align.RIGHT, 68f)
                text(widgetText(context, "Tokens"), left, g.SPARSE_CAPTION_BASELINE, caption, muted)
                text(widgetText(context, "Share"), g.TOOL_RIGHT, g.SPARSE_CAPTION_BASELINE, caption, muted, Paint.Align.RIGHT)
                drawBar(RectF(left, g.SPARSE_BAR_TOP, g.TOOL_RIGHT, g.SPARSE_BAR_BOTTOM), row.share, color)
                if (row.costUsd > 0) text(widgetText(context, "${formatMoney(row.costUsd)} estimated cost"), left, g.SPARSE_COST_BASELINE, secondary, muted)
            }
            data.models.firstOrNull()?.let { row ->
                val color = vendorColor(row.name, 0)
                drawMark(row.name, g.MODELS_X, g.SPARSE_MARK_TOP, g.SPARSE_MARK_SIZE, color, R.drawable.view_model)
                text(row.name, g.SPARSE_MODEL_NAME_X, g.SPARSE_IDENTITY_BASELINE, bodyStrong, ink, maxWidth = right - g.SPARSE_MODEL_NAME_X)
                fitText(compactFigure(row.tokens), g.MODELS_X, g.SPARSE_VALUE_BASELINE, figure, Type.STAT, ink, 72f)
                text(sharePercent(row.share), right, g.SPARSE_VALUE_BASELINE, figure, ink, Paint.Align.RIGHT, 68f)
                text(widgetText(context, "Tokens"), g.MODELS_X, g.SPARSE_CAPTION_BASELINE, caption, muted)
                text(widgetText(context, "Share"), right, g.SPARSE_CAPTION_BASELINE, caption, muted, Paint.Align.RIGHT)
                drawBar(RectF(g.MODELS_X, g.SPARSE_BAR_TOP, right, g.SPARSE_BAR_BOTTOM), row.share, color)
            }
            return
        }
        drawBreakdownRows(data.tools, left, g.TOOL_NAME_X, g.TOOL_RIGHT, R.drawable.view_tool) { it.displayName() }
        drawBreakdownRows(data.models, g.MODELS_X, g.MODEL_NAME_X, right, R.drawable.view_model) { it }
    }

    /** One dense column: mark, name and share on the first line, tokens and cost on the second, then the bar. */
    private fun drawBreakdownRows(rows: List<WidgetDeckUsageRow>, columnLeft: Float, nameX: Float, columnRight: Float, fallbackMark: Int, label: (String) -> String) {
        val g = WidgetDeckGrid.Breakdown
        rows.take(g.ROW_TOPS.size).forEachIndexed { index, row ->
            val top = g.ROW_TOPS[index]
            val color = vendorColor(row.name, index)
            drawMark(row.name, columnLeft, top + g.MARK_OFFSET, g.MARK_SIZE, color, fallbackMark)
            val shareWidth = text(sharePercent(row.share), columnRight, top + g.NAME_BASELINE_OFFSET, bodyStrong, ink, Paint.Align.RIGHT)
            text(label(row.name), nameX, top + g.NAME_BASELINE_OFFSET, body, ink, maxWidth = columnRight - shareWidth - g.SHARE_GAP - nameX)
            val detail = compactFigure(row.tokens) + if (row.costUsd > 0) " · ${formatMoney(row.costUsd)}" else ""
            text(detail, nameX, top + g.DETAIL_BASELINE_OFFSET, secondary, muted, maxWidth = columnRight - nameX)
            drawBar(RectF(columnLeft, top + g.BAR_TOP_OFFSET, columnRight, top + g.BAR_BOTTOM_OFFSET), row.share, color)
        }
    }

    // Activity

    private fun drawActivity(data: WidgetDeckData) {
        val g = WidgetDeckGrid.Activity
        if (data.history.isEmpty()) {
            drawCentered(widgetText(context, "No activity history reported"))
            return
        }
        vline(g.DIVIDER_X, g.DIVIDER_TOP, g.DIVIDER_BOTTOM, line)

        val weekTokens = data.week.sumOf { it.tokens }
        val weekCost = data.week.sumOf { it.costUsd }
        val peak = data.week.maxOfOrNull { it.tokens } ?: 0L
        text(widgetText(context, "7 days"), left, g.SECTION_BASELINE, section, label)
        val advance = text(compactFigure(weekTokens), left, g.SUMMARY_BASELINE, stat, ink)
        val peakWidth = measure(widgetText(context, "Peak ${compactFigure(peak)}"), caption)
        text(widgetText(context, "tokens · ${formatMoney(weekCost)}"), left + advance + 6f, g.SUMMARY_BASELINE, secondary, muted, maxWidth = g.LEFT_RIGHT_EDGE - peakWidth - 10f - left - advance - 6f)
        text(widgetText(context, "Peak ${compactFigure(peak)}"), g.LEFT_RIGHT_EDGE, g.SUMMARY_BASELINE, caption, muted, Paint.Align.RIGHT)
        drawWeekChart(data.history, data.date)

        text(widgetText(context, "Activity"), g.RIGHT_X, g.SECTION_BASELINE, section, label)
        drawHeatmap(data.history, data.date)
        hline(g.RIGHT_X, right, g.RULE_Y, line)
        vline(g.STAT_DIVIDER_X, g.STAT_DIVIDER_TOP, g.STAT_DIVIDER_BOTTOM, line)
        text(data.activeDays.toString(), g.RIGHT_X, g.STAT_VALUE_BASELINE, stat, ink)
        fitCaption(widgetText(context, "Active days"), widgetText(context, "Days"), g.RIGHT_X, g.STAT_DIVIDER_X - g.RIGHT_X - 6f)
        if (data.messagesToday > 0) {
            text(compactFigure(data.messagesToday), g.STAT_RIGHT_X, g.STAT_VALUE_BASELINE, stat, ink)
            fitCaption(widgetText(context, "Messages today"), widgetText(context, "Messages"), g.STAT_RIGHT_X, right - g.STAT_RIGHT_X)
        } else {
            // The Hub reports no message count for today; a zero here would be a false figure.
            text(formatMoney(data.snapshot?.today?.costUsd ?: 0.0), g.STAT_RIGHT_X, g.STAT_VALUE_BASELINE, stat, ink)
            fitCaption(widgetText(context, "Cost today"), widgetText(context, "Cost"), g.STAT_RIGHT_X, right - g.STAT_RIGHT_X)
        }
    }

    private fun drawWeekChart(history: List<HistoryPoint>, end: LocalDate) {
        val g = WidgetDeckGrid.Activity
        val byDay = history.associateBy { it.label.take(10) }
        val days = (6 downTo 0).map { end.minusDays(it.toLong()) }
        val values = days.map { byDay[it.toString()] }
        val peak = values.maxOfOrNull { it?.tokens ?: 0L } ?: 0L
        val top = niceCeiling(ceil(peak / 3.0).toLong()) * 3
        val plotHeight = g.PLOT_BOTTOM - g.PLOT_TOP
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = line
            strokeWidth = 1f
            pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f)
        }
        listOf(1f / 3f, 2f / 3f, 1f).forEach { fraction ->
            val y = g.PLOT_BOTTOM - plotHeight * fraction
            canvas.drawLine(g.PLOT_LEFT, y, g.LEFT_RIGHT_EDGE, y, gridPaint)
            text(axisLabel(top * fraction), left, y + 2.5f, axis, muted, maxWidth = g.PLOT_LEFT - left - 2f)
        }
        text(widgetText(context, "0"), left, g.PLOT_BOTTOM + 2.5f, axis, muted)
        hline(g.PLOT_LEFT, g.LEFT_RIGHT_EDGE, g.PLOT_BOTTOM, strongLine)
        val column = (g.LEFT_RIGHT_EDGE - g.PLOT_LEFT) / 7f
        val barWidth = column * g.BAR_FRACTION
        values.forEachIndexed { index, point ->
            val x = g.PLOT_LEFT + index * column + (column - barWidth) / 2f
            val today = index == 6
            if (point == null || point.tokens <= 0) {
                fillRoundRect(RectF(x, g.PLOT_BOTTOM - 1.5f, x + barWidth, g.PLOT_BOTTOM), 0.75f, strongLine)
            } else {
                val barHeight = (point.tokens.toDouble() / top * plotHeight).toFloat().coerceIn(2f, plotHeight)
                val barTop = g.PLOT_BOTTOM - barHeight
                fillRoundRect(RectF(x, barTop, x + barWidth, g.PLOT_BOTTOM), g.BAR_RADIUS, if (today) accent else palette.blue.toArgb())
                if (!today) {
                    val claude = point.perClient.entries.filter { vendorOf(it.key) == "claude" }.sumOf { it.value.tokens }
                    if (claude > 0 && point.tokens > 0) {
                        val capHeight = (barHeight * claude / point.tokens.toDouble()).toFloat()
                        if (capHeight >= 1.5f) {
                            canvas.save()
                            canvas.clipRect(x, barTop, x + barWidth, barTop + capHeight)
                            fillRoundRect(RectF(x, barTop, x + barWidth, g.PLOT_BOTTOM), g.BAR_RADIUS, palette.orange.toArgb())
                            canvas.restore()
                        }
                    }
                }
            }
            text(days[index].dayOfWeek.getDisplayName(TextStyle.SHORT, localizedContext(context).resources.configuration.locales[0]), x + barWidth / 2f, g.DAY_LABEL_BASELINE, caption, muted, Paint.Align.CENTER)
        }
    }

    private fun drawHeatmap(history: List<HistoryPoint>, end: LocalDate) {
        val g = WidgetDeckGrid.Activity
        val byDay = history.associateBy { it.label.take(10) }
        val start = end.minusDays((g.HEAT_COLUMNS - 1) * 7L + (end.dayOfWeek.value % 7).toLong())
        val maximum = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }
            .mapNotNull { byDay[it.toString()]?.tokens }.maxOrNull()?.coerceAtLeast(1) ?: 1L
        val cell = (right - g.RIGHT_X - g.HEAT_GAP * (g.HEAT_COLUMNS - 1)) / g.HEAT_COLUMNS
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val months = mutableListOf<Pair<Int, LocalDate>>()
        var date = start
        repeat(g.HEAT_COLUMNS * g.HEAT_ROWS) { offset ->
            val column = offset / g.HEAT_ROWS
            val row = offset % g.HEAT_ROWS
            if (!date.isAfter(end)) {
                if (date.dayOfMonth == 1) months += column to date
                val tokens = byDay[date.toString()]?.tokens ?: 0L
                val step = when {
                    tokens <= 0 -> 0
                    tokens.toDouble() / maximum >= 0.75 -> 4
                    tokens.toDouble() / maximum >= 0.5 -> 3
                    tokens.toDouble() / maximum >= 0.25 -> 2
                    else -> 1
                }
                paint.color = palette.heat[step].toArgb()
                val x = g.RIGHT_X + column * (cell + g.HEAT_GAP)
                val y = g.HEAT_TOP + row * (cell + g.HEAT_GAP)
                canvas.drawRoundRect(x, y, x + cell, y + cell, g.HEAT_RADIUS, g.HEAT_RADIUS, paint)
            }
            date = date.plusDays(1)
        }
        var lastLabelRight = Float.NEGATIVE_INFINITY
        months.forEach { (column, monthDate) ->
            val x = g.RIGHT_X + column * (cell + g.HEAT_GAP)
            val name = monthDate.month.getDisplayName(TextStyle.SHORT, localizedContext(context).resources.configuration.locales[0])
            val labelWidth = measure(name, caption)
            if (x >= lastLabelRight + 4f && x + labelWidth <= right + 1f) {
                text(name, x, g.MONTH_BASELINE, caption, muted)
                lastLabelRight = x + labelWidth
            }
        }
    }

    // Shared drawing helpers

    private fun drawCentered(message: String) {
        val maxWidth = right - left
        val paint = paint(body, muted)
        val lines = mutableListOf<String>()
        var current = ""
        message.split(' ').forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) current = candidate
            else { lines += current; current = word }
        }
        if (current.isNotEmpty()) lines += current
        val lineHeight = 15f
        val start = (WidgetDeckGrid.Overview.DIVIDER_TOP + WidgetDeckGrid.Activity.DIVIDER_BOTTOM) / 2f - (lines.size - 1) * lineHeight / 2f
        lines.take(3).forEachIndexed { index, value -> text(value, width / 2f, start + index * lineHeight, body, muted, Paint.Align.CENTER, maxWidth) }
    }

    /** Widget figures: one decimal below 100K and 100M, none above, so number columns stay narrow. */
    private fun compactFigure(value: Long): String = when {
        value >= 100_000_000L -> "${(value / 1_000_000.0).roundToInt()}M"
        value >= 1_000_000L -> formatCompactTokens(value)
        value >= 100_000L -> "${(value / 1_000.0).roundToInt()}K"
        else -> formatCompactTokens(value)
    }

    /** Draws the long caption when it fits the column, otherwise the short one. */
    private fun fitCaption(long: String, short: String, x: Float, maxWidth: Float) {
        text(if (measure(long, caption) <= maxWidth) long else short, x, WidgetDeckGrid.Activity.STAT_CAPTION_BASELINE, caption, muted, maxWidth = maxWidth)
    }

    private fun vendorColor(name: String, index: Int): Int = originalToolColor(name, fallbackColors[index % fallbackColors.size]).toArgb()

    private fun sharePercent(share: Double): String {
        val percent = share * 100
        return if (percent > 0 && percent < 1) "<1%" else "${percent.roundToInt()}%"
    }

    private fun niceCeiling(value: Long): Double {
        if (value <= 0) return 1.0
        val magnitude = 10.0.pow(floor(log10(value.toDouble())))
        val normalized = value / magnitude
        val step = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0).first { normalized <= it + 1e-9 }
        return step * magnitude
    }

    private fun axisLabel(value: Double): String = when {
        value >= 1_000_000_000 -> trim(value / 1_000_000_000) + "B"
        value >= 1_000_000 -> trim(value / 1_000_000) + "M"
        value >= 1_000 -> trim(value / 1_000) + "K"
        else -> value.roundToInt().toString()
    }

    private fun trim(value: Double): String {
        val rounded = (value * 10).roundToInt() / 10.0
        return if (rounded == floor(rounded)) rounded.toInt().toString() else rounded.toString()
    }

    private fun drawMark(name: String, left: Float, top: Float, size: Float, color: Int, fallback: Int) {
        drawDrawable(upstreamToolAsset(name) ?: fallback, left, top, size, size, color)
    }

    private fun drawDrawable(resource: Int, left: Float, top: Float, drawWidth: Float, drawHeight: Float, tint: Int? = null) {
        val drawable = context.getDrawable(resource)?.mutate() ?: return
        if (tint != null) drawable.setTint(tint)
        drawable.isFilterBitmap = true
        // Drawable bounds are integers; draw in a scaled-up local space so small marks keep their exact size.
        val precision = 8f
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(1f / precision, 1f / precision)
        drawable.setBounds(0, 0, (drawWidth * precision).roundToInt(), (drawHeight * precision).roundToInt())
        drawable.draw(canvas)
        canvas.restore()
    }

    private fun drawBar(rect: RectF, fraction: Double, color: Int) {
        val radius = rect.height() / 2f
        fillRoundRect(rect, radius, strongLine)
        val fillRight = rect.left + rect.width() * fraction.coerceIn(0.0, 1.0).toFloat()
        if (fillRight > rect.left) fillRoundRect(RectF(rect.left, rect.top, fillRight.coerceAtLeast(rect.left + rect.height()), rect.bottom), radius, color)
    }

    private fun drawSegmentedBar(rect: RectF, segments: List<Pair<Double, Int>>) {
        val radius = rect.height() / 2f
        fillRoundRect(rect, radius, strongLine)
        canvas.save()
        canvas.clipPath(android.graphics.Path().apply { addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW) })
        var x = rect.left
        segments.forEach { (share, color) ->
            val segmentRight = min(rect.right, x + rect.width() * share.coerceIn(0.0, 1.0).toFloat())
            canvas.drawRect(x, rect.top, segmentRight, rect.bottom, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
            x = segmentRight
        }
        canvas.restore()
    }

    private fun fitText(value: String, x: Float, baseline: Float, type: DeckType, minimum: Float, color: Int, maxWidth: Float) {
        var size = type.size
        var current = DeckType(type.typeface, size, type.tracking, type.uppercase, type.tabular)
        while (size > minimum && measure(value, current) > maxWidth) {
            size -= 1f
            current = DeckType(type.typeface, size, type.tracking, type.uppercase, type.tabular)
        }
        text(value, x, baseline, current, color, maxWidth = maxWidth)
    }

    /** Draws [value] and returns its advance width in reference units. */
    private fun text(
        value: String,
        x: Float,
        baseline: Float,
        type: DeckType,
        color: Int,
        align: Paint.Align = Paint.Align.LEFT,
        maxWidth: Float = Float.MAX_VALUE,
    ): Float {
        val paint = paint(type, color, align)
        val shown = ellipsize(if (type.uppercase) value.uppercase(Locale.US) else value, paint, maxWidth)
        canvas.drawText(shown, x, baseline, paint)
        return paint.measureText(shown)
    }

    private fun measure(value: String, type: DeckType): Float = paint(type, ink).measureText(if (type.uppercase) value.uppercase(Locale.US) else value)

    private fun paint(type: DeckType, color: Int, align: Paint.Align = Paint.Align.LEFT) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textSize = type.size
        this.color = color
        typeface = type.typeface
        textAlign = align
        letterSpacing = type.tracking
        if (type.tabular) fontFeatureSettings = "tnum"
    }

    private fun ellipsize(value: String, paint: Paint, maxWidth: Float): String {
        if (maxWidth == Float.MAX_VALUE || paint.measureText(value) <= maxWidth) return value
        val suffix = "…"
        var end = value.length
        while (end > 0 && paint.measureText(value.substring(0, end) + suffix) > maxWidth) end--
        return value.substring(0, end) + suffix
    }

    private fun hline(startX: Float, endX: Float, y: Float, color: Int) {
        canvas.drawLine(startX, y, endX, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 1f })
    }

    private fun vline(x: Float, startY: Float, endY: Float, color: Int) {
        canvas.drawLine(x, startY, x, endY, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 1f })
    }

    private fun circle(x: Float, y: Float, radius: Float, color: Int) {
        canvas.drawCircle(x, y, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    private fun fillRoundRect(rect: RectF, radius: Float, color: Int) {
        canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    private fun strokeRoundRect(rect: RectF, radius: Float, color: Int, strokeWidth: Float) {
        canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
        })
    }
}
