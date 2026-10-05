package io.github.theminionooo.tokenmonitor

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.ui.DashboardViewModel
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class ConnectionLifecycleTest {
    @Test fun leavingDuringPairingReleasesTheConnectButton() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".preview"))
        grantFixtureNetworkAccess()
        val address = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>().first { it.isSiteLocalAddress }.hostAddress
        val accepted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val store = ViewModelStore()
        lateinit var viewModel: DashboardViewModel
        ServerSocket(0).use { server ->
            server.soTimeout = 10_000
            val peer = thread(isDaemon = true) {
                runCatching { server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { /* Wait for complete headers. */ }
                    accepted.countDown()
                    release.await(8, TimeUnit.SECONDS)
                } }
            }
            try {
                instrumentation.runOnMainSync {
                    viewModel = DashboardViewModel(context.applicationContext as Application)
                    store.put("pairing", viewModel)
                    viewModel.saveConnection("http://$address:${server.localPort}", "", "synthetic-test", true)
                }
                assertTrue("Pairing request did not reach the local test server", accepted.await(5, TimeUnit.SECONDS))
                instrumentation.runOnMainSync { viewModel.onPause() }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (viewModel.connectionForm.value.saving && System.nanoTime() < deadline) Thread.sleep(20)
                assertFalse("Connect must be usable again after cancelling pairing", viewModel.connectionForm.value.saving)
            } finally {
                release.countDown()
                instrumentation.runOnMainSync { store.clear() }
                peer.join(1000)
            }
        }
    }
}
