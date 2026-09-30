package io.github.theminionooo.tokenmonitor.data.storage

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class RankingMetric { Tokens, Cost }
internal enum class LimitBarMetric { Remaining, Used }
internal enum class ReduceMotionMode { System, On, Off }

/** Mirrors the desktop Zoom control in three steps; [step] is added to every type size in sp. */
internal enum class TextScale(val step: Int) { Compact(0), Comfortable(1), Large(2) }

/** Mobile-owned presentation choices. Hub credentials remain in [SecureConnectionStore]. */
internal data class DisplayOptions(
    val colorfulToolMarks: Boolean = false,
    val compactTokenTotal: Boolean = false,
    val reduceMotion: ReduceMotionMode = ReduceMotionMode.System,
    val textScale: TextScale = TextScale.Comfortable,
    /** A desktop `TM1-…` theme code; null means the default preset. */
    val themeCode: String? = null,
    /** Porcelain while the phone is in light mode and the chosen dark preset at night. */
    val followSystemTheme: Boolean = false,
    val showLiveIndicator: Boolean = true,
    val showToolIcons: Boolean = true,
    val rankingMetric: RankingMetric = RankingMetric.Tokens,
    val showLimitSource: Boolean = false,
    val showAccountEmails: Boolean = false,
    val limitBarMetric: LimitBarMetric = LimitBarMetric.Remaining,
    val defaultPeriod: String = "Today",
    val visibleViews: List<String> = defaultViews,
    val visibleHomeModules: List<String> = defaultHomeModules,
) {
    companion object {
        val defaultViews = listOf("Home", "Tools", "Status", "Devices", "Models", "Projects", "Sessions", "Limits", "Trends")
        val defaultHomeModules = listOf("Limits", "Tools", "Models", "Sessions", "Devices", "Activity")
    }
}

