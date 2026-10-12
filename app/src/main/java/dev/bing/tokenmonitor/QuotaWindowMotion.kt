package dev.bing.tokenmonitor

import androidx.compose.animation.core.CubicBezierEasing
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs

/** Display-only reset identity/state; no provider record or action survives projection. */
internal data class QuotaWindowTarget(val key:String,val remainingPercent:Double?,val displayPercent:Double?,
    val resetsAt:String?,val percentSuffix:String?=null)
internal data class QuotaMeterFrame(val fill:Float,val percent:Double,val glow:Float=0f)
internal data class QuotaWindowMotionFrame(val meters:Map<String,QuotaMeterFrame> = emptyMap(),
    val entryFraction:Float?=null,val running:Boolean=false) {
    fun fill(target:QuotaWindowTarget):Float = entryFraction?.let {((target.displayPercent ?: 0.0)/100*it).toFloat()}
        ?: meters[target.key]?.fill ?: ((target.displayPercent ?: 0.0)/100).toFloat()
}

/** Exact pinned limits/resetMotion.js identity and threshold policy. */
internal object QuotaResetPolicy {
    private fun JsonObject.clean(key:String)=(get(key) as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
    private fun normalized(value:String)=value.trim().lowercase(Locale.ROOT)
    private fun opaque(parts:List<String>):String {
        var hash=0x811c9dc5.toInt()
        parts.joinToString("\u0000") {it.trim()}.forEach {hash=(hash xor it.code)*0x01000193}
        return Integer.toUnsignedString(hash,36)
    }
    fun providerKey(provider:JsonObject):String {
        val identity=provider.clean("accountKey").ifEmpty {provider.clean("webAccountKey")}
            .ifEmpty {normalized(provider.clean("accountEmail"))}.ifEmpty {provider.clean("accountName")}
            .ifEmpty {provider.clean("accountLabel")}.ifEmpty {provider.clean("profileId")}.ifEmpty {"default"}
        return opaque(listOf(normalized(provider.clean("provider")),identity))
    }
    fun windowKey(label:String,window:JsonObject):String {
        val id=listOf("limitId","id","quotaId","model","group").firstNotNullOfOrNull {window.clean(it).takeIf(String::isNotEmpty)}.orEmpty()
        return opaque(listOf(normalized(window.clean("kind")),id,normalized(window.clean("label").ifEmpty {label}),
            if((window["additional"] as? JsonPrimitive)?.booleanOrNull==true) "additional" else "canonical"))
    }
    fun finitePercent(value:Double?)=value?.takeIf(Double::isFinite)?.coerceIn(0.0,100.0)
    private fun JsonObject.percent(key:String)=clean(key).takeIf(String::isNotEmpty)?.toDoubleOrNull()?.let(::finitePercent)
    fun remaining(window:JsonObject)=window.percent("remainingPercent") ?: window.percent("usedPercent")?.let {100-it}
    fun target(provider:JsonObject,label:String,window:JsonObject,display:Double?,percentSuffix:String?):QuotaWindowTarget =
        QuotaWindowTarget(providerKey(provider)+":"+windowKey(label,window),remaining(window),finitePercent(display),
            window.clean("resetsAt").takeIf(String::isNotEmpty),percentSuffix)
    private fun resetTime(value:String?)=value?.let {
        // Standard ISO boundaries use JS Date's UTC date-only / local no-offset behavior.
        runCatching {Instant.parse(it).toEpochMilli()}.getOrNull()
            ?: runCatching {LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()}.getOrNull()
            ?: runCatching {LocalDateTime.parse(it).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()}.getOrNull()
    }
    fun shouldReset(previous:QuotaWindowTarget,current:QuotaWindowTarget):Boolean {
        val from=finitePercent(previous.remainingPercent) ?: return false
        val to=finitePercent(current.remainingPercent) ?: return false
        if(from>=99.5 || to<99.5) return false
        val old=resetTime(previous.resetsAt);val next=resetTime(current.resetsAt)
        return old==null || next==null || next>old
    }
    fun duration(from:Double?,to:Double?):Long {
        val a=finitePercent(from);val b=finitePercent(to)
        return if(a==null || b==null) 1100 else Math.round(900+abs(b-a)*7)
    }
}

/** One finite surface controller. Entry animates bars only; reset also animates percentage/glow. */
internal class QuotaWindowMotion {
    private data class Reset(val from:Double,val to:Double,var duration:Long,var start:Long?=null)
    private var source:Long?=null
    private var main:String?=null
    private var lastShown:Boolean?=null
    private var entryPending=false
    private var entryStart:Long?=null
    private var token:Any?=null
    private var previous=emptyMap<String,QuotaWindowTarget?>()
    private var resets=emptyMap<String,Reset>()
    var frame=QuotaWindowMotionFrame();private set
    val running get()=frame.running
    val retainedMotionCount get()=resets.size

