package dev.bing.tokenmonitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Read-only phone, Hub and uploaded collectors keep separate identities and status meanings. */
@Composable internal fun HubSyncDevices(state:HubState,now:Long=LocalHubCalendarClock.current.instant().toEpochMilli()) {
    val strings=LocalResources.current;val colors=MaterialTheme.colorScheme
    val connected=state.connectedEndpoint!=null
    Column(Modifier.fillMaxWidth().testTag("sync-device-information"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color=colors.outlineVariant)
        Text(strings.getString(R.string.sync_devices_heading),style=quotaTextStyle(11,true))
        // Share the collectors' card geometry; natural row height keeps enlarged text complete.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("sync-infrastructure-grid").semantics {
            collectionInfo=CollectionInfo(1,2)
        },horizontalArrangement=Arrangement.spacedBy(CompactUiTokens.SectionSpacing)) {
            SyncDeviceCard(Modifier.weight(1f).fillMaxHeight().testTag("sync-viewer-information").semantics {
                collectionItemInfo=CollectionItemInfo(0,1,0,1)
            }) {
                SyncCardTitle(strings.getString(R.string.sync_viewer_role),painterResource(R.drawable.desktop_view_device),Modifier.testTag("sync-viewer-role"))
                Text("${SyncSettingsPresentation.phoneModel(android.os.Build.MODEL)} · Android ${android.os.Build.VERSION.RELEASE}",Modifier.fillMaxWidth().testTag("sync-viewer-model"),style=quotaTextStyle(10),color=colors.onSurfaceVariant)
                Text(strings.getString(R.string.sync_viewer_note),Modifier.fillMaxWidth().testTag("sync-viewer-note"),style=quotaTextStyle(10),color=colors.onSurfaceVariant)
            }
            SyncDeviceCard(Modifier.weight(1f).fillMaxHeight().testTag("sync-hub-information").semantics {
                collectionItemInfo=CollectionItemInfo(0,1,1,1)
            }) {
                val build=state.hubBuild
                val runtime=when(build?.runtime) {"node-hub"->"Node Hub";"cloudflare-worker"->"Cloudflare Worker";else->"Hub"}
                SyncCardTitle(runtime,painterResource(R.drawable.desktop_view_device))
                val host=SyncSettingsPresentation.hubHostName(state) ?: strings.getString(R.string.sync_hub_name_unknown)
                Text(strings.getString(R.string.sync_hub_destination,host),Modifier.fillMaxWidth().testTag("sync-hub-destination"),style=quotaTextStyle(10),color=colors.onSurfaceVariant)
                val revisions=if(build?.coreRevision!=null && build.runtimeRevision!=null) strings.getString(R.string.hub_build_version,runtime,build.coreRevision,build.runtimeRevision) else strings.getString(R.string.hub_version_unknown)
                Text(revisions,Modifier.fillMaxWidth(),style=quotaTextStyle(10),color=colors.onSurfaceVariant)
                ConnectionDisplay.address(state.connectedEndpoint ?: state.lastSuccessfulEndpoint)?.let {
                    Text(it,Modifier.fillMaxWidth().testTag("sync-hub-address"),style=quotaTextStyle(10),color=colors.onSurfaceVariant)
                }
                val status=when {
                    connected->R.string.sync_hub_connected
                    state.lastSuccessfulEndpoint!=null && (state.error!=null || state.connectionFailure!=null)->R.string.sync_hub_offline
                    state.lastSuccessfulEndpoint!=null->R.string.sync_hub_cached
                    else->R.string.sync_hub_unconnected
                }
                Text(strings.getString(status),Modifier.fillMaxWidth().testTag("sync-hub-status"),style=quotaTextStyle(10),color=colors.onSurfaceVariant)
            }
        }
        Text(strings.getString(R.string.sync_collectors_heading),style=quotaTextStyle(11,true))
        val devices=SyncSettingsPresentation.devices(state.snapshot?.devices.orEmpty())
        if(devices.isEmpty()) Text(strings.getString(R.string.sync_devices_empty),Modifier.testTag("sync-devices-empty"),style=quotaTextStyle(10),color=colors.onSurfaceVariant)
        Column(Modifier.fillMaxWidth().testTag("sync-device-grid").semantics {
            collectionInfo=CollectionInfo((devices.size+1)/2,2)
        },verticalArrangement=Arrangement.spacedBy(CompactUiTokens.SectionSpacing)) {
        devices.chunked(2).forEachIndexed {rowIndex,pair->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("sync-device-row-$rowIndex"),
                horizontalArrangement=Arrangement.spacedBy(CompactUiTokens.SectionSpacing)) {
            pair.forEachIndexed {columnIndex,device->
                val key=device.id.ifEmpty {(rowIndex*2+columnIndex).toString()}
                SyncDeviceCard(Modifier.weight(1f).fillMaxHeight().testTag("sync-device-$key").semantics {
                    collectionItemInfo=CollectionItemInfo(rowIndex,1,columnIndex,1)
                }) {
                val icon=DeviceDisplay.icon(device.rawPlatform ?: device.platform.orEmpty())
                SyncCardTitle(DeviceDisplay.name(device,state.deviceAliases[device.id],strings.getString(R.string.unnamed_device)),
                    painterResource(when(icon) {"apple"->R.drawable.brand_os_apple;"windows"->R.drawable.brand_os_windows;"linux"->R.drawable.brand_os_linux;else->R.drawable.desktop_view_device}),
                    Modifier.testTag("sync-device-name-$key"))
                Text(SyncSettingsPresentation.platform(device) ?: strings.getString(R.string.not_provided),Modifier.fillMaxWidth().testTag("sync-device-platform-$key"),style=quotaTextStyle(10),color=colors.onSurfaceVariant)
                Text(SyncSettingsPresentation.version(device) ?: listOfNotNull(SyncSettingsPresentation.runtime(device),strings.getString(R.string.sync_version_missing)).joinToString(" · "),Modifier.fillMaxWidth().testTag("sync-device-version-$key"),style=quotaTextStyle(10),color=colors.onSurfaceVariant)
                val status=strings.getString(when(device.stale) {
                    false->if(connected) R.string.sync_device_online else R.string.sync_device_online_cached
                    true->if(connected) R.string.sync_device_offline else R.string.sync_device_offline_cached
                    null->R.string.sync_device_unknown
                })
                val age=DeviceDisplay.age(SyncSettingsPresentation.syncedAt(device),now)?.let {(unit,value)->strings.getString(when(unit) {"minutes"->R.string.device_age_minutes;"hours"->R.string.device_age_hours;"days"->R.string.device_age_days;else->R.string.device_age_now},value)}
                Text("$status · ${age?.let {strings.getString(R.string.device_synced,it)} ?: strings.getString(R.string.sync_time_missing)}",Modifier.fillMaxWidth().testTag("sync-device-status-$key"),style=quotaTextStyle(10),color=colors.onSurfaceVariant)
                }
            }
            // An odd last collector keeps the same column width as every other
            // collector; its empty peer carries no data or accessibility label.
            if(pair.size==1) Spacer(Modifier.weight(1f))
            }
        }
        }
    }
}

/** Same padding, natural height, title hierarchy and border for every device role. */
@Composable private fun SyncDeviceCard(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    val shape=RoundedCornerShape(8.dp)
    Column(modifier.border(1.dp,MaterialTheme.colorScheme.outlineVariant,shape)
        .background(LocalDesktopOverlayColor.current.copy(alpha=.025f),shape)
        .padding(CompactUiTokens.CardContentPadding),verticalArrangement=Arrangement.spacedBy(3.dp),content=content)
}

@Composable private fun SyncCardTitle(title:String,icon:Painter,modifier:Modifier=Modifier) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Icon(icon,null,Modifier.padding(top=1.dp).size(14.dp))
        Text(title,modifier.weight(1f),style=quotaTextStyle(11,true))
    }
}
