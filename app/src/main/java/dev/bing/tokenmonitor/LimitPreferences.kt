package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import java.util.Locale

/** Fixed desktop provider ordering and usage-item identity; presentation only. */
internal object LimitPreferences {
    val providers=desktopProviderLabels.keys.toList()
    fun settingsLabel(provider:String)=if(provider=="zai") "Z.ai / GLM" else providerLabel(provider)
    fun matches(provider:String,query:String,locale:Locale):Boolean {
        val label=when(provider) {"claude"->"Claude";"zai"->"GLM";else->providerLabel(provider)}
        return (settingsLabel(provider)+" "+label+" "+provider).lowercase(locale).contains(query.trim().lowercase(locale))
    }
    private fun id(value:String)=value.trim().lowercase(Locale.ROOT)
    fun selection(values:Collection<String>)=values.map(::id).filter {it in providers}.distinct()
    fun order(values:Collection<String>):List<String> {val selected=selection(values);return selected+providers.filter {it !in selected}}
    fun item(raw:String):String? {
        val value=raw.trim();if(value in setOf("credits","spend","resets")) return value
        if(value.length>400) return null
        val parts=runCatching {Json.parseToJsonElement(value) as? JsonArray}.getOrNull() ?: return null
        fun string(index:Int)=(parts.getOrNull(index) as? JsonPrimitive)?.takeIf {it.isString}?.content
        fun bool(index:Int)=(parts.getOrNull(index) as? JsonPrimitive)?.takeIf {!it.isString}?.booleanOrNull
        val backend=parts.size==6 && string(0)=="id"
        if(!backend && parts.size!=4) return null
        val start=if(backend) 2 else 0
        val kind=string(start)?.takeIf {it.isNotEmpty()} ?: return null
        val metric=string(start+if(backend) 1 else 2) ?: return null
        val additional=bool(start+if(backend) 2 else 3) ?: return null
        val window=buildJsonObject {
            put("kind",kind);put("metric",metric);put("additional",additional)
            if(backend) {
                val limitId=string(1)?.trim()?.takeIf {it.isNotEmpty()} ?: return null
                val minutes=parts[5]
                if(minutes!=JsonNull && ((minutes as? JsonPrimitive)?.takeIf {!it.isString}?.doubleOrNull?.let {it>0}!=true)) return null
                put("limitId",limitId);put("windowMinutes",minutes)
            } else put("label",string(1) ?: return null)
        }
        return LimitProviderLayout.windowKey(window)
    }
    fun hidden(value:Map<String,List<String>>):Map<String,List<String>> = buildMap {
        value.forEach {(raw,values)->val provider=id(raw);if(provider in providers) {
            val ids=values.mapNotNull(::item).distinct().take(64);if(ids.isNotEmpty()) put(provider,ids)
        }}
    }
    fun windowItem(provider:String,window:JsonObject):String? {
        val metric=(window["metric"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim().lowercase(Locale.ROOT)
        val kind=(window["kind"] as? JsonPrimitive)?.contentOrNull
        val label=(window["label"] as? JsonPrimitive)?.contentOrNull
        if(provider=="cline" && metric=="spend") return "credits"
        if(provider=="claude" && metric.isEmpty() && kind=="billing" && label=="Usage credits") return "spend"
        if(provider=="openrouter" && metric.isEmpty() && label=="Credits") return "credits"
        return metric.takeIf {it in setOf("credits","spend")} ?: LimitProviderLayout.windowKey(window)
    }
    fun setHidden(value:Map<String,List<String>>,provider:String,key:String,hidden:Boolean):Map<String,List<String>> {
        val canonical=item(key) ?: return hidden(value);val ids=hidden(value)[id(provider)].orEmpty().toMutableList()
        if(hidden && canonical !in ids) ids+=canonical else if(!hidden) ids.remove(canonical)
        return hidden(value+(id(provider) to ids))
    }
    fun fallback(provider:String,key:String):String {
        when(key) {"credits"->return "Balance";"spend"->return "Spend";"resets"->return "Resets"}
        val canonical=item(key) ?: return "";val parts=Json.parseToJsonElement(canonical).jsonArray
        val backend=parts.size==6;val kind=parts[if(backend) 2 else 0].jsonPrimitive.content
        val window=buildJsonObject {put("kind",kind);if(!backend) put("label",parts[1])}
        val label=LimitProviderLayout.label(provider,window,kind)
        return if(backend && parts[4].jsonPrimitive.boolean) "${parts[1].jsonPrimitive.content} · $label" else label
    }
    fun filter(rows:List<ProviderQuotaRow>,hidden:Collection<String>):List<ProviderQuotaRow> {
        if(hidden.isEmpty() || rows.none {it.item in hidden}) return rows
        val visible=rows.filter {it.item !in hidden}.toMutableList();val run=mutableListOf<Int>()
        fun close() {if(run.size%2==1) {val index=run.last();visible[index]=visible[index].copy(wide=true)};run.clear()}
        visible.forEachIndexed {index,row->if(row.wide || (run.isNotEmpty() && visible[run.first()].group!=row.group)) close();if(!row.wide) run+=index}
        close();return visible
    }
    fun move(values:List<String>,provider:String,direction:Int):List<String> {
        val ordered=order(values).toMutableList();val from=ordered.indexOf(id(provider));val to=from+direction
        if(direction !in setOf(-1,1) || from<0 || to !in ordered.indices) return ordered
        ordered.add(to,ordered.removeAt(from));return ordered
    }
    fun mimoGroups(accounts:List<List<Limit>>):List<List<List<Limit>>> = accounts.withIndex().groupBy {(index,rows)->
        val first=rows.first();val raw=first.presentation.providerRecord
        val name=if(raw!=null) (raw["accountName"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim() else first.presentation.identity.name.orEmpty().trim()
        val email=if(raw!=null) (raw["accountEmail"] as? JsonPrimitive)?.contentOrNull else first.presentation.identity.email
        val suffix=Regex("(?:^|\\s(?:·\\s)?)(MiMo [a-f0-9]{7})$",RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1)
        when {suffix!=null->"suffix:${id(suffix)}";!email.isNullOrBlank()->"email:${id(email)}";name.isNotEmpty()->"name:${id(name)}";else->"row:${first.presentation.identity.key ?: index}"}
    }.values.map {group->group.map {it.value}}
}
