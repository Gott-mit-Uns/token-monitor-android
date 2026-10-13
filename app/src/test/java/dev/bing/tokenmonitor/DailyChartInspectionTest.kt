package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class DailyChartInspectionTest {
    @Test fun emptySinglePointAndInvalidGeometryDoNotInventRecords() {
        assertNull(dailyChartIndex(30f, 0f, 100f, 0, false))
        assertNull(dailyChartIndex(30f, 0f, 0f, 7, false))
        assertNull(dailyChartIndex(Float.NaN, 0f, 100f, 7, true))
        assertEquals(0, dailyChartIndex(100f, 0f, 100f, 1, false))
    }
    @Test fun initialHitRejectsAxesWhileLockedDragClampsToBoundaryDates() {
        assertNull(dailyChartIndex(51f, 52f, 700f, 7, false))
        assertNull(dailyChartIndex(753f, 52f, 700f, 7, false))
        assertEquals(0, dailyChartIndex(-100f, 52f, 700f, 7, true))
        assertEquals(6, dailyChartIndex(999f, 52f, 700f, 7, true))
        assertEquals(6, dailyChartIndex(752f, 52f, 700f, 7, false))
    }
    @Test fun adjacentDatesUseExistingEqualWidthSlotsInBothDirections() {
        assertEquals(listOf(0, 1, 2, 3), listOf(5f, 15f, 25f, 35f).map { dailyChartIndex(it, 0f, 40f, 4, true) })
        assertEquals(listOf(3, 2, 1, 0), listOf(35f, 25f, 15f, 5f).map { dailyChartIndex(it, 0f, 40f, 4, true) })
    }
}
