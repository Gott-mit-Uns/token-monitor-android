package dev.bing.tokenmonitor

import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal data class SubscriptionRowView(val title:String,val meta:String,val warning:String,val icon:Boolean)
internal object SubscriptionWording {
    fun text(locale:Locale,key:String,params:Map<String,String> = emptyMap()):String {
        var value=desktopSubscriptionWording.getValue(if(locale.language=="zh") "zh-CN" else "en")[key] ?: key
        params.forEach {(name,replacement)->value=value.replace("{$name}",replacement)}
        return value
    }
    fun date(locale:Locale,day:LocalDate,short:Boolean=false):String=day.format(
        if(short) DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
        else DateTimeFormatter.ofPattern(if(locale.language=="zh") "y年M月d日" else "MMM d, y",locale))
    fun price(record:Subscription,locale:Locale):String {
        val unit=when(record.interval) {"month"->text(locale,"settings.subscriptions.unitMonth");"year"->text(locale,"settings.subscriptions.unitYear");else->"—"}
        val cadence=when(record.count?.takeIf {it in 1..24}) {1L->unit;null->"—";else->text(locale,"settings.subscriptions.everyN",mapOf("count" to record.count.toString(),"unit" to unit))}
        return SubscriptionDisplay.money(record.amount,record.currency)+" / "+cadence
    }
}
internal object SubscriptionPresentation {
    fun row(record:Subscription,accounts:List<JsonObject>,today:LocalDate,locale:Locale,masked:Boolean,icons:Boolean):SubscriptionRowView {
        val account=SubscriptionAccounts.match(record,accounts);val label=SubscriptionAccounts.label(record,account,masked)
        val title=listOf(providerLabel(record.provider),label.ifEmpty {record.plan.orEmpty()}).filter {it.isNotEmpty()}.joinToString(" · ")
        val parts=mutableListOf<String>()
        if(label.isNotEmpty() && !record.plan.isNullOrBlank()) parts+=record.plan
        if(record.kind=="topup") {
            parts+=SubscriptionWording.text(locale,"settings.subscriptions.topUpMonthMeta",mapOf("total" to SubscriptionDisplay.money(SubscriptionDisplay.monthTotal(record,today)?.toPlainString(),record.currency)))
            record.topUps.maxByOrNull {it.date}?.let {top->SubscriptionDisplay.date(top.date)?.let {day->parts+=SubscriptionWording.text(locale,"settings.subscriptions.topUpLastMeta",mapOf("date" to SubscriptionWording.date(locale,day,short=true)))}}
        } else {
            parts+=SubscriptionWording.price(record,locale)
            record.coverageEnd(today)?.let {day->parts+=SubscriptionWording.text(locale,if(record.autoRenew==true) "settings.subscriptions.renewsOn" else "settings.subscriptions.endsOn",mapOf("date" to SubscriptionWording.date(locale,day,short=true)))}
        }
        val warning=if(SubscriptionAccounts.ambiguous(record,accounts)) SubscriptionWording.text(locale,"settings.subscriptions.needsRebind") else ""
        return SubscriptionRowView(LimitDisplay.text(title,masked),LimitDisplay.text(parts.joinToString(" · "),masked),warning,icons && record.provider in desktopProviderLabels)
    }
}
