package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.floor

/** Read-only projection of desktop v0.68.0 windowsView subscription cards. */
internal data class SubscriptionTooltipRow(val label:String="",val value:String="",val separator:Boolean=false,val title:String="",val warn:Boolean=false)
internal class SubscriptionReadContext(
    val records:List<Subscription>,val accounts:List<JsonObject>,val costs:Map<String,String?>,
    val today:LocalDate,val locale:Locale,val currency:UsageCurrency
) {
    private fun word(key:String,vararg params:Pair<String,String>)=SubscriptionWording.text(locale,"subscription.tooltip.$key",params.toMap())
    private fun date(day:LocalDate?)=day?.let {SubscriptionWording.date(locale,it)}.orEmpty()
    private fun days(value:Long)=if(value==0L) word("today") else word("daysLeft","days" to value.toString())
    private fun row(key:String,value:String,title:String="")=SubscriptionTooltipRow(word(key),value,title=title)
    private fun jsonText(record:JsonObject?,key:String)=(record?.get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun number(record:JsonObject?,key:String)=jsonText(record,key).toDoubleOrNull()?.takeIf {it.isFinite()}
    private fun fixed(value:Double,digits:Int)=BigDecimal(value).setScale(digits,RoundingMode.HALF_UP).toPlainString()
    private fun rate(code:String?)=code?.uppercase(Locale.ROOT)?.takeIf {it in UsageCurrency.codes}?.let {fixed(currency.rateFor(it).toDouble(),6).toDouble().takeIf {it>0}}
    private fun active(provider:String)=records.filter {it.provider.equals(provider,true) && (it.coverageStop()?.isAfter(today)!=false)}
    private fun rollup(provider:String):Pair<Int,Double?> {
        val selected=active(provider)
        var total=0.0
        for(record in selected) {
            val exchange=rate(record.currency) ?: return selected.size to null
            val units=if(record.kind=="topup") SubscriptionDisplay.monthTotal(record,today)?.toDouble()
                else record.intervalMonths()?.let {months->record.amount?.toDoubleOrNull()?.div(months)}
            if(units==null || !units.isFinite() || units<0) return selected.size to null
            total+=units/exchange
        }
        return selected.size to total.takeIf {it.isFinite()}
    }
    private fun usage(provider:String):Double? {
        val aliases=mapOf("droid" to "factory","zcode" to "zai","qodercn" to "qoder","dsh" to "deepseek","mcode" to "minimax")
        var sum=0.0
        costs.forEach {(client,value)->val id=client.trim().lowercase(Locale.ROOT);val mapped=aliases[id] ?: id
            if(mapped in desktopProviderLabels && mapped==provider.lowercase(Locale.ROOT)) sum+=value?.toDoubleOrNull()?.takeIf {it.isFinite()} ?: 0.0}
        return sum.takeIf {it.isFinite() && it>0}
    }
    private fun totalRow(provider:String,rollup:Pair<Int,Double?>)=SubscriptionTooltipRow(
        word("providerTotal","provider" to providerLabel(provider)),
        word("providerTotalValue","count" to rollup.first.toString(),"total" to (rollup.second?.let {currency.format(it.toString())} ?: "—")))
    private fun usageRow(amount:Double,all:Boolean)=row("monthUsage","≈ "+currency.format(amount.toString())+(if(all) " · "+word("allAccounts") else ""),word("monthUsageNote"))
    private fun appendRollup(rows:MutableList<SubscriptionTooltipRow>,record:Subscription,include:Boolean) {
        if(!include) return
        val usage=usage(record.provider) ?: return
        val total=rollup(record.provider);val paid=total.second?.takeIf {it>0} ?: return
        val ratio=usage/paid
        if(!ratio.isFinite()) return
        rows+=SubscriptionTooltipRow(separator=true)
        if(record.kind!="topup" && total.first>1) rows+=totalRow(record.provider,total)
        rows+=usageRow(usage,record.kind!="topup" && total.first>1)
        rows+=row("valueMultiple",fixed(ratio,1)+"×")
    }
    private fun group(provider:String):List<SubscriptionTooltipRow> {
        val total=rollup(provider);val rows=mutableListOf(totalRow(provider,total))
        usage(provider)?.let {cost->
            rows+=SubscriptionTooltipRow(separator=true);rows+=usageRow(cost,true)
            total.second?.takeIf {it>0}?.let {paid->(cost/paid).takeIf {it.isFinite()}?.let {rows+=row("valueMultiple",fixed(it,1)+"×")}}
        }
        return rows
    }
    fun forRow(provider:JsonObject,includeRollup:Boolean):List<SubscriptionTooltipRow> {
        val id=jsonText(provider,"provider").lowercase(Locale.ROOT)
        if((provider["accountGroup"] as? JsonPrimitive)?.booleanOrNull==true) {
            val drawn=(provider["groupAccounts"] as? JsonArray)?.mapNotNull {it as? JsonObject}
            val entries=records.filter {it.provider.equals(id,true)}.map {it to SubscriptionAccounts.match(it,accounts)}
                .filter {(_,account)->account==null || drawn==null || drawn.any {SubscriptionAccounts.same(account,it)}}
            return when(entries.size) {0->emptyList();1->forSubscription(entries[0].first,entries[0].second ?: provider,true);else->group(id)}
        }
        val record=records.firstOrNull {it.provider.equals(id,true) && SubscriptionAccounts.match(it,accounts)?.let {account->SubscriptionAccounts.same(account,provider)}==true} ?: return emptyList()
        return forSubscription(record,provider,includeRollup)
    }
    fun forSubscription(record:Subscription,provider:JsonObject?,includeRollup:Boolean=true):List<SubscriptionTooltipRow> {
        val rows=mutableListOf<SubscriptionTooltipRow>()
        if(record.kind=="topup") {
            record.topUps.maxByOrNull {it.date}?.let {last->rows+=row("lastTopUp",date(SubscriptionDisplay.date(last.date))+" · "+SubscriptionDisplay.money(last.amount?.toPlainString(),record.currency))}
            SubscriptionDisplay.monthTotal(record,today)?.takeIf {it.signum()>0}?.let {rows+=row("topUpMonth",SubscriptionDisplay.money(it.toPlainString(),record.currency))}
            val total=if(record.topUps.any {it.amount==null}) null else record.topUps.fold(BigDecimal.ZERO) {sum,top->sum+top.amount!!}
            if(record.topUps.size>1) rows+=row("topUpTotal",SubscriptionDisplay.money(total?.toPlainString(),record.currency)+" · "+word("topUpCount","count" to record.topUps.size.toString()))
            val window=(provider?.get("windows") as? JsonArray)?.mapNotNull {it as? JsonObject}?.firstOrNull {jsonText(it,"metric")=="credits"}
            val balanceRecord=provider?.get("balance") as? JsonObject
            val balance=number(window,"remaining") ?: number(balanceRecord,"amount")
            val code=listOf(jsonText(window,"currency"),jsonText(balanceRecord,"currency"),record.currency.orEmpty()).firstOrNull {it.isNotBlank()}
            fun money(amount:Double)=if(code==null) "—" else (when(code.uppercase(Locale.ROOT)) {"USD"->"$";"CNY"->"¥";else->code.uppercase(Locale.ROOT)+" "})+fixed(amount,2)
            if(balance!=null) {
                rows+=row("balance",money(balance))
                val first=record.topUps.minOfOrNull {it.date}?.let(SubscriptionDisplay::date)
                val elapsed=first?.let {ChronoUnit.DAYS.between(it,today)} ?: 0
                val from=rate(record.currency);val to=rate(code)
                if(total!=null && from!=null && to!=null && elapsed>0) {
                    val usd=total.toDouble()/from
                    val converted=if(code?.uppercase(Locale.ROOT)=="USD") usd else fixed(usd*to,6).toDouble()
                    val poured=floor(converted*100+0.5)/100
                    val spent=poured-balance
                    if(poured>0 && spent>0) {
                        val burn=spent/elapsed
                        if(burn.isFinite() && burn>0) {
                            rows+=row("burnRate",word("perDay","amount" to money(burn)))
                            val left=floor(balance/burn)
                            if(left.isFinite() && left in -3650000.0..3650000.0) runCatching {today.plusDays(left.toLong())}.getOrNull()?.let {rows+=row("exhausts",date(it)+" · "+days(left.toLong()))}
                        }
                    }
                }
            }
        } else {
            rows+=row("price",SubscriptionWording.price(record,locale))
            val end=record.coverageEnd(today)
            val left=end?.let {ChronoUnit.DAYS.between(today,it)}
            rows+=row(if(record.autoRenew==true) "nextCharge" else "validUntil",date(end)+(left?.let {" · "+if(it<0) word("expired") else days(it)} ?: ""))
            if(record.autoRenew==false) rows+=row("autoRenew",word("autoRenewOff"))
            val elapsed=SubscriptionDisplay.elapsed(record,today)
            val elapsedText=if(SubscriptionDisplay.date(record.start)?.isAfter(today)==true) word("notStarted") else {
                val span=if(elapsed==null) "—" else word(if(elapsed.second) "months" else "daysCount",(if(elapsed.second) "months" else "days") to elapsed.first.toString())
                span+" · "+word("paidTotal","total" to SubscriptionDisplay.money(record.recordedTotal(today)?.toPlainString(),record.currency))
            }
            rows+=row("subscribed",elapsedText)
        }
        appendRollup(rows,record,includeRollup)
        return rows
    }
}
