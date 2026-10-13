package dev.bing.tokenmonitor

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Offline OFL derivatives; missing glyphs retain Android's normal system fallback. */
internal object BundledFonts {
    val ui = FontFamily(Font(R.font.token_ui_regular, FontWeight.Normal),
        Font(R.font.token_ui_bold, FontWeight.Bold))
    val data = FontFamily(Font(R.font.token_data_regular, FontWeight.Normal),
        Font(R.font.token_data_bold, FontWeight.Bold))
}
internal val LocalDesktopDataFont = staticCompositionLocalOf { BundledFonts.data }
