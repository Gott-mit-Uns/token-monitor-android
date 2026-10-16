package dev.bing.tokenmonitor

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Actual retained AndroidViewModel and Main dispatcher; no network, Activity UI or phone claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AndroidUpdateLifecycleTest {
    private val application: Application get() = RuntimeEnvironment.getApplication()
    private fun drain() = shadowOf(Looper.getMainLooper()).idle()
    private fun provider(store: ViewModelStore, source: AndroidReleaseSource) =
        ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AndroidUpdateModel(application, source, "0.72.0-01") as T
        })
    private val release get() = AndroidRelease(AndroidReleaseVersion.parse("0.72.0-02")!!,
        "${AndroidReleases.repository}/releases/tag/v0.72.0-02",
        "${AndroidReleases.repository}/releases/download/v0.72.0-02/token-monitor-android-0.72.0-02.apk")

    @Test fun repeatedConfigurationRecreationRetainsOneSlowRequestAndRecordsOnlyItsResult() = runBlocking {
        // AndroidViewModelFactory still finds the public Application-only constructor.
        assertNotNull(AndroidUpdateModel::class.java.getConstructor(Application::class.java))
        val owner = ViewModelStore()
        val pending = CompletableDeferred<AndroidRelease>()
        var calls = 0
        var cancellations = 0
        val source = AndroidReleaseSource {
            calls++
            try { pending.await() } finally { if (!pending.isCompleted) cancellations++ }
        }
        try {
            val original = provider(owner, source)[AndroidUpdateModel::class.java]
            original.setForeground(true); drain()
            assertEquals(1, calls)
            assertEquals(AndroidUpdateState.Checking, original.controller.state.value)
            repeat(8) {
                // MainActivity forwards isChangingConfigurations on every stop;
                // each replacement owner retrieves the same retained ViewModelStore.
                original.onActivityStopped(changingConfigurations = true); drain()
                val recreated = provider(owner, source)[AndroidUpdateModel::class.java]
                assertSame(original, recreated)
                recreated.setForeground(true); drain()
                assertEquals(1, calls)
                assertEquals(AndroidUpdateState.Checking, recreated.controller.state.value)
            }
            assertEquals(0, cancellations)
            val preferences = application.getSharedPreferences("android-release-check", 0)
            assertFalse(preferences.contains("last-success-ms"))
            assertFalse(preferences.contains("last-failure-ms"))
            pending.complete(release); drain()
            assertTrue(original.controller.state.value is AndroidUpdateState.Available)
            assertTrue(preferences.getLong("last-success-ms", 0L) > 0L)
            assertFalse(preferences.contains("last-failure-ms"))
            original.onActivityStopped(changingConfigurations = false)
            original.setForeground(true); drain()
            assertEquals(1, calls) // Persisted success throttle still applies.
        } finally { owner.clear(); drain() }
    }

    @Test fun realBackgroundAfterRecreationCancelsAndAllowsOneForegroundRetryWithoutFalseStamp() {
        val owner = ViewModelStore()
        var calls = 0
        var cancellations = 0
        val source = AndroidReleaseSource {
            calls++
            try { awaitCancellation() } finally { cancellations++ }
        }
        try {
            val model = provider(owner, source)[AndroidUpdateModel::class.java]
            model.setForeground(true); drain()
            model.onActivityStopped(changingConfigurations = true)
            model.setForeground(true); drain()
            assertEquals(1, calls); assertEquals(0, cancellations)
            model.onActivityStopped(changingConfigurations = false); drain()
            assertEquals(1, cancellations)
            assertEquals(AndroidUpdateState.Idle, model.controller.state.value)
            val preferences = application.getSharedPreferences("android-release-check", 0)
            assertFalse(preferences.contains("last-success-ms"))
            assertFalse(preferences.contains("last-failure-ms"))
            drain(); assertEquals(1, calls) // No wakeup or retry while stopped.
            model.setForeground(true); drain()
            assertEquals(2, calls)
            model.onActivityStopped(changingConfigurations = false); drain()
            assertEquals(2, cancellations)
        } finally { owner.clear(); drain() }
    }

    @Test fun clearingRetainedOwnerCancelsEvenIfLastStopWasConfigurationChange() {
        val owner = ViewModelStore()
        var calls = 0
        var cancellations = 0
        val model = provider(owner, AndroidReleaseSource {
            calls++
            try { awaitCancellation() } finally { cancellations++ }
        })[AndroidUpdateModel::class.java]
        model.setForeground(true); drain()
        model.onActivityStopped(changingConfigurations = true); drain()
        assertEquals(1, calls); assertEquals(0, cancellations)
        owner.clear(); drain()
        assertEquals(1, cancellations) // viewModelScope owns final destruction.
        val preferences = application.getSharedPreferences("android-release-check", 0)
        assertFalse(preferences.contains("last-success-ms"))
        assertFalse(preferences.contains("last-failure-ms"))
    }
}
