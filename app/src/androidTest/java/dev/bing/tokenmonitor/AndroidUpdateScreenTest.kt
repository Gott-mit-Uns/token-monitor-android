package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AndroidUpdateScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val dark get() = InstrumentationRegistry.getArguments().getString("visualDark") != "false"
    @Test fun statesHaveAccessibleActionsAndExactOfficialBrowserTargets() {
        val state = mutableStateOf<AndroidUpdateState>(AndroidUpdateState.Idle)
        val urls = mutableListOf<String>()
        var checks = 0
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark, LanguageChoice.ENGLISH) {
            Column(Modifier.background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {AndroidUpdateContent("0.2.60", state.value, {checks++}, urls::add)}
        }}}
        assertEquals(0, checks)
        compose.onNodeWithTag("android-update-check").performClick(); assertEquals(1, checks)
        compose.onNodeWithTag("android-update-current-notes").performClick()
        compose.onNodeWithTag("desktop-release-notes").assertDoesNotExist()
        assertEquals(listOf(AndroidReleases.currentNotes("0.2.60")), urls)
        compose.runOnIdle {state.value = AndroidUpdateState.Checking}
        compose.onNodeWithTag("android-update-check").assertIsNotEnabled()
        compose.onNodeWithTag("android-update-status").assertTextEquals("Checking Android releases…")
        compose.runOnIdle {state.value = AndroidUpdateState.Current}
        compose.onNodeWithTag("android-update-status").assertTextEquals("The Android client is up to date.")
        compose.runOnIdle {state.value = AndroidUpdateState.Failed(AndroidUpdateFailure.ACCESS)}
        compose.onNodeWithTag("android-update-status").assertTextContains("Cannot access the Android release API", substring = true)
        compose.onNodeWithTag("about-desktop-latest").assertDoesNotExist()
        saveVisualEvidence(compose.activity, compose.onRoot(), "android-update-inaccessible-${if(dark) "dark" else "light"}.png")
        val release = AndroidRelease(AndroidReleaseVersion(0, 2, 61), "${AndroidReleases.repository}/releases/tag/v0.2.61", "${AndroidReleases.repository}/releases/download/v0.2.61/token-monitor-android-0.2.61.apk")
        compose.runOnIdle {state.value = AndroidUpdateState.Available(release)}
        compose.onNodeWithTag("android-update-status").assertTextEquals("Android 0.2.61 is available.")
        compose.onNodeWithTag("android-update-new-notes").performScrollTo().performClick()
        compose.onNodeWithTag("android-update-apk").performScrollTo().performClick()
        assertEquals(listOf(release.notesUrl, release.apkUrl), urls.takeLast(2))
        saveVisualEvidence(compose.activity, compose.onRoot(), "android-update-available-${if(dark) "dark" else "light"}.png")
    }
    @Test fun desktopQueryIsAbsentWhileAndroidNotesAndLicensesRemain() {
        var checks=0;var licenses=0;val urls=mutableListOf<String>()
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,LanguageChoice.ENGLISH) {
            Column(Modifier.background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
                AndroidUpdateContent("0.77.0-02",AndroidUpdateState.Idle,{checks++},urls::add,{licenses++})
            }
        }}}
        listOf("about-desktop-latest","desktop-release-notes","desktop-release-status").forEach {compose.onNodeWithTag(it).assertDoesNotExist()}
        compose.onNodeWithTag("android-update-status").assertDoesNotExist()
        compose.onNodeWithTag("android-update-current-notes").performClick()
        compose.onNodeWithTag("about-open-licenses").performClick()
        assertEquals(listOf(AndroidReleases.currentNotes("0.77.0-02")),urls)
        assertEquals(1,licenses);assertEquals(0,checks)
        compose.onNodeWithTag("android-update-check").performClick();assertEquals(1,checks)
        saveVisualEvidence(compose.activity,compose.onRoot(),"compact-about-${if(dark) "dark" else "light"}.png")
    }
    @Test fun openingSettingsAndResumingKeepAndroidQueryManual() {
        val android=AtomicInteger()
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,LanguageChoice.CHINESE) {
            Column(Modifier.background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
                AndroidUpdateSettings("0.72.0-01",source=AndroidReleaseSource {android.incrementAndGet();awaitCancellation()})
            }
        }}}
        compose.waitForIdle();assertEquals(0,android.get())
        compose.onNodeWithTag("android-update-check").performClick()
        compose.onNodeWithTag("about-desktop-latest").assertDoesNotExist()
        compose.waitUntil {android.get()==1}
        compose.onNodeWithTag("android-update-check").assertIsNotEnabled()
        compose.onNodeWithTag("about-desktop-latest").assertDoesNotExist()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle();assertEquals(1,android.get())
        compose.onNodeWithTag("android-update-check").assertIsEnabled()
        compose.onNodeWithTag("about-desktop-latest").assertDoesNotExist()
        saveVisualEvidence(compose.activity,compose.onRoot(),"updates-manual-idle.png")
    }
    @Test fun realLifecycleCancelsInFlightCheckAndResumeStaysManual() {
        val calls = AtomicInteger(0)
        val cancelled = AtomicBoolean(false)
        val source = AndroidReleaseSource {
            calls.incrementAndGet()
            try {awaitCancellation()} finally {cancelled.set(true)}
        }
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark, LanguageChoice.ENGLISH) {AndroidUpdateSettings("0.2.60", source)}}}
        compose.onNodeWithTag("android-update-check").performClick()
        compose.waitUntil {calls.get() == 1}
        compose.onNodeWithTag("android-update-status").assertTextEquals("Checking Android releases…")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        // Job.cancel immediately marks the request cancelled; its suspended finally resumes
        // on the main dispatcher. Await that evidence without returning the app to foreground.
        compose.waitUntil(timeoutMillis = 5_000) {cancelled.get()}
        assertTrue(cancelled.get())
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("android-update-status").assertDoesNotExist()
        compose.onNodeWithTag("android-update-check").assertIsEnabled()
        assertEquals(1, calls.get())
    }
}
