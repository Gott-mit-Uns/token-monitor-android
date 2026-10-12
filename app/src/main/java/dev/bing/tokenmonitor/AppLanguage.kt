package dev.bing.tokenmonitor

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

enum class LanguageChoice { CHINESE, ENGLISH, SYSTEM }

/** App-only resource scope; changing presentation never recreates the Activity or opens a connection. */
@Composable
fun AppLanguage(language: LanguageChoice, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val original = LocalConfiguration.current
    val tag = when (language) {
        LanguageChoice.CHINESE -> "zh"
        LanguageChoice.ENGLISH -> "en"
        LanguageChoice.SYSTEM -> if (original.locales[0].language == "zh") "zh" else "en"
    }
    val configuration = remember(original, tag) { Configuration(original).apply { setLocales(LocaleList.forLanguageTags(tag)) } }
    val localized = remember(base, configuration) { base.createConfigurationContext(configuration) }
    CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides configuration,
        LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}

/** Restore analysis state saved by pre-localization builds without using translated labels as keys. */
fun normalizeAnalysisKey(value: String): String = when (value) {
    "工具" -> "tools"; "模型" -> "models"; "项目" -> "projects"; "会话" -> "sessions"; "历史" -> "history"; "价格" -> "pricing"
    else -> value.takeIf { it in setOf("tools", "models", "projects", "sessions", "history", "pricing") } ?: "tools"
}
