package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.SessionUsage
import java.time.Instant
import kotlin.math.roundToInt

internal enum class SessionActivityState { Running, Finished, Idle }

internal data class SessionContext(
    val percentUsed: Int,
    val percentLeft: Int,
)

internal data class SessionRowLabels(val title: String, val meta: String)

internal fun sessionRowLabels(session: SessionUsage): SessionRowLabels {
    val client = session.client.displayName()
    val title = session.title.ifBlank {
        listOf(client, session.modelNames.firstOrNull()).filter { !it.isNullOrBlank() }.joinToString(" · ")
    }.ifBlank { "Session" }
    val meta = listOf(
        client.takeIf { session.title.isNotBlank() }.orEmpty(),
        session.lastUsedAt.shortClockTime(),
        session.projectLabel,
        session.messageCount.takeIf { it > 0 }?.let { "$it messages" }.orEmpty(),
    ).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { session.id }
    return SessionRowLabels(title, meta)
}

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
