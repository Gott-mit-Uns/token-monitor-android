package io.github.theminionooo.tokenmonitor.data.storage

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*

/** Presentation only; never stores a connection or changes a Hub snapshot. */
internal data class DesktopOptions(
    val currency: String = "USD",
    val usdRate: Double = 1.0,
    val modelAliases: Map<String, String> = emptyMap(),
    val hiddenTools: Set<String> = emptySet(),
    val hiddenModels: Set<String> = emptySet(),
)
internal class DesktopPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("desktop_presentation", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val options = state.asStateFlow()
    init { active = state.value }
    fun save(value: DesktopOptions): Boolean {
        if (value.currency !in setOf("USD", "CNY", "HKD", "TWD") || !value.usdRate.isFinite() || value.usdRate <= 0) return false
        val encoded = buildJsonObject {
            put("currency", value.currency); put("rate", value.usdRate)
            put("aliases", buildJsonObject { value.modelAliases.forEach { (key, target) -> put(key, target) } })
            put("tools", JsonArray(value.hiddenTools.map(::JsonPrimitive)))
            put("models", JsonArray(value.hiddenModels.map(::JsonPrimitive)))
        }.toString()
        if (!prefs.edit().putString("options", encoded).commit()) return false
        state.value = value; active = value
        return true
    }
    private fun load(): DesktopOptions = runCatching {
        val data = Json.parseToJsonElement(prefs.getString("options", "{}")!!).jsonObject
        DesktopOptions(
            currency = data["currency"]?.jsonPrimitive?.content ?: "USD",
            usdRate = data["rate"]?.jsonPrimitive?.double ?: 1.0,
            modelAliases = data["aliases"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty(),
            hiddenTools = data["tools"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty(),
            hiddenModels = data["models"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty(),
        ).also { require(it.usdRate.isFinite() && it.usdRate > 0 && it.currency in setOf("USD", "CNY", "HKD", "TWD")) }
    }.getOrDefault(DesktopOptions())
    companion object { @Volatile var active = DesktopOptions(); private set }
}
