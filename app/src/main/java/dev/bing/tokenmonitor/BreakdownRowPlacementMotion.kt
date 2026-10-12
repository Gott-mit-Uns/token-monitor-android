package dev.bing.tokenmonitor

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.abs

internal data class BreakdownRowLayer(val alpha:Float=1f,val translationY:Float=0f)
internal data class BreakdownRowPlacementFrame(val layers:Map<String,BreakdownRowLayer> = emptyMap(),val running:Boolean=false) {
    fun row(key:String)=layers[key] ?: BreakdownRowLayer()
}

/** Source animateBreakdownFrom row transforms. Coordinates and targets are display-only. */
internal class BreakdownRowPlacementMotion {
    private data class PositionTween(val from:Float,val start:Long)
    private data class NewTween(val start:Long,val delayMs:Int,val offset:Float)
    private data class CapturedPosition(val base:Float,val displayed:Float)
    private var source:Long?=null
    private var main:String?=null
    private var period:String?=null
    private var periodRevision=0L
    private var lastShown:Boolean?=null
    private var entryPending=false
    private var previousCount=0
    private var keys=emptySet<String>()
    private var numericKeys=emptySet<String>()
    private var token:Any?=null
    private var lastRows:List<BreakdownRowSpec>?=null
    private val positions=linkedMapOf<String,Float>()
    private var pending=emptyMap<String,CapturedPosition>()
    private var pendingStart=0L
    private var placements=emptyMap<String,PositionTween>()
    private var newRows=emptyMap<String,NewTween>()
    var frame=BreakdownRowPlacementFrame()
        private set
    val running get()=frame.running
    val retainedCoordinateCount get()=positions.size
    val retainedAnimationCount get()=(placements.keys+newRows.keys+pending.keys).size

    /** One call per owner commit, never from a row's animation frame. */
    fun render(rows:List<BreakdownRowSpec>?,connectionKey:Long,mainViewKey:String,periodKey:String,
        event:BreakdownPeriodEvent,renderToken:Any,now:Long,shown:Boolean,animate:Boolean,
        scale:Float=1f,newRowOffsetPx:Float=7f):Boolean {
        val sourceChanged=source!=null && source!=connectionKey
        if(sourceChanged) {settle();positions.clear();keys=emptySet();numericKeys=emptySet();previousCount=0;lastRows=null;token=null;entryPending=false}
        source=connectionKey
        val mainChanged=main!=null && main!=mainViewKey
        if(mainChanged) entryPending=true
        main=mainViewKey
        val restoring=lastShown==false && shown && !entryPending
        lastShown=shown
        val periodChanged=period!=null && period!=periodKey
        val tab=periodChanged && event.reason==BreakdownPeriodReason.TAB && event.revision!=periodRevision
        period=periodKey;periodRevision=event.revision
        if(rows==null) {
            val changed=running
            settle();positions.clear()
            if(shown) {keys=emptySet();numericKeys=emptySet();previousCount=0;lastRows=null;token=null;entryPending=false}
            return changed
        }
        val entering=entryPending && shown
        if(shown) entryPending=false // Entry is consumed by its synchronous visible render, including empty rows.
        val enabled=shown && animate && scale>0f && scale.isFinite()
        if(!enabled || entering || restoring || sourceChanged || (periodChanged && !tab) ||
            previousCount>BreakdownMotion.MAX_ANIMATED_ROWS || rows.size>BreakdownMotion.MAX_ANIMATED_ROWS) {
            val changed=running
            settle();commit(rows,renderToken)
            if(!shown || entering || sourceChanged) positions.clear()
            return changed
        }
        if(token===renderToken && (lastRows===rows || lastRows==rows)) return false
        advance(now,scale)
        val oldKeys=keys
        val captured=positions.mapValues { (key,base)->CapturedPosition(base,base+frame.row(key).translationY) }
        val nextNumeric=rows.filter {it.number!=null}.mapTo(linkedSetOf()) {it.key}
        val nextPlacements=placements.filterKeys {it in nextNumeric}
        val nextNewRows=newRows.filterKeys {it in nextNumeric}.toMutableMap()
        var index=0
        for(row in rows) if(row.key !in oldKeys && row.number!=null) {
            nextNewRows[row.key]=NewTween(now,minOf(index++,6)*18,newRowOffsetPx.takeIf {it.isFinite()} ?: 0f)
        }
        placements=nextPlacements;newRows=nextNewRows
        pending=captured.filterKeys {it in nextNumeric && it in oldKeys}
        pendingStart=now
        commit(rows,renderToken)
        advance(now,scale)
        return true
    }

