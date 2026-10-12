package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Native projection of the fixed desktop windowsView DOM. Only named display fields are read. */
internal data class ProviderQuotaRow(val label:String, val value:String, val sourceIndex:Int?=null,
    val group:String?=null, val wide:Boolean=false, val percent:Double?=null, val tone:Double=1.0,
    val detail:String="", val boundary:String="", val noReset:Boolean=false, val item:String?=null,
    val details:List<List<String>> = emptyList(),val resetSummary:String?=null,val motion:QuotaWindowTarget?=null)

internal object LimitProviderLayout {
    // Retain only the desktop's display schema. Provider credentials and unknown
    // nested fields cannot enter the native presentation model.
    fun displayRecord(raw:JsonObject):JsonObject {
        fun fields(value:JsonObject,allowed:Set<String>,scalarOnly:Set<String> = emptySet())=JsonObject(value.filter { (key,item) ->
            key in allowed && (key !in scalarOnly || (item is JsonPrimitive && item !== JsonNull))
        })
        val header=setOf("provider","status","stale","updatedAt","checkedAt","accountLabel","accountName","accountEmail","email","planLabel","accountKey","workspaceLabel","workspaceKind","source","sourceDetail","sourceDeviceId","adapterId","balanceUsd","actionRequired","profileId")
        val window=setOf("kind","label","metric","remaining","used","limit","currency","remainingPercent","usedPercent","resetsAt","resetDescription","boundaryKind","detail","showMeter","additional","limitId","limitName","windowMinutes","id","quotaId","model","group")
        val funds=setOf("amount","currency","todaySpend","weekSpend","monthSpend","allTimeSpend","requestCount","quotaGroup","expiresAt","giftBalance","cashBalance","planUsed","planLimit","planPercent","planStatus")
        val usage=setOf("period","todayTokens","weekTokens","totalTokens","inputTokens","outputTokens","requests","cacheReadTokens","cacheCreationTokens","averageDurationMs","standardCost")
        return JsonObject(fields(raw,header,setOf("profileId")).toMutableMap().apply {
            (raw["webAccountKey"] as? JsonPrimitive)?.takeIf {it.isString}?.let {put("webAccountKey",it)}
            (raw["accountKeyAliases"] as? JsonArray)?.let {values->put("accountKeyAliases",JsonArray(values.filter {it is JsonPrimitive && it.isString}))}
            put("windows",JsonArray(raw.rows("windows").map {fields(it,window,setOf("id","quotaId","model","group"))}))
            raw.obj("balance")?.let {value->put("balance",JsonObject(fields(value,funds).toMutableMap().apply {if(value["tranches"] is JsonArray) put("tranches",JsonArray(value.rows("tranches").map {fields(it,setOf("amount","currency","expiresAt"))}))}))}
            raw.obj("usageSummary")?.let {put("usageSummary",fields(it,usage))}
            raw.obj("resetCredits")?.let {reset->put("resetCredits",JsonObject(fields(reset,setOf("availableCount","expirations","nextExpiresAt")).toMutableMap().apply {
                if(reset["grants"] is JsonArray) put("grants",JsonArray(reset.rows("grants").map {fields(it,setOf("label","resetsLeft","endsAt","paused","clears","useRequiresLimit","usableNow"))}))
            }))}
        })
    }
    fun record(windows:List<Limit>):JsonObject {
        windows.first().presentation.providerRecord?.let {return it}
        val first=windows.first();val identity=first.presentation.identity
        return buildJsonObject {
            put("provider",first.provider);first.status?.let {put("status",it)};first.stale?.let {put("stale",it)};first.updated?.let {put("updatedAt",it)}
            first.account?.let {put("accountLabel",it)};identity.plan?.let {put("planLabel",it)};identity.name?.let {put("accountName",it)}
            identity.key?.let {put("accountKey",it)};identity.email?.let {put("accountEmail",it)}
            put("windows",buildJsonArray {windows.filter {it.kind!=null}.forEach {w->add(buildJsonObject {
                put("kind",w.kind);w.label?.let {put("label",it)};w.metric?.let {put("metric",it)};w.remaining?.let {put("remainingPercent",it)};w.presentation.usedPercent?.let {put("usedPercent",it)}
                listOf("remaining" to w.amount,"used" to w.usedAmount,"limit" to w.limitAmount).forEach {(key,value)->value?.toDoubleOrNull()?.let {put(key,it)}}
                w.currency?.let {put("currency",it)};w.resets?.let {put("resetsAt",it)};w.detail?.let {put("detail",it)};w.boundaryKind?.let {put("boundaryKind",it)}
                put("showMeter",w.presentation.showMeter);put("additional",w.presentation.additional);w.presentation.limitId?.let {put("limitId",it)};w.presentation.resetDescription?.let {put("resetDescription",it)}
            })}})
            if(first.presentation.balanceAmount!=null) put("balance",buildJsonObject {put("amount",first.presentation.balanceAmount);first.presentation.balanceCurrency?.let {put("currency",it)};first.presentation.monthSpend?.let {put("monthSpend",it)}})
        }
    }
    fun compactRecord(windows:List<Limit>,record:JsonObject=record(windows)):JsonObject {
        val raw=(record["windows"] as? JsonArray).orEmpty()
        // Zed's compact selection adds a display-only override to a copy. Match
        // its original window before filtering the immutable provider record.
        val indexes=LimitDisplay.compactWindows(windows).map {selected->
            windows.indexOf(selected).takeIf {it>=0} ?: windows.indexOfFirst {candidate->
                candidate.presentation.limitId==selected.presentation.limitId && candidate.copy(presentation=selected.presentation)==selected
            }
        }.toSet()
        return JsonObject(record.toMutableMap().apply {put("windows",JsonArray(raw.filterIndexed {index,_->index in indexes}))})
    }
    fun status(status:String?)=when(status) {"ok"->"Live";"disabled"->"Disabled";"notConfigured"->"Not signed in";"noSyncedData"->"No synced data";"unauthorized"->"Sign in again";"rateLimited"->"Limited";"sourceRateLimited"->"Usage API limited";"unavailable"->"Unavailable";else->"Error"}
    fun plan(window:Limit,grouped:Boolean,masked:Boolean):String {
        if(window.status!=null && window.status!="ok" && window.stale!=true) return status(window.status)
        val record=window.presentation.providerRecord;val explicit=window.presentation.identity.plan.orEmpty()
        if(grouped && window.provider=="volcengine" && window.status=="ok") return ""
        if(window.provider=="thirdparty" && window.status=="ok") when(record?.s("adapterId")) {"newapi-account"->return "New API · Account";"newapi-token"->return "New API · API key";"sub2api"->return "Sub2API · Account";"custom"->return "Custom"}
        if(grouped && (window.status=="ok" || window.stale==true) && explicit.isBlank()) {
            if(window.provider=="volcengine" && window.status=="ok") return ""
            if(window.provider=="opencode" && window.presentation.identity.name.isNullOrBlank() && !window.account.isNullOrBlank() && window.account !in setOf("Go","Zen")) return ""
            if(window.provider=="openrouter" && !window.presentation.identity.name.isNullOrBlank() && window.presentation.identity.name==window.account) return ""
        }
        val value=LimitDisplay.plan(if(record!=null) window.copy(account=record.s("accountLabel")) else window,masked)
        return value.ifEmpty {if(window.status!=null && window.status!="ok") status(window.status) else ""}
    }
    fun meta(window:Limit,now:Instant,display:LimitDisplayPreferences):String {
        if(window.stale!=true && window.status!="ok") return status(window.status)
        val at=window.updated?.let {runCatching {Instant.parse(it)}.getOrNull()}
        val age=at?.let {(now.toEpochMilli()-it.toEpochMilli()).coerceAtLeast(0)}
        val ageText=age?.let {if(it<45000) "just now" else {val m=Math.round(it/60000.0);val h=Math.round(m/60.0);when {m<60->"${m}m ago";h<24->"${h}h ago";else->"${Math.round(h/24.0)}d ago"}}}
        val freshness=if(window.stale==true) "Stale"+(ageText?.let {" · $it"} ?: "") else ageText?.let {"Updated $it"} ?: "Update unknown"
        return buildList {add(freshness);if(display.showSource) {if(window.stale!=true) LimitDisplay.source(window).takeIf {it.isNotEmpty()}?.let(::add);window.presentation.sourceDevice?.let {add(LimitDisplay.text(it,display.maskEmails))}}}.joinToString(" · ")
    }
    fun adapterMark(window:Limit):String=if(window.provider=="thirdparty") when(window.presentation.providerRecord?.s("adapterId")) {"newapi-account","newapi-token"->"newapi";"sub2api"->"sub2api";else->"thirdparty"} else window.provider
    private fun JsonObject.s(key:String)=(get(key) as? JsonPrimitive)?.contentOrNull?.takeIf {it.isNotEmpty()}
    private fun JsonObject.n(key:String)=s(key)?.toDoubleOrNull()?.takeIf {it.isFinite()}
    private fun JsonObject.flag(key:String)=(get(key) as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.obj(key:String)=get(key) as? JsonObject
    private fun JsonObject.rows(key:String)=(get(key) as? JsonArray).orEmpty().mapNotNull {it as? JsonObject}
    private fun count(value:Double)=BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    private fun money(value:Double?,currency:String?)=LimitDisplay.money(value?.toString(),currency)
    private fun money(record:JsonObject?,key:String,currency:String?)=money(record?.n(key),currency)
    private val fiveHour=setOf("alibaba","antigravity","cline","commandcode","kimi","volcengine","zai","zaiteam")
    fun label(provider:String,window:JsonObject,fallback:String=""):String=window.s("label")?.trim()?.takeIf {it.isNotEmpty()} ?: when(window.s("kind")) {
        "session" -> if(provider in fiveHour) "5-hour" else "Session";"daily" -> "Daily";"weekly" -> "Weekly";"billing" -> "Monthly";else -> fallback
    }
    fun duration(ms:Long):String {
        val minutes=Math.round(ms.coerceAtLeast(0)/60000.0)
        return when {minutes>=1440 -> "${minutes/1440}d ${(minutes%1440)/60}h";minutes>=60 -> "${minutes/60}h ${minutes%60}m";minutes>0 -> "${minutes}m";else -> "<1m"}
    }
    internal fun boundary(window:JsonObject,now:Instant):String {
        val at=window.s("resetsAt")?.let {runCatching {Instant.parse(it)}.getOrNull()}
        if(at==null) return window.s("resetDescription").orEmpty()
        val left=at.toEpochMilli()-now.toEpochMilli()
        if(left< -60000) return ""
        val prefix=when(window.s("boundaryKind")) {"expiry" -> "Expires";"mixed" -> "Changes in";else -> "Reset"}
        return if(left<=0) {if(window.s("boundaryKind")=="mixed") "Changes now" else "$prefix now"} else "$prefix ${duration(left)}"
    }
    fun item(window:JsonObject):String? {
        window.s("metric")?.takeIf {it in setOf("credits","spend")}?.let {return it}
        return windowKey(window)
    }
    fun windowKey(window:JsonObject):String? {
        val kind=window.s("kind") ?: return null
        return buildJsonArray {
            val id=window.s("limitId")?.trim()?.takeIf {it.isNotEmpty()}
            if(id!=null) {add("id");add(id);add(kind);add(window.s("metric").orEmpty());add(window.flag("additional")==true);val minutes=window.n("windowMinutes")?.takeIf {it>0};if(minutes==null) add(JsonNull) else add(if(minutes==minutes.toLong().toDouble()) JsonPrimitive(minutes.toLong()) else JsonPrimitive(minutes))}
            else {add(kind);add(window.s("label").orEmpty());add(window.s("metric").orEmpty());add(window.flag("additional")==true)}
        }.toString()
    }
    private fun percent(window:JsonObject,used:Boolean):Double? {
        val remaining=window.n("remainingPercent")?.takeIf {it in 0.0..100.0} ?: window.n("usedPercent")?.takeIf {it in 0.0..100.0}?.let {100-it}
        return remaining?.let {if(used) 100-it else it}
    }
    private fun creditsPercent(provider:JsonObject,window:JsonObject?):Double? {
        window?.n("usedPercent")?.takeIf {it in 0.0..100.0}?.let {return 100-it}
        window?.n("remainingPercent")?.takeIf {it in 0.0..100.0}?.let {return it}
        val balance=provider.obj("balance")
        val amount=(window?.n("remaining") ?: balance?.n("amount"))?.coerceAtLeast(0.0) ?: return null
        if(amount==0.0) return 0.0
        return (amount/(amount+(balance?.n("monthSpend") ?: 0.0).coerceAtLeast(0.0))*100).coerceIn(0.0,100.0)
    }
    private fun edited(window:JsonObject,vararg values:Pair<String,JsonElement?>)=JsonObject(window.toMutableMap().apply {values.forEach {(k,v)->if(v==null) remove(k) else put(k,v)}})
    private fun text(provider:JsonObject,window:JsonObject,used:Boolean):LimitWindowText {
        val limit=Limit(provider.s("provider").orEmpty(),provider.s("status"),window.n("remainingPercent"),window.s("resetsAt"),
            provider.s("accountLabel"),window.s("kind"),window.s("metric"),window.s("remaining"),window.s("currency"),provider.flag("stale"),provider.s("updatedAt"),window.s("label"),
            window.s("used"),window.s("limit"),window.s("boundaryKind"),window.s("detail"),window.s("source"),
            LimitPresentation(usedPercent=window.n("usedPercent"),showMeter=window.flag("showMeter")!=false,limitId=window.s("limitId")))
        return LimitDisplay.windowText(limit,used)
    }
    fun project(provider:JsonObject,used:Boolean=false,locale:Locale=Locale.US,now:Instant=Instant.now(),zone:ZoneId=ZoneId.systemDefault()):List<ProviderQuotaRow> {
        val id=provider.s("provider").orEmpty();val windows=provider.rows("windows");val balance=provider.obj("balance")
        val result=mutableListOf<ProviderQuotaRow>()
        fun first(kind:String)=windows.firstOrNull {it.s("kind")==kind}
        fun all(kind:String)=windows.filter {it.s("kind")==kind}
        fun credits()=windows.firstOrNull {it.s("metric")=="credits"}
        fun row(window:JsonObject?,name:String?=null,tone:Double=0.68,wide:Boolean=false,value:String?=null,detail:String="",noReset:Boolean=false,group:String?=null,itemId:String?=null) {
            if(window==null) return
            val showMeter=window.flag("showMeter")!=false
            val fill=if(showMeter) percent(window,used && value==null) else null
            val headline=value ?: if(fill!=null) "${Math.round(fill)}% ${if(used) "used" else "left"}" else if(window.s("detail").equals("unlimited",true)) "Unlimited" else window.n("remaining")?.let {count(it)+(if(showMeter) " left" else "")} ?: window.n("limit")?.let {count(it)+" cap"} ?: window.s("detail").orEmpty().ifEmpty {"—"}
            val motion=fill?.let {QuotaResetPolicy.target(provider,name ?: label(id,window),window,it,
                if(value==null) if(used) "used" else "left" else null)}
            result+=ProviderQuotaRow(name ?: label(id,window),headline,windows.indexOf(window).takeIf {it>=0},group,wide,fill,tone,detail,if(noReset) "" else boundary(window,now),noReset,itemId ?: item(window),motion=motion)
        }
        fun moneyBalance(window:JsonObject?,name:String="Balance",tone:Double=0.95,meter:Boolean=true,boundaryAt:String?=null,detail:String="") {
            val amount=window?.n("remaining") ?: balance?.n("amount") ?: return
            val currency=window?.s("currency") ?: balance?.s("currency")
            val synthetic=edited(window ?: JsonObject(emptyMap()),"showMeter" to JsonPrimitive(meter),"remainingPercent" to creditsPercent(provider,window)?.let {JsonPrimitive(it)},"usedPercent" to null,
                "resetsAt" to boundaryAt?.let {JsonPrimitive(it)},"resetDescription" to null)
            row(synthetic,name,tone,true,money(amount,currency),detail,boundaryAt==null,itemId="credits")
        }
        fun spend() {
            if(balance==null) return
            val entries=listOf("Today" to "todaySpend","Week" to "weekSpend","Month" to "monthSpend","All time" to "allTimeSpend").mapNotNull {(name,key)->balance.n(key)?.let {name to money(it,balance.s("currency"))}}
            if(entries.isEmpty()) return
            val selected=entries.filter {it.first in setOf("Today","Month")}.ifEmpty {entries.take(2)}
            result+=ProviderQuotaRow("Spend",selected.joinToString(" · ") {"${it.first} ${it.second}"},wide=true,item="spend",details=if(entries.size>selected.size) entries.map {listOf(it.first,it.second)} else emptyList())
        }
        fun thirdPartySpend(credit:JsonObject?) {
            val currency=balance?.s("currency") ?: "USD";val usage=provider.obj("usageSummary")
            val details=mutableListOf<List<String>>()
            val captions=if(locale.language=="zh") listOf("总额度","请求次数","分组","到期日","本月请求","本月 Tokens","输入 Tokens","输出 Tokens","缓存 Tokens","平均响应","标准成本") else listOf("Total quota","Requests","Group","Expires","Month requests","Month tokens","Input tokens","Output tokens","Cache tokens","Avg response","Standard cost")
            val counts=NumberFormat.getIntegerInstance(Locale.US)
            fun countDetail(label:Int,value:Double?) {value?.let {details+=listOf(captions[label],counts.format(it.coerceAtLeast(0.0).toLong()))}}
            credit?.n("limit")?.let {details+=listOf(captions[0],money(it,currency))}
            countDetail(1,balance?.n("requestCount"));balance?.s("quotaGroup")?.let {details+=listOf(captions[2],it)}
            balance?.s("expiresAt")?.let {runCatching {Instant.parse(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}.getOrNull()}?.let {details+=listOf(captions[3],"${it.monthValue}/${it.dayOfMonth}/${it.year}")}
            listOf("requests","totalTokens","inputTokens","outputTokens").forEachIndexed {i,key->countDetail(i+4,usage?.n(key))}
            val cache=(usage?.n("cacheReadTokens") ?: 0.0)+(usage?.n("cacheCreationTokens") ?: 0.0);if(cache>0) countDetail(8,cache)
            usage?.n("averageDurationMs")?.let {ms->details+=listOf(captions[9],if(ms<1000) "${Math.round(ms)} ms" else String.format(Locale.US,if(ms<10000) "%.1f s" else "%.0f s",ms/1000))}
            usage?.n("standardCost")?.let {details+=listOf(captions[10],money(it,currency))}
            val summary=listOfNotNull(balance?.n("monthSpend")?.let {"Month ${money(it,currency)}"},balance?.n("allTimeSpend")?.let {"All time ${money(it,currency)}"}).joinToString(" · ")
            if(summary.isNotEmpty() || details.isNotEmpty()) result+=ProviderQuotaRow(if(summary.isEmpty()) "Details" else "Spend",summary,wide=true,item="spend",details=details)
        }
        fun rates(monthly:Boolean=false,monthlyTone:Double=0.5) {
            row(first("session"),tone=0.95,wide=first("weekly")==null)
            row(first("weekly"),wide=first("session")==null)
            if(monthly) all("billing").forEach {w->row(w,tone=monthlyTone,wide=true,detail=text(provider,w,used).detail)}
        }
        fun date(value:String?)=value?.let {runCatching {Instant.parse(it)}.getOrNull()}
        fun expiryLabel(at:Instant)=DateTimeFormatter.ofPattern(if(locale.language=="zh") "M/d H:mm" else "M/d, h:mm a",locale).withZone(zone).format(at)
        fun until(at:Instant)=if(at<=now) "now" else duration(at.toEpochMilli()-now.toEpochMilli())
        fun resets() {
            val credits=provider.obj("resetCredits") ?: return
            val count=credits.n("availableCount")?.let {kotlin.math.floor(it).coerceAtLeast(0.0)}?.takeIf {it>0} ?: return
            val dates=(credits["expirations"] as? JsonArray).orEmpty().mapNotNull {(it as? JsonPrimitive)?.contentOrNull?.let(::date)}.sorted().ifEmpty {listOfNotNull(date(credits.s("nextExpiresAt")))}
            val summary=dates.take(3).map(::until)+(if(dates.size>3) listOf("+${dates.size-3}") else emptyList())
            val details=mutableListOf<List<String>>()
            val grants=if(id=="claude") credits.rows("grants") else emptyList()
            if(grants.isEmpty()) dates.forEach {details+=listOf(expiryLabel(it),until(it))} else grants.forEach {grant->
                grant.s("label")?.let {details+=listOf(it)}
                val end=date(grant.s("endsAt"));val remaining=if(grant.flag("paused")==true) "Paused" else end?.let {if(it<=now) "Expired" else until(it)}.orEmpty()
                details+=listOf("Expires",if(end==null) remaining.ifEmpty {"No expiry"} else "${expiryLabel(end)} · $remaining")
                val clearKeys=(grant["clears"] as? JsonArray).orEmpty().mapNotNull {(it as? JsonPrimitive)?.contentOrNull}
                val clears=clearKeys.filter {it!="seven_day_overage_included" || "seven_day" !in clearKeys}.map {when(it) {
                    "five_hour"->"Session";"seven_day"->"Weekly";"seven_day_overage_included"->"Fable weekly";"seven_day_opus"->"Opus weekly";"seven_day_sonnet"->"Sonnet weekly";"seven_day_oauth_apps"->"OAuth apps weekly";"seven_day_cowork"->"Cowork weekly";"seven_day_omelette"->"Omelette weekly";else->it.replace('_',' ').trim()
                }}.filter {it.isNotEmpty()}.joinToString(" · ")
                if(clears.isNotEmpty()) details+=listOf("Clears",clears)
                if(grant.flag("useRequiresLimit")==true) details+=listOf("Usable","at a limit only") else if(grant.flag("usableNow")==false) details+=listOf("Usable","not right now")
            }
            result+=ProviderQuotaRow("","${count(count)} reset${if(count==1.0) "" else "s"}",wide=true,item="resets",details=details,resetSummary=summary.joinToString("·"))
        }
        when(id) {
            "codex" -> {
                val primary=windows.filter {it.flag("additional")!=true}
                listOf("session","weekly","billing").forEach {kind->row(primary.firstOrNull {it.s("kind")==kind},tone=if(kind=="session") 0.95 else 0.68,wide=kind=="billing" || (kind=="session" && primary.none {it.s("kind") in setOf("weekly","billing")}) || (kind=="weekly" && primary.none {it.s("kind") in setOf("session","billing")}))}
                windows.filter {it.flag("additional")==true}.forEach {w->row(w,tone=0.78,wide=true)}
                resets()
            }
            "cursor" -> windows.forEach {w->val line=text(provider,w,used);row(w,w.s("label") ?: "Quota",wide=true,value=line.value,detail=line.detail)}
            "antigravity" -> {
                val rateWindows=windows.filter {it.s("kind") in setOf("session","weekly")}
                fun group(w:JsonObject):String? {val suffix=if(w.s("kind")=="session") "\\s+5-hour$" else "\\s+weekly$";val name=w.s("label")?.trim() ?: return null;val pattern=Regex(suffix,RegexOption.IGNORE_CASE);return if(pattern.containsMatchIn(name)) name.replace(pattern,"").trim().takeIf {it.isNotEmpty()} else null}
                if(rateWindows.isNotEmpty() && rateWindows.all {group(it)!=null}) rateWindows.groupBy {group(it)}.forEach {(name,rows)->rows.forEach {w->row(edited(w,"label" to JsonPrimitive(if(w.s("kind")=="session") "5-hour" else "Weekly")),tone=if(w.s("kind")=="session") 0.95 else 0.78,wide=rows.size==1,group=name,itemId=item(w))}}
                else windows.forEach {row(it,tone=0.78,wide=true)}
            }
            "opencode" -> {
                row(first("session"),tone=0.95);row(first("weekly"));val credit=credits()
                row(all("billing").firstOrNull {it!==credit},tone=0.5,wide=true)
                val amount=credit?.n("remaining") ?: balance?.n("amount") ?: provider.n("balanceUsd")
                if(amount!=null) row(buildJsonObject {put("showMeter",false)},credit?.let {label(id,it,"Balance")} ?: "Balance",wide=true,value=money(amount,credit?.s("currency") ?: balance?.s("currency") ?: "USD"),itemId="credits")
            }
            "grok","copilot","kiro","qoder","zed" -> {
                val selected=if(id in setOf("grok","qoder")) listOfNotNull(first("billing")) else all("billing")
                selected.forEach {w->val line=text(provider,w,used);val name=when(id) {"qoder" -> w.s("label") ?: "Credits";"kiro" -> w.s("label") ?: if(w.flag("showMeter")==false) "Overage" else "Credits";"zed" -> w.s("label") ?: "Token Spend";else -> label(id,w)}
                    row(w,name,tone=if(id=="zed") 0.95 else if(id=="kiro" && w.flag("showMeter")==false) 0.6 else 0.68,wide=true,value=line.value,detail=line.detail,noReset=(id=="kiro" && w.flag("showMeter")==false)||(id=="zed" && w.s("limitId")=="zed.edit-predictions" && w.s("detail").equals("unlimited",true))) }
            }
            "commandcode","kimi" -> rates(true)
            "volcengine" -> listOf("session","daily","weekly","billing").mapNotNull {first(it)}.forEach {row(it,tone=when(it.s("kind")){"session"->0.95;"daily"->0.78;else->0.68})}
            "alibaba","stepfun" -> {row(first("billing"),wide=true);rates()}
            "ollama" -> rates()
            "factory" -> {
                val credit=credits()
                for(additional in listOf(false,true)) {
                    val pool=windows.filter {it!==credit && (it.flag("additional")==true)==additional}
                    val rate=pool.filter {it.s("kind")!="billing"};rate.forEachIndexed {index,w->row(w,tone=if(w.s("kind")=="session") 0.95 else 0.68,wide=rate.size%2==1 && index==rate.lastIndex)}
                    pool.filter {it.s("kind")=="billing"}.forEach {row(it,tone=0.5,wide=true)}
                }
                credit?.let {moneyBalance(it,label(id,it,"Extra usage balance"),0.68,false)}
            }
            "devin" -> {val rates=listOfNotNull(first("daily"),first("weekly"));rates.forEach {row(it,tone=if(it.s("kind")=="daily") 0.95 else 0.68,wide=rates.size==1)};credits()?.let {moneyBalance(it,label(id,it,"Extra usage balance"),0.68,false)}}
            "workbuddy","trae" -> {
                first("billing")?.let {w->val amount=w.n("remaining") ?: balance?.n("amount");val value=amount?.let {money(it,w.s("currency") ?: balance?.s("currency"))} ?: if(w.s("detail").equals("unlimited",true)) "Unlimited" else null
                    if(value!=null) {row(w,w.s("label") ?: "Credits",0.95,true,value,noReset=w.s("resetsAt")==null && w.s("resetDescription")==null,itemId=item(w));spend()}}
            }
            "deepseek","typesafe" -> {if(balance!=null) {
                val credit=credits();val grant=if(id=="typesafe") balance.rows("tranches").firstOrNull {it.s("expiresAt")?.let {runCatching {Instant.parse(it)>now}.getOrDefault(false)}==true} else null
                val at=if(id=="typesafe") grant?.s("expiresAt") else credit?.s("resetsAt")
                val detail=if(grant!=null && kotlin.math.abs((grant.n("amount") ?: 0.0)-(balance.n("amount") ?: 0.0))>=0.005) money(grant.n("amount"),grant.s("currency") ?: balance.s("currency")) else ""
                moneyBalance(edited(credit ?: JsonObject(emptyMap()),"remaining" to balance["amount"],"currency" to balance["currency"]),boundaryAt=at,detail=detail)
                val usage=provider.obj("usageSummary")
                if(id!="typesafe") spend() else if(usage?.s("period")=="month" && usage.n("totalTokens")!=null) {
                    val captions=if(locale.language=="zh") listOf("今日","近 7 天","Tokens","输入 Tokens","输出 Tokens","请求次数","预估花费") else listOf("Today","Last 7 days","Tokens","Input tokens","Output tokens","Requests","Estimated spend")
                    val counts=NumberFormat.getIntegerInstance(locale);val details=mutableListOf<List<String>>()
                    listOf("todayTokens","weekTokens","totalTokens","inputTokens","outputTokens","requests").forEachIndexed {i,key->if(i==2) details+=listOf(if(locale.language=="zh") "本月" else "Month");usage.n(key)?.let {details+=listOf(captions[i],counts.format(it))}}
                    usage.n("standardCost")?.let {details+=listOf(captions[6],if(it>0 && it<0.00005) "<$0.0001" else "$"+BigDecimal(it).setScale(4,java.math.RoundingMode.HALF_UP).toPlainString())}
                    fun brief(n:Double)=if(kotlin.math.abs(n)>=1000) count(n) else counts.format(n)
                    result+=ProviderQuotaRow("Tokens",listOfNotNull(usage.n("todayTokens")?.let {"Today ${brief(it)}"},usage.n("totalTokens")?.let {"Month ${brief(it)}"}).joinToString(" · "),wide=true,item="spend",details=details)
                }
            }}
            "claude" -> {
                row(first("session"),tone=0.95);all("weekly").forEach {row(it,wide=it.s("label")!=null)}
                windows.firstOrNull {it.s("metric")=="spend" || (it.s("metric")==null && it.s("kind")=="billing" && it.s("label")=="Usage credits")}?.let {row(it,"Usage credits",0.5,true,text(provider,it,used).value,noReset=true,itemId="spend")}
                balance?.n("amount")?.let {amount->
                    val details=balance.rows("tranches").mapNotNull {grant->grant.n("amount")?.let {funds->val at=date(grant.s("expiresAt"));listOf(money(funds,grant.s("currency") ?: balance.s("currency")),at?.let(::expiryLabel).orEmpty(),at?.let {if(it<=now) "Expired" else until(it)} ?: "No expiry")}}
                    result+=ProviderQuotaRow("Balance",money(amount,balance.s("currency")),wide=true,item="credits",details=details)
                }
                resets()
            }
            "openrouter" -> {
                val credit=credits() ?: windows.firstOrNull {it.s("label")=="Credits"}
                balance?.n("amount")?.let {amount->val synthetic=credit ?: buildJsonObject {put("showMeter",amount==0.0);if(amount==0.0) put("remainingPercent",0)}
                    row(synthetic,"Balance",0.95,true,money(amount,balance.s("currency")),noReset=true,itemId="credits")}
                windows.filter {it!==credit}.forEach {w->val meter=w.flag("showMeter")!=false;val detail=if(meter && w.n("remaining")!=null && w.n("limit")!=null) "${money(w,"remaining","USD")} left · ${money(w,"limit","USD")} total" else ""
                    row(w,w.s("label") ?: "Usage",if(meter) 0.85 else 0.6,true,if(meter) null else w.s("detail") ?: "—",detail,noReset=!meter)}
                spend()
            }
            "thirdparty" -> {val credit=credits();if(balance?.n("amount")!=null) moneyBalance(edited(credit ?: JsonObject(emptyMap()),"remaining" to balance["amount"],"currency" to JsonPrimitive(balance.s("currency") ?: "USD")),credit?.s("label") ?: "Balance")
                else if(credit?.flag("showMeter")==false && credit.s("detail")!=null) row(credit,credit.s("label") ?: "Balance",0.95,true,value=if(credit.s("detail").equals("unlimited",true)) "Unlimited" else credit.s("detail"),noReset=true,itemId="credits")
                thirdPartySpend(credit)}
            "mimo" -> {
                val quotas=windows.filter {it.s("metric")!="credits"};quotas.forEach {row(it,wide=true)}
                if(quotas.none {!it.s("label").isNullOrBlank()} && balance!=null) {
                    val usedValue=balance.n("planUsed");val cap=balance.n("planLimit");val planPercent=balance.n("planPercent") ?: if(usedValue!=null && cap!=null && cap>0) usedValue/cap*100 else null
                    if(balance.s("planStatus")=="expired") row(buildJsonObject {put("showMeter",false)},"Token Plan",wide=true,value=if(locale.language=="zh") "已过期" else "Expired",noReset=true)
                    else if(usedValue!=null || cap!=null || planPercent!=null) row(buildJsonObject {put("kind","billing");put("label","Token Plan");planPercent?.let {put("remainingPercent",100-it.coerceIn(0.0,100.0))}},wide=true)
                }
                val credit=credits();val amount=credit?.n("remaining") ?: balance?.n("amount")
                if(amount!=null || balance?.n("giftBalance")!=null || balance?.n("cashBalance")!=null) {
                    val currency=credit?.s("currency") ?: balance?.s("currency");val detail=listOfNotNull(balance?.n("giftBalance")?.let {"Gift ${money(it,currency)}"},balance?.n("cashBalance")?.let {"Cash ${money(it,currency)}"}).joinToString(" · ")
                    val synthetic=buildJsonObject {val fill=creditsPercent(provider,credit);if(fill!=null) put("remainingPercent",fill) else put("showMeter",false)}
                    row(synthetic,"Balance",0.68,true,money(amount,currency),detail,noReset=true,itemId="credits");spend()
                }
            }
            "cline" -> {
                row(first("session"),tone=0.95);row(first("weekly"));row(all("billing").firstOrNull {it.s("metric") !in setOf("credits","spend")},tone=0.5,wide=true)
                val credit=all("billing").firstOrNull {it.s("metric")=="credits"};val consumed=all("billing").firstOrNull {it.s("metric")=="spend"}
                val amount=credit?.n("remaining") ?: balance?.n("amount")
                if(credit!=null && amount!=null) result+=ProviderQuotaRow(credit.s("label") ?: "Credits",money(amount,credit.s("currency") ?: balance?.s("currency")),wide=true,item="credits",details=consumed?.n("used")?.let {listOf(listOf("Month spent",money(it,consumed.s("currency"))))} ?: emptyList())
            }
            "zai","zaiteam" -> {
                val pairs=listOfNotNull(first("session"))+all("daily")+listOfNotNull(first("weekly"))+all("billing").filter {it.s("limitId")!=null && it.s("metric")==null}
                pairs.forEachIndexed {index,w->row(w,if(w.s("kind")=="daily") w.s("label") ?: if(all("daily").size>1) "Daily ${all("daily").indexOf(w)+1}" else "Daily" else w.s("label") ?: if(w.s("kind")=="billing") "Start Plan" else label(id,w),tone=when(w.s("kind")){"session"->0.95;"daily"->0.78;else->0.68},wide=pairs.size%2==1 && index==pairs.lastIndex,detail=text(provider,w,used).detail)}
                all("billing").filter {it.s("metric")==null && it.s("limitId")==null}.forEach {row(it,it.s("label") ?: "MCP",wide=true,detail=it.s("detail").orEmpty())}
                credits()?.let {moneyBalance(it);spend()}
            }
            else -> rates()
        }
        // CSS also spans a sole remaining window. Preserve pairing barriers and groups.
        return result.map {row->if(result.size==1 || (row.group!=null && result.count {it.group==row.group}==1)) row.copy(wide=true) else row}
    }
}
