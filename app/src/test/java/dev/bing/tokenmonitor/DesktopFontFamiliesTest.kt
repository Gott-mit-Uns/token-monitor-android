package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class DesktopFontFamiliesTest {
    @Test fun quotedAndEscapedCommasDoNotBecomeFallbackFamilies() {
        assertEquals(listOf("Missing, serif", "monospace"), DesktopFontFamilies.candidates("\"Missing, serif\", monospace"))
        assertEquals(listOf("Missing, serif", "serif"), DesktopFontFamilies.candidates("Missing\\, serif, serif"))
        assertEquals(listOf("Quoted \"Name\"", "serif"), DesktopFontFamilies.candidates("\"Quoted \\\"Name\\\"\", serif"))
        assertEquals(listOf("serif", "monospace"), DesktopFontFamilies.candidates("\\73 erif, monospace"))
    }

    @Test fun desktopListsRetainCandidateOrderAndRejectUnsafeDeclarations() {
        assertEquals(listOf("ui-monospace", "SFMono-Regular", "Menlo", "Consolas", "Liberation Mono", "monospace"), DesktopFontFamilies.candidates(DesktopAppearance.defaultInterfaceFont))
        assertEquals(listOf("Missing Family", "sans-serif-condensed", "serif"), DesktopFontFamilies.candidates("  'Missing Family' , sans-serif-condensed, serif "))
        assertEquals(emptyList<String>(), DesktopFontFamilies.candidates("serif; color:red"))
        assertEquals(emptyList<String>(), DesktopFontFamilies.candidates(""))
    }
}
