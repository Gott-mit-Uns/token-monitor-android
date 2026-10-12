package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

/** Budget ownership and response identity, independently of transport and screen timers. */
class HistoryReadCoordinatorTest {
    private val key = HistoryReadKey(7, "public-history-r1")

    @Test fun initialTargetQueuesWithoutInventingANetworkRequest() {
        val coordinator = HistoryReadCoordinator()
        assertFalse(coordinator.canStart(key, false))
        assertNull(coordinator.begin(key, 1, false, true))
        val queued = coordinator.target(key)
        assertEquals(HistoryReadPhase.QUEUED, queued.phase)
        assertEquals(0, queued.attempt)
        assertNull(queued.requestId)
        assertFalse(queued.accepted)
        assertTrue(queued.allowsAutomaticRead)
        assertSame(queued, coordinator.target(key))
    }

    @Test fun onlyAcceptedStartsConsumeFourAttemptsAndManualStartOpensANewCycle() {
        val coordinator = HistoryReadCoordinator()
        val cycle = coordinator.target(key).cycleId
        repeat(4) { index ->
            val start = coordinator.begin(key, (index + 1).toLong(), false, true)!!
            assertEquals(cycle, start.cycleId)
            assertEquals(index + 1, start.attempt)
            assertFalse(coordinator.canStart(key, false))
            assertFalse(coordinator.canStart(key, true))
            assertNull(coordinator.begin(key, 100, true, true))
            assertSame(start, coordinator.receipt)
            val failed = coordinator.finish(start, HistoryReadPhase.FAILED)!!
            assertEquals(index < 3, failed.allowsAutomaticRead)
        }
        assertNull(coordinator.begin(key, 5, false, true))
        assertEquals(4, coordinator.receipt!!.attempt)
        val manual = coordinator.begin(key, 6, true, true)!!
        assertTrue(manual.cycleId > cycle)
        assertEquals(1, manual.attempt)
        assertEquals(6L, manual.requestId)
        coordinator.finish(manual, HistoryReadPhase.FAILED)
        val retry = coordinator.begin(key, 7, false, true)!!
        assertEquals(manual.cycleId, retry.cycleId)
        assertEquals(2, retry.attempt)
    }

    @Test fun trustedCacheCompletesOnlyInitialTargetAndCannotCompleteAManualCycle() {
        val coordinator = HistoryReadCoordinator()
        val cached = coordinator.target(key, 9)
        assertEquals(HistoryReadPhase.CACHED, cached.phase)
        assertTrue(cached.accepted)
        assertNull(cached.requestId)
        assertFalse(coordinator.canStart(key, false))
        val manual = coordinator.begin(key, 12, true, true)!!
        assertTrue(manual.cycleId > cached.cycleId)
        assertSame(manual, coordinator.target(key, 9))
        val failed = coordinator.finish(manual, HistoryReadPhase.FAILED)!!
        assertNull(failed.fetchedDays)
        assertFalse(failed.accepted)
        assertTrue(failed.allowsAutomaticRead)
        assertSame(failed, coordinator.target(key, 9))
    }

    @Test fun onlyFetchedResultAndFrozenPreviewDecideAcceptance() {
        for (preview in listOf(false, true)) {
            for (days in listOf(0, 2)) {
                val coordinator = HistoryReadCoordinator()
                coordinator.target(key)
                val start = coordinator.begin(key, 1, false, preview)!!
                val resolved = coordinator.finish(start, HistoryReadPhase.RESOLVED, days)!!
                assertEquals(preview, resolved.previewHadDays)
                assertEquals(days, resolved.fetchedDays)
                assertEquals(days > 0 || !preview, resolved.accepted)
                assertEquals(days == 0 && preview, resolved.allowsAutomaticRead)
            }
        }
    }

