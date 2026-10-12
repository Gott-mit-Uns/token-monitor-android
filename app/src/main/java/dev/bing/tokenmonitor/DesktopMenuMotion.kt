package dev.bing.tokenmonitor

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.abs

/** One shared fraction drives the original opacity, translation, scale and blur. */
internal class DesktopMenuMotion {
    var value=0f;private set
    var running=false;private set
    var durationMs=0f;private set
    private var from=0f
    private var target=0f
    private var reversingStart=0f
    private var shortening=1f
    private var eased=0f
    private val ease=CubicBezierEasing(.22f,1f,.36f,1f)
    fun retarget(open:Boolean,animate:Boolean) {
        val next=if(open) 1f else 0f
        val reversing=running && next!=target && next==reversingStart
        val factor=if(reversing) abs(eased*shortening+1f-shortening).coerceIn(0f,1f) else 1f
        reversingStart=if(reversing) target else value
        from=value;target=next;shortening=factor;eased=0f
        durationMs=(if(open) 190f else 130f)*factor
        running=animate && value!=target && durationMs>0f
        if(!running) settle(open)
    }
    fun frame(fraction:Float) {
        if(!running) return
        val p=fraction.coerceIn(0f,1f);eased=ease.transform(p)
        value=from+(target-from)*eased
        if(p>=1f) {value=target;running=false}
    }
    fun settle(open:Boolean) {value=if(open) 1f else 0f;target=value;running=false}
}
