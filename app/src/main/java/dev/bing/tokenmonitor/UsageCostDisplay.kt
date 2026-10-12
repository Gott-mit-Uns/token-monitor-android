package dev.bing.tokenmonitor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalResources
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale

internal val LocalUsageCurrency = staticCompositionLocalOf { UsageCurrency() }
internal data class UsageCostLanguage(val locale: Locale, val unpricedLabel: String)
internal val LocalUsageCostLanguage = staticCompositionLocalOf<UsageCostLanguage?> { null }

@Composable internal fun usageCostText(value: String?, unpriced: Long? = null, compact: Boolean = false): String =
    usageCostText(value, unpriced?.let(BigInteger::valueOf), compact)

@Composable internal fun usageCostText(value: String?, unpriced: BigInteger?, compact: Boolean = false): String {
    val strings = LocalResources.current
    // Android Dialog creates another resource context. Keep the selected app
    // language across that window rather than falling back to the system locale.
    val language = LocalUsageCostLanguage.current
    val numbers = NumberFormat.getIntegerInstance(language?.locale ?: strings.configuration.locales[0])
    return LocalUsageCurrency.current.label(value, unpriced, compact, { numbers.format(it) }, language?.unpricedLabel ?: strings.getString(R.string.usage_unpriced_label))
}
