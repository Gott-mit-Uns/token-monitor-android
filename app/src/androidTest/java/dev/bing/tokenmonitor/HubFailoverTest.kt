package dev.bing.tokenmonitor

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Controlled loopback peers; no production address, credential or app-data reset. */
class HubFailoverTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun await(label: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(20)
        assertTrue(label, condition())
    }
    private class Streams : EventListener() {
        val active = AtomicInteger()
        val max = AtomicInteger()
        override fun callStart(call: Call) { if(call.request().url.encodedPath.endsWith("/stream")) {
            val n=active.incrementAndGet();max.updateAndGet { maxOf(it,n) }
        } }
        override fun callEnd(call: Call) { finish(call) }
        override fun callFailed(call: Call, ioe: java.io.IOException) { finish(call) }
        private fun finish(call:Call) { if(call.request().url.encodedPath.endsWith("/stream")) active.decrementAndGet() }
    }
    private inner class Harness(associated:Boolean=true, tlsTrusted:Boolean=true, localBlocked:Boolean=false) : AutoCloseable {
        val secret=UUID.randomUUID().toString()
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build()
        val tls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val http=NetworkFixtureHub(secret)
        val https=NetworkFixtureHub(secret,tls)
        val local="http://192.168.1.1:${http.port}"
        val secure="https://127.0.0.1:${https.port}"
        val streams=Streams()
        val gate=CountDownLatch(1)
        val home=fixtureLocalScope().copy(networkId=10L)
        val mobile=HubNetworkScope(false,false,emptyList(),networkId=20L)
        val owner=ViewModelStore()
        val store=HubStore(app,"test-failover-${UUID.randomUUID()}.bin")
        val client:OkHttpClient
        lateinit var model:HubViewModel
        init {
            if(localBlocked) http.statsGate.set(gate)
            val builder=HubViewModel.defaultClient().newBuilder().eventListener(streams)
                .readTimeout(600,TimeUnit.MILLISECONDS).callTimeout(1500,TimeUnit.MILLISECONDS)
                .addInterceptor {chain->
                    val req=chain.request()
                    chain.proceed(if(req.url.scheme=="http") req.newBuilder().url(req.url.newBuilder().host("127.0.0.1").build()).build() else req)
                }
            if(tlsTrusted) {
                val trusted=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
                builder.sslSocketFactory(trusted.sslSocketFactory(),trusted.trustManager)
            }
            client=builder.build()
            store.save(SavedHub(secure,secret,"""{"periods":{"today":{"totalTokens":17}},"devices":[]}""",1L,local).also {it.endpointsAssociated=associated})
            main {model=HubViewModel(app,store,client);owner.put("failover",model)}
            await("cache loaded") {!model.state.value.loading}
        }
        fun start(scope:HubNetworkScope=home) {main {model.updateNetworkScope(scope);model.setForeground(true)}}
        fun live(type:String) {
            await("live $type") {model.state.value.live && model.endpointType()==type && !model.state.value.loading}
            val expected=if(type=="http") local else secure
            assertEquals(expected,model.state.value.connectedEndpoint)
            assertEquals(expected,model.state.value.lastSuccessfulEndpoint)
        }
        override fun close() {
            gate.countDown();main {owner.clear()};await("all streams released") {streams.active.get()==0}
            client.dispatcher.cancelAll();client.connectionPool.evictAll();http.close();https.close();store.clear()
            assertTrue("maximum active stream calls",streams.max.get() <= 1)
        }
    }
    @Test fun confirmedLanTimeoutFallsBackAndCooldownSurvivesForegroundReturn() {
        Harness(localBlocked=true).use {h->
            h.start();h.live("https")
            assertEquals(0,h.http.streams.get());assertEquals(1,h.https.streams.get())
            val reads=h.http.requests.get()
            main {h.model.setForeground(false)};await("background closed") {h.streams.active.get()==0}
            assertNull(h.model.state.value.connectedEndpoint)
            assertEquals(h.secure,h.model.state.value.lastSuccessfulEndpoint)
            main {h.model.setForeground(true)};h.live("https")
            assertEquals(reads,h.http.requests.get());assertEquals(123L,h.model.state.value.snapshot?.periods?.get("today")?.tokens)
            assertTrue(h.store.load()!!.endpointsAssociated)
        }
    }
    @Test fun associationRequiresExplicitConfirmationAndBothReadsThenPersistsIndependentDisable() {
        Harness(associated=false).use {h->
            h.http.statsBody.set("""{"periods":{"today":{"totalTokens":999}},"devices":[]}""")
            h.start();h.live("https");assertEquals(0,h.http.requests.get())
            // Different payloads cannot prove or disprove identity. Without confirmation they never enter state.
            assertEquals(123L,h.model.state.value.snapshot?.periods?.get("today")?.tokens)
            main {h.model.configureLan(true,true)};h.live("http")
            assertTrue(h.model.endpointsAssociated());assertTrue(h.store.load()!!.endpointsAssociated)
            assertEquals(999L,h.model.state.value.snapshot?.periods?.get("today")?.tokens)
            main {h.model.configureLan(false)};h.live("https")
            await("disabled saved") {h.store.load()?.lanEnabled==false}
            val reads=h.http.requests.get()
            main {h.model.setForeground(false)};await("closed") {h.streams.active.get()==0}
            main {h.model.setForeground(true)};h.live("https");assertEquals(reads,h.http.requests.get())
        }
    }
    @Test fun localAuthFailureNeverFallsBackAndLifecycleCannotUnblockIt() {
        Harness().use {h->
            h.http.acceptedCredential.set(UUID.randomUUID().toString())
            h.start();await("auth error") {!h.model.state.value.loading && h.model.state.value.error==R.string.error_auth}
            assertNull(h.model.state.value.connectedEndpoint)
            assertNull(h.model.state.value.lastSuccessfulEndpoint)
            assertEquals(0,h.https.requests.get());assertEquals(17L,h.model.state.value.snapshot?.periods?.get("today")?.tokens)
            val reads=h.http.requests.get()
            main {h.model.setForeground(false);h.model.updateNetworkScope(h.mobile);h.model.setForeground(true)}
            Thread.sleep(250);assertEquals(reads,h.http.requests.get());assertEquals(0,h.https.requests.get())
            assertEquals(17L,HubParser.parse(h.store.load()!!.snapshot).periods["today"]?.tokens)
        }
    }
    @Test fun tlsFailureAndExternalNetworkFailureNeverDowngrade() {
        Harness(tlsTrusted=false).use {h->
            h.start(h.mobile);await("TLS distinct") {!h.model.state.value.loading && h.model.state.value.error==R.string.error_tls}
            assertNull(h.model.state.value.connectedEndpoint)
            assertNull(h.model.state.value.lastSuccessfulEndpoint)
            assertEquals(0,h.http.requests.get());assertEquals(17L,h.model.state.value.snapshot?.periods?.get("today")?.tokens)
            main {h.model.setForeground(false);h.model.updateNetworkScope(h.home);h.model.setForeground(true)}
            Thread.sleep(150);assertEquals(0,h.http.requests.get())
        }
        Harness().use {h->
            h.https.close();h.start(h.mobile)
            await("external failed") {!h.model.state.value.loading && h.model.state.value.error!=null}
            assertEquals(0,h.http.requests.get());assertEquals(17L,h.model.state.value.snapshot?.periods?.get("today")?.tokens)
        }
    }
    @Test fun streamDropResyncsFullSnapshotAndHttpsNetworkFailureCanUseApprovedLan() {
        Harness().use {h->
            h.start();h.live("http")
            h.http.statsBody.set("""{"periods":{"today":{"totalTokens":777}},"devices":[]}""")
            val streams=h.http.streams.get()
            h.http.dropStreams()
            await("stream reconnect full snapshot") {h.http.streams.get()>streams && h.model.state.value.live && h.model.state.value.snapshot?.periods?.get("today")?.tokens==777L}
            assertEquals(1,h.streams.max.get())
            // Manual selection of already associated HTTPS; failed private read can safely use allowed LAN.
            h.https.close()
            main {h.model.refresh(h.secure,h.secret,h.local)}
            h.live("http")
            assertEquals(777L,h.model.state.value.snapshot?.periods?.get("today")?.tokens)
        }
    }
    @Test fun failedAssociationCannotAdmitCandidateDataOrSaveApproval() {
        Harness(associated=false).use {h->
            h.start();h.live("https")
            h.http.acceptedCredential.set(UUID.randomUUID().toString())
            val before=h.store.load()!!
            main {h.model.configureLan(true,true)}
            await("candidate rejected") {!h.model.state.value.loading && h.model.state.value.error==R.string.error_auth}
            assertFalse(h.model.endpointsAssociated());assertFalse(h.store.load()!!.endpointsAssociated)
            assertEquals(before.snapshot,h.store.load()!!.snapshot)
            assertEquals(123L,h.model.state.value.snapshot?.periods?.get("today")?.tokens)
            assertEquals(0,h.http.streams.get())
        }
    }
    @Test fun networkAndLifecycleRacesKeepOneStreamAndLateLocalResponseCannotOverwriteHttps() {
        Harness(localBlocked=true).use {h->
            h.http.statsBody.set("""{"periods":{"today":{"totalTokens":999}},"devices":[]}""")
            h.https.statsBody.set("""{"periods":{"today":{"totalTokens":321}},"devices":[]}""")
            h.start();assertTrue(h.http.statsEntered.await(2,TimeUnit.SECONDS))
            main {h.model.updateNetworkScope(h.mobile)};h.live("https")
            h.gate.countDown();Thread.sleep(200)
            assertEquals(321L,h.model.state.value.snapshot?.periods?.get("today")?.tokens)
            main {h.model.setForeground(false)};await("background zero") {h.streams.active.get()==0}
            val reads=h.http.requests.get()+h.https.requests.get()
            main {repeat(5) {h.model.updateNetworkScope(h.home.copy(networkId=30L+it))}}
            Thread.sleep(150);assertEquals(reads,h.http.requests.get()+h.https.requests.get())
            main {h.model.setForeground(true)};h.live("http")
            repeat(3) {n->
                main {h.model.setForeground(false);h.model.setForeground(true);h.model.updateNetworkScope(h.mobile.copy(networkId=50L+n))}
                h.live("https")
                main {h.model.updateNetworkScope(h.home.copy(networkId=60L+n))};h.live("http")
            }
            assertEquals(1,h.streams.max.get());assertEquals(999L,h.model.state.value.snapshot?.periods?.get("today")?.tokens)
            main {h.model.updateNetworkScope(h.mobile.copy(available=false,networkId=100L))}
            await("offline releases") {h.streams.active.get()==0}
            val offline=h.http.requests.get()+h.https.requests.get()
            main {h.model.refreshSaved()};Thread.sleep(100)
            assertEquals(offline,h.http.requests.get()+h.https.requests.get())
            assertEquals(999L,h.model.state.value.snapshot?.periods?.get("today")?.tokens)
        }
    }
}
