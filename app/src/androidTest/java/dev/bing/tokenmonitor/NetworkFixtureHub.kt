package dev.bing.tokenmonitor

import okhttp3.tls.HandshakeCertificates
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import kotlin.concurrent.thread

/** Local HTTP/TLS peers with shared synthetic data. Authentication never enters diagnostics. */
internal class NetworkFixtureHub(private val credential: String, tls: HandshakeCertificates? = null, private val stats:String = """{"periods":{"today":{"totalTokens":123}},"devices":[]}""") : AutoCloseable {
    private val server = tls?.sslContext()?.serverSocketFactory?.createServerSocket(0, 12, InetAddress.getByName("127.0.0.1"))
        ?: ServerSocket(0, 12, InetAddress.getByName("127.0.0.1"))
    val port get() = server.localPort
    val requests = AtomicInteger()
    val streams = AtomicInteger()
    val closedStreams = AtomicInteger()
    val badAuthentication = AtomicInteger()
    val acceptedCredential=AtomicReference(credential)
    val status = AtomicInteger(200)
    private val sockets = ConcurrentLinkedQueue<Socket>()
    private val workers = ConcurrentLinkedQueue<Thread>()
    val statsBody=AtomicReference(stats)
    val statsGate=AtomicReference<CountDownLatch?>()
    val statsEntered=CountDownLatch(1)
    private val accepting = thread {
        while (!server.isClosed) {
            val socket = try { server.accept() } catch (_: IOException) { break }
            sockets += socket
            workers += thread {
                try {
                    socket.use {
                        socket.soTimeout = 60000
                        val input = socket.getInputStream().bufferedReader()
                        val line = input.readLine().orEmpty()
                        var authenticated = false
                        while (true) {
                            val header = input.readLine() ?: break
                            if (header.isEmpty()) break
                            if (header == "Authorization: Bearer ${acceptedCredential.get()}") authenticated = true
                        }
                        requests.incrementAndGet()
                        val health = line.contains("/api/health")
                        if (!health && !authenticated) badAuthentication.incrementAndGet()
                        val code = if (!health && !authenticated) 401 else status.get()
                        if(line.contains("/api/stats ")) {statsEntered.countDown();statsGate.get()?.await(10,TimeUnit.SECONDS)}
                        val output = socket.getOutputStream()
                        if (code == 200 && line.contains("/api/stats/stream")) {
                            streams.incrementAndGet()
                            output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\nevent: snapshot\ndata: {\"stats\":${statsBody.get()}}\n\n").toByteArray())
                            output.flush()
                            try { input.read() } finally { closedStreams.incrementAndGet() }
                        } else {
                            val body = when {
                                code != 200 -> "{}"
                                health -> """{"ok":true,"role":"hub"}"""
                                line.contains("/api/history") -> """{"daily":[{"date":"2026-10-09","tokens":123}]}"""
                                else -> statsBody.get()
                            }
                            output.write(("HTTP/1.1 $code Result\r\nContent-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body").toByteArray())
                            output.flush()
                        }
                    }
                } catch (_: IOException) { /* Peer cancellation is part of the fixture. */ }
            }
        }
    }
    override fun close() {
        server.close(); sockets.forEach { runCatching { it.close() } }
        accepting.join(1000); workers.forEach { it.join(1000) }
    }
}

internal class RecordingSocketFactory : SocketFactory() {
    private val delegate = getDefault()
    val created = AtomicInteger()
    private fun created(socket: Socket): Socket { created.incrementAndGet(); return socket }
    override fun createSocket(): Socket = created(delegate.createSocket())
    override fun createSocket(host: String, port: Int): Socket = created(delegate.createSocket(host, port))
    override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int): Socket = created(delegate.createSocket(host, port, local, localPort))
    override fun createSocket(host: InetAddress, port: Int): Socket = created(delegate.createSocket(host, port))
    override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int): Socket = created(delegate.createSocket(host, port, local, localPort))
}
