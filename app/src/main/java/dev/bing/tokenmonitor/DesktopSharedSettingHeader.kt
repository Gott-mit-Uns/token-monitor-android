package dev.bing.tokenmonitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Original cursor-settings-toggle/status pill, with Android's shared 48dp touch bound. */
@Composable internal fun DesktopSharedSettingHeader(title:String,status:String,tag:String,expanded:Boolean=false,enabled:Boolean=true,open:()->Unit) {
    val colors=MaterialTheme.colorScheme
    val state=LocalResources.current.getString(if(expanded) R.string.desktop_collapse else R.string.desktop_expand)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val statusWidth=maxWidth*.58f
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(enabled=enabled,role=Role.Button,onClick=open).testTag(tag).semantics {stateDescription=state}.alpha(if(enabled) 1f else .38f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,Modifier.weight(1f),style=quotaTextStyle(11).copy(lineHeight=(11*1.35f*LocalDesktopTextScale.current).sp),color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(status,Modifier.widthIn(max=statusWidth).border(1.dp,desktopEditLine(),RoundedCornerShape(999.dp)).background(LocalDesktopOverlayColor.current.copy(alpha=.045f),RoundedCornerShape(999.dp)).padding(horizontal=5.dp,vertical=1.dp).testTag("$tag-status"),style=quotaTextStyle(9).copy(lineHeight=(9*1.25f*LocalDesktopTextScale.current).sp),color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
            val chevron=colors.onSurfaceVariant
            Canvas(Modifier.size(12.dp)) {
                val mid=size.width/2;val rise=if(expanded) -1f else 1f
                val path=Path();path.moveTo(mid-2.5.dp.toPx(),size.height/2-rise*1.25.dp.toPx());path.lineTo(mid,size.height/2+rise*1.25.dp.toPx());path.lineTo(mid+2.5.dp.toPx(),size.height/2-rise*1.25.dp.toPx())
                drawPath(path,chevron,style=Stroke(1.5.dp.toPx()))
            }
        }
    }
}
