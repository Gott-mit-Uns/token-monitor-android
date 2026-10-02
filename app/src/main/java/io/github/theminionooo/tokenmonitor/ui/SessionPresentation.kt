package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.SessionUsage
import java.time.Instant
import kotlin.math.roundToInt
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalSessionTitles = staticCompositionLocalOf { true }

internal enum class SessionActivityState { Running, Finished, Idle }

internal data class SessionContext(
    val percentUsed: Int,
    val percentLeft: Int,
)

internal data class SessionRowLabels(val title: String, val meta: String)

internal fun sessionRowLabels(session: SessionUsage, showTitles: Boolean = true): SessionRowLabels {
    val client = session.client.displayName()
    val reportedTitle = session.title.takeIf { showTitles }.orEmpty()
    val title = reportedTitle.ifBlank {
        listOf(client, session.modelNames.firstOrNull()).filter { !it.isNullOrBlank() }.joinToString(" · ")
    }.ifBlank { "Session" }
    val meta = listOf(
        client.takeIf { reportedTitle.isNotBlank() }.orEmpty(),
        session.lastUsedAt.shortClockTime(),
        session.projectLabel,
        session.messageCount.takeIf { it > 0 }?.let { "$it messages" }.orEmpty(),
    ).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { session.id }
    return SessionRowLabels(title, meta)
}

/** Session-average output speed; absence of timed output is not zero speed. */
internal fun sessionTokenRate(session: SessionUsage): Double? {
    val output = minOf(session.outputTokens, session.timedOutputTokens).coerceAtLeast(0)
    return if (output > 0 && session.timedDurationMs > 0)
        output.toDouble() * 1000 / session.timedDurationMs else null
}

internal fun sessionCacheHitPercent(session: SessionUsage): Double? {
    val reads = session.cacheReadTokens.coerceAtLeast(0).toDouble()
    val writes = session.cacheWriteTokens.coerceAtLeast(0).toDouble()
    if (reads == 0.0 && writes == 0.0) return null
    return reads * 100 / (session.inputTokens.coerceAtLeast(0).toDouble() + reads + writes)
}

internal fun sessionPromptCacheMinutes(session: SessionUsage, now: Long): Int? {
    if (session.archived || session.client !in setOf("claude", "codex")) return null
    val cache = session.promptCache ?: return null
    if (cache.ttlSeconds !in setOf(300L, 1800L, 3600L)) return null
    val observed = runCatching { Instant.parse(cache.observedAt).toEpochMilli() }.getOrNull() ?: return null
    // Subtract timestamps instead of adding TTL, avoiding overflow on extreme dates.
    if (observed > now) return null
    val elapsed = runCatching { Math.subtractExact(now, observed) }.getOrNull() ?: return null
    val remaining = cache.ttlSeconds * 1000 - elapsed
    return if (remaining > 0) ((remaining + 59_999) / 60_000).toInt() else null
}

internal fun sessionMetricLabels(session: SessionUsage, now: Long): String = buildList {
    sessionCacheHitPercent(session)?.let { percent ->
        val value = if (percent > 0 && percent < 1) "<1" else percent.roundToInt().toString()
        add("Cache hit $value%")
    }
    sessionTokenRate(session)?.roundToInt()?.takeIf { it > 0 }?.let { add("$it tok/s") }
    sessionPromptCacheMinutes(session, now)?.let { add("Cache estimate ~$it min left") }
}.joinToString(" · ")

private const val SESSION_RUNNING_WINDOW_MS = 10 * 60_000L

internal fun sessionActivityState(session: SessionUsage, now: Long): SessionActivityState {
    if (session.archived) return SessionActivityState.Idle
    val lastUsed = runCatching { Instant.parse(session.lastUsedAt).toEpochMilli() }.getOrNull()
        ?: return SessionActivityState.Idle
    if (now - lastUsed > SESSION_RUNNING_WINDOW_MS) return SessionActivityState.Idle
    return if (session.turnEnded == true) SessionActivityState.Finished else SessionActivityState.Running
}

internal fun sessionContextForRow(session: SessionUsage, now: Long): SessionContext? {
    if (sessionActivityState(session, now) == SessionActivityState.Idle) return null
    if (session.contextTokens <= 0 || session.contextWindow <= 0) return null
    val left = (((session.contextWindow - session.contextTokens).coerceAtLeast(0).toDouble() / session.contextWindow) * 100)
        .roundToInt()
        .coerceIn(0, 100)
    return SessionContext(percentUsed = 100 - left, percentLeft = left)
}
