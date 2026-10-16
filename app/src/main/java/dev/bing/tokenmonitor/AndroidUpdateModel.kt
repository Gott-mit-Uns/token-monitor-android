package dev.bing.tokenmonitor

import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

internal val LocalAndroidUpdateController = staticCompositionLocalOf<AndroidUpdateController?> { null }

/** Activity recreation retains the request and throttle; no worker, alarm or background wakeup. */
class AndroidUpdateModel internal constructor(
    application: Application,
    source: AndroidReleaseSource,
    installedVersion: String
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, GitHubAndroidReleaseSource(),
        application.packageManager.getPackageInfo(application.packageName, 0).versionName.orEmpty())
    private val preferences = application.getSharedPreferences("android-release-check", 0)
    internal val controller = AndroidUpdateController(
        installedVersion, source, viewModelScope,
        loadLastAutomaticCheck = { preferences.getLong("last-success-ms", 0L) },
        saveLastAutomaticCheck = { preferences.edit().putLong("last-success-ms", it).apply() },
        loadLastAutomaticFailure = { preferences.getLong("last-failure-ms", 0L).takeIf { it > 0L } },
        saveLastAutomaticFailure = { at ->
            preferences.edit().apply {
                if (at == null) remove("last-failure-ms") else putLong("last-failure-ms", at)
            }.apply()
        }
    )
    /** Configuration recreation keeps this retained model and its single in-flight request. */
    fun onActivityStopped(changingConfigurations: Boolean) {
        if (!changingConfigurations) setForeground(false)
    }
    fun setForeground(active: Boolean) {
        controller.setForeground(active)
        if (active) controller.checkOnForeground()
    }
}
