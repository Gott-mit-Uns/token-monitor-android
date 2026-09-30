package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class HomeSessionsTest {
    private val now = Instant.parse("2026-09-30T12:00:00Z").toEpochMilli()

    @Test fun `keeps five newest plus running sessions beyond the cap`() {
        val rows = (0..7).map { session("s$it", now - it * 60_000L, ended = it != 6) }
        assertEquals(listOf("s0", "s1", "s2", "s3", "s4", "s6"), recentHomeSessions(snapshot(rows), now).map { it.id })
    }

    @Test fun `uses canonical client and id across month and today and excludes background work`() {
        val month = session("same", now - 1_000)
        val otherClient = month.copy(client = "grok")
        val invalid = month.copy(id = "bad", lastUsedAt = "invalid")
        val background = month.copy(id = "review", sessionKind = "background-review")
        val startedOnly = month.copy(id = "start", lastUsedAt = "")
        val snapshot = snapshot(listOf(month, invalid, background), listOf(month, otherClient, startedOnly))
        assertEquals(listOf(month, otherClient, startedOnly), recentHomeSessions(snapshot, now))
    }

    @Test fun `archived sessions cannot be running or retain context`() {
        val archived = session("archived", now - 1_000).copy(archived = true, contextTokens = 10, contextWindow = 100)
        assertEquals(SessionActivityState.Idle, sessionActivityState(archived, now))
        assertNull(sessionContextForRow(archived, now))
        val recent = (0..4).map { session("s$it", now - it * 100L, true) }
        assertEquals(5, recentHomeSessions(snapshot(recent + archived), now).size)
    }

    private fun snapshot(month: List<SessionUsage>, today: List<SessionUsage> = emptyList()) =
        HubSnapshot(stats = HubStats(periods = mapOf("month" to UsagePeriod(sessions = month), "today" to UsagePeriod(sessions = today))))

    private fun session(id: String, time: Long, ended: Boolean = false) = SessionUsage(
        id = id, client = "codex", title = "fix a mixedCase bug", projectLabel = "Example", totalTokens = 10,
        costUsd = 0.0, modelNames = emptyList(), messageCount = 0, startedAt = Instant.ofEpochMilli(time).toString(),
        lastUsedAt = Instant.ofEpochMilli(time).toString(), turnEnded = ended,
    )
}
