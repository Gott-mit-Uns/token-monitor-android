package dev.bing.tokenmonitor

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun TokenTheme(dark: Boolean = isSystemInDarkTheme(), language: LanguageChoice = LanguageChoice.CHINESE, appearance: AppearancePreferences = AppearancePreferences(), content: @Composable () -> Unit) {
    AppLanguage(language) {
    val palette=androidx.compose.runtime.remember(dark,appearance.colors) { DesktopAppearance.palette(appearance.colors,dark) }
    val numberColor=androidx.compose.runtime.remember(dark,appearance.colors) { hexColor(DesktopAppearance.numberColor(appearance.colors,dark)) }
    val font=androidx.compose.runtime.remember(appearance.interfaceFont) { nativeFont(appearance.interfaceFont) }
    val displayFont=androidx.compose.runtime.remember(appearance.displayFont,font) { nativeFont(appearance.displayFont,font) }
    val scale=DesktopAppearance.scale(appearance.textSize)
    val light=DesktopAppearance.light(palette.getValue("bg"))
    val motionScale=rememberAndroidMotionScale()
    val tooltipOverlay=if(light) Color(0xFF0F1218) else Color.White
    MaterialTheme(colorScheme = desktopColorScheme(palette), typography = desktopTypography(font,scale)) {
        androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            LocalDesktopBaseDark provides dark,LocalDesktopBaseLanguage provides language,
            LocalDesktopChartColor provides hexColor(palette.getValue("chart")),
            LocalDesktopChartBarColor provides hexColor(DesktopAppearance.chartBarColor(palette.getValue("chart"))),
            LocalDesktopNumberColor provides numberColor,
            LocalDesktopSuccessColor provides if(light) Color(0xFF18794E) else Color(0xFFB7EAD4),
            LocalDesktopSystemMotionScale provides motionScale,
            LocalDesktopMotionScale provides DesktopMotionPreference.scale(appearance.reduceMotion,motionScale),
            LocalDesktopPanelColor provides if(light) Color.White else Color(0xFF10151E),
            LocalDesktopSunkenColor provides if(DesktopAppearance.light(palette.getValue("bg"))) Color(0xFFBCC4CE) else Color(0xFF04080D),
            LocalDesktopTooltipColors provides listOf(0.075f,0.025f).map {tooltipOverlay.copy(alpha=it).compositeOver(hexColor(palette.getValue("bg")))},
            LocalDesktopTooltipBorder provides (if(light) Color(0xFF181C24) else Color(0xFFE8EEF4)).copy(alpha=0.22f),
            LocalDesktopOverlayColor provides tooltipOverlay,
            LocalDesktopLineColor provides if(light) Color(0xFF181C24) else Color(0xFFE8EEF4),
            LocalDesktopHeatColors provides DesktopAppearance.heatColors(palette.getValue("chart")).map(::hexColor),
            LocalDesktopTextScale provides scale,LocalDesktopDisplayFont provides displayFont) { content() }
    }
    }
}

/* Desktop labels/data text scale independently of the Token total and icon sizes. */
private fun desktopTypography(font:FontFamily,scale:Float):Typography {
    fun style(size:Int,height:Int,weight:FontWeight=FontWeight.Normal)=TextStyle(fontFamily=font,fontWeight=weight,fontSize=(size*scale).sp,lineHeight=(height*scale).sp)
    return Typography(
        displaySmall=style(36,42,FontWeight.Bold),headlineMedium=style(24,30,FontWeight.Bold),
        titleLarge=style(18,24,FontWeight.Bold),titleMedium=style(15,22,FontWeight.SemiBold),
        bodyLarge=style(15,22),bodyMedium=style(14,21),bodySmall=style(12,18),
        labelLarge=style(13,18,FontWeight.SemiBold),labelMedium=style(12,18),labelSmall=style(11,16)
    )
}
