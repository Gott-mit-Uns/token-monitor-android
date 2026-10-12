package dev.bing.tokenmonitor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal val LocalBackgroundImageModel = staticCompositionLocalOf<BackgroundImageModel?> { null }
internal val LocalBackgroundImageState = staticCompositionLocalOf { BackgroundImageState(busy = false) }

@Composable internal fun DesktopBackgroundSurface(content: @Composable BoxScope.() -> Unit) {
    val state = LocalBackgroundImageState.current
    val color = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize().background(color)) {
        state.bitmap?.let { bitmap ->
            Box(Modifier.matchParentSize().graphicsLayer { alpha = state.opacity / 100f }.clearAndSetSemantics { }.testTag("background-image-layer")) {
                Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.Center)
                Box(Modifier.matchParentSize().background(color.copy(alpha = .18f)))
            }
        }
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun BackgroundImageControl() {
    val strings = LocalResources.current
    val model = LocalBackgroundImageModel.current
    val state = LocalBackgroundImageState.current
    val colors = MaterialTheme.colorScheme
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> model?.select(uri) }
    Column(Modifier.fillMaxWidth().testTag("appearance-background-controls"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Column(Modifier.heightIn(min = 48.dp).testTag("appearance-background-description"), verticalArrangement = Arrangement.Center) {
                Text(strings.getString(R.string.background_image_title), style = quotaTextStyle(11))
                Text(strings.getString(when { state.error -> R.string.background_image_error; state.busy -> R.string.background_image_loading; state.bitmap != null -> R.string.background_image_active; else -> R.string.background_image_none }),
                    Modifier.testTag("appearance-background-status"), style = quotaTextStyle(9), color = if (state.error) colors.error else colors.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BackgroundImageButton(strings.getString(R.string.background_image_choose), "appearance-background-choose", model != null && !state.busy) { picker.launch(arrayOf("image/*")) }
                if (state.bitmap != null) BackgroundImageButton(strings.getString(R.string.background_image_remove), "appearance-background-remove", !state.busy) { model?.clear() }
            }
        }
        if (state.bitmap != null) {
            HorizontalDivider(color = LocalDesktopLineColor.current.copy(alpha = .06f), thickness = 1.dp)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.getString(R.string.background_image_opacity), style = quotaTextStyle(11))
                    DesktopEditAction("↺", strings.getString(R.string.background_image_reset), "appearance-background-reset", visualSize = 18, glyphSize = 10, enabled = !state.busy && state.opacity != 28) { model?.previewOpacity(28); model?.saveOpacity() }
                }
                Box(Modifier.fillMaxWidth(.42f).heightIn(min = 48.dp).testTag("appearance-background-opacity").clearAndSetSemantics {
                    contentDescription = strings.getString(R.string.background_image_opacity)
                    progressBarRangeInfo = ProgressBarRangeInfo(state.opacity.toFloat(), 0f..100f, 99)
                    if (model == null || state.busy) disabled()
                    setProgress { value ->
                        if (model == null || state.busy) false else {
                            model.previewOpacity(value.roundToInt()); model.saveOpacity(); true
                        }
                    }
                }, contentAlignment = Alignment.Center) {
                    Slider(state.opacity.toFloat(), { model?.previewOpacity(it.roundToInt()) }, Modifier.fillMaxWidth(), enabled = model != null && !state.busy,
                        valueRange = 0f..100f, steps = 99, onValueChangeFinished = { model?.saveOpacity() },
                        thumb = { Box(Modifier.size(13.dp).background(colors.primary, CircleShape)) },
                        track = {
                            Canvas(Modifier.fillMaxWidth().height(20.dp)) {
                                // Slider measures its rail between thumb centres; extend to the input edges.
                                val halfThumb = 6.5.dp.toPx()
                                val thickness = 2.dp.toPx()
                                drawRoundRect(Brush.horizontalGradient(listOf(colors.primary.copy(alpha = .22f), colors.primary), -halfThumb, size.width + halfThumb),
                                    topLeft = Offset(-halfThumb, (size.height - thickness) / 2),
                                    size = Size(size.width + halfThumb * 2, thickness), cornerRadius = CornerRadius(thickness / 2))
                            }
                        })
                }
                Box(Modifier.heightIn(min = 48.dp).widthIn(min = 24.dp), contentAlignment = Alignment.CenterEnd) {
                    Text("${state.opacity}%", Modifier.testTag("appearance-background-percent"), style = quotaTextStyle(10), color = colors.onSurfaceVariant, textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable private fun BackgroundImageButton(label: String, tag: String, enabled: Boolean, action: () -> Unit) {
    DesktopActionButton(action, Modifier.testTag(tag), enabled = enabled, role = DesktopActionRole.BACKGROUND, visualTag = "$tag-visual") {
        Text(label, style = quotaTextStyle(11))
    }
}
