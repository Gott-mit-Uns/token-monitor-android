package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.domain.LimitAccount
import io.github.theminionooo.tokenmonitor.domain.LimitWindow
import java.time.Instant
import io.github.theminionooo.tokenmonitor.localization.localizedText

internal fun quotaRemainingPercent(window: LimitWindow): Double? =
    (window.remainingPercent?.takeIf { it.isFinite() }
        ?: window.usedPercent?.takeIf { it.isFinite() }?.let { 100.0 - it })?.coerceIn(0.0, 100.0)

internal fun prioritizeAvailableLimits(providers: List<LimitAccount>): List<LimitAccount> = providers.withIndex()
    .sortedWith(compareBy<IndexedValue<LimitAccount>> { it.value.windows.isEmpty() }
        .thenBy { account -> account.value.windows.mapNotNull(::quotaRemainingPercent).minOrNull() ?: 101.0 }
        .thenBy { it.index })
    .map { it.value }

private data class QuotaNote(val text: String, val priority: Int, val remaining: Double = 101.0, val boundary: Long = Long.MAX_VALUE)

internal fun limitAttention(snapshot: HubSnapshot, now: Long): List<String> = snapshot.stats.limits.providers.flatMap { account ->
    val updated = runCatching { Instant.parse(account.updatedAt.ifBlank { snapshot.stats.limits.updatedAt }).toEpochMilli() }.getOrNull()
    val staleAfter = snapshot.stats.staleAfterMs?.takeIf { it > 0 } ?: 600_000L
    val provider = account.provider.providerLabel()
    if (updated == null || snapshot.fromCache || snapshot.stale || updated > now + 60_000L || now - updated > staleAfter) {
        listOf(QuotaNote("$provider: ${if (updated == null) localizedText("freshness unavailable") else localizedText("saved quota; refresh to confirm")}", 3))
    } else account.windows.mapNotNull { window ->
        val remaining = quotaRemainingPercent(window)
        val reset = runCatching { Instant.parse(window.resetsAt).toEpochMilli() }.getOrNull()
        val label = window.label.ifBlank { window.kind }
        when {
            reset != null && reset <= now -> QuotaNote("$provider $label: ${formatBoundary(window.resetsAt, window.boundaryKind, now)}; ${localizedText("awaiting updated quota")}", 1, boundary = reset)
            remaining != null && remaining <= 20 -> QuotaNote("$provider $label: ${localizedText("${formatPercent(remaining)} left")}", 0, remaining)
            reset != null && reset - now <= 3_600_000L -> QuotaNote("$provider $label: ${formatBoundary(window.resetsAt, window.boundaryKind, now)}", 2, boundary = reset)
            else -> null
        }
    }
}.sortedWith(compareBy<QuotaNote> { it.priority }.thenBy { it.remaining }.thenBy { it.boundary })
    .map { it.text }.distinct()
