package io.github.theminionooo.tokenmonitor.data.network

import io.github.theminionooo.tokenmonitor.domain.HubConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class EndpointFailoverTest {
    private val tailscale = "http://100.64.0.9:17321"
    private val home = "http://192.168.1.20:17321"
    private val connection = HubConnection(tailscale, "secret", allowLocalNetwork = true, fallbackUrl = home)

    @Test
    fun `the address that answered last is tried first`() {
        assertEquals(listOf(tailscale, home), EndpointFailover.candidates(connection, preferred = null))
        assertEquals(listOf(home, tailscale), EndpointFailover.candidates(connection, preferred = home))
        assertEquals(listOf(tailscale, home), EndpointFailover.candidates(connection, preferred = "http://stale.example"))
    }

    @Test
    fun `without a fallback only the primary address is used`() {
        val single = connection.copy(fallbackUrl = null)
        assertEquals(listOf(tailscale), EndpointFailover.candidates(single, preferred = home))
        assertEquals(listOf(tailscale), EndpointFailover.candidates(connection.copy(fallbackUrl = tailscale), preferred = null))
    }

    @Test
    fun `an unreachable primary moves over to the home address`() {
        val attempts = mutableListOf<String>()
        val (url, result) = EndpointFailover.run(listOf(tailscale, home)) { candidate ->
            attempts += candidate
            if (candidate == tailscale) throw IOException("no route") else "snapshot"
        }
        assertEquals(home, url)
        assertEquals("snapshot", result)
        assertEquals(listOf(tailscale, home), attempts)
    }

    @Test
    fun `a rejected secret is not retried on another address`() {
        val rejected = HubApiException(401, "rejected")
        val attempts = mutableListOf<String>()
        try {
            EndpointFailover.run(listOf(tailscale, home)) { candidate ->
                attempts += candidate
                throw rejected
            }
            fail("expected the secret rejection to surface")
        } catch (error: HubApiException) {
            assertSame(rejected, error)
        }
        assertEquals(listOf(tailscale), attempts)
    }

    @Test
    fun `forbidden hub credentials do not trigger endpoint cycling`() {
        val attempts = mutableListOf<String>()
        try {
            EndpointFailover.run(listOf(tailscale, home)) { candidate ->
                attempts += candidate
                throw HubApiException(403, "forbidden")
            }
            fail("expected forbidden response")
        } catch (error: HubApiException) { assertEquals(403, error.statusCode) }
        assertEquals(listOf(tailscale), attempts)
    }

    @Test
    fun `the first failure is reported when every address fails`() {
        val first = IOException("first")
        try {
            EndpointFailover.run(listOf(tailscale, home)) { candidate ->
                throw if (candidate == tailscale) first else IOException("second")
            }
            fail("expected a failure")
        } catch (error: IOException) {
            assertSame(first, error)
        }
    }
}
