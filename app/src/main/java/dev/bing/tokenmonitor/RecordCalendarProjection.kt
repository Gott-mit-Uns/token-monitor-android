package dev.bing.tokenmonitor

import java.time.LocalDate
import java.time.YearMonth

/** Display-only index of the accepted Hub payload. Never derive monthly totals from daily rows. */
internal enum class RecordCellState { RECORDED, UNKNOWN, NO_RECORD, UNAVAILABLE, OUTSIDE_RANGE, FUTURE, NOT_LOADED, LOADING, FAILED }
internal data class RecordCalendarCell(val key: String, val row: HistoryRow?, val state: RecordCellState)

internal object RecordCalendarProjection {
    private fun index(rows: List<HistoryRow>): Map<String, HistoryRow> = rows.groupBy { it.date }.mapValues { (date, values) ->
        // Match HistoryRanges' ambiguity rule: duplicates have no authoritative value.
        if (values.size == 1) values.single() else HistoryRow(date, null, null)
    }

    private fun cell(key: String, rows: Map<String, HistoryRow>, loaded: Boolean, loading: Boolean,
        failed: Boolean, future: Boolean, provided: Boolean = true, outsideRange: Boolean = false): RecordCalendarCell {
        val row = rows[key]
        val state = when {
            outsideRange -> RecordCellState.OUTSIDE_RANGE
            row != null -> if (row.tokens != null) RecordCellState.RECORDED else RecordCellState.UNKNOWN
            // Hub calendar keys can be ahead of the phone's day in another timezone.
            // Existing rows stay authoritative; only absent future dates are unavailable.
            future -> RecordCellState.FUTURE
            loading -> RecordCellState.LOADING
            failed -> RecordCellState.FAILED
            !loaded -> RecordCellState.NOT_LOADED
            !provided -> RecordCellState.UNAVAILABLE
            else -> RecordCellState.NO_RECORD
        }
        return RecordCalendarCell(key, row, state)
    }

    fun dailyNavigationEnd(history: HubHistory?, today: LocalDate): YearMonth = maxOf(
        YearMonth.from(today), history?.daily.orEmpty().mapNotNull { row ->
            runCatching { YearMonth.from(LocalDate.parse(row.date)) }.getOrNull()
        }.maxOrNull() ?: YearMonth.from(today))

    fun monthlyNavigationEndYear(history: HubHistory?, today: LocalDate): Int = maxOf(
        today.year, history?.monthly.orEmpty().mapNotNull { row ->
            runCatching { YearMonth.parse(row.date).year }.getOrNull()
        }.maxOrNull() ?: today.year)

    fun days(history: HubHistory?, month: YearMonth, today: LocalDate, range: HistoryRangeSummary?,
        loading: Boolean = false, failed: Boolean = false): List<List<RecordCalendarCell?>> {
        val rows = index(range?.rows ?: history?.daily.orEmpty())
        val first = month.atDay(1)
        val offset = first.dayOfWeek.value - 1
        val length = ((offset + month.lengthOfMonth() + 6) / 7) * 7
        return (0 until length).map { slot ->
            val day = slot - offset + 1
            if (day !in 1..month.lengthOfMonth()) null else {
                val date = month.atDay(day)
                cell(date.toString(), rows, history != null, loading, failed, date > today,
                    provided = history?.dailyProvided == true,
                    outsideRange = range != null && (date < range.start || date > range.end))
            }
        }.chunked(7)
    }

    fun months(history: HubHistory?, year: Int, today: LocalDate, loading: Boolean = false,
        failed: Boolean = false): List<RecordCalendarCell> {
        val rows = index(history?.monthly.orEmpty())
        return (1..12).map { month ->
            val key = YearMonth.of(year, month)
            cell(key.toString(), rows, history != null, loading, failed, key > YearMonth.from(today),
                provided = history?.monthlyProvided == true)
        }
    }
}
