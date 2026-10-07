package io.github.theminionooo.tokenmonitor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterfaceThemeTest {
    @Test fun chartCodesRoundTripWithoutChangingProviderColors() {
        val theme = InterfaceTheme.fromCode(" tm2-b7ead4-303438-eef5fb-a3adbb-ff6688 ")!!
        assertEquals("TM2-B7EAD4-303438-EEF5FB-A3ADBB-FF6688", theme.code)
        val palette = Palette.from(theme)
        val original = Palette.from(InterfaceTheme.Default)
        assertEquals(original.blue, palette.blue)
        assertEquals(original.orange, palette.orange)
        assertEquals(palette.chart, palette.heat[1])
        assertEquals(original.heat[0], palette.heat[0])
        assertEquals("#73bdf5", InterfaceTheme.fromCode(InterfaceTheme.Default.code)!!.chart)
        assertEquals(original.heat, Palette.from(InterfaceTheme.Default.copy(chart = "#73BDF5")).heat)
        assertEquals(Palette.from(InterfaceTheme.Porcelain).blue, Palette.from(InterfaceTheme.Porcelain).chart)
        assertNull(InterfaceTheme.fromCode("TM1-B7EAD4-303438-EEF5FB-A3ADBB-FF6688"))
        assertNull(InterfaceTheme.fromCode("TM3-B7EAD4-303438-EEF5FB-A3ADBB-FF6688"))
    }

    @Test
    fun presetCodesMatchTheDesktop() {
        assertEquals("TM1-B7EAD4-303438-EEF5FB-A3ADBB", InterfaceTheme.Default.code)
        assertEquals("TM1-E6E8EC-0B0C0E-ECEEF2-8F949C", InterfaceTheme.Obsidian.code)
        assertEquals("TM1-2563EB-F6F7F9-1C1F26-5B626D", InterfaceTheme.Porcelain.code)
    }

    @Test
    fun codesRoundTripAndTolerateCaseAndSpaces() {
        val decoded = InterfaceTheme.fromCode("  tm1-b7ead4-303438-eef5fb-a3adbb ")
        assertEquals(InterfaceTheme.Default, decoded)
        assertEquals("default", InterfaceTheme.idOf(decoded!!))
        assertEquals("custom", InterfaceTheme.idOf(InterfaceTheme.fromCode("TM1-FF0000-303438-EEF5FB-A3ADBB")!!))
    }

    @Test
    fun malformedCodesAreRejected() {
        assertNull(InterfaceTheme.fromCode(""))
        assertNull(InterfaceTheme.fromCode("TM2-B7EAD4-303438-EEF5FB-A3ADBB"))
        assertNull(InterfaceTheme.fromCode("TM1-B7EAD4-303438-EEF5FB"))
        assertNull(InterfaceTheme.fromCode("TM1-GGGGGG-303438-EEF5FB-A3ADBB"))
    }

    @Test
    fun followingThePhoneKeepsTheChosenDarkPresetAndUsesPorcelainByDay() {
        val obsidian = InterfaceTheme.Obsidian.code
        assertEquals(InterfaceTheme.Obsidian, resolveInterfaceTheme(obsidian, followSystem = false, systemDark = false))
        assertEquals(InterfaceTheme.Obsidian, resolveInterfaceTheme(obsidian, followSystem = true, systemDark = true))
        assertEquals(InterfaceTheme.Porcelain, resolveInterfaceTheme(obsidian, followSystem = true, systemDark = false))
        assertEquals(InterfaceTheme.Default, resolveInterfaceTheme(null, followSystem = true, systemDark = true))
        assertEquals(InterfaceTheme.Porcelain, resolveInterfaceTheme(null, followSystem = true, systemDark = false))
        assertEquals(InterfaceTheme.Porcelain, resolveInterfaceTheme(InterfaceTheme.Porcelain.code, followSystem = true, systemDark = false))
        assertEquals(InterfaceTheme.Default, resolveInterfaceTheme(InterfaceTheme.Porcelain.code, followSystem = true, systemDark = true))
        val lightCustom = "TM1-2563EB-FFFFFF-101010-777777"
        assertEquals(lightCustom, resolveInterfaceTheme(lightCustom, followSystem = true, systemDark = false).code)
        assertEquals(InterfaceTheme.Default, resolveInterfaceTheme(lightCustom, followSystem = true, systemDark = true))
    }

    @Test
    fun lightBackgroundsFlipTheSurfaceSystem() {
        assertFalse(InterfaceTheme.Default.isLight)
        assertFalse(InterfaceTheme.Obsidian.isLight)
        assertTrue(InterfaceTheme.Porcelain.isLight)

        val dark = Palette.from(InterfaceTheme.Default)
        val light = Palette.from(InterfaceTheme.Porcelain)
        assertTrue(dark.line.red > 0.8f)
        assertTrue(light.line.red < 0.2f)
        assertTrue(light.recessed.red > 0.99f)
        assertTrue(light.success.green < dark.success.green)
    }
}
