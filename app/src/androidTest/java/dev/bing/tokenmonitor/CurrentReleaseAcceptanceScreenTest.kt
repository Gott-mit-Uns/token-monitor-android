package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Final APK and the real public canonical metadata must agree on the installed identity. */
class CurrentReleaseAcceptanceScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    /** Run after the canonical formal Release has been published as latest. */
    @Test fun publishedAndroidReleaseIsFetchedByTheRealAnonymousAndroidClient() {
        val expected=InstrumentationRegistry.getArguments().getString("expectedVersion") ?: "0.72.0-01"
        val release=kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {GitHubAndroidReleaseSource().latest()}
        assertEquals(AndroidReleaseVersion.parse(expected),release.version)
        assertEquals("${AndroidReleases.repository}/releases/tag/v$expected",release.notesUrl)
        assertEquals("${AndroidReleases.repository}/releases/download/v$expected/token-monitor-android-$expected.apk",release.apkUrl)
    }
    @Test fun finalInstalledReleaseHasExactNotesAndNoUpdateLoop() {
        val args=InstrumentationRegistry.getArguments()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val installed=context.packageManager.getPackageInfo(context.packageName,0)
        val expected=args.getString("expectedVersion") ?: "0.72.0-01"
        assertEquals(expected,installed.versionName)
        @Suppress("DEPRECATION")
        val installedCode=if(android.os.Build.VERSION.SDK_INT>=28) installed.longVersionCode else installed.versionCode.toLong()
        assertEquals((args.getString("expectedVersionCode") ?: "76").toLong(),installedCode)
        val path=args.getString("releaseMetadataPath")
        val metadata=if(path!=null) File(path).readText() else """{"name":"Token Monitor Android v$expected","draft":false,"prerelease":false,"tag_name":"v$expected","html_url":"${AndroidReleases.repository}/releases/tag/v$expected","assets":[{"name":"token-monitor-android-$expected.apk","state":"uploaded","size":12345,"browser_download_url":"${AndroidReleases.repository}/releases/download/v$expected/token-monitor-android-$expected.apk"}]}"""
        val parsed=AndroidReleases.parse(metadata)
        assertEquals(AndroidReleaseVersion.parse(expected),parsed.version)
        assertEquals("${AndroidReleases.repository}/releases/tag/v$expected",parsed.notesUrl)
        val urls=mutableListOf<String>();var checks=0
        val source=AndroidReleaseSource {checks++;AndroidReleases.parse(metadata)}
        val clock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(false,LanguageChoice.ENGLISH) {
            CompositionLocalProvider(LocalDesktopAnimationClock provides clock,LocalUriHandler provides object:UriHandler {override fun openUri(uri:String) {urls+=uri}}) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AndroidUpdateSettings(expected,source)
                }
            }
        }}}
        assertEquals(0,checks)
        compose.onNodeWithTag("android-update-current-notes").performClick()
        compose.onNodeWithTag("about-desktop-latest").assertDoesNotExist()
        assertEquals(listOf(AndroidReleases.currentNotes(expected)),urls)
        compose.onNodeWithTag("android-update-check").performClick()
        compose.onNodeWithTag("android-update-status").performScrollTo().assertTextEquals("The Android client is up to date.")
        compose.onNodeWithTag("android-update-apk").assertDoesNotExist()
        assertEquals(1,checks)
    }
}
