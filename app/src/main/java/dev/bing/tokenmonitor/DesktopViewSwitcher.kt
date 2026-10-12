package dev.bing.tokenmonitor

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Original label tap cycles views; disclosure and label hold open the view menu. */
@Composable internal fun DesktopViewSwitcher(page:HubPage,next:HubPage,cycle:()->Unit,menu:()->Unit,expanded:Boolean=false,modifier:Modifier=Modifier) {
    val colors=MaterialTheme.colorScheme;val strings=LocalResources.current
    val background=LocalDesktopOverlayColor.current.copy(alpha=0.049f);val border=LocalDesktopTooltipBorder.current.copy(alpha=0.18f)
    val title=strings.getString(page.title)
    val titleWidth=with(LocalDensity.current) {rememberDesktopLabelWidths(listOf(title),quotaTextStyle(11)).single().toDp()}
    BoxWithConstraints(modifier.widthIn(max=150.dp)) {
    val compact=titleWidth+84.dp>maxWidth
    Row(Modifier.width(if(compact) 96.dp else titleWidth+84.dp).height(48.dp).testTag(if(compact) "hub-view-compact" else "hub-view-regular").drawBehind {
        val height=30.dp.toPx();val y=(size.height-height)/2;val radius=CornerRadius(7.dp.toPx())
        drawRoundRect(background,Offset(0f,y),Size(size.width-24.dp.toPx(),height),radius)
        drawRoundRect(border,Offset(.5.dp.toPx(),y+.5.dp.toPx()),Size(size.width-25.dp.toPx(),height-1.dp.toPx()),radius,style=Stroke(1.dp.toPx()))
        val divider=size.width-48.dp.toPx();drawLine(border,Offset(divider,y),Offset(divider,y+height),1.dp.toPx())
    },verticalAlignment=Alignment.CenterVertically) {
        Row(Modifier.weight(1f,false).height(48.dp).widthIn(min=48.dp).combinedClickable(role=Role.Button,onClick=cycle,onLongClick=menu)
            .padding(horizontal=8.dp).testTag("hub-view-current").semantics {selected=true;contentDescription=title+", "+strings.getString(R.string.main_next_view,strings.getString(next.title))},
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Icon(painterResource(page.icon),null,Modifier.size(14.dp),tint=colors.onSurfaceVariant)
            if(!compact) Text(title,style=quotaTextStyle(11),color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        Box(Modifier.size(48.dp).combinedClickable(role=Role.Button,onClick=menu,onLongClick=menu).testTag("hub-view-selector").semantics {contentDescription=strings.getString(page.title);stateDescription=strings.getString(if(expanded) R.string.desktop_collapse else R.string.desktop_expand)},contentAlignment=Alignment.Center) {
            Canvas(Modifier.offset(x=(-12).dp).size(10.dp)) {
                val side=6.dp.toPx()/kotlin.math.sqrt(2f)
                val center=size.width/2;val middle=size.height/2
                val direction=if(expanded) -1 else 1
                val shape=Path().apply {moveTo(center-side,middle-direction*side/2);lineTo(center,middle+direction*side/2);lineTo(center+side,middle-direction*side/2)}
                drawPath(shape,colors.onSurfaceVariant,style=Stroke(1.5.dp.toPx(),cap=StrokeCap.Butt))
            }
        }
    }
    }
}
