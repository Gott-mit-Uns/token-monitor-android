package io.github.theminionooo.tokenmonitor.data.storage

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        val saved = io.github.theminionooo.tokenmonitor.fork.PresentationPreferencesCodec.normalized(value) ?: return false
        val encoded = io.github.theminionooo.tokenmonitor.fork.PresentationPreferencesCodec.encode(saved)
        if (!prefs.edit().putString("options", encoded).commit()) return false
        state.value = saved; active = saved
        return true
    }
    private fun load(): DesktopOptions = io.github.theminionooo.tokenmonitor.fork.PresentationPreferencesCodec.decode(runCatching { prefs.getString("options", null) }.getOrNull())
    companion object { @Volatile var active = DesktopOptions(); private set }
}
