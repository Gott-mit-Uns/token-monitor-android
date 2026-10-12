package dev.bing.tokenmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.*
import java.time.Instant

/** Use the source's quota text sizes while preserving the chosen font and text scale. */
@Composable internal fun quotaTextStyle(size:Int,bold:Boolean=false):TextStyle = MaterialTheme.typography.bodySmall.copy(
    fontSize=(size*LocalDesktopTextScale.current).sp,lineHeight=(size*1.2f*LocalDesktopTextScale.current).sp,
    fontWeight=if(bold) FontWeight.SemiBold else FontWeight.Normal)

@Composable
internal fun DesktopAccount(windows:List<Limit>,compact:Boolean=false,showAccount:Boolean=!compact,
    peers:List<Limit> = emptyList(),accountIndex:Int=0,showProvider:Boolean=true,now:Instant=Instant.now(),titleOverride:String?=null,
    quotaRows:List<ProviderQuotaRow>?=null,quotaMotion:State<QuotaWindowMotionFrame>?=null) {
    if(windows.isEmpty()) return
    val strings=LocalResources.current;val first=windows.first();val colors=MaterialTheme.colorScheme;val display=LocalLimitDisplay.current
    val renderObserver=LocalHeadlineRenderObserver.current
    if(renderObserver!=null) SideEffect {renderObserver("quota-account:${first.presentation.recordIndex ?: accountIndex}")}
    val account=LimitDisplay.accountTitle(first,peers.ifEmpty {listOf(first)},accountIndex,display.maskEmails)
    val plan=LimitProviderLayout.plan(first,!showProvider,display.maskEmails)
    val locale=strings.configuration.locales[0]
    val record=remember(windows) {LimitProviderLayout.record(windows)}
    val selected=remember(record,windows,compact) {
        if(!compact) record else LimitProviderLayout.compactRecord(windows,record)
    }
    val hidden=display.hiddenItems[first.provider].orEmpty()
    val projected=remember(selected,display.showUsed,locale,now,hidden,quotaRows) {quotaRows ?: LimitPreferences.filter(LimitProviderLayout.project(selected,display.showUsed,locale,now),hidden)}
    val mark=LimitProviderLayout.adapterMark(first)
    val color=when(mark) {"newapi"->Color(0xFFC738FB);"sub2api"->Color(0xFF39D9E7);"thirdparty"->Color(0xFF8A96A8);else->providerColor(first.provider,LocalDesktopVendorPalette.current)}
    var expanded by rememberSaveable(first.provider,first.presentation.recordIndex,accountIndex) {mutableStateOf(false)}
    Column(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable {expanded=!expanded}.testTag("limit-account-${first.presentation.recordIndex ?: accountIndex}"),
            verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(showProvider || (first.provider=="thirdparty" && peers.map(LimitProviderLayout::adapterMark).distinct().size>1)) BrandMark(mark,Modifier.padding(top=1.dp),respectToolIcons=false,providerMark=true,size=12.dp)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                val title=titleOverride ?: if(showProvider) first.displayProvider(strings) else account
                if(title.isNotEmpty()) Text(title,style=quotaTextStyle(if(showProvider) 12 else 11))
                if(showProvider && showAccount) Text(account,style=quotaTextStyle(11),color=colors.onSurface)
                if(first.status=="ok" || first.stale==true) Text(LimitProviderLayout.meta(first,now,display),style=quotaTextStyle(10),color=if(first.stale==true) colors.error else colors.onSurfaceVariant,
                    modifier=Modifier.testTag("limit-meta-${first.presentation.recordIndex ?: accountIndex}"))
            }
            if(plan.isNotEmpty()) SubscriptionPlanControl(plan,record,showProvider,"limit-plan-${first.presentation.recordIndex ?: accountIndex}",Modifier.widthIn(max=160.dp))
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val oneColumn=maxWidth<300.dp || effectiveTextScale()>1.15f
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                val grid=mutableListOf<List<ProviderQuotaRow>>();var pending=mutableListOf<ProviderQuotaRow>()
                projected.forEach {row ->
                    if(pending.isNotEmpty() && (pending.first().group!=row.group || row.wide || oneColumn)) {grid+=pending.toList();pending.clear()}
                    pending+=row
                    if(oneColumn || row.wide || pending.size==2) {grid+=pending.toList();pending.clear()}
                }
                if(pending.isNotEmpty()) grid+=pending.toList()
                var previousGroup:String?=null
                grid.forEach {row ->
                    val group=row.first().group
                    if(group!=null && group!=previousGroup) Text(LimitDisplay.text(group,display.maskEmails),style=quotaTextStyle(10),color=colors.onSurface,modifier=Modifier.testTag("limit-group-${first.presentation.recordIndex ?: accountIndex}-$group"))
                    previousGroup=group
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        row.forEach {quota->Box(Modifier.weight(1f)) {DesktopQuotaWindow(quota,first.presentation.recordIndex ?: accountIndex,projected.indexOf(quota),first.provider,color,quotaMotion)}}
                    }
                }
            }
        }
        if(expanded) {
            Text(account,style=quotaTextStyle(11),modifier=Modifier.testTag("limit-expanded-account-${first.presentation.recordIndex ?: accountIndex}"))
            projected.filter {it.details.isNotEmpty()}.forEach {row->
                Text(LimitDisplay.text(row.label,display.maskEmails),style=quotaTextStyle(11,bold=true))
                row.details.forEach {cells->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {cells.forEach {Text(LimitDisplay.text(it,display.maskEmails),Modifier.weight(1f),style=quotaTextStyle(10))}}}
            }
            val raw=(record["windows"] as? JsonArray).orEmpty()
            windows.filterIndexed {index,w->w.kind!=null && (raw.getOrNull(index) as? JsonObject)?.let {LimitPreferences.windowItem(first.provider,it)} !in hidden}.forEach {LimitWindow(it)}
        }
        if(!compact) HorizontalDivider(color=colors.outlineVariant.copy(alpha=0.6f))
    }
}

