package dev.bing.tokenmonitor

import android.app.Application
import android.os.StrictMode
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.Socket
import java.net.Proxy
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Idle real Conscrypt TLS sockets, strict main-thread policy, cancelled ViewModel scope. */
class HubTlsPoolLifecycleTest {
    @Test fun networkRetirementAndOwnerClearCloseIdleTlsAwayFromMainThread() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as Application
        val certificate=HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build()
        val serverTls=HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server=serverTls.sslContext().serverSocketFactory.createServerSocket(0,8,InetAddress.getByName("127.0.0.1"))
        val sockets=ConcurrentLinkedQueue<Socket>();val workers=ConcurrentLinkedQueue<Thread>();val closed=AtomicInteger()
        val accepting=thread {
            while(!server.isClosed) {
                val socket=try {server.accept()} catch(_:java.io.IOException) {break}
                sockets+=socket;workers+=thread {
                    try {socket.use {
                        val input=socket.getInputStream().bufferedReader()
                        while(input.readLine()!=null) {
                            while(!input.readLine().isNullOrEmpty()) { }
                            socket.getOutputStream().apply {
                                write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: keep-alive\r\n\r\n{}".toByteArray());flush()
                            }
                        }
                    }} catch(_:java.io.IOException) { } finally {closed.incrementAndGet()}
                }
            }
        }
        val client=HubViewModel.defaultClient().newBuilder().proxy(Proxy.NO_PROXY).sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build()
        val store=HubStore(app,"test-tls-pool-lifecycle.bin")
        val base="https://127.0.0.1:${server.localPort}"
        store.save(SavedHub(base,"public-tls-lifecycle-fixture","""{"periods":{"today":{"totalTokens":1}},"devices":[]}""",1))
        val owner=ViewModelStore();lateinit var model:HubViewModel;lateinit var original:StrictMode.ThreadPolicy
        fun main(action:()->Unit)=instrumentation.runOnMainSync(action)
        fun await(condition:()->Boolean) {
            val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
            while(!condition() && System.nanoTime()<until) Thread.sleep(10)
            assertTrue("Real TLS pool retirement checkpoint",condition())
        }
        fun prime() {
            client.newCall(Request.Builder().url("$base/probe").build()).execute().use {it.body!!.string()}
            assertEquals(1,client.connectionPool.idleConnectionCount())
        }
        try {
            main {
                original=StrictMode.getThreadPolicy()
                StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder(original).detectNetwork().penaltyDeathOnNetwork().build())
                model=ViewModelProvider(owner,object:ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=HubViewModel(app,store,client) as T
                })[HubViewModel::class.java]
            }
            await {!model.state.value.loading}
            main {model.updateNetworkScope(HubNetworkScope(false,false,emptyList(),networkId=1))}
            prime()
            main {model.updateNetworkScope(HubNetworkScope(false,false,emptyList(),networkId=2))}
            await {client.connectionPool.connectionCount()==0 && closed.get()>=1}
            assertFalse(model.state.value.live)
            prime()
            // ViewModelStore cancels viewModelScope before invoking onCleared.
            main {model.setForeground(false);owner.clear()}
            await {client.connectionPool.connectionCount()==0 && closed.get()>=2}
            assertTrue(client.dispatcher.runningCalls().isEmpty() && client.dispatcher.queuedCalls().isEmpty())
        } finally {
            main {owner.clear();StrictMode.setThreadPolicy(original)}
            client.dispatcher.cancelAll();client.connectionPool.evictAll();store.clear()
            server.close();sockets.forEach {runCatching {it.close()}}
            accepting.join(1000);workers.forEach {it.join(1000)}
        }
    }
}
