package dev.bing.tokenmonitor

import androidx.compose.animation.core.CubicBezierEasing

internal data class DesktopPeriodIndicatorFrame(val index:Float,val running:Boolean)

/** Pinned app.js syncPeriodTabs and styles.css tab-indicator: one 220ms translated layer. */
internal class DesktopPeriodIndicatorMotion(initialIndex:Int) {
    private var target=initialIndex.coerceIn(0,2).toFloat()
    private val channel=DisclosureTransitionChannel(target,220,CubicBezierEasing(.22f,1f,.36f,1f))
    private var resumePending=false
    val frame get()=DesktopPeriodIndicatorFrame(channel.value,channel.running)
    fun render(index:Int,shown:Boolean,now:Long,scale:Float):Boolean {
        val next=index.coerceIn(0,2).toFloat()
        val changed=next!=target
        val enabled=shown && !resumePending && scale>0f && scale.isFinite()
        channel.retarget(next,enabled,now,scale)
        target=next
        resumePending=!shown
        return changed || !enabled
    }
    fun advance(now:Long) {channel.sample(now)}
    fun stop() {resumePending=true;channel.settle(target)}
}
