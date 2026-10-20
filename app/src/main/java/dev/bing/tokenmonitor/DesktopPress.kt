package dev.bing.tokenmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * A desktop-style press state for flat rows: a short overlay tint instead of a
 * Material splash, so the hairline surfaces keep their calm hierarchy.
 */
internal fun Modifier.desktopPressable(
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(8.dp),
    role: Role = Role.Button,
    onClickLabel: String? = null,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val overlay = LocalDesktopOverlayColor.current
    this
        .clip(shape)
        .background(if (pressed && enabled) overlay.copy(alpha = .045f) else Color.Transparent, shape)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick
        )
}
