package dev.bing.tokenmonitor

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/** Desktop compactTokens.js. Full source counts remain available everywhere. */
internal object DesktopCompactTokens {
    fun localized(units:String,locale:String)=units=="localized" && Regex("^(zh|ja|ko)(?:-|$)").containsMatchIn(locale.replace('_','-').lowercase(Locale.ROOT))
    fun threshold(units:String,locale:String)=if(localized(units,locale)) 1e4 else 1e3
    fun format(value:Double,units:String="western",locale:String="en",roundTokens:Boolean=true):String {
        if(!value.isFinite()) return "—"
        val number=if(roundTokens) floor(value+0.5) else value
        val local=localized(units,locale)
        val language=locale.replace('_','-').lowercase(Locale.ROOT)
        val suffix=when {
            language.startsWith("ko")->listOf("만","억")
            language.startsWith("ja")->listOf("万","億")
            language.startsWith("zh-hans") || Regex("^(?:zh)(?:-[a-z0-9]+)*-(?:cn|sg|my)(?:-|$)").containsMatchIn(language)->listOf("万","亿")
            else->listOf("萬","億")
        }
        val scales=if(local) listOf(1e4,1e8) else listOf(1e3,1e6,1e9)
        val names=if(local) suffix else listOf("K","M","B")
        var index=scales.indexOfLast {abs(number)>=it}
        if(index<0) return BigDecimal.valueOf(number).stripTrailingZeros().toPlainString()
        fun fixed():String {val scaled=number/scales[index];return BigDecimal(scaled).setScale(if(local && abs(scaled)<10) 2 else 1,RoundingMode.HALF_UP).toPlainString()}
        var display=fixed()
        if(abs(display.toDouble())>=(if(local) 10000 else 1000) && index<scales.lastIndex) {index++;display=fixed()}
        return display.replace(Regex("\\.?0+$"),"")+names[index]
    }
    fun liveRate(value:Double,units:String,locale:Locale):String {
        val rate=value.coerceAtLeast(0.0)
        if(rate>0 && rate<0.1) return "<0.1"
        if(rate>0 && rate<1) return java.text.NumberFormat.getNumberInstance(locale).apply {maximumFractionDigits=1;roundingMode=RoundingMode.HALF_UP}.format(BigDecimal.valueOf(rate))
        return format(rate,units,locale.toLanguageTag())
    }
    fun localeTag(locale:Locale)=if(locale.language=="zh" && locale.country.isEmpty() && locale.script!="Hant") "zh-CN" else locale.toLanguageTag()
}
