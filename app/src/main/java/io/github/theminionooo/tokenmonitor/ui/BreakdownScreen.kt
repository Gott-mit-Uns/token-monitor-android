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
 * Tools and Models lists with proportional bars and token component details.
 */
internal fun LazyListScope.breakdownItems(
    tokens: Map<String, Long>,
    costs: Map<String, Double>,
    cacheReads: Map<String, Long>,
    outputs: Map<String, Long>,
    unclassified: Map<String, Long>,
    emptyMessage: String,
    rankingMetric: RankingMetric,
    modelRows: Boolean = false,
    onToolSelected: ((String) -> Unit)? = null,
) {
    if (tokens.isEmpty()) {
        item { MutedCopy(emptyMessage, modifier = Modifier.padding(vertical = 12.dp)) }
    } else {
        val maximum = tokens.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        items(tokens.entries.sortedByDescending { (name, count) -> if (rankingMetric == RankingMetric.Cost) costs[name] ?: 0.0 else count.toDouble() }, key = { it.key }) { (name, count) ->
            DesktopUsageRow(
                name = name,
                totalTokens = count,
                detail = costs[name]?.let(::formatMoney).orEmpty(),
                ratio = count.toFloat() / maximum,
                cacheReadTokens = cacheReads[name] ?: 0L,
                outputTokens = outputs[name] ?: 0L,
                unclassifiedTokens = unclassified[name] ?: 0L,
                modelRow = modelRows,
                onToolSelected = onToolSelected,
            )
        }
    }
}

@Composable
internal fun DesktopUsageRow(
    name: String,
    totalTokens: Long,
    detail: String,
    ratio: Float,
    cacheReadTokens: Long,
    outputTokens: Long,
    unclassifiedTokens: Long,
    modelRow: Boolean,
    onToolSelected: ((String) -> Unit)? = null,
) {
    val hasBreakdown = cacheReadTokens > 0 || outputTokens > 0 || unclassifiedTokens > 0
    var expanded by rememberSaveable(name) { mutableStateOf(false) }
    val motionEnabled = LocalInteractionMotion.current
    val rowModifier = if (onToolSelected != null) {
        Modifier.fillMaxWidth().clickable(onClickLabel = localizedText("Models used through $name")) { onToolSelected(name) }.padding(vertical = 2.dp)
    } else if (hasBreakdown) {
        Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 2.dp)
    } else {
        Modifier.fillMaxWidth().padding(vertical = 2.dp)
    }
    Column(modifier = rowModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (modelRow) ModelMark(name, accentFor(name), size = LocalContentIconSize.current)
            else UpstreamToolMark(name, accentFor(name), size = LocalContentIconSize.current)
            Spacer(Modifier.width(8.dp))
            Text(name.displayName(), color = Ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatTokens(totalTokens), color = Ink, style = MaterialTheme.typography.bodySmall)
                if (detail.isNotBlank()) Text(tr(detail), color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        UsageBar(ratio, accentFor(name))
        AnimatedVisibility(
            visible = expanded && hasBreakdown,
            enter = fadeIn(tween(if (motionEnabled) 160 else 0)) + expandVertically(tween(if (motionEnabled) 240 else 0, easing = DesktopEaseOut)),
            exit = fadeOut(tween(if (motionEnabled) 100 else 0)) + shrinkVertically(tween(if (motionEnabled) 180 else 0, easing = DesktopEaseOut)),
        ) {
            TokenComponentBreakdown(
                totalTokens = totalTokens,
                cacheReadTokens = cacheReadTokens,
                outputTokens = outputTokens,
                unclassifiedTokens = unclassifiedTokens,
            )
        }
        HorizontalDivider(color = Line)
    }
}

@Composable
internal fun TokenComponentBreakdown(
    totalTokens: Long,
    cacheReadTokens: Long,
    outputTokens: Long,
    unclassifiedTokens: Long,
) {
    val unclassified = unclassifiedTokens.coerceIn(0L, totalTokens)
    val classified = totalTokens - unclassified
    val cacheRead = cacheReadTokens.coerceIn(0L, classified)
    val output = outputTokens.coerceIn(0L, classified - cacheRead)
    val cacheMiss = (classified - cacheRead - output).coerceAtLeast(0L)
    val input = cacheRead + cacheMiss
    val hit = if (input > 0) cacheRead.toDouble() / input * 100.0 else 0.0
    val miss = if (input > 0) 100.0 - hit else 0.0
    val rows = buildList {
        add(Triple("Input (Cache Hit)", cacheRead, hit))
        add(Triple("Input (Cache Miss)", cacheMiss, miss))
        add(Triple("Output", output, null))
        if (unclassified > 0) add(Triple("Unclassified", unclassified, null))
    }
    Column(modifier = Modifier.padding(start = LocalContentIconSize.current + 8.dp, end = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rows.forEach { (label, value, percent) ->
            Row {
                Text(tr(label), color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                percent?.let { Text(formatPercent(it), color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 12.dp)) }
                Text(formatTokens(value), color = Ink, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
