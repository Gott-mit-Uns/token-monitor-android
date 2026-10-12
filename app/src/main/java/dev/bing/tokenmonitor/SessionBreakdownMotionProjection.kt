package dev.bing.tokenmonitor

import java.math.BigInteger

/** Display-only inputs from the exact already-filtered/paged collection rendered by Sessions. */
internal object SessionBreakdownMotionProjection {
    fun key(id:String)="session:$id"
    fun fallbackKey(row:SessionFallbackRow)="session-fallback:${row.model}:${row.id}"

    fun rows(visibleRows:List<SessionListRow>,maximum:BigInteger):List<BreakdownRowSpec> =
        visibleRows.map {BreakdownRowSpec(key(it.id),SessionListDisplay.bar(it.tokens,maximum),it.tokens)}

    fun fallback(rows:List<SessionFallbackRow>,maximum:Double):List<BreakdownRowSpec> =
        rows.map {BreakdownRowSpec(fallbackKey(it),BreakdownMotion.bar(it.barValue,maximum),
            breakdownNumericValue(it.tokens,it.detail))}
}
