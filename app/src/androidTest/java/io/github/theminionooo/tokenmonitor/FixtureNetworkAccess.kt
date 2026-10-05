package io.github.theminionooo.tokenmonitor

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.network.LocalNetworkAccess

/** Local fixture servers require the same explicit LAN permission as a home Hub. */
internal fun grantFixtureNetworkAccess() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    check(context.packageName.endsWith(".preview"))
    if (Build.VERSION.SDK_INT >= 37) {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, LocalNetworkAccess.permission)
    }
}
