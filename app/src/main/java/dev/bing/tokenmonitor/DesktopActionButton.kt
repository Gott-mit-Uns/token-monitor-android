package dev.bing.tokenmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

internal enum class DesktopActionRole { SETTINGS, BACKGROUND, CONNECTION_EDIT }

/** Renderer action chrome is independent of the Android touch area and native dialog actions. */
@Composable internal fun DesktopActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: DesktopActionRole = DesktopActionRole.SETTINGS,
    transparent: Boolean = false,
    visualTag: String? = null,
    content: @Composable RowScope.() -> Unit
) {
    val inline = role == DesktopActionRole.CONNECTION_EDIT
    val background = role == DesktopActionRole.BACKGROUND
    val radius = if (inline) 4.dp else if (background) 6.dp else 8.dp
    val height = if (inline) 24.dp else if (background) 26.dp else 30.dp
    val shape = RoundedCornerShape(radius)
    val opacity = if (enabled) 1f else if (background) .5f else .38f
    val chrome = !inline
    val line = desktopEditLine()
    val overlay = LocalDesktopOverlayColor.current
    val text = if (inline) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Box(modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Row(Modifier.graphicsLayer { alpha = opacity }.heightIn(min = height)
            .then(if (visualTag == null) Modifier else Modifier.testTag(visualTag))
            .border(1.dp, if (chrome) line else Color.Transparent, shape)
            .background(if (chrome && !transparent) overlay.copy(alpha = DesktopEditTokens.control) else Color.Transparent, shape)
            .drawWithContent {
                drawContent()
                if (role == DesktopActionRole.SETTINGS && size.width > radius.toPx() * 2) {
                    drawLine(overlay.copy(alpha = .08f), Offset(radius.toPx(), 1.dp.toPx()),
                        Offset(size.width - radius.toPx(), 1.dp.toPx()), 1.dp.toPx())
                }
            }
            .padding(horizontal = if (inline) 6.dp else if (background) 7.dp else 10.dp,
                vertical = if (inline) 3.dp else if (background) 4.dp else 0.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            CompositionLocalProvider(LocalContentColor provides text, LocalTextStyle provides quotaTextStyle(if (inline) 10 else 11)) {
                content()
            }
        }
    }
}
