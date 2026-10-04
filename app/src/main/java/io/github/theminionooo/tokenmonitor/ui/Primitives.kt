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
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.data.storage.LimitBarMetric
import io.github.theminionooo.tokenmonitor.data.storage.RankingMetric
import io.github.theminionooo.tokenmonitor.data.storage.ReduceMotionMode
import io.github.theminionooo.tokenmonitor.data.storage.TextScale
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.delay

/*
 * Small building blocks: status blocks and lines, muted copy, usage bars, the trend line, and the empty state.
 */
@Composable
internal fun DesktopStatusBlock(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(tr(title.uppercase(Locale.US)), color = Ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        content()
        HorizontalDivider(color = Line)
    }
}

@Composable
internal fun StatusLine(label: String, value: String, color: Color = Ink) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr(label), color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.45f), maxLines = 1)
        Text(value, color = color, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.55f))
    }
}

@Composable
internal fun StatusMessage(message: String, stale: Boolean) {
    Text(tr(message), color = if (stale) Orange else Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 3.dp))
}

@Composable
internal fun MutedCopy(text: String, modifier: Modifier = Modifier) {
    Text(tr(text), color = Muted, style = MaterialTheme.typography.bodySmall, modifier = modifier, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun UsageBar(ratio: Float, color: Color) {
    val motionEnabled = LocalInteractionMotion.current
    val animatedRatio by animateFloatAsState(
        targetValue = ratio.coerceIn(0f, 1f),
        animationSpec = tween(if (motionEnabled) 360 else 0, easing = DesktopEaseOut),
        label = "usage bar",
    )
    val palette = LocalPalette.current
    Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(palette.sunken.copy(alpha = 0.55f), cornerRadius = radius)
        drawRoundRect(color, size = Size(size.width * animatedRatio.coerceIn(0.02f, 1f), size.height), cornerRadius = radius)
    }
}

@Composable
internal fun TrendChart(values: List<Long>, height: androidx.compose.ui.unit.Dp) {
    val reveal = rememberChartReveal(values.size)
    val palette = LocalPalette.current
    Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas
        val maximum = max(values.maxOrNull()?.toFloat() ?: 1f, 1f) / reveal.coerceAtLeast(0.001f)
        val step = if (values.size == 1) 0f else size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * step
            val y = size.height - ((value.toFloat() / maximum) * (size.height - 12.dp.toPx())) - 6.dp.toPx()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawLine(palette.line, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), strokeWidth = 1.dp.toPx())
        drawPath(path, palette.blue, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
internal fun EmptyDashboard(modifier: Modifier, message: String?, onOpenSettings: () -> Unit) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.Start) {
        Text(tr("Token Monitor"), color = Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(tr("Connect a private Hub to see desktop activity here."), color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 7.dp))
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenSettings) { Text(tr("OPEN CONNECTION SETTINGS")) }
        message?.let { StatusMessage(it, stale = true) }
    }
}
