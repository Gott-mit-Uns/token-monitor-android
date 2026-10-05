package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.localization.tr
import io.github.theminionooo.tokenmonitor.localization.localizedText
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun ChoiceGroup(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TactileSegmentedControl(options = options, selected = selected, modifier = modifier, onSelect = onSelect)
}

/** Desktop-style segmented control with a sliding indicator and light touch feedback. */
@Composable
internal fun <T> TactileSegmentedControl(
    options: List<Pair<String, T>>,
    selected: T,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    val motionEnabled = LocalInteractionMotion.current
    val haptic = LocalHapticFeedback.current
    val selectedIndex = options.indexOfFirst { it.second == selected }.coerceAtLeast(0)
    val indicatorPosition by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(if (motionEnabled) 220 else 0, easing = DesktopEaseOut),
        label = "segmented indicator",
    )
    Surface(
        color = Color.White.copy(alpha = 0.026f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Ink.copy(alpha = 0.09f)),
        modifier = modifier.height(36.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            val gap = 2.dp
            val segmentWidth = (maxWidth - gap * (options.size - 1)) / options.size
            Surface(
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, Ink.copy(alpha = 0.13f)),
                modifier = Modifier.offset(x = (segmentWidth + gap) * indicatorPosition).width(segmentWidth).fillMaxHeight(),
            ) {}
            Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxSize().selectableGroup()) {
                options.forEach { (label, value) ->
                    val active = value == selected
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .selectable(selected = active, role = Role.Tab, interactionSource = interactionSource, indication = null) {
                                if (!active) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(value)
                            }
                            .alpha(if (pressed && !active) 0.6f else 1f),
                    ) {
                        Text(tr(label),
                            color = if (active) Accent else Muted,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
