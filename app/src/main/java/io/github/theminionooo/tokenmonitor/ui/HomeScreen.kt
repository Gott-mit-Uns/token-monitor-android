package io.github.theminionooo.tokenmonitor.ui

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
 * The Home view: desktop-style usage, limits, sessions, devices and activity modules.
 */
internal fun LazyListScope.homeItems(
    snapshot: HubSnapshot,
    usage: UsagePeriod,
    period: DashboardPeriod,
    displayOptions: DisplayOptions,
    history: List<HistoryPoint>,
    activity: ActivityHeatmap,
    onChoose: (DashboardDestination) -> Unit,
) {
    displayOptions.visibleHomeModules.forEach { module ->
        when (module) {
            "Limits" -> item { DesktopModule("LIMITS", DashboardDestination.Limits, onChoose) { HomeLimits(snapshot.stats.limits.providers, displayOptions) } }
            "Tools" -> item { DesktopModule("TOOLS", DashboardDestination.Tools, onChoose) { HomeBreakdown(usage.clients, usage.clientCosts, displayOptions.rankingMetric) } }
            "Devices" -> item { DesktopModule("DEVICES", DashboardDestination.Devices, onChoose) { HomeDevices(snapshot.stats.devices, period = period, aggregateUsage = usage) } }
            "Models" -> item { DesktopModule("MODELS", DashboardDestination.Models, onChoose) { HomeBreakdown(usage.models, usage.modelCosts, displayOptions.rankingMetric, modelRows = true) } }
            "Sessions" -> item { HomeSessionsModule(snapshot, onChoose) }
            "Activity" -> item {
                DesktopModule(
                    title = "ACTIVITY",
                    destination = DashboardDestination.Trends,
                    onChoose = onChoose,
                    meta = "${activity.activeDays} active day${if (activity.activeDays == 1) "" else "s"}",
                ) {
                    HomeActivity(activity, history)
                }
            }
        }
    }
}

/** A Home module header mirrors the desktop: title, optional meta, and the icon of the view it opens. */
@Composable
internal fun DesktopModule(
    title: String,
    destination: DashboardDestination,
    onChoose: (DashboardDestination) -> Unit,
    meta: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open ${destination.title}") { onChoose(destination) }
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            meta?.let { Text(it, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            Spacer(Modifier.width(7.dp))
            Icon(painterResource(destination.iconRes()), contentDescription = null, tint = Muted, modifier = Modifier.size(13.dp))
        }
        content()
        HorizontalDivider(color = Line)
    }
}

@Composable
internal fun HomeLimits(accounts: List<LimitAccount>, displayOptions: DisplayOptions) {
    val visible = accounts
        .filter { account -> account.windows.any { it.remainingPercent != null || it.usedPercent != null || it.remaining != null } }
        .sortedBy { account -> account.windows.mapNotNull { it.remainingPercent }.minOrNull() ?: 100.0 }
        .take(3)
    if (visible.isEmpty()) {
        MutedCopy("No account limits available")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        visible.forEach { account ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                UpstreamToolMark(account.provider, Blue, size = 6.dp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(account.provider.ifBlank { "Provider" }.providerLabel(), color = Ink, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val email = account.accountEmail.takeIf { displayOptions.showAccountEmails }.orEmpty()
                    val meta = listOf(account.accountName, account.plan, email).filter { it.isNotBlank() }.joinToString(" · ")
                    if (meta.isNotBlank()) Text(meta, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (account.windows.isEmpty()) {
                MutedCopy("No quota windows reported", modifier = Modifier.padding(start = 14.dp))
            } else {
                Row(modifier = Modifier.padding(start = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    account.windows.take(2).forEach { window ->
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(windowTitle(window, account.windows), color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val remainingPercent = window.remainingPercent ?: window.usedPercent?.let { 100.0 - it.coerceIn(0.0, 100.0) }
                                val amount = remainingPercent?.let { "${formatPercent(it)} left" }
                                    ?: window.remaining?.let { formatWindowAmount(it, window.currency) }
                                    ?: "—"
                                // Like the desktop Home module, a nearly spent window is flagged in place.
                                val risk = remainingPercent?.let { (1.0 - it / 100.0).toFloat() } ?: 0f
                                val tone = quotaColor(risk)
                                if (tone != Success) {
                                    StatusDot(tone, size = 5.dp)
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(amount, color = if (tone != Success) tone else Ink, style = MaterialTheme.typography.labelSmall)
                            }
                            formatBoundary(window.resetsAt, window.boundaryKind, LocalNow.current).takeIf { it.isNotBlank() }?.let { Text(it, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HomeBreakdown(
    tokens: Map<String, Long>,
    costs: Map<String, Double>,
    rankingMetric: RankingMetric,
    modelRows: Boolean = false,
) {
    if (tokens.isEmpty()) {
        MutedCopy("No activity for this period")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val total = tokens.values.sum().coerceAtLeast(1L)
        tokens.entries.sortedByDescending { (name, count) -> if (rankingMetric == RankingMetric.Cost) costs[name] ?: 0.0 else count.toDouble() }.take(5).forEach { (name, count) ->
            HomeListRow(
                name = name,
                primary = "${formatCompactTokens(count)}  ${formatPercent(count.toDouble() / total * 100.0)}",
                secondary = costs[name]?.let(::formatMoney).orEmpty(),
                upstreamName = name,
                modelRow = modelRows,
            )
        }
    }
}

@Composable
internal fun HomeDevices(devices: List<DeviceUsage>, period: DashboardPeriod, aggregateUsage: UsagePeriod) {
    if (devices.isEmpty()) {
        MutedCopy("No devices have checked in")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        devices.take(3).forEach { device ->
            val deviceUsage = period.usage(device)
            val deviceTokens = deviceUsage.totalTokens.takeIf { it > 0 }
                ?: if (devices.size == 1) aggregateUsage.totalTokens else 0L
            HomeListRow(
                name = device.hostname.ifBlank { device.id },
                primary = if (device.stale) "Stale" else formatCompactTokens(deviceTokens),
                secondary = listOf(device.osName, if (device.stale) "Stale" else "Synced").filter { it.isNotBlank() }.joinToString(" · "),
                color = if (device.stale) Muted else Blue,
            )
        }
    }
}

@Composable
internal fun HomeListRow(
    name: String,
    primary: String,
    secondary: String,
    color: Color = Blue,
    upstreamName: String? = null,
    modelRow: Boolean = false,
    literalName: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        when {
            upstreamName == null -> StatusDot(color, size = 8.dp)
            modelRow -> ModelMark(upstreamName, color, size = 8.dp)
            else -> UpstreamToolMark(upstreamName, color, size = 8.dp)
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(if (literalName) name else name.displayName(), color = Ink, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (secondary.isNotBlank()) Text(secondary, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(primary, color = Ink, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

internal fun homeActivityPoints(snapshot: HubSnapshot): List<HistoryPoint> = io.github.theminionooo.tokenmonitor.domain.usageHistory(snapshot)

@Composable
internal fun HomeActivity(activity: ActivityHeatmap, history: List<HistoryPoint>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ActivityHeatmapGrid(activity, history)
        val recent = history.takeLast(45)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TREND", color = Ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("Peak ${formatCompactTokens(history.maxOfOrNull { it.tokens } ?: 0L)}", color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        TrendChart(recent.map { it.tokens }, height = 70.dp)
        if (recent.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(recent.first(), recent[recent.lastIndex / 2], recent.last()).forEach { point ->
                    Text(shortDate(point.label), color = Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
