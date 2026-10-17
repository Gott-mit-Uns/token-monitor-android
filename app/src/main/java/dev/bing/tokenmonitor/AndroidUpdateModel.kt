package dev.bing.tokenmonitor

import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

internal val LocalDesktopReleaseController = staticCompositionLocalOf<DesktopReleaseController?> { null }

internal val LocalAndroidUpdateController = staticCompositionLocalOf<AndroidUpdateController?> { null }

/** Activity recreation retains both manual requests; no worker, alarm or background wakeup. */
class AndroidUpdateModel internal constructor(
    application: Application,
    source: AndroidReleaseSource,
    installedVersion: String,
    desktopSource: DesktopReleaseSource = GitHubDesktopReleaseSource()
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, GitHubAndroidReleaseSource(),
        application.packageManager.getPackageInfo(application.packageName, 0).versionName.orEmpty())
    // Legacy automatic-check preferences are intentionally never read: upgrades are manual too.
    internal val controller = AndroidUpdateController(installedVersion, source, viewModelScope)
    internal val desktopController = DesktopReleaseController(desktopSource, viewModelScope)
    /** Configuration recreation keeps this retained model and its single in-flight request. */
    fun onActivityStopped(changingConfigurations: Boolean) {
        if (!changingConfigurations) setForeground(false)
    }
    fun setForeground(active: Boolean) {
        controller.setForeground(active)
        desktopController.setForeground(active)
    }
}
