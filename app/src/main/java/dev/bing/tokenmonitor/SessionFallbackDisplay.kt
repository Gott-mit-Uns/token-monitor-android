package dev.bing.tokenmonitor

import java.math.BigInteger

internal data class SessionFallbackRow(val id:String,val tokens:BigInteger?,val detail:UsageBreakdown?,val model:Boolean,val barValue:Double)

/** Same summary projection used by the Models/Tools views when desktop has no session rows. */
internal object SessionFallbackDisplay {
    fun rows(period:Period?,names:ModelNames?,observed:Collection<String>,preferences:DisplayPreferences,currency:UsageCurrency=UsageCurrency()):List<SessionFallbackRow> {
        if(period==null || (period.tokens ?: 0)<=0) return emptyList()
        val projected=UsageAttribution.period(period,currency)
        val details=names?.projectDetails(projected.modelDetails,observed) ?: projected.modelDetails
        val counts=names?.project(projected.models,observed) ?: projected.models.mapValues {BigInteger.valueOf(it.value)}
        val models=DesktopModelRanking.rows(counts,details,preferences.modelRanking)
        if(models.isNotEmpty()) return models.map {SessionFallbackRow(it.id,it.tokens,details[it.id],true,it.barValue)}
        return ToolDisplay.usageRows(projected,preferences).map {(id,tokens)->SessionFallbackRow(id,tokens,projected.toolDetails[id],false,tokens?.toDouble() ?: 0.0)}
    }
}
