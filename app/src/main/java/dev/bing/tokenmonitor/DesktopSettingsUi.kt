package dev.bing.tokenmonitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

internal val LocalDesktopSettingsRowHeight=staticCompositionLocalOf {48.dp}

@Composable internal fun DesktopSettingSection(id:String,title:String,icon:Int,summary:String,expanded:Boolean,tag:String,first:Boolean=false,last:Boolean=false,toggle:()->Unit,content:@Composable ColumnScope.()->Unit) {
    val shape=androidx.compose.foundation.shape.RoundedCornerShape(topStart=if(first) 12.dp else 0.dp,topEnd=if(first) 12.dp else 0.dp,bottomStart=if(last) 12.dp else 0.dp,bottomEnd=if(last) 12.dp else 0.dp)
    val colors=MaterialTheme.colorScheme
    val description=LocalResources.current.getString(if(expanded) R.string.desktop_collapse else R.string.desktop_expand)
    val saved=rememberSaveableStateHolder()
    val motion=rememberDesktopDisclosureMotion(expanded,DesktopDisclosureKind.SETTINGS_SECTION)
    val anchor=LocalDesktopSettingsScrollAnchor.current
    val toggleWithAnchor:()->Unit={if(anchor==null) toggle() else anchor.toggle(id,!expanded,toggle)}
    Column(Modifier.fillMaxWidth().clip(shape).background(colors.surfaceContainer.copy(alpha=if(expanded) .55f else .35f)).testTag("settings-section-$id")) {
        if(!first) HorizontalDivider(color=LocalDesktopLineColor.current.copy(alpha=.07f),thickness=1.dp)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val titleStyle=quotaTextStyle(11).copy(fontWeight=FontWeight.SemiBold)
        val summaryStyle=quotaTextStyle(9).copy(fontSize=9.5.sp,lineHeight=11.875.sp)
        val density=LocalDensity.current
        val natural=with(density) {(rememberDesktopLabelWidths(listOf(title),titleStyle).single()+rememberDesktopLabelWidths(listOf(summary),summaryStyle).single()).toDp()}+73.dp
        val stacked=natural>maxWidth
        Row(Modifier.fillMaxWidth().heightIn(min=LocalDesktopSettingsRowHeight.current).settingsHeaderPosition(anchor,id,motion).clickable(role=Role.Button,onClick=toggleWithAnchor).padding(horizontal=11.dp,vertical=6.dp).testTag(tag).semantics {stateDescription=description},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(icon),null,Modifier.size(15.dp),tint=if(expanded) colors.primary else colors.onSurfaceVariant.copy(alpha=.85f))
            if(stacked) Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title,Modifier.testTag("$tag-title"),style=titleStyle)
                Text(summary,Modifier.testTag("$tag-summary"),style=summaryStyle,color=colors.onSurfaceVariant)
            } else {
                Text(title,Modifier.weight(1f).testTag("$tag-title"),style=titleStyle)
                Text(summary,Modifier.testTag("$tag-summary"),style=summaryStyle,color=colors.onSurfaceVariant,textAlign=TextAlign.End)
            }
            val chevron=colors.onSurfaceVariant
            Canvas(Modifier.size(12.dp).graphicsLayer {rotationZ=180f*motion.pose.value.chevronFraction}) {
                val p=Path();val mid=size.width/2;val rise=1f
                p.moveTo(mid-2.5.dp.toPx(),size.height/2-rise*1.25.dp.toPx());p.lineTo(mid,size.height/2+rise*1.25.dp.toPx());p.lineTo(mid+2.5.dp.toPx(),size.height/2-rise*1.25.dp.toPx())
                drawPath(p,chevron,style=Stroke(1.5.dp.toPx()))
            }
        }
        }
        DesktopAccordion(expanded,"settings-section-motion-$id",kind=DesktopDisclosureKind.SETTINGS_SECTION,state=motion) {
            HorizontalDivider(color=LocalDesktopLineColor.current.copy(alpha=.08f),thickness=1.dp)
            saved.SaveableStateProvider(id) {Column(Modifier.fillMaxWidth().padding(horizontal=11.dp,vertical=6.dp).testTag("settings-details-$id"),verticalArrangement=Arrangement.spacedBy(4.dp),content=content)}
        }
    }
}

@Composable internal fun DesktopSettingChoices(title:String,choices:List<Pair<String,String>>,selected:String,tag:String,change:(String)->Unit) {
    Text(title,style=quotaTextStyle(11))
    FlowRow(Modifier.fillMaxWidth().selectableGroup(),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
        choices.forEach {(id,label)->DesktopChoicePill(label,selected==id,"$tag-$id") {change(id)}}
    }
}

/** Original settings-item label/control row, wrapping only when Android text no longer fits. */
@Composable internal fun DesktopSettingInlineChoices(title:String,choices:List<Pair<String,String>>,selected:String,tag:String,change:(String)->Unit) {
    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(0.dp)) {
        Box(Modifier.heightIn(min=LocalDesktopSettingsRowHeight.current),contentAlignment=Alignment.CenterStart) {Text(title,style=quotaTextStyle(11))}
        FlowRow(Modifier.selectableGroup(),horizontalArrangement=Arrangement.spacedBy(2.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            choices.forEach {(id,label)->DesktopChoicePill(label,selected==id,"$tag-$id") {change(id)}}
        }
    }
}

