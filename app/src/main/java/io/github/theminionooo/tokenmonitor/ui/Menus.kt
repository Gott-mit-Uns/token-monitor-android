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
 * Anchored popup menus used by the header and footer controls.
 */
/**
 * The desktop's floating menu: a glass panel that sits a few pixels off its anchor,
 * above the view switcher or below the period tabs, instead of the stock Android menu
 * that keeps a large margin from the screen edge.
 */
@Composable
internal fun AnchoredMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    above: Boolean,
    alignEnd: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    val provider = remember(above, alignEnd, gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                val x = (if (alignEnd) anchorBounds.right - popupContentSize.width else anchorBounds.left).coerceIn(0, maxX)
                val aboveY = anchorBounds.top - gap - popupContentSize.height
                val belowY = anchorBounds.bottom + gap
                val fitsBelow = belowY + popupContentSize.height <= windowSize.height
                val y = if (above) (if (aboveY >= 0) aboveY else belowY) else (if (fitsBelow) belowY else aboveY)
                return IntOffset(x, y.coerceAtLeast(0))
            }
        }
    }
    val motionEnabled = LocalInteractionMotion.current
    val reveal = remember { Animatable(if (motionEnabled) 0f else 1f) }
    LaunchedEffect(Unit) { if (motionEnabled) reveal.animateTo(1f, tween(140, easing = DesktopEaseOut)) }
    Popup(popupPositionProvider = provider, onDismissRequest = onDismissRequest, properties = PopupProperties(focusable = true)) {
        Surface(
            color = Shell,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, StrongLine),
            shadowElevation = 12.dp,
            modifier = Modifier
                .graphicsLayer {
                    alpha = reveal.value
                    scaleX = 0.96f + 0.04f * reveal.value
                    scaleY = scaleX
                    transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, if (above) 1f else 0f)
                }
                .widthIn(min = 154.dp)
                .width(IntrinsicSize.Max),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

@Composable
internal fun MenuRow(label: String, selected: Boolean, iconRes: Int? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(34.dp).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        iconRes?.let { Icon(painterResource(it), contentDescription = null, tint = if (selected) Accent else Muted, modifier = Modifier.size(15.dp)) }
        Text(tr(label), color = if (selected) Accent else Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
