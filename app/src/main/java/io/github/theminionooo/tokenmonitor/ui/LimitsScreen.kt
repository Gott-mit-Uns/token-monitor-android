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
 * Limits and Subscriptions lists.
 */
internal fun LazyListScope.limitItems(snapshot: HubSnapshot, displayOptions: DisplayOptions) {
    val deviceNames = snapshot.stats.devices.associate { it.id to it.hostname }
    item { LimitAttentionPanel(snapshot) }
    if (snapshot.stats.limits.providers.isEmpty()) item { MutedCopy("No account limits are available from this Hub yet.", modifier = Modifier.padding(vertical = 12.dp)) }
    else {
        val providers = prioritizeAvailableLimits(snapshot.stats.limits.providers)
        items(providers, key = { "${it.provider}:${it.accountName}:${it.accountEmail}" }) { account ->
            LimitAccountRow(account, displayOptions, deviceNames)
        }
    }
    if (snapshot.subscriptions.entries.isNotEmpty()) {
        item { Text(tr("SUBSCRIPTIONS"), color = Ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
        items(snapshot.subscriptions.entries, key = { it.id }) { subscription -> SubscriptionRow(subscription) }
    }
}

/** The desktop's wording for a provider row that has no quota window to draw. */
internal fun limitStatusLabel(status: String): String = when (status) {
    "notConfigured" -> "Not signed in"
    "unauthorized" -> "Sign in again"
    "disabled" -> "Disabled"
    "noSyncedData" -> "No synced data"
    "rateLimited", "sourceRateLimited" -> "Limited"
    "unavailable", "error" -> "Unavailable"
    "ok" -> "No quota reported"
    else -> "Not available"
}

internal fun prioritizeAvailableLimits(providers: List<LimitAccount>): List<LimitAccount> = providers.withIndex()
    .sortedWith(compareBy<IndexedValue<LimitAccount>> { it.value.windows.isEmpty() }.thenBy { account -> account.value.windows.mapNotNull { it.remainingPercent ?: it.usedPercent?.let { used -> 100.0 - used } }.minOrNull() ?: 101.0 }.thenBy { it.index })
    .map { it.value }

@Composable
internal fun LimitAccountRow(account: LimitAccount, displayOptions: DisplayOptions, deviceNames: Map<String, String> = emptyMap()) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UpstreamToolMark(account.provider, Blue, size = LocalContentIconSize.current)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(if (account.provider.isBlank()) tr("Provider") else account.provider.providerLabel(), color = Ink, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val email = account.accountEmail.takeIf { displayOptions.showAccountEmails }.orEmpty()
                val source = account.sourceDeviceId.takeIf { displayOptions.showLimitSource && it.isNotBlank() }?.let { tr("Source ${deviceNames[it] ?: it.displayName()}") }
                val updated = account.updatedAt.relativeAge(LocalNow.current).takeIf { it.isNotBlank() }?.let { tr("Updated $it") }
                val meta = listOf(account.accountName, account.plan, email, source, updated).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (account.windows.isEmpty()) {
                Spacer(Modifier.width(10.dp))
                Text(tr(limitStatusLabel(account.status)), color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
        if (account.windows.isNotEmpty()) {
            account.windows.chunked(2).forEach { windows ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    windows.forEach { window ->
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            val remaining = window.remainingPercent ?: window.usedPercent?.let { 100.0 - it }
                            val usedPercent = window.usedPercent ?: window.remainingPercent?.let { 100.0 - it }
                            Row {
                                Text(windowTitle(window, account.windows), color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), maxLines = 1)
                                val display = (if (displayOptions.limitBarMetric == LimitBarMetric.Remaining) {
                                    remaining?.let { "${formatPercent(it)} left" }
                                } else {
                                    usedPercent?.let { "${formatPercent(it)} used" }
                                })
                                    ?: window.remaining?.let { formatWindowAmount(it, window.currency) }
                                    ?: "Available"
                                Text(tr(display), color = Ink, style = MaterialTheme.typography.labelSmall)
                            }
                            val meterPercent = if (displayOptions.limitBarMetric == LimitBarMetric.Remaining) remaining else usedPercent
                            val meter = (meterPercent ?: 0.0).coerceIn(0.0, 100.0).toFloat() / 100f
                            val risk = ((usedPercent ?: 0.0).coerceIn(0.0, 100.0) / 100.0).toFloat()
                            if (window.showMeter != false && meterPercent != null) UsageBar(meter, quotaColor(risk))
                            if (window.detail.isNotBlank()) Text(window.detail, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            formatBoundary(window.resetsAt, window.boundaryKind, LocalNow.current).takeIf { it.isNotBlank() }?.let { Text(it, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                        }
                    }
                    if (windows.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        HorizontalDivider(color = Line)
    }
}

@Composable
internal fun SubscriptionRow(subscription: Subscription) {
    DesktopDetailRow(
        label = subscription.provider.providerLabel(),
        subtitle = subscription.planName.ifBlank { tr("Subscription") },
        value = formatSubscriptionAmount(subscription.amountMinor, subscription.currency),
        detail = subscription.interval.displayName(),
        color = Purple,
        upstreamName = subscription.provider,
    )
}
