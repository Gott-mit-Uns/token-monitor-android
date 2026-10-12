package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException

class HubConnectionPolicyTest {
    private val local = "http://192.168.1.2:3000"
    private val secure = "https://hub.example.invalid"
    @Test fun confirmedLanIsPreferredOnlyWhenPermittedAndNotCoolingDown() {
        assertEquals(local, HubConnectionPolicy.preferred(secure,local,true,true))
        assertEquals(secure, HubConnectionPolicy.preferred(secure,local,true,false))
        assertEquals(secure, HubConnectionPolicy.preferred(local,secure,true,true,true))
    }
    @Test fun unconfirmedEndpointCannotAutomaticallyReplaceTheAuthority() {
        assertEquals(secure, HubConnectionPolicy.preferred(secure,local,false,true))
        assertNull(HubConnectionPolicy.preferred(local,secure,false,false))
        assertNull(HubConnectionPolicy.fallback(secure,local,false,true,HubConnectionPolicy.Failure.NETWORK))
    }
    @Test fun networkFailureHasOneAllowedAlternateInEitherDirection() {
        assertEquals(secure,HubConnectionPolicy.fallback(local,secure,true,true,HubConnectionPolicy.Failure.NETWORK))
        assertEquals(local,HubConnectionPolicy.fallback(secure,local,true,true,HubConnectionPolicy.Failure.NETWORK))
        assertNull(HubConnectionPolicy.fallback(secure,local,true,false,HubConnectionPolicy.Failure.NETWORK))
    }
    @Test fun authenticationTlsProtocolAndCancellationNeverFallback() {
        for (failure in HubConnectionPolicy.Failure.entries.filter {it != HubConnectionPolicy.Failure.NETWORK})
            assertNull(HubConnectionPolicy.fallback(local,secure,true,true,failure))
    }
    @Test fun missingEntriesAndNoNetworkCannotInventAnEndpoint() {
        assertEquals(secure,HubConnectionPolicy.preferred(secure,null,true,false))
        assertEquals(local,HubConnectionPolicy.preferred(local,null,true,true))
        assertNull(HubConnectionPolicy.preferred(local,null,true,false))
        assertNull(HubConnectionPolicy.fallback(secure,null,true,true,HubConnectionPolicy.Failure.NETWORK))
    }
    @Test fun errorsAreClassifiedWithoutPrivateMessageInspection() {
        assertEquals(HubConnectionPolicy.Failure.NETWORK,HubConnectionPolicy.classify(SocketTimeoutException()))
        assertEquals(HubConnectionPolicy.Failure.TLS,HubConnectionPolicy.classify(IOException(SSLHandshakeException("synthetic"))))
        assertEquals(HubConnectionPolicy.Failure.AUTH,HubConnectionPolicy.classify(HubReadException(0,true)))
        assertEquals(HubConnectionPolicy.Failure.PROTOCOL,HubConnectionPolicy.classify(IllegalArgumentException()))
        assertEquals(HubConnectionPolicy.Failure.CANCELLED,HubConnectionPolicy.classify(java.util.concurrent.CancellationException()))
    }
}
