package io.github.theminionooo.tokenmonitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.domain.SessionUsage
import java.time.Instant

/** Home is a recent-session summary, independent of the selected usage period. */
internal fun recentHomeSessions(snapshot: HubSnapshot, now: Long): List<SessionUsage> =
    (snapshot.month.sessions + snapshot.today.sessions)
        .filter { it.sessionKind != "background-review" }
        .distinctBy { it.client to it.id }
        .mapNotNull { session ->
            val timestamp = session.lastUsedAt.ifBlank { session.startedAt }
            runCatching { Instant.parse(timestamp).toEpochMilli() }.getOrNull()?.let { session to it }
        }
        .sortedByDescending { it.second }
        .mapIndexedNotNull { index, (session, _) ->
            session.takeIf { index < 5 || sessionActivityState(it, now) == SessionActivityState.Running }
        }

@Composable
internal fun HomeSessionsModule(snapshot: HubSnapshot, onChoose: (DashboardDestination) -> Unit) {
    val now = LocalNow.current
    val sessions = recentHomeSessions(snapshot, now)
    val running = sessions.count { sessionActivityState(it, now) == SessionActivityState.Running }
    DesktopModule("SESSIONS", DashboardDestination.Sessions, onChoose, meta = "$running running") {
        if (sessions.isEmpty()) {
            MutedCopy("No recent sessions reported by the Hub")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                sessions.forEach { session ->
                    val state = sessionActivityState(session, now)
                    val age = session.lastUsedAt.ifBlank { session.startedAt }.relativeAge(now)
                    Column {
                        HomeListRow(
                            name = sessionRowLabels(session, LocalSessionTitles.current).title,
                            primary = formatCompactTokens(session.totalTokens),
                            secondary = listOf(session.client.displayName(), state.name, age, session.projectLabel)
                                .filter { it.isNotBlank() }.joinToString(" · "),
                            color = when (state) {
                                SessionActivityState.Running -> Success
                                SessionActivityState.Finished -> Blue
                                SessionActivityState.Idle -> Muted
                            },
                            literalName = true,
                        )
                        sessionContextForRow(session, now)?.let { context ->
                            Text("Context ${context.percentLeft}% left", color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 16.dp))
                        }
                        sessionMetricLabels(session, now).takeIf { it.isNotBlank() }?.let {
                            Text(it, color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }
        }
    }
}
