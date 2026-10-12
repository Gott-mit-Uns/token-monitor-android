package dev.bing.tokenmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** The source Home uses small text rows, with a separate optional meter mode. */
@Composable internal fun HomeQuotaRow(account:HomeQuotaAccount,entryFraction:State<Float?>?=null,open:()->Unit) {
    val colors=MaterialTheme.colorScheme;val display=LocalLimitDisplay.current
    val renderObserver=LocalHeadlineRenderObserver.current
    if(renderObserver!=null) SideEffect {renderObserver("home-quota-row:${account.recordIndex}")}
    val muted=lerp(colors.onSurfaceVariant,colors.onSurface,0.22f)
    val mark=LimitProviderLayout.adapterMark(account.source.first())
    val color=when(mark) {"newapi"->Color(0xFFC738FB);"sub2api"->Color(0xFF39D9E7);else->providerColor(account.provider,LocalDesktopVendorPalette.current)}
    Column(Modifier.fillMaxWidth().testTag("home-quota-${account.recordIndex}")
        .pointerInput(open) {detectTapGestures {open()}}.semantics {onClick {open();true}},verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            BrandMark(mark,respectToolIcons=true,providerMark=true,size=10.dp)
            Text(LimitDisplay.text(account.name,display.maskEmails),Modifier.weight(1f).testTag("home-quota-name-${account.recordIndex}"),style=quotaTextStyle(11))
            if(account.plan.isNotEmpty()) Text(account.plan,Modifier.widthIn(max=140.dp),style=quotaTextStyle(10),color=muted)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start=if(display.homeBars) 0.dp else 18.dp)) {
            val availableWidth=maxWidth.value
            val stacked=maxWidth<270.dp || effectiveTextScale()>1.15f
            @Composable fun window(index:Int,w:HomeQuotaWindow) {
                val tag="${account.recordIndex}-$index"
                val tone=homeQuotaTone(w.remaining,display.highlightLowHomeLimits)
                val critical=tone==HomeQuotaTone.CRITICAL
                val low=tone==HomeQuotaTone.LOW
                val valueColor=if(critical) LocalDesktopWarningColor.current else if(low) color else colors.onSurface
                @Composable fun value() {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        if(critical) Box(Modifier.size(4.dp).clip(RoundedCornerShape(999.dp)).background(valueColor).testTag("home-quota-critical-$tag"))
                        Text(w.value,style=quotaTextStyle(10),color=valueColor,modifier=Modifier.testTag("limit-value-$tag"))
                    }
                }
                Column(Modifier.fillMaxWidth().testTag("limit-window-$tag"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    val wrap=effectiveTextScale()>1.15f || w.label.length+w.value.length>availableWidth/(if(stacked || account.windows.size==1) 5.5f else 11f)
                    if(wrap) Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        Text(w.label,style=quotaTextStyle(10),color=muted)
                        value()
                    } else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(w.label,Modifier.weight(1f),style=quotaTextStyle(10),color=muted)
                        value()
                    }
                    if(display.homeBars && w.source.presentation.showMeter) w.percent?.let {percent->
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(color.copy(alpha=0.16f)).testTag("home-quota-meter-$tag")) {
                            HomeQuotaEntryMeter(percent,color,if(w.source.kind in setOf("session","daily")) 0.95f else 0.68f,entryFraction,Modifier.testTag("home-quota-entry-$tag"))
                        }
                    }
                    if(w.boundary.isNotEmpty()) Text(w.boundary,style=quotaTextStyle(9),color=muted,modifier=Modifier.testTag("home-quota-reset-$tag"))
                }
            }
            if(stacked) Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {account.windows.forEachIndexed {i,w->window(i,w)}}
            else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(if(display.homeBars) 10.dp else 16.dp)) {account.windows.forEachIndexed {i,w->Box(Modifier.weight(1f)) {window(i,w)}}}
        }
    }
}
