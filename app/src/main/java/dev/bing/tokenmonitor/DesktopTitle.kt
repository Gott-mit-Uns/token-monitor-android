package dev.bing.tokenmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Original 16px title/4px indicator, with automatic icon fallback at narrow widths. */
@Composable internal fun DesktopTitle(iconOnly:Boolean,showDot:Boolean,live:Boolean,modifier:Modifier,onAverage:()->Unit={},onHome:()->Unit) {
    val colors=MaterialTheme.colorScheme;val strings=LocalResources.current;val density=LocalDensity.current
    val style=MaterialTheme.typography.bodySmall.copy(fontSize=16.sp,lineHeight=18.sp,fontWeight=FontWeight.Bold)
    val measured=rememberTextMeasurer().measure(AnnotatedString("Token Monitor"),style).size.width/density.density
    BoxWithConstraints(modifier.padding(end=12.dp)) {
        val collapsed=iconOnly || maxWidth.value<measured+(if(showDot) 9 else 0)
        Box(Modifier.heightIn(min=48.dp).widthIn(min=48.dp).testTag("hub-title-control")
            .combinedClickable(role=androidx.compose.ui.semantics.Role.Button,onClick=onHome,onLongClick=onAverage,onLongClickLabel=strings.getString(R.string.average_rate_title))
            .semantics {contentDescription=strings.getString(R.string.desktop_home)},contentAlignment=Alignment.CenterStart) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom) {
                Text(if(collapsed) "Σ" else "Token Monitor",style=style,color=colors.onSurface,modifier=Modifier.testTag(if(collapsed) "hub-title-icon" else "hub-title-name"))
                if(showDot) Box(Modifier.padding(start=5.dp,bottom=4.dp).size(4.dp).background(if(live) colors.primary else Color(0xFF5B6471),CircleShape).testTag("hub-live-dot").semantics {contentDescription=strings.getString(if(live) R.string.live_foreground else R.string.saved_snapshot)})
            }
        }
    }
}
