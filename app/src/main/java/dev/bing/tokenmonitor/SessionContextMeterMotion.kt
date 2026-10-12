package dev.bing.tokenmonitor

import androidx.compose.animation.core.CubicBezierEasing

internal data class SessionContextMeterFrame(val scale:Float,val running:Boolean)

/** Retained .row-context-fill CSS; newly rendered/displayed gauges begin at their actual target. */
internal class SessionContextMeterMotion(initialScale:Float) {
    private var target=safe(initialScale)
    private val channel=DisclosureTransitionChannel(target,420,EASING)
    private var resumePending=false
    val frame get()=SessionContextMeterFrame(channel.value,channel.running)

    fun render(nextScale:Float,shown:Boolean,now:Long,durationScale:Float):Boolean {
        val next=safe(nextScale)
        val changed=target!=next
        val enabled=shown && !resumePending && durationScale>0f && durationScale.isFinite()
        channel.retarget(next,enabled,now,durationScale)
        target=next
        resumePending=!shown
        return changed || !enabled
    }
    fun advance(now:Long,durationScale:Float=1f) {
        if(durationScale<=0f || !durationScale.isFinite()) stop() else channel.sample(now)
    }
    fun stop() {resumePending=true;channel.settle(target)}

    companion object {
        private val EASING=CubicBezierEasing(.22f,1f,.36f,1f)
        private fun safe(value:Float)=if(value.isFinite()) value.coerceIn(0f,1f) else 0f
    }
}
