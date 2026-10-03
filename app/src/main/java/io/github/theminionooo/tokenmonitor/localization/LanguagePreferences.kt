package io.github.theminionooo.tokenmonitor.localization

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** Local presentation choice only; Hub data and enum persistence keys are unaffected. */
object LanguagePreferences {
    private val mutableMode = MutableStateFlow("system")
    val mode: StateFlow<String> = mutableMode
    private var applicationContext: Context? = null

    @Synchronized fun initialize(context: Context) {
        if (applicationContext != null) return
        applicationContext = context.applicationContext
        mutableMode.value = context.getSharedPreferences("language_preferences", Context.MODE_PRIVATE)
            .getString("language", "system")?.takeIf { it in setOf("system", "zh-CN", "en") } ?: "system"
    }

    fun set(context: Context, mode: String): Boolean {
        require(mode in setOf("system", "zh-CN", "en"))
        initialize(context)
        if (!context.getSharedPreferences("language_preferences", Context.MODE_PRIVATE).edit().putString("language", mode).commit()) return false
        mutableMode.value = mode
        kotlin.concurrent.thread(name = "widget-language", isDaemon = true) {
            runCatching { io.github.theminionooo.tokenmonitor.widget.WidgetUpdateCoordinator.refresh(context.applicationContext) }
        }
        return true
    }

    internal fun context(): Context? = applicationContext
}

fun localizedContext(context: Context): Context {
    LanguagePreferences.initialize(context)
    val mode = LanguagePreferences.mode.value
    if (mode == "system" || context.resources.configuration.locales[0].toLanguageTag() == mode) return context
    val configuration = Configuration(context.resources.configuration)
    configuration.setLocales(LocaleList(Locale.forLanguageTag(mode)))
    return context.createConfigurationContext(configuration)
}
