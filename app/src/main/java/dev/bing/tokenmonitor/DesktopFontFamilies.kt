package dev.bing.tokenmonitor

/** Stored desktop font-family lists may contain quoted commas and CSS escapes. */
internal object DesktopFontFamilies {
    fun candidates(value: String): List<String> {
        val source = DesktopAppearance.font(value)
        val families = mutableListOf<String>()
        val name = StringBuilder()
        var quote: Char? = null
        var index = 0
        fun finish() {
            name.toString().trim().takeIf { it.isNotEmpty() }?.let(families::add)
            name.setLength(0)
        }
        while (index < source.length) {
            val char = source[index++]
            when {
                char == '\\' && index < source.length -> {
                    val start = index
                    while (index < source.length && index - start < 6 && source[index].digitToIntOrNull(16) != null) index++
                    if (index > start) {
                        val point = source.substring(start, index).toInt(16)
                        name.appendCodePoint(if (point == 0 || point in 0xd800..0xdfff || point > 0x10ffff) 0xfffd else point)
                        if (index < source.length && source[index].isWhitespace()) index++
                    } else name.append(source[index++])
                }
                quote != null -> if (char == quote) quote = null else name.append(char)
                char == '\'' || char == '"' -> quote = char
                char == ',' -> finish()
                else -> name.append(char)
            }
        }
        finish()
        return families
    }
}