    @Test fun failureWithoutPreviewCannotAutomaticallyRetryButManualRemainsAvailable() {
        val coordinator = HistoryReadCoordinator()
        coordinator.target(key)
        val start = coordinator.begin(key, 1, false, false)!!
        val failed = coordinator.finish(start, HistoryReadPhase.FAILED)!!
        assertFalse(failed.accepted)
        assertFalse(failed.allowsAutomaticRead)
        assertNull(failed.fetchedDays)
        assertFalse(coordinator.canStart(key, false))
        assertTrue(coordinator.canStart(key, true))
    }

    @Test fun sameTargetCancellationWithoutPreviewCanResumeButDoesNotRefundItsAttempt() {
        for (phase in listOf(HistoryReadPhase.CANCELLED, HistoryReadPhase.STALE)) {
            val coordinator = HistoryReadCoordinator()
            coordinator.target(key)
            val start = coordinator.begin(key, 1, false, false)!!
            val cancelled = coordinator.finish(start, phase)!!
            assertEquals(1, cancelled.attempt)
            assertFalse(cancelled.previewHadDays)
            assertTrue(cancelled.allowsAutomaticRead)
            assertSame(cancelled, coordinator.target(key))
            val resumed = coordinator.begin(key, 2, false, false)!!
            assertEquals(start.cycleId, resumed.cycleId)
            assertEquals(2, resumed.attempt)
            coordinator.finish(resumed, HistoryReadPhase.FAILED)
            assertFalse(coordinator.receipt!!.allowsAutomaticRead)
        }
    }

    @Test fun duplicateWrongRequestAndPreviousCycleRepliesCannotFinishCurrentRead() {
        val coordinator = HistoryReadCoordinator()
        coordinator.target(key)
        val first = coordinator.begin(key, 21, false, true)!!
        assertFalse(coordinator.matches(first.copy(requestId = 22)))
        assertFalse(coordinator.matches(first.copy(cycleId = first.cycleId + 1)))
        assertFalse(coordinator.matches(first.copy(key = key.copy(signature = "other"))))
        assertNull(coordinator.finish(first.copy(requestId = 22), HistoryReadPhase.RESOLVED, 2))
        val failed = coordinator.finish(first, HistoryReadPhase.FAILED)!!
        assertNull(coordinator.finish(first, HistoryReadPhase.RESOLVED, 2))
        assertSame(failed, coordinator.receipt)
        val manual = coordinator.begin(key, 23, true, true)!!
        assertNull(coordinator.finish(first, HistoryReadPhase.RESOLVED, 2))
        assertSame(manual, coordinator.receipt)
        assertTrue(coordinator.matches(manual))
    }

    @Test fun cancellationAndRevalidationNeverRefundAnAttempt() {
        val coordinator = HistoryReadCoordinator()
        coordinator.target(key)
        val first = coordinator.begin(key, 1, false, true)!!
        val cancelled = coordinator.cancelActive()!!
        assertEquals(HistoryReadPhase.CANCELLED, cancelled.phase)
        assertEquals(first.cycleId, cancelled.cycleId)
        assertEquals(1, cancelled.attempt)
        assertNull(coordinator.cancelActive())
        assertSame(cancelled, coordinator.target(key))
        val second = coordinator.begin(key, 2, false, true)!!
        val blocked = coordinator.finish(second, HistoryReadPhase.AUTH_BLOCKED)!!
        assertFalse(blocked.allowsAutomaticRead)
        assertNull(coordinator.begin(key, 3, false, true))
        val revalidated = coordinator.revalidated()!!
        assertEquals(HistoryReadPhase.CANCELLED, revalidated.phase)
        assertEquals(second.cycleId, revalidated.cycleId)
        assertEquals(2, revalidated.attempt)
        assertTrue(revalidated.allowsAutomaticRead)
        assertEquals(3, coordinator.begin(key, 4, false, true)!!.attempt)
    }

