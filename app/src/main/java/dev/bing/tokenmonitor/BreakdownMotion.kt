package dev.bing.tokenmonitor

import androidx.compose.animation.core.CubicBezierEasing
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.math.abs

internal enum class BreakdownPeriodReason { STATIC, TAB }
internal data class BreakdownPeriodEvent(val revision:Long=0,val reason:BreakdownPeriodReason=BreakdownPeriodReason.STATIC)
internal data class BreakdownRowSpec(val key:String,val barScale:Float,val number:BigInteger?)
internal data class BreakdownNumberFrame(val value:BigInteger,val widest:BigInteger)

/** Large entries share one fraction; only compact live/period views have row overrides. */
internal data class BreakdownMotionFrame(
    val targets:Map<String,BreakdownRowSpec> = emptyMap(),
    val entryBars:Map<String,Float> = emptyMap(),
    val entryFraction:Float=1f,
    val bars:Map<String,Float> = emptyMap(),
    val numbers:Map<String,BreakdownNumberFrame> = emptyMap(),
    val running:Boolean=false
) {
    fun bar(key:String,fallback:Float):Float = bars[key] ?: entryBars[key]?.let {it*entryFraction}
        ?: targets[key]?.barScale ?: fallback
    fun number(key:String,fallback:BigInteger):BigInteger = numbers[key]?.value ?: targets[key]?.number ?: fallback
    fun widest(key:String,fallback:BigInteger):BigInteger = numbers[key]?.widest ?: targets[key]?.number ?: fallback
}

/** app.js applyBarScale/animateBreakdownFrom; no clocks, source reads or UI geometry. */
internal class BreakdownMotion {
    private data class BarTween(val from:Float,val target:Float,val startedAt:Long,val delayMs:Int,val durationMs:Int)
    private data class NumberTween(val from:BigDecimal,val target:BigInteger,val startedAt:Long,val durationMs:Int,val widest:BigInteger)
    private data class Entry(val targets:Map<String,Float>,val startedAt:Long)
    private var connectionKey:Long?=null
    private var mainViewKey:String?=null
    private var periodKey:String?=null
    private var periodEventRevision=0L
    private var entryPending=false
    private var hasTargets=false
    private var lastShown:Boolean?=null
    private var lastTargets:List<BreakdownRowSpec>?=null
    private var lastTargetMap=emptyMap<String,BreakdownRowSpec>()
    private var entry:Entry?=null
    private var barTweens=emptyMap<String,BarTween>()
    private var numberTweens=emptyMap<String,NumberTween>()
    private var numberValues=emptyMap<String,BigDecimal>()
    var frame=BreakdownMotionFrame()
        private set
    val running get()=frame.running
    val retainedBarTweens get()=barTweens.size
    val retainedNumberTweens get()=numberTweens.size

