package dev.bing.tokenmonitor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Read-only phone, Hub and uploaded collectors keep separate identities and status meanings. */
@Composable internal fun HubSyncDevices(state:HubState,now:Long=LocalHubCalendarClock.current.instant().toEpochMilli()) {
    val strings=LocalResources.current;val colors=MaterialTheme.colorScheme
    val connected=state.connectedEndpoint!=null
    Column(Modifier.fillMaxWidth().testTag("sync-device-information"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color=colors.outlineVariant)
        Text(strings.getString(R.string.sync_devices_heading),style=quotaTextStyle(11,true))
        Column(Modifier.fillMaxWidth().testTag("sync-viewer-information"),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(strings.getString(R.string.sync_viewer_role),style=quotaTextStyle(11,true))
            Text("${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}",style=quotaTextStyle(10),color=colors.onSurfaceVariant)
            Text(strings.getString(R.string.sync_viewer_note),style=quotaTextStyle(10),color=colors.onSurfaceVariant)
        }
        Column(Modifier.fillMaxWidth().testTag("sync-hub-information"),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            val build=state.hubBuild
            val runtime=when(build?.runtime) {"node-hub"->"Node Hub";"cloudflare-worker"->"Cloudflare Worker";else->"Hub"}
            Text(runtime,style=quotaTextStyle(11,true))
            val revisions=if(build?.coreRevision!=null && build.runtimeRevision!=null) strings.getString(R.string.hub_build_version,runtime,build.coreRevision,build.runtimeRevision) else strings.getString(R.string.hub_version_unknown)
            Text(revisions,style=quotaTextStyle(10),color=colors.onSurfaceVariant)
            Text(strings.getString(if(connected) R.string.sync_hub_connected else R.string.sync_hub_cached),style=quotaTextStyle(10),color=colors.onSurfaceVariant)
        }
        Text(strings.getString(R.string.sync_collectors_heading),style=quotaTextStyle(11,true))
        val devices=SyncSettingsPresentation.devices(state.snapshot?.devices.orEmpty())
        if(devices.isEmpty()) Text(strings.getString(R.string.sync_devices_empty),Modifier.testTag("sync-devices-empty"),style=quotaTextStyle(10),color=colors.onSurfaceVariant)
        devices.forEachIndexed {index,device->
            val key=device.id.ifEmpty {index.toString()}
            Column(Modifier.fillMaxWidth().testTag("sync-device-$key"),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    val icon=DeviceDisplay.icon(device.rawPlatform ?: device.platform.orEmpty())
                    Icon(painterResource(when(icon) {"apple"->R.drawable.brand_os_apple;"windows"->R.drawable.brand_os_windows;"linux"->R.drawable.brand_os_linux;else->R.drawable.desktop_view_device}),null,Modifier.padding(top=1.dp).size(14.dp))
                    Text(DeviceDisplay.name(device,state.deviceAliases[device.id],strings.getString(R.string.unnamed_device)),Modifier.weight(1f).testTag("sync-device-name-$key"),style=quotaTextStyle(11,true))
                }
                Text(SyncSettingsPresentation.platform(device) ?: strings.getString(R.string.not_provided),Modifier.fillMaxWidth().testTag("sync-device-platform-$key"),style=quotaTextStyle(10),color=colors.onSurfaceVariant)
                Text(SyncSettingsPresentation.version(device) ?: listOfNotNull(SyncSettingsPresentation.runtime(device),strings.getString(R.string.sync_version_missing)).joinToString(" · "),Modifier.fillMaxWidth().testTag("sync-device-version-$key"),style=quotaTextStyle(10),color=colors.onSurfaceVariant)
                val status=strings.getString(when(device.stale) {
                    false->if(connected) R.string.sync_device_online else R.string.sync_device_online_cached
                    true->if(connected) R.string.sync_device_offline else R.string.sync_device_offline_cached
                    null->R.string.sync_device_unknown
                })
                val age=DeviceDisplay.age(SyncSettingsPresentation.syncedAt(device),now)?.let {(unit,value)->strings.getString(when(unit) {"minutes"->R.string.device_age_minutes;"hours"->R.string.device_age_hours;"days"->R.string.device_age_days;else->R.string.device_age_now},value)}
                Text("$status · ${age?.let {strings.getString(R.string.device_synced,it)} ?: strings.getString(R.string.sync_time_missing)}",Modifier.fillMaxWidth().testTag("sync-device-status-$key"),style=quotaTextStyle(10),color=colors.onSurfaceVariant)
                if(index<devices.lastIndex) HorizontalDivider(Modifier.padding(top=5.dp),color=colors.outlineVariant.copy(alpha=.6f))
            }
        }
    }
}