/** Stores non-sensitive display choices locally. Pairing secrets are never stored here. */
internal class DisplayPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    private val _options = MutableStateFlow(load())
    val options: StateFlow<DisplayOptions> = _options.asStateFlow()

    fun setColorfulToolMarks(enabled: Boolean) = update { copy(colorfulToolMarks = enabled) }
    fun setCompactTokenTotal(enabled: Boolean) = update { copy(compactTokenTotal = enabled) }
    fun setReduceMotion(mode: ReduceMotionMode) = update { copy(reduceMotion = mode) }
    fun setTextScale(scale: TextScale) = update { copy(textScale = scale) }
    fun setThemeCode(code: String?) = update { copy(themeCode = code) }
    fun setFollowSystemTheme(enabled: Boolean) = update { copy(followSystemTheme = enabled) }
    fun setShowLiveIndicator(enabled: Boolean) = update { copy(showLiveIndicator = enabled) }
    fun setShowToolIcons(enabled: Boolean) = update { copy(showToolIcons = enabled) }
    fun setRankingMetric(metric: RankingMetric) = update { copy(rankingMetric = metric) }
    fun setShowLimitSource(enabled: Boolean) = update { copy(showLimitSource = enabled) }
    fun setShowAccountEmails(enabled: Boolean) = update { copy(showAccountEmails = enabled) }
    fun setLimitBarMetric(metric: LimitBarMetric) = update { copy(limitBarMetric = metric) }
    fun setDefaultPeriod(period: String) = update { copy(defaultPeriod = period) }

    fun setViewVisible(view: String, visible: Boolean) = update {
        val next = toggleOrderedValue(visibleViews, view, visible, DisplayOptions.defaultViews)
        copy(visibleViews = if ("Home" in next) next else listOf("Home") + next)
    }

    fun setHomeModuleVisible(module: String, visible: Boolean) = update {
        copy(visibleHomeModules = toggleOrderedValue(visibleHomeModules, module, visible, DisplayOptions.defaultHomeModules))
    }

    fun moveView(view: String, offset: Int) = update { copy(visibleViews = visibleViews.move(view, offset)) }
    fun moveHomeModule(module: String, offset: Int) = update { copy(visibleHomeModules = visibleHomeModules.move(module, offset)) }

    private fun update(block: DisplayOptions.() -> DisplayOptions) {
        val next = _options.value.block()
        preferences.edit {
            putBoolean(colorfulToolMarksKey, next.colorfulToolMarks)
            putBoolean(compactTokenTotalKey, next.compactTokenTotal)
            putString(reduceMotionKey, next.reduceMotion.name)
            putString(textScaleKey, next.textScale.name)
            putString(themeCodeKey, next.themeCode)
            putBoolean(followSystemThemeKey, next.followSystemTheme)
            putBoolean(showLiveIndicatorKey, next.showLiveIndicator)
            putBoolean(showToolIconsKey, next.showToolIcons)
            putString(rankingMetricKey, next.rankingMetric.name)
            putBoolean(showLimitSourceKey, next.showLimitSource)
            putBoolean(showAccountEmailsKey, next.showAccountEmails)
            putString(limitBarMetricKey, next.limitBarMetric.name)
            putString(defaultPeriodKey, next.defaultPeriod)
            putString(visibleViewsKey, next.visibleViews.joinToString(","))
            putString(visibleHomeModulesKey, next.visibleHomeModules.joinToString(","))
        }
        _options.value = next
    }

    private fun load(): DisplayOptions = DisplayOptions(
        colorfulToolMarks = preferences.getBoolean(colorfulToolMarksKey, false),
        compactTokenTotal = preferences.getBoolean(compactTokenTotalKey, false),
        reduceMotion = preferences.getString(reduceMotionKey, null).enumOrDefault(ReduceMotionMode.System),
        textScale = preferences.getString(textScaleKey, null).enumOrDefault(TextScale.Comfortable),
        themeCode = preferences.getString(themeCodeKey, null)?.takeIf { it.isNotBlank() },
        followSystemTheme = preferences.getBoolean(followSystemThemeKey, false),
        showLiveIndicator = preferences.getBoolean(showLiveIndicatorKey, true),
        showToolIcons = preferences.getBoolean(showToolIconsKey, true),
        rankingMetric = preferences.getString(rankingMetricKey, null).enumOrDefault(RankingMetric.Tokens),
        showLimitSource = preferences.getBoolean(showLimitSourceKey, false),
        showAccountEmails = preferences.getBoolean(showAccountEmailsKey, false),
        limitBarMetric = preferences.getString(limitBarMetricKey, null).enumOrDefault(LimitBarMetric.Remaining),
        defaultPeriod = preferences.getString(defaultPeriodKey, "Today") ?: "Today",
        visibleViews = preferences.getString(visibleViewsKey, null).orderedValues(DisplayOptions.defaultViews),
        visibleHomeModules = preferences.getString(visibleHomeModulesKey, null).orderedValues(DisplayOptions.defaultHomeModules),
    )

    private companion object {
        const val fileName = "display_preferences"
        const val colorfulToolMarksKey = "colorful_tool_marks"
        const val compactTokenTotalKey = "compact_token_total"
        const val reduceMotionKey = "reduce_motion"
        const val textScaleKey = "text_scale"
        const val themeCodeKey = "theme_code"
        const val followSystemThemeKey = "follow_system_theme"
        const val showLiveIndicatorKey = "show_live_indicator"
        const val showToolIconsKey = "show_tool_icons"
        const val rankingMetricKey = "ranking_metric"
        const val showLimitSourceKey = "show_limit_source"
        const val showAccountEmailsKey = "show_account_emails"
        const val limitBarMetricKey = "limit_bar_metric"
        const val defaultPeriodKey = "default_period"
        const val visibleViewsKey = "visible_views"
        const val visibleHomeModulesKey = "visible_home_modules"
    }
}

private inline fun <reified T : Enum<T>> String?.enumOrDefault(default: T): T =
    enumValues<T>().firstOrNull { it.name == this } ?: default

private fun String?.orderedValues(defaults: List<String>): List<String> {
    if (this == null) return defaults
    val stored = orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
    return stored.filter { it in defaults }.distinct()
}

internal fun toggleOrderedValue(
    current: List<String>,
    value: String,
    enabled: Boolean,
    allowed: List<String>,
): List<String> = when {
    enabled && value !in current -> current + value
    !enabled -> current - value
    else -> current
}.filter { it in allowed }.distinct()

private fun List<String>.move(value: String, offset: Int): List<String> {
    val from = indexOf(value)
    if (from < 0 || isEmpty()) return this
    val to = (from + offset).coerceIn(0, lastIndex)
    if (from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
