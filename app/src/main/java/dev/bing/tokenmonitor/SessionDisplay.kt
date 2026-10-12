package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.ceil
import kotlin.math.floor

data class SessionPromptCache(val observedAt:String,val ttlSeconds:Int)
data class SessionAccounting(val calls:Double=0.0,val input:Double=0.0,val cacheRead:Double=0.0,val cacheWrite:Double=0.0,
    val output:Double=0.0,val timedOutput:Double=0.0,val timedDurationMs:Double=0.0)
/** Only aggregate display metadata provided by the Hub; no transcript or message fields. */
data class SessionMetadata(val title:String?=null,val projectLabel:String?=null,val sessionId:String?=null,
    val startedAt:String?=null,val kind:String?=null,val archived:Boolean=false,val models:Map<String,Long> = emptyMap(),
    val contextTokens:Double?=null,val contextWindow:Double?=null,val promptCache:SessionPromptCache?=null,
    val accounting:SessionAccounting=SessionAccounting(),val excludedFromHubList:Boolean=false)

internal object SessionMetadataParser {
    fun parse(row:JsonObject,title:String?):SessionMetadata {
        fun text(key:String)=((row[key] as? JsonPrimitive)?.contentOrNull)?.takeIf {it.isNotBlank() && it.length<=1024 && it.none(Char::isISOControl)}
        fun positive(key:String)=(row[key] as? JsonPrimitive)?.doubleOrNull?.takeIf {it.isFinite() && it>0}
        fun counter(key:String)=(row[key] as? JsonPrimitive)?.doubleOrNull?.takeIf {it.isFinite()} ?: 0.0
        val cache=(row["promptCache"] as? JsonObject)?.let {raw->
            val at=(raw["observedAt"] as? JsonPrimitive)?.contentOrNull?.takeIf {it.length<=128}
            val ttl=(raw["ttlSeconds"] as? JsonPrimitive)?.intOrNull?.takeIf {it in listOf(300,1800,3600)}
            if(at!=null && ttl!=null) SessionPromptCache(at,ttl) else null
        }
        val models=(row["models"] as? JsonObject).orEmpty().mapNotNull {(id,value)->
            (value as? JsonPrimitive)?.longOrNull?.takeIf {it>0}?.let {id to it}
        }.toMap()
        return SessionMetadata(title,text("projectLabel"),text("sessionId"),text("startedAt"),text("sessionKind"),
            listOf("archived","deleted","sourceDeleted").any {(row[it] as? JsonPrimitive)?.booleanOrNull==true},models,
            positive("contextTokens"),positive("contextWindow"),cache,
            SessionAccounting(counter("messageCount"),counter("inputTokens"),counter("cacheReadTokens"),counter("cacheWriteTokens"),
                counter("outputTokens"),counter("timedOutputTokens"),counter("timedDurationMs")),
            ((row["sessionId"] as? JsonPrimitive)?.contentOrNull?.takeIf {it.isNotEmpty()} ?: (row["session_id"] as? JsonPrimitive)?.contentOrNull).orEmpty().trim().lowercase(java.util.Locale.ROOT).let {it.startsWith("reasonix:") || it.startsWith("reasonix-stats:")})
    }
}

internal data class SessionContext(val tokens:Double,val window:Double,val left:Int,val used:Int,val tone:String)
internal data class SessionCache(val expiresAt:Long,val ttlSeconds:Int,val minutes:Int)
internal data class SessionModelShare(val name:String,val tokens:BigInteger,val share:String)

