package dev.bing.tokenmonitor

/** A connection receipt describes a completed request, never a pending preference or a draft. */
internal data class SyncEndpointRow(val protocol:String,val address:String?,val status:SyncEndpointStatus)
internal enum class SyncEndpointStatus { CONFIGURED, CURRENT, PREVIOUS }

internal object SyncSettingsPresentation {
    // Mac syncDevicePanel.syncedAt uses Hub receipt time for remote collection devices.
    fun syncedAt(device:Device):String? = listOfNotNull(device.received,device.updated).firstOrNull {SessionDisplay.timestamp(it)!=null}
    fun devices(devices:List<Device>):List<Device> = devices.sortedWith(
        compareBy<Device> {when(it.stale) {false->0;null->1;true->2}}
            .thenByDescending {SessionDisplay.timestamp(syncedAt(it)) ?: 0L}.thenBy {it.id})
    fun endpoints(configured:List<String>,state:HubState):List<SyncEndpointRow> = listOf("https","http").map {scheme->
        val raw=configured.firstOrNull {it.startsWith("$scheme://",ignoreCase=true)}
        SyncEndpointRow(scheme,ConnectionDisplay.address(raw),when {
            raw!=null && raw==state.connectedEndpoint -> SyncEndpointStatus.CURRENT
            raw!=null && raw==state.lastSuccessfulEndpoint -> SyncEndpointStatus.PREVIOUS
            else -> SyncEndpointStatus.CONFIGURED
        })
    }
    fun runtime(device:Device):String? = when(device.agentRuntime?.trim()) {
        "electron-widget"->"Widget"
        "headless-agent"->"Agent"
        else->device.agentRuntime?.trim()?.takeIf(String::isNotEmpty)
    }
    fun version(device:Device):String? = device.agentVersion?.trim()?.takeIf(String::isNotEmpty)?.let {
        listOfNotNull(runtime(device),"v${it.removePrefix("v")}").joinToString(" ")
    }
    fun platform(device:Device):String? = DeviceDisplay.platform(device).trim().takeIf(String::isNotEmpty)
}
