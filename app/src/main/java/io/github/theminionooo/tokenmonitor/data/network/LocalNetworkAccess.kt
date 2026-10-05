package io.github.theminionooo.tokenmonitor.data.network

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.theminionooo.tokenmonitor.domain.HubConnection

/** Android's LAN permission is separate from the app's choice to save a private route. */
internal object LocalNetworkAccess {
    const val permission = "android.permission.ACCESS_LOCAL_NETWORK"
    const val deniedMessage = "Home Wi-Fi access is off. Allow local network access in Settings to use this route."

    fun granted(context: Context): Boolean = Build.VERSION.SDK_INT < 37 ||
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun needed(connection: HubConnection): Boolean =
        listOfNotNull(connection.baseUrl, connection.fallbackUrl).any(HubAddressValidator::isLocalAddress)

    fun allowedRoutes(connection: HubConnection, preferred: String?, granted: Boolean): List<String> =
        EndpointFailover.candidates(connection, preferred).filter { granted || !HubAddressValidator.isLocalAddress(it) }
}
