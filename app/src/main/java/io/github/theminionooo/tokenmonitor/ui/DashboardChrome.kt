package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.localization.tr
import io.github.theminionooo.tokenmonitor.localization.localizedText
import android.app.Activity
import android.provider.Settings
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.theminionooo.tokenmonitor.R
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.data.HubRepositoryState
import io.github.theminionooo.tokenmonitor.data.network.HubAddressValidator
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.data.storage.LimitBarMetric
import io.github.theminionooo.tokenmonitor.data.storage.RankingMetric
import io.github.theminionooo.tokenmonitor.data.storage.ReduceMotionMode
import io.github.theminionooo.tokenmonitor.data.storage.TextScale
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.delay

/*
 * The desktop-style header, period tabs, footer, and view switcher around the dashboard.
 */
@Composable
internal fun DesktopHeader(
    state: HubRepositoryState,
    settingsOpen: Boolean,
    showLiveIndicator: Boolean,
    period: DashboardPeriod,
    onPeriodChange: (DashboardPeriod) -> Unit,
    onGoHome: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Σ"), color = Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (!settingsOpen && showLiveIndicator) {
                    Spacer(Modifier.width(6.dp))
                    StatusDot(if (state.streamActive) Success else if (state.snapshot?.stale == true) Orange else Muted, size = 5.dp)
                }
            }
            val status = when {
                settingsOpen -> "Hub connection"
                state.snapshot?.stale == true -> "Saved snapshot · ${formatCapturedAt(state.snapshot.capturedAt)}"
                // Name the route that is answering, so Tailscale gets a label the way home Wi-Fi does.
                state.streamActive -> HubAddressValidator.routeLabel(state.activeUrl ?: state.connectionUrl, state.fallbackUrl)
                state.refreshing -> "Refreshing"
                state.message != null -> state.message
                else -> null
            }
            if (status != null) Text(tr(status), color = if (state.snapshot?.stale == true) Orange else Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (settingsOpen) {
            IconButton(onClick = onGoHome, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.action_arrow_left), contentDescription = localizedText("Back to Home"), tint = Muted, modifier = Modifier.size(18.dp))
            }
        } else {
            DesktopPeriodTabs(period = period, onPeriodChange = onPeriodChange)
        }
    }
}

@Composable
internal fun DesktopPeriodTabs(period: DashboardPeriod, onPeriodChange: (DashboardPeriod) -> Unit) {
    var rangesOpen by remember { mutableStateOf(false) }
    val middle = if (period in DashboardPeriod.rangeChoices) period else DashboardPeriod.Month
    Box {
        TactileSegmentedControl(
            options = listOf(
                DashboardPeriod.Today.label to DashboardPeriod.Today,
                middle.label to middle,
                DashboardPeriod.AllTime.label to DashboardPeriod.AllTime,
            ),
            selected = period,
            modifier = Modifier.width(190.dp),
            onSelect = { option ->
                if (option == middle) rangesOpen = true else onPeriodChange(option)
            },
        )
        AnchoredMenu(expanded = rangesOpen, onDismissRequest = { rangesOpen = false }, above = false, alignEnd = true) {
            DashboardPeriod.rangeChoices.forEach { option ->
                MenuRow(label = periodRangeLabel(option), selected = option == period) {
                    rangesOpen = false
                    onPeriodChange(option)
                }
            }
        }
    }
}

@Composable
internal fun DesktopFooter(
    destination: DashboardDestination,
    state: HubRepositoryState,
    displayOptions: DisplayOptions,
    onChoose: (DashboardDestination) -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DesktopViewSwitcher(destination = destination, displayOptions = displayOptions, onChoose = onChoose)
        Spacer(Modifier.weight(1f))
        val tracker = remember(state.connectionUrl) { LiveTokenRateTracker() }
        var sample by remember(state.connectionUrl) { mutableStateOf<LiveRateSample?>(null) }
        var burnMode by remember { mutableStateOf(false) }
        val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
        androidx.compose.runtime.DisposableEffect(lifecycle, tracker) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { tracker.reset(); sample = null }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer); tracker.reset() }
        }
        LaunchedEffect(state.snapshot) {
            val snapshot = state.snapshot
            sample = if (snapshot == null || snapshot.fromCache || snapshot.stale) { tracker.reset(); null }
                else tracker.observe(snapshot.today, System.currentTimeMillis())
        }
        val now = LocalNow.current
        val visibleSample = sample?.takeIf { now - it.sampledAt < 180_000 }
        if (state.streamActive || state.widgetLiveActive) TextButton(onClick = { burnMode = !burnMode }, modifier = Modifier.height(48.dp)) {
            Text(visibleSample?.let { String.format(java.util.Locale.US, "%.1f", if (burnMode) it.burn else it.speed) }?.plus(if (burnMode) "/m" else "/s") ?: "— /s", color = if (visibleSample != null && now - visibleSample.sampledAt < 8_000) Accent else Muted, style = MaterialTheme.typography.labelSmall)
        }
        if (!state.streamActive || state.refreshing) {
            IconButton(onClick = onRefresh, enabled = !state.refreshing, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Refresh, contentDescription = localizedText("Refresh"), tint = if (state.refreshing) Muted.copy(alpha = 0.5f) else Muted, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(6.dp))
        }
        Surface(
            color = Overlay,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, Line),
            modifier = Modifier.size(48.dp),
        ) {
            IconButton(onClick = { onChoose(DashboardDestination.Settings) }) {
                Icon(painterResource(R.drawable.action_settings), contentDescription = localizedText("Settings"), tint = Ink, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
internal fun DesktopViewSwitcher(
    destination: DashboardDestination,
    displayOptions: DisplayOptions,
    onChoose: (DashboardDestination) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box {
        Surface(
            color = if (expanded) Accent.copy(alpha = 0.08f) else Overlay,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, if (expanded) Accent.copy(alpha = 0.24f) else StrongLine),
            modifier = Modifier.height(48.dp).width(154.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxHeight().clip(MaterialTheme.shapes.small).clickable { expanded = !expanded }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(destination.iconRes()), contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr(destination.title), color = if (expanded) Ink else Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(1.dp).fillMaxHeight().background(if (expanded) Accent.copy(alpha = 0.24f) else StrongLine))
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = localizedText("Choose view"), tint = Muted, modifier = Modifier.size(16.dp))
                }
            }
        }
        AnchoredMenu(expanded = expanded, onDismissRequest = { expanded = false }, above = true) {
            displayOptions.visibleViews.mapNotNull { title -> DashboardDestination.entries.firstOrNull { it.title == title } }.forEach { option ->
                MenuRow(label = option.title, selected = option == destination, iconRes = option.iconRes()) {
                    expanded = false
                    if (option != destination) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onChoose(option)
                }
            }
        }
    }
}
