package dev.bing.tokenmonitor

import kotlinx.serialization.json.*

data class PeriodAverageRate(val speed:Double=0.0,val burn:Double=0.0)
internal object DesktopAverageRate {
    fun parse(period:JsonObject):PeriodAverageRate {
        fun positive(key:String):Double {
            val text=(period[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf {it.length<=128} ?: return 0.0
            val value=when(text) {"true"->1.0;"false"->0.0;else->text.toDoubleOrNull() ?: 0.0}
            return value.takeIf {it.isFinite() && it>0} ?: 0.0
        }
        val duration=positive("timedDurationMs")
        fun rate(key:String,multiplier:Double):Double {
            val numerator=positive(key)
            return if(duration>0 && numerator>0) (numerator*multiplier/duration).coerceIn(0.0,1e12) else 0.0
        }
        return PeriodAverageRate(rate("timedOutputTokens",1000.0),rate("timedTokens",60000.0))
    }
    fun text(rate:PeriodAverageRate?,mode:String,units:String,locale:String):String? {
        val value=if(mode=="burn") rate?.burn ?: 0.0 else rate?.speed ?: 0.0
        // The original title reveal hides a reading which rounds to zero.
        if(kotlin.math.floor(value+.5)<=0) return null
        return "≈ "+DesktopCompactTokens.format(value,units,locale)+(if(mode=="burn") " tok/min" else " tok/s")
    }
}
