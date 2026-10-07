package io.github.theminionooo.tokenmonitor.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Desktop theme codes retain TM1's four colors; TM2 appends an independent chart color.
 */
internal data class InterfaceTheme(
    val accent: String,
    val bg: String,
    val text: String,
    val muted: String,
    val chart: String = "#73bdf5",
) {
    /** Perceived brightness above 0.6 flips the overlay and border system, as on the desktop. */
    val isLight: Boolean get() = luminance(bg) > 0.6

    val code: String get() {
        val customChart = !chart.equals("#73bdf5", ignoreCase = true)
        val colors = listOf(accent, bg, text, muted) + if (customChart) listOf(chart) else emptyList()
        return colors.joinToString("-", prefix = if (customChart) "TM2-" else "TM1-") { it.removePrefix("#").uppercase(Locale.US) }
    }

    companion object {
        val Default = InterfaceTheme(accent = "#b7ead4", bg = "#303438", text = "#eef5fb", muted = "#a3adbb")
        val Obsidian = InterfaceTheme(accent = "#e6e8ec", bg = "#0b0c0e", text = "#eceef2", muted = "#8f949c")
        val Porcelain = InterfaceTheme(accent = "#2563eb", bg = "#f6f7f9", text = "#1c1f26", muted = "#5b626d")

        val presets: Map<String, InterfaceTheme> = linkedMapOf("default" to Default, "obsidian" to Obsidian, "porcelain" to Porcelain)

        private val codePattern = Regex("^TM([12])-([0-9a-f]{6})-([0-9a-f]{6})-([0-9a-f]{6})-([0-9a-f]{6})(?:-([0-9a-f]{6}))?$", RegexOption.IGNORE_CASE)

        /** TM1 resets chart color to default; unknown versions and wrong field counts are refused. */
        fun fromCode(value: String): InterfaceTheme? {
            val match = codePattern.matchEntire(value.trim()) ?: return null
            val (version, accent, bg, text, muted, chart) = match.destructured
            if ((version == "2") != chart.isNotEmpty()) return null
            return InterfaceTheme("#${accent.lowercase(Locale.US)}", "#${bg.lowercase(Locale.US)}", "#${text.lowercase(Locale.US)}", "#${muted.lowercase(Locale.US)}",
                if (chart.isEmpty()) "#73bdf5" else "#${chart.lowercase(Locale.US)}")
        }

        /** The preset id whose colors equal [theme], or `custom`. */
        fun idOf(theme: InterfaceTheme): String = presets.entries.firstOrNull { it.value == theme }?.key ?: "custom"

        fun luminance(hex: String): Double {
            val v = hex.removePrefix("#")
            if (v.length != 6) return 0.0
            val r = v.substring(0, 2).toInt(16)
            val g = v.substring(2, 4).toInt(16)
            val b = v.substring(4, 6).toInt(16)
            return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        }
    }
}

/**
 * Every color the dashboard draws, resolved from an [InterfaceTheme]. Mirrors the
 * desktop stylesheet: the four theme colors drive the glass, text, and accent; the
 * overlay, line, panel, and sunken surfaces flip between light-on-dark and
 * dark-on-light; semantic colors stay fixed so their meaning survives any accent.
 */
internal data class Palette(
    val theme: InterfaceTheme,
    val isLight: Boolean,
    val shell: Color,
    val gradientTop: Color,
    val gradientBottom: Color,
    val recessed: Color,
    val sunken: Color,
    val overlay: Color,
    val line: Color,
    val strongLine: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val success: Color,
    val blue: Color,
    val chart: Color,
    val orange: Color,
    val purple: Color,
    val yellow: Color,
    val danger: Color,
    /** Five-step activity ramp, index 0 is an inactive day. */
    val heat: List<Color>,
) {
    companion object {
        fun from(theme: InterfaceTheme): Palette {
            val light = theme.isLight
            val shell = hex(theme.bg)
            val ink = hex(theme.text)
            val overlayBase = if (light) Color(0xFF0F1218) else Color.White
            val lineBase = if (light) Color(0xFF181C24) else Color(0xFFE8EEF4)
            val blue = Color(0xFF73BDF5)
            return Palette(
                theme = theme,
                isLight = light,
                shell = shell,
                gradientTop = if (light) lerp(shell, Color.White, 0.5f) else lerp(shell, Color(0xFF6B8AB8), 0.16f),
                gradientBottom = if (light) lerp(shell, Color.Black, 0.05f) else lerp(shell, Color.Black, 0.45f),
                recessed = if (light) Color.White else Color(0xFF10151E),
                sunken = if (light) Color(0xFFBCC4CE) else Color(0xFF04080D),
                overlay = overlayBase.copy(alpha = 0.05f),
                line = lineBase.copy(alpha = 0.138f),
                strongLine = lineBase.copy(alpha = 0.238f),
                ink = ink,
                muted = hex(theme.muted),
                accent = hex(theme.accent),
                success = if (light) Color(0xFF18794E) else Color(0xFFB7EAD4),
                blue = blue,
                chart = if (theme.chart.equals("#73bdf5", ignoreCase = true)) blue else hex(theme.chart),
                orange = Color(0xFFF4A073),
                purple = Color(0xFFB394F4),
                yellow = Color(0xFFF1D973),
                danger = Color(0xFFF47788),
                heat = if (!theme.chart.equals("#73bdf5", ignoreCase = true)) {
                    val chart = hex(theme.chart)
                    listOf(if (light) Color(0xFFE3E7EC) else lerp(shell, Color.Black, 0.35f)) +
                        (0..3).map { level -> lerp(chart, Color.White, level * 0.18f) }
                } else if (light) {
                    listOf(Color(0xFFE3E7EC), lerp(Color(0xFFE3E7EC), blue, 0.3f), lerp(Color(0xFFE3E7EC), blue, 0.55f), lerp(Color(0xFFE3E7EC), blue, 0.8f), Color(0xFF2E7BD6))
                } else {
                    listOf(lerp(shell, Color.Black, 0.35f), Color(0xFF2E4645), Color(0xFF46706A), Color(0xFF6FA79B), Color(0xFFA9D9C8))
                },
            )
        }

        private fun hex(value: String): Color {
            val v = value.removePrefix("#")
            return Color(0xFF000000.toInt() or v.toLong(16).toInt())
        }
    }
}

