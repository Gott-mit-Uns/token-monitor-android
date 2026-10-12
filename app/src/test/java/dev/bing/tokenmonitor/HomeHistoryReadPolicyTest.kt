package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class HomeHistoryReadPolicyTest {
    private val key = HistoryReadKey(7, "public-r1")
    private fun receipt(phase: HistoryReadPhase, attempt: Int = 0, cycle: Long = 1,
        preview: Boolean = true, days: Int? = null, target: HistoryReadKey = key,
        request: Long? = attempt.takeIf { it > 0 }?.toLong()) =
        HistoryReadReceipt(target, cycle, request, attempt, phase, preview, days, bindingRequested = attempt > 0)

    @Test fun observedShortGapCannotDispatchARetryForTheSameHistoryTarget() {
        val coordinator = HistoryReadCoordinator()
        val schedule = HomeHistoryReadSchedule(key)
        schedule.observe(63227, true, false, coordinator.target(key))
        assertTrue(schedule.requested(63227))
        val start = coordinator.begin(key, 1, false, true)!!
        schedule.observe(63227, true, true, start)
        val failed = coordinator.finish(start, HistoryReadPhase.FAILED)!!
        schedule.observe(63228, true, false, failed)

        // Replay r10's 1.685-second start-to-start gap without changing the target.
        schedule.observe(64912, true, false, coordinator.target(key))
        assertFalse(schedule.ready(64912))
        assertFalse(schedule.requested(64912))
        assertEquals(1, coordinator.receipt!!.attempt)
        assertFalse(schedule.ready(67227))
        assertTrue(schedule.requested(67228))
        assertEquals(start.cycleId, coordinator.begin(key, 2, false, true)!!.cycleId)
        assertEquals(2, coordinator.receipt!!.attempt)
    }

    @Test fun observedShortGapCanDispatchANewRevisionWithoutAcceptingTheOldReply() {
        val coordinator = HistoryReadCoordinator()
        val oldSchedule = HomeHistoryReadSchedule(key)
        oldSchedule.observe(63227, true, false, coordinator.target(key))
        assertTrue(oldSchedule.requested(63227))
        val oldStart = coordinator.begin(key, 1, false, true)!!
        oldSchedule.observe(63227, true, true, oldStart)

        // acceptSnapshot cancels an in-flight old revision before targeting the new one.
        coordinator.cancelActive(HistoryReadPhase.STALE)
        val nextKey = key.copy(signature = "public-r2")
        val queued = coordinator.target(nextKey)
        val nextSchedule = HomeHistoryReadSchedule(nextKey)
        nextSchedule.observe(64912, true, false, queued)
        assertTrue(queued.cycleId > oldStart.cycleId)
        assertEquals(0, queued.attempt)
        assertTrue(nextSchedule.requested(64912))
        val nextStart = coordinator.begin(nextKey, 2, false, true)!!
        assertEquals(1, nextStart.attempt)
        assertNull(coordinator.finish(oldStart, HistoryReadPhase.RESOLVED, 2))
        assertSame(nextStart, coordinator.receipt)
        assertEquals(nextKey, nextStart.key)
    }

    @Test fun manualCycleUsesItsOwnReceiptsAndSameReceiptCannotRestartFourSecondWait() {
        val schedule = HomeHistoryReadSchedule(key)
        schedule.observe(0, true, false, receipt(HistoryReadPhase.CACHED, days = 20))
        assertFalse(schedule.ready(0))
        var now = 10L
        repeat(4) { index ->
            val attempt = index + 1
            val start = receipt(HistoryReadPhase.STARTED, attempt, cycle = 2)
            schedule.observe(now, true, true, start)
            schedule.observe(now + 1, true, false, start)
            assertFalse("Unrelated loading=false cannot finish a history request", schedule.ready(now + 1))
            now += 100
            val terminal = start.copy(phase = if (attempt == 2) HistoryReadPhase.RESOLVED else HistoryReadPhase.FAILED,
                fetchedDays = if (attempt == 2) 0 else null)
            schedule.observe(now, true, false, terminal)
            schedule.observe(now + 2000, true, false, terminal.copy())
            assertFalse(schedule.ready(now + 3999))
            assertEquals(attempt, schedule.attempts)
            now += 4000
            assertEquals(attempt < 4, schedule.ready(now))
            if (attempt < 4) {
                assertTrue(schedule.requested(now))
                assertEquals("Only a STARTED receipt may consume an attempt", attempt, schedule.attempts)
                assertFalse(schedule.requested(now))
                schedule.observe(now + 10, true, false, terminal)
                assertFalse("A repeated old receipt cannot release an accepted dispatch", schedule.ready(now + 10000))
            }
        }
        assertNull(schedule.waitMs(now + 100000))
    }

    @Test fun acceptedSuccessAndEmptyWithoutPreviewStopButRacedEmptyAndFailureFollowFrozenPreview() {
        listOf(receipt(HistoryReadPhase.CACHED, days = 2),
            receipt(HistoryReadPhase.RESOLVED, 1, days = 2),
            receipt(HistoryReadPhase.RESOLVED, 1, preview = false, days = 0)).forEach { result ->
            val schedule = HomeHistoryReadSchedule(key)
            schedule.observe(0, true, false, result)
            assertFalse(schedule.ready(10000))
            assertNull(schedule.waitMs(10000))
        }
        val failedWithoutPreview = HomeHistoryReadSchedule(key)
        failedWithoutPreview.observe(0, true, false, receipt(HistoryReadPhase.FAILED, 1, preview = false))
        assertFalse(failedWithoutPreview.ready(10000))
        val racedEmpty = HomeHistoryReadSchedule(key)
        racedEmpty.observe(0, true, false, receipt(HistoryReadPhase.RESOLVED, 1, days = 0))
        assertTrue(racedEmpty.ready(4000))
        racedEmpty.observe(4000, true, true, receipt(HistoryReadPhase.STARTED, 2))
        racedEmpty.observe(4001, true, false, receipt(HistoryReadPhase.RESOLVED, 2, days = 1))
        assertFalse(racedEmpty.ready(10000))
    }

    @Test fun backgroundAndOtherSerialReadsGateAttemptsWithoutResettingTheBudget() {
        val schedule = HomeHistoryReadSchedule(key)
        val failed = receipt(HistoryReadPhase.FAILED, 1)
        schedule.observe(10, true, false, failed)
        schedule.observe(2000, true, true, failed)
        assertFalse(schedule.ready(4010))
        schedule.observe(5010, true, false, failed)
        assertTrue("Other API loading must preserve the original history deadline", schedule.ready(5010))
        schedule.pause()
        assertFalse(schedule.ready(10000))
        assertNull(schedule.waitMs(10000))
        schedule.observe(10000, true, false, failed)
        assertFalse(schedule.ready(13999))
        assertTrue(schedule.ready(14000))
        assertEquals(1, schedule.attempts)
        schedule.observe(15000, false, false, failed)
        schedule.observe(100000, true, false, failed)
        assertFalse(schedule.ready(103999))
        assertTrue(schedule.ready(104000))
        assertEquals(1, schedule.attempts)
    }

    @Test fun staleKeysCyclesAndRequestIdsCannotResolveTheCurrentRequest() {
        val schedule = HomeHistoryReadSchedule(key)
        val current = receipt(HistoryReadPhase.STARTED, 2, cycle = 4, request = 42)
        schedule.observe(0, true, true, current)
        listOf(current.copy(phase = HistoryReadPhase.RESOLVED, fetchedDays = 10, key = key.copy(connectionRevision = 6)),
            current.copy(phase = HistoryReadPhase.RESOLVED, fetchedDays = 10, cycleId = 3),
            current.copy(phase = HistoryReadPhase.RESOLVED, fetchedDays = 10, requestId = 41),
            current.copy(phase = HistoryReadPhase.RESOLVED, fetchedDays = 10, attempt = 1)).forEach {
            schedule.observe(100, true, false, it)
            assertFalse(schedule.ready(10000))
            assertEquals(2, schedule.attempts)
        }
        val failed = current.copy(phase = HistoryReadPhase.FAILED)
        schedule.observe(200, true, false, failed)
        schedule.observe(300, true, false, current)
        schedule.observe(400, true, false, current.copy(phase = HistoryReadPhase.RESOLVED, fetchedDays = 10))
        assertTrue("A late same-request terminal cannot replace its first terminal", schedule.ready(4200))
    }

    @Test fun rebuildingThePolicyRestoresTheViewModelBudgetAndNewCyclesCanReadAgain() {
        val failed = receipt(HistoryReadPhase.FAILED, 3, cycle = 8)
        val rebuilt = HomeHistoryReadSchedule(key)
        rebuilt.observe(20000, true, false, failed)
        assertEquals(3, rebuilt.attempts)
        assertFalse(rebuilt.ready(23999))
        assertTrue(rebuilt.ready(24000))
        rebuilt.observe(24000, true, true, receipt(HistoryReadPhase.STARTED, 4, cycle = 8))
        rebuilt.observe(24001, true, false, receipt(HistoryReadPhase.FAILED, 4, cycle = 8))
        assertFalse(rebuilt.ready(100000))
        val afterCap = HomeHistoryReadSchedule(key)
        afterCap.observe(100000, true, false, receipt(HistoryReadPhase.FAILED, 4, cycle = 8))
        assertFalse(afterCap.ready(200000))
        afterCap.observe(200000, true, false, receipt(HistoryReadPhase.QUEUED, cycle = 9))
        assertTrue(afterCap.ready(200000))
        afterCap.observe(200001, true, true, receipt(HistoryReadPhase.STARTED, 1, cycle = 10))
        assertEquals(1, afterCap.attempts)
        assertFalse(afterCap.ready(300000))
    }

    @Test fun cancellationAndStaleResumeWithoutPreviewButAuthAndUsedLegacyBindingsStayBlocked() {
        listOf(HistoryReadPhase.CANCELLED, HistoryReadPhase.STALE).forEach { phase ->
            val schedule = HomeHistoryReadSchedule(key)
            schedule.observe(0, true, false, receipt(phase, 2, preview = false))
            assertFalse(schedule.ready(3999))
            assertTrue(schedule.ready(4000))
            assertEquals(2, schedule.attempts)
        }
        val blocked = HomeHistoryReadSchedule(key)
        val denied = receipt(HistoryReadPhase.AUTH_BLOCKED, 2, preview = false)
        blocked.observe(0, true, false, denied)
        assertFalse(blocked.ready(10000))
        blocked.observe(10000, true, false, denied.copy(phase = HistoryReadPhase.CANCELLED))
        assertFalse(blocked.ready(13999))
        assertTrue(blocked.ready(14000))
        val legacyKey = key.copy(signature = "")
        val legacy = HomeHistoryReadSchedule(legacyKey)
        legacy.observe(0, true, false, receipt(HistoryReadPhase.CANCELLED, 1, preview = false, target = legacyKey))
        assertFalse(legacy.ready(10000))
        legacy.observe(10000, true, false, receipt(HistoryReadPhase.QUEUED, cycle = 2, target = legacyKey).copy(bindingRequested = true))
        assertFalse(legacy.ready(20000))
        val noReceipt = HomeHistoryReadSchedule(key)
        noReceipt.observe(0, true, false, null)
        assertFalse(noReceipt.ready(10000))
    }

    @Test fun legacySignatureTracksBackfillsCostMonthlyAndSummaryWithoutFreshness() {
        val history = HistoryParser.parse("""{"daily":[{"date":"2026-10-08","tokens":1,"cost":0.1},{"date":"2026-10-09","tokens":2,"cost":0.2}],"monthly":[{"month":"2026-10","tokens":3,"cost":0.3}],"summary":{"totalTokens":3,"totalCost":0.3,"unpricedTokens":1}}""")
        val key = homeHistoryPreviewSignature(history)
        assertEquals(key, homeHistoryPreviewSignature(history.copy(daily = history.daily.reversed())))
        assertNotEquals(key, homeHistoryPreviewSignature(history.copy(daily = history.daily.map { if (it.date == "2026-10-08") it.copy(cost = "0.15") else it })))
        assertNotEquals(key, homeHistoryPreviewSignature(history.copy(monthly = history.monthly.map { it.copy(tokens = 4) })))
        assertNotEquals(key, homeHistoryPreviewSignature(history.copy(summary = history.summary!!.copy(unpricedTokens = 2))))
        assertEquals("", homeHistoryPreviewSignature(history.copy(daily = emptyList())))
        // Incoming updatedAt/age fields are not part of the explicit preview projection.
        val snapshot = HubParser.parse("""{"updatedAt":"first","periods":{},"historyPreview":{"daily":[{"date":"2026-10-09","tokens":2,"cost":0.2}],"monthly":[]}}""")
        assertEquals(homeHistoryPreviewSignature(snapshot.historyPreview), homeHistoryPreviewSignature(snapshot.copy(updated = "later").historyPreview))
    }

    @Test fun snapshotSignaturePrefersTrimmedRevisionAndUsesStableFallbackForMissingOrBlankRevision() {
        val snapshot = HubParser.parse("""{"updatedAt":"first","periods":{},"historyPreview":{"daily":[{"date":"2026-10-08","tokens":1,"cost":0.1},{"date":"2026-10-09","tokens":2,"cost":0.2}],"monthly":[{"month":"2026-10","tokens":3,"cost":0.3}],"summary":{"totalTokens":3,"totalCost":0.3}}}""")
        val fallback = homeHistorySignature(snapshot)
        assertTrue(fallback.isNotEmpty())
        assertEquals("public-revision", homeHistorySignature(snapshot.copy(historyRevision = " public-revision ")))
        assertEquals(fallback, homeHistorySignature(snapshot.copy(historyRevision = "  \n\t")))
        assertEquals(fallback, homeHistorySignature(snapshot.copy(updated = "later")))
        val backfilled = snapshot.copy(historyPreview = snapshot.historyPreview!!.copy(daily = snapshot.historyPreview!!.daily.map { if (it.date == "2026-10-08") it.copy(tokens = 10) else it }))
        assertNotEquals(fallback, homeHistorySignature(backfilled))
        assertEquals("fixed", homeHistorySignature(backfilled.copy(historyRevision = "fixed")))
        assertEquals("fixed", homeHistorySignature(snapshot.copy(historyRevision = "fixed")))
        assertEquals("", homeHistorySignature(null))
    }
}
