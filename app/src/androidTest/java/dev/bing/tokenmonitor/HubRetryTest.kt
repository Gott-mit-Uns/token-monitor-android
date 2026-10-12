package dev.bing.tokenmonitor

import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class HubRetryTest {
    @Test fun unavailableStreamReturnsServerDelayWithoutSnapshot() {
        val server = ServerSocket(0)
        val worker = thread {
            server.accept().use { socket ->
                val input = socket.getInputStream().bufferedReader()
                while (!input.readLine().isNullOrEmpty()) { /* Consume without logging headers. */ }
                socket.getOutputStream().write("HTTP/1.1 429 Too Many Requests\r\nRetry-After: 120\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            }
        }
        val finished = CountDownLatch(1)
        val result = AtomicReference<StreamEnd>()
        val client = OkHttpClient()
        val call = HubStream(client).open("http://127.0.0.1:${server.localPort}", UUID.randomUUID().toString(),
            { fail("An unavailable stream must not publish data") }, { result.set(it); finished.countDown() })
        try {
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertFalse(result.get().unauthorized)
            assertEquals(120000L, result.get().retryAfterMs)
        } finally {
            call.cancel(); server.close(); worker.join(1000)
            client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
        }
    }
}
