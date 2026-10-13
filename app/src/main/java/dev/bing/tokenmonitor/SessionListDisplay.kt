package dev.bing.tokenmonitor

import java.math.BigDecimal
import java.math.BigInteger
import java.text.Collator
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.floor

internal data class SessionListRow(val id:String,val source:UsageRow?,val name:String,val subtitle:String,val activity:String,
    val detail:String,val tokens:BigInteger,val cost:String,val unpriced:Long?,val sortTime:Long,val review:Boolean=false,
    val reviews:List<SessionListRow> = emptyList(),val groupKind:String?=null,val groupClient:String?=null,
    val groupModels:Map<String,BigInteger> = emptyMap(),val groupState:String?=null,val groupRole:String?=null,
    val unpricedTotal:BigInteger?=unpriced?.let(BigInteger::valueOf))
internal data class SessionListPage(val rows:List<SessionListRow>,val page:Int,val pageCount:Int,val start:Int,val end:Int,val total:Int) {
    val paginated get()=total>100
}

/** Pure display projections of immutable sessionRows.js and breakdownRenderPolicy.js. */
internal object SessionListDisplay {
    private val numbers=NumberFormat.getIntegerInstance(Locale.US)
    private val uuidPattern=Regex("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}",RegexOption.IGNORE_CASE)
    private val rolloutPattern=Regex("^rollout-\\d{4}-\\d{2}-\\d{2}T\\d{2}[:-]\\d{2}[:-]\\d{2}-(.+)$")
    private val datePattern=Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}[:-]\\d{2}")
    private fun integer(value:Double)=numbers.format(floor(value+0.5))
    fun time(value:Long,now:Long,zone:ZoneId=ZoneId.systemDefault()):String {
        if(value==0L) return ""
        val date=Instant.ofEpochMilli(value).atZone(zone);val today=Instant.ofEpochMilli(now).atZone(zone)
        val clock="%02d:%02d".format(Locale.ROOT,date.hour,date.minute)
        return if(date.toLocalDate()==today.toLocalDate()) clock else "%02d/%02d ".format(Locale.ROOT,date.monthValue,date.dayOfMonth)+clock
    }
    fun idLabel(value:String):String {
        val raw=value.trim();if(raw.isEmpty()) return ""
        val lowered=raw.lowercase(Locale.ROOT)
        if(lowered.startsWith("reasonix:")) return raw.substring(9).takeUnless {it.lowercase(Locale.ROOT).startsWith("reasonix-stats:")}.orEmpty()
        if(lowered.startsWith("reasonix-stats:")) return ""
        val uuids=uuidPattern.findAll(raw).map {it.value}.toList()
        if(uuids.size>1) return uuids.joinToString(" · ")
        val rollout=rolloutPattern.matchEntire(raw)
        if(rollout!=null) return uuids.firstOrNull() ?: rollout.groupValues[1]
        if(datePattern.containsMatchIn(raw)) return ""
        return raw
    }
    private fun synthetic(row:UsageRow):Boolean {
        val client=row.tool.orEmpty().trim().lowercase(Locale.ROOT)
        val id=row.session?.sessionId.orEmpty().trim().lowercase(Locale.ROOT);val key=row.id.trim().lowercase(Locale.ROOT)
        return row.session?.excludedFromHubList==true || client in listOf("reasonix","reasonix-stats") || id.startsWith("reasonix:") || id.startsWith("reasonix-stats:") || key.startsWith("reasonix:") || key.contains("reasonix-stats:")
    }
    fun modelLabel(row:UsageRow,names:ModelNames?=null,observed:Collection<String> = SessionDisplay.models(row).keys)=SessionDisplay.displayModels(row,names,observed).keys.let {when(it.size) {0->"";1->it.single();else->"${it.size} models"}}
    fun cachePercent(row:UsageRow):Double? {
        val a=row.session?.accounting ?: return null
        val read=a.cacheRead.coerceAtLeast(0.0);val write=a.cacheWrite.coerceAtLeast(0.0)
        if(read<=0 && write<=0) return null
        return read/(a.input.coerceAtLeast(0.0)+read+write)*100
    }
    fun rate(row:UsageRow):Double {
        val a=row.session?.accounting ?: return 0.0
        val output=minOf(a.output.coerceAtLeast(0.0),a.timedOutput.coerceAtLeast(0.0))
        return if(a.timedDurationMs>0 && output>0) output*1000/a.timedDurationMs else 0.0
    }
    fun raw(rows:List<UsageRow>,now:Long,hideTitles:Boolean=false,archivedLabel:String="Archived",label:(String)->String={it},zone:ZoneId=ZoneId.systemDefault(),names:ModelNames?=null,observed:Collection<String> = rows.flatMap {SessionDisplay.models(it).keys}):List<SessionListRow> = HubWorkTrace.trace(HubWorkStage.SESSION_RAW,units=rows.size.toLong()) {
        rows.filter {(it.tokens ?: 0)>0 && !synthetic(it)}.map {row->
            val model=modelLabel(row,names,observed);val client=row.tool.orEmpty().trim();val clientLabel=label(client).ifEmpty {"Session"}
            val title=row.session?.title?.takeUnless {hideTitles}.orEmpty();val parts=listOf(clientLabel,model).filter(String::isNotEmpty).joinToString(" · ")
            val at=SessionDisplay.timestamp(row.lastUsed?.takeIf {it.isNotEmpty()} ?: row.session?.startedAt) ?: 0L
            val accounting=row.session?.accounting ?: SessionAccounting()
            val cache=cachePercent(row)?.let {if(it>0 && it<1) "<1%" else "${floor(it.coerceAtMost(100.0)+0.5).toInt()}%"}.orEmpty()
            val rate=floor(rate(row)+0.5).takeIf {it>0}?.let {"${integer(it)} tok/s"}.orEmpty()
            val activity=listOf(if(row.session?.archived==true) archivedLabel else "",time(at,now,zone),
                if(accounting.calls>0) "${integer(accounting.calls)} ${if(accounting.calls==1.0) "call" else "calls"}" else "",cache,rate).filter(String::isNotEmpty).joinToString(" · ")
            SessionListRow(row.id,row,title.ifEmpty {parts},if(title.isEmpty()) activity else parts,if(title.isEmpty()) "" else activity,
                idLabel(row.session?.sessionId ?: row.id),BigInteger.valueOf(row.tokens!!),row.cost?.let {runCatching {BigDecimal(it)}.getOrNull()}?.toPlainString() ?: "0",row.unpriced,at)
        }.let(::sorted)
    }
    /** Full display rows and all summaries share the desktop's mixed-list ordering. */
    fun sorted(rows:List<SessionListRow>):List<SessionListRow> {
        val collator=Collator.getInstance(Locale.ENGLISH)
        val order=Comparator<SessionListRow> {a,b->
            b.sortTime.compareTo(a.sortTime).takeIf {it!=0} ?: b.tokens.compareTo(a.tokens).takeIf {it!=0}
            ?: BigDecimal(b.cost).compareTo(BigDecimal(a.cost)).takeIf {it!=0} ?: collator.compare(a.name,b.name)
        }
        // Raw rows already carry this order. Grouping must not sort/copy that whole list again.
        for(index in 1 until rows.size) if(order.compare(rows[index-1],rows[index])>0) return rows.sortedWith(order)
        return rows
    }
    fun grouped(rows:List<SessionListRow>,label:String="Codex Auto Review",count:(Int)->String={"$it sessions"},latest:(String)->String={"Latest $it"},compact:(BigInteger)->String={it.toString()},now:Long,zone:ZoneId=ZoneId.systemDefault(),grokLabel:String="Grok Bot",subagentCount:(Int)->String={"$it subagents"}):List<SessionListRow> = HubWorkTrace.trace(HubWorkStage.SESSION_GROUP,units=rows.size.toLong()) {
        val reviews=rows.filter {it.source?.session?.kind?.trim()=="background-review"}.sortedByDescending {it.sortTime}
        if(reviews.isEmpty()) return@trace SessionRelationshipGroups.group(rows,grokLabel,subagentCount,now)
        val tokens=reviews.fold(BigInteger.ZERO) {sum,row->sum+row.tokens};val cost=reviews.fold(BigDecimal.ZERO) {sum,row->sum+BigDecimal(row.cost)}
        val first=reviews.first();val clock=time(first.sortTime,now,zone)
        val summary=SessionListRow("session-group:codex-auto-review",null,label,listOf(if(clock.isNotEmpty()) latest(clock) else "",if(first.tokens.signum()>0) compact(first.tokens) else "").filter(String::isNotEmpty).joinToString(" · "),"",count(reviews.size),tokens,cost.toPlainString(),null,first.sortTime,true,reviews)
        sorted(SessionRelationshipGroups.group(rows.filter {it.source?.session?.kind?.trim()!="background-review"},grokLabel,subagentCount,now)+summary.copy(groupKind="review",groupClient="codex",unpricedTotal=reviews.fold(BigInteger.ZERO) {sum,row->sum+(row.unpricedTotal ?: BigInteger.ZERO)}))
    }
    fun page(rows:List<SessionListRow>,requested:Int):SessionListPage = HubWorkTrace.trace(HubWorkStage.SESSION_PAGE,units=rows.size.toLong()) {
        val count=((rows.size+99)/100).coerceAtLeast(1);val page=requested.coerceIn(0,count-1);val start=page*100
        SessionListPage(rows.subList(start,minOf(rows.size,start+100)).toList(),page,count,if(rows.isEmpty()) 0 else start+1,minOf(rows.size,start+100),rows.size)
    }
    fun bar(value:BigInteger,max:BigInteger):Float=if(value.signum()<=0 || max.signum()<=0) 0f else (value.toDouble()/max.toDouble()).toFloat().coerceIn(.02f,1f)
}
