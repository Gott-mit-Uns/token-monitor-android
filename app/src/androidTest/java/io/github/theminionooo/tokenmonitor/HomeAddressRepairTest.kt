package io.github.theminionooo.tokenmonitor

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.HubRepository
import io.github.theminionooo.tokenmonitor.data.storage.SecureConnectionStore
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class HomeAddressRepairTest {
    @Test fun repairChecksTheChosenAddressAndPreservesPairingOnFailure() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        check(target.packageName.endsWith(".preview"))
        if (android.os.Build.VERSION.SDK_INT >= 37) {
            instrumentation.uiAutomation.grantRuntimePermission(target.packageName, "android.permission.ACCESS_LOCAL_NETWORK")
        }
        val context = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = File(target.cacheDir, "repair-test").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                target.getSharedPreferences("repair-test-$name", mode)
        }
        val store = SecureConnectionStore(context)
        val address = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>().first { it.isSiteLocalAddress }.hostAddress
        val saved = HubConnection("http://100.100.100.100:17321", "synthetic-repair-secret", false, "http://192.168.1.2:17321")
        store.save(saved)
        val requests = Collections.synchronizedList(mutableListOf<Pair<String, String?>>())
        ServerSocket(0).use { server ->
            server.soTimeout = 10_000
            val reject = AtomicBoolean(false)
            val peer = thread(isDaemon = true) {
                while (!server.isClosed) {
                    runCatching {
                        server.accept().use { socket ->
                            val reader = socket.getInputStream().bufferedReader()
                            val path = reader.readLine().split(' ')[1]
                            var auth: String? = null
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                if (line.startsWith("Authorization:", true)) auth = line.substringAfter(':').trim()
                            }
                            requests.add(path to auth)
                            val status = if (reject.get() && path != "/api/health") 401 else 200
                            val body = when (path) {
                                "/api/health" -> """{"ok":true,"role":"hub"}"""
                                "/api/stats" -> """{"periods":{"today":{"totalTokens":1234}}}"""
                                else -> "{}"
                            }
                            socket.getOutputStream().write("HTTP/1.1 $status OK\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body".toByteArray())
                        }
                    }
                }
            }
            val repository = withContext(Dispatchers.Main) { HubRepository(context) }
            try {
                val newAddress = "http://$address:${server.localPort}"
                assertNull(withContext(Dispatchers.Main) { repository.repairHomeAddress(newAddress) })
                val repaired = store.read()!!
                assertEquals(saved.baseUrl, repaired.baseUrl)
                assertEquals(saved.secret, repaired.secret)
                assertEquals(newAddress, repaired.fallbackUrl)
                assertEquals(newAddress, repository.state.value.activeUrl)
                assertNull(requests.first().second)
                assertTrue(requests.drop(1).all { it.second == "Bearer ${saved.secret}" })
                reject.set(true)
                assertNotNull(withContext(Dispatchers.Main) { repository.repairHomeAddress(newAddress) })
                assertEquals(repaired, store.read())
            } finally {
                withContext(Dispatchers.Main) { repository.close() }
                server.close()
                peer.join(1000)
                store.clear()
            }
        }
    }
}