    /** Return true when the current finite runner should be canceled/replaced. */
    fun render(
        targets:List<BreakdownRowSpec>?,connectionKey:Long,mainViewKey:String,periodKey:String,
        periodEvent:BreakdownPeriodEvent,nowNanos:Long,animate:Boolean,shown:Boolean,
        durationScale:Float=1f
    ):Boolean {
        val sourceChanged=this.connectionKey!=null && this.connectionKey!=connectionKey
        if(sourceChanged) {
            settle();frame=BreakdownMotionFrame();hasTargets=false;entryPending=false
            lastTargets=null;lastTargetMap=emptyMap()
        }
        this.connectionKey=connectionKey
        if(this.mainViewKey!=null && this.mainViewKey!=mainViewKey) entryPending=true
        this.mainViewKey=mainViewKey
        val restoringSameView=lastShown==false && shown && !entryPending
        lastShown=shown
        val periodChanged=this.periodKey!=null && this.periodKey!=periodKey
        val tabPeriod=periodChanged && periodEvent.reason==BreakdownPeriodReason.TAB && periodEvent.revision!=periodEventRevision
        this.periodKey=periodKey;periodEventRevision=periodEvent.revision
        if(targets==null) {
            if(!shown) {
                val changed=running
                settle()
                return changed
            }
            if(shown) entryPending=false
            val changed=running || frame.targets.isNotEmpty()
            settle();frame=BreakdownMotionFrame();hasTargets=false
            lastTargets=null;lastTargetMap=emptyMap()
            return changed
        }
        // The owner's event projection is remembered. Ordinary commits and all entry
        // frames reuse this map, without copying each row or retaining a Hub snapshot.
        val next=if(lastTargets===targets) lastTargetMap else targets.associate {spec->
            val scale=safeScale(spec.barScale)
            spec.key to if(scale==spec.barScale) spec else spec.copy(barScale=scale)
        }.also {lastTargets=targets;lastTargetMap=it}
        val entering=entryPending && shown && next.isNotEmpty()
        if(shown) entryPending=false // The source clears its entry flag after the synchronous render, including empty rows.
        val enabled=animate && shown && durationScale>0f && durationScale.isFinite()
        if(!enabled || restoringSameView) {
            val changed=running || frame.targets!=next
            frame=BreakdownMotionFrame(targets=next);settle();hasTargets=true
            if(shown && next.isNotEmpty()) entryPending=false
            return changed
        }
        if(hasTargets && frame.targets==next && !entering && !sourceChanged) return false
        advance(nowNanos,durationScale)
        val previous=frame
        val previousEntry=entry
        val previousBars=barTweens
        val previousNumbers=numberTweens
        val previousNumberValues=numberValues
        val compact=previous.targets.size<=MAX_ANIMATED_ROWS && next.size<=MAX_ANIMATED_ROWS
        if(entering) {
            // The source entry flag belongs to a real main-view change. applyBarScale
            // animates bars for every row; it does not count numbers or use the 40-row gate.
            val entryTargets=next.mapValues {it.value.barScale}
            entry=Entry(entryTargets,nowNanos).takeIf {entryTargets.values.any {it>0f}}
            barTweens=emptyMap();numberTweens=emptyMap();numberValues=emptyMap()
            frame=BreakdownMotionFrame(targets=next,entryBars=entry?.targets.orEmpty(),entryFraction=if(entry!=null) 0f else 1f,running=entry!=null)
            entryPending=false;hasTargets=true
            return true
        }
        if(sourceChanged || (periodChanged && !tabPeriod) || !compact) {
            // Menu/settings period changes are not the tab handler's 800ms path.
            // A large update creates no per-row work; an already running entry has
            // its original captured bar targets until it ends, then the latest base wins.
            barTweens=emptyMap();numberTweens=emptyMap();numberValues=emptyMap()
            if(sourceChanged || periodChanged) entry=null
            frame=BreakdownMotionFrame(targets=next,entryBars=entry?.targets.orEmpty(),entryFraction=previous.entryFraction,running=entry!=null)
            hasTargets=true
            return true
        }
        val duration=if(tabPeriod) 800 else 600
        var enteringIndex=0
        val bars=linkedMapOf<String,BarTween>()
        val numbers=linkedMapOf<String,NumberTween>()
        for((key,target) in next) {
            // Unknown/semantic primary values never acquire a zero-number reading.
            if(target.number==null) continue
            val old=previous.targets[key]
            val delay=if(old==null) minOf(enteringIndex++,6)*18 else 0
            val oldBar=previousBars[key] ?: previousEntry?.takeIf {key in it.targets}?.let {
                BarTween(0f,it.targets.getValue(key),it.startedAt,0,420)
            }
            val fromBar=if(old==null) 0f else previous.bar(key,old.barScale)
            if(oldBar!=null && abs(oldBar.target-target.barScale)<.001f) bars[key]=oldBar
            else if(abs(target.barScale-fromBar)>=.001f)
                bars[key]=BarTween(fromBar,target.barScale,nowNanos,delay,(duration-delay).coerceAtLeast(1))
            val oldNumber=previousNumbers[key]
            val fromNumber=if(old==null) BigDecimal.ZERO else previousNumberValues[key]
                ?: previous.number(key,old.number ?: target.number).toBigDecimal()
            if(oldNumber!=null && oldNumber.target==target.number) numbers[key]=oldNumber
            else if(fromNumber.compareTo(target.number.toBigDecimal())!=0) {
                val widest=maxOf(fromNumber.setScale(0,RoundingMode.HALF_UP).toBigIntegerExact(),target.number)
                numbers[key]=NumberTween(fromNumber,target.number,nowNanos,duration,widest)
            }
        }
        entry=null;barTweens=bars;numberTweens=numbers;hasTargets=true
        frame=BreakdownMotionFrame(targets=next)
        advance(nowNanos,durationScale)
        return true
    }

