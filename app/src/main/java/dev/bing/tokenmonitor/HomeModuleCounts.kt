package dev.bing.tokenmonitor

/** Fixed desktop v0.76 count defaults and bounds, independent per Home module. */
internal object HomeModuleCounts {
    val defaults=linkedMapOf("tools" to 5,"models" to 5,"sessions" to 5,"devices" to 4)
    fun normalize(id:String,input:String?):Int {
        val fallback=defaults.getValue(id)
        val value=input?.trim()?.toDoubleOrNull()?.takeIf {it.isFinite()} ?: return fallback
        return value.coerceIn(1.0,50.0).toInt()
    }
    fun normalized(values:Map<String,Int>)=defaults.mapValues {(id,_)->normalize(id,values[id]?.toString())}
    fun count(p:DisplayPreferences,id:String)=normalize(id,p.homeModuleCounts[id]?.toString())
    fun update(values:Map<String,Int>,id:String,input:String)=normalized(values)+(id to normalize(id,input))
}
