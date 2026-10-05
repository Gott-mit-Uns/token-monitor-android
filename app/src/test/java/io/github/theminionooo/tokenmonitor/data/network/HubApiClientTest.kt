package io.github.theminionooo.tokenmonitor.data.network

import com.sun.net.httpserver.HttpServer
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class HubApiClientTest {
    @Test fun `HTML challenge never opens an apparently connected stream`() = runBlocking {
        val opened = AtomicInteger()
        val receivedVersion = AtomicReference<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/stats/stream") { exchange ->
                receivedVersion.set(exchange.requestHeaders.getFirst("x-token-monitor-stream"))
                exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                val body = "<html>Sign in</html>".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        try {
            HubApiClient().use { api ->
                try {
                    api.streamStats(HubConnection("http://127.0.0.1:${server.address.port}", "fixture", true),
                        onOpen = { opened.incrementAndGet() }, onEvent = { fail("No HTML event may be delivered") })
                    fail("Expected an invalid stream content type to be rejected")
                } catch (error: HubApiException) { assertEquals(200, error.statusCode) }
            }
            assertEquals(0, opened.get())
            assertEquals("2", receivedVersion.get())
        } finally { server.stop(0) }
    }

    @Test fun `unchanged versions reuse large details but changed versions reload only their endpoint`() {
        val stats = AtomicReference("""{"periods":{},"historyRevision":"h1","deviceHistoryRevision":"d1","subscriptionsUpdatedAt":"s1"}""")
        val counts = listOf("/api/devices", "/api/history", "/api/subscriptions").associateWith { AtomicInteger() }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/health") { exchange ->
                val body = """{"ok":true,"role":"hub"}""".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            createContext("/api/stats") { exchange ->
                val body = stats.get().toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            counts.forEach { (path, count) -> createContext(path) { exchange ->
                count.incrementAndGet()
                // Real multi-device histories can comfortably exceed the upstream 2 MiB limit.
                val body = (if (path == "/api/history") " " .repeat(3 * 1024 * 1024) + "{}" else """{"periods":{}}""").toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            } }
            start()
        }
        try {
            val connection = HubConnection("http://127.0.0.1:${server.address.port}", "fixture", true)
            HubApiClient().use { api ->
                val first = api.loadSnapshot(connection)
                val second = api.loadSnapshot(connection, first)
                assertEquals(first.history, second.history)
                counts.values.forEach { assertEquals(1, it.get()) }
                stats.set(stats.get().replace("h1", "h2"))
                api.loadSnapshot(connection, second)
                assertEquals(2, counts.getValue("/api/history").get())
                assertEquals(1, counts.getValue("/api/devices").get())
                assertEquals(1, counts.getValue("/api/subscriptions").get())
                // Explicit/manual refresh deliberately bypasses the previous snapshot.
                api.loadSnapshot(connection)
                assertEquals(3, counts.getValue("/api/history").get())
            }
        } finally { server.stop(0) }
    }

    @Test
    fun `expired ownership prevents the next endpoint in a snapshot read`() {
        val checks = AtomicInteger()
        val reads = java.util.Collections.synchronizedList(mutableListOf<String>())
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/") { exchange ->
                val path = exchange.requestURI.path
                reads.add(path)
                val body = (if (path == "/api/health") """{"ok":true,"role":"hub"}""" else """{"periods":{}}""").toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        try {
            val connection = HubConnection("http://127.0.0.1:${server.address.port}", "synthetic-secret", true)
            HubApiClient(beforeRequest = {
                if (checks.incrementAndGet() == 3) throw CancellationException("Widget session expired")
            }).use { client ->
                try {
                    client.loadSnapshot(connection)
                    fail("Expected the expired widget lease to cancel before the devices request")
                } catch (_: CancellationException) {
                    assertEquals(listOf("/api/health", "/api/stats"), reads.toList())
                    assertEquals(3, checks.get())
                }
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a valid snapshot above the old stream limit remains live`() = runBlocking {
        val sessions = (0 until 2400).joinToString(",") { index ->
            """"synthetic-$index":{"sessionId":"synthetic-$index","title":"Synthetic parser capacity sample $index","client":"codex","projectLabel":"Benchmark project","totalTokens":1000,"costUsd":0.01,"models":{"synthetic-model":1000},"messageCount":5,"startedAt":"2026-10-04T10:00:00Z","lastUsedAt":"2026-10-04T12:00:00Z"}"""
        }
        val stats = """{"periods":{"month":{"totalTokens":2400000,"sessions":{$sessions}}}}"""
        val payload = "event: snapshot\ndata: {\"stats\":$stats}\n\n"
        assertTrue(payload.length > 512 * 1024)
        assertTrue(payload.length < 2 * 1024 * 1024)
        val server = streamServer(payload)
        try {
            val connection = HubConnection("http://127.0.0.1:${server.address.port}", "synthetic-secret", true)
            val events = mutableListOf<HubStreamEvent>()
            HubApiClient().use { client ->
                client.streamStats(connection, onOpen = {}, onEvent = { events.add(it) })
            }
            assertEquals(1, events.size)
            assertEquals("snapshot", events.single().type)
            val decoded = HubProtocolParser.decodeStats(events.single().data)
            assertEquals(2400, decoded.periods.getValue("month").sessions.size)
            assertEquals(2_400_000, decoded.periods.getValue("month").totalTokens)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `streaming still rejects an oversized event`() = runBlocking {
        val payload = "event: snapshot\ndata: {\"padding\":\"${"x".repeat(33 * 1024 * 1024)}\"}\n\n"
        val server = streamServer(payload)
        try {
            val connection = HubConnection("http://127.0.0.1:${server.address.port}", "synthetic-secret", true)
            HubApiClient().use { client ->
                try {
                    client.streamStats(connection, onOpen = {}, onEvent = { fail("Oversized event must not be delivered") })
                    fail("Expected oversized SSE rejection")
                } catch (error: HubApiException) {
                    assertTrue(error.presentation.contains("too large"))
                }
            }
        } finally {
            server.stop(0)
        }
    }

    private fun streamServer(payload: String): HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/api/stats/stream") { exchange ->
            // A bounded reader deliberately closes an oversized response early.
            try {
                val body = payload.toByteArray()
                exchange.responseHeaders.add("Content-Type", "text/event-stream")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            } catch (_: java.io.IOException) {
                exchange.close()
            }
        }
        start()
    }

    @Test
    fun `stats refresh reads only the changing aggregate`() {
        val authorization = AtomicReference<String?>()
        val statsReads = AtomicInteger()
        val detailReads = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/stats") { exchange ->
                authorization.set(exchange.requestHeaders.getFirst("Authorization"))
                statsReads.incrementAndGet()
                val body = """{"periods":{},"updatedAt":"now"}""".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            listOf("/api/health", "/api/devices", "/api/history", "/api/subscriptions").forEach { path ->
                createContext(path) { exchange ->
                    detailReads.incrementAndGet()
                    exchange.sendResponseHeaders(500, -1)
                    exchange.close()
                }
            }
            start()
        }
        try {
            val connection = HubConnection("http://127.0.0.1:${server.address.port}", "private-secret", true)
            HubApiClient().use { client ->
                assertEquals("""{"periods":{},"updatedAt":"now"}""", client.getStats(connection))
            }
            assertEquals("Bearer private-secret", authorization.get())
            assertEquals(1, statsReads.get())
            assertEquals(0, detailReads.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `authenticated reads do not follow redirects`() {
        val receivedAuthorization = AtomicReference<String?>()
        val redirectTarget = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/capture") { exchange ->
                receivedAuthorization.set(exchange.requestHeaders.getFirst("Authorization"))
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.close()
            }
            start()
        }
        val hub = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/health") { exchange ->
                val body = """{"ok":true,"role":"hub"}""".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            createContext("/api/stats") { exchange ->
                exchange.responseHeaders.add("Location", "http://127.0.0.1:${redirectTarget.address.port}/capture")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            start()
        }
        try {
            val connection = HubConnection("http://127.0.0.1:${hub.address.port}", "private-secret", true)
            try {
                HubApiClient().loadSnapshot(connection)
                fail("Expected a redirect response to be rejected")
            } catch (error: HubApiException) {
                assertEquals(302, error.statusCode)
            }
            assertNull(receivedAuthorization.get())
        } finally {
            hub.stop(0)
            redirectTarget.stop(0)
        }
    }

    @Test
    fun `hub identity is checked before sending the secret`() {
        val receivedAuthorization = AtomicReference<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/health") { exchange ->
                val body = """{"ok":true,"role":"other"}""".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            createContext("/api/stats") { exchange ->
                receivedAuthorization.set(exchange.requestHeaders.getFirst("Authorization"))
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.close()
            }
            start()
        }
        try {
            val connection = HubConnection("http://127.0.0.1:${server.address.port}", "private-secret", true)
            try {
                HubApiClient().loadSnapshot(connection)
                fail("Expected a non-Hub identity to be rejected")
            } catch (_: HubApiException) {}
            assertNull(receivedAuthorization.get())
        } finally {
            server.stop(0)
        }
    }
}
