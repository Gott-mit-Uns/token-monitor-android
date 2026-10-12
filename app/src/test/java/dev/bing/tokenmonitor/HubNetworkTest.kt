package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class HubNetworkTest {
    @Test fun privateHttpRequiresLocalTransportAndMatchingSubnet() {
        val home = HubNetworkScope(true, false, listOf(LocalSubnet("192.168.31.54", 24)))
        assertTrue(home.permitsHttp("http://192.168.31.12:17321"))
        assertFalse(home.permitsHttp("http://192.168.32.12:17321"))
        assertFalse(home.copy(localTransport = false).permitsHttp("http://192.168.31.12"))
        assertFalse(home.copy(vpn = true).permitsHttp("http://192.168.31.12"))
        assertFalse(home.copy(subnets = emptyList()).permitsHttp("http://192.168.31.12"))
    }
    @Test fun prefixesAreBitwiseRatherThanStringMatching() {
        val subnet = HubNetworkScope(true, false, listOf(LocalSubnet("172.16.8.15", 20)))
        assertTrue(subnet.permitsHttp("http://172.16.15.254"))
        assertFalse(subnet.permitsHttp("http://172.16.16.1"))
        assertFalse(subnet.copy(subnets = listOf(LocalSubnet("172.16.8.15", 0))).permitsHttp("http://172.16.8.1"))
    }
    @Test fun pendingAndLostNetworksNeverCarryOverLocalPermission() {
        val home = HubNetworkScope(true, false, listOf(LocalSubnet("192.168.31.54", 24)))
        assertFalse(home.copy(ready = false).permitsHttp("http://192.168.31.12"))
        assertFalse(home.copy(available = false).permitsHttp("http://192.168.31.12"))
    }
}
