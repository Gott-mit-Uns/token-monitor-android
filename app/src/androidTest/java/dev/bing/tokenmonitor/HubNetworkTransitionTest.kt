package dev.bing.tokenmonitor

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.net.Proxy
import java.net.ProxySelector
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.URI
import java.io.IOException

class HubNetworkTransitionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(20)
        assertTrue("Network transition condition was not reached", condition())
    }
    private fun client(trusted: HandshakeCertificates? = null): OkHttpClient {
        val builder = HubViewModel.defaultClient().newBuilder()
        if (trusted != null) builder.sslSocketFactory(trusted.sslSocketFactory(), trusted.trustManager)
        // Synthetic private address maps to this test process only; HTTPS uses a real TLS socket.
        builder.addInterceptor { chain ->
            val request = chain.request()
            chain.proceed(if (request.url.scheme == "http") request.newBuilder().url(request.url.newBuilder().host("127.0.0.1").build()).build() else request)
        }
        return builder.build()
    }
    @Test fun unconfirmedDualStreamsRequireExplicitAuthorityChangeAcrossDefaultNetworks() {
        val generated = UUID.randomUUID().toString()
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trusted = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val http = NetworkFixtureHub(generated)
        val https = NetworkFixtureHub(generated, serverTls)
        val connection = client(trusted)
        val store = HubStore(app, "test-network-${UUID.randomUUID()}.bin")
        val owner = ViewModelStore()
        val localFactory = RecordingSocketFactory()
        val home = fixtureLocalScope().copy(socketFactory = localFactory)
        val secure = "https://127.0.0.1:${https.port}"
        val local = "http://192.168.1.1:${http.port}"
        lateinit var model: HubViewModel
        try {
            main { model = HubViewModel(app, store, connection); owner.put("network", model) }
            await { !model.state.value.loading }
            main { model.updateNetworkScope(home); model.setForeground(true); model.connect(secure, generated, local) }
            await { https.streams.get() == 1 && model.state.value.live }
            main { model.loadHistory() }
            await { model.state.value.history != null && store.load()?.history != null && !model.state.value.loading }
            main { model.switchEndpoint() }
            await { http.streams.get() == 1 && https.closedStreams.get() == 1 && model.state.value.live }
            assertTrue(localFactory.created.get() > 0)
            assertEquals("http", model.endpointType())
            // Unconfirmed alternate is a new authority; old details are not silently mixed.
            assertNull(model.state.value.history)
            main { model.loadHistory() }
            await { model.state.value.history != null && !model.state.value.loading }
            val localReads = http.requests.get()
            val mobile = HubNetworkScope(false, false, emptyList(), networkId = 2L)
            main { model.updateNetworkScope(mobile) }
            await { http.closedStreams.get() == 1 && !model.state.value.live }
            assertEquals(1, https.streams.get()) // No automatic association inferred from configured URLs.
            main { model.switchEndpoint() }
            await { https.streams.get() == 2 && http.closedStreams.get() == 1 && model.state.value.live }
            assertEquals("https", model.endpointType())
            assertNull(model.state.value.history) // Explicitly replaced unconfirmed authority.
            main { model.switchEndpoint() }
            await { !model.state.value.loading && https.streams.get() == 3 && model.state.value.live }
            assertEquals(localReads, http.requests.get())
            // HTTPS was already selected: a different default network still replaces its socket.
            main { model.updateNetworkScope(mobile.copy(networkId = 3L)) }
            await { https.streams.get() == 4 && https.closedStreams.get() == 3 && model.state.value.live }
            main { model.updateNetworkScope(HubNetworkScope(false, false, emptyList(), available = false, ready = false)) }
            await { https.closedStreams.get() == 4 && !model.state.value.live }
            val offlineRequests = https.requests.get()
            main { model.refreshSaved(); model.loadHistory() }
            assertEquals(offlineRequests, https.requests.get())
            assertEquals(123L, model.state.value.snapshot?.periods?.get("today")?.tokens)
            main { model.updateNetworkScope(mobile.copy(networkId = 4L)) }
            await { https.streams.get() == 5 && model.state.value.live }
            main { model.setForeground(false) }
            await { https.closedStreams.get() == 5 }
            val backgroundRequests = https.requests.get()
            main { model.updateNetworkScope(home.copy(networkId = 5L)); model.loadHistory() }
            assertEquals(backgroundRequests, https.requests.get())
            main { model.setForeground(true) }
            await { https.streams.get() == 6 && model.state.value.live }
            assertEquals(0, http.badAuthentication.get()); assertEquals(0, https.badAuthentication.get())
            assertEquals(localReads, http.requests.get())
        } finally {
            main { owner.clear() }
            connection.dispatcher.cancelAll(); connection.connectionPool.evictAll()
            http.close(); https.close(); store.clear()
        }
    }
    @Test fun unknownAndUnboundLocalScopesNeverOpenSocketsAndRecoverWhenReady() {
        val generated = UUID.randomUUID().toString()
        val server = NetworkFixtureHub(generated)
        val connection = client()
        val store = HubStore(app, "test-network-ready-${UUID.randomUUID()}.bin")
        val owner = ViewModelStore()
        val firstFactory = RecordingSocketFactory()
        val secondFactory = RecordingSocketFactory()
        val local = "http://192.168.1.1:${server.port}"
        lateinit var model: HubViewModel
        try {
            main { model = HubViewModel(app, store, connection); owner.put("ready", model) }
            await { !model.state.value.loading }
            main { model.setForeground(true); model.connect(local, generated, "") }
            assertEquals(0, server.requests.get())
            main { model.updateNetworkScope(fixtureLocalScope().copy(ready = false, socketFactory = firstFactory)); model.connect(local, generated, "") }
            assertEquals(0, server.requests.get())
            main { model.updateNetworkScope(fixtureLocalScope().copy(socketFactory = null)); model.connect(local, generated, "") }
            assertEquals(0, server.requests.get())
            main { model.updateNetworkScope(fixtureLocalScope().copy(socketFactory = firstFactory)); model.connect(local, generated, "") }
            await { server.streams.get() == 1 && model.state.value.live }
            assertTrue(firstFactory.created.get() > 0)
            val reads = server.requests.get()
            main { model.updateNetworkScope(fixtureLocalScope(2).copy(ready = false, socketFactory = secondFactory)) }
            await { server.closedStreams.get() == 1 && !model.state.value.live }
            assertEquals(reads, server.requests.get()); assertEquals(0, secondFactory.created.get())
            main { model.updateNetworkScope(fixtureLocalScope(2).copy(socketFactory = secondFactory)) }
            await { server.streams.get() == 2 && model.state.value.live }
            assertTrue(secondFactory.created.get() > 0)
            assertEquals(0, server.badAuthentication.get())
        } finally {
            main { owner.clear() }
            connection.dispatcher.cancelAll(); connection.connectionPool.evictAll(); server.close(); store.clear()
        }
    }
    @Test fun secureFailureNeverDowngradesAndNetworkChangesPreserveAuthenticationStop() {
        val generated = UUID.randomUUID().toString()
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build()
        val https = NetworkFixtureHub(generated, HandshakeCertificates.Builder().heldCertificate(certificate).build())
        val http = NetworkFixtureHub(generated)
        val connection = client(HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build())
        val store = HubStore(app, "test-network-auth-${UUID.randomUUID()}.bin")
        val owner = ViewModelStore()
        lateinit var model: HubViewModel
        try {
            main { model = HubViewModel(app, store, connection); owner.put("auth", model) }
            await { !model.state.value.loading }
            main { model.updateNetworkScope(fixtureLocalScope()); model.setForeground(true)
                model.connect("https://127.0.0.1:${https.port}", generated, "http://192.168.1.1:${http.port}") }
            await { https.streams.get() == 1 && model.state.value.live }
            https.status.set(503)
            main { model.updateNetworkScope(HubNetworkScope(false, false, emptyList(), networkId = 2L)) }
            await { !model.state.value.loading && model.state.value.error != null }
            assertEquals(0, http.requests.get())
            assertEquals("https", model.endpointType())
            assertEquals(123L, model.state.value.snapshot?.periods?.get("today")?.tokens)
            https.status.set(401)
            main { model.refreshSaved() }
            await { !model.state.value.loading && model.state.value.error == R.string.error_auth }
            val blockedReads = https.requests.get()
            main { model.updateNetworkScope(fixtureLocalScope(3)); model.setForeground(false); model.setForeground(true) }
            assertEquals(blockedReads, https.requests.get()); assertEquals(0, http.requests.get())
        } finally {
            main { owner.clear() }
            connection.dispatcher.cancelAll(); connection.connectionPool.evictAll(); https.close(); http.close(); store.clear()
        }
    }
    @Test fun privateHttpUsesItsBoundFactoryAndIgnoresSystemProxy() {
        val generated = UUID.randomUUID().toString()
        val direct = NetworkFixtureHub(generated)
        val proxy = NetworkFixtureHub(generated)
        val connection = client().newBuilder().proxySelector(object : ProxySelector() {
            override fun select(uri: URI) = listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxy.port)))
            override fun connectFailed(uri: URI, address: SocketAddress, error: IOException) {}
        }).build()
        val store = HubStore(app, "test-local-proxy-${UUID.randomUUID()}.bin")
        val owner = ViewModelStore()
        val factory = RecordingSocketFactory()
        lateinit var model: HubViewModel
        try {
            main { model = HubViewModel(app, store, connection); owner.put("proxy", model) }
            await { !model.state.value.loading }
            main { model.updateNetworkScope(fixtureLocalScope().copy(socketFactory = factory)); model.setForeground(true)
                model.connect("http://192.168.1.1:${direct.port}", generated, "") }
            await { direct.streams.get() == 1 && model.state.value.live }
            assertEquals(0, proxy.requests.get())
            assertTrue(factory.created.get() > 0)
            assertEquals(0, direct.badAuthentication.get())
        } finally {
            main { owner.clear() }
            connection.dispatcher.cancelAll(); connection.connectionPool.evictAll(); direct.close(); proxy.close(); store.clear()
        }
    }
    @Test fun localAuthenticationFailureDoesNotRetryOnForegroundOrVpn() {
        val generated = UUID.randomUUID().toString()
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build()
        val https = NetworkFixtureHub(generated, HandshakeCertificates.Builder().heldCertificate(certificate).build())
        val http = NetworkFixtureHub(generated)
        val connection = client(HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build())
        val store = HubStore(app, "test-local-auth-${UUID.randomUUID()}.bin")
        val owner = ViewModelStore()
        val home = fixtureLocalScope()
        lateinit var model: HubViewModel
        try {
            main { model = HubViewModel(app, store, connection); owner.put("local-auth", model) }
            await { !model.state.value.loading }
            main { model.updateNetworkScope(home); model.setForeground(true)
                model.connect("https://127.0.0.1:${https.port}", generated, "http://192.168.1.1:${http.port}") }
            await { model.state.value.live && https.streams.get() == 1 }
            main { model.switchEndpoint() }
            await { model.state.value.live && http.streams.get() == 1 }
            http.status.set(401)
            main { model.refreshSaved() }
            await { !model.state.value.loading && model.state.value.error == R.string.error_auth }
            val secureReads = https.requests.get()
            val localReads = http.requests.get()
            main { model.setForeground(false); model.setForeground(true) }
            assertEquals("http", model.endpointType())
            assertEquals(secureReads, https.requests.get()); assertEquals(localReads, http.requests.get())
            main { model.updateNetworkScope(home.copy(vpn = true)) }
            assertEquals("http", model.endpointType()) // Unconfirmed alternate cannot acquire authority.
            assertEquals(R.string.error_auth, model.state.value.error)
            assertEquals(secureReads, https.requests.get()); assertEquals(localReads, http.requests.get())
        } finally {
            main { owner.clear() }
            connection.dispatcher.cancelAll(); connection.connectionPool.evictAll(); http.close(); https.close(); store.clear()
        }
    }
}
