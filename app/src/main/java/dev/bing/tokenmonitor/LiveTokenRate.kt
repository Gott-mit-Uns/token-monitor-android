package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import kotlin.math.min

data class TimedUsage(val timedTokens:Double,val timedOutputTokens:Double,val timedDurationMs:Double)
data class Throughput(val counters:TimedUsage,val models:Map<String,TimedUsage>?=null)
internal data class RateModel(val model:String,val speed:Double,val burn:Double)
internal data class DeviceRate(val id:String,val name:String,val models:List<RateModel>)
internal data class RateSample(val speed:Double,val burn:Double,val sampledAt:Long,val revision:Long,val devices:List<DeviceRate>,val deviceCount:Int,val expiresAt:Long,val idle:Boolean)
internal data class RateEntry(val id:String,val name:String,val throughput:Throughput?)

internal object ThroughputParser {
    fun parse(period:JsonObject):Throughput? {
        fun counters(value:JsonObject):TimedUsage? {
            if((value["capabilities"] as? JsonObject)?.get("throughput")?.let {(it as? JsonPrimitive)?.booleanOrNull}==false) return null
            fun value(key:String)=(value[key] as? JsonPrimitive)?.contentOrNull?.takeIf {it.length<=128}?.toDoubleOrNull()?.takeIf {it.isFinite() && it>=0}
            return TimedUsage(value("timedTokens") ?: return null,value("timedOutputTokens") ?: return null,value("timedDurationMs") ?: return null)
        }
        return Throughput(counters(period) ?: return null,(period["modelThroughput"] as? JsonObject)?.mapNotNull {(name,row)->(row as? JsonObject)?.let(::counters)?.let {name to it}}?.toMap())
    }
}

