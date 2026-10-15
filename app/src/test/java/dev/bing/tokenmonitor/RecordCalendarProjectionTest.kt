package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.Instant
import java.time.ZoneId

class RecordCalendarProjectionTest {
    private val today = LocalDate.parse("2026-10-16")
    private val history = HubHistory(listOf(HistoryRow("2026-10-16", 100, "1"),
        HistoryRow("2026-10-15", 0, "0"), HistoryRow("2026-10-13", null, null),
        HistoryRow("2026-09-30", 900, "9")), listOf(HistoryRow("2026-10", 9999, "12")))
    private fun days(source: HubHistory? = history, loading: Boolean = false, failed: Boolean = false,
        range: HistoryRangeSummary? = null) = RecordCalendarProjection.days(source, YearMonth.of(2026, 10), today,
        range, loading, failed).flatten().filterNotNull().associateBy { it.key }

    @Test fun preservesKnownZeroUnknownMissingAndFutureAsDifferentStates() {
        val days = days()
        assertEquals(RecordCellState.RECORDED, days.getValue("2026-10-15").state)
        assertEquals(0L, days.getValue("2026-10-15").row?.tokens)
        assertEquals(RecordCellState.UNKNOWN, days.getValue("2026-10-13").state)
        assertEquals(RecordCellState.NO_RECORD, days.getValue("2026-10-14").state)
        assertEquals(RecordCellState.FUTURE, days.getValue("2026-10-17").state)
    }

    @Test fun followsExistingDailyRangeIncludingGapsWithoutSummingAgain() {
        val range = HistoryRanges.summarize(history, 7)!!
        val grid = days(range = range)
        assertEquals(RecordCellState.OUTSIDE_RANGE, grid.getValue("2026-10-09").state)
        assertEquals(RecordCellState.NO_RECORD, grid.getValue("2026-10-10").state)
        assertSame(range.rows.first(), grid.getValue("2026-10-16").row)
        assertEquals("100", range.tokens.toString())
    }

    @Test fun monthlyUsesAuthoritativeMonthlyRecordInsteadOfSumOfDays() {
        val october = RecordCalendarProjection.months(history, 2026, today)[9]
        assertSame(history.monthly.single(), october.row)
        assertEquals(9999L, october.row?.tokens)
        assertEquals(RecordCellState.NO_RECORD, RecordCalendarProjection.months(history, 2026, today)[8].state)
        assertEquals(RecordCellState.FUTURE, RecordCalendarProjection.months(history, 2026, today)[10].state)
    }

    @Test fun neverShowsMissingAsZeroOrAsAbsentBeforeSuccessfulLoad() {
        assertEquals(RecordCellState.NOT_LOADED, days(null).getValue("2026-10-14").state)
        assertEquals(RecordCellState.LOADING, days(null, loading = true).getValue("2026-10-14").state)
        assertEquals(RecordCellState.FAILED, days(null, failed = true).getValue("2026-10-14").state)
        assertEquals(RecordCellState.RECORDED, days(loading = true).getValue("2026-10-16").state)
        assertEquals(RecordCellState.RECORDED, days(failed = true).getValue("2026-10-15").state)
        assertEquals(RecordCellState.FAILED, days(failed = true).getValue("2026-10-14").state)
    }

    @Test fun duplicatesRemainAmbiguousForDailyAndMonthlyCells() {
        val duplicate = HubHistory(history.daily + HistoryRow("2026-10-16", 100, "1"),
            history.monthly + HistoryRow("2026-10", 9999, "12"))
        assertNull(days(duplicate).getValue("2026-10-16").row?.tokens)
        assertEquals(RecordCellState.UNKNOWN, RecordCalendarProjection.months(duplicate, 2026, today)[9].state)
    }

    @Test fun leapYearsAndWeekOffsetsUseSourceCalendarKeys() {
        val rows = RecordCalendarProjection.days(HubHistory(emptyList(), emptyList()), YearMonth.of(2024, 2), today, null)
        assertEquals(5, rows.size)
        assertEquals(3, rows.first().takeWhile { it == null }.size)
        assertEquals(29, rows.flatten().filterNotNull().size)
        assertEquals("2024-02-29", rows.last().filterNotNull().last().key)
        assertEquals(12, RecordCalendarProjection.months(history, 2025, today).size)
        assertTrue(RecordCalendarProjection.months(history, 2027, today).all { it.state == RecordCellState.FUTURE })
    }

