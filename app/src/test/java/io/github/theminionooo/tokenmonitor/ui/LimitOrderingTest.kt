package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.LimitAccount
import io.github.theminionooo.tokenmonitor.domain.LimitWindow
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.domain.HubStats
import io.github.theminionooo.tokenmonitor.domain.HubLimits
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class LimitOrderingTest {
    @Test fun usedOnlyLimitsSortAheadOfHealthyAccountsAndTiesStayStable() {
        val healthy = account("Healthy", true)
        val low = account("Low", true).copy(windows = listOf(window().copy(remainingPercent = null, usedPercent = 99.0)))
        assertEquals(listOf("Low", "Healthy", "Other"), prioritizeAvailableLimits(listOf(healthy, low, healthy.copy(provider = "Other"))).map { it.provider })
    }

    @Test fun freshLowQuotaCannotBeHiddenBehindEarlierStaleNotices() {
        val now = Instant.parse("2026-10-04T12:00:00Z")
        val stale = (1..4).map { account("stale-$it", true) }
        val low = account("Low", true).copy(updatedAt = now.toString(), windows = listOf(window().copy(remainingPercent = 1.0)))
        val snapshot = HubSnapshot(stats = HubStats(limits = HubLimits(providers = stale + low)))
        assertEquals("Low Weekly: 1% left", limitAttention(snapshot, now.toEpochMilli()).first())
        assertEquals(5, limitAttention(snapshot, now.toEpochMilli()).size)
        assertEquals("Low: saved quota; refresh to confirm", limitAttention(snapshot.copy(fromCache = true), now.toEpochMilli()).last())
    }

    @Test
    fun accountsWithQuotaWindowsAppearFirstWithoutChangingGroupOrder() {
        val accounts = listOf(
            account("Antigravity"),
            account("Claude", hasWindow = true),
            account("Codex", hasWindow = true),
            account("Ollama"),
        )

        assertEquals(
            listOf("Claude", "Codex", "Antigravity", "Ollama"),
            prioritizeAvailableLimits(accounts).map { it.provider },
        )
    }

    private fun account(provider: String, hasWindow: Boolean = false) = LimitAccount(
        provider = provider,
        accountName = "",
        accountEmail = "",
        plan = "",
        status = "",
        sourceDeviceId = "",
        updatedAt = "",
        windows = if (hasWindow) listOf(window()) else emptyList(),
    )

    private fun window() = LimitWindow(
        kind = "weekly",
        label = "Weekly",
        usedPercent = 25.0,
        remainingPercent = 75.0,
        remaining = null,
        resetsAt = "",
        metric = "percent",
        currency = "",
        detail = "",
        showMeter = true,
    )
}