    fun render(targets:List<QuotaWindowTarget>?,connectionKey:Long,mainViewKey:String,renderToken:Any,
        now:Long,shown:Boolean,animate:Boolean,scale:Float=1f):Boolean {
        val sourceChanged=source!=null && source!=connectionKey
        if(sourceChanged) {settle();previous=emptyMap();entryPending=false;token=null}
        source=connectionKey
        if(main!=null && main!=mainViewKey) entryPending=true
        main=mainViewKey
        val restoring=lastShown==false && shown && !entryPending
        lastShown=shown
        if(targets==null) {
            val changed=running;settle()
            if(shown) {previous=emptyMap();entryPending=false;token=null}
            return changed
        }
        val current=linkedMapOf<String,QuotaWindowTarget?>()
        targets.forEach {current[it.key]=if(it.key in current) null else it}
        val entering=entryPending && shown
        if(shown) entryPending=false // Consume even an empty visible surface.
        val enabled=shown && animate && scale>0f && scale.isFinite()
        if(!enabled || restoring || sourceChanged) {
            val changed=running;settle();previous=current;token=renderToken;return changed
        }
        if(entering) {
            settle();previous=current;token=renderToken
            if(targets.any {it.displayPercent!=null && it.displayPercent>0}) entryStart=now
            publish(now,scale);return true
        }
        if(token===renderToken && previous==current) return false
        // A real DOM rebuild cancels an in-flight entry. Reset timelines are explicitly carried.
        entryStart=null
        publish(now,scale)
        val next=linkedMapOf<String,Reset>()
        current.forEach {(key,target)->
            val old=previous[key] ?: return@forEach
            val value=target ?: return@forEach // Duplicate identities stay static.
            val active=resets[key]
            if(active!=null && old.remainingPercent==value.remainingPercent && old.displayPercent==value.displayPercent && old.resetsAt==value.resetsAt) {
                next[key]=active;return@forEach
            }
            val from=QuotaResetPolicy.finitePercent(old.displayPercent)
            val to=QuotaResetPolicy.finitePercent(value.displayPercent)
            if(from!=null && to!=null && QuotaResetPolicy.shouldReset(old,value))
                next[key]=Reset(from,to,QuotaResetPolicy.duration(from,to))
        }
        val longest=next.values.filter {it.start==null}.maxOfOrNull {it.duration}
        if(longest!=null) next.values.filter {it.start==null}.forEach {it.duration=longest}
        resets=next;previous=current;token=renderToken
        publish(now,scale);return true
    }

    fun advance(now:Long,scale:Float=1f) {
        if(scale<=0f || !scale.isFinite()) {settle();return}
        // Desktop starts newly detected refills at the next render frame.
        resets.values.forEach {if(it.start==null) it.start=now}
        publish(now,scale)
    }
    private fun publish(now:Long,scale:Float) {
        if(scale<=0f || !scale.isFinite()) {settle();return}
        val entry=entryStart?.let {
            val p=((now-it).coerceAtLeast(0).toDouble()/scale/420_000_000.0).coerceIn(0.0,1.0).toFloat()
            if(p>=1f) {entryStart=null;null} else ENTRY_EASING.transform(p)
        }
        val next=linkedMapOf<String,Reset>();val meters=linkedMapOf<String,QuotaMeterFrame>()
        resets.forEach {(key,motion)->
            val start=motion.start
            if(start==null) {next[key]=motion;return@forEach}
            val elapsed=(now-start).coerceAtLeast(0).toDouble()/scale/1_000_000.0
            if(elapsed>=motion.duration+GLOW_MS-GLOW_LEAD_MS) return@forEach
            next[key]=motion
            val p=(elapsed/motion.duration).coerceIn(0.0,1.0)
            val bar=if(abs(motion.to-motion.from)<.1) motion.to
                else motion.from+(motion.to-motion.from)*RESET_EASING.transform(p.toFloat())
            val percent=motion.from+(motion.to-motion.from)*(1-(1-p)*(1-p))
            val glowTime=elapsed-(motion.duration-GLOW_LEAD_MS).coerceAtLeast(0)
            val glow=when {glowTime<0 || glowTime>=GLOW_MS->0.0;glowTime<=GLOW_LEAD_MS->.52*glowTime/GLOW_LEAD_MS;else->.52*(GLOW_MS-glowTime)/(GLOW_MS-GLOW_LEAD_MS)}
            meters[key]=QuotaMeterFrame((bar/100).toFloat(),percent,glow.toFloat())
        }
        resets=next
        frame=QuotaWindowMotionFrame(meters,entry,entryStart!=null || resets.isNotEmpty())
    }
    fun settle() {entryStart=null;resets=emptyMap();frame=QuotaWindowMotionFrame()}
    fun stop() {lastShown=false;entryPending=false;settle()}
    companion object {
        const val GLOW_MS=700L
        const val GLOW_LEAD_MS=252L
        private val ENTRY_EASING=CubicBezierEasing(.22f,1f,.36f,1f)
        private val RESET_EASING=CubicBezierEasing(.333f,.667f,.667f,1f)
    }
}
