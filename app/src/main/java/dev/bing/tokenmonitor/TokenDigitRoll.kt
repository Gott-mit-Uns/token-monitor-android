package dev.bing.tokenmonitor

import kotlin.math.abs

internal object TokenNumberAnimationMode {
    fun normalize(value:String?)=if(value=="count") "count" else "roll"
}

internal data class RollingGlyph(val char:Char,val from:Float,val to:Float) {
    fun position(progress:Float)=from+(to-from)*progress
}

/** Finite two-layer columns. Retarget from the current glyph offsets; never stack old timelines. */
internal class TokenDigitRoll(initial:String) {
    var target=initial
        private set
    var columns=initial.map {listOf(RollingGlyph(it,0f,0f))}
        private set
    var running=false
        private set
    var progress=1f
        private set
    val length get()=columns.size
    fun retarget(next:String,increasing:Boolean,animate:Boolean):Boolean {
        if(!animate) {target=next;settle();return false}
        if(next==target) return false
        val sampled=columns.map {column->column.map {it.char to it.position(progress)}}
        val count=maxOf(sampled.size,next.length)
        val old=sampled+List(count-sampled.size) {emptyList<Pair<Char,Float>>()}
        val padded=next.padEnd(count,' ')
        var changed=false
        val direction=if(increasing) -1f else 1f
        columns=padded.mapIndexed {i,char->
            val visible=old[i].minByOrNull {abs(it.second)}
            val incoming=old[i].firstOrNull {it.first==char}
            if(visible?.first==char && abs(visible.second)<.0001f) listOf(RollingGlyph(char,0f,0f))
            else {
                changed=true
                buildList {
                    visible?.takeIf {it.first!=char && it.first!=' '}?.let {add(RollingGlyph(it.first,it.second,direction))}
                    if(char!=' ') add(RollingGlyph(char,incoming?.second ?: -direction,0f))
                }
            }
        }
        target=next;progress=0f;running=changed
        return changed
    }
    fun frame(fraction:Float) {
        val p=fraction.coerceIn(0f,1f)
        progress=1f-(1f-p)*(1f-p)*(1f-p)*(1f-p)
        if(p>=1f) settle()
    }
    fun settle() {
        columns=target.padEnd(if(target=="—") target.length else maxOf(columns.size,target.length),' ').map {listOf(RollingGlyph(it,0f,0f))}
        progress=1f;running=false
    }
}
