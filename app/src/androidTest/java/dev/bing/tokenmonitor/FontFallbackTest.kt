package dev.bing.tokenmonitor

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class FontFallbackTest {
    @Test fun unavailableDesktopFamiliesUseTheFirstAvailableAndroidFallback() {
        val missing = "TokenMonitorMissingPublicFont"
        assertEquals(Typeface.DEFAULT, Typeface.create(missing, Typeface.NORMAL))
        assertEquals(FontFamily.Serif, nativeFont("\"$missing, monospace\", serif"))
        assertEquals(FontFamily.Monospace, nativeFont("$missing, monospace, serif"))
        assertEquals(FontFamily.SansSerif, nativeFont("$missing, sans-serif, monospace"))
        assertEquals(FontFamily.Serif, nativeFont("$missing", FontFamily.Serif))
        assertEquals(FontFamily.Monospace, nativeFont(DesktopAppearance.defaultInterfaceFont))
        assertEquals(BundledFonts.ui, nativeFont(DesktopAppearance.defaultDisplayFont))
        assertEquals(BundledFonts.ui, nativeFont(""))
        assertEquals(FontFamily.SansSerif, nativeFont(DesktopAppearance.systemFont))
        val condensed = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        assertNotEquals(Typeface.DEFAULT, condensed)
        val resolver = createFontFamilyResolver(InstrumentationRegistry.getInstrumentation().targetContext)
        assertEquals(condensed, resolver.resolve(nativeFont("$missing, sans-serif-condensed, serif")).value)
    }
}
