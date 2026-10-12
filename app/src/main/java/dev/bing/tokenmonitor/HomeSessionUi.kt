package dev.bing.tokenmonitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Home's original two-line grid: tool, activity glyph, title, compact total, metadata. */
@Composable internal fun HomeSessionRow(row:UsageRow,now:Long,hideTitles:Boolean,names:ModelNames?=null,observed:Collection<String> = SessionDisplay.models(row).keys,open:()->Unit) {
    val strings=LocalResources.current;val colors=MaterialTheme.colorScheme;val main=LocalMainDisplay.current
    val locale=DesktopCompactTokens.localeTag(strings.configuration.locales[0]);val overlay=LocalDesktopOverlayColor.current
    val state=SessionDisplay.activity(row,now);val models=SessionDisplay.displayModels(row,names,observed)
    val modelLabel=when(models.size) {0->"";1->models.keys.single();else->"${models.size} models"}
    val age=SessionDisplay.age(row,now)?.let {(unit,count)->strings.getString(when(unit) {
        "minutes"->R.string.session_ago_minutes;"hours"->R.string.session_ago_hours;"days"->R.string.session_ago_days;else->R.string.session_ago_now
    },count)}.orEmpty()
    val context=SessionDisplay.context(row,now);val cache=SessionDisplay.cache(row,now)
    var showModels by rememberSaveable(row.id) {mutableStateOf(false)}
    LaunchedEffect(models.size) {if(models.size<2) showModels=false}
    var showContext by rememberSaveable(row.id) {mutableStateOf(false)}
    val contextLabel=strings.getString(R.string.session_context_details)
    Column(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(onClick=open).padding(vertical=5.dp).testTag("home-session-${row.id}"),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            BrandMark(row.tool.orEmpty(),size=10.dp,modifier=Modifier.padding(top=1.dp))
            SessionActivityMark(state,Modifier.padding(top=1.dp),SessionDisplay.waitingForReply(row,now))
            Text(if(hideTitles) strings.getString(R.string.dimension_summary,strings.getString(R.string.dimension_sessions)) else SessionDisplay.title(row),
                Modifier.weight(1f).testTag("home-session-title-${row.id}"),style=quotaTextStyle(11),maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(DesktopCompactTokens.format((row.tokens ?: 0).toDouble(),main.compactUnits,locale),Modifier.widthIn(min=42.dp).testTag("home-session-value-${row.id}"),style=quotaTextStyle(11),textAlign=TextAlign.End)
        }
        Row(Modifier.fillMaxWidth().padding(start=17.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            if(models.size>1) Text(listOf(modelLabel,age).filter(String::isNotEmpty).joinToString(" · "),
                Modifier.weight(1f).clickable {showModels=true}.testTag("home-session-models-${row.id}"),style=quotaTextStyle(10),color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
            else Text(listOf(modelLabel,age).filter(String::isNotEmpty).joinToString(" · "),Modifier.weight(1f),style=quotaTextStyle(10),color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(context!=null) {
                val value=if(main.sessionContextMetric=="remaining") context.left else context.used
                val tone=when(context.tone) {"low"->Color(0xFFF4A073);"caution"->Color(0xFFF1D973);else->colors.onSurfaceVariant}
                Row(Modifier.widthIn(min=48.dp).clickable {showContext=true}.semantics {contentDescription=contextLabel}.testTag("home-session-context-${row.id}"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp,Alignment.End)) {
                    Canvas(Modifier.size(16.dp,3.dp)) {
                        drawRoundRect(overlay.copy(alpha=0.18f),cornerRadius=CornerRadius(2.dp.toPx()))
                        drawRoundRect(if(context.tone.isEmpty()) overlay.copy(alpha=0.5f) else tone,size=Size(size.width*value/100,size.height),cornerRadius=CornerRadius(2.dp.toPx()))
                    }
                    Text("$value%",style=quotaTextStyle(10),color=tone)
                }
            } else if(cache!=null) Text(strings.getString(R.string.session_cache,cache.minutes),Modifier.clickable {showContext=true}.testTag("home-session-cache-${row.id}"),style=quotaTextStyle(10),color=colors.onSurfaceVariant)
        }
    }
    if(showModels && models.size>1) CompactUsageDialog(strings.getString(R.string.session_models_details),"home-session-model-details",{showModels=false},desktopTooltip=true) {
        SessionDisplay.modelShares(row,names,observed).forEach {entry->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(entry.name.ifEmpty {strings.getString(R.string.token_unclassified)},Modifier.weight(1f),style=quotaTextStyle(10))
                Text(DesktopCompactTokens.format(entry.tokens.toDouble(),main.compactUnits,locale),style=quotaTextStyle(10),color=colors.onSurface.copy(alpha=0.54f))
                Text(entry.share,style=quotaTextStyle(10),color=colors.onSurface.copy(alpha=0.54f))
            }
        }
    }
    if(showContext && (context!=null || cache!=null)) CompactUsageDialog(contextLabel,"home-session-context-details",{showContext=false},desktopTooltip=true) {
        // The desktop's context tooltip always uses western compact units, independently of total settings.
        context?.let {Text("${DesktopCompactTokens.format(it.tokens,"western","en",false)} / ${DesktopCompactTokens.format(it.window,"western","en",false)}",style=quotaTextStyle(10))}
        cache?.let {Text(strings.getString(R.string.session_cache_details,it.minutes),style=quotaTextStyle(10))}
    }
}

@Composable internal fun SessionActivityMark(state:String,modifier:Modifier=Modifier,waitingReply:Boolean=false) {
    val strings=LocalResources.current;val colors=MaterialTheme.colorScheme;val overlay=LocalDesktopOverlayColor.current
    val label=strings.getString(when(state) {"running"->R.string.session_running;"ended"->R.string.session_finished;"waiting"->if(waitingReply) R.string.session_waiting_reply else R.string.session_waiting;else->R.string.session_idle})
    val elapsed=rememberSessionSpinner(state=="running")
    Canvas(modifier.size(10.dp).testTag("session-state-$state").semantics {contentDescription=label;this[StatusMotionPhase]=(elapsed%900/900).toFloat()}) {
        when(state) {
            "running"->{
                val scale=size.width/11
                repeat(8) {index->rotate(315f-index*45) {
                    val alpha=DesktopSessionSpinner.alpha(elapsed,index)
                    drawRoundRect(colors.onSurface.copy(alpha=alpha),Offset(center.x-0.65f*scale,center.y-4.75f*scale),Size(1.3f*scale,2.75f*scale),CornerRadius(.65f*scale))
                }}
            }
            "waiting"->{
                val tone=colors.primary
                drawLine(tone,Offset(size.width*.35f,size.height*.2f),Offset(size.width*.35f,size.height*.8f),size.width*.15f,StrokeCap.Round)
                drawLine(tone,Offset(size.width*.65f,size.height*.2f),Offset(size.width*.65f,size.height*.8f),size.width*.15f,StrokeCap.Round)
            }
            "ended"->{
                val scale=size.width/24
                drawCircle(colors.onSurfaceVariant,size.width*9/24,style=Stroke(2*scale))
                drawLine(colors.onSurfaceVariant,Offset(8.7f*scale,12.2f*scale),Offset(10.8f*scale,14.3f*scale),2*scale,StrokeCap.Round)
                drawLine(colors.onSurfaceVariant,Offset(10.8f*scale,14.3f*scale),Offset(15.3f*scale,9.7f*scale),2*scale,StrokeCap.Round)
            }
            else->drawCircle(overlay.copy(alpha=.22f),size.width/4)
        }
    }
}
