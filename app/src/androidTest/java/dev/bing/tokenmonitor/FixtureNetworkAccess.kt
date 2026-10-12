package dev.bing.tokenmonitor

import javax.net.SocketFactory

/** Explicit local scope for synthetic servers; never included in the application. */
internal fun fixtureLocalScope(networkId: Long = 1L) = HubNetworkScope(true, false,
    listOf(LocalSubnet("192.168.1.54", 24)), networkId, socketFactory = SocketFactory.getDefault())