/** Ports sessionLive.js and the Home's recentSessionRows(...,5,includeRunningBeyondCap). */
internal object SessionDisplay {
    const val runningWindow=600_000L
    fun timestamp(value:String?):Long?=value?.takeIf {it.isNotBlank()}?.let {
        runCatching {Instant.parse(it).toEpochMilli()}.getOrNull()
            ?: runCatching {LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()}.getOrNull()
    }
    fun source(snapshot:Snapshot?):List<UsageRow> = HubWorkTrace.trace(HubWorkStage.HOME_SOURCE,
        units=(snapshot?.periods?.get("month")?.sessions?.size ?: 0).toLong()+(snapshot?.periods?.get("today")?.sessions?.size ?: 0)) {
        val selected=linkedMapOf<String,UsageRow>()
        for(period in listOf("month","today")) for(row in snapshot?.periods?.get(period)?.sessions.orEmpty()) {
            if(row.id in selected || row.session?.kind=="background-review" || timestamp(row.lastUsed ?: row.session?.startedAt)==null) continue
            selected[row.id]=row
        }
        selected.values.sortedByDescending {timestamp(it.lastUsed ?: it.session?.startedAt)}
    }
    fun recent(snapshot:Snapshot?,now:Long,cap:Int=5)=HubWorkTrace.trace(HubWorkStage.HOME_RECENT,
        units=(snapshot?.periods?.get("month")?.sessions?.size ?: 0).toLong()+(snapshot?.periods?.get("today")?.sessions?.size ?: 0)) {
        source(snapshot).filterIndexed {index,row->index<cap || activity(row,now)=="running"}
    }
    fun activity(row:UsageRow,now:Long):String {
        if(row.session?.archived==true) return "idle"
        val last=timestamp(row.lastUsed)?.takeIf {it!=0L} ?: return "idle"
        val recent=now.toDouble()-last.toDouble()<=runningWindow
        return if(!recent) "idle" else if(row.turnEnded==true) "ended" else "running"
    }
    fun context(row:UsageRow,now:Long):SessionContext? {
        if(activity(row,now)=="idle") return null
        val tokens=row.session?.contextTokens?.takeIf {it.isFinite() && it>0} ?: return null
        val window=row.session.contextWindow?.takeIf {it.isFinite() && it>0} ?: return null
        val left=floor(((window-tokens)/window*100).coerceAtLeast(0.0)+0.5).toInt()
        return SessionContext(tokens,window,left,100-left,when {left<=10->"low";left<=30->"caution";else->""})
    }
    fun cache(row:UsageRow,now:Long):SessionCache? {
        if(row.session?.archived==true || row.tool?.trim()?.lowercase(java.util.Locale.ROOT) !in listOf("claude","codex")) return null
        val raw=row.session?.promptCache ?: return null
        val at=timestamp(raw.observedAt)?.takeIf {it!=0L && it<=now} ?: return null
        if(raw.ttlSeconds !in listOf(300,1800,3600)) return null
        val expires=at+raw.ttlSeconds*1000L
        if(expires<=now) return null
        return SessionCache(expires,raw.ttlSeconds,ceil((expires-now)/60000.0).toInt())
    }
    fun nextStatusAt(rows:List<UsageRow>,now:Long):Long?=buildList {
        rows.forEach {row->
            cache(row,now)?.let {add(it.expiresAt-(it.minutes-1)*60_000L)}
            if(row.session?.archived!=true) timestamp(row.lastUsed)?.takeIf {it!=0L}?.let {last->
                (last+runningWindow+1).takeIf {it>now}?.let(::add)
            }
        }
    }.minOrNull()
    fun title(row:UsageRow)=row.session?.title ?: row.session?.projectLabel ?: row.session?.sessionId?.take(12) ?: "—"
    fun models(row:UsageRow)=row.session?.models.orEmpty().filterValues {it>0}
    fun displayModels(row:UsageRow,names:ModelNames?=null,observed:Collection<String> = models(row).keys)=names?.project(models(row),observed) ?: models(row).mapValues {BigInteger.valueOf(it.value)}
    fun modelShares(row:UsageRow,names:ModelNames?=null,observed:Collection<String> = models(row).keys):List<SessionModelShare> {
        val models=displayModels(row,names,observed);if(models.size<2) return emptyList()
        val attributed=models.values.fold(BigInteger.ZERO) {a,b->a+b}
        val total=BigInteger.valueOf((row.tokens ?: 0).coerceAtLeast(0));val denominator=maxOf(total,attributed)
        fun share(value:BigInteger):String {
            val percent=if(denominator.signum()>0) value.toDouble()/denominator.toDouble()*100 else 0.0
            return if(percent>0 && percent<1) "<1%" else "${floor(percent.coerceAtMost(100.0)+0.5).toInt()}%"
        }
        val collator=java.text.Collator.getInstance(java.util.Locale.ENGLISH)
        val named=models.filterKeys {it.isNotEmpty()}.toList().sortedWith {a,b->b.second.compareTo(a.second).takeIf {it!=0} ?: collator.compare(a.first,b.first)}
        val rows=named.map {(name,value)->SessionModelShare(name,value,share(value))}.toMutableList()
        val remainder=attributed-named.fold(BigInteger.ZERO) {a,b->a+b.second}+(total-attributed).coerceAtLeast(BigInteger.ZERO)
        if(remainder.signum()>0) rows+=SessionModelShare("",remainder,share(remainder))
        return rows
    }
    fun age(row:UsageRow,now:Long):Pair<String,Long>? {
        val at=timestamp(row.lastUsed ?: row.session?.startedAt)?.takeIf {it>0} ?: return null
        val minutes=floor((now.toDouble()-at).coerceAtLeast(0.0)/60000+0.5).toLong()
        if(minutes<1) return "now" to 0
        if(minutes<60) return "minutes" to minutes
        val hours=floor(minutes/60.0+0.5).toLong()
        return if(hours<24) "hours" to hours else "days" to floor(hours/24.0+0.5).toLong()
    }
}
