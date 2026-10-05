package io.github.theminionooo.tokenmonitor.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HubAddressValidatorTest {
    @Test
    fun `tailscale address is accepted by default`() {
        val result = HubAddressValidator.validate(
            rawUrl = "http://100.101.102.103:17321/",
            secret = "redacted",
            allowLocalNetwork = false,
        )

        assertTrue(result is HubAddressValidation.Allowed)
        assertEquals(
            "http://100.101.102.103:17321",
            (result as HubAddressValidation.Allowed).connection.baseUrl,
        )
    }

    @Test
    fun `a bare address gains the scheme and default port`() {
        val bare = HubAddressValidator.validate(" 100.101.102.103 ", "redacted", false)
        val withPort = HubAddressValidator.validate("100.101.102.103:17321/", "redacted", false)
        val home = HubAddressValidator.validate("192.168.1.20", "redacted", true)

        assertEquals("http://100.101.102.103:17321", (bare as HubAddressValidation.Allowed).connection.baseUrl)
        assertEquals("http://100.101.102.103:17321", (withPort as HubAddressValidation.Allowed).connection.baseUrl)
        assertEquals("http://192.168.1.20:17321", (home as HubAddressValidation.Allowed).connection.baseUrl)
        assertTrue(HubAddressValidator.validate("", "redacted", false) is HubAddressValidation.Rejected)
        assertTrue(HubAddressValidator.validate("not an address", "redacted", true) is HubAddressValidation.Rejected)
    }

    @Test
    fun `magic dns tailscale name is accepted`() {
        val result = HubAddressValidator.validate(
            rawUrl = "https://studio.tailnet-name.ts.net",
            secret = "redacted",
            allowLocalNetwork = false,
        )

        assertTrue(result is HubAddressValidation.Allowed)
    }

    @Test
    fun `local wifi requires explicit opt in`() {
        val refused = HubAddressValidator.validate("http://192.168.1.44:17321", "redacted", false)
        val allowed = HubAddressValidator.validate("http://192.168.1.44:17321", "redacted", true)

        assertTrue(refused is HubAddressValidation.Rejected)
        assertTrue(allowed is HubAddressValidation.Allowed)
    }

    @Test
    fun `public http and api path targets are rejected`() {
        assertTrue(
            HubAddressValidator.validate("http://example.com", "redacted", false) is HubAddressValidation.Rejected,
        )
        assertTrue(
            HubAddressValidator.validate("http://100.64.1.2:17321/api/stats", "redacted", false) is HubAddressValidation.Rejected,
        )
    }

    @Test
    fun `public https defaults to 443 and preserves explicit ports`() {
        val cloud = HubAddressValidator.validate(" https://usage.workers.dev/ ", "synthetic", false)
        assertEquals("https://usage.workers.dev:443", (cloud as HubAddressValidation.Allowed).connection.baseUrl)
        val custom = HubAddressValidator.validate("https://hub.example.com:8443", "synthetic", false)
        assertEquals("https://hub.example.com:8443", (custom as HubAddressValidation.Allowed).connection.baseUrl)
        assertTrue(HubAddressValidator.validate("hub.example.com", "synthetic", false) is HubAddressValidation.Rejected)
        assertEquals("HTTPS Hub", HubAddressValidator.routeLabel("https://usage.workers.dev:443"))
        assertEquals("HTTPS Hub", HubAddressValidator.routeLabel("https://hub.example.com:443"))
    }

    @Test
    fun `fallback remains private even with https`() {
        assertTrue(HubAddressValidator.validate("https://hub.example.com", "synthetic", true, allowPublicHttps = false) is HubAddressValidation.Rejected)
        assertTrue(HubAddressValidator.validate("https://192.168.1.20", "synthetic", true, allowPublicHttps = false) is HubAddressValidation.Allowed)
        assertTrue(HubAddressValidator.validate("https://192.168.1.20", "synthetic", false) is HubAddressValidation.Rejected)
    }

    @Test
    fun `unsafe URL and secret contents are rejected rather than repaired`() {
        listOf("https://exa mple.com", "https://hub.example.com/api/stats", "https://hub.example.com//",
            "https://user:password@hub.example.com", "https://hub.example.com?secret=x", "https://hub.example.com#fragment",
            "http://100.64.999.1", "http://192.168.1.999", "http://10.-1.2.3", "https://hub.example.com:0").forEach {
            assertTrue(it, HubAddressValidator.validate(it, "synthetic", true) is HubAddressValidation.Rejected)
        }
        assertTrue(HubAddressValidator.validate("https://hub.example.com", "synthetic\nheader", false) is HubAddressValidation.Rejected)
    }

    @Test
    fun `routes are named for the header`() {
        assertEquals("Tailscale", HubAddressValidator.routeLabel("http://100.101.102.103:17321"))
        assertEquals("Tailscale", HubAddressValidator.routeLabel("http://desk.tail1234.ts.net:17321"))
        assertEquals("Home Wi-Fi", HubAddressValidator.routeLabel("http://192.168.1.20:17321", "http://192.168.1.20:17321"))
        assertEquals("Private network", HubAddressValidator.routeLabel("http://192.168.1.20:17321"))
        assertEquals("Hub", HubAddressValidator.routeLabel(null))
    }
}