@Composable internal fun DesktopSettingSelect(title:String,choices:List<Pair<String,String>>,selected:String,tag:String,stacked:Boolean=false,change:(String)->Unit) {
    var menu by remember {mutableStateOf(false)}
    var menuWidth by remember {mutableStateOf(200.dp)}
    val density=LocalDensity.current;val strings=LocalResources.current
    val colors=MaterialTheme.colorScheme
    val style=quotaTextStyle(11)
    val selectedLabel=choices.firstOrNull {it.first==selected}?.second ?: selected
    val labelWidths=rememberDesktopLabelWidths(choices.map {it.second}+selectedLabel,style)
    val titleWidth=rememberDesktopLabelWidths(listOf(title),style).single()
    val naturalControlWidth=with(density) {
        maxOf(48.dp.roundToPx(),(labelWidths.maxOrNull() ?: 0)+2*8.dp.roundToPx()+8.dp.roundToPx()+10.dp.roundToPx()).toDp()
    } // Round each actual padding edge, gap and disclosure before adding them.
    val control:@Composable (Modifier)->Unit={modifier->
        Box(modifier.onSizeChanged {menuWidth=with(density) {it.width.toDp()}}) {
            Row(Modifier.fillMaxWidth().heightIn(min=LocalDesktopSettingsRowHeight.current).clickable(role=Role.Button,onClick={menu=true}).testTag("$tag-select").semantics {stateDescription=strings.getString(if(menu) R.string.desktop_collapse else R.string.desktop_expand)},verticalAlignment=Alignment.CenterVertically) {
                Row(Modifier.fillMaxWidth().border(1.dp,desktopEditLine(),androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).background(LocalDesktopSunkenColor.current.copy(alpha=DesktopEditTokens.sunken),androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).padding(horizontal=8.dp,vertical=7.dp).testTag("$tag-visual"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(selectedLabel,Modifier.weight(1f).testTag("$tag-value"),style=style,color=colors.onSurface)
                    DesktopDisclosure(menu)
                }
            }
            DesktopOptionMenu(menu,choices.map {(id,label)->DesktopMenuOption(id,label,"$tag-$id")},selected, "$tag-menu",width=menuWidth,compactSettings=true,dismiss={menu=false}) {id->change(id);menu=false}
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("$tag-row")) {
        val availableWidth=maxWidth
        val canStayInline=!stacked && with(density) {titleWidth+8.dp.roundToPx()+naturalControlWidth.roundToPx()<=availableWidth.roundToPx()}
        if(canStayInline) Row(Modifier.fillMaxWidth().heightIn(min=LocalDesktopSettingsRowHeight.current),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(title.isNotEmpty()) Text(title,Modifier.weight(1f).testTag("$tag-title"),style=style)
            control(Modifier.width(naturalControlWidth.coerceAtMost(availableWidth)))
        } else Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            if(title.isNotEmpty()) Text(title,Modifier.fillMaxWidth().testTag("$tag-title"),style=style,color=colors.onSurfaceVariant)
            control(Modifier.fillMaxWidth())
        }
    }
}

@Composable internal fun DesktopSettingSwitch(label:String,checked:Boolean,tag:String,enabled:Boolean=true,change:(Boolean)->Unit) {
    val colors=MaterialTheme.colorScheme
    val sunken=LocalDesktopSunkenColor.current.copy(alpha=.55f)
    val pose=rememberSettingSwitchMotion(checked,enabled,colors.primary.copy(alpha=.88f),sunken,colors.primary.copy(alpha=.5f),colors.onSurface.copy(alpha=.16f),colors.surfaceContainer,colors.onSurfaceVariant)
    Row(Modifier.fillMaxWidth().heightIn(min=LocalDesktopSettingsRowHeight.current).toggleable(checked,enabled=enabled,role=Role.Checkbox,onValueChange=change).testTag(tag).semantics {this[SettingSwitchPosition]=pose.position;this[SettingSwitchPlaying]=pose.running},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label,Modifier.weight(1f),style=quotaTextStyle(11))
        Canvas(Modifier.size(34.dp,20.dp).alpha(if(enabled) 1f else .38f)) {
            val shape=androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
            drawRoundRect(pose.background,cornerRadius=shape)
            drawRoundRect(pose.border,cornerRadius=shape,style=Stroke(1.dp.toPx()))
            drawCircle(pose.thumb,radius=7.dp.toPx(),center=Offset((10f+14f*pose.position).dp.toPx(),10.dp.toPx()))
        }
    }
}

@Composable internal fun SubscriptionMonthlyTotal(records:List<Subscription>,today:LocalDate) {
    if(records.isEmpty()) return
    val strings=LocalResources.current;val currency=LocalUsageCurrency.current
    val monthly=SubscriptionDisplay.monthlyUsd(records,today,currency)?.let(currency::format) ?: "—"
    Text(strings.getString(R.string.settings_subscription_total,monthly),Modifier.fillMaxWidth().padding(top=8.dp).testTag("subscription-monthly-total"),style=quotaTextStyle(11),textAlign=TextAlign.End)
}
