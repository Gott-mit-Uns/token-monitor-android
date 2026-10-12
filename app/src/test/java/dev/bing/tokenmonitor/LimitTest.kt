package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class LimitTest {
    @Test fun spendWithoutDenominatorAndExpiryAreNotResetOrZero() {
        val s = HubParser.parse("""{"periods":{},"limits":{"providers":[{"provider":"example","windows":[
            {"kind":"billing","metric":"spend","used":1.25,"currency":"CNY","boundaryKind":"expiry","resetsAt":"2026-10-31T00:00:00Z","source":"web"},
            {"metric":"credits","remaining":"NaN"}]}]}}""")
        val spend = s.limits.first()
        assertEquals("1.25", spend.usedAmount)
        assertNull(spend.limitAmount)
        assertNull(spend.remaining)
        assertEquals("expiry", spend.boundaryKind)
        assertEquals("CNY", spend.currency)
        assertNull(s.limits.last().amount)
    }
    @Test fun preservesAccountsCreditUnitsAndUnavailableProviders() {
        val s = HubParser.parse("""{"periods":{},"limits":{"providers":[
            {"provider":"example","accountLabel":"Account A","stale":true,"windows":[{"kind":"balance","metric":"credits","remaining":12.5,"currency":"USD"}]},
            {"provider":"example","accountLabel":"Account B","status":"error","windows":[]}
        ]}}""")
        assertEquals(2, s.limits.size)
        assertEquals("Account A", s.limits[0].account)
        assertEquals("credits", s.limits[0].metric)
        assertEquals("12.5", s.limits[0].amount)
        assertNull(s.limits[0].remaining)
        assertEquals("Account B", s.limits[1].account)
        assertEquals("error", s.limits[1].status)
    }
}
