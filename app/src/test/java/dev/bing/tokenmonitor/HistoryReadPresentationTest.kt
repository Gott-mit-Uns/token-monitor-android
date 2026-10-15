package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class HistoryReadPresentationTest {
    private val key = HistoryReadKey(7, "public-history-r1")

    @Test fun onlyStartedAndHistoryOwnedFailuresChangeVisibleReadStatus() {
        for (phase in HistoryReadPhase.entries) {
            val receipt = HistoryReadReceipt(key, 1, 9, 1, phase, fetchedDays = if (phase == HistoryReadPhase.RESOLVED) 2 else null)
            val shown = HistoryReadPresentation.project(key, receipt)
            assertEquals(phase == HistoryReadPhase.STARTED, shown.loading)
            assertEquals(when (phase) {
                HistoryReadPhase.FAILED -> R.string.record_read_failed
                HistoryReadPhase.AUTH_BLOCKED -> R.string.error_stream_auth
                else -> null
            }, shown.error)
        }
    }

    @Test fun missingPreviousBindingAndPreviousSignatureReceiptsCannotClaimLoadingOrFailure() {
        assertEquals(HistoryReadPresentation(), HistoryReadPresentation.project(key, null))
        for (oldKey in listOf(key.copy(connectionRevision = 6), key.copy(signature = "public-history-r0"))) {
            for (phase in listOf(HistoryReadPhase.STARTED, HistoryReadPhase.FAILED, HistoryReadPhase.AUTH_BLOCKED)) {
                assertEquals(HistoryReadPresentation(), HistoryReadPresentation.project(key,
                    HistoryReadReceipt(oldKey, 100, 999, 4, phase)))
            }
        }
    }

    @Test fun acceptedCacheAndNewTargetRetainExistingCoordinatorResponseIsolation() {
        val coordinator = HistoryReadCoordinator()
        val cached = coordinator.target(key, trustedCachedDays = 2)
        assertEquals(HistoryReadPresentation(), HistoryReadPresentation.project(key, cached))
        val oldStart = coordinator.begin(key, 1, manual = true, previewHadDays = true)!!
        assertTrue(HistoryReadPresentation.project(key, oldStart).loading)
        val nextKey = key.copy(signature = "public-history-r2")
        val nextCache = coordinator.target(nextKey, trustedCachedDays = 2)
        assertNull(coordinator.finish(oldStart, HistoryReadPhase.FAILED))
        assertSame(nextCache, coordinator.receipt)
        assertEquals(HistoryReadPresentation(), HistoryReadPresentation.project(nextKey, oldStart))
        assertEquals(HistoryReadPresentation(), HistoryReadPresentation.project(nextKey, nextCache))
        val nextStart = coordinator.begin(nextKey, 2, manual = true, previewHadDays = true)!!
        val blocked = coordinator.finish(nextStart, HistoryReadPhase.AUTH_BLOCKED)!!
        assertEquals(R.string.error_stream_auth, HistoryReadPresentation.project(nextKey, blocked).error)
    }
}
