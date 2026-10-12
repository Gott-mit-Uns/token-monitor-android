package dev.bing.tokenmonitor

import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger

class HubHttpLifecycleTest {
    @Test fun pausedAndSupersededQueuedReadsNeverReachTransport() {
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor {
            requests.incrementAndGet()
            throw IOException("Synthetic transport")
        }.build()
        val reader = HubHttp(client)
        try {
            reader.pause()
            assertTrue(runCatching { reader.read("https://example.invalid", "history") }.exceptionOrNull() is CancellationException)
            assertEquals(0, requests.get())
            reader.resume()
            assertTrue(runCatching { reader.read("https://example.invalid", "history", valid = { false }) }.exceptionOrNull() is CancellationException)
            assertEquals(0, requests.get())
            assertTrue(runCatching { reader.read("https://example.invalid", "history") }.exceptionOrNull() is IOException)
            assertEquals(1, requests.get())
        } finally { reader.pause(); client.dispatcher.cancelAll(); client.connectionPool.evictAll() }
    }
}
