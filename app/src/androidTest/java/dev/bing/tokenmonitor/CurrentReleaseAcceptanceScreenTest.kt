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

/** Final APK and the real public compatibility metadata must agree on the installed identity. */
class CurrentReleaseAcceptanceScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    /** Run only after both official distribution entries have been published. */
    @Test fun publishedAndroidReleaseIsFetchedByTheRealAnonymousAndroidClient() {
        val expected=InstrumentationRegistry.getArguments().getString("expectedVersion") ?: "0.71.0-02"
        val release=kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {GitHubAndroidReleaseSource().latest()}
        assertEquals(AndroidReleaseVersion.parse(expected),release.version)
        assertEquals("${AndroidReleases.repository}/releases/tag/v$expected",release.notesUrl)
        assertEquals("${AndroidReleases.repository}/releases/download/v0.2.66/token-monitor-android-0.2.66.apk",release.apkUrl)
    }
    @Test fun upstreamDesktopReleaseIsFetchedByTheRealAnonymousAndroidClient() {
        val expected=InstrumentationRegistry.getArguments().getString("expectedDesktopVersion") ?: "0.72.0"
        val source=GitHubDesktopReleaseSource()
        val release=try {kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {source.latest()}}
        catch(failure:AndroidUpdateException) {
            // Public-endpoint diagnostics only. Reuse this exact anonymous client without changing its configuration.
            val diagnostic=runCatching {kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                val field=source.javaClass.getDeclaredField("client").apply {isAccessible=true}
                val client=field.get(source) as okhttp3.OkHttpClient
                client.newCall(okhttp3.Request.Builder().url(DesktopReleases.apiUrl).header("Accept","application/vnd.github+json").header("X-GitHub-Api-Version","2022-11-28").build()).execute().use {
                    val body=it.body.source();body.request(2048)
                    val text=body.readUtf8(minOf(body.buffer.size,2048L))
                    val message=runCatching {org.json.JSONObject(text).optString("message")}.getOrDefault("non-JSON error").replace(Regex("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+"),"[address]").take(256)
                    "HTTP ${it.code}; remaining=${it.header("X-RateLimit-Remaining")}; reset=${it.header("X-RateLimit-Reset")}; retryAfter=${it.header("Retry-After")}; message=$message"
                }
            }}.getOrElse {it.javaClass.simpleName}
            throw AssertionError("Real anonymous desktop query failed: ${failure.reason}; $diagnostic",failure)
        }
        assertEquals(expected,release.version)
        assertEquals("${DesktopReleases.repository}/releases/tag/v$expected",release.notesUrl)
    }
    @Test fun finalInstalledReleaseHasExactNotesAndNoUpdateLoop() {
        val args=InstrumentationRegistry.getArguments()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val installed=context.packageManager.getPackageInfo(context.packageName,0)
        val expected=args.getString("expectedVersion") ?: "0.71.0-02"
        assertEquals(expected,installed.versionName)
        @Suppress("DEPRECATION")
        val installedCode=if(android.os.Build.VERSION.SDK_INT>=28) installed.longVersionCode else installed.versionCode.toLong()
        assertEquals(75L,installedCode)
        val path=args.getString("releaseMetadataPath")
        val metadata=if(path!=null) File(path).readText() else """{"name":"Token Monitor Android v$expected","draft":false,"prerelease":false,"tag_name":"v0.2.66","html_url":"${AndroidReleases.repository}/releases/tag/v0.2.66","assets":[{"name":"token-monitor-android-0.2.66.apk","state":"uploaded","size":12345,"browser_download_url":"${AndroidReleases.repository}/releases/download/v0.2.66/token-monitor-android-0.2.66.apk"}]}"""
        val parsed=AndroidReleases.parse(metadata)
        assertEquals(AndroidReleaseVersion.parse(expected),parsed.version)
        assertEquals("${AndroidReleases.repository}/releases/tag/v$expected",parsed.notesUrl)
        val urls=mutableListOf<String>();var checks=0
        val source=AndroidReleaseSource {checks++;AndroidReleases.parse(metadata)}
        val clock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(false,LanguageChoice.ENGLISH) {
            CompositionLocalProvider(LocalDesktopAnimationClock provides clock,LocalUriHandler provides object:UriHandler {override fun openUri(uri:String) {urls+=uri}}) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AndroidUpdateSettings(expected,source,desktopSource=DesktopReleaseSource {DesktopRelease("0.72.0","${DesktopReleases.repository}/releases/tag/v0.72.0")})
                }
            }
        }}}
        assertEquals(0,checks)
        compose.onNodeWithTag("android-update-current-notes").performClick()
        compose.onNodeWithTag("about-desktop-latest").performClick()
        assertEquals(listOf(AndroidReleases.currentNotes(expected),"${DesktopReleases.repository}/releases/tag/v0.72.0"),urls)
        compose.onNodeWithTag("android-update-check").performClick()
        compose.onNodeWithTag("android-update-status").performScrollTo().assertTextEquals("The Android client is up to date.")
        compose.onNodeWithTag("android-update-apk").assertDoesNotExist()
        assertEquals(1,checks)
    }
}
