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
 * Projects and Sessions lists.
 */
internal fun LazyListScope.projectItems(projects: List<ProjectUsage>, period: DashboardPeriod) {
    if (projects.isEmpty()) item { MutedCopy(if (period in DashboardPeriod.rangeChoices) "The Hub reports projects for Day, Month, and Total only." else "No project attribution is available for ${period.label.lowercase(Locale.US)}.", modifier = Modifier.padding(vertical = 12.dp)) }
    else {
        val maximum = projects.maxOfOrNull { it.totalTokens }?.coerceAtLeast(1L) ?: 1L
        items(projects.sortedByDescending { it.totalTokens }, key = { it.id }) { project ->
            ProjectUsageRow(project, project.totalTokens.toFloat() / maximum)
        }
    }
}

@Composable
internal fun ProjectUsageRow(project: ProjectUsage, ratio: Float) {
    val expandable = project.clients.isNotEmpty()
    var expanded by rememberSaveable(project.id) { mutableStateOf(false) }
    val motionEnabled = LocalInteractionMotion.current
    Column(
        modifier = Modifier.fillMaxWidth().then(
            if (expandable) Modifier.clickable { expanded = !expanded } else Modifier,
        ).padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(Purple, size = 10.dp)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(project.label.ifBlank { project.id }, color = Ink, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = when {
                    project.clients.isNotEmpty() -> project.clients.keys.joinToString { it.displayName() }
                    project.sessionCount > 0 -> "${project.sessionCount} sessions"
                    else -> "Project total"
                }
                Text(meta, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatTokens(project.totalTokens), color = Ink, style = MaterialTheme.typography.bodySmall)
                Text(formatMoney(project.costUsd), color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        UsageBar(ratio, Purple)
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(if (motionEnabled) 160 else 0)) + expandVertically(tween(if (motionEnabled) 240 else 0, easing = DesktopEaseOut)),
            exit = fadeOut(tween(if (motionEnabled) 100 else 0)) + shrinkVertically(tween(if (motionEnabled) 180 else 0, easing = DesktopEaseOut)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                project.clients.entries.sortedByDescending { it.value }.forEach { (client, tokens) ->
                    Row(modifier = Modifier.padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        UpstreamToolMark(client, accentFor(client), size = 8.dp)
                        Spacer(Modifier.width(7.dp))
                        Text(client.displayName(), color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(formatTokens(tokens), color = Ink, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        HorizontalDivider(color = Line)
    }
}

internal fun LazyListScope.sessionItems(sessions: List<SessionUsage>, period: DashboardPeriod) {
    if (sessions.isEmpty()) item { MutedCopy(if (period in DashboardPeriod.rangeChoices) "The Hub reports sessions for Day, Month, and Total only." else "No session detail is available for ${period.label.lowercase(Locale.US)}.", modifier = Modifier.padding(vertical = 12.dp)) }
    else {
        val maximum = sessions.maxOfOrNull { it.totalTokens }?.coerceAtLeast(1L) ?: 1L
        items(sessions.sortedByDescending { it.totalTokens }, key = { "${it.client}:${it.id}" }) { session ->
            val labels = sessionRowLabels(session, LocalSessionTitles.current)
            SessionUsageRow(session, labels.title, labels.meta, session.totalTokens.toFloat() / maximum)
        }
    }
}

@Composable
internal fun SessionUsageRow(session: SessionUsage, title: String, meta: String, ratio: Float) {
    var expanded by rememberSaveable(session.client, session.id) { mutableStateOf(false) }
    val motionEnabled = LocalInteractionMotion.current
    val now = LocalNow.current
    val activity = sessionActivityState(session, now)
    val activityLabel = when (activity) {
        SessionActivityState.Running -> "Running"
        SessionActivityState.Finished -> "Finished"
        SessionActivityState.Idle -> ""
    }
    val activityColor = if (activity == SessionActivityState.Running) Success else Muted
    val context = sessionContextForRow(session, now)
    Column(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UpstreamToolMark(session.client, accentFor(session.client), size = 12.dp)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Ink, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (activityLabel.isNotBlank()) {
                        StatusDot(activityColor, size = 6.dp)
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        listOf(activityLabel, meta).filter { it.isNotBlank() }.joinToString(" · "),
                        color = Muted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatTokens(session.totalTokens), color = Ink, style = MaterialTheme.typography.bodySmall)
                Text(formatMoney(session.costUsd), color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse session details" else "Expand session details",
                tint = Muted,
                modifier = Modifier.size(16.dp).rotate(rememberChevronRotation(expanded)),
            )
        }
        UsageBar(ratio, accentFor(session.client))
        sessionMetricLabels(session, now).takeIf { it.isNotBlank() }?.let {
            Text(it, color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 20.dp))
        }
        context?.let { reading ->
            val contextColor = when {
                reading.percentLeft <= 10 -> Danger
                reading.percentLeft <= 30 -> Orange
                else -> Muted
            }
            Row(modifier = Modifier.fillMaxWidth().padding(start = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Context ${reading.percentUsed}% used", color = Muted, style = MaterialTheme.typography.labelSmall)
                Text("${reading.percentLeft}% left", color = contextColor, style = MaterialTheme.typography.labelSmall)
            }
            UsageBar(reading.percentUsed / 100f, contextColor)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(if (motionEnabled) 160 else 0)) + expandVertically(tween(if (motionEnabled) 240 else 0, easing = DesktopEaseOut)),
            exit = fadeOut(tween(if (motionEnabled) 100 else 0)) + shrinkVertically(tween(if (motionEnabled) 180 else 0, easing = DesktopEaseOut)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (session.projectLabel.isNotBlank()) DetailLine("Project", session.projectLabel)
                if (session.startedAt.isNotBlank()) DetailLine("Started", session.startedAt.shortTime())
                if (session.modelNames.isNotEmpty()) DetailLine("Models", session.modelNames.joinToString { it.displayName() })
                DetailLine("Session", session.id)
                Text(
                    "Prompt and reply text stays on the desktop and is not synchronized by the v${BuildConfig.UPSTREAM_VERSION} Hub.",
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 20.dp, top = 2.dp),
                    lineHeight = 15.sp,
                )
            }
        }
        HorizontalDivider(color = Line)
    }
}
