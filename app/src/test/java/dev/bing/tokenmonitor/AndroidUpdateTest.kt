package dev.bing.tokenmonitor

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AndroidUpdateTest {
    private fun release(version: String = "0.2.61", extra: String = "", name: String = "token-monitor-android-$version.apk", repo: String = AndroidReleases.repository) = """{
        "draft":false,"prerelease":false,"tag_name":"v$version","html_url":"$repo/releases/tag/v$version",
        "assets":[{"name":"$name","state":"uploaded","size":12345,"browser_download_url":"$repo/releases/download/v$version/$name"}]$extra
    }"""
    private fun rejected(body: String) {
        try {AndroidReleases.parse(body); fail("Unverified release accepted")}
        catch (failure: AndroidUpdateException) {assertEquals(AndroidUpdateFailure.INVALID_RELEASE, failure.reason)}
    }
    @Test fun numericProjectVersionOrderAndStableOnly() {
        assertTrue(AndroidReleaseVersion.parse("v0.2.100")!! > AndroidReleaseVersion.parse("0.2.99")!!)
        assertTrue(AndroidReleaseVersion.parse("0.10.0")!! > AndroidReleaseVersion.parse("0.9.99")!!)
        assertEquals(AndroidReleaseVersion.parse("v0.2.60"), AndroidReleaseVersion.parse("0.2.60"))
        listOf("0.2.60-dev", "v0.2.61-rc.1", "0.2", "00.2.60", "0.2.60+build", "-1.2.3", "999999999999999999999.1.1").forEach {assertNull(it, AndroidReleaseVersion.parse(it))}
    }
    @Test fun verifiedOfficialAssetAndNotesOnly() {
        val parsed = AndroidReleases.parse(release())
        assertEquals("${AndroidReleases.repository}/releases/tag/v0.2.61", parsed.notesUrl)
        assertEquals("${AndroidReleases.repository}/releases/download/v0.2.61/token-monitor-android-0.2.61.apk", parsed.apkUrl)
        rejected(release().replace("\"draft\":false", "\"draft\":true"))
        rejected(release().replace("\"prerelease\":false", "\"prerelease\":true"))
        rejected(release("0.2.61-rc.1"))
        rejected(release(name="token-monitor-android-0.2.61-debug.apk"))
        rejected(release(repo="https://github.com/Gott-mit-Uns/token-monitor"))
        rejected(release().replace("\"size\":12345", "\"size\":0"))
        rejected(release().replace("\"uploaded\"", "\"new\""))
        rejected(release().replace("/releases/download/", "/other/download/"))
        rejected(release().replace("\"assets\":[", "\"assets\":[] ,\"ignored\":["))
        val asset = """{"name":"token-monitor-android-0.2.61.apk","state":"uploaded","size":12345,"browser_download_url":"${AndroidReleases.repository}/releases/download/v0.2.61/token-monitor-android-0.2.61.apk"}"""
        rejected(release().replace("\"assets\":[", "\"assets\":[$asset,"))
    }
    @Test fun noAutomaticChecksAndFailuresRecoverWithoutHub() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var calls = 0
            var failed = true
            val controller = AndroidUpdateController("0.2.60", AndroidReleaseSource {
                calls++
                if (failed) throw AndroidUpdateException(AndroidUpdateFailure.ACCESS)
                AndroidReleases.parse(release())
            }, scope)
            controller.check(); assertEquals(0, calls)
            controller.setForeground(true); assertEquals(0, calls)
            controller.check(); assertEquals(AndroidUpdateState.Failed(AndroidUpdateFailure.ACCESS), controller.state.value)
            failed = false; controller.check(); assertTrue(controller.state.value is AndroidUpdateState.Available)
            assertEquals(2, calls)
            controller.setForeground(false); controller.setForeground(true); assertEquals(2, calls)
        } finally {scope.cancel()}
    }
    @Test fun equalAndOlderStableVersionsAreCurrent() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            for (version in listOf("0.2.60", "0.2.59")) {
                val controller = AndroidUpdateController("0.2.60", AndroidReleaseSource {AndroidReleases.parse(release(version))}, scope)
                controller.setForeground(true); controller.check()
                assertEquals(AndroidUpdateState.Current, controller.state.value)
            }
        } finally {scope.cancel()}
    }
    @Test fun backgroundCancelsRequestDoesNotRestartAndGuardsStaleResponse() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val pending = CompletableDeferred<AndroidRelease>()
        var cancelled = false
        var calls = 0
        val controller = AndroidUpdateController("0.2.60", AndroidReleaseSource {
            calls++
            try {pending.await()} finally {cancelled = true}
        }, scope)
        try {
            controller.setForeground(true); controller.check(); controller.check()
            assertEquals(1, calls); assertEquals(AndroidUpdateState.Checking, controller.state.value)
            controller.setForeground(false)
            assertTrue(cancelled); assertEquals(AndroidUpdateState.Idle, controller.state.value)
            controller.setForeground(true); assertEquals(1, calls)
            pending.complete(AndroidReleases.parse(release()))
            assertEquals(AndroidUpdateState.Idle, controller.state.value)
            controller.check(); assertEquals(2, calls)
            assertTrue(controller.state.value is AndroidUpdateState.Available)
        } finally {scope.cancel()}
    }
}
