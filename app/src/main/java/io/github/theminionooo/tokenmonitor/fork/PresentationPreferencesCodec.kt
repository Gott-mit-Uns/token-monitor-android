package io.github.theminionooo.tokenmonitor.fork

import io.github.theminionooo.tokenmonitor.data.storage.DesktopOptions
import kotlinx.serialization.json.*

/** Local extension boundary. Legacy storage keys remain stable for existing installations. */
internal object PresentationPreferencesCodec {
    private val currencies = setOf("USD", "CNY", "HKD", "TWD")
    fun normalized(value: DesktopOptions): DesktopOptions? {
        if (value.currency !in currencies || !value.usdRate.isFinite() || value.usdRate <= 0) return null
        if (value.interfaceFont !in setOf("mono", "system") || value.displayFont !in setOf("mono", "system", "follow") || value.modelGrouping !in setOf("off", "duplicates", "prefix")) return null
        if (value.homeActivityMetric !in setOf("automatic", "tokens", "cost") || value.homeActiveDays !in setOf("all", "year")) return null
        return value.copy(currencyRates = value.currencyRates.filter { (k,v) -> k in currencies && v.isFinite() && v > 0 } + (value.currency to value.usdRate), toolOrder = value.toolOrder.distinct(), modelOrder = value.modelOrder.distinct(), accountOrder = value.accountOrder.distinct())
    }
    fun encode(v: DesktopOptions): String = buildJsonObject {
        fun list(key: String, items: Iterable<String>) { put(key, JsonArray(items.map(::JsonPrimitive))) }
        list("pinnedTools",v.pinnedTools);list("pinnedModels",v.pinnedModels);list("toolOrder",v.toolOrder);list("modelOrder",v.modelOrder)
        list("pinnedAccounts",v.pinnedAccounts);list("accountOrder",v.accountOrder);list("hiddenAccounts",v.hiddenAccounts);list("hiddenQuotaWindows",v.hiddenQuotaWindows)
        list("tools",v.hiddenTools);list("models",v.hiddenModels)
        put("homeActivityMetric",v.homeActivityMetric);put("homeActiveDays",v.homeActiveDays);put("contextRemaining",v.contextRemaining)
        put("interfaceFont",v.interfaceFont);put("displayFont",v.displayFont);put("modelGrouping",v.modelGrouping)
        put("currency",v.currency);put("rate",v.usdRate)
        put("rates",buildJsonObject { v.currencyRates.forEach { (k,rate) -> put(k,rate) } })
        put("aliases",buildJsonObject { v.modelAliases.forEach { (k,target) -> put(k,target) } })
    }.toString()
    fun decode(raw: String?): DesktopOptions {
        val d = runCatching { Json.parseToJsonElement(raw ?: "{}").jsonObject }.getOrDefault(JsonObject(emptyMap()))
        fun text(key: String) = (d[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        fun choice(key: String, choices: Set<String>, fallback: String) = text(key)?.takeIf { it in choices } ?: fallback
        fun list(key: String) = (d[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { it.isString }?.content }?.distinct().orEmpty()
        val rates = (d["rates"] as? JsonObject)?.mapNotNull { (k,v) -> (v as? JsonPrimitive)?.doubleOrNull?.takeIf { k in currencies && it.isFinite() && it > 0 }?.let { k to it } }?.toMap().orEmpty()
        val selectedCurrency = choice("currency",currencies,"USD")
        val savedRate = (d["rate"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() && it > 0 } ?: rates[selectedCurrency]
        // Never invent a 1:1 FX rate after corruption. Restore USD if no valid conversion remains.
        val currency = if (selectedCurrency != "USD" && savedRate == null) "USD" else selectedCurrency
        val rate = savedRate ?: 1.0
        return DesktopOptions(
            currency=currency,usdRate=rate,currencyRates=rates,
            modelAliases=(d["aliases"] as? JsonObject)?.mapNotNull { (k,v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { k to it } }?.toMap().orEmpty(),
            hiddenTools=list("tools").toSet(),hiddenModels=list("models").toSet(),
            pinnedTools=list("pinnedTools").toSet(),pinnedModels=list("pinnedModels").toSet(),toolOrder=list("toolOrder"),modelOrder=list("modelOrder"),
            pinnedAccounts=list("pinnedAccounts").toSet(),accountOrder=list("accountOrder"),hiddenAccounts=list("hiddenAccounts").toSet(),hiddenQuotaWindows=list("hiddenQuotaWindows").toSet(),
            homeActivityMetric=choice("homeActivityMetric",setOf("automatic","tokens","cost"),"automatic"),homeActiveDays=choice("homeActiveDays",setOf("all","year"),"year"),
            contextRemaining=(d["contextRemaining"] as? JsonPrimitive)?.booleanOrNull ?: false,
            interfaceFont=choice("interfaceFont",setOf("mono","system"),"mono"),displayFont=choice("displayFont",setOf("mono","system","follow"),"system"),modelGrouping=choice("modelGrouping",setOf("off","duplicates","prefix"),"off"),
        )
    }
}
