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
    val pinnedTools: Set<String> = emptySet(),
    val pinnedModels: Set<String> = emptySet(),
    val toolOrder: List<String> = emptyList(),
    val modelOrder: List<String> = emptyList(),
    val homeActivityMetric: String = "automatic",
    val homeActiveDays: String = "year",
    val contextRemaining: Boolean = false,
    val pinnedAccounts: Set<String> = emptySet(),
    val accountOrder: List<String> = emptyList(),
    val hiddenAccounts: Set<String> = emptySet(),
    val hiddenQuotaWindows: Set<String> = emptySet(),
    val interfaceFont: String = "mono",
    val displayFont: String = "system",
    val modelGrouping: String = "off",
    val currencyRates: Map<String, Double> = emptyMap(),
)
internal class DesktopPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("desktop_presentation", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val options = state.asStateFlow()
    init { active = state.value }
    fun save(value: DesktopOptions): Boolean {
        if (value.currency !in setOf("USD", "CNY", "HKD", "TWD") || !value.usdRate.isFinite() || value.usdRate <= 0) return false
        if (value.interfaceFont !in setOf("mono", "system") || value.displayFont !in setOf("mono", "system", "follow") || value.modelGrouping !in setOf("off", "duplicates", "prefix")) return false
        val saved = value.copy(currencyRates = value.currencyRates.filterValues { it.isFinite() && it > 0 } + (value.currency to value.usdRate))
        val encoded = buildJsonObject {
            put("pinnedTools", JsonArray(saved.pinnedTools.map(::JsonPrimitive)))
            put("pinnedModels", JsonArray(saved.pinnedModels.map(::JsonPrimitive)))
            put("toolOrder", JsonArray(saved.toolOrder.map(::JsonPrimitive)))
            put("modelOrder", JsonArray(saved.modelOrder.map(::JsonPrimitive)))
            put("homeActivityMetric", saved.homeActivityMetric); put("homeActiveDays", saved.homeActiveDays)
            put("contextRemaining", saved.contextRemaining)
            put("pinnedAccounts", JsonArray(saved.pinnedAccounts.map(::JsonPrimitive)))
            put("accountOrder", JsonArray(saved.accountOrder.map(::JsonPrimitive)))
            put("hiddenAccounts", JsonArray(saved.hiddenAccounts.map(::JsonPrimitive)))
            put("hiddenQuotaWindows", JsonArray(saved.hiddenQuotaWindows.map(::JsonPrimitive)))
            put("interfaceFont", saved.interfaceFont); put("displayFont", saved.displayFont); put("modelGrouping", saved.modelGrouping)
            put("rates", buildJsonObject { saved.currencyRates.forEach { (key, rate) -> if (rate.isFinite() && rate > 0) put(key, rate) } })
            put("currency", value.currency); put("rate", value.usdRate)
            put("aliases", buildJsonObject { value.modelAliases.forEach { (key, target) -> put(key, target) } })
            put("tools", JsonArray(value.hiddenTools.map(::JsonPrimitive)))
            put("models", JsonArray(value.hiddenModels.map(::JsonPrimitive)))
        }.toString()
        if (!prefs.edit().putString("options", encoded).commit()) return false
        state.value = saved; active = saved
        return true
    }
    private fun load(): DesktopOptions = runCatching {
        val data = Json.parseToJsonElement(prefs.getString("options", "{}")!!).jsonObject
        DesktopOptions(
            pinnedTools = data["pinnedTools"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty(),
            pinnedModels = data["pinnedModels"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty(),
            toolOrder = data["toolOrder"]?.jsonArray?.map { it.jsonPrimitive.content }?.distinct().orEmpty(),
            modelOrder = data["modelOrder"]?.jsonArray?.map { it.jsonPrimitive.content }?.distinct().orEmpty(),
            homeActivityMetric = data["homeActivityMetric"]?.jsonPrimitive?.content?.takeIf { it in setOf("automatic", "tokens", "cost") } ?: "automatic",
            homeActiveDays = data["homeActiveDays"]?.jsonPrimitive?.content?.takeIf { it in setOf("all", "year") } ?: "year",
            contextRemaining = data["contextRemaining"]?.jsonPrimitive?.booleanOrNull ?: false,
            pinnedAccounts = data["pinnedAccounts"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty(),
            accountOrder = data["accountOrder"]?.jsonArray?.map { it.jsonPrimitive.content }?.distinct().orEmpty(),
            hiddenAccounts = data["hiddenAccounts"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty(),
            hiddenQuotaWindows = data["hiddenQuotaWindows"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty(),
            interfaceFont = data["interfaceFont"]?.jsonPrimitive?.content?.takeIf { it in setOf("mono", "system") } ?: "mono",
            displayFont = data["displayFont"]?.jsonPrimitive?.content?.takeIf { it in setOf("mono", "system", "follow") } ?: "system",
            modelGrouping = data["modelGrouping"]?.jsonPrimitive?.content?.takeIf { it in setOf("off", "duplicates", "prefix") } ?: "off",
            currencyRates = data["rates"]?.jsonObject?.mapNotNull { (key, item) -> item.jsonPrimitive.doubleOrNull?.takeIf { it.isFinite() && it > 0 }?.let { key to it } }?.toMap().orEmpty(),            currency = data["currency"]?.jsonPrimitive?.content ?: "USD",
            usdRate = data["rate"]?.jsonPrimitive?.double ?: 1.0,
            modelAliases = data["aliases"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty(),
            hiddenTools = data["tools"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty(),
            hiddenModels = data["models"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty(),
        ).also { require(it.usdRate.isFinite() && it.usdRate > 0 && it.currency in setOf("USD", "CNY", "HKD", "TWD")) }
    }.getOrDefault(DesktopOptions())
    companion object { @Volatile var active = DesktopOptions(); private set }
}
