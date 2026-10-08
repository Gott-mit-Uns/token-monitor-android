package io.github.theminionooo.tokenmonitor.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal val LocalSettingsScroll = staticCompositionLocalOf<ScrollState?> { null }

/** A deliberate long press on the handle; regular content taps and scrolling stay untouched. */
@Composable
internal fun DragOrderHandle(name: String, enabled: Boolean = true, rowHeight: Float? = null, onMove: (Int) -> Unit) {
    val move by rememberUpdatedState(onMove)
    val scroll = LocalSettingsScroll.current
    val density = LocalDensity.current
    val row = rowHeight ?: with(density) { 48.dp.toPx() }
    val edge = with(density) { 80.dp.toPx() }
    val screen = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    var dragging by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var delta by remember { mutableFloatStateOf(0f) }
    var startScroll by remember { mutableIntStateOf(0) }
    LaunchedEffect(dragging, scroll) {
        while (dragging && scroll != null) {
            val distance = when { pointerY < edge -> -12f; pointerY > screen - edge -> 12f; else -> 0f }
            if (distance != 0f) scroll.scrollBy(distance)
            delay(16)
        }
    }
    Icon(Icons.Outlined.DragHandle,
        contentDescription = null, tint = if (enabled) Muted else Muted.copy(alpha = .35f),
        modifier = Modifier.size(48.dp).onGloballyPositioned { origin = it.positionInRoot() }
            .semantics { contentDescription = desktopText("拖动排序：$name", "Drag to reorder: $name") }
            .pointerInput(name, enabled, row) {
                if (enabled) detectDragGesturesAfterLongPress(
                    onDragStart = { position -> dragging = true; delta = 0f; startScroll = scroll?.value ?: 0; pointerY = origin.y + position.y },
                    onDrag = { change, amount -> change.consume(); delta += amount.y; pointerY += amount.y },
                    onDragEnd = {
                        val shift = ((delta + (scroll?.value ?: 0) - startScroll) / row).roundToInt()
                        dragging = false; delta = 0f
                        if (shift != 0) move(shift)
                    },
                    onDragCancel = { dragging = false; delta = 0f },
                )
            }.padding(12.dp))
}
