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
 * Provider service status rows.
 */
internal fun LazyListScope.statusItems(
    @Suppress("UNUSED_PARAMETER") snapshot: HubSnapshot,
    @Suppress("UNUSED_PARAMETER") state: HubRepositoryState,
    serviceStatus: ServiceStatusState,
    onOpenServicePage: (String) -> Unit,
) {
    if (serviceStatus.loading && serviceStatus.snapshot == null) {
        item { MutedCopy("Checking Claude, OpenAI, Cursor, and DeepSeek…", modifier = Modifier.padding(vertical = 8.dp)) }
    }
    serviceStatus.snapshot?.providers?.let { providers ->
        items(providers, key = { it.id }) { provider ->
            ServiceStatusRow(provider, onOpenServicePage)
        }
    }
}

@Composable
internal fun ServiceStatusRow(provider: ServiceProviderStatus, onOpenServicePage: (String) -> Unit) {
    val tone = when (provider.health) {
        ServiceHealth.Ok -> Success
        ServiceHealth.Degraded -> Yellow
        ServiceHealth.Outage -> Danger
        ServiceHealth.Unknown -> Muted
    }
    val label = when (provider.health) {
        ServiceHealth.Ok -> "Operational"
        ServiceHealth.Degraded -> "Degraded"
        ServiceHealth.Outage -> "Outage"
        ServiceHealth.Unknown -> "Unknown"
    }
    val details = buildList {
        if (provider.affectedComponents > 0) add(localizedText("${provider.affectedComponents} affected"))
        if (provider.incidentCount > 0) add(localizedText("${provider.incidentCount} incident${if (provider.incidentCount == 1) "" else "s"}"))
        if (provider.maintenanceCount > 0) add(localizedText("${provider.maintenanceCount} maintenance"))
        if (isEmpty() && provider.health == ServiceHealth.Ok) add(localizedText("No ongoing issues"))
        add(formatRelativeAge(provider.checkedAt, LocalNow.current))
    }.joinToString(" · ")
    Column(
        modifier = Modifier.fillMaxWidth().clickable { onOpenServicePage(provider.pageUrl) }.padding(bottom = 11.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UpstreamToolMark(provider.id, tone, size = LocalContentIconSize.current)
            Spacer(Modifier.width(7.dp))
            Text(provider.label, color = Ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Surface(color = Color.Transparent, border = BorderStroke(1.dp, tone.copy(alpha = 0.4f)), shape = MaterialTheme.shapes.small) {
                Text(tr(label), color = tone, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
            }
        }
        Text(provider.description, color = Ink.copy(alpha = 0.88f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(details, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        HorizontalDivider(color = Line)
    }
}
