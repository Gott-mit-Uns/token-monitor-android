package dev.bing.tokenmonitor

import java.math.BigDecimal
import java.math.BigInteger

/** One event projection, reused by both the lazy list and its display-only motion targets. */
internal data class MainBreakdownProjection(
    val rows:List<Pair<String,BigInteger?>> = emptyList(),
    val details:Map<String,UsageBreakdown> = emptyMap(),
    val barValues:Map<String,Double> = emptyMap(),
    val maximum:Double=0.0,
    val allRowCount:Int=0
) {
    companion object {
        fun project(page:HubPage,period:Period?,names:ModelNames?,observed:Collection<String>,preferences:DisplayPreferences):MainBreakdownProjection {
            if(page !in listOf(HubPage.TOOLS,HubPage.MODELS)) return MainBreakdownProjection()
            val raw=if(page==HubPage.TOOLS) period?.tools.orEmpty() else period?.models.orEmpty()
            val details=if(page==HubPage.TOOLS) period?.toolDetails.orEmpty() else names?.projectDetails(period?.modelDetails.orEmpty(),observed) ?: period?.modelDetails.orEmpty()
            val projected=if(page==HubPage.MODELS) names?.project(raw,observed) else null
            val counts=projected ?: raw.mapValues {BigInteger.valueOf(it.value)}
            val ranked=DesktopModelRanking.rows(counts,details,preferences.modelRanking)
            val usageRows=if(page==HubPage.MODELS) ranked.map {it.id to it.tokens}
                else (counts.keys+details.keys).associateWith {counts[it]}.toList().sortedByDescending {it.second}
            val rows=if(page==HubPage.TOOLS) ToolDisplay.usageRows(period,preferences) else usageRows
            val barValues=ranked.associate {it.id to it.barValue}
            val maximum=if(page==HubPage.MODELS) rows.maxOfOrNull {barValues[it.first] ?: 0.0} ?: 0.0
                else rows.maxOfOrNull {it.second?.toDouble() ?: 0.0} ?: 0.0
            return MainBreakdownProjection(rows,details,barValues,maximum,usageRows.size)
        }
    }
}

/** Keep a semantic cost/unknown primary label static, as the existing Android row does. */
internal fun breakdownNumericValue(value:BigInteger?,breakdown:UsageBreakdown?):BigInteger? {
    val costOnly=(value==null || value.signum()==0) && breakdown?.cost?.let {
        runCatching {BigDecimal(it).signum()>0}.getOrDefault(false)
    }==true
    return value.takeUnless {costOnly}
}
