package dev.bing.tokenmonitor

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DesktopReleaseTest {
    private fun body(tag:String="v0.72.0",repo:String=DesktopReleases.repository)="""{"draft":false,"prerelease":false,"tag_name":"$tag","html_url":"$repo/releases/tag/$tag"}"""
    @Test fun upstreamIdentityStableOnlyAndNoAndroidAssetRequirement() {
        val release=DesktopReleases.parse(body())
        assertEquals("0.72.0",release.version)
        assertEquals("https://github.com/Javis603/token-monitor/releases/tag/v0.72.0",release.notesUrl)
        listOf(body(repo=AndroidReleases.repository),body(repo="https://github.com/Gott-mit-Uns/token-monitor-nas"),body("v0.72.0-beta"),body("v0.72.0-01"),body().replace("\"draft\":false","\"draft\":true"),body().replace("\"prerelease\":false","\"prerelease\":true"),"{}").forEach {
            try {DesktopReleases.parse(it);fail("Invalid desktop release accepted")} catch(failure:AndroidUpdateException) {assertEquals(AndroidUpdateFailure.INVALID_RELEASE,failure.reason)}
        }
    }
    @Test fun httpFailuresRemainTruthful() {
        assertEquals(AndroidUpdateFailure.RATE_LIMIT,DesktopReleases.failure(429,null))
        assertEquals(AndroidUpdateFailure.RATE_LIMIT,DesktopReleases.failure(403,"0"))
        assertEquals(AndroidUpdateFailure.RATE_LIMIT,DesktopReleases.failure(403,"10","60"))
        assertEquals(AndroidUpdateFailure.ACCESS,DesktopReleases.failure(403,"10"))
        assertEquals(AndroidUpdateFailure.NETWORK,DesktopReleases.failure(500,null))
    }
    @Test fun separateChecksFailuresRetryAndBackgroundCancellation()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            var calls=0;var fail=true
            val desktop=DesktopReleaseController(DesktopReleaseSource {calls++;if(fail) throw AndroidUpdateException(AndroidUpdateFailure.NETWORK);DesktopReleases.parse(body())},scope)
            val android=AndroidUpdateController("0.71.0-01",AndroidReleaseSource {error("Desktop check must not invoke Android")},scope)
            desktop.check();assertEquals(0,calls)
            desktop.setForeground(true);desktop.check()
            assertEquals(DesktopReleaseState.Failed(AndroidUpdateFailure.NETWORK),desktop.state.value)
            assertEquals(AndroidUpdateState.Idle,android.state.value)
            fail=false;desktop.check();assertEquals(DesktopReleaseState.Ready(DesktopReleases.parse(body())),desktop.state.value)
            var cancelled=false
            val waiting=DesktopReleaseController(DesktopReleaseSource {try {awaitCancellation()} finally {cancelled=true}},scope)
            waiting.setForeground(true);waiting.check();assertEquals(DesktopReleaseState.Checking,waiting.state.value)
            waiting.setForeground(false);assertTrue(cancelled);assertEquals(DesktopReleaseState.Idle,waiting.state.value)
            waiting.setForeground(true);assertEquals(DesktopReleaseState.Idle,waiting.state.value)
        } finally {scope.cancel()}
    }
    @Test fun currentAndroidNotesNeverFollowLatest() {
        for(version in listOf("0.2.60","0.71.0-01","0.71.0-02")) assertEquals("${AndroidReleases.repository}/releases/tag/v$version",AndroidReleases.currentNotes(version))
    }
}
