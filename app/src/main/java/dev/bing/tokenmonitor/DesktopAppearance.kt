package dev.bing.tokenmonitor

/** Presentation only. Fixed desktop themePresets/fontSettings semantics, no remote settings. */
data class AppearancePreferences(val colors: Map<String,String> = emptyMap(), val textSize: String = "standard",
    val interfaceFont: String = "", val displayFont: String = DesktopAppearance.defaultDisplayFont,
    val vendorColors: Map<String,String> = emptyMap(), val showToolIcons: Boolean = true, val showLiveDot: Boolean = true,
    val reduceMotion:String = "system")

internal data class ThemeCodeResult(val colors: Map<String,String>? = null, val reason: String? = null)

internal object DesktopAppearance {
    val colorKeys = listOf("accent","chart","bg","text","muted")
    private val codeKeys = listOf("accent","bg","text","muted")
    val sizes = listOf("standard","larger","largest")
    const val defaultInterfaceFont = "ui-monospace, SFMono-Regular, Menlo, Consolas, \"Liberation Mono\", monospace"
    const val defaultDisplayFont = "-apple-system, BlinkMacSystemFont, \"SF Pro Display\", \"Segoe UI\", sans-serif"
    const val systemFont = "system-ui, -apple-system, BlinkMacSystemFont, \"Segoe UI\", sans-serif"
    const val defaultNumberColor = "#f3fbf7"
    val presets = linkedMapOf(
        "default" to linkedMapOf("accent" to "#b7ead4","chart" to "#73bdf5","bg" to "#303438","text" to "#eef5fb","muted" to "#a3adbb"),
        "obsidian" to linkedMapOf("accent" to "#e6e8ec","chart" to "#73bdf5","bg" to "#0b0c0e","text" to "#eceef2","muted" to "#8f949c"),
        "porcelain" to linkedMapOf("accent" to "#2563eb","chart" to "#73bdf5","bg" to "#f6f7f9","text" to "#1c1f26","muted" to "#5b626d")
    )
    fun hex(value: String?): String? = value?.trim()?.takeIf { Regex("#[0-9a-fA-F]{6}").matches(it) }?.lowercase(java.util.Locale.ROOT)
    fun cleanColors(values: Map<String,String>) = values.mapNotNull { (key,value) -> if(key in colorKeys) hex(value)?.let { key to it } else null }.toMap()
    // Desktop mirrors an explicitly present text override onto --number. An
    // absent text key falls back to the stylesheet's separate number colour.
    // Android's existing light base retains its readable porcelain ink.
    fun numberColor(overrides: Map<String,String>, dark: Boolean = true): String = cleanColors(overrides)["text"]
        ?: if(dark) defaultNumberColor else presets.getValue("porcelain").getValue("text")
    fun presetOverrides(id: String): Map<String,String>? = presets[id]?.filter { (key,value) -> value != presets.getValue("default")[key] }
    fun palette(overrides: Map<String,String> = emptyMap(), dark: Boolean = true): Map<String,String> = presets.getValue(if(dark) "default" else "porcelain") + cleanColors(overrides)
    // CSS keeps --chart-color and --chart-bar distinct at the default colour.
    // themeCssVarEntries overrides both only for a custom chart colour.
    fun chartBarColor(chart: String): String = hex(chart)?.takeIf {it!=presets.getValue("default").getValue("chart")} ?: "#6ab4f0"
    fun light(hex: String): Boolean {
        val value = hex(hex) ?: return false
        val r=value.substring(1,3).toInt(16);val g=value.substring(3,5).toInt(16);val b=value.substring(5,7).toInt(16)
        return (0.299*r+0.587*g+0.114*b)/255 > 0.6
    }
    fun scale(value: String) = when(value) { "larger" -> 1.1f; "largest" -> 1.2f; else -> 1f }
    fun heatColors(chart: String): List<String> {
        val value=hex(chart) ?: "#73bdf5"
        if(value=="#73bdf5") return listOf("#5aaaff","#78beff","#96d2ff","#b4e6ff")
        val channels=listOf(value.substring(1,3),value.substring(3,5),value.substring(5,7)).map { it.toInt(16) }
        return (0..3).map { level -> "#"+channels.joinToString("") { channel -> Math.round(channel+(255-channel)*(level*0.18)).toString(16).padStart(2,'0') } }
    }
    fun encode(colors: Map<String,String>): String {
        val full=palette(colors)
        val custom=full.getValue("chart")!=presets.getValue("default").getValue("chart")
        val keys=if(custom) codeKeys+"chart" else codeKeys
        return (if(custom) "TM2" else "TM1")+"-"+keys.joinToString("-") { full.getValue(it).drop(1).uppercase(java.util.Locale.ROOT) }
    }
    fun decode(value: String): ThemeCodeResult {
        val code=value.trim(); val version=Regex("^TM(\\d+)(?:-|$)",RegexOption.IGNORE_CASE).find(code)?.groupValues?.get(1)
        if(version!=null && version !in listOf("1","2")) return ThemeCodeResult(reason="unsupportedVersion")
        val parts=code.split('-');val keys=if(version=="2") codeKeys+"chart" else codeKeys
        if(version==null || parts.size!=keys.size+1 || parts.drop(1).any { !Regex("[0-9a-fA-F]{6}").matches(it) }) return ThemeCodeResult(reason="invalid")
        return ThemeCodeResult(keys.mapIndexed { index,key -> key to "#"+parts[index+1].lowercase(java.util.Locale.ROOT) }.toMap())
    }
    private val whitespace=Regex("[\\u0009-\\u000d\\u0020\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]+")
    fun font(value: String): String {
        val result=whitespace.replace(value," ").trim()
        return result.takeIf { it.length in 1..160 && !Regex("[\\u0000-\\u001f\\u007f;{}<>]").containsMatchIn(it) } ?: ""
    }
    fun fontFor(preset: String, custom: String="", display: Boolean=false): String = when(preset) {
        "custom" -> font(custom); "system" -> systemFont; "mono" -> defaultInterfaceFont
        "app" -> if(display) defaultDisplayFont else ""; else -> ""
    }
    fun fontPreset(value: String, display: Boolean=false): String {
        val normalized=font(value)
        return when { display && normalized==defaultDisplayFont -> "app"; normalized==systemFont -> "system"
            normalized==defaultInterfaceFont -> "mono"; normalized.isNotEmpty() -> "custom"; display -> "follow"; else -> "app" }
    }
    fun valid(value: AppearancePreferences) = value.textSize in sizes && cleanColors(value.colors)==value.colors &&
        font(value.interfaceFont)==value.interfaceFont && font(value.displayFont)==value.displayFont && DesktopVendorPalette.clean(value.vendorColors)==value.vendorColors && value.reduceMotion in listOf("system","on","off")
}
