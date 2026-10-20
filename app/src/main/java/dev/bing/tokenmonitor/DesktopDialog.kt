package dev.bing.tokenmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Desktop card geometry: 12dp corners, panel surface, hairline edge, no tonal Material elevation. */
@Composable
internal fun DesktopAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(12.dp)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.border(1.dp, LocalDesktopTooltipBorder.current.copy(alpha = .14f), shape),
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = shape,
        containerColor = LocalDesktopPanelColor.current,
        tonalElevation = 0.dp,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Desktop pill: one selected fill, no Material outline, ripple clipped to the visible shape. */
@Composable
internal fun DesktopFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable () -> Unit
) {
    val overlay = LocalDesktopOverlayColor.current
    val shape = RoundedCornerShape(6.dp)
    ProvideTextStyle(quotaTextStyle(11)) {
        Box(
            modifier
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(if (selected) overlay.copy(alpha = .12f) else Color.Transparent, shape)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) { label() }
    }
}
