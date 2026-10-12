package dev.bing.tokenmonitor

import java.util.Locale

data class MainDisplayPreferences(val viewOrder:List<String> = emptyList(),val hiddenViews:Set<String> = emptySet(),
    val titleIconOnly:Boolean=true,val compactTotal:Boolean=false,val compactUnits:String="western",val showLiveRate:Boolean=false,
    val rateMode:String="speed",val rateDevice:String="",val swapActions:Boolean=false,val sessionContextMetric:String="used")

/** Desktop main views supported by the Hub viewer; shared-data pages live under settings. */
internal object MainDisplay {
    val ids=listOf("home","limits","tools","models","projects","sessions","devices","trends")
    // Existing V12–V16 profiles may contain the former shortcuts. Loading must preserve them.
    val storedIds=ids+listOf("subscriptions","pricing")
    private fun id(value:String)=value.trim().lowercase(Locale.ROOT)
    fun order(values:List<String>):List<String> {val selected=values.map(::id).filter {it in ids}.distinct();return selected+ids.filter {it !in selected}}
    fun hidden(values:Collection<String>):Set<String> = values.map(::id).filter {it in ids}.distinct().let {if(it.size==ids.size) emptySet() else it.toSet()}
    fun visible(value:MainDisplayPreferences,include:Set<String> = emptySet())=order(value.viewOrder).filter {it !in hidden(value.hiddenViews) || it in include}
    fun preferred(value:MainDisplayPreferences,current:String)=visible(value).let {if(current in it) current else it.first()}
    fun move(value:MainDisplayPreferences,view:String,direction:Int):MainDisplayPreferences {
        val ordered=order(value.viewOrder).toMutableList();val from=ordered.indexOf(id(view));val to=from+direction
        if(from<0 || direction !in listOf(-1,1) || to !in ordered.indices) return value.copy(viewOrder=ordered)
        ordered.add(to,ordered.removeAt(from));return value.copy(viewOrder=ordered)
    }
    fun valid(value:MainDisplayPreferences)=value.viewOrder.distinct().size==value.viewOrder.size && value.viewOrder.all {it in storedIds} &&
        value.hiddenViews.all {it in storedIds} && value.hiddenViews.size<storedIds.size && value.compactUnits in listOf("western","localized") && value.rateMode in listOf("speed","burn") &&
        value.rateDevice.length<=256 && value.rateDevice.none(Char::isISOControl) && value.sessionContextMetric in listOf("used","remaining")
}