    fun advance(nowNanos:Long,durationScale:Float=1f) {
        if(durationScale<=0f || !durationScale.isFinite()) {settle();return}
        val currentEntry=entry
        val entryProgress=currentEntry?.let {progress(nowNanos,it.startedAt,0,420,durationScale)} ?: 1.0
        if(currentEntry!=null && entryProgress>=1.0) entry=null
        val bars=linkedMapOf<String,Float>()
        val survivingBars=linkedMapOf<String,BarTween>()
        for((key,tween) in barTweens) {
            val p=progress(nowNanos,tween.startedAt,tween.delayMs,tween.durationMs,durationScale)
            if(p<1.0) {
                survivingBars[key]=tween
                bars[key]=tween.from+(tween.target-tween.from)*BAR_EASING.transform(p.toFloat())
            }
        }
        val numbers=linkedMapOf<String,BreakdownNumberFrame>()
        val continuous=linkedMapOf<String,BigDecimal>()
        val survivingNumbers=linkedMapOf<String,NumberTween>()
        for((key,tween) in numberTweens) {
            val p=progress(nowNanos,tween.startedAt,0,tween.durationMs,durationScale)
            if(p<1.0) {
                survivingNumbers[key]=tween
                val remaining=BigDecimal.ONE.subtract(BigDecimal.valueOf(p))
                val eased=BigDecimal.ONE.subtract(remaining.pow(4))
                val value=tween.from.add(tween.target.toBigDecimal().subtract(tween.from).multiply(eased)).setScale(12,RoundingMode.HALF_UP)
                continuous[key]=value
                numbers[key]=BreakdownNumberFrame(value.setScale(0,RoundingMode.HALF_UP).toBigIntegerExact(),tween.widest)
            }
        }
        barTweens=survivingBars;numberTweens=survivingNumbers;numberValues=continuous
        frame=BreakdownMotionFrame(frame.targets,entry?.targets.orEmpty(),
            if(entry!=null) BAR_EASING.transform(entryProgress.toFloat()) else 1f,bars,numbers,
            entry!=null || bars.isNotEmpty() || numbers.isNotEmpty())
    }

    /** Preserve a STOP boundary even if lifecycle changes coalesce before another owner render. */
    fun stop() {
        lastShown=false
        entryPending=false
        settle()
    }

    fun settle() {
        entry=null;barTweens=emptyMap();numberTweens=emptyMap();numberValues=emptyMap()
        frame=BreakdownMotionFrame(targets=frame.targets)
    }

    companion object {
        const val MAX_ANIMATED_ROWS=40
        private val BAR_EASING=CubicBezierEasing(.22f,1f,.36f,1f)
        private fun safeScale(value:Float)=if(value.isFinite()) value.coerceIn(0f,1f) else 0f
        private fun progress(now:Long,start:Long,delayMs:Int,durationMs:Int,scale:Float):Double =
            (((now-start).coerceAtLeast(0).toDouble()/scale-delayMs*1_000_000.0)/(durationMs*1_000_000.0)).coerceIn(0.0,1.0)
        /** Pinned rowWidth has no artificial max=1 floor, and preserves a 2% nonzero bar. */
        fun bar(value:Double,max:Double):Float = if(!value.isFinite() || value<=0.0 || !max.isFinite() || max<=0.0) 0f
            else (value/max).coerceIn(.02,1.0).toFloat()
    }
}
