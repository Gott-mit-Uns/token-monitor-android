package dev.bing.tokenmonitor

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

internal data class SettingSwitchPose(val position:Float,val background:Color,val border:Color,val thumb:Color,val running:Boolean=false)
/** Retarget the pixels currently displayed; the checked setting never waits for this controller. */
internal class SettingSwitchMotion(initial:SettingSwitchPose) {
    var pose=initial;private set
    private var from=initial
    private var target=initial
    private val thumbEase=CubicBezierEasing(.22f,1f,.36f,1f)
    private val colorEase=CubicBezierEasing(.25f,.1f,.25f,1f)
    fun retarget(next:SettingSwitchPose,animate:Boolean) {
        from=pose.copy(running=false);target=next.copy(running=false)
        pose=if(animate && from!=target) from.copy(running=true) else target
    }
    fun frame(fraction:Float) {
        if(!pose.running) return
        val p=fraction.coerceIn(0f,1f)
        if(p>=1f) {settle(target);return}
        val x=thumbEase.transform(p);val c=colorEase.transform(p)
        pose=SettingSwitchPose(from.position+(target.position-from.position)*x,
            lerp(from.background,target.background,c),lerp(from.border,target.border,c),lerp(from.thumb,target.thumb,c),true)
    }
    fun settle(next:SettingSwitchPose) {target=next.copy(running=false);pose=target}
}
