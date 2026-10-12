package dev.bing.tokenmonitor

import androidx.compose.animation.core.CubicBezierEasing

/** Original updateRowLive timestamp rule: never first render, idle, heartbeat, or regression. */
internal class SessionPulseMemory {
    private var previousAt=0L
    fun observe(next:Long,running:Boolean):Boolean {
        val previous=previousAt
        if(next>0) previousAt=next
        return running && previous>0 && next>previous
    }
    fun clear() {previousAt=0}
}
internal data class SessionGlow(val blur:Float=4f,val opacity:Float=.55f)
internal object SessionRowPulse {
    const val durationMillis=1200L
    private val ease=CubicBezierEasing(0f,0f,.58f,1f)
    fun frame(progress:Float):SessionGlow {
        val p=progress.coerceIn(0f,1f)
        if(p>=1f) return SessionGlow()
        return if(p<.35f) {
            val t=ease.transform(p/.35f);SessionGlow(3f+7f*t,.45f+.5f*t)
        } else {
            val t=ease.transform((p-.35f)/.65f);SessionGlow(10f-6f*t,.95f-.4f*t)
        }
    }
}
