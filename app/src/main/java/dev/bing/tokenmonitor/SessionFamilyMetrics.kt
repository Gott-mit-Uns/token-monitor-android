package dev.bing.tokenmonitor

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.floor

/** Ratios over disjoint Hub accounting rows, never over already rendered summaries. */
internal data class SessionFamilyMetrics(val calls:Double?,val cachePercent:Double?,val tokensPerSecond:Double?) {
    fun label():String {
        val numbers=NumberFormat.getIntegerInstance(Locale.US)
        fun rounded(value:Double)=numbers.format(floor(value+0.5))
        return listOf(calls?.let {"${rounded(it)} ${if(it==1.0) "call" else "calls"}"} ?: "Calls unknown",
            cachePercent?.let {if(it>0 && it<1) "<1%" else "${rounded(it)}%"} ?: "—%",
            tokensPerSecond?.let {"${rounded(it)} tok/s"} ?: "— tok/s").joinToString(" · ")
    }
    companion object {
        fun from(rows:List<SessionListRow>):SessionFamilyMetrics {
            val accounting=rows.distinctBy {it.id}.map {it.source?.session?.accounting}
            fun sum(selector:(SessionAccounting)->Double)=accounting.sumOf {it?.let(selector) ?: 0.0}
            fun Double.valid()=takeIf {it.isFinite() && it>=0}
            val calls=if(accounting.isNotEmpty() && accounting.all {it?.callsKnown==true}) sum {it.calls.coerceAtLeast(0.0)}.valid() else null
            // A missing member's telemetry cannot silently become a cache miss.
            val cacheKnown=rows.distinctBy {it.id}.all {row->row.source?.session?.accounting?.let {
                it.inputKnown && it.cacheReadKnown && (row.source.tool=="goose" || it.cacheWriteKnown) &&
                    (it.cacheReported || it.cacheRead>0 || it.cacheWrite>0)
            }==true}
            val read=sum {it.cacheRead.coerceAtLeast(0.0)}
            val input=rows.distinctBy {it.id}.sumOf {row->
                row.source?.session?.accounting?.let {a->
                    if(row.source.tool=="goose") a.input.coerceAtLeast(0.0)
                    else a.input.coerceAtLeast(0.0)+a.cacheRead.coerceAtLeast(0.0)+a.cacheWrite.coerceAtLeast(0.0)
                } ?: 0.0
            }
            val cache=if(cacheKnown && input>0 && input.isFinite()) (read/input*100).valid()?.coerceAtMost(100.0) else null
            // Clamp BEFORE adding: untimed output cannot borrow another member's duration.
            val timed=accounting.filterNotNull().filter {it.timingKnown && it.timedDurationMs>0}
            val duration=timed.sumOf {it.timedDurationMs}
            val output=timed.sumOf {minOf(it.output.coerceAtLeast(0.0),it.timedOutput.coerceAtLeast(0.0))}
            val speed=if(duration>0 && duration.isFinite() && output>0) (output*1000/duration).valid() else null
            return SessionFamilyMetrics(calls,cache,speed)
        }
    }
}
