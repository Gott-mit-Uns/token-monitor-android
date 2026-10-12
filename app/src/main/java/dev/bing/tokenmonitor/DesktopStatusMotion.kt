package dev.bing.tokenmonitor

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.floor

internal object DesktopSessionSpinner {
    const val durationMillis=900L
    private val opacity=floatArrayOf(.95f,.78f,.62f,.44f,.30f,.22f,.16f,.12f,.95f)
    fun alpha(elapsedMillis:Double,index:Int):Float {
        val phase=((elapsedMillis/durationMillis+index/8.0)%1.0+1.0)%1.0
        val at=phase*8;val segment=floor(at).toInt().coerceIn(0,7)
        return opacity[segment]+(opacity[segment+1]-opacity[segment])*(at-segment).toFloat()
    }
}
internal data class RateFlashFrame(val scale:Float=1f,val blur:Float=0f,val opacity:Float=0f,val playing:Boolean=false)
internal object DesktopRateFlash {
    const val durationMillis=620L
    private val ease=CubicBezierEasing(.22f,1f,.36f,1f)
    fun frame(progress:Float):RateFlashFrame {
        val p=progress.coerceIn(0f,1f)
        if(p>=1f) return RateFlashFrame()
        return if(p<.42f) {
            val t=ease.transform(p/.42f);RateFlashFrame(.86f+.27f*t,5f*t,.68f*t,true)
        } else {
            val t=ease.transform((p-.42f)/.58f);RateFlashFrame(1.13f-.13f*t,5f*(1-t),.68f*(1-t),true)
        }
    }
}
