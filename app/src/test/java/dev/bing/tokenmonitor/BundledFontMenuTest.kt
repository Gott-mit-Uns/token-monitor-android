package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class BundledFontMenuTest {
    @Test fun namedFontsRoundTripForBothRolesWithoutChangingLegacyChoices() {
        for(display in listOf(false,true)) {
            val presets=if(display) listOf("bundled-ui","bundled-data","app","system","mono") else listOf("bundled-ui","bundled-data","system","mono")
            for(preset in presets) {
                val stored=DesktopAppearance.fontFor(preset,display=display)
                assertEquals(preset,DesktopAppearance.fontPreset(stored,display))
                assertTrue(DesktopAppearance.valid(if(display) AppearancePreferences(displayFont=stored) else AppearancePreferences(interfaceFont=stored)))
            }
            assertEquals("bundled-data",DesktopAppearance.fontPreset(" jetbrains mono ",display))
            assertEquals("custom",DesktopAppearance.fontPreset("serif",display))
        }
        assertEquals("",DesktopAppearance.fontFor("app"))
        assertEquals(DesktopAppearance.defaultDisplayFont,DesktopAppearance.fontFor("app",display=true))
        assertEquals(DesktopAppearance.defaultInterfaceFont,DesktopAppearance.fontFor("mono"))
        assertEquals(DesktopAppearance.systemFont,DesktopAppearance.fontFor("system"))
        assertEquals("follow",DesktopAppearance.fontPreset("",true))
        // The mobile default follows the desktop monospace identity; "app" is a display-only alias.
        assertEquals("mono",DesktopAppearance.fontPreset(""))
    }
}
