package dev.bing.tokenmonitor

import android.content.res.Resources
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import java.text.NumberFormat

@Composable internal fun ToolDetailSwitch(mode: String, change: (String) -> Unit) {
    val strings=LocalResources.current;val colors=MaterialTheme.colorScheme
    val overlay=LocalDesktopOverlayColor.current
    val border=LocalDesktopTooltipBorder.current
    Row(Modifier.width(104.dp).height(48.dp).drawBehind {
        val height=30.dp.toPx();val top=(size.height-height)/2
        val bounds=Rect(0f,top,size.width,top+height);val radius=CornerRadius(7.dp.toPx())
        clipPath(Path().apply {addRoundRect(RoundRect(bounds,radius))}) {
            drawRect(overlay.copy(alpha=.049f),Offset(0f,top),Size(size.width,height))
            drawRect(colors.primary.copy(alpha=.11f),Offset(if(mode=="models") size.width/2 else 0f,top),Size(size.width/2,height))
        }
        drawRoundRect(border.copy(alpha=.18f),Offset(.5.dp.toPx(),top+.5.dp.toPx()),Size(size.width-1.dp.toPx(),height-1.dp.toPx()),radius,style=Stroke(1.dp.toPx()))
        drawLine(border.copy(alpha=.14f),Offset(size.width/2,top),Offset(size.width/2,top+height),1.dp.toPx())
    }.testTag("tool-detail-switch"),verticalAlignment=Alignment.CenterVertically) {
        listOf("tokens" to strings.getString(R.string.tool_detail_tokens),"models" to strings.getString(R.string.dimension_models)).forEach { (key,label) ->
            Box(Modifier.weight(1f).height(48.dp).clickable(role=Role.Button) {change(key)}.testTag("tool-detail-mode-$key").semantics { selected=mode==key },contentAlignment=Alignment.Center) {
                Text(label,style=quotaTextStyle(10),color=if(mode==key) colors.onSurface else colors.onSurfaceVariant)
            }
        }
    }
}

/** Compact label/share/count alignment. Long values stack to remain complete. */
@Composable private fun DetailMetric(label: String, value: String, percent: String?, showPercent: Boolean, tag: String, modifier: Modifier=Modifier) {
    val colors=MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxWidth().testTag(tag)) {
        val stacked=effectiveTextScale()>1.15f || maxWidth<240.dp || value.length>15
        val scale=LocalDesktopTextScale.current
        val style=quotaTextStyle(11).copy(lineHeight=(11*1.35f*scale).sp)
        Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(label,Modifier.weight(1f,false),style=style,color=colors.onSurfaceVariant,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if(showPercent) Text(percent ?: "—",Modifier.testTag("$tag-percent"),style=quotaTextStyle(10).copy(fontFamily=LocalDesktopDataFont.current),color=colors.onSurfaceVariant.copy(alpha=0.6f))
                }
                if(!stacked) Text(value,Modifier.testTag("$tag-value"),style=style.copy(fontFamily=LocalDesktopDataFont.current),color=colors.onSurface.copy(alpha=.85f))
            }
            if(stacked) Text(value,Modifier.fillMaxWidth().testTag("$tag-value"),style=style.copy(fontFamily=LocalDesktopDataFont.current),color=colors.onSurface.copy(alpha=.85f),textAlign=androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

@Composable internal fun ToolTokenDetails(rows: List<ToolTokenDetail>, tag: String, strings: Resources=LocalResources.current) {
    val numbers=NumberFormat.getIntegerInstance(strings.configuration.locales[0])
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
        rows.forEach { row ->
            val label=strings.getString(when(row.key) { "hit" -> R.string.tool_token_hit;"miss" -> R.string.tool_token_miss;"output" -> R.string.token_output;else -> R.string.token_unclassified })
            DetailMetric(label,row.tokens?.let(numbers::format) ?: "—",row.percent,row.key in listOf("hit","miss"),"$tag-${row.key}")
        }
    }
}

@Composable internal fun ToolDetailBody(projection: ToolDetailProjection, mode: String, tag: String) {
    val strings=LocalResources.current;val numbers=NumberFormat.getIntegerInstance(strings.configuration.locales[0])
    var inspected by rememberSaveable { mutableStateOf<String?>(null) }
    fun identity(row: ToolModelDetail)=row.key+if(row.unattributed) ":remainder" else ":model"
    LaunchedEffect(projection.models.map(::identity)) {
        if(inspected!=null && projection.models.none { identity(it)==inspected }) inspected=null
    }
    val line=LocalDesktopOverlayColor.current.copy(alpha=.08f)
    Column(Modifier.fillMaxWidth().drawBehind {drawLine(line,Offset(4.dp.toPx(),0f),Offset(4.dp.toPx(),size.height),1.dp.toPx())}.padding(start=17.dp,top=5.dp,bottom=2.dp)) {
    if(mode=="models") Column(verticalArrangement=Arrangement.spacedBy(3.dp),modifier=Modifier.testTag("$tag-models")) {
        projection.models.forEach { row ->
            val label=if(row.unattributed) strings.getString(R.string.token_unclassified) else row.key
            val value=if((row.usage.tokens ?: 0)>0) numbers.format(row.usage.tokens) else usageCostText(row.usage.cost,row.usage.unpriced)
            DetailMetric(label,value,row.percent,(row.usage.tokens ?: 0)>0,"$tag-model-${identity(row)}",
                Modifier.heightIn(min=48.dp).clickable(role=Role.Button,onClickLabel=strings.getString(R.string.usage_summary)) { inspected=identity(row) }.padding(top=5.dp,bottom=2.dp))
        }
    } else ToolTokenDetails(projection.tokens,tag,strings)
    }
    projection.models.firstOrNull { identity(it)==inspected }?.let { row ->
        CompactUsageDialog(if(row.unattributed) strings.getString(R.string.token_unclassified) else row.key,"tool-model-inspection",{ inspected=null }) {
            Text((row.usage.tokens?.let(numbers::format) ?: "—")+" Token",style=MaterialTheme.typography.titleMedium)
            Text(strings.getString(R.string.known_cost_formatted,usageCostText(row.usage.cost,row.usage.unpriced)),Modifier.testTag("tool-model-inspection-cost"),style=MaterialTheme.typography.bodySmall)
            row.percent?.let { Text(it,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
