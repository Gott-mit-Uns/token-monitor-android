package dev.bing.tokenmonitor

import java.math.BigDecimal
import java.math.BigInteger
import java.text.Collator
import java.util.Locale

/** Original motionPreference.js and usageAttributionRows.js presentation rules. */
internal object DesktopMotionPreference {
    fun normalize(value:String) = value.trim().takeIf {it in listOf("system","on","off")} ?: "system"
    fun reduced(value:String,system:Boolean) = when(normalize(value)) {"on"->true;"off"->false;else->system}
    fun scale(value:String,system:Float) = when(normalize(value)) {"on"->0f;"off"->1f;else->system}
}
internal data class RankedModel(val id:String,val tokens:BigInteger?,val barValue:Double)
internal object DesktopModelRanking {
    fun rows(counts:Map<String,BigInteger>,details:Map<String,UsageBreakdown>,metric:String):List<RankedModel> {
        val keys=(counts.keys+details.keys).toList()
        fun cost(id:String)=details[id]?.cost?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        fun tokens(id:String)=counts[id] ?: BigInteger.ZERO
        val byCost=metric=="cost" && keys.any {it!=UsageAttribution.UNATTRIBUTED && cost(it).signum()>0}
        val collator=Collator.getInstance(Locale.ENGLISH)
        val sorted=keys.sortedWith {a,b->
            val money=if(byCost) cost(b).compareTo(cost(a)) else 0
            if(money!=0) money else tokens(b).compareTo(tokens(a)).takeIf {it!=0} ?: collator.compare(a,b)
        }
        return sorted.map {id->RankedModel(id,counts[id],(if(byCost) cost(id).toDouble() else tokens(id).toDouble()).coerceAtLeast(0.0))}
    }
}
