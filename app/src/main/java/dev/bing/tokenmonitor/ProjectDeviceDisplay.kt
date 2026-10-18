package dev.bing.tokenmonitor

import java.math.BigDecimal
import java.math.BigInteger
import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import kotlin.math.floor

/** Aggregate fields only. Native/local project telemetry is outside the Hub viewer. */
data class ProjectMetadata(val label:String?,val tokens:Long?,val clients:Map<String,Long>,val models:Map<String,Long> = emptyMap())
internal data class AttributionRow(val id:String,val label:String,val tokens:BigInteger,val percent:Double,val models:List<Pair<String,Long>> = emptyList(),val sourceClient:String=id)
internal data class ProjectRow(val id:String,val name:String,val tokens:BigInteger,val cost:String,val unpriced:BigInteger,val clients:List<AttributionRow>,val models:Map<String,BigInteger> = emptyMap())
internal data class GradientStop(val position:Float,val client:String)
internal data class HomeDeviceRow(val id:String,val name:String,val tokens:Long,val platform:String,val stale:Boolean,val local:Boolean=false)

internal object ProjectDisplay {
    fun normalized(value:String)=Normalizer.normalize(value.trim {it.isWhitespace() || it=='\uFEFF'},Normalizer.Form.NFC)
    fun key(value:String)=Normalizer.normalize(normalized(value).lowercase(Locale.ROOT),Normalizer.Form.NFC)
    private data class Sum(val id:String,var name:String,var tokens:BigInteger=BigInteger.ZERO,var cost:BigDecimal=BigDecimal.ZERO,var unpriced:BigInteger=BigInteger.ZERO,val clients:MutableMap<String,BigInteger> = linkedMapOf(),val models:MutableMap<String,BigInteger> = linkedMapOf())
    fun rows(period:Period?,label:(String)->String={it},unknown:String="Unknown tool"):List<ProjectRow> = HubWorkTrace.trace(HubWorkStage.PROJECT_ROWS,
        units=(if(period?.projectsProvided==true || !period?.projects.isNullOrEmpty()) period?.projects?.size ?: 0 else period?.sessions?.size ?: 0).toLong()) {
        if(period==null) return@trace emptyList()
        val projects=linkedMapOf<String,Sum>()
        fun add(name:String,count:Long?,cost:String?,unpriced:Long?,clients:Map<String,Long>,models:Map<String,Long>) {
            val clean=normalized(name);val key=key(clean);if(key.isEmpty() || clean.isEmpty()) return
            val sum=projects.getOrPut(key) {Sum(key,clean)};sum.name=minOf(sum.name,clean)
            val value=(count ?: 0).coerceAtLeast(0);sum.tokens+=BigInteger.valueOf(value)
            sum.cost+=cost?.let {runCatching {BigDecimal(it)}.getOrNull()} ?: BigDecimal.ZERO
            sum.unpriced+=BigInteger.valueOf((unpriced ?: 0).coerceIn(0,value))
            // Bound each source's model attribution before merging projects. Missing shares stay unknown.
            var remaining=value
            // A sorted map would discard distinct IDs that collate equally (for example NFC/NFD).
            models.entries.sortedWith(compareBy(Collator.getInstance(Locale.ENGLISH)) {it.key}).forEach {(model,count)->
                val tokens=count.coerceIn(0,remaining)
                if(model.isNotEmpty() && tokens>0) {sum.models[model]=(sum.models[model] ?: BigInteger.ZERO)+BigInteger.valueOf(tokens);remaining-=tokens}
            }
            clients.forEach {(client,tokens)->if(tokens>0) sum.clients[client]=(sum.clients[client] ?: BigInteger.ZERO)+BigInteger.valueOf(tokens)}
        }
        if(period.projectsProvided || period.projects.isNotEmpty()) {
            period.projects.forEach {row->
                val meta=row.project;val raw=meta?.label?.takeIf {it.isNotEmpty()} ?: if(meta!=null) row.id else row.label
                add(raw,if(meta!=null) meta.tokens else row.tokens,row.cost,row.unpriced,meta?.clients ?: row.tool?.let {mapOf(it to (row.tokens ?: 0))}.orEmpty(),meta?.models.orEmpty())
            }
        } else period.sessions.forEach {row->
            val name=row.session?.projectLabel ?: return@forEach
            add(name,row.tokens,row.cost,row.unpriced,row.tool?.takeIf(String::isNotEmpty)?.let {mapOf(it to (row.tokens ?: 0))}.orEmpty(),if(period.projectsIncomplete) emptyMap() else row.session?.models.orEmpty())
        }
        val collator=Collator.getInstance(Locale.ENGLISH)
        projects.values.map {sum->
            val attributed=sum.clients.values.fold(BigInteger.ZERO,BigInteger::add)
            if(sum.tokens>attributed) sum.clients[""]=sum.tokens-attributed
            val clients=sum.clients.filterValues {it.signum()>0}.map {(client,tokens)->AttributionRow(client.ifEmpty {"unknown"},if(client.isEmpty()) unknown else label(client),tokens,if(sum.tokens.signum()>0) tokens.toDouble()/sum.tokens.toDouble()*100 else 0.0,sourceClient=client)}
                .sortedWith {a,b->b.tokens.compareTo(a.tokens).takeIf {it!=0} ?: collator.compare(a.label,b.label)}
            ProjectRow(sum.id,sum.name,sum.tokens,sum.cost.toPlainString(),sum.unpriced,clients,sum.models.toMap())
        }.sortedWith {a,b->BigDecimal(b.cost).compareTo(BigDecimal(a.cost)).takeIf {it!=0} ?: b.tokens.compareTo(a.tokens).takeIf {it!=0} ?: collator.compare(a.name,b.name)}
    }
    fun modelShares(row:ProjectRow,names:ModelNames?=null,observed:Collection<String> = row.models.keys,unknown:String="Unknown model"):List<AttributionRow> {
        // Resolve aliases only after source attribution is bounded; exact sums may exceed Long.
        val labels=names?.labels(row.models.keys,observed).orEmpty()
        val grouped=linkedMapOf<String,BigInteger>()
        row.models.forEach {(id,tokens)->val label=labels[id] ?: id;grouped[label]=(grouped[label] ?: BigInteger.ZERO)+tokens}
        val remaining=row.tokens-grouped.values.fold(BigInteger.ZERO,BigInteger::add)
        if(remaining.signum()>0) grouped[""]=remaining
        val collator=Collator.getInstance(Locale.ENGLISH)
        return grouped.filterValues {it.signum()>0}.map {(id,tokens)->
            AttributionRow(id.ifEmpty {"unknown"},id.ifEmpty {unknown},tokens,if(row.tokens.signum()>0) tokens.toDouble()/row.tokens.toDouble()*100 else 0.0,sourceClient=id)
        }.sortedWith {a,b->b.tokens.compareTo(a.tokens).takeIf {it!=0} ?: collator.compare(a.label,b.label)}
    }
    /** The source gradient sorts by client key, separately from translated accordion names. */
    fun gradient(rows:List<AttributionRow>):List<GradientStop> {
        val collator=Collator.getInstance(Locale.ENGLISH)
        val entries=rows.filter {it.tokens.signum()>0}.sortedWith {a,b->b.tokens.compareTo(a.tokens).takeIf {it!=0} ?: collator.compare(a.sourceClient,b.sourceClient)}
        if(entries.isEmpty()) return emptyList()
        val total=entries.fold(BigInteger.ZERO) {a,b->a+b.tokens};val stops=mutableListOf(GradientStop(0f,entries.first().sourceClient));var cumulative=0.0
        fun rounded(value:Double)=BigDecimal(value).setScale(2,java.math.RoundingMode.HALF_UP).toFloat()/100f
        for(index in 0 until entries.lastIndex) {
            val share=entries[index].tokens.toDouble()/total.toDouble()*100;val next=entries[index+1].tokens.toDouble()/total.toDouble()*100
            cumulative+=share;val blend=minOf(1.5,share/2,next/2)
            stops+=GradientStop(rounded((cumulative-blend).coerceAtLeast(0.0)),entries[index].sourceClient)
            stops+=GradientStop(rounded((cumulative+blend).coerceAtMost(100.0)),entries[index+1].sourceClient)
        }
        stops+=GradientStop(1f,entries.last().sourceClient);return stops
    }
    fun stableIndex(value:String):Int {
        var hash=0;value.codePoints().forEach {point->hash=hash*31+(if(point>0xffff) Character.highSurrogate(point).code else point)}
        return (kotlin.math.abs(hash.toLong())%6).toInt()
    }
}

