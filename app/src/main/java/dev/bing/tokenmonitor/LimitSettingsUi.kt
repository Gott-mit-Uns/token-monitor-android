package dev.bing.tokenmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Original CSS --success, --orange, --yellow and provider tag spacing.
private val settingsSource=Color(0xFF73BDF5)
private val settingsSetup=Color(0xFFF4A073)
private val settingsWarning=Color(0xFFF1D973)

@Composable private fun ProviderTag(label:String,kind:String,tag:String) {
    val colors=MaterialTheme.colorScheme
    val color=when(kind) {"ok"->LocalDesktopSuccessColor.current;"setup"->settingsSetup;"warn","stale"->settingsWarning;"source","remote","sync"->settingsSource;else->colors.onSurfaceVariant}
    val alpha=when(kind) {"setup","warn","stale"->.32f;"source"->.24f;"ok","remote"->.28f;else->.13f}
    val border=if(kind=="muted") LocalDesktopLineColor.current else color
    Text(label,Modifier.testTag(tag).border(1.dp,border.copy(alpha=alpha),RoundedCornerShape(5.dp)).padding(horizontal=5.dp,vertical=1.dp),style=quotaTextStyle(9),color=color,maxLines=1,overflow=TextOverflow.Ellipsis)
}

@Composable internal fun LimitSettingsProviderCopy(id:String,label:String,groups:List<List<Limit>>,masked:Boolean) {
    val locale=LocalResources.current.configuration.locales[0]
    val row=LimitSettingsPresentation.record(groups)
    val status=row?.let(LimitSettingsPresentation::status)
    val detected=row?.let(LimitSettingsPresentation::detected)==true
    Column(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(label,Modifier.weight(1f,false),style=quotaTextStyle(11),color=MaterialTheme.colorScheme.onSurface,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(detected && status!=null) Box(Modifier.size(6.dp).background(LocalDesktopSuccessColor.current,RoundedCornerShape(50)).testTag("limit-provider-detected-$id"))
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(3.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            if(status!=null && !detected) ProviderTag(LimitSettingsPresentation.text(locale,status.label),status.tone,"limit-provider-status-$id")
            if(row!=null && status!=null && (row.status=="ok" || row.stale==true)) {
                LimitDisplay.source(row).takeIf {it.isNotEmpty()}?.let {ProviderTag(it,"source","limit-provider-source-$id")}
                row.presentation.sourceDevice?.takeIf {it.isNotBlank()}?.let {device->
                    val from=LimitSettingsPresentation.text(locale,"From {device}").replace("{device}",LimitDisplay.text(device,masked))
                    ProviderTag(from,"remote","limit-provider-device-$id")
                }
            }
        }
    }
}

@Composable internal fun LimitSettingsAccounts(id:String,groups:List<List<Limit>>,masked:Boolean) {
    val strings=LocalResources.current;val locale=strings.configuration.locales[0]
    val accounts=LimitSettingsPresentation.accounts(groups);val peers=accounts.map {it.first()}
    if(accounts.isNotEmpty()) Column(Modifier.fillMaxWidth().testTag("limit-provider-accounts-$id"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(strings.getString(R.string.limit_reported_accounts),style=quotaTextStyle(10))
        accounts.forEachIndexed {index,group->
            val row=group.first()
            Column(Modifier.fillMaxWidth().testTag("limit-provider-account-$id-$index"),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(LimitDisplay.accountTitle(row,peers,index,masked),style=quotaTextStyle(11),color=MaterialTheme.colorScheme.onSurface)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(3.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    LimitSettingsPresentation.plan(row,masked).takeIf {it.isNotBlank()}?.let {ProviderTag(it,"muted","limit-provider-account-plan-$id-$index")}
                    LimitSettingsPresentation.status(row)?.let {ProviderTag(LimitSettingsPresentation.text(locale,it.label),it.tone,"limit-provider-account-status-$id-$index")}
                    if(row.status=="ok" || row.stale==true) LimitDisplay.source(row).takeIf {it.isNotBlank()}?.let {ProviderTag(it,"source","limit-provider-account-source-$id-$index")}
                }
            }
        }
    }
}
