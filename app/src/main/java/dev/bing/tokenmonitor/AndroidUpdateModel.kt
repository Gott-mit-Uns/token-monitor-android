package dev.bing.tokenmonitor

import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

internal val LocalAndroidUpdateController = staticCompositionLocalOf<AndroidUpdateController?> { null }

/** Activity recreation retains the request and throttle; no worker, alarm or background wakeup. */
class AndroidUpdateModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("android-release-check", 0)
    internal val controller = AndroidUpdateController(
        application.packageManager.getPackageInfo(application.packageName, 0).versionName.orEmpty(), GitHubAndroidReleaseSource(), viewModelScope,
        loadLastAutomaticCheck = { preferences.getLong("last-success-ms", 0L) },
        saveLastAutomaticCheck = { preferences.edit().putLong("last-success-ms", it).apply() },
        loadLastAutomaticFailure = { preferences.getLong("last-failure-ms", 0L).takeIf { it > 0L } },
        saveLastAutomaticFailure = { at ->
            preferences.edit().apply {
                if (at == null) remove("last-failure-ms") else putLong("last-failure-ms", at)
            }.apply()
        }
    )
    fun setForeground(active: Boolean) {
        controller.setForeground(active)
        if (active) controller.checkOnForeground()
    }
}