/** Original matched performance-counter deltas, independently tracked per Hub device. */
internal class LiveTokenRateTracker(private val now:()->Long) {
    private data class Sample(val speed:Double,val burn:Double,val sampledAt:Long,val revision:Long,val models:List<RateModel>,val expiresAt:Long)
    private class Tracker(private val now:()->Long) {
        private data class Part(val counters:TimedUsage,val sampledAt:Long,val independent:Boolean=false)
        private var baseline:Throughput?=null
        private var revision=0L
        private var modelSamples=linkedMapOf<String,Part>()
        private var unattributed:Part?=null
        var sample:Sample?=null;private set
        private fun clear() {modelSamples.clear();unattributed=null;sample=null}
        fun reset(value:Throughput?) {baseline=value;clear()}
        fun at(timestamp:Long):Sample? {
            val models=modelSamples.filterValues {timestamp<it.sampledAt+8000}
            val parts=models.values.toList()+listOfNotNull(unattributed?.takeIf {timestamp<it.sampledAt+8000})
            if(parts.isEmpty()) return null
            val counters=parts.fold(TimedUsage(0.0,0.0,0.0)) {sum,part->plus(sum,part.counters)}
            return Sample(speed(counters),burn(counters),parts.maxOf {it.sampledAt},revision,
                models.map {(name,part)->RateModel(name,speed(part.counters),burn(part.counters))},parts.minOf {it.sampledAt+8000})
        }
        fun observe(current:Throughput?):Sample? {
            val previous=baseline;baseline=current
            if(previous==null || current==null) {clear();return null}
            val change=delta(current.counters,previous.counters)
            if(negative(change)) {clear();return null}
            if(change.timedDurationMs<=0) return sample
            val timestamp=now();val next=linkedMapOf<String,Part>();var attributed=TimedUsage(0.0,0.0,0.0)
            val models=current.models;val old=previous.models
            var consistent=models!=null && old!=null && old.all {(name,counters)->models[name]?.let { !negative(delta(it,counters)) }==true}
            if(consistent) for((name,counters) in models.orEmpty()) {
                val difference=delta(counters,old?.get(name) ?: TimedUsage(0.0,0.0,0.0))
                if(negative(difference) || negative(delta(change,difference))) {consistent=false;break}
                if(difference.timedDurationMs>0) {attributed=plus(attributed,difference);next[name]=Part(difference,timestamp)}
                else if(difference.timedTokens==0.0 && difference.timedOutputTokens==0.0) modelSamples[name]?.takeIf {timestamp<it.sampledAt+8000}?.let {next[name]=it}
                else {consistent=false;break}
            }
            val remainder=delta(change,attributed)
            if(consistent && !negative(remainder) && (remainder.timedDurationMs>0 || (remainder.timedTokens==0.0 && remainder.timedOutputTokens==0.0))) {
                modelSamples=next
                if(remainder.timedDurationMs>0) unattributed=Part(remainder,timestamp,true)
                else if(unattributed?.independent!=true || timestamp>=unattributed!!.sampledAt+8000) unattributed=null
            } else {modelSamples.clear();unattributed=Part(change,timestamp)}
            revision++;sample=at(timestamp);return sample
        }
    }
    private data class Device(val tracker:Tracker,var name:String)
    private val devices=linkedMapOf<String,Device>()
    private var revision=0L
    private var retained:RateSample?=null
    private fun normalized(entries:List<RateEntry>)=entries.map {it.copy(id=it.id.trim(),name=it.name.ifEmpty {it.id.trim()})}.filter {it.id.isNotEmpty()}.distinctBy {it.id}
    fun reset(entries:List<RateEntry> = emptyList()) {devices.clear();retained=null;normalized(entries).forEach {entry->devices[entry.id]=Device(Tracker(now).also {it.reset(entry.throughput)},entry.name)}}
    fun observe(entries:List<RateEntry>):RateSample? {
        val rows=normalized(entries);val present=rows.map {it.id}.toSet()
        devices.keys.toList().filter {it !in present}.forEach(devices::remove)
        var fresh=false;var invalidated=false
        rows.forEach {entry->
            val device=devices[entry.id]
            if(device==null) devices[entry.id]=Device(Tracker(now).also {it.reset(entry.throughput)},entry.name)
            else {
                device.name=entry.name
                val previous=device.tracker.sample;val sample=device.tracker.observe(entry.throughput)
                if(sample!==previous) {if(sample!=null) fresh=true else if(previous!=null) invalidated=true}
            }
        }
        if(invalidated) retained=null
        if(fresh) revision++
        return sample()
    }
    fun sample():RateSample? {
        val timestamp=now()
        val active=devices.entries.mapNotNull {(id,device)->device.tracker.at(timestamp)?.let {Triple(id,device,it)}}
        if(active.isNotEmpty()) {
            val time=active.maxOf {it.third.sampledAt}
            val sample=RateSample(cap(active.sumOf {it.third.speed}),cap(active.sumOf {it.third.burn}),time,revision,
                active.filter {it.third.models.isNotEmpty()}.map {DeviceRate(it.first,it.second.name,it.third.models)},active.size,time+180000,false)
            retained=sample
            return sample.copy(expiresAt=active.minOf {it.third.expiresAt})
        }
        val previous=retained ?: return null
        return previous.takeIf {timestamp<it.expiresAt}?.copy(idle=true)
    }
    companion object {
        private fun delta(current:TimedUsage,previous:TimedUsage)=TimedUsage(current.timedTokens-previous.timedTokens,current.timedOutputTokens-previous.timedOutputTokens,current.timedDurationMs-previous.timedDurationMs)
        private fun plus(a:TimedUsage,b:TimedUsage)=TimedUsage(a.timedTokens+b.timedTokens,a.timedOutputTokens+b.timedOutputTokens,a.timedDurationMs+b.timedDurationMs)
        private fun negative(value:TimedUsage)=value.timedTokens<0 || value.timedOutputTokens<0 || value.timedDurationMs<0
        private fun cap(value:Double)=if(value.isNaN() || value<=0) 0.0 else min(1e12,value)
        private fun speed(value:TimedUsage)=if(value.timedDurationMs<=0 || value.timedOutputTokens<=0) 0.0 else cap(value.timedOutputTokens*1000/value.timedDurationMs)
        private fun burn(value:TimedUsage)=if(value.timedDurationMs<=0 || value.timedTokens<=0) 0.0 else cap(value.timedTokens*60000/value.timedDurationMs)
        fun entries(snapshot:Snapshot?,deviceId:String="",aliases:Map<String,String> = emptyMap()):List<RateEntry> = snapshot?.devices.orEmpty().filter {device->
            if(deviceId.isEmpty()) device.stale!=true && device.periodDetails["today"]!=null else device.id==deviceId && device.periodDetails["today"]!=null
        }.map {RateEntry("device:"+it.id.ifEmpty {"unknown"},DeviceDisplay.name(it,aliases[it.id]),it.periodDetails["today"]?.throughput)}
    }
}
