package dev.bing.tokenmonitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.floor

/** Desktop app.renderTrends: the main window preview opens the independent history view. */
@Composable internal fun DesktopTrendsMain(
    model: TrendsMainModel,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    availableHeight: Dp? = null,
    barMotion: State<TrendsBarFrame>? = null
) {
    val strings = LocalResources.current
    val text = MaterialTheme.colorScheme.onSurface
    val preferredChartHeight = CompactUiTokens.chartHeight()
    if (model.points.isEmpty()) {
        Box(Modifier.fillMaxWidth().then(modifier).heightIn(min = CompactUiTokens.ChartHeightSecondary)
            .padding(end = 2.dp).padding(vertical = CompactUiTokens.SectionSpacing).testTag("trends-main"), contentAlignment = Alignment.Center) {
            Text(strings.getString(R.string.trends_empty), style = quotaTextStyle(12), color = text.copy(alpha = .6f))
        }
        return
    }

    val locale = strings.configuration.locales[0]
    val integers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val display = LocalMainDisplay.current
    val textScale = LocalDesktopTextScale.current
    val chart = LocalDesktopChartBarColor.current
    val overlay = LocalDesktopOverlayColor.current
    val open = strings.getString(R.string.trends_open_history)
    val range = strings.getString(when (model.range) {
        TrendsMainRange.WEEK -> R.string.trends_range_week
        TrendsMainRange.MONTH -> R.string.trends_range_month
        TrendsMainRange.BY_MONTH -> R.string.trends_range_by_month
    })
    val first = TrendsMainProjection.shortLabel(model.points.first().label)
    val last = TrendsMainProjection.shortLabel(model.points.last().label)
    val description = strings.getString(R.string.trends_chart_description, first, last)
    val barTargets = remember(model.points) { TrendsBarMotion.targets(model.points) }
    val sparkGeometry = remember(model.points) {
        val max = model.points.maxOf { it.tokens }.coerceAtLeast(1).toDouble()
        val slot = 300.0 / model.points.size
        val width = slot * .7
        fun coordinate(value: Double) = (floor(value * 100 + .5) / 100).toFloat()
        model.points.mapIndexed { index, point ->
            val height = 120.0 * point.tokens / max
            TrendsSparkGeometry(coordinate(index * slot + (slot - width) / 2),
                coordinate(120.0 - height), coordinate(width), coordinate(height))
        }
    }
    val metrics = listOf(
        Triple("active-days", R.string.trends_active_days, integers.format(model.stats.activeDays)),
        Triple("streak", R.string.trends_current_streak, integers.format(model.stats.currentStreak)),
        Triple("time", R.string.trends_active_time, TrendsMainProjection.activeDuration(model.stats.activeTimeMs)),
        Triple("peak", R.string.trends_peak_day, DesktopCompactTokens.format(model.stats.peakDayTokens.toDouble(), display.compactUnits, DesktopCompactTokens.localeTag(locale)))
    )

    // The caller's viewport is not a chart growth target. Measure real paragraphs
    // independently so short screens scroll and large text keeps its natural height.
    Layout(modifier = Modifier.fillMaxWidth().then(modifier).padding(end = 2.dp).testTag("trends-main"), content = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(range.uppercase(Locale.ROOT), Modifier.weight(1f).testTag("trends-main-range"),
                style = quotaTextStyle(11).copy(letterSpacing = (.44f * textScale).sp), color = text.copy(alpha = .6f))
            Box(Modifier.sizeIn(minWidth = CompactUiTokens.InteractiveMinHeight, minHeight = CompactUiTokens.InteractiveMinHeight).testTag("trends-main-open-history")
                .semantics { contentDescription = open }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                    role = Role.Button, onClickLabel = open, onClick = onOpenHistory), contentAlignment = Alignment.Center) {
                Text("↗", style = quotaTextStyle(13), color = text.copy(alpha = .6f * .75f))
            }
        }
        Canvas(Modifier.fillMaxWidth().testTag("trends-main-chart")
            .semantics { contentDescription = description }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                role = Role.Button, onClickLabel = open, onClick = onOpenHistory)
            .padding(horizontal = 4.dp, vertical = 6.dp)) {
            val sx = size.width / 300f
            val sy = size.height / 120f
            // The sole per-frame read is in DrawScope, never in composition or measurement.
            val motion = barMotion?.value
            val scales = motion?.takeIf { it.targets == barTargets }?.scales
            model.points.forEachIndexed { index, point ->
                val geometry = sparkGeometry[index]
                val x = geometry.x * sx
                val barWidth = geometry.width * sx
                val y = geometry.y * sy
                val height = geometry.height * sy
                val lastBar = index == model.points.lastIndex
                if (point.tokens > 0) {
                    val scaleY = scales?.getOrNull(index) ?: 1f
                    if (scaleY > 0f) withTransform({
                        // CSS transform-box:fill-box/origin:bottom center also scales the rx vertically.
                        scale(1f, scaleY, pivot = Offset(x + barWidth / 2, y + height))
                    }) {
                        drawRoundRect(chart.copy(alpha = if (lastBar) 1f else .5f),
                            topLeft = Offset(x, y), size = Size(barWidth, height), cornerRadius = CornerRadius(sx, sy))
                    }
                } else if (model.showZeroMarkers) {
                    drawLine(chart.copy(alpha = if (lastBar) .32f else .24f),
                        start = Offset(x, 119.5f * sy), end = Offset(x + barWidth, 119.5f * sy), strokeWidth = 1.dp.toPx())
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(first, Modifier.weight(1f).testTag("trends-main-axis-start"),
                style = quotaDataTextStyle(10), color = text.copy(alpha = .45f))
            Text(last, Modifier.weight(1f).testTag("trends-main-axis-end"),
                style = quotaDataTextStyle(10), color = text.copy(alpha = .45f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        Column(verticalArrangement = Arrangement.spacedBy(CompactUiTokens.SectionSpacing)) {
            metrics.chunked(2).forEach { row -> TrendsMainStatRow(row, text, overlay) }
        }
    }) { children, constraints ->
        val natural = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val cap = children[0].measure(natural)
        val axis = children[2].measure(natural)
        val cards = children[3].measure(natural)
        val gap = CompactUiTokens.ChartTitleSpacing.roundToPx()
        val chartPadding = 12.dp.roundToPx()
        val fixedHeight = cap.height + axis.height + cards.height + gap * 3
        val chartHeight = maxOf(CompactUiTokens.InteractiveMinHeight.roundToPx() + chartPadding, preferredChartHeight.roundToPx())
        val spark = children[1].measure(natural.copy(minHeight = chartHeight, maxHeight = chartHeight))
        val height = fixedHeight + spark.height
        layout(constraints.maxWidth, constraints.constrainHeight(height)) {
            var y = 0
            listOf(cap, spark, axis, cards).forEachIndexed { index, child ->
                child.placeRelative(0, y)
                y += child.height + if (index < 3) gap else 0
            }
        }
    }
}

private data class TrendsSparkGeometry(val x: Float, val y: Float, val width: Float, val height: Float)

/** Measure real paragraphs once; fractional line heights must not be clamped by intrinsics. */
@Composable private fun TrendsMainStatRow(metrics: List<Triple<String, Int, String>>, text: Color, overlay: Color) {
    val strings = LocalResources.current
    Layout(modifier = Modifier.fillMaxWidth(), content = {
        metrics.forEach { (id, caption, value) ->
            Box(Modifier.testTag("trends-main-$id-card").background(overlay.copy(alpha = .04f), RoundedCornerShape(8.dp)))
            Text(value, Modifier.fillMaxWidth().testTag("trends-main-$id"),
                style = quotaTextStyle(16).copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), color = text)
            Text(strings.getString(caption), Modifier.fillMaxWidth().testTag("trends-main-$id-label"),
                style = quotaTextStyle(10), color = text.copy(alpha = .55f))
        }
    }) { children, constraints ->
        val gap = CompactUiTokens.SectionSpacing.roundToPx()
        val horizontalPadding = CompactUiTokens.CardHorizontalPadding.roundToPx()
        val verticalPadding = CompactUiTokens.CardContentPadding.roundToPx()
        val textGap = 2.dp.roundToPx()
        val firstWidth = (constraints.maxWidth - gap) / 2
        val widths = listOf(firstWidth, constraints.maxWidth - gap - firstWidth)
        val values = metrics.indices.map { index ->
            children[index * 3 + 1].measure(Constraints(maxWidth = (widths[index] - horizontalPadding * 2).coerceAtLeast(0)))
        }
        val captions = metrics.indices.map { index ->
            children[index * 3 + 2].measure(Constraints(maxWidth = (widths[index] - horizontalPadding * 2).coerceAtLeast(0)))
        }
        val height = metrics.indices.maxOf { verticalPadding * 2 + values[it].height + textGap + captions[it].height }
        val backgrounds = metrics.indices.map { index -> children[index * 3].measure(Constraints.fixed(widths[index], height)) }
        layout(constraints.maxWidth, height) {
            metrics.indices.forEach { index ->
                val x = if (index == 0) 0 else firstWidth + gap
                backgrounds[index].placeRelative(x, 0)
                values[index].placeRelative(x + horizontalPadding, verticalPadding)
                captions[index].placeRelative(x + horizontalPadding, verticalPadding + values[index].height + textGap)
            }
        }
    }
}
