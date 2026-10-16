package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class SyncSettingsPresentationTest {
    @Test fun exactPhoneModelMappingDoesNotRenameOtherDevices() {
        assertEquals("Xiaomi 13 Ultra",SyncSettingsPresentation.phoneModel("2304FPN60C"))
        assertEquals("Xiaomi 13 Ultra",SyncSettingsPresentation.phoneModel("2304FPN6DC"))
        assertEquals("2304FPN60G",SyncSettingsPresentation.phoneModel("2304FPN60G"))
        assertEquals("Pixel 10",SyncSettingsPresentation.phoneModel("Pixel 10"))
    }
    @Test fun actualHubEndpointChangesNameWithoutGuessingHostFromCollectors() {
        val state=HubState(lastSuccessfulEndpoint="https://previous.example.invalid",connectedEndpoint="https://current.example.invalid")
        assertEquals("current.example.invalid",SyncSettingsPresentation.hubHostName(state))
        assertEquals("previous.example.invalid",SyncSettingsPresentation.hubHostName(state.copy(connectedEndpoint=null)))
        assertEquals("new.example.invalid",SyncSettingsPresentation.hubHostName(state.copy(connectedEndpoint="https://new.example.invalid")))
        assertNull(SyncSettingsPresentation.hubHostName(HubState()))
        assertNull(SyncSettingsPresentation.hubHostName(HubState(connectedEndpoint="http://192.168.31.12:17321")))
        assertNull(SyncSettingsPresentation.hubHostName(HubState(connectedEndpoint="http://[2001:db8::1]:17321")))
        assertNull(SyncSettingsPresentation.hubHostName(HubState(connectedEndpoint="invalid",lastSuccessfulEndpoint="https://previous.example.invalid")))
    }
    private val secure="https://public.example.invalid"
    private val local="http://192.168.1.1:17321"
    @Test fun actualReceiptWinsOverConfiguredOrderAndOfflineRetainsOnlyPrevious() {
        val connected=HubState(lastSuccessfulEndpoint=local,connectedEndpoint=local)
        val rows=SyncSettingsPresentation.endpoints(listOf(secure,local),connected)
        assertEquals(listOf("https","http"),rows.map {it.protocol})
        assertEquals(SyncEndpointStatus.CONFIGURED,rows[0].status)
        assertEquals(SyncEndpointStatus.CURRENT,rows[1].status)
        val offline=SyncSettingsPresentation.endpoints(listOf(secure,local),connected.copy(connectedEndpoint=null,error=1))
        assertEquals(SyncEndpointStatus.PREVIOUS,offline[1].status)
        assertFalse(offline.any {it.status==SyncEndpointStatus.CURRENT})
        assertTrue(SyncSettingsPresentation.endpoints(listOf(local,secure),HubState()).all {it.status==SyncEndpointStatus.CONFIGURED})
    }
    @Test fun missingProtocolIsNeverInventedAndAuthenticationPartsNeverDisplay() {
        val rows=SyncSettingsPresentation.endpoints(listOf(secure),HubState())
        assertNull(rows[1].address)
        assertEquals("https://example.invalid:9443",ConnectionDisplay.address("https://fixture-user:fixture-value@example.invalid:9443/private?key=fixture#fixture"))
        assertNull(ConnectionDisplay.address("file:///fixture"))
        assertNull(ConnectionDisplay.address("not a URL"))
        assertFalse(SyncSettingsPresentation.endpoints(listOf(secure),HubState(lastSuccessfulEndpoint=local,connectedEndpoint=local)).any {it.status!=SyncEndpointStatus.CONFIGURED})
    }
    @Test fun realOptionalCollectorFieldsKeepNamesPlatformsRuntimeAndMissingValues() {
        val full=HubParser.parse("""{"periods":{"today":{}},"devices":[{"deviceId":"mac-fixture","hostname":"fixture-host","displayName":"Fixture Mac","platform":"darwin","osName":"macOS","osVersion":"27.0.1","agentRuntime":"electron-widget","agentVersion":"0.70.0","stale":false,"updatedAt":"2026-10-14T04:00:00Z","receivedAt":"2026-10-14T04:00:01Z"},{"deviceId":"agent-fixture","platform":"linux","osName":"Debian GNU/Linux","osVersion":"12","agentRuntime":"headless-agent","agentVersion":"0.70.0-01"},{"deviceId":"old-fixture"}]}""")
        assertEquals("Fixture Mac",DeviceDisplay.name(full.devices[0]))
        assertEquals("macOS 27.0.1",SyncSettingsPresentation.platform(full.devices[0]))
        assertEquals("Widget v0.70.0",SyncSettingsPresentation.version(full.devices[0]))
        assertEquals("2026-10-14T04:00:01Z",SyncSettingsPresentation.syncedAt(full.devices[0]))
        assertEquals(full.devices[0].updated,SyncSettingsPresentation.syncedAt(full.devices[0].copy(received="invalid-date")))
        assertEquals(full.devices[0].updated,SyncSettingsPresentation.syncedAt(full.devices[0].copy(received="")))
        assertNull(SyncSettingsPresentation.syncedAt(full.devices[0].copy(received="invalid-date",updated="invalid-date")))
        assertEquals("Debian GNU/Linux 12",SyncSettingsPresentation.platform(full.devices[1]))
        assertEquals("Agent v0.70.0-01",SyncSettingsPresentation.version(full.devices[1]))
        assertNull(SyncSettingsPresentation.platform(full.devices[2]))
        assertNull(SyncSettingsPresentation.version(full.devices[2]))
        assertNull(full.devices[2].stale)
        assertEquals(listOf("mac-fixture","agent-fixture","old-fixture"),SyncSettingsPresentation.devices(full.devices).map {it.id})
        val fresh=HubParser.applyFreshness(full,kotlinx.serialization.json.Json.parseToJsonElement("""{"updatedAt":"2026-10-14T04:00:02Z","devices":[{"updatedAt":"2026-10-14T04:00:02Z","receivedAt":"2026-10-14T04:00:02Z","stale":true},{},{ }]}""").let {it as kotlinx.serialization.json.JsonObject})
        assertEquals("Widget v0.70.0",SyncSettingsPresentation.version(fresh.devices[0]))
        assertTrue(fresh.devices[0].stale!!)
    }
}
