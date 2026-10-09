package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.*
import org.junit.Assert.*
import org.junit.Test

class HomeDeviceRankingTest {
    private fun device(id: String, today: Long, month: Long = today, stale: Boolean = false) = DeviceUsage(
        id, id, "linux", "Debian GNU/Linux", "12", "", "", null, stale, null, null, HubHistory(), emptyList(),
        mapOf("today" to UsagePeriod(totalTokens = today), "month" to UsagePeriod(totalTokens = month)),
    )

    @Test fun homeOmitsZeroAndSortsTokensWithoutChangingSnapshotOrder() {
        val source = listOf(device("4300", 0), device("Mac", 8), device("Windows", 63), device("4800", 19))
        assertEquals(listOf("Windows", "4800", "Mac"), activeHomeDevices(source, DashboardPeriod.Today).map { it.first.id })
        assertEquals(listOf("4300", "Mac", "Windows", "4800"), source.map { it.id })
        assertEquals(0L, source.first().periods.getValue("today").totalTokens)
    }
    @Test fun zeroTodayDeviceReappearsWhenSelectedMonthHasUsage() {
        val source = listOf(device("4300", 0, 50), device("Mac", 8, 10))
        assertEquals(listOf("Mac"), activeHomeDevices(source, DashboardPeriod.Today).map { it.first.id })
        assertEquals(listOf("4300", "Mac"), activeHomeDevices(source, DashboardPeriod.Month).map { it.first.id })
    }
    @Test fun positiveStaleDeviceStillShowsItsCachedUsage() {
        val source = device("offline", 20, stale = true)
        val result = activeHomeDevices(listOf(source), DashboardPeriod.Today).single()
        assertTrue(result.first.stale)
        assertEquals(20L, result.second.totalTokens)
    }
    @Test fun tiesKeepHubOrderAndMissingPeriodsNeverUseAggregateTotals() {
        val source = listOf(device("first", 10), device("second", 10), device("missing", 9).copy(periods = emptyMap()))
        assertEquals(listOf("first", "second"), activeHomeDevices(source, DashboardPeriod.Today).map { it.first.id })
        assertTrue(activeHomeDevices(listOf(source.last()), DashboardPeriod.Today).isEmpty())
    }
    @Test fun emptyOrNonPositivePeriodReturnsAnEmptyHomeList() {
        assertTrue(activeHomeDevices(emptyList(), DashboardPeriod.Today).isEmpty())
        assertTrue(activeHomeDevices(listOf(device("zero", 0), device("invalid", -1)), DashboardPeriod.Today).isEmpty())
    }
}
