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
 * Routes the selected view to its screen and draws the total panel above every list.
 */
@Composable
internal fun DashboardContent(
    modifier: Modifier,
    state: HubRepositoryState,
    destination: DashboardDestination,
    period: DashboardPeriod,
    onChoose: (DashboardDestination) -> Unit,
    onRefresh: () -> Unit,
    homeReturnVisible: Boolean,
    serviceStatus: ServiceStatusState,
    displayOptions: DisplayOptions,
    onOpenServicePage: (String) -> Unit,
    modelFilter: String? = null,
    onClearModelFilter: () -> Unit = {},
    onOpenToolModels: (String) -> Unit = {},
    originalDeviceNames: Map<String, String> = emptyMap(),
    onRenameDevice: ((String, String) -> String?)? = null,
) {
    val snapshot = state.snapshot
    val showSessionTitles = LocalSessionTitles.current
    if (snapshot == null) {
        EmptyDashboard(modifier, state.message, { onChoose(DashboardDestination.Settings) })
        return
    }
    // Aggregating a rolling range walks the whole daily history; do it once per Hub event, not per recomposition.
    val usage = remember(snapshot, period) { period.usage(snapshot) }
    val homeActivity = remember(snapshot) {
        val history = homeActivityPoints(snapshot)
        history to buildActivityHeatmap(history)
    }
    BackHandler(destination == DashboardDestination.Models && modelFilter != null) {
        onChoose(DashboardDestination.Tools)
    }
    val filteredTool = modelFilter
    val modelUsage = filteredTool?.let { usage.modelsForTool(it) } ?: usage
    val listState = rememberSaveable(destination, modelFilter, saver = LazyListState.Saver) { LazyListState() }
    var search by rememberSaveable(destination) { mutableStateOf("") }
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = state.refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = Recessed,
                color = Accent,
            )
        },
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(if (destination in listOf(DashboardDestination.Models, DashboardDestination.Projects, DashboardDestination.Tools)) 4.dp else 10.dp),
    ) {
        item { TotalPanel(if (destination == DashboardDestination.Models) modelUsage else usage, destination == DashboardDestination.Home && displayOptions.compactTokenTotal, home = destination == DashboardDestination.Home) }
        if (destination == DashboardDestination.Models && filteredTool != null) item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("${filteredTool.displayName()} · Models"), color = Ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClearModelFilter) { Text(tr("ALL MODELS")) }
                }
                Text(tr("Usage through this tool in the selected period"), color = Muted, style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { onChoose(DashboardDestination.Tools) }) { Text(tr("← Back to Tools")) }
            }
        }
        if (state.message != null && !state.streamActive) item { StatusMessage(state.message, state.snapshot.stale) }
        if (destination != DashboardDestination.Home && homeReturnVisible && modelFilter == null) item { BackToHomeRow { onChoose(DashboardDestination.Home) } }
        if (destination == DashboardDestination.Sessions || destination == DashboardDestination.Projects) {
            item { CompactSearchField(search, { search = it }, if (destination == DashboardDestination.Sessions) {
                if (showSessionTitles) "Search title, ID, tool, model, or project" else "Search ID, tool, model, or project"
            } else "Search project, ID, or tool") }
        }
        when (destination) {
            DashboardDestination.Home -> homeItems(snapshot, usage, period, displayOptions, homeActivity.first, homeActivity.second, onChoose)
            DashboardDestination.Tools -> breakdownItems(
                tokens = usage.clients,
                costs = usage.clientCosts,
                unpriced = usage.clientUnpricedTokens,
                cacheReads = usage.clientCacheReads,
                outputs = usage.clientOutputs,
                unclassified = usage.clientUnclassifiedTokens,
                onToolSelected = onOpenToolModels,
                emptyMessage = "No tool activity is available for this period.",
                rankingMetric = displayOptions.rankingMetric,
            )
            DashboardDestination.Status -> statusItems(snapshot, state, serviceStatus, onOpenServicePage)
            DashboardDestination.Devices -> deviceItems(snapshot.stats.devices, period, originalDeviceNames, onRenameDevice)
            DashboardDestination.Models -> breakdownItems(
                tokens = modelUsage.models,
                costs = modelUsage.modelCosts,
                unpriced = modelUsage.modelUnpricedTokens,
                cacheReads = modelUsage.modelCacheReads,
                outputs = modelUsage.modelOutputs,
                unclassified = modelUsage.modelUnclassifiedTokens,
                emptyMessage = if (modelFilter != null) "No tool-to-model breakdown was supplied for this period." else "No model activity is available for this period.",
                rankingMetric = displayOptions.rankingMetric,
                modelRows = true,
            )
            DashboardDestination.Projects -> projectItems(usage.projects.filter { it.matchesSearch(search) }, period, search.isNotBlank())
            DashboardDestination.Sessions -> sessionItems(usage.sessions.filter { it.matchesSearch(search, showSessionTitles) }, period, search.isNotBlank())
            DashboardDestination.Limits -> limitItems(snapshot, displayOptions)
            DashboardDestination.Trends -> trendItems(snapshot)
            DashboardDestination.Settings -> Unit
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
    }
}

@Composable
internal fun TotalPanel(usage: UsagePeriod, compact: Boolean, home: Boolean = true) {
    val rollingTokens = rememberRollingValue(usage.totalTokens.toDouble())
    val tokens = if (rollingTokens == usage.totalTokens.toDouble()) usage.totalTokens else rollingTokens.roundToLong()
    val cost = rememberRollingValue(usage.costUsd)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(tr("TOTAL TOKENS"), color = Muted, style = MaterialTheme.typography.labelMedium)
        Text(
            formatTokenTotal(tokens, home, compact, LocalHomeChineseUnits.current),
            color = Ink,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 5.dp),
        )
        Text(formatUsageCost(cost, usage.unpricedTokens), color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
internal fun BackToHomeRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(R.drawable.action_arrow_left), contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
        Text(tr("Back to Home"), color = Muted, style = MaterialTheme.typography.labelMedium)
    }
}
