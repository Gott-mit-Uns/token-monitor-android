package dev.bing.tokenmonitor

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A single module entry, like homeModuleShell; nested chart/detail actions remain independent. */
@Composable internal fun DesktopHomeModule(title:String,icon:Int,meta:String?=null,headerTag:String?=null,bodyGap:Int=6,open:()->Unit,content:@Composable ColumnScope.()->Unit) {
    val colors=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().heightIn(min=48.dp).pointerInput(open) {detectTapGestures {open()}}
        .semantics {role=Role.Button;contentDescription=title;onClick {open();true}}
        .onKeyEvent {event->if(event.type==KeyEventType.KeyUp && event.key in listOf(Key.Enter,Key.NumPadEnter,Key.Spacebar)) {open();true} else false}.focusable(),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth().then(if(headerTag!=null) Modifier.testTag(headerTag) else Modifier),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(title.uppercase(java.util.Locale.ROOT),Modifier.weight(1f),style=quotaTextStyle(11,true),maxLines=1,overflow=TextOverflow.Ellipsis)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                meta?.let {Text(it,if(headerTag=="home-sessions-jump") Modifier.testTag("home-sessions-running") else Modifier,style=quotaTextStyle(10),color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)}
                Icon(painterResource(icon),null,Modifier.size(desktopScaledIconSize(13.dp)).testTag("home-heading-icon-$icon").alpha(.8f),tint=colors.onSurfaceVariant)
            }
        }
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(bodyGap.dp),content=content)
        Spacer(Modifier.height(5.dp)) // Seven-pixel grid gap plus five = original 12-pixel ending space.
    }
    HorizontalDivider(color=LocalDesktopTooltipBorder.current.copy(alpha=.12f))
}

@Composable internal fun DesktopHomeEmpty(text:String) {
    Box(Modifier.fillMaxWidth().heightIn(min=36.dp),contentAlignment=Alignment.CenterStart) {
        Text(text,style=quotaTextStyle(11),color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
