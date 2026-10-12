package dev.bing.tokenmonitor

import android.net.ConnectivityManager
import java.net.Inet4Address
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import org.junit.Assert.*
import org.junit.Test

@SdkSuppress(minSdkVersion = 28)
class HubNetworkObserverTest {
    @Test fun replacementRevokesOldSubnetBeforeNewNetworkDetailsArrive() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val scopes = mutableListOf<HubNetworkScope>()
        val observer = HubNetworkObserver(instrumentation.targetContext) { scopes += it }
        instrumentation.runOnMainSync {
            try {
                observer.start()
                val active = instrumentation.targetContext.getSystemService(ConnectivityManager::class.java).activeNetwork
                    ?: error("Observer test requires an active emulator network")
                val connectivity = instrumentation.targetContext.getSystemService(ConnectivityManager::class.java)
                val wifi = connectivity.getNetworkCapabilities(active)!!
                val homeLinks = connectivity.getLinkProperties(active)!!
                val local = homeLinks.linkAddresses.first { it.address is Inet4Address }.address.hostAddress!!
                val target = "http://$local"
                val first = Network.fromNetworkHandle((701L shl 32) or 0xcafed00dL)
                val second = Network.fromNetworkHandle((702L shl 32) or 0xcafed00dL)
                observer.callback.onAvailable(first)
                assertFalse(scopes.last().ready)
                observer.callback.onCapabilitiesChanged(first, wifi)
                assertFalse(scopes.last().permitsHttp(target))
                observer.callback.onLinkPropertiesChanged(first, homeLinks)
                assertTrue(scopes.last().permitsHttp(target))
                assertNotNull(scopes.last().socketFactory)
                observer.callback.onAvailable(second)
                assertEquals(second.networkHandle, scopes.last().networkId)
                assertFalse(scopes.last().permitsHttp(target))
                assertTrue(scopes.last().subnets.isEmpty())
                observer.callback.onLost(first)
                assertEquals(second.networkHandle, scopes.last().networkId)
                observer.callback.onCapabilitiesChanged(second, wifi)
                assertFalse(scopes.last().ready)
                observer.callback.onLinkPropertiesChanged(second, LinkProperties())
                assertTrue(scopes.last().ready)
                assertFalse(scopes.last().permitsHttp(target))
                observer.callback.onLost(second)
                assertFalse(scopes.last().available)
                assertFalse(scopes.last().permitsHttp(target))
            } finally { observer.stop() }
        }
    }
}
