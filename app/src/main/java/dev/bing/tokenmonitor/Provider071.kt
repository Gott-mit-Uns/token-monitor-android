package dev.bing.tokenmonitor

import kotlinx.serialization.json.*

/** Fixed v0.71 wire display rules. No provider transport, credentials or collectors. */
internal object Provider071 {
    private fun JsonObject.s(key:String)=(get(key) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.n(key:String)=(get(key) as? JsonPrimitive)?.takeIf {!it.isString}?.doubleOrNull?.takeIf {it.isFinite()}
    private fun JsonObject.flag(key:String)=(get(key) as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.windows()=(get("windows") as? JsonArray).orEmpty().mapNotNull {it as? JsonObject}

    fun notionWindows(record:JsonObject):List<JsonObject> {
        if(record.s("status")!="ok") return emptyList()
        val windows=record.windows()
        if(windows.isEmpty() || windows.size>2 || windows.map {it.s("kind")}.distinct().size!=windows.size) return emptyList()
        if(windows.any {w ->
            val used=w.n("used");val cap=w.n("limit")
            w.s("kind") !in setOf("session","billing") || !w.s("metric").isNullOrBlank() || !w.s("currency").isNullOrBlank() ||
                used==null || used<0 || cap==null || cap<=0 || !((used/cap)*100).isFinite() ||
                (w.s("kind")=="session" && w.n("windowMinutes")!=360.0) ||
                (w.s("kind")=="billing" && w["windowMinutes"]!=null && w["windowMinutes"]!=JsonNull)
        }) return emptyList()
        return windows
    }
    fun notionRecord(raw:JsonObject):JsonObject {
        val windows=notionWindows(raw)
        // Notion identity is a collector-generated hash. User/workspace ids and names are never display fields.
        val public=LimitProviderLayout.displayRecord(raw).toMutableMap()
        listOf("balance","usageSummary","accountEmail","email","accountName","accountLabel","workspaceLabel","workspaceKind","webAccountKey","accountKeyAliases").forEach(public::remove)
        val key=raw.s("accountKey")?.takeIf {Regex("sha256:[a-f0-9]{64}").matches(it)}
        if(key==null) public.remove("accountKey") else public["accountKey"]=JsonPrimitive(key)
        public["windows"]=JsonArray(windows.map {w ->
            JsonObject(w.toMutableMap().apply {
                val percent=(w.n("used")!! / w.n("limit")!! * 100).coerceIn(0.0,100.0)
                put("usedPercent",JsonPrimitive(percent));put("remainingPercent",JsonPrimitive(100-percent))
                remove("currency");remove("metric")
            })
        })
        if(raw.s("status")=="ok" && windows.isEmpty()) public["status"]=JsonPrimitive("unavailable")
        return LimitProviderLayout.displayRecord(JsonObject(public))
    }
    /** A complete snapshot's account set only; never sum the visible/filtered subset. */
    fun workbuddyTotals(records:List<JsonObject>):List<JsonObject>? {
        if(records.size<2) return null
        val keys=records.map {it.s("accountKey").orEmpty()}
        if(keys.any {it.isBlank()} || keys.distinct().size!=keys.size) return null
        val measurements=setOf("used","remaining","limit","usedPercent","remainingPercent")
        fun signature(w:JsonObject)=JsonObject(w.toSortedMap().filterKeys {it !in measurements}).toString()
        val readings=records.map {p ->
            if(p.s("provider")!="workbuddy" || p.s("status")!="ok" || p.flag("stale")==true || !p.s("actionRequired").isNullOrBlank()) return null
            if(p.s("region")!=records.first().s("region")) return null
            val windows=p.windows();if(windows.isEmpty()) return null
            if(windows.any {w ->
                val cap=w.n("limit");val used=w.n("used");val remaining=w.n("remaining")
                w.s("metric")!="credits" || w.s("currency").isNullOrBlank() || w.s("detail")=="unlimited" ||
                    cap==null || cap<=0 || used==null || used !in 0.0..cap || remaining==null || remaining !in 0.0..cap
            }) return null
            val mapped=windows.associateBy(::signature);if(mapped.size!=windows.size) return null
            mapped
        }
        val first=readings.first()
        if(readings.any {it.keys!=first.keys}) return null
        return first.map { (key,w) ->
            val totals=listOf("used","remaining","limit").associateWith {field -> readings.sumOf {it.getValue(key).n(field)!!}}
            if(totals.values.any {!it.isFinite()}) return null
            JsonObject(w.toMutableMap().apply {
                totals.forEach {(field,value)->put(field,JsonPrimitive(value))}
                put("usedPercent",JsonPrimitive(totals.getValue("used")/totals.getValue("limit")*100))
                put("remainingPercent",JsonPrimitive(totals.getValue("remaining")/totals.getValue("limit")*100))
            })
        }
    }
}
