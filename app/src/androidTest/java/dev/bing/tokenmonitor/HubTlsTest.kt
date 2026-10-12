package dev.bing.tokenmonitor

import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.concurrent.thread

/** Actual TLS sockets. Test roots and private keys exist only in test-process memory. */
class HubTlsTest {
    @Test fun certificateHostnameRedirectAndStreamPoliciesHoldOverTls() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trusted = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server = serverCertificates.sslContext().serverSocketFactory.createServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val redirectTarget = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        redirectTarget.soTimeout = 500
        val base = "https://127.0.0.1:${server.localPort}"
        val credential = UUID.randomUUID().toString()
        val redirect = AtomicBoolean()
        val authenticated = AtomicInteger()
        val read = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val failed = AtomicBoolean()
        val snapshots = ConcurrentLinkedQueue<String>()
        val workers = ConcurrentLinkedQueue<Thread>()
        val sockets = ConcurrentLinkedQueue<java.net.Socket>()
        val body = """{"periods":{"today":{"totalTokens":5000000123}},"devices":[]}"""
        val accepting = thread {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: IOException) { break }
                sockets += socket
                workers += thread {
                    try {
                        socket.use {
                            socket.soTimeout = 10000
                            val input = socket.getInputStream().bufferedReader()
                            val path = input.readLine().orEmpty()
                            var authorizationMatches = false
                            while (true) {
                                val header = input.readLine() ?: break
                                if (header.isEmpty()) break
                                if (header == "Authorization: Bearer $credential") authorizationMatches = true
                            }
                            if (authorizationMatches) authenticated.incrementAndGet()
                            val output = socket.getOutputStream()
                            if (redirect.get() && path.contains("/api/stats")) {
                                output.write(("HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:${redirectTarget.localPort}/api/stats\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").toByteArray())
                                output.flush()
                            } else if (path.contains("/api/stats/stream")) {
                                output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\nevent: snapshot\ndata: {\"stats\":$body}\n\n").toByteArray())
                                output.flush()
                                val ended = try { input.read() == -1 } catch (_: java.net.SocketException) { true }
                                if (ended) closed.countDown()
                            } else {
                                val response = if (path.contains("/api/health")) """{"ok":true,"role":"hub"}""" else body
                                output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${response.toByteArray().size}\r\nConnection: close\r\n\r\n$response").toByteArray())
                                output.flush()
                            }
                        }
                    } catch (_: IOException) { /* Rejected certificate/hostname closes the peer; never log headers. */ }
                }
            }
        }
        val defaultClient = HubViewModel.defaultClient()
        val client = defaultClient.newBuilder().sslSocketFactory(trusted.sslSocketFactory(), trusted.trustManager).build()
        try {
            assertTrue(runCatching { HubHttp(defaultClient).verify(base) }.exceptionOrNull() is SSLHandshakeException)
            assertTrue(runCatching { HubHttp(client).verify("https://localhost:${server.localPort}") }.exceptionOrNull() is SSLPeerUnverifiedException)
            val http = HubHttp(client)
            http.verify(base)
            assertEquals(5000000123L, HubParser.parse(http.read(base, "stats", credential)).periods.getValue("today").tokens)
            val call = HubStream(client).open(base, credential, { snapshots += it; read.countDown() }, { failed.set(true) })
            assertTrue(read.await(5, TimeUnit.SECONDS))
            assertEquals(5000000123L, HubParser.parse(snapshots.first()).periods.getValue("today").tokens)
            call.cancel()
            assertTrue(closed.await(5, TimeUnit.SECONDS))
            assertFalse(failed.get())
            redirect.set(true)
            assertTrue(runCatching { http.read(base, "stats", credential) }.exceptionOrNull() is HubReadException)
            val unexpected = runCatching { redirectTarget.accept() }.getOrNull()
            unexpected?.close()
            assertNull("HTTPS must not follow a redirect to HTTP", unexpected)
            assertEquals(3, authenticated.get())
        } finally {
            defaultClient.dispatcher.cancelAll(); client.dispatcher.cancelAll()
            defaultClient.connectionPool.evictAll(); client.connectionPool.evictAll()
            server.close(); redirectTarget.close(); sockets.forEach { runCatching { it.close() } }
            accepting.join(1000); workers.forEach { it.join(1000) }
        }
    }
}
