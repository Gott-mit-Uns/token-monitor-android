package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import java.time.Instant
import java.util.Locale

internal data class HomeQuotaWindow(val source:Limit,val raw:JsonObject,val remaining:Double?,val label:String,val value:String,val boundary:String,val percent:Double?)
internal data class HomeQuotaAccount(val recordIndex:Int,val provider:String,val windows:List<HomeQuotaWindow>,val source:List<Limit>,val peers:List<Limit>,val accountIndex:Int,val name:String,val plan:String,val lowest:Double)

internal enum class HomeQuotaTone { NORMAL, LOW, CRITICAL }
internal fun homeQuotaTone(remaining:Double?,enabled:Boolean):HomeQuotaTone {
    val value=remaining?.takeIf {it.isFinite()}?.coerceIn(0.0,100.0)
    return if(!enabled || value==null) HomeQuotaTone.NORMAL else if(value<20) HomeQuotaTone.CRITICAL else if(value<50) HomeQuotaTone.LOW else HomeQuotaTone.NORMAL
}

/** Desktop homeOverview compact pick and urgency order, independent of full-card layout. */
internal object HomeQuotaLayout {
    private fun JsonObject.s(key:String)=(get(key) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.n(key:String)=s(key)?.toDoubleOrNull()?.takeIf {it.isFinite()}
    private val priority=mapOf("session" to 0,"daily" to 1,"weekly" to 2,"billing" to 3,"monthly" to 4)
    fun providerTitle(id:String)=when(id) {"claude"->"Claude";"zai"->"GLM";else->providerLabel(id)}
    fun project(all:List<Limit>,prefs:LimitDisplayPreferences,icons:Boolean=true,locale:Locale=Locale.US,now:Instant=Instant.now()):List<HomeQuotaAccount> {
        val grouped=LimitDisplay.groups(all).groupBy {it.first().provider}
        val order=LimitPreferences.order(prefs.homeProviderOrder.ifEmpty {prefs.providerOrder})+grouped.keys.filter {it !in LimitPreferences.providers}
        val accounts=mutableListOf<HomeQuotaAccount>()
        for(provider in order) {
            if(provider in prefs.hiddenHomeProviders) continue
            val groups=grouped[provider].orEmpty();val peers=groups.map {it.first()}
            val count=if(provider=="mimo") LimitPreferences.mimoGroups(groups).size else groups.size
            groups.forEachIndexed {accountIndex,original ->
                val first=original.first();if(provider in setOf("notion","workbuddy") && (first.status!="ok" || first.stale==true)) return@forEachIndexed
                val record=LimitProviderLayout.record(original);val raw=(record["windows"] as? JsonArray).orEmpty();val hidden=prefs.hiddenItems[provider].orEmpty()
                fun windowObject(w:Limit):JsonObject {
                    val index=original.indexOf(w).takeIf {it>=0} ?: original.indexOfFirst {it.presentation.limitId==w.presentation.limitId && it.copy(presentation=w.presentation)==w}
                    return raw.getOrNull(index) as? JsonObject ?: buildJsonObject {w.kind?.let {put("kind",it)};w.label?.let {put("label",it)};w.metric?.let {put("metric",it)};put("additional",w.presentation.additional)}
                }
                val visible=original.filter {it.kind!=null && (provider!="antigravity" || it.kind.isNotBlank()) && LimitPreferences.windowItem(provider,windowObject(it)) !in hidden}
                val chosen=LimitDisplay.compactWindows(visible).toMutableList()
                val balance=record["balance"] as? JsonObject
                if(provider=="mimo" && balance!=null) {
                    val expired=balance.s("planStatus")=="expired"
                    if(expired) chosen.removeAll {it.kind=="billing" && it.metric!="credits"}
                    if(expired || chosen.none {it.kind=="billing" && it.metric!="credits"}) {
                        val used=balance.n("planUsed");val limit=balance.n("planLimit");val supplied=balance.n("planPercent")
                        val percent=supplied?.coerceIn(0.0,100.0) ?: if(used!=null && limit!=null && limit>0) (used/limit*100).coerceIn(0.0,100.0) else null
                        if(expired || used!=null || limit!=null || supplied!=null) {
                            val synthetic=first.copy(kind="billing",label="Token Plan",metric=null,remaining=if(expired) null else percent?.let {100-it},resets=null,amount=null,usedAmount=null,limitAmount=null,detail=null,
                                presentation=first.presentation.copy(showMeter=!expired,usedPercent=percent,additional=false,limitId=null,compactValue=if(expired) {if(locale.language=="zh") "已过期" else "Expired"} else null,resetDescription=null))
                            if(LimitProviderLayout.windowKey(buildJsonObject {put("kind","billing");put("label","Token Plan")}) !in hidden) chosen.add(0,synthetic)
                        }
                    }
                }
                val usable=chosen.mapIndexedNotNull {index,w ->
                    val credits=w.metric=="credits";val percent=if(credits) LimitDisplay.creditsPercent(w) else if(w.presentation.showMeter) LimitDisplay.percent(w,false) else null
                    val money=(w.amount ?: w.presentation.balanceAmount)?.toDoubleOrNull()?.takeIf {it.isFinite()}
                    val fixed=w.presentation.compactValue.orEmpty()
                    if(percent==null && fixed.isEmpty() && !(credits && (money!=null || !w.detail.isNullOrBlank()))) null
                    else Triple(index,w,percent)
                }.let {rows->if(provider=="antigravity") rows else rows.sortedWith(compareBy<Triple<Int,Limit,Double?>> {priority[it.second.kind] ?: 10}.thenBy {it.first})}.take(2)
                if(usable.isEmpty()) return@forEachIndexed
                fun group(w:Limit):String? {
                    val pattern=when(w.kind) {"session"->Regex("\\s+5-hour$",RegexOption.IGNORE_CASE);"weekly"->Regex("\\s+weekly$",RegexOption.IGNORE_CASE);else->return null}
                    val label=w.label?.trim() ?: return null
                    return if(pattern.containsMatchIn(label)) label.replace(pattern,"").trim().takeIf {it.isNotEmpty()} else null
                }
                val labels=usable.map {group(it.second)}
                val named=provider=="antigravity" && labels.all {it!=null} && labels.isNotEmpty()
                val singleGroup=named && labels.distinct().size==1
                val windows=usable.map {(_,w,remaining)->
                    val credits=w.metric=="credits";val fixed=w.presentation.compactValue
                    val used=prefs.showUsed && !credits && fixed.isNullOrEmpty()
                    val percent=remaining?.let {if(used) 100-it else it}
                    val value=fixed ?: if(w.detail.equals("unlimited",true)) "Unlimited" else if(credits) LimitDisplay.creditsMoney(w) else percent?.let {"${Math.round(it)}% ${if(used) "used" else "left"}"} ?: "—"
                    // homeOverview supplies the raw kind as the label of an unnamed billing window.
                    val caption=when {provider=="notion"->if(w.kind=="session") {if(locale.language=="zh") "滚动 6 小时" else "Rolling 6 hours"} else {if(locale.language=="zh") "月度" else "Monthly"};singleGroup->"${group(w)} ${if(w.kind=="session") "5-hour" else "Weekly"}";named && labels.distinct().size==labels.size->group(w)!!;provider=="zai" && w.kind=="daily" && !w.label.isNullOrBlank()->w.label;w.kind=="billing"->w.label?.takeIf {it.isNotBlank()} ?: "billing";else->when(w.kind) {"session"->"Session";"daily"->"Daily";"weekly"->"Weekly";"monthly"->"Monthly";else->w.label.orEmpty()}}
                    val objectValue=windowObject(w)
                    val boundary=if(provider=="notion" && w.resets.isNullOrEmpty()) {if(locale.language=="zh") "重置时间未知" else "Reset time unknown"} else if(!w.resets.isNullOrEmpty()) LimitProviderLayout.boundary(objectValue,now) else w.presentation.resetDescription?.takeIf {it.isNotEmpty()}?.let {"${if(locale.language=="zh") "重置" else "Reset"} $it"}.orEmpty()
                    HomeQuotaWindow(w,objectValue,remaining,caption,value,if(named && !singleGroup && boundary.isNotEmpty()) "${if(w.kind=="session") "5-hour" else "Weekly"} · $boundary" else boundary,percent)
                }
                val showProvider=prefs.showHomeProviderNames || !icons
                val title=providerTitle(provider)
                val accountTitle=LimitDisplay.accountTitle(first,peers,accountIndex,prefs.maskEmails)
                val product=if(provider=="mimo") first.account?.takeIf {it in setOf("Console","Desktop Membership","Membership")} else null
                val name=when {count>1->if(showProvider) "$title · $accountTitle" else accountTitle;product!=null->if(showProvider) "$title · $product" else product;else->title}
                accounts+=HomeQuotaAccount(first.presentation.recordIndex ?: accounts.size,provider,windows,original,peers,accountIndex,name,LimitProviderLayout.plan(first,groups.size>1,prefs.maskEmails),windows.minOf {it.remaining ?: 100.0})
            }
        }
        return (if(prefs.homeProviderOrder.isNotEmpty()) accounts else accounts.sortedBy {it.lowest}).take(prefs.homeAccountCount)
    }
}
