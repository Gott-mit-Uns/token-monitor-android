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
 * Devices list with expandable per-device detail.
 */
internal fun LazyListScope.deviceItems(devices: List<DeviceUsage>, period: DashboardPeriod, originalNames: Map<String, String> = emptyMap(), onRename: ((String, String) -> String?)? = null) {
    if (devices.isEmpty()) item { MutedCopy("No devices have checked in to this Hub.", modifier = Modifier.padding(vertical = 12.dp)) }
    else {
        val values = devices.associateWith { device -> period.usage(device).totalTokens }
        val maximum = values.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        items(devices, key = { it.id }) { device ->
            val usage = period.usage(device)
            val operatingSystem = listOf(device.osName.ifBlank { device.platform.displayName() }, device.osVersion)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            val syncCadence = device.syncUploadIntervalMs?.takeIf { it > 0 }?.let { "Uploads every ${formatDuration(it)}" } ?: "Live uploads"
            DeviceUsageRow(
                device = device,
                usage = usage,
                operatingSystem = operatingSystem,
                syncCadence = syncCadence,
                ratio = usage.totalTokens.toFloat() / maximum,
                originalName = originalNames[device.id] ?: device.hostname,
                onRename = onRename,
            )
        }
    }
}

@Composable
internal fun DeviceUsageRow(
    device: DeviceUsage,
    usage: UsagePeriod,
    operatingSystem: String,
    syncCadence: String,
    ratio: Float,
    originalName: String = device.hostname,
    onRename: ((String, String) -> String?)? = null,
) {
    var renaming by remember { mutableStateOf(false) }
    var aliasInput by remember { mutableStateOf("") }
    var renameError by remember { mutableStateOf<String?>(null) }
    if (renaming && onRename != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(tr("Device name")) },
            text = { Column {
                Text(tr("Hub original name: ${originalName.ifBlank { device.id }}"), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = aliasInput, onValueChange = { aliasInput = it; renameError = null }, singleLine = true, label = { Text(tr("Name on this Android device")) }, isError = renameError != null)
                renameError?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
                Text(tr("Only saved on this phone, up to 32 characters"), style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(onClick = { renameError = onRename(device.id, aliasInput); if (renameError == null) renaming = false }) { Text(tr("Save")) } },
            dismissButton = { Row {
                TextButton(onClick = { renameError = onRename(device.id, ""); if (renameError == null) renaming = false }) { Text(tr("Restore original name")) }
                TextButton(onClick = { renaming = false }) { Text(tr("Cancel")) }
            } },
        )
    }
    val expandable = usage.clients.isNotEmpty() || usage.models.isNotEmpty() || device.trackedClients.isNotEmpty()
    var expanded by rememberSaveable(device.id) { mutableStateOf(false) }
    val motionEnabled = LocalInteractionMotion.current
    val tone = if (device.stale) Muted else Blue
    Column(
        modifier = Modifier.fillMaxWidth().then(
            if (expandable) Modifier.clickable { expanded = !expanded } else Modifier,
        ).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DevicePlatformMark(device.platform, if (device.stale) Muted else Ink, size = 12.dp)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.hostname.ifBlank { device.id }, color = Ink, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tr(listOf(operatingSystem, if (device.stale) tr("Stale") else tr("Synced ${device.updatedAt.relativeAge(LocalNow.current)}".trimEnd())).filter { it.isNotBlank() }.joinToString(" · ")),
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onRename != null) TextButton(onClick = { aliasInput = if (device.hostname == originalName) "" else device.hostname; renameError = null; renaming = true }) { Text(tr("Rename device")) }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatTokens(usage.totalTokens), color = Ink, style = MaterialTheme.typography.bodySmall)
                Text(formatMoney(usage.costUsd), color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (device.hostname != originalName) Text(tr("Hub original name: ${originalName.ifBlank { device.id }}"), color = Muted, style = MaterialTheme.typography.labelSmall)
        UsageBar(ratio, tone)
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(if (motionEnabled) 160 else 0)) + expandVertically(tween(if (motionEnabled) 240 else 0, easing = DesktopEaseOut)),
            exit = fadeOut(tween(if (motionEnabled) 100 else 0)) + shrinkVertically(tween(if (motionEnabled) 180 else 0, easing = DesktopEaseOut)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                val total = usage.totalTokens.coerceAtLeast(1L)
                usage.clients.entries.sortedByDescending { it.value }.forEach { (client, tokens) ->
                    Row(modifier = Modifier.padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        UpstreamToolMark(client, accentFor(client), size = 8.dp)
                        Spacer(Modifier.width(7.dp))
                        Text(client.displayName(), color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(tr("${formatPercent(tokens.toDouble() / total * 100.0)}  ${formatTokens(tokens)}"), color = Ink, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (usage.models.isNotEmpty()) {
                    HorizontalDivider(color = Line, modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 4.dp))
                    Text(tr("TOP MODELS ON THIS DEVICE"), color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 20.dp))
                }
                usage.models.entries.sortedByDescending { it.value }.take(4).forEach { (model, tokens) ->
                    Row(modifier = Modifier.padding(start = 20.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        ModelMark(model, accentFor(model), size = 8.dp)
                        Spacer(Modifier.width(7.dp))
                        Text(model.displayName(), color = Ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatCompactTokens(tokens), color = Ink, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(tr(listOf(tr(syncCadence), if (device.historyAvailable == true) tr("History available") else null).filterNotNull().joinToString(" · ")),
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 20.dp, top = 2.dp),
                )
            }
        }
        HorizontalDivider(color = Line)
    }
}
