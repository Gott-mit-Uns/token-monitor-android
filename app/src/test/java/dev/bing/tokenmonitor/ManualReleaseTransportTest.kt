package dev.bing.tokenmonitor

import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class ManualReleaseTransportTest {
    @Test fun transientResponsesAndRedirectsNeverRepeatTheHttpAttempt() {
        for(code in listOf(408,503,302)) {
            ServerSocket(0,8,InetAddress.getByName("127.0.0.1")).use {server ->
                server.soTimeout=500
                val requests=AtomicInteger()
                val worker=thread {
                    try {
                        while(true) server.accept().use {socket ->
                            socket.soTimeout=1000
                            val input=socket.getInputStream().bufferedReader()
                            input.readLine();while(!input.readLine().isNullOrEmpty()) {}
                            requests.incrementAndGet()
                            socket.getOutputStream().write(("HTTP/1.1 $code Synthetic\r\nRetry-After: 0\r\n"+
                                "Location: http://127.0.0.1:${server.localPort}/again\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").toByteArray())
                        }
                    } catch(_:SocketTimeoutException) {}
                }
                val client=manualReleaseCheckClient().newBuilder().proxy(java.net.Proxy.NO_PROXY).build()
                try {
                    val result=runCatching {client.newCall(Request.Builder().url("http://127.0.0.1:${server.localPort}/latest").build()).execute().use {it.code}}
                    worker.join(3000)
                    assertFalse("Fixture worker must finish",worker.isAlive)
                    assertEquals("Exactly one HTTP attempt for $code",1,requests.get())
                    if(code==503) assertTrue(result.exceptionOrNull() is AndroidUpdateException)
                    else assertEquals(code,result.getOrThrow())
                } finally {client.dispatcher.executorService.shutdown();client.connectionPool.evictAll()}
            }
        }
    }
}
