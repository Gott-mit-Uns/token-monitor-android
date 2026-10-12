package dev.bing.tokenmonitor

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Fixed desktop number strings for shared USD-per-million rates, without currency conversion. */
internal fun sharedRateText(value:BigDecimal):String {
    val number=value.toDouble()
    if(number==0.0) return "0"
    val exact=BigDecimal(number)
    val shortest=(1..17).asSequence().map {exact.round(MathContext(it,RoundingMode.HALF_EVEN))}.first {it.toDouble()==number}.stripTrailingZeros()
    return if(number>=1e21 || number<1e-6) shortest.toString().replace('E','e') else shortest.toPlainString()
}
internal fun sharedPricingSummary(price:ModelPrice,labels:Map<String,String>):String =
    listOf("cacheReadPerM","inputPerM","outputPerM","cacheWritePerM","cacheWrite1hPerM").mapNotNull {key -> price.rates[key]?.let {labels.getValue(key)+" $"+sharedRateText(it)}}.joinToString(" · ")+" / 1M"

internal object SubscriptionDisplay {
    val symbols=mapOf("USD" to "$","TWD" to "NT$","HKD" to "HK$","CNY" to "¥")
    fun money(amount:String?,currency:String?):String {
        val code=currency?.trim()?.uppercase(java.util.Locale.ROOT)?.takeIf {it.isNotEmpty()} ?: return "—"
        val value=amount?.toBigDecimalOrNull()?.takeIf {it.signum()>=0} ?: return "—"
        val number=value.toDouble().takeIf {it.isFinite()} ?: return "—"
        return (symbols[code] ?: "$code ")+BigDecimal(number).setScale(2,RoundingMode.HALF_UP).toPlainString()
    }
    fun date(value:String?)=value?.let {runCatching {LocalDate.parse(it)}.getOrNull()}
    fun monthTotal(record:Subscription,today:LocalDate):BigDecimal? {
        val rows=record.topUps.filter {date(it.date)?.let {day -> YearMonth.from(day)==YearMonth.from(today)}==true}
        if(rows.any {it.amount==null}) return null
        return rows.fold(BigDecimal.ZERO) {sum,row -> sum+row.amount!!}
    }
    fun monthlyUsd(records:List<Subscription>,today:LocalDate,currency:UsageCurrency):String? {
        var total=0.0
        for(record in records) {
            val stop=record.coverageStop()
            if(stop!=null && !stop.isAfter(today)) continue
            val code=record.currency?.uppercase(java.util.Locale.ROOT)?.takeIf {it in UsageCurrency.codes} ?: return null
            val amount=if(record.kind=="topup") monthTotal(record,today)?.toDouble() else {
                if(record.autoRenew==null || date(record.start)==null) return null
                record.intervalMonths()?.let {months -> record.amount?.toDoubleOrNull()?.div(months)}
            } ?: return null
            val rate=currency.rateFor(code).toDouble()
            total+=amount/rate
        }
        return total.takeIf {it.isFinite()}?.let {BigDecimal.valueOf(it).toPlainString()}
    }
    fun elapsed(record:Subscription,today:LocalDate):Pair<Long,Boolean>? {
        if(record.kind=="topup") return null
        val start=date(record.start) ?: return null
        if(today.isBefore(start)) return null
        val stop=record.coverageStop()?.takeIf {it.isBefore(today)} ?: today
        var months=ChronoUnit.MONTHS.between(YearMonth.from(start),YearMonth.from(stop))
        if(stop.dayOfMonth<start.dayOfMonth) months--
        return if(months>=1) months to true else ChronoUnit.DAYS.between(start,stop).coerceAtLeast(0) to false
    }
    fun keyed(records:List<Subscription>):List<Pair<String,Subscription>> {
        val ids=records.mapNotNull {it.id}.groupingBy {it}.eachCount()
        val seen=mutableMapOf<String,Int>()
        return records.map {record ->
            val base=record.id?.takeIf {ids[it]==1} ?: run {
                val identity=listOf(record.provider,record.kind,record.account,record.binding?.accountKey,record.binding?.accountEmail,record.plan,record.start,record.currency).joinToString("\u0000")
                "fallback:"+java.security.MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8)).take(16).joinToString("") {"%02x".format(it)}
            }
            val index=seen[base] ?: 0;seen[base]=index+1
            (if(index==0) base else "$base:$index") to record
        }
    }
}