internal object DeviceDisplay {
    val accent=0xFF73BDF5L;val staleColor=0xFF8C97A7L
    fun name(device:Device,alias:String?=null,fallback:String="device"):String =
        listOf(alias,device.hubDisplayName,device.id,device.name.takeUnless {it=="__unnamed_device__"}).firstNotNullOfOrNull {it?.trim()?.takeIf(String::isNotEmpty)} ?: fallback
    fun platform(device:Device):String {
        val raw=device.rawPlatform ?: device.platform.orEmpty();val prefix=raw.lowercase(Locale.ROOT).substringBefore('-')
        val fallback=when(prefix) {"darwin"->"macOS";"win32"->"Windows";"linux"->"Linux";else->raw}
        return listOf(device.osName?.trim()?.takeIf(String::isNotEmpty) ?: fallback,device.osVersion?.trim().orEmpty()).filter(String::isNotEmpty).joinToString(" ")
    }
    fun icon(platform:String)=when(platform.lowercase(Locale.ROOT).substringBefore('-')) {"darwin"->"apple";"win32"->"windows";"linux","freebsd","openbsd"->"linux";else->null}
    fun rows(devices:List<Device>,period:String)=HubWorkTrace.trace(HubWorkStage.DEVICE_ROWS,units=devices.size.toLong()) {
        devices.sortedByDescending {(it.periods[period]?.tokens ?: 0).coerceAtLeast(0)}
    }
    fun tools(device:Device,period:String,label:(String)->String={it},unknown:String="Unclassified"):List<AttributionRow> {
        val data=device.periods[period];val total=BigInteger.valueOf((data?.tokens ?: 0).coerceAtLeast(0));val counts=data?.tools.orEmpty().filterValues {it>0}.mapValues {BigInteger.valueOf(it.value)}.toMutableMap()
        val attributed=counts.values.fold(BigInteger.ZERO,BigInteger::add);if(total>attributed) counts[UsageAttribution.UNATTRIBUTED]=total-attributed
        val collator=Collator.getInstance(Locale.ENGLISH)
        return counts.map {(id,value)->
            val models=data?.clientModels?.get(id).orEmpty().filterValues {it>0}.toList().sortedWith {a,b->b.second.compareTo(a.second).takeIf {it!=0} ?: collator.compare(a.first,b.first)}
            AttributionRow(id,if(id==UsageAttribution.UNATTRIBUTED) unknown else label(id),value,if(total.signum()>0) value.toDouble()/total.toDouble()*100 else 0.0,models)
        }.sortedWith {a,b->b.tokens.compareTo(a.tokens).takeIf {it!=0} ?: collator.compare(a.label,b.label)}
    }
    fun home(devices:List<Device>,period:String,aliases:Map<String,String> = emptyMap(),localId:String="",cap:Int=4):List<HomeDeviceRow> = devices.mapIndexedNotNull {index,device->
        val value=(device.periods[period]?.tokens ?: 0).coerceAtLeast(0);if(value<=0) null else {
            val id=device.id.trim().ifEmpty {index.toString()};val name=name(device,aliases[device.id])
            HomeDeviceRow(id,name,value,device.rawPlatform ?: device.platform.orEmpty(),device.stale==true,localId.trim().isNotEmpty() && id==localId.trim()) to index
        }
    }.sortedWith {a,b->b.first.tokens.compareTo(a.first.tokens).takeIf {it!=0} ?: b.first.local.compareTo(a.first.local).takeIf {it!=0} ?: a.first.stale.compareTo(b.first.stale).takeIf {it!=0} ?: a.second.compareTo(b.second)}.take(cap.coerceAtLeast(0)).map {it.first}
    fun age(value:String?,now:Long):Pair<String,Long>? {
        val at=SessionDisplay.timestamp(value) ?: return null;val diff=(now.toDouble()-at).coerceAtLeast(0.0)
        if(diff<45000) return "now" to 0
        val minutes=floor(diff/60000+0.5).toLong();if(minutes<60) return "minutes" to minutes
        val hours=floor(minutes/60.0+0.5).toLong();return if(hours<24) "hours" to hours else "days" to floor(hours/24.0+0.5).toLong()
    }
    fun nextAgeAt(value:String?,now:Long):Long? {
        val at=SessionDisplay.timestamp(value) ?: return null
        val (unit,count)=age(value,now) ?: return null
        val delta=when(unit) {
            "now"->45_000.0
            "minutes"->(count+0.5)*60_000
            "hours"->((count+0.5)*60-0.5)*60_000
            else->(((count+0.5)*24-0.5)*60-0.5)*60_000
        }
        return (at+delta.toLong()).takeIf {it>now}
    }
}
