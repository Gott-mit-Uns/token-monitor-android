package dev.bing.tokenmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.math.floor

/** Dashboard-only displayColor; aggregation identity is not a label alias. */
internal fun dashboardColor(key:String,byModel:Boolean,palette:Map<String,Long>):Color {
    val base=if(byModel) modelColor(key,palette) else Color(palette[key] ?: palette.getValue("default"))
    val rgb=base.toArgb()
    val r=(rgb ushr 16) and 255;val g=(rgb ushr 8) and 255;val b=rgb and 255
    if(.2126*r+.7152*g+.0722*b>=42) return base
    fun lift(value:Int)=floor(value+(205-value)*.62+.5).toInt()
    return Color(lift(r),lift(g),lift(b))
}

@Composable internal fun DesktopDashboardSegment(
    options:List<Pair<String,String>>, selected:String, tag:String,
    onSelect:(String)->Unit, small:Boolean=false
) {
    val overlay=LocalDesktopOverlayColor.current
    val textScale=LocalDesktopTextScale.current
    Row(Modifier.drawBehind {
        val height=((if(small) 22.dp else 28.dp)*textScale).toPx().coerceAtMost(size.height)
        drawRoundRect(overlay.copy(alpha=.05f),Offset(0f,(size.height-height)/2),Size(size.width,height),CornerRadius(7.dp.toPx()))
    }.padding(2.dp),
        horizontalArrangement=Arrangement.spacedBy(2.dp)) {
        options.forEach { (id,label)->
            Box(Modifier.heightIn(min=48.dp).widthIn(min=48.dp).testTag("$tag-$id")
                .semantics {this.selected=id==selected}
                .clickable(role=Role.RadioButton) {if(id!=selected) onSelect(id)},
                contentAlignment=androidx.compose.ui.Alignment.Center) {
                Text(label,Modifier.background(if(id==selected) overlay.copy(alpha=.12f) else Color.Transparent,
                    RoundedCornerShape(5.dp)).padding(horizontal=if(small) 8.dp else 10.dp,vertical=if(small) 3.dp else 5.dp),
                    style=quotaTextStyle(if(small) 10 else 11),
                    color=MaterialTheme.colorScheme.onSurface.copy(alpha=if(id==selected) 1f else .6f))
            }
        }
    }
}

/** Percentages retain exact integer totals until the presentation boundary. */
internal fun dashboardPercent(value:BigInteger,total:BigInteger):String =
    if(total.signum()<=0) "0.0%" else BigDecimal(value).multiply(BigDecimal(100))
        .divide(BigDecimal(total),1,RoundingMode.HALF_UP).toPlainString()+"%"
