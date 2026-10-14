package dev.bing.tokenmonitor

import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Same test binary executes the installed 0.2.64 parser/UI and the new parser/UI. */
class LegacyUpdateCompatibilityScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val installed get()=context.packageManager.getPackageInfo(context.packageName,0).versionName!!
    private fun metadata():String {
        val path=InstrumentationRegistry.getArguments().getString("releaseMetadataPath")
        if(path!=null) return File(path).readText()
        return """{"name":"Token Monitor Android v0.71.0-01","draft":false,"prerelease":false,"tag_name":"v0.2.65","html_url":"https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/v0.2.65","assets":[{"name":"token-monitor-android-0.2.65.apk","state":"uploaded","size":12345,"browser_download_url":"https://github.com/Gott-mit-Uns/token-monitor-android/releases/download/v0.2.65/token-monitor-android-0.2.65.apk"}]}"""
    }
    private fun show():Pair<MutableList<String>,()->Int> {
        val urls=mutableListOf<String>();var calls=0
        val source=AndroidReleaseSource {calls++;AndroidReleases.parse(metadata())}
        val clock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
        val current=installed
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(false,LanguageChoice.ENGLISH) {
            CompositionLocalProvider(LocalDesktopAnimationClock provides clock, LocalUriHandler provides object:UriHandler {override fun openUri(uri:String) {urls+=uri}}) {AndroidUpdateSettings(current,source)}
        }}}
        return urls to {calls}
    }
    @Test fun exactInstalled064ParserRecognizesCanonicalReleaseAndShowsDownload() {
        assertEquals("0.2.64",installed)
        // Negative control distinguishes the installed legacy parser from the new main code.
        val composite=metadata().replace("v0.2.65","v0.71.0-android.0.2.65").replace("token-monitor-android-0.2.65.apk","token-monitor-android-0.71.0-android.0.2.65.apk")
        try {AndroidReleases.parse(composite);fail("Installed legacy parser must reject composite tags")}
        catch(failure:AndroidUpdateException) {assertEquals(AndroidUpdateFailure.INVALID_RELEASE,failure.reason)}
        val (urls,calls)=show();assertEquals(0,calls())
        compose.onNodeWithTag("android-update-check").performClick()
        compose.onNodeWithTag("android-update-status").assertTextEquals("Android 0.2.65 is available.")
        compose.onNodeWithTag("android-update-new-notes").performClick()
        compose.onNodeWithTag("android-update-apk").performClick()
        assertEquals(listOf("${AndroidReleases.repository}/releases/tag/v0.2.65","${AndroidReleases.repository}/releases/download/v0.2.65/token-monitor-android-0.2.65.apk"),urls)
        assertEquals(1,calls())
    }
    @Test fun installedDualVersionIsCurrentAndItsNotesUseCanonicalTag() {
        assertEquals("0.71.0-01",installed)
        val (urls,calls)=show();compose.onNodeWithTag("android-update-current-notes").performClick()
        assertEquals(listOf("${AndroidReleases.repository}/releases/tag/v0.71.0-01"),urls)
        compose.onNodeWithTag("android-update-check").performClick()
        compose.onNodeWithTag("android-update-status").assertTextEquals("The Android client is up to date.")
        assertEquals(1,calls())
    }
}
