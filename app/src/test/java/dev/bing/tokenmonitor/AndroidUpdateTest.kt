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
    @Test fun canonicalAndroidTagSupportsDualInstalledVersionAndFutureUpdates() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val installed = "0.71.0-android.0.2.65"
            assertEquals("${AndroidReleases.repository}/releases/tag/v0.2.65", AndroidReleases.currentNotes(installed))
            val current = AndroidUpdateController(installed, AndroidReleaseSource {AndroidReleases.parse(release("0.2.65"))}, scope)
            current.setForeground(true); current.check(); assertEquals(AndroidUpdateState.Current,current.state.value)
            val future = AndroidUpdateController(installed, AndroidReleaseSource {AndroidReleases.parse(release("0.2.66"))}, scope)
            future.setForeground(true); future.check(); assertTrue(future.state.value is AndroidUpdateState.Available)
            val old = AndroidUpdateController("0.2.64", AndroidReleaseSource {AndroidReleases.parse(release("0.2.65"))}, scope)
            old.setForeground(true); old.check(); assertTrue(old.state.value is AndroidUpdateState.Available)
            assertEquals("${AndroidReleases.repository}/releases/download/v0.2.65/token-monitor-android-0.2.65.apk", (old.state.value as AndroidUpdateState.Available).release.apkUrl)
        } finally {scope.cancel()}
    }
    @Test fun upstreamRevisionAndLegacyBridgeCompareCurrentAndFuture() {
        val installed=AndroidReleaseVersion.parse("0.71.0-01")!!
        assertTrue(installed>AndroidReleaseVersion.parse("0.2.64")!!)
        assertTrue(AndroidReleaseVersion.parse("0.71.0-02")!!>installed)
        assertTrue(AndroidReleaseVersion.parse("0.72.0-01")!!>installed)
        assertNull(AndroidReleaseVersion.parse("0.71.0-00"))
        assertNull(AndroidReleaseVersion.parse("0.71.0-1"))
        assertEquals("${AndroidReleases.repository}/releases/tag/v0.71.0-01",AndroidReleases.currentNotes(installed.toString()))
        val bridge=AndroidReleases.parse(release("0.2.65",extra=",\"name\":\"Token Monitor Android v0.71.0-01\""))
        assertEquals(installed,bridge.version)
        assertEquals(AndroidReleases.currentNotes(installed.toString()),bridge.notesUrl)
        assertTrue(AndroidReleases.parse(release("0.2.66",extra=",\"name\":\"Token Monitor Android v0.71.0-02\"" )).version>installed)
        assertEquals(installed,AndroidReleases.parse(release("0.71.0-01")).version)
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
    @Test fun foregroundAutoChecksArePersistedThrottledAndManualCheckRemainsAvailable() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var now = 1_000_000_000L
            var persisted = 0L
            var calls = 0
            val source = AndroidReleaseSource { calls++; AndroidReleases.parse(release("0.72.0-01")) }
            fun controller() = AndroidUpdateController("0.71.0-02", source, scope, { now }, { persisted }, { persisted = it })
            val first = controller()
            first.checkOnForeground(); assertEquals(0, calls)
            first.setForeground(true); first.checkOnForeground()
            assertEquals(1, calls); assertEquals(now, persisted)
            repeat(5) { first.setForeground(false); first.setForeground(true); first.checkOnForeground() }
            assertEquals(1, calls)
            val recreated = controller()
            recreated.setForeground(true); recreated.checkOnForeground(); assertEquals(1, calls)
            now += AndroidUpdateController.AUTOMATIC_CHECK_INTERVAL_MS - 1
            recreated.checkOnForeground(); assertEquals(1, calls)
            now++
            recreated.checkOnForeground(); assertEquals(2, calls)
            recreated.check(); assertEquals(3, calls)
            // A clock correction must not disable checks indefinitely.
            now -= 2 * AndroidUpdateController.AUTOMATIC_CHECK_INTERVAL_MS
            recreated.checkOnForeground(); assertEquals(4, calls)
        } finally { scope.cancel() }
    }
    @Test fun failedAndCancelledAutomaticChecksRemainRetryableWithoutBackgroundWork() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var now = 1_000_000_000L
            var calls = 0
            var fail = true
            var persisted = 0L
            val controller = AndroidUpdateController("0.71.0-02", AndroidReleaseSource {
                calls++
                if (fail) throw AndroidUpdateException(AndroidUpdateFailure.NETWORK)
                AndroidReleases.parse(release("0.72.0-01"))
            }, scope, { now }, { persisted }, { persisted = it })
            controller.setForeground(true); controller.checkOnForeground()
            assertEquals(1, calls); assertEquals(0L, persisted)
            controller.setForeground(false); controller.setForeground(true); controller.checkOnForeground()
            assertEquals(1, calls)
            now += AndroidUpdateController.AUTOMATIC_FAILURE_INTERVAL_MS
            fail = false; controller.checkOnForeground(); assertEquals(2, calls)
            assertTrue(controller.state.value is AndroidUpdateState.Available)
            val pending = CompletableDeferred<AndroidRelease>()
            val cancelled = AndroidUpdateController("0.71.0-02", AndroidReleaseSource { calls++; pending.await() }, scope,
                { now }, { 0L }, { persisted = it })
            cancelled.setForeground(true); cancelled.checkOnForeground()
            assertEquals(3, calls)
            cancelled.setForeground(false); assertEquals(AndroidUpdateState.Idle, cancelled.state.value)
            pending.complete(AndroidReleases.parse(release("0.72.0-01")))
            cancelled.checkOnForeground(); assertEquals(3, calls)
            cancelled.setForeground(true); cancelled.checkOnForeground()
            assertEquals(4, calls); assertTrue(cancelled.state.value is AndroidUpdateState.Available)
        } finally { scope.cancel() }
    }
    @Test fun nextCompatibilityBridgeWorksForLegacyAndCurrentReleaseFamiliesWithoutDowngrade() {
        val bridge = AndroidReleases.parse(release("0.2.67", extra=",\"name\":\"Token Monitor Android v0.72.0-01\""))
        assertEquals("0.72.0-01", bridge.version.toString())
        assertEquals("${AndroidReleases.repository}/releases/tag/v0.72.0-01", bridge.notesUrl)
        assertTrue(bridge.apkUrl.endsWith("/v0.2.67/token-monitor-android-0.2.67.apk"))
        listOf("0.2.64", "0.2.66", "0.71.0-android.0.2.65", "0.71.0-02").forEach {
            assertTrue(it, bridge.version > AndroidReleaseVersion.parse(it)!!)
        }
        assertEquals(0, bridge.version.compareTo(AndroidReleaseVersion.parse("0.72.0-01")!!))
        assertTrue(bridge.version < AndroidReleaseVersion.parse("0.72.0-02")!!)
    }

    @Test fun automaticFailureBackoffSurvivesControllerRecreationAndManualSuccessClearsIt() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var now = 1_000_000_000L
            var failureAt: Long? = null
            var successAt = 0L
            var calls = 0
            var failing = true
            val source = AndroidReleaseSource {
                calls++
                if (failing) throw AndroidUpdateException(AndroidUpdateFailure.NETWORK)
                AndroidReleases.parse(release("0.72.0-01"))
            }
            fun controller() = AndroidUpdateController("0.71.0-02", source, scope, { now },
                { successAt }, { successAt = it }, { failureAt }, { failureAt = it })
            val first = controller()
            first.setForeground(true); first.checkOnForeground()
            assertEquals(1, calls); assertEquals(now, failureAt); assertEquals(0L, successAt)
            first.setForeground(false)
            val recreated = controller()
            recreated.setForeground(true); recreated.checkOnForeground()
            assertEquals(1, calls)
            now += AndroidUpdateController.AUTOMATIC_FAILURE_INTERVAL_MS - 1
            recreated.checkOnForeground(); assertEquals(1, calls)
            now++
            recreated.checkOnForeground(); assertEquals(2, calls); assertEquals(now, failureAt)
            // Explicit manual retry bypasses the persisted interval. A failure
            // does not move the automatic timestamp or misreport success.
            val savedFailure = failureAt
            recreated.check(); assertEquals(3, calls); assertEquals(savedFailure, failureAt)
            failing = false
            recreated.check(); assertEquals(4, calls)
            assertNull(failureAt); assertEquals(now, successAt)
            recreated.setForeground(false)
            val afterSuccess = controller()
            afterSuccess.setForeground(true); afterSuccess.checkOnForeground()
            assertEquals(4, calls)
        } finally { scope.cancel() }
    }
    @Test fun cancelledAutomaticAttemptDoesNotPersistFailureAndClockRollbackCanRecoverPersistedBackoff() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var now = 1_000_000_000L
            var failureAt: Long? = null
            var writes = 0
            var calls = 0
            val pending = CompletableDeferred<AndroidRelease>()
            val cancelled = AndroidUpdateController("0.71.0-02", AndroidReleaseSource { calls++; pending.await() }, scope,
                { now }, { 0L }, {}, { failureAt }, { failureAt = it; writes++ })
            cancelled.setForeground(true); cancelled.checkOnForeground()
            assertEquals(1, calls)
            cancelled.setForeground(false)
            pending.complete(AndroidReleases.parse(release("0.72.0-01")))
            assertNull(failureAt); assertEquals(0, writes)
            val recreated = AndroidUpdateController("0.71.0-02", AndroidReleaseSource {
                calls++; throw AndroidUpdateException(AndroidUpdateFailure.NETWORK)
            }, scope, { now }, { 0L }, {}, { failureAt }, { failureAt = it; writes++ })
            recreated.setForeground(true); recreated.checkOnForeground()
            assertEquals(2, calls); assertEquals(now, failureAt); assertEquals(1, writes)
            recreated.setForeground(false)
            // A persisted future failure timestamp must not suppress checks
            // forever after the user corrects the device clock backwards.
            now -= 10_000L
            val rolledBack = AndroidUpdateController("0.71.0-02", AndroidReleaseSource {
                calls++; AndroidReleases.parse(release("0.72.0-01"))
            }, scope, { now }, { 0L }, {}, { failureAt }, { failureAt = it; writes++ })
            rolledBack.setForeground(true); rolledBack.checkOnForeground()
            assertEquals(3, calls); assertNull(failureAt)
            assertTrue(rolledBack.state.value is AndroidUpdateState.Available)
        } finally { scope.cancel() }
    }

}
