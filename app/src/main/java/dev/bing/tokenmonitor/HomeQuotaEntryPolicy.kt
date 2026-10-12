package dev.bing.tokenmonitor

/** Entry-only capability: no actual remaining/cycle/value information enters the reset controller. */
internal object HomeQuotaEntryPolicy {
    private val entryOnly=listOf(QuotaWindowTarget("home-quota-entry-surface",null,100.0,null,null))
    fun targets(hasMeters:Boolean):List<QuotaWindowTarget> = if(hasMeters) entryOnly else emptyList()
    fun hasMeters(accounts:List<HomeQuotaAccount>,bars:Boolean):Boolean = bars && accounts.any {account->
        account.windows.any {it.source.presentation.showMeter && it.percent?.takeIf(Double::isFinite)?.let {percent->
            (percent/100).coerceIn(0.0,1.0)>=.001
        }==true}
    }
    fun fill(percent:Double,fraction:Float?):Float {
        val target=((percent.takeIf(Double::isFinite) ?: 0.0)/100).coerceIn(0.0,1.0).toFloat()
        // Shared source animateBarBetween skips a scale displacement below .001.
        return if(target<.001f) target else target*(fraction ?: 1f)
    }
}
