package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class ThemeSemanticTest {
    @Test fun graphiteSelectionResetAndExplicitDefaultCodeRetainDifferentNumberSemantics() {
        val original=DisplayPreferences(theme=ThemeChoice.DARK,appearance=AppearancePreferences(colors=mapOf("text" to "#123456")))
        val graphite=DisplayChange.ThemePreset("default").apply(original)
        assertEquals(emptyMap<String,String>(),graphite.appearance.colors)
        assertEquals("#eef5fb",DesktopAppearance.palette(graphite.appearance.colors).getValue("text"))
        assertEquals("#f3fbf7",DesktopAppearance.numberColor(graphite.appearance.colors))

        val imported=DisplayChange.ThemeCode("TM1-B7EAD4-303438-EEF5FB-A3ADBB").apply(graphite)
        assertTrue(imported.appearance.colors.containsKey("text"))
        assertEquals("#eef5fb",DesktopAppearance.numberColor(imported.appearance.colors))
        assertEquals(DesktopAppearance.palette(graphite.appearance.colors),DesktopAppearance.palette(imported.appearance.colors))
        val resetText=DisplayChange.ThemeColor("text",null).apply(imported)
        assertFalse(resetText.appearance.colors.containsKey("text"))
        assertEquals("#f3fbf7",DesktopAppearance.numberColor(resetText.appearance.colors))
        val resetAll=DisplayChange.Theme(ThemeChoice.DARK).apply(imported)
        assertEquals(graphite,resetAll)
        assertEquals("#f3fbf7",DesktopAppearance.numberColor(resetAll.appearance.colors))
    }

    @Test fun independentAccentChartAndBackgroundDoNotOverrideNumberWhilePresetsKeepSparseText() {
        var preferences=DisplayPreferences(theme=ThemeChoice.DARK)
        for ((key,value) in listOf("accent" to "#ff0000","chart" to "#00ff00","bg" to "#ffffff")) {
            preferences=DisplayChange.ThemeColor(key,value).apply(preferences)
            assertEquals("#f3fbf7",DesktopAppearance.numberColor(preferences.appearance.colors))
        }
        assertEquals("#f3fbf7",DesktopAppearance.numberColor(mapOf("text" to "invalid")))
        val text=DisplayChange.ThemeColor("text"," #A1B2C3 ").apply(preferences)
        assertEquals("#a1b2c3",DesktopAppearance.numberColor(text.appearance.colors))

        val carbon=DisplayChange.ThemePreset("obsidian").apply(text)
        assertEquals(setOf("accent","bg","text","muted"),carbon.appearance.colors.keys)
        assertEquals("#eceef2",DesktopAppearance.numberColor(carbon.appearance.colors))
        assertEquals(DesktopAppearance.presets.getValue("obsidian"),DesktopAppearance.palette(carbon.appearance.colors))
        val paper=DisplayChange.ThemePreset("porcelain").apply(carbon)
        assertEquals(ThemeChoice.LIGHT,paper.theme)
        assertEquals(setOf("accent","bg","text","muted"),paper.appearance.colors.keys)
        assertEquals("#1c1f26",DesktopAppearance.numberColor(paper.appearance.colors,false))
        assertEquals(DesktopAppearance.presets.getValue("porcelain"),DesktopAppearance.palette(paper.appearance.colors,false))
    }

    @Test fun nativeLightBaseAndExistingFullOverridesRemainReadableWithoutMigration() {
        val original=DisplayPreferences(theme=ThemeChoice.DARK,currency="CNY",currencyRates=mapOf("CNY" to "7"),
            appearance=AppearancePreferences(colors=DesktopAppearance.presets.getValue("default"),interfaceFont="serif",textSize="largest"))
        // Old full-palette preferences and imported explicit defaults keep their text key.
        val chart=DisplayChange.ThemeColor("chart","#123456").apply(original)
        assertEquals("#eef5fb",chart.appearance.colors["text"])
        assertEquals("#eef5fb",DesktopAppearance.numberColor(chart.appearance.colors))
        assertEquals("serif",chart.appearance.interfaceFont)
        assertEquals("CNY",chart.currency)

        val light=DisplayChange.Theme(ThemeChoice.LIGHT).apply(chart)
        assertTrue(light.appearance.colors.isEmpty())
        assertEquals("#1c1f26",DesktopAppearance.numberColor(light.appearance.colors,false))
        val system=DisplayChange.Theme(ThemeChoice.SYSTEM).apply(light)
        assertEquals("#1c1f26",DesktopAppearance.numberColor(system.appearance.colors,false))
        assertEquals("#f3fbf7",DesktopAppearance.numberColor(system.appearance.colors,true))
        assertEquals(original.appearance.copy(colors=emptyMap()),system.appearance)
        assertEquals(original.currencyRates,system.currencyRates)
    }

    @Test fun textPreviewCancelAndApplyKeepSavedPresenceSeparateFromRenderedRole() {
        val saved=AppearancePreferences(colors=mapOf("accent" to "#ff0000"))
        val preview=AppearancePreviewState();val owner=Any()
        preview.show("appearance-color-text","#abcdef",owner)
        assertEquals("#abcdef",DesktopAppearance.numberColor(preview.rendered(saved).colors))
        assertFalse(saved.colors.containsKey("text"))
        preview.clear(owner)
        assertEquals(saved,preview.rendered(saved))
        assertEquals("#f3fbf7",DesktopAppearance.numberColor(preview.rendered(saved).colors))
        val committed=DisplayChange.ThemeColor("text","#abcdef").apply(DisplayPreferences(theme=ThemeChoice.DARK,appearance=saved))
        assertEquals("#abcdef",committed.appearance.colors["text"])
        assertEquals("#abcdef",DesktopAppearance.numberColor(committed.appearance.colors))
    }
}