    @Test fun statsQueueCreatesIntentWithoutRequestIdentityOrConsumedAttempt() {
        val coordinator = HistoryReadCoordinator()
        coordinator.target(key, 2)
        val first = coordinator.begin(key, 9, true, true)!!
        coordinator.finish(first, HistoryReadPhase.FAILED)
        val queued = coordinator.queue(key)
        assertTrue(queued.cycleId > first.cycleId)
        assertEquals(HistoryReadPhase.QUEUED, queued.phase)
        assertEquals(0, queued.attempt)
        assertNull(queued.requestId)
        assertTrue(queued.bindingRequested)
        assertTrue(queued.allowsAutomaticRead)
        assertSame(queued, coordinator.target(key, 2))
        assertNull(coordinator.finish(first, HistoryReadPhase.RESOLVED, 2))
        assertEquals(1, coordinator.begin(key, 10, false, true)!!.attempt)
    }

    @Test fun legacyEmptySignatureKeepsRequestedMarkerAcrossForceAndTargetChangesWithinBinding() {
        val coordinator = HistoryReadCoordinator()
        val empty = key.copy(signature = "")
        coordinator.target(empty)
        val start = coordinator.begin(empty, 1, false, false)!!
        coordinator.finish(start, HistoryReadPhase.RESOLVED, 0)
        val forced = coordinator.queue(empty)
        assertEquals(0, forced.attempt)
        assertTrue(forced.bindingRequested)
        assertFalse(forced.allowsAutomaticRead)
        assertNull(coordinator.begin(empty, 2, false, false))
        assertNotNull(coordinator.begin(empty, 3, true, false))
        coordinator.target(key)
        val emptyAgain = coordinator.target(empty)
        assertTrue(emptyAgain.bindingRequested)
        assertFalse(emptyAgain.allowsAutomaticRead)
        val newBinding = coordinator.target(empty.copy(connectionRevision = 8))
        assertFalse(newBinding.bindingRequested)
        assertTrue(newBinding.allowsAutomaticRead)
    }

    @Test fun revisionAndBindingChangesFenceOldRepliesWhileResetKeepsCycleIdsMonotonic() {
        val coordinator = HistoryReadCoordinator()
        coordinator.target(key)
        val old = coordinator.begin(key, 1, false, true)!!
        val nextKey = key.copy(signature = "public-history-r2")
        val next = coordinator.target(nextKey)
        assertTrue(next.cycleId > old.cycleId)
        assertEquals(0, next.attempt)
        assertNull(coordinator.finish(old, HistoryReadPhase.RESOLVED, 2))
        val pending = coordinator.begin(nextKey, 2, false, true)!!
        val rebound = coordinator.target(nextKey.copy(connectionRevision = 9))
        assertFalse(rebound.bindingRequested)
        assertNull(coordinator.finish(pending, HistoryReadPhase.FAILED))
        coordinator.reset()
        assertNull(coordinator.receipt)
        assertNull(coordinator.revalidated())
        val reset = coordinator.target(key)
        assertTrue(reset.cycleId > rebound.cycleId)
        assertFalse(reset.bindingRequested)
        assertNull(coordinator.finish(old, HistoryReadPhase.RESOLVED, 2))
    }

    @Test fun invalidTerminalShapesAreRejectedWithoutChangingTheActiveRead() {
        val coordinator = HistoryReadCoordinator()
        coordinator.target(key)
        val start = coordinator.begin(key, 1, false, true)!!
        assertThrows(IllegalArgumentException::class.java) { coordinator.finish(start, HistoryReadPhase.STARTED) }
        assertThrows(IllegalArgumentException::class.java) { coordinator.finish(start, HistoryReadPhase.RESOLVED) }
        assertThrows(IllegalArgumentException::class.java) { coordinator.finish(start, HistoryReadPhase.RESOLVED, -1) }
        assertThrows(IllegalArgumentException::class.java) { coordinator.finish(start, HistoryReadPhase.FAILED, 1) }
        assertSame(start, coordinator.receipt)
    }
}