    @Test fun replacementPayloadCompletelyReplacesProjectionWithoutStaleSelectionOrMergedCounts() {
        val first = days().getValue("2026-10-16")
        val replacement = history.copy(daily = listOf(HistoryRow("2026-10-16", 5, null)))
        assertEquals(100L, first.row?.tokens)
        assertEquals(5L, days(replacement).getValue("2026-10-16").row?.tokens)
        assertEquals(RecordCellState.NO_RECORD, days(replacement).getValue("2026-10-15").state)
    }

    @Test fun authoritativeRowsAheadOfPhoneDateKeepCountsZeroAndAmbiguityWithoutChangingRange() {
        val instant = Instant.parse("2026-11-01T00:30:00Z")
        val phoneDay = instant.atZone(ZoneId.of("America/Los_Angeles")).toLocalDate()
        val hubDay = instant.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()
        assertEquals(LocalDate.parse("2026-10-31"), phoneDay)
        assertEquals(LocalDate.parse("2026-11-01"), hubDay)
        for (sourceRows in listOf(
            listOf(HistoryRow(hubDay.toString(), 81, "1")),
            listOf(HistoryRow(hubDay.toString(), 0, "0")),
            listOf(HistoryRow(hubDay.toString(), null, "1")),
            listOf(HistoryRow(hubDay.toString(), 81, "1"), HistoryRow(hubDay.toString(), 81, "1")))) {
            val source = HubHistory(sourceRows + HistoryRow("2026-10-01", 999, "9"), emptyList())
            val range = HistoryRanges.summarize(source, 7)!!
            // The existing authoritative summary already includes the Hub date beyond the phone day.
            assertEquals(hubDay, range.end)
            val grid = RecordCalendarProjection.days(source, YearMonth.from(hubDay), phoneDay, range)
                .flatten().filterNotNull().associateBy { it.key }
            val cell = grid.getValue(hubDay.toString())
            val expectedRow = range.rows.single()
            assertSame(expectedRow, cell.row)
            assertEquals(if (expectedRow.tokens == null) RecordCellState.UNKNOWN else RecordCellState.RECORDED, cell.state)
            assertEquals((expectedRow.tokens ?: 0).toString(), range.tokens.toString())
            // The unchanged range excludes later absent days; without a range they remain future.
            assertEquals(RecordCellState.OUTSIDE_RANGE, grid.getValue("2026-11-02").state)
            assertEquals(RecordCellState.FUTURE, RecordCalendarProjection.days(source, YearMonth.from(hubDay), phoneDay, null)
                .flatten().filterNotNull().single { it.key == "2026-11-02" }.state)
            assertEquals(RecordCellState.OUTSIDE_RANGE, RecordCalendarProjection.days(source, YearMonth.of(2026, 10), phoneDay, range)
                .flatten().filterNotNull().single { it.key == "2026-10-01" }.state)
        }
    }

    @Test fun navigationReachesAuthoritativeNextMonthAndYearAfterGoingBack() {
        val phoneDay = Instant.parse("2027-01-01T00:30:00Z").atZone(ZoneId.of("America/Los_Angeles")).toLocalDate()
        assertEquals(LocalDate.parse("2026-12-31"), phoneDay)
        val source = HubHistory(listOf(HistoryRow("2027-01-01", 123, "1")),
            listOf(HistoryRow("2027-01", 456, "2")))
        assertEquals(YearMonth.of(2027, 1), RecordCalendarProjection.dailyNavigationEnd(source, phoneDay))
        assertEquals(2027, RecordCalendarProjection.monthlyNavigationEndYear(source, phoneDay))
        val january = RecordCalendarProjection.months(source, 2027, phoneDay)
        assertEquals(RecordCellState.RECORDED, january.first().state)
        assertSame(source.monthly.single(), january.first().row)
        assertEquals(RecordCellState.FUTURE, january[1].state)
        assertEquals(YearMonth.of(2026, 12), RecordCalendarProjection.dailyNavigationEnd(null, phoneDay))
        assertEquals(2026, RecordCalendarProjection.monthlyNavigationEndYear(HubHistory(emptyList(), emptyList()), phoneDay))
    }

