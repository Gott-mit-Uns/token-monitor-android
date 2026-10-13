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
            for (char in "办公室无线蜂窝网络切换中文AéΣ0123￥−") assertTrue("Bundled mixed glyph $char", ui.hasGlyph(char.toString()))
            val mono = Paint().apply { textSize = 48f; typeface = resolver.resolve(BundledFonts.data, weight).value as Typeface }
            val zero = mono.measureText("0")
            for (digit in '0'..'9') assertEquals("Equal digit advance", zero, mono.measureText(digit.toString()), .01f)
            assertTrue(mono.hasGlyph("$")); assertTrue(mono.hasGlyph("−"))
            val system = Paint().apply { textSize = 48f; typeface = Typeface.DEFAULT }
            // Rare dynamic names outside GB2312 must retain the device's own fallback.
            for (rare in listOf("龘", "𠮷")) if (system.hasGlyph(rare)) assertTrue("System fallback $rare", ui.hasGlyph(rare))
        }
    }
}