    private fun commit(rows:List<BreakdownRowSpec>,renderToken:Any) {
        previousCount=rows.size
        // Never allocate a 320-row coordinate table for large entry/updates.
        keys=if(rows.size<=BreakdownMotion.MAX_ANIMATED_ROWS) rows.mapTo(linkedSetOf()) {it.key} else emptySet()
        numericKeys=if(keys.isNotEmpty()) rows.filter {it.number!=null}.mapTo(linkedSetOf()) {it.key} else emptySet()
        positions.keys.retainAll(keys)
        lastRows=rows;token=renderToken
    }

    /** Called once after a real layout/position commit; the outer coordinate has no own transform. */
    fun positioned(key:String,baseWindowY:Float,renderToken:Any,now:Long,scale:Float=1f):Boolean {
        if(key !in keys || !baseWindowY.isFinite()) return false
        positions[key]=baseWindowY
        if(token!==renderToken || key !in numericKeys) return false
        val before=pending[key] ?: return false
        pending=pending-key
        if(abs(before.base-baseWindowY)>.5f && scale>0f && scale.isFinite()) {
            // Replacing a FLIP starts at the previously captured displayed position.
            placements=placements+(key to PositionTween(before.displayed-baseWindowY,now))
            // A new-row fade may remain active, but FLIP replaces its translation.
        }
        advance(now,scale)
        return true
    }

    fun detached(key:String) {
        positions.remove(key)
        pending=pending-key
    }

    fun advance(now:Long,scale:Float=1f) {
        if(scale<=0f || !scale.isFinite()) {settle();return}
        if(progress(now,pendingStart,0,280,scale)>=1f) pending=emptyMap()
        val layers=linkedMapOf<String,BreakdownRowLayer>()
        val survivingNew=linkedMapOf<String,NewTween>()
        for((key,tween) in newRows) {
            val p=progress(now,tween.start,tween.delayMs,240,scale)
            if(p<1f) {
                survivingNew[key]=tween
                val eased=EASING.transform(p)
                layers[key]=BreakdownRowLayer(eased,tween.offset*(1f-eased))
            }
        }
        val survivingPositions=linkedMapOf<String,PositionTween>()
        for((key,tween) in placements) {
            val p=progress(now,tween.start,0,280,scale)
            if(p<1f) {
                survivingPositions[key]=tween
                layers[key]=BreakdownRowLayer(layers[key]?.alpha ?: 1f,tween.from*(1f-EASING.transform(p)))
            }
        }
        newRows=survivingNew;placements=survivingPositions
        frame=BreakdownRowPlacementFrame(layers,placements.isNotEmpty() || newRows.isNotEmpty() || pending.isNotEmpty())
    }

    fun settle() {placements=emptyMap();newRows=emptyMap();pending=emptyMap();frame=BreakdownRowPlacementFrame()}

    /** STOP is authoritative even if foreground false/true commits are coalesced. */
    fun stop() {lastShown=false;entryPending=false;positions.clear();settle()}

    companion object {
        private val EASING=CubicBezierEasing(.22f,1f,.36f,1f)
        private fun progress(now:Long,start:Long,delay:Int,duration:Int,scale:Float)=
            (((now-start).coerceAtLeast(0).toDouble()/scale-delay*1_000_000.0)/(duration*1_000_000.0)).coerceIn(0.0,1.0).toFloat()
    }
}
