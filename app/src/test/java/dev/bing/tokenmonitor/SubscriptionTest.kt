package dev.bing.tokenmonitor
import org.junit.Assert.*
import org.junit.Test
class SubscriptionTest {
    @Test fun renewalsKeepOriginalMonthAnchorAndRespectCancellation() {
        val row = SubscriptionParser.parse("""{"subscriptions":[{"kind":"subscription","amountMinor":1000,"interval":"month","intervalCount":1,"startDate":"2026-01-31","autoRenew":true}]}""").single()
        assertEquals("2026-03-31", row.nextRenewal(java.time.LocalDate.parse("2026-03-01")).toString())
        assertEquals("20.00", row.recordedTotal(java.time.LocalDate.parse("2026-03-01"))!!.toPlainString())
        assertNull(row.copy(autoRenew = false).nextRenewal(java.time.LocalDate.parse("2026-03-01")))
        assertEquals("10.00", row.copy(autoRenew = false).recordedTotal(java.time.LocalDate.parse("2026-03-01"))!!.toPlainString())
    }
    @Test fun topUpsHaveNoCadenceAndExcludeFutureEntries() {
        val row = SubscriptionParser.parse("""{"subscriptions":[{"kind":"topup","topUps":[{"date":"2026-01-01","amountMinor":101},{"date":"2027-01-01","amountMinor":200}]}]}""").single()
        assertEquals("1.01", row.recordedTotal(java.time.LocalDate.parse("2026-10-09"))!!.toPlainString())
        assertNull(row.nextRenewal())
    }
    @Test fun convertsMinorUnitsExactlyAndKeepsUnknownValues() {
        val rows = SubscriptionParser.parse("""{"subscriptions":[{"provider":"example","amountMinor":9000,"currency":"HKD","autoRenew":false},{}]}""")
        assertEquals("90.00", rows[0].amount)
        assertEquals("HKD", rows[0].currency)
        assertEquals(false, rows[0].autoRenew)
        assertNull(rows[1].amount)
        assertNull(rows[1].autoRenew)
    }
}
