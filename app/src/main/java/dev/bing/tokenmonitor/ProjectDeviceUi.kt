package dev.bing.tokenmonitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.floor

private val sourceFallbackColors=listOf(0xFF6AB4F0L,0xFF5FBF8AL,0xFFA57DF0L,0xFFD97BC4L,0xFFF0D66AL,0xFFF06A7BL)
@Composable private fun SourceUsageBar(value:BigInteger,max:BigInteger,color:Color,gradient:List<Pair<Float,Color>> = emptyList(),tag:String,motion:State<BreakdownMotionFrame>?=null,motionKey:String=tag) {
    val sunken=LocalDesktopSunkenColor.current.copy(alpha=.46f)
    val target=SessionListDisplay.bar(value,max)
    val stops=remember(gradient) {gradient.toTypedArray()}
    Canvas(Modifier.fillMaxWidth().height(6.dp).testTag(tag).semantics {
        progressBarRangeInfo=ProgressBarRangeInfo(motion?.value?.bar(motionKey,target) ?: target,0f..1f)
    }) {
        drawRoundRect(sunken,cornerRadius=CornerRadius(3.dp.toPx()))
        val width=size.width*(motion?.value?.bar(motionKey,target) ?: target)
        if(width>0) {
            if(gradient.size>1) drawRoundRect(Brush.horizontalGradient(*stops,endX=width),size=Size(width,size.height),cornerRadius=CornerRadius(3.dp.toPx()))
            else drawRoundRect(gradient.firstOrNull()?.second ?: color,size=Size(width,size.height),cornerRadius=CornerRadius(3.dp.toPx()))
        }
    }
}
@Composable private fun RowRule() {Spacer(Modifier.height(10.dp));HorizontalDivider(thickness=1.dp,color=LocalDesktopLineColor.current.copy(alpha=.12f))}
@Composable internal fun DeviceMark(platform:String,stale:Boolean,tag:String,size:androidx.compose.ui.unit.Dp=10.dp) {
    val os=DeviceDisplay.icon(platform)
    if(LocalDesktopToolIcons.current && os!=null) Icon(painterResource(when(os) {"apple"->R.drawable.brand_os_apple;"windows"->R.drawable.brand_os_windows;else->R.drawable.brand_os_linux}),null,Modifier.size(size).testTag("device-icon-$tag"),tint=MaterialTheme.colorScheme.onSurface)
    else Canvas(Modifier.size(size).testTag("device-dot-$tag")) {drawCircle(Color(if(stale) DeviceDisplay.staleColor else DeviceDisplay.accent),4.dp.toPx().coerceAtMost(size.toPx()/2))}
}
@Composable internal fun HomeDeviceLine(row:HomeDeviceRow) {
    val strings=LocalResources.current;val main=LocalMainDisplay.current;val colors=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().testTag("home-device-${row.id}"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        DeviceMark(row.platform,row.stale,"home-${row.id}")
        Text(row.name,Modifier.weight(1f),style=quotaTextStyle(11),color=if(row.stale) colors.onSurfaceVariant else colors.onSurface,maxLines=1,overflow=TextOverflow.Ellipsis)
        if(row.local) Text("you",style=quotaTextStyle(9),color=colors.onSurfaceVariant)
        Text(DesktopCompactTokens.format(row.tokens.toDouble(),main.compactUnits,DesktopCompactTokens.localeTag(strings.configuration.locales[0])),style=quotaTextStyle(11),textAlign=TextAlign.End,color=if(row.stale) colors.onSurfaceVariant else colors.onSurface)
    }
}