    @Test fun missingNullAndWrongShapeDimensionsStayUnavailableWhileExplicitEmptyArraysMeanNoRecords() {
        val daily = "\"daily\":[{\"date\":\"2026-10-16\",\"tokens\":5}]"
        val monthly = "\"monthly\":[{\"month\":\"2026-10\",\"tokens\":9}]"
        for ((value, provided) in listOf("" to false, ":null" to false, ":{}" to false, ":[]" to true)) {
            val monthlyField = if (value.isEmpty()) "" else ",\"monthly\"$value"
            val onlyDaily = HistoryParser.parse("{$daily$monthlyField}")
            assertTrue(onlyDaily.dailyProvided)
            assertEquals(provided, onlyDaily.monthlyProvided)
            assertEquals(if (provided) RecordCellState.NO_RECORD else RecordCellState.UNAVAILABLE,
                RecordCalendarProjection.months(onlyDaily, 2026, today)[9].state)
            assertEquals(5L, onlyDaily.daily.single().tokens)
            val dailyField = if (value.isEmpty()) "" else ",\"daily\"$value"
            val onlyMonthly = HistoryParser.parse("{$monthly$dailyField}")
            assertTrue(onlyMonthly.monthlyProvided)
            assertEquals(provided, onlyMonthly.dailyProvided)
            assertEquals(if (provided) RecordCellState.NO_RECORD else RecordCellState.UNAVAILABLE,
                days(onlyMonthly).getValue("2026-10-16").state)
            assertEquals(9L, onlyMonthly.monthly.single().tokens)
        }
    }

    @Test fun missingDimensionKeepsLoadingFailureAndFutureSeparateAndNeverHidesExistingRows() {
        val partial = HistoryParser.parse("""{"daily":[{"date":"2026-10-16","tokens":0}]}""")
        assertEquals(RecordCellState.LOADING, RecordCalendarProjection.months(partial, 2026, today, loading = true)[9].state)
        assertEquals(RecordCellState.FAILED, RecordCalendarProjection.months(partial, 2026, today, failed = true)[9].state)
        assertEquals(RecordCellState.FUTURE, RecordCalendarProjection.months(partial, 2026, today)[10].state)
        val inconsistentMetadata = partial.copy(dailyProvided = false)
        assertEquals(RecordCellState.RECORDED, days(inconsistentMetadata).getValue("2026-10-16").state)
        assertEquals(0L, days(inconsistentMetadata).getValue("2026-10-16").row?.tokens)
        assertEquals(RecordCellState.UNAVAILABLE, days(inconsistentMetadata).getValue("2026-10-15").state)
        assertEquals(RecordCellState.LOADING, days(inconsistentMetadata, loading = true).getValue("2026-10-15").state)
        assertEquals(RecordCellState.FAILED, days(inconsistentMetadata, failed = true).getValue("2026-10-15").state)
        assertEquals(RecordCellState.FUTURE, days(inconsistentMetadata).getValue("2026-10-17").state)
    }

    @Test fun presentationCopiesRetainParsedDimensionPresenceWithoutChangingCounts() {
        val names = ModelNames(mapOf("demo" to "Friendly"), "off")
        for (dimension in listOf("daily", "monthly")) {
            val key = if (dimension == "daily") "date" else "month"
            val date = if (dimension == "daily") "2026-10-16" else "2026-10"
            val source = HistoryParser.parse("""{"$dimension":[{"$key":"$date","tokens":5,"perModel":{"demo":{"tokens":5}}}]}""")
            val shown = DashboardHistoryPresentation.project(source, names)
            assertNotSame(source, shown)
            assertEquals(source.dailyProvided, shown.dailyProvided)
            assertEquals(source.monthlyProvided, shown.monthlyProvided)
            assertEquals(listOf("Friendly"), (shown.daily + shown.monthly).single().perModel.keys.toList())
            assertEquals(5L, (shown.daily + shown.monthly).single().tokens)
            assertEquals(source.dailyProvided, source.copy().dailyProvided)
            assertEquals(source.monthlyProvided, source.copy().monthlyProvided)
        }
    }
}
