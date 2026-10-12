package dev.bing.tokenmonitor

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address
import java.net.URI
import javax.net.SocketFactory

data class LocalSubnet(val address: String, val prefix: Int)
data class HubNetworkScope(val localTransport: Boolean, val vpn: Boolean, val subnets: List<LocalSubnet>, val networkId: Long = 0L,
    val available: Boolean = true, val ready: Boolean = true, val socketFactory: SocketFactory? = null) {
    fun permitsHttp(address: String): Boolean {
        if (!available || !ready || !localTransport || vpn) return false
        fun ipv4(value: String): Long? {
            val parts = value.split('.').map { it.toIntOrNull() ?: return null }
            if (parts.size != 4 || parts.any { it !in 0..255 }) return null
            return parts.fold(0L) { sum, part -> (sum shl 8) or part.toLong() }
        }
        val host = runCatching { URI(address).host }.getOrNull() ?: return false
        val target = ipv4(host) ?: return false
        return subnets.any { subnet ->
            val local = ipv4(subnet.address)
            if (local == null || subnet.prefix !in 1..32) false else {
                val mask = (0xffffffffL shl (32 - subnet.prefix)) and 0xffffffffL
                (local and mask) == (target and mask)
            }
        }
    }
}

/** Only observes the foreground default network; no probing, SSID access or background service. */
class HubNetworkObserver(context: Context, private val changed: (HubNetworkScope) -> Unit) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var network: Network? = null
    private var capabilities: NetworkCapabilities? = null
    private var links: LinkProperties? = null
    private fun publish() {
        if (!running) return
        val caps = capabilities
        val local = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true || caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        changed(HubNetworkScope(
            local,
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
            links?.linkAddresses.orEmpty().mapNotNull { link ->
                if (link.address is Inet4Address) link.address.hostAddress?.let { LocalSubnet(it, link.prefixLength) } else null
            }, network?.networkHandle ?: 0L, available = network != null,
            ready = caps != null && links != null, socketFactory = network?.socketFactory))
    }
    internal val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(next: Network) {
            if (!running) return
            if (network == next) return
            network = next; capabilities = null; links = null
            // Revoke the previous network's local scope before the new capabilities arrive.
            publish()
        }
        override fun onCapabilitiesChanged(next: Network, caps: NetworkCapabilities) {
            if (!running || network != next) return
            capabilities = caps; publish()
        }
        override fun onLinkPropertiesChanged(next: Network, properties: LinkProperties) {
            if (!running || network != next) return
            links = properties; publish()
        }
        override fun onLost(previous: Network) {
            if (network != previous) return
            network = null; capabilities = null; links = null; publish()
        }
    }
    fun start() {
        if (running) return
        running = true
        network = connectivity.activeNetwork
        capabilities = network?.let { connectivity.getNetworkCapabilities(it) }
        links = network?.let { connectivity.getLinkProperties(it) }
        publish()
        connectivity.registerDefaultNetworkCallback(callback, handler)
    }
    fun stop() {
        if (!running) return
        running = false
        connectivity.unregisterNetworkCallback(callback)
        network = null; capabilities = null; links = null
    }
}