@Composable private fun DesktopQuotaWindow(row:ProviderQuotaRow,recordIndex:Int,index:Int,provider:String,color:Color,motion:State<QuotaWindowMotionFrame>?=null) {
    val colors=MaterialTheme.colorScheme;val display=LocalLimitDisplay.current;val tag="$recordIndex-$index"
    val readableMuted=lerp(colors.onSurfaceVariant,colors.onSurface,0.22f)
    if(row.resetSummary!=null) {
        Row(Modifier.fillMaxWidth().testTag("limit-window-$tag"),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Bottom) {
            Text(row.value,Modifier.weight(1f).testTag("limit-value-$tag"),style=quotaTextStyle(9),color=colors.onSurfaceVariant)
            if(row.resetSummary.isNotEmpty()) Text(row.resetSummary.replace("·"," · "),style=quotaTextStyle(9),color=colors.onSurface.copy(alpha=0.66f),modifier=Modifier.testTag("limit-resets-$tag"))
        }
        return
    }
    Column(Modifier.fillMaxWidth().testTag("limit-window-$tag"),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Source truncates; Android wraps full numbers and identities at narrow widths.
            val inline=effectiveTextScale()<=1.15f && row.value.length+row.label.length<maxWidth.value/5.6f
            if(inline) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(LimitDisplay.text(row.label,display.maskEmails),Modifier.weight(1f),style=quotaTextStyle(10),color=readableMuted)
                QuotaMotionValue(row,motion,style=quotaTextStyle(10),modifier=Modifier.testTag("limit-value-$tag"))
            } else Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(LimitDisplay.text(row.label,display.maskEmails),style=quotaTextStyle(10),color=readableMuted)
                QuotaMotionValue(row,motion,style=quotaTextStyle(10),modifier=Modifier.testTag("limit-value-$tag"))
            }
        }
        row.percent?.let {percent->Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(color.copy(alpha=0.16f)).testTag("limit-color-$provider")) {
            QuotaMotionMeter(row.motion,percent,color,row.tone.toFloat(),motion,Modifier.testTag("limit-meter-$tag"))
        }}
        if(!row.noReset && (row.percent!=null || row.boundary.isNotEmpty() || row.detail.isNotEmpty())) Row(Modifier.fillMaxWidth().heightIn(min=11.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(LimitDisplay.text(row.boundary,display.maskEmails),Modifier.weight(1f),style=quotaTextStyle(9),color=readableMuted)
            if(row.detail.isNotEmpty()) Text(LimitDisplay.text(row.detail,display.maskEmails),style=quotaTextStyle(9),color=colors.onSurface.copy(alpha=0.7f),modifier=Modifier.testTag("limit-detail-$tag"))
        } else if(row.detail.isNotEmpty()) Text(LimitDisplay.text(row.detail,display.maskEmails),style=quotaTextStyle(9),color=colors.onSurface.copy(alpha=0.7f),modifier=Modifier.testTag("limit-detail-$tag"))
    }
}
internal fun limitStatusResource(status:String?)=when(status) {
    "disabled"->R.string.limit_status_disabled;"notConfigured"->R.string.limit_status_not_configured
    "noSyncedData"->R.string.limit_status_no_data;"unauthorized"->R.string.limit_status_unauthorized
    "rateLimited","sourceRateLimited"->R.string.limit_status_limited;"unavailable"->R.string.limit_status_unavailable;else->R.string.limit_status_error
}
