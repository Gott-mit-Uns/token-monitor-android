package dev.bing.tokenmonitor

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily

internal val LocalDesktopVendorPalette=staticCompositionLocalOf { desktopVendorColors }
internal val LocalDesktopToolIcons=staticCompositionLocalOf { true }
internal val LocalDesktopChartColor=staticCompositionLocalOf { Color(0xFF73BDF5) }
internal val LocalDesktopChartBarColor=staticCompositionLocalOf { Color(0xFF6AB4F0) }
internal val LocalDesktopNumberColor=staticCompositionLocalOf { Color(0xFFF3FBF7) }
internal val LocalDesktopSuccessColor=staticCompositionLocalOf { Color(0xFFB7EAD4) }
internal val LocalDesktopPanelColor=staticCompositionLocalOf { Color(0xFF10151E) }
internal val LocalDesktopSunkenColor=staticCompositionLocalOf { Color(0xFF04080D) }
internal val LocalDesktopWarningColor=staticCompositionLocalOf { Color(0xFFF47788) }
internal val LocalDesktopTooltipColors=staticCompositionLocalOf { listOf(Color(0xFF404447),Color(0xFF35393D)) }
internal val LocalDesktopTooltipBorder=staticCompositionLocalOf { Color(0xFFE8EEF4).copy(alpha=0.22f) }
internal val LocalDesktopLineColor=staticCompositionLocalOf { Color(0xFFE8EEF4) }
internal val LocalDesktopOverlayColor=staticCompositionLocalOf { Color.White }
internal val LocalDesktopHeatColors=staticCompositionLocalOf { listOf(Color(0xFF5AAAFF),Color(0xFF78BEFF),Color(0xFF96D2FF),Color(0xFFB4E6FF)) }
internal val LocalDesktopTextScale=staticCompositionLocalOf { 1f }
internal val LocalDesktopDisplayFont=staticCompositionLocalOf<FontFamily> { FontFamily.SansSerif }
internal fun hexColor(value:String)=Color(0xFF000000L or value.drop(1).toLong(16))
@Composable internal fun renderedDesktopPalette(overrides:Map<String,String>):Map<String,String> {
    val bg=MaterialTheme.colorScheme.background
    val light=.299f*bg.red+.587f*bg.green+.114f*bg.blue>.6f
    return DesktopAppearance.palette(overrides,LocalDesktopBaseDark.current ?: !light)
}
@Composable internal fun effectiveTextScale() = LocalDensity.current.fontScale * LocalDesktopTextScale.current

internal fun nativeFont(value:String, fallback:FontFamily=FontFamily.Monospace):FontFamily {
    val normalized=DesktopAppearance.font(value)
    if(normalized.isEmpty()) return fallback
    if(normalized==DesktopAppearance.defaultInterfaceFont) return FontFamily.Monospace
    if(normalized==DesktopAppearance.defaultDisplayFont || normalized==DesktopAppearance.systemFont) return FontFamily.SansSerif
    for (name in DesktopFontFamilies.candidates(normalized)) {
        when(name.lowercase(java.util.Locale.ROOT)) {
            "serif" -> return FontFamily.Serif
            "monospace","ui-monospace" -> return FontFamily.Monospace
            "sans-serif","system-ui","-apple-system","roboto" -> return FontFamily.SansSerif
            else -> {
                // Android silently returns DEFAULT for an unavailable family. Keep
                // looking so a desktop-only family cannot swallow its fallbacks.
                val face=android.graphics.Typeface.create(name,android.graphics.Typeface.NORMAL)
                if(face != android.graphics.Typeface.DEFAULT) return FontFamily(face)
            }
        }
    }
    return fallback
}

/** Android surfaces are opaque; source palette/overlays retain their roles. */
internal fun desktopColorScheme(values:Map<String,String>):ColorScheme {
    val bg=hexColor(values.getValue("bg"));val text=hexColor(values.getValue("text"));val muted=hexColor(values.getValue("muted"));val accent=hexColor(values.getValue("accent"))
    val light=DesktopAppearance.light(values.getValue("bg"))
    val overlay=if(light) Color(0xFF0F1218) else Color.White
    val line=if(light) Color(0xFF181C24) else Color(0xFFE8EEF4)
    val panel=if(light) Color.White else Color(0xFF10151E)
    fun surface(alpha:Float)=overlay.copy(alpha=alpha).compositeOver(bg)
    return (if(light) lightColorScheme() else darkColorScheme()).copy(
        primary=accent,onPrimary=bg,primaryContainer=accent.copy(alpha=0.11f).compositeOver(bg),onPrimaryContainer=text,
        secondary=accent,onSecondary=bg,secondaryContainer=surface(0.05f),onSecondaryContainer=text,
        tertiary=if(light) Color(0xFF18794E) else Color(0xFFB7EAD4),onTertiary=bg,
        background=bg,surface=bg,onBackground=text,onSurface=text,onSurfaceVariant=muted,surfaceVariant=surface(0.05f),
        outline=line.copy(alpha=0.238f).compositeOver(bg),outlineVariant=line.copy(alpha=0.138f).compositeOver(bg),
        surfaceTint=accent,surfaceDim=bg,surfaceBright=surface(0.08f),
        surfaceContainerLowest=bg,surfaceContainerLow=surface(0.025f),surfaceContainer=panel,
        surfaceContainerHigh=surface(0.06f),surfaceContainerHighest=surface(0.08f)
    )
}
