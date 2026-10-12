package dev.bing.tokenmonitor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Shared compact desktop-style inspection window, with accessible touch controls. */
@Composable internal fun CompactUsageDialog(title: String, tag: String, onClose: () -> Unit, desktopTooltip:Boolean=false, content: @Composable ColumnScope.() -> Unit) {
    val strings = LocalResources.current;val colors=MaterialTheme.colorScheme
    val close=strings.getString(R.string.close)
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.widthIn(max=360.dp).fillMaxWidth(0.88f).testTag(tag),shape=RoundedCornerShape(if(desktopTooltip) 7.dp else 8.dp),color=if(desktopTooltip) colors.background else colors.surfaceContainer,
            border=BorderStroke(1.dp,if(desktopTooltip) LocalDesktopTooltipBorder.current else colors.outlineVariant.copy(alpha=0.6f))) {
            Column((if(desktopTooltip) Modifier.background(Brush.verticalGradient(LocalDesktopTooltipColors.current)) else Modifier).padding(horizontal=if(desktopTooltip) 7.dp else 12.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(title,Modifier.weight(1f).testTag("$tag-title"),style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
                    DesktopTextButton(onClick=onClose,modifier=Modifier.heightIn(min=48.dp)) { Text(close,style=MaterialTheme.typography.labelSmall) }
                }
                content()
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
