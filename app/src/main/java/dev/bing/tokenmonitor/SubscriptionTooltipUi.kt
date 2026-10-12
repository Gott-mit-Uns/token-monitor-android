package dev.bing.tokenmonitor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject

internal val LocalSubscriptionReadContext=staticCompositionLocalOf<SubscriptionReadContext?> {null}

/** Touch equivalent of the original dotted plan-label tooltip. */
@Composable internal fun SubscriptionPlanControl(text:String,provider:JsonObject,includeRollup:Boolean,tag:String,modifier:Modifier=Modifier) {
    if(text.isEmpty()) return
    val context=LocalSubscriptionReadContext.current
    val rows=context?.forRow(provider,includeRollup).orEmpty()
    var opened by remember(provider["provider"],provider["accountKey"],provider["accountGroup"]) {mutableStateOf(false)}
    LaunchedEffect(rows.isEmpty()) {if(rows.isEmpty()) opened=false}
    val colors=MaterialTheme.colorScheme
    if(rows.isEmpty()) Text(text,modifier,style=quotaTextStyle(10),color=colors.onSurfaceVariant)
    else Box(modifier.heightIn(min=48.dp).clickable {opened=true}.semantics {role=Role.Button}.testTag(tag),contentAlignment=Alignment.TopEnd) {
        Text(text,Modifier.drawBehind {drawLine(colors.onSurfaceVariant.copy(alpha=0.5f),Offset(0f,size.height),Offset(size.width,size.height),strokeWidth=1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(),2.dp.toPx())))},style=quotaTextStyle(10),color=colors.onSurfaceVariant)
    }
    if(opened && rows.isNotEmpty()) CompactUsageDialog(text,"subscription-plan-details",{opened=false},desktopTooltip=true) {SubscriptionTooltipTable(rows,"subscription-plan")}
}

@Composable internal fun SubscriptionTooltipTable(rows:List<SubscriptionTooltipRow>,tag:String) {
    val colors=MaterialTheme.colorScheme;val masked=LocalLimitDisplay.current.maskEmails
    val locale=LocalSubscriptionReadContext.current?.locale ?: LocalResources.current.configuration.locales[0]
    Column(Modifier.fillMaxWidth().testTag("$tag-table"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        rows.forEachIndexed {index,row->
            if(row.separator) HorizontalDivider(Modifier.padding(vertical=2.dp),color=colors.outlineVariant)
            else BoxWithConstraints(Modifier.fillMaxWidth()) {
                val label=LimitDisplay.text(row.label,masked);val value=LimitDisplay.text(row.value,masked)
                val valueTag=when(row.label) {
                    SubscriptionWording.text(locale,"subscription.tooltip.subscribed")->if(tag=="shared-subscription") "shared-subscription-paid" else "$tag-value-$index"
                    SubscriptionWording.text(locale,"subscription.tooltip.nextCharge"),
                    SubscriptionWording.text(locale,"subscription.tooltip.validUntil")->if(tag=="shared-subscription") "shared-subscription-boundary" else "$tag-value-$index"
                    else->"$tag-value-$index"
                }
                // The desktop is two columns. Wrap on narrow or enlarged-text displays.
                if(maxWidth<260.dp || effectiveTextScale()>1.15f) Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(label,style=quotaTextStyle(10),color=colors.onSurfaceVariant)
                    Text(value,Modifier.testTag(valueTag),style=quotaTextStyle(10),color=if(row.warn) LocalDesktopWarningColor.current else colors.onSurface.copy(alpha=0.54f))
                } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top) {
                    Text(label,Modifier.weight(0.36f),style=quotaTextStyle(10),color=colors.onSurfaceVariant)
                    Text(value,Modifier.weight(0.64f).testTag(valueTag),style=quotaTextStyle(10),textAlign=TextAlign.End,color=if(row.warn) LocalDesktopWarningColor.current else colors.onSurface.copy(alpha=0.54f))
                }
            }
        }
        rows.map {it.title}.filter {it.isNotEmpty()}.distinct().forEach {note->Text(LimitDisplay.text(note,masked),style=quotaTextStyle(9),color=colors.onSurfaceVariant,modifier=Modifier.testTag("$tag-note"))}
    }
}
