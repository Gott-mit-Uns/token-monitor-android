package dev.bing.tokenmonitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale

@Composable internal fun SessionPageControls(page:SessionListPage,change:(Int)->Unit) {
    if(!page.paginated) return
    val strings=LocalResources.current
    Row(Modifier.fillMaxWidth().testTag("session-pager"),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
        IconButton({change(page.page-1)},enabled=page.page>0,modifier=Modifier.testTag("session-page-previous")) {
            Icon(painterResource(R.drawable.desktop_arrow_left),strings.getString(R.string.session_page_previous),Modifier.size(12.dp))
        }
        Text(strings.getString(R.string.session_page_range,page.start,page.end,page.total),Modifier.widthIn(min=112.dp).testTag("session-page-status"),style=quotaTextStyle(10),color=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.78f),textAlign=TextAlign.Center)
        IconButton({change(page.page+1)},enabled=page.page<page.pageCount-1,modifier=Modifier.testTag("session-page-next")) {
            Icon(painterResource(R.drawable.desktop_arrow_right),strings.getString(R.string.session_page_next),Modifier.size(12.dp))
        }
    }
}

@Composable internal fun SessionReturn(label:String,tag:String,back:()->Unit) {
    val strings=LocalResources.current
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(onClick=back).testTag(tag),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        Icon(painterResource(R.drawable.desktop_arrow_left),strings.getString(R.string.session_back),Modifier.size(12.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label,style=quotaTextStyle(11),color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Desktop session-mode row: 12/10/9sp hierarchy, three metrics and the source 5dp usage bar. */
@Composable internal fun DesktopSessionRow(display:SessionListRow,max:BigInteger,now:Long,reviewRun:Boolean=false,reduceMotion:String="system",names:ModelNames?=null,observed:Collection<String> = display.source?.let(SessionDisplay::models)?.keys.orEmpty(),motion:State<BreakdownMotionFrame>?=null,motionKey:String=SessionBreakdownMotionProjection.key(display.id),openReview:()->Unit) {
    val strings=LocalResources.current;val colors=MaterialTheme.colorScheme;val main=LocalMainDisplay.current
    val row=display.source;val numbers=remember {NumberFormat.getIntegerInstance(Locale.US)}
    val renderObserver=LocalHeadlineRenderObserver.current
    SideEffect {renderObserver?.invoke("session-row:$motionKey")}
    val barTarget=SessionListDisplay.bar(display.tokens,max)
    val active=!reviewRun && row!=null && SessionDisplay.activity(row,now)=="running"
    val models=row?.let {SessionDisplay.displayModels(it,names,observed)}.orEmpty();val modelLabel=row?.let {SessionListDisplay.modelLabel(it,names,observed)}.orEmpty()
    val context=row?.takeUnless {reviewRun}?.let {SessionDisplay.context(it,now)};val cache=row?.takeUnless {reviewRun}?.let {SessionDisplay.cache(it,now)}
    val palette=LocalDesktopVendorPalette.current
    val client=if(display.review) "codex" else row?.tool.orEmpty()
    val fallbackColors=listOf(0xFF6AB4F0L,0xFF5FBF8AL,0xFFA57DF0L,0xFFD97BC4L,0xFFF0D66AL,0xFFF06A7BL)
    val color=if(reviewRun) Color(0xFF6AB4F0) else palette[client]?.let {Color(it)} ?: if(modelLabel.isNotEmpty()) modelColor(modelLabel,palette) else Color(fallbackColors[(kotlin.math.abs(display.id.hashCode().toLong())%fallbackColors.size).toInt()])
    val shownName=if(reviewRun) listOf(modelLabel,SessionListDisplay.time(display.sortTime,now)).filter(String::isNotEmpty).joinToString(" · ").ifEmpty {strings.getString(R.string.session_review_group)} else display.name
    val shownSubtitle=if(reviewRun) display.detail else display.subtitle
    val shownActivity=if(reviewRun) "" else display.activity
    val shownDetail=if(reviewRun) "" else display.detail
    val sunkenContext=LocalDesktopSunkenColor.current.copy(alpha=.46f)
    val lineColor=LocalDesktopLineColor.current
    val neutralContext=lineColor.copy(alpha=.55f)
    var modelsOpen by rememberSaveable(display.id) {mutableStateOf(false)}
    LaunchedEffect(models.size) {if(models.size<2) modelsOpen=false}
    var contextOpen by rememberSaveable(display.id) {mutableStateOf(false)}
    var summaryOpen by rememberSaveable(display.id) {mutableStateOf(false)}
    fun titleModifier(text:String):Modifier=if(models.size>1 && (text.endsWith(modelLabel) || (reviewRun && text==shownName))) Modifier.clickable {modelsOpen=true}.testTag("session-models-${display.id}") else Modifier
    Column(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable {if(display.review) openReview() else summaryOpen=true}.testTag("session-row-${display.id}").semantics {if(active) stateDescription=strings.getString(R.string.session_running)},verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                Box(Modifier.padding(top=2.dp).size(11.dp)) {
                    if(reviewRun) Text("›",style=quotaTextStyle(12),color=colors.onSurfaceVariant)
                    else if(LocalDesktopToolIcons.current && brandResource(client)!=null) BrandMark(client,size=11.dp)
                    else Canvas(Modifier.size(11.dp).testTag("session-dot-${display.id}")) {drawCircle(color,4.dp.toPx())}
                    SessionRunningDot(display.id,display.sortTime,active,reduceMotion,Modifier.align(Alignment.TopStart).offset(x=6.dp,y=6.dp).size(5.dp))
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(1.dp)) {
                    Box(titleModifier(shownName)) {Text(shownName,Modifier.testTag("session-title-${display.id}"),style=quotaTextStyle(12),maxLines=1,overflow=TextOverflow.Ellipsis)}
                    if(shownSubtitle.isNotEmpty()) Box(titleModifier(shownSubtitle)) {Text(shownSubtitle,Modifier.testTag("session-subtitle-${display.id}"),style=quotaTextStyle(10),color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)}
                    if(shownActivity.isNotEmpty()) Text(shownActivity,Modifier.testTag("session-activity-${display.id}"),style=quotaTextStyle(10),color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                    // Hub metadata has no local transcript detail. Keep its identifier available in the read-only summary.
                    if(shownActivity.isEmpty() && shownDetail.isNotEmpty()) Text(shownDetail,Modifier.testTag("session-id-${display.id}"),style=quotaTextStyle(10),color=colors.onSurfaceVariant.copy(alpha=.84f),maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
            Column(Modifier.widthIn(min=66.dp),horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                BreakdownAnimatedNumber(display.tokens,motion,motionKey,Modifier.testTag("session-tokens-${display.id}"),
                    style=quotaTextStyle(12),locale=Locale.US,maxLines=1)
                Text(usageCostText(display.cost,display.unpriced),Modifier.testTag("session-cost-${display.id}"),style=quotaTextStyle(10),color=colors.onSurfaceVariant,maxLines=1)
                if(context!=null) {
                    val percent=if(main.sessionContextMetric=="remaining") context.left else context.used
                    val tone=when(context.tone) {"low"->Color(0xFFF4A073);"caution"->Color(0xFFF1D973);else->colors.onSurfaceVariant}
                    Row(Modifier.widthIn(min=48.dp).clickable {contextOpen=true}.testTag("session-context-${display.id}").semantics {contentDescription=strings.getString(R.string.session_context_details)},horizontalArrangement=Arrangement.spacedBy(4.dp,Alignment.End),verticalAlignment=Alignment.CenterVertically) {
                        DesktopSessionContextMeter(display.id,percent,sunkenContext,
                            if(context.tone.isEmpty()) neutralContext else tone,reduceMotion)
                        Text("$percent%",style=quotaTextStyle(9),color=tone)
                    }
                } else if(cache!=null) Text(strings.getString(R.string.session_cache,cache.minutes),Modifier.clickable {contextOpen=true}.testTag("session-cache-${display.id}"),style=quotaTextStyle(9),color=colors.onSurfaceVariant)
            }
        }
        val sunken=LocalDesktopSunkenColor.current.copy(alpha=.46f)
        Column {
            Canvas(Modifier.fillMaxWidth().height(5.dp).testTag("session-bar-${display.id}")) {
                drawRoundRect(sunken,cornerRadius=CornerRadius(3.dp.toPx()))
                drawRoundRect(color,size=Size(size.width*(motion?.value?.bar(motionKey,barTarget) ?: barTarget),size.height),cornerRadius=CornerRadius(3.dp.toPx()))
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(thickness=1.dp,color=lineColor.copy(alpha=.12f))
        }
    }
    if(modelsOpen && row!=null && models.size>1) CompactUsageDialog(strings.getString(R.string.session_models_details),"session-model-details",{modelsOpen=false},desktopTooltip=true) {
        SessionDisplay.modelShares(row,names,observed).forEach {entry->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(entry.name.ifEmpty {strings.getString(R.string.token_unclassified)},Modifier.weight(1f),style=quotaTextStyle(10))
            Text(numbers.format(entry.tokens),style=quotaTextStyle(10),color=colors.onSurface.copy(alpha=.54f))
            Text(entry.share,style=quotaTextStyle(10),color=colors.onSurface.copy(alpha=.54f))
        }}
    }
    if(contextOpen && row!=null && (context!=null || cache!=null)) CompactUsageDialog(strings.getString(R.string.session_context_details),"session-context-details",{contextOpen=false},desktopTooltip=true) {
        val tokens=row.session?.contextTokens;val window=row.session?.contextWindow
        if(row.session?.archived!=true && tokens!=null && window!=null) Text("${DesktopCompactTokens.format(tokens,"western","en",false)} / ${DesktopCompactTokens.format(window,"western","en",false)}",style=quotaTextStyle(10))
        cache?.let {Text(strings.getString(R.string.session_cache_details,it.minutes),style=quotaTextStyle(10))}
    }
    if(summaryOpen && row!=null) CompactUsageDialog(display.name,"session-summary-details",{summaryOpen=false},desktopTooltip=true) {
        Text(strings.getString(R.string.session_hub_summary),style=quotaTextStyle(10),color=colors.onSurfaceVariant)
        Text("${numbers.format(display.tokens)} Token · ${usageCostText(display.cost,display.unpriced)}",style=quotaTextStyle(11))
        Text(display.detail,style=quotaTextStyle(10))
        Text(listOf(display.subtitle,display.activity).filter(String::isNotEmpty).joinToString("\n"),style=quotaTextStyle(10))
    }
}