@Composable internal fun DesktopProjectRow(row:ProjectRow,max:BigInteger,hideTitles:Boolean,motion:State<BreakdownMotionFrame>?=null,motionKey:String="project:${row.id}") {
    val strings=LocalResources.current;val colors=MaterialTheme.colorScheme;val palette=LocalDesktopVendorPalette.current
    val numbers=remember {NumberFormat.getIntegerInstance(Locale.US)}
    val observer=LocalHeadlineRenderObserver.current
    SideEffect {observer?.invoke("breakdown-row:$motionKey")}
    val border=LocalDesktopOverlayColor.current.copy(alpha=.08f)
    val name=if(hideTitles) strings.getString(R.string.dimension_summary,strings.getString(R.string.dimension_projects)) else row.name
    val fallback=Color(sourceFallbackColors[ProjectDisplay.stableIndex(row.id)])
    fun color(client:String)=palette[client]?.let {Color(it)} ?: fallback
    val gradient=ProjectDisplay.gradient(row.clients).map {it.position to color(it.client)}
    var expanded by rememberSaveable(row.id) {mutableStateOf(false)}
    var summary by rememberSaveable(row.id) {mutableStateOf(false)}
    Column(Modifier.fillMaxWidth().heightIn(min=48.dp).combinedClickable(role=Role.Button,onClick={expanded=!expanded},onLongClick={summary=true},onLongClickLabel=strings.getString(R.string.usage_summary)).semantics {stateDescription=strings.getString(if(expanded) R.string.desktop_collapse else R.string.desktop_expand)}.testTag("project-row-${row.id}")) {
        Row(Modifier.fillMaxWidth().testTag("project-head-${row.id}"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            if(LocalDesktopToolIcons.current) Icon(painterResource(R.drawable.desktop_project_row),null,Modifier.size(10.dp)) else Canvas(Modifier.size(8.dp)) {drawCircle(fallback)}
            Text(name,Modifier.weight(1f).testTag("project-title-${row.id}"),style=quotaTextStyle(12),maxLines=1,overflow=TextOverflow.Ellipsis)
            Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                BreakdownAnimatedNumber(row.tokens,motion,motionKey,Modifier.testTag("project-tokens-${row.id}"),style=quotaTextStyle(12),locale=Locale.US)
                Text(usageCostText(row.cost,row.unpriced),Modifier.testTag("project-cost-${row.id}"),style=quotaTextStyle(10),color=colors.onSurfaceVariant,maxLines=1)
            }
        }
        Spacer(Modifier.height(6.dp))
        SourceUsageBar(row.tokens,max,fallback,gradient,"project-bar-${row.id}",motion,motionKey)
        DesktopAccordion(expanded,"project-details-motion-${row.id}",kind=DesktopDisclosureKind.PROJECT_DETAILS) {
        Column(Modifier.fillMaxWidth().padding(start=4.dp).drawBehind {drawLine(border,Offset.Zero,Offset(0f,size.height),1.dp.toPx())}.padding(start=12.dp,top=5.dp,bottom=2.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            row.clients.forEachIndexed {index,client->Row(Modifier.fillMaxWidth().testTag("project-tool-${row.id}-$index"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Canvas(Modifier.size(7.dp)) {drawCircle(color(client.sourceClient))}
                    Text(client.label,Modifier.weight(1f,false),style=quotaTextStyle(11).copy(lineHeight=14.85.sp),color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text("${floor(client.percent+0.5).toLong()}%",style=quotaTextStyle(10),color=colors.onSurfaceVariant.copy(alpha=.6f))
                }
                Text(numbers.format(client.tokens),style=quotaTextStyle(11).copy(lineHeight=14.85.sp),color=colors.onSurface.copy(alpha=.85f))
            }}
        }
        }
        RowRule()
    }
    if(summary) CompactUsageDialog(if(hideTitles) strings.getString(R.string.usage_summary) else row.name,"project-summary-details",{summary=false},desktopTooltip=true) {
        Text("${numbers.format(row.tokens)} Token",style=quotaTextStyle(11))
        Text(strings.getString(R.string.known_cost_formatted,usageCostText(row.cost,row.unpriced)),style=quotaTextStyle(10))
    }
}

@Composable internal fun DesktopDeviceRow(device:Device,alias:String?,period:String,max:BigInteger,now:Long,motion:State<BreakdownMotionFrame>?=null,motionKey:String="device:${device.id}",details:()->Unit) {
    val strings=LocalResources.current;val colors=MaterialTheme.colorScheme;val usage=device.periods[period]
    val border=LocalDesktopOverlayColor.current.copy(alpha=.08f)
    val observer=LocalHeadlineRenderObserver.current
    SideEffect {observer?.invoke("breakdown-row:$motionKey")}
    val numbers=remember {NumberFormat.getIntegerInstance(Locale.US)};val main=LocalMainDisplay.current
    val total=BigInteger.valueOf((usage?.tokens ?: 0).coerceAtLeast(0));val stale=device.stale==true
    val color=Color(if(stale) DeviceDisplay.staleColor else DeviceDisplay.accent)
    val tools=DeviceDisplay.tools(device,period,::toolLabel,strings.getString(R.string.token_unclassified))
    var expanded by rememberSaveable(device.id) {mutableStateOf(false)}
    val runtime=when(device.agentRuntime) {"electron-widget"->"Widget";"headless-agent"->"Agent";else->device.agentRuntime.orEmpty()}
    val version=if(!device.agentVersion.isNullOrEmpty()) (if(runtime.isEmpty()) "" else "$runtime ")+"v${device.agentVersion}" else runtime
    val age=DeviceDisplay.age(device.updated,now)?.let {(unit,value)->strings.getString(when(unit) {"minutes"->R.string.device_age_minutes;"hours"->R.string.device_age_hours;"days"->R.string.device_age_days;else->R.string.device_age_now},value)}
    val meta=listOf(DeviceDisplay.platform(device),version,age?.let {strings.getString(R.string.device_synced,it)}.orEmpty()).filter(String::isNotEmpty).joinToString(" · ")
    Column(Modifier.fillMaxWidth().heightIn(min=48.dp).combinedClickable(role=Role.Button,onClick={expanded=!expanded},onLongClick=details,onLongClickLabel=strings.getString(R.string.device_details)).semantics {stateDescription=strings.getString(if(expanded) R.string.desktop_collapse else R.string.desktop_expand)}.testTag("device-row-${device.id}")) {
        Row(Modifier.fillMaxWidth().testTag("device-head-${device.id}"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            DeviceMark(device.rawPlatform ?: device.platform.orEmpty(),stale,device.id)
            Text(alias?.takeIf(String::isNotEmpty) ?: device.id.takeIf(String::isNotEmpty) ?: device.name.takeUnless {it=="__unnamed_device__" || it.isEmpty()} ?: "device",Modifier.weight(1f).testTag("device-name-${device.id}"),style=quotaTextStyle(12),color=if(stale) colors.onSurfaceVariant else colors.onSurface,maxLines=1,overflow=TextOverflow.Ellipsis)
            Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                BreakdownAnimatedNumber(total,motion,motionKey,Modifier.testTag("device-tokens-${device.id}"),style=quotaTextStyle(12),color=if(stale) colors.onSurfaceVariant else colors.onSurface,locale=Locale.US)
                Text(usageCostText(usage?.cost ?: "0",usage?.unpriced),Modifier.testTag("device-cost-${device.id}"),style=quotaTextStyle(10),color=colors.onSurfaceVariant,maxLines=1)
            }
        }
        Spacer(Modifier.height(6.dp))
        SourceUsageBar(total,max,color,tag="device-bar-${device.id}",motion=motion,motionKey=motionKey)
        DesktopAccordion(expanded,"device-details-motion-${device.id}",kind=DesktopDisclosureKind.DEVICE_DETAILS) {
        Column(Modifier.fillMaxWidth().padding(start=4.dp).drawBehind {drawLine(border,Offset.Zero,Offset(0f,size.height),1.dp.toPx())}.padding(start=12.dp,top=5.dp,bottom=2.dp).testTag("device-expanded-${device.id}"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            if(tools.isEmpty()) Text(strings.getString(if(total.signum()>0) R.string.device_tool_details_missing else R.string.home_tools_empty),style=quotaTextStyle(10),color=colors.onSurfaceVariant)
            tools.forEachIndexed {index,tool->
                if(index>0) HorizontalDivider(color=LocalDesktopOverlayColor.current.copy(alpha=.07f),thickness=1.dp)
                Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth().testTag("device-tool-${device.id}-${tool.id}"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            BreakdownMark(tool.id,false,null,9.dp)
                            Text(tool.label,Modifier.weight(1f,false),style=quotaTextStyle(11),color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text("${floor(tool.percent+0.5).toLong()}%",style=quotaTextStyle(10),color=colors.onSurfaceVariant.copy(alpha=.6f))
                        }
                        Text(numbers.format(tool.tokens),style=quotaTextStyle(11),color=colors.onSurface.copy(alpha=.85f))
                    }
                    tool.models.forEach {(model,value)->Row(Modifier.fillMaxWidth().padding(start=15.dp).testTag("device-model-${device.id}-${tool.id}-$model"),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(model,Modifier.weight(1f),style=quotaTextStyle(10).copy(lineHeight=13.sp),color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(DesktopCompactTokens.format(value.toDouble(),main.compactUnits,DesktopCompactTokens.localeTag(strings.configuration.locales[0])),style=quotaTextStyle(10).copy(lineHeight=13.sp),color=colors.onSurfaceVariant.copy(alpha=.62f))
                    }}
                }
            }
            if(meta.isNotEmpty()) {HorizontalDivider(color=LocalDesktopOverlayColor.current.copy(alpha=.07f),thickness=1.dp);Text(meta,Modifier.testTag("device-meta-${device.id}"),style=quotaTextStyle(9).copy(lineHeight=12.15.sp),color=colors.onSurfaceVariant)}
        }
        }
        RowRule()
    }
}
