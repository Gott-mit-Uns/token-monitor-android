package io.github.theminionooo.tokenmonitor.data.network

import io.github.theminionooo.tokenmonitor.domain.HubConnection
import org.junit.Assert.*
import org.junit.Test

class LocalNetworkAccessTest {
    private val saved = HubConnection("http://100.100.100.100:17321", "synthetic-secret", false, "http://192.168.1.2:17321")

    @Test fun `denied LAN keeps Tailscale reachable even when home answered last`() {
        assertEquals(listOf(saved.baseUrl), LocalNetworkAccess.allowedRoutes(saved, saved.fallbackUrl, false))
        assertEquals(listOf(saved.fallbackUrl, saved.baseUrl), LocalNetworkAccess.allowedRoutes(saved, saved.fallbackUrl, true))
    }

    @Test fun `LAN only pairing has no permitted route after revocation`() {
        assertTrue(LocalNetworkAccess.allowedRoutes(saved.copy(baseUrl = saved.fallbackUrl!!, fallbackUrl = null), null, false).isEmpty())
        assertTrue(HubAddressValidator.isLocalAddress("desktop.local"))
        assertFalse(HubAddressValidator.isLocalAddress("desktop.tailnet.ts.net"))
        assertFalse(HubAddressValidator.isLocalAddress("100.100.100.100"))
        assertFalse(HubAddressValidator.isLocalAddress("192.168.999.2"))
    }

    @Test fun `repair preserves original primary secret and policy`() {
        val repaired = (prepareHomeAddressRepair(saved, "192.168.1.9") as HubAddressValidation.Allowed).connection
        assertEquals(saved.baseUrl, repaired.baseUrl)
        assertEquals(saved.secret, repaired.secret)
        assertEquals(saved.allowLocalNetwork, repaired.allowLocalNetwork)
        assertEquals("http://192.168.1.9:17321", repaired.fallbackUrl)
    }

    @Test fun `repair refuses a public host tailscale address and missing pairing`() {
        listOf("https://example.com", "100.100.100.101", "http://192.168.1.9/api/stats").forEach {
            assertTrue(prepareHomeAddressRepair(saved, it) is HubAddressValidation.Rejected)
        }
        assertTrue(prepareHomeAddressRepair(null, "192.168.1.9") is HubAddressValidation.Rejected)
    }
}
