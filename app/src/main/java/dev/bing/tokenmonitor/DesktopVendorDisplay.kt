package dev.bing.tokenmonitor

import androidx.compose.ui.graphics.Color
import java.util.Locale

internal fun toolId(raw: String): String = when (val value = raw.lowercase(Locale.ROOT)) {
    "claude code" -> "claude"; "gemini cli" -> "gemini"; "github copilot" -> "copilot"; "hermes-agent" -> "hermes"
    else -> value
}
internal fun toolLabel(raw: String): String = desktopToolLabels[toolId(raw)] ?: raw
internal fun providerLabel(raw: String): String = desktopProviderLabels[raw.lowercase(Locale.ROOT)] ?: raw
internal fun brandResource(raw: String): Int? = desktopVendorMarks[toolId(raw)]

internal fun toolColor(raw:String,palette:Map<String,Long> = desktopVendorColors):Color = Color(palette[toolId(raw)] ?: palette.getValue("default"))
internal fun providerColor(raw:String,palette:Map<String,Long> = desktopVendorColors):Color = toolColor(if(raw.lowercase(Locale.ROOT)=="factory") "droid" else raw,palette)
private val fallbackModelColors = listOf(0xFF6AB4F0L, 0xFF5FBF8AL, 0xFFA57DF0L, 0xFFD97BC4L, 0xFFF0D66AL, 0xFFF06A7BL)
internal fun modelColor(raw:String,palette:Map<String,Long> = desktopVendorColors):Color {
    modelVendor(raw)?.let {palette[it]}?.let {return Color(it)}
    val hash=raw.lowercase(Locale.ROOT).hashCode().toLong()
    return Color(fallbackModelColors[((if(hash<0) -hash else hash)%fallbackModelColors.size).toInt()])
}
