package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.localization.tr
import io.github.theminionooo.tokenmonitor.localization.localizedText
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.theminionooo.tokenmonitor.BuildConfig
import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.data.HubRepositoryState
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.data.storage.LimitBarMetric
import io.github.theminionooo.tokenmonitor.data.storage.RankingMetric
import io.github.theminionooo.tokenmonitor.domain.DeviceUsage
import io.github.theminionooo.tokenmonitor.domain.HistoryPoint
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.domain.LimitAccount
import io.github.theminionooo.tokenmonitor.domain.ProjectUsage
import io.github.theminionooo.tokenmonitor.domain.SessionUsage
import io.github.theminionooo.tokenmonitor.domain.ServiceHealth
import io.github.theminionooo.tokenmonitor.domain.ServiceProviderStatus
import io.github.theminionooo.tokenmonitor.domain.Subscription
import io.github.theminionooo.tokenmonitor.domain.UsagePeriod
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

/*
 * The tappable activity heatmap shared by Home and Trends.
 */
/**
 * The desktop contribution grid. Where the desktop shows a day on hover, a tap here
 * outlines the cell and prints its date, tokens, and cost beneath the grid; tapping
 * it again clears it. Horizontal scrolling of the grid is untouched.
 */
@Composable
internal fun ActivityHeatmapGrid(activity: ActivityHeatmap, history: List<HistoryPoint>, home: Boolean = false) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val palette = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    val motionEnabled = LocalInteractionMotion.current
    val byDate = remember(history) { history.associateBy { it.label.take(10) } }
    val today = LocalDate.now()
    val selectable = activity.cells.filter { !it.date.isAfter(today) }
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectable.firstOrNull { it.date.toString() == selectedDate } ?: selectable.lastOrNull()
    val previousDay = selectable.lastOrNull { selected != null && it.date < selected.date }
    val nextDay = selectable.firstOrNull { selected != null && it.date > selected.date }
    var detailDate by rememberSaveable { mutableStateOf<String?>(null) }
    val monthPaint = remember(density, palette) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.muted.copy(alpha = 0.5f).toArgb()
            textSize = with(density) { 9.dp.toPx() }
            typeface = Typeface.MONOSPACE
        }
    }
    LaunchedEffect(activity.weeks) { scroll.scrollTo(scroll.maxValue) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.fillMaxWidth().horizontalScroll(scroll)) {
            Canvas(
                modifier = Modifier
                    .width((activity.weeks * 12 - 3).coerceAtLeast(1).dp)
                    .height(97.dp)
                    .semantics {
                        contentDescription = localizedText("Activity heatmap. Open daily details or use the day navigation below.")
                        onClick("Open latest recorded day") {
                            detailDate = history.filter { it.label.take(10) <= today.toString() }.maxByOrNull { it.label }?.label?.take(10)
                            detailDate != null
                        }
                    }
                    .pointerInput(activity, today, selectedDate) {
                        detectTapGestures { offset ->
                            val pitch = 12.dp.toPx()
                            val column = (offset.x / pitch).toInt()
                            val row = (offset.y / pitch).toInt()
                            val hit = selectable.firstOrNull { it.column == column && it.row == row }
                            if (hit != null && hit != selected) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            if (hit != null) selectedDate = hit.date.toString()
                        }
                    },
            ) {
                val cell = 9.dp.toPx()
                val gap = 3.dp.toPx()
                val pitch = cell + gap
                val gridHeight = 7 * pitch - gap
                activity.cells.forEach { heatCell ->
                    drawRoundRect(
                        color = palette.heat[heatCell.intensity.coerceIn(0, 4)],
                        topLeft = Offset(heatCell.column * pitch, heatCell.row * pitch),
                        size = Size(cell, cell),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
                selected?.let { picked ->
                    val inset = 1.dp.toPx()
                    drawRoundRect(
                        color = palette.ink,
                        topLeft = Offset(picked.column * pitch - inset, picked.row * pitch - inset),
                        size = Size(cell + 2 * inset, cell + 2 * inset),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
                drawIntoCanvas { canvas ->
                    activity.monthLabels.forEach { label ->
                        val month = label.date.month.getDisplayName(DateTextStyle.SHORT, uiLocale())
                        canvas.nativeCanvas.drawText(month, label.column * pitch, gridHeight + 12.dp.toPx(), monthPaint)
                    }
                }
            }
        }
        AnimatedContent(
            targetState = selected,
            transitionSpec = { fadeIn(tween(if (motionEnabled) 160 else 0)) togetherWith fadeOut(tween(if (motionEnabled) 100 else 0)) },
            label = "heatmap day",
        ) { picked ->
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp)) {
                if (picked != null) {
                    val point = byDate[picked.date.toString()]
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(picked.date.format(dayFormat), color = Muted, style = MaterialTheme.typography.labelSmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(tr(point?.let { "${if (home) formatHomeTokens(it.tokens, LocalHomeChineseUnits.current) else formatTokens(it.tokens)} tokens" } ?: "No recorded usage"), color = Ink, style = MaterialTheme.typography.bodySmall)
                            if (point != null) Text(formatMoney(point.costUsd), color = Ink, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { selectedDate = previousDay?.date?.toString() }, enabled = previousDay != null, modifier = Modifier.weight(1f)) { Text(tr("PREVIOUS"), style = MaterialTheme.typography.labelMedium) }
            TextButton(onClick = { detailDate = selected?.date?.toString() }, enabled = selected != null, modifier = Modifier.weight(1f)) { Text(tr("DETAILS"), style = MaterialTheme.typography.labelMedium) }
            TextButton(onClick = { selectedDate = nextDay?.date?.toString() }, enabled = nextDay != null, modifier = Modifier.weight(1f)) { Text(tr("NEXT DAY"), style = MaterialTheme.typography.labelMedium) }
        }
        detailDate?.let { DayUsageDialog(it, history) { detailDate = null } }

    }
}
