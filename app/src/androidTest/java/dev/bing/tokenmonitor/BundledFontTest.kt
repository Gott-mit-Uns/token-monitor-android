package dev.bing.tokenmonitor

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class BundledFontTest {
    @Test fun bundledFacesParseAndNumericDigitsHaveEqualAdvances() {
        val resolver = createFontFamilyResolver(InstrumentationRegistry.getInstrumentation().targetContext)
        for (weight in listOf(FontWeight.Normal, FontWeight.Bold)) {
            val ui = Paint().apply { textSize = 48f; typeface = resolver.resolve(BundledFonts.ui, weight).value as Typeface }
            val system = Paint().apply { textSize = 48f; typeface = Typeface.DEFAULT }
            for (char in "办公室无线蜂窝网络切换中文AéΣ0123￥−") {
                val glyph = char.toString()
                println("PUBLIC_FONT_GLYPH weight=${weight.weight} codepoint=U+${char.code.toString(16).uppercase()} system=${system.hasGlyph(glyph)} ui=${ui.hasGlyph(glyph)}")
                assertTrue("Bundled mixed glyph $char", ui.hasGlyph(glyph))
            }
            val mono = Paint().apply { textSize = 48f; typeface = resolver.resolve(BundledFonts.data, weight).value as Typeface }
            val zero = mono.measureText("0")
            for (digit in '0'..'9') assertEquals("Equal digit advance", zero, mono.measureText(digit.toString()), .01f)
            assertTrue(mono.hasGlyph("$")); assertTrue(mono.hasGlyph("−"))
            // Rare dynamic names outside GB2312 must retain the device's own fallback.
            for (rare in listOf("龘", "𠮷")) {
                val systemAvailable = system.hasGlyph(rare)
                val uiAvailable = ui.hasGlyph(rare)
                println("PUBLIC_FONT_GLYPH weight=${weight.weight} codepoint=U+${rare.codePointAt(0).toString(16).uppercase()} system=$systemAvailable ui=$uiAvailable")
                if (systemAvailable) assertTrue("System fallback $rare", uiAvailable)
            }
        }
    }
}
