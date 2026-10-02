package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.SessionUsage
import io.github.theminionooo.tokenmonitor.domain.PromptCache
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionPresentationTest {
    @Test fun `fx retains its lowercase name and upstream vendor mark`() {
        assertEquals("fx", "fx".displayName())
        assertEquals("fx", vendorOf("fx"))
        org.junit.Assert.assertNotNull(upstreamToolAsset("fx"))
    }
    private val now = Instant.parse("2026-09-21T12:00:00Z").toEpochMilli()

    @Test fun `recent unfinished and finished turns stay distinct`() {
        assertEquals(SessionActivityState.Running, sessionActivityState(session("2026-09-21T11:59:30Z"), now))
        assertEquals(SessionActivityState.Running, sessionActivityState(session("2026-09-21T11:59:30Z", false), now))
        assertEquals(SessionActivityState.Finished, sessionActivityState(session("2026-09-21T11:59:30Z", true), now))
        assertEquals(SessionActivityState.Idle, sessionActivityState(session("2026-09-21T11:49:59Z"), now))
    }

    @Test fun `context requires both values and remains visible after a recent turn finishes`() {
        val finished = session("2026-09-21T11:59:30Z", true).copy(contextTokens = 190_000, contextWindow = 200_000)
        assertEquals(SessionContext(percentUsed = 95, percentLeft = 5), sessionContextForRow(finished, now))
        assertNull(sessionContextForRow(finished.copy(contextWindow = 0), now))
        assertNull(sessionContextForRow(finished.copy(lastUsedAt = "2026-09-21T11:00:00Z"), now))
    }

    @Test fun `reported title is primary but untitled sessions keep their original labels`() {
        val untitled = session("2026-09-21T11:59:30Z")
        val oldLabels = sessionRowLabels(untitled)
        assertEquals("Codex · gpt-6-astra", oldLabels.title)
        assertEquals("${untitled.lastUsedAt.shortClockTime()} · Token Monitor · 1 messages", oldLabels.meta)

        val titled = sessionRowLabels(untitled.copy(title = "Example planning conversation"))
        assertEquals("Example planning conversation", titled.title)
        assertEquals("Codex · ${oldLabels.meta}", titled.meta)
    }

    @Test fun `hidden titles use the untitled label without mutating the source`() {
        val titled = session("2026-09-21T11:59:30Z").copy(title = "Private conversation")
        assertEquals("Codex · gpt-6-astra", sessionRowLabels(titled, false).title)
        assertEquals("Private conversation", titled.title)
    }

    @Test fun `speed is capped at output and unknown without positive timed values`() {
        val base = session("").copy(outputTokens = 100, timedOutputTokens = 200, timedDurationMs = 2000)
        assertEquals(50.0, sessionTokenRate(base)!!, 0.001)
        assertNull(sessionTokenRate(base.copy(timedDurationMs = 0)))
        assertNull(sessionTokenRate(base.copy(timedOutputTokens = 0)))
        assertNull(sessionTokenRate(base.copy(outputTokens = -1)))
        assertEquals(1000.0, sessionTokenRate(base.copy(outputTokens = Long.MAX_VALUE, timedOutputTokens = Long.MAX_VALUE, timedDurationMs = Long.MAX_VALUE))!!, 0.001)
    }

    @Test fun `cache hit is distinct from missing cache telemetry and does not overflow`() {
        val base = session("").copy(inputTokens = 20, cacheReadTokens = 60, cacheWriteTokens = 20)
        assertEquals(60.0, sessionCacheHitPercent(base)!!, 0.001)
        assertEquals(0.0, sessionCacheHitPercent(base.copy(cacheReadTokens = 0))!!, 0.001)
        assertNull(sessionCacheHitPercent(base.copy(cacheReadTokens = 0, cacheWriteTokens = 0)))
        assertEquals(50.0, sessionCacheHitPercent(base.copy(inputTokens = Long.MAX_VALUE, cacheReadTokens = Long.MAX_VALUE, cacheWriteTokens = 0))!!, 0.001)
    }

    @Test fun `cache estimates expire and suppress archived future invalid or unsupported rows`() {
        val base = session("").copy(promptCache = PromptCache("2026-09-21T11:59:00Z", 300))
        assertEquals(4, sessionPromptCacheMinutes(base, now))
        assertEquals(1, sessionPromptCacheMinutes(base, now + 239_999))
        assertNull(sessionPromptCacheMinutes(base, now + 240_000))
        assertNull(sessionPromptCacheMinutes(base.copy(archived = true), now))
        assertNull(sessionPromptCacheMinutes(base.copy(client = "fx"), now))
        assertNull(sessionPromptCacheMinutes(base.copy(promptCache = PromptCache("2026-09-21T12:00:01Z", 300)), now))
        assertNull(sessionPromptCacheMinutes(base.copy(promptCache = PromptCache("bad", 300)), now))
        assertNull(sessionPromptCacheMinutes(base.copy(promptCache = PromptCache("2026-09-21T11:59:00Z", 600)), now))
    }

    private fun session(lastUsedAt: String, turnEnded: Boolean? = null) = SessionUsage(
        id = "session-1",
        client = "codex",
        projectLabel = "Token Monitor",
        totalTokens = 10,
        costUsd = 0.0,
        modelNames = listOf("gpt-6-astra"),
        messageCount = 1,
        startedAt = "2026-09-21T11:50:00Z",
        lastUsedAt = lastUsedAt,
        turnEnded = turnEnded,
    )
}
