package io.github.theminionooo.tokenmonitor

import android.app.Application
import android.content.res.Configuration
import io.github.theminionooo.tokenmonitor.widget.WidgetUpdateCoordinator
import kotlin.concurrent.thread

/**
 * Nothing runs at startup. The one job here is to redraw saved widgets when the phone switches
 * between light and dark while the process is alive, because a widget cannot observe that itself.
 */
class TokenMonitorApplication : Application() {
    private var nightMode = 0

    override fun onCreate() {
        super.onCreate()
        nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val next = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (next != nightMode) {
            nightMode = next
            thread(name = "widget-night-mode") { WidgetUpdateCoordinator.refresh(applicationContext) }
        }
    }
}
