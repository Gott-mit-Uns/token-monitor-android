package dev.bing.tokenmonitor

import kotlinx.serialization.json.*

/** Renderer-only visibility and group order from fixed desktop v0.76.0. */
internal object AntigravityQuota {
    fun group(kind:String?,label:String?):String? {
        val suffix=when(kind) {"session"->"\\s+5-hour$";"weekly"->"\\s+weekly$";else->return null}
        val text=label?.trim() ?: return null
        val pattern=Regex(suffix,RegexOption.IGNORE_CASE)
        return if(pattern.containsMatchIn(text)) text.replace(pattern,"").trim().takeIf {it.isNotEmpty()} else null
    }
    fun visible(w:Limit)=group(w.kind,w.label)!=null || w.remaining?.isFinite()==true || w.presentation.usedPercent?.isFinite()==true
    fun visible(w:JsonObject):Boolean {
        fun string(key:String)=(w[key] as? JsonPrimitive)?.contentOrNull
        fun finite(key:String)=(w[key] as? JsonPrimitive)?.takeIf {!it.isString}?.doubleOrNull?.isFinite()==true
        return group(string("kind"),string("label"))!=null || finite("remainingPercent") || finite("usedPercent")
    }
    fun rank(group:String?)=listOf("Gemini","Claude/GPT","Gemini 4 Argon").indexOf(group).takeIf {it>=0} ?: 3
}
