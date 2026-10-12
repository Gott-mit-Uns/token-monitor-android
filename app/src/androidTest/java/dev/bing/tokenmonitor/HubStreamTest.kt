package dev.bing.tokenmonitor

import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class HubStreamTest {
    @Test fun cancellationClosesLiveSocketWithoutFailureCallback() {
        val server = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val received = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val failure = AtomicBoolean(false)
        val worker = thread {
            server.accept().use { socket ->
                socket.soTimeout = 5000
                val input = socket.getInputStream().bufferedReader()
                while (!input.readLine().isNullOrEmpty()) { /* Consume request without logging headers. */ }
                val output = socket.getOutputStream()
                output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n" +
                    "event: snapshot\ndata: {\"stats\":{\"periods\":{}}}\n\n").toByteArray())
                output.flush()
                if (input.read() == -1) closed.countDown()
            }
        }
        val client = OkHttpClient.Builder().callTimeout(0, TimeUnit.SECONDS).build()
        val call = HubStream(client).open("http://127.0.0.1:${server.localPort}", UUID.randomUUID().toString(),
            { received.countDown() }, { failure.set(true) })
        try {
            assertTrue(received.await(5, TimeUnit.SECONDS))
            call.cancel()
            assertTrue(closed.await(5, TimeUnit.SECONDS))
            assertTrue(call.isCanceled())
            assertFalse(failure.get())
        } finally {
            call.cancel(); server.close(); worker.join(1000)
            client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
        }
    }
}