/**
 * The theme to draw with. A saved code (or the Default preset) is the base. With follow-system on,
 * the phone's light mode takes Porcelain unless the base is already light, and its dark mode keeps
 * the base unless the base is light, in which case Default stands in. Two dark presets stay
 * possible that way: whichever one is chosen is the one night mode uses.
 */
internal fun resolveInterfaceTheme(themeCode: String?, followSystem: Boolean, systemDark: Boolean): InterfaceTheme {
    val base = themeCode?.let(InterfaceTheme::fromCode) ?: InterfaceTheme.Default
    if (!followSystem) return base
    return when {
        systemDark && base.isLight -> InterfaceTheme.Default
        !systemDark && !base.isLight -> InterfaceTheme.Porcelain
        else -> base
    }
}

internal val LocalPalette = staticCompositionLocalOf { Palette.from(InterfaceTheme.Default) }

/* The desktop renderer's glass, recessed-panel, line, and semantic color tokens. */
internal val Shell: Color @Composable get() = LocalPalette.current.shell
internal val Recessed: Color @Composable get() = LocalPalette.current.recessed
internal val Sunken: Color @Composable get() = LocalPalette.current.sunken
internal val Overlay: Color @Composable get() = LocalPalette.current.overlay
internal val Line: Color @Composable get() = LocalPalette.current.line
internal val StrongLine: Color @Composable get() = LocalPalette.current.strongLine
internal val Ink: Color @Composable get() = LocalPalette.current.ink
internal val Muted: Color @Composable get() = LocalPalette.current.muted
internal val Accent: Color @Composable get() = LocalPalette.current.accent
internal val Blue: Color @Composable get() = LocalPalette.current.blue
internal val Orange: Color @Composable get() = LocalPalette.current.orange
internal val Purple: Color @Composable get() = LocalPalette.current.purple
internal val Yellow: Color @Composable get() = LocalPalette.current.yellow
internal val Danger: Color @Composable get() = LocalPalette.current.danger
internal val Success: Color @Composable get() = LocalPalette.current.success

internal val LocalColorfulToolMarks = staticCompositionLocalOf { false }
internal val LocalToolIcons = staticCompositionLocalOf { true }
internal val LocalInteractionMotion = staticCompositionLocalOf { true }

/** The desktop renderer's `cubic-bezier(0.22, 1, 0.36, 1)`: a quick start that settles softly. */
internal val DesktopEaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

@Composable
internal fun quotaColor(used: Float): Color = when {
    used >= 0.85f -> Danger
    used >= 0.65f -> Orange
    else -> Success
}

/** Uses the vendor color when known; otherwise chooses a stable fallback accent. */
@Composable
internal fun accentFor(name: String): Color {
    val hashed = listOf(Success, Blue, Orange, Purple, Yellow)[name.hashCode().absoluteValue % 5]
    return originalToolColor(name, hashed)
}

internal fun tokenMonitorColors(palette: Palette): ColorScheme {
    val base = if (palette.isLight) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = palette.accent,
        onPrimary = if (palette.isLight) Color.White else palette.recessed,
        primaryContainer = palette.accent.copy(alpha = 0.15f),
        secondary = palette.blue,
        background = palette.shell,
        onBackground = palette.ink,
        surface = palette.shell,
        onSurface = palette.ink,
        surfaceVariant = palette.recessed,
        onSurfaceVariant = palette.muted,
        surfaceTint = palette.accent,
        surfaceDim = palette.recessed,
        surfaceBright = palette.overlay.compositeOver(palette.shell),
        surfaceContainerLowest = palette.sunken,
        surfaceContainerLow = palette.recessed,
        surfaceContainer = palette.shell,
        surfaceContainerHigh = palette.overlay.compositeOver(palette.shell),
        surfaceContainerHighest = palette.recessed,
        onPrimaryContainer = palette.ink,
        secondaryContainer = palette.recessed,
        onSecondaryContainer = palette.ink,
        outlineVariant = palette.line,
        outline = palette.strongLine,
        error = palette.danger,
    )
}

/** Desktop type sizes at [step] 0; each step adds one point to every style and two to the headline total. */
internal fun tokenMonitorTypography(step: Int): Typography = Typography(
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = (42 + 2 * step).sp, lineHeight = (46 + 2 * step).sp, fontFeatureSettings = "tnum"),
    titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (20 + step).sp, lineHeight = (26 + step).sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (16 + step).sp, lineHeight = (20 + step).sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (13 + step).sp, lineHeight = (18 + step).sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (12 + step).sp, lineHeight = (17 + step).sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (11 + step).sp, lineHeight = (16 + step).sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (12 + step).sp, lineHeight = (16 + step).sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (11 + step).sp, lineHeight = (15 + step).sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (11 + step).sp, lineHeight = (14 + step).sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (9 + step).sp, lineHeight = (12 + step).sp),
)
