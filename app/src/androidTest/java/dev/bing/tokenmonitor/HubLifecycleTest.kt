package dev.bing.tokenmonitor

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class HubLifecycleTest {
    @Test fun backgroundClosesStreamAndForegroundCreatesNewSnapshotConnection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val storeNameForBuild="test-lifecycle-${UUID.randomUUID()}.bin"
        val store = HubStore(app,storeNameForBuild)
        val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val first = CountDownLatch(1)
        val second = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val streams = AtomicInteger()
        val healthReads=AtomicInteger()
        val workers = mutableListOf<Thread>()
        val build=HubBuildInfo("node-hub",1,74,HubBuildRegistry.entries.getValue("core").getValue(74L),5,HubBuildRegistry.entries.getValue("node-hub").getValue(5L))
        val health=build.cache().dropLast(1)+",\"ok\":true,\"role\":\"hub\"}"
        val body = """{"periods":{"today":{"totalTokens":123}},"devices":[]}"""
        val accepting = thread {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                workers += thread {
                    socket.use {
                        socket.soTimeout = 5000
                        val input = socket.getInputStream().bufferedReader()
                        val path = input.readLine().orEmpty()
                        while (!input.readLine().isNullOrEmpty()) { }
                        val output = socket.getOutputStream()
                        if (path.contains("/api/stats/stream")) {
                            val n = streams.incrementAndGet()
                            output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\nevent: snapshot\ndata: {\"stats\":$body}\n\n").toByteArray())
                            output.flush()
                            if (n == 1) first.countDown() else second.countDown()
                            val disconnected = try { input.read() == -1 } catch (_: java.net.SocketException) { true }
                            if (disconnected && n == 1) closed.countDown()
                        } else {
                            val response = if (path.contains("/api/health")) {healthReads.incrementAndGet();health} else body
                            output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${response.toByteArray().size}\r\nConnection: close\r\n\r\n$response").toByteArray())
                            output.flush()
                        }
                    }
                }
            }
        }
        val client = HubViewModel.defaultClient().newBuilder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().url(chain.request().url.newBuilder().host("127.0.0.1").build()).build())
        }.build()
        lateinit var model: HubViewModel
        instrumentation.runOnMainSync { model = HubViewModel(app, store, client) }
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (model.state.value.loading && System.nanoTime() < deadline) Thread.sleep(10)
            instrumentation.runOnMainSync {
                model.updateNetworkScope(fixtureLocalScope())
                model.setForeground(true)
                model.refresh("http://192.168.1.1:${server.localPort}", UUID.randomUUID().toString())
            }
            assertTrue(first.await(5, TimeUnit.SECONDS))
            assertEquals("Build uses the existing single health read",1,healthReads.get())
            assertEquals(build,model.state.value.hubBuild)
            assertEquals(build,HubStore(app,storeNameForBuild).load()?.hubBuild)
            lateinit var restored:HubViewModel
            instrumentation.runOnMainSync {restored=HubViewModel(app,HubStore(app,storeNameForBuild),client)}
            val restoreDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
            while(restored.state.value.loading && System.nanoTime()<restoreDeadline) Thread.sleep(10)
            assertEquals(build,restored.state.value.hubBuild)
            assertEquals("Cached build restores without a second stream",1,streams.get())
            assertEquals("Cache restoration does not read health again",1,healthReads.get())
            instrumentation.runOnMainSync { model.setForeground(false) }
            assertTrue(closed.await(5, TimeUnit.SECONDS))
            assertEquals(1, streams.get())
            assertFalse(model.state.value.live)
            instrumentation.runOnMainSync { model.setForeground(true) }
            assertTrue(second.await(5, TimeUnit.SECONDS))
            assertEquals(2, streams.get())
        } finally {
            instrumentation.runOnMainSync { model.setForeground(false) }
            client.dispatcher.cancelAll()
            server.close(); accepting.join(1000)
            workers.forEach { it.join(1000) }
            store.clear()
        }
    }
}
