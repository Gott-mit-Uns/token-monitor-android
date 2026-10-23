package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import androidx.compose.ui.graphics.toArgb

class VendorDisplayTest {
    private fun fixture()=javaClass.classLoader!!.getResourceAsStream("vendor-display-parity.json")!!.bufferedReader().use {Json.parseToJsonElement(it.readText()).jsonObject}
    private fun strings(value:JsonElement)=value.jsonObject.mapValues {it.value.jsonPrimitive.content}
    @Test fun canonicalAndCustomPalettesModelsAndFactoryProviderMatchFixedDesktop() {
        val fixture=fixture()
        assertEquals(fixture.getValue("ids").jsonArray.map {it.jsonPrimitive.content},DesktopVendorPalette.ids)
        assertEquals(strings(fixture.getValue("labels")),desktopVendorLabels)
        fixture.getValue("palettes").jsonArray.forEach {raw ->
            val item=raw.jsonObject;val input=strings(item.getValue("input"));val palette=DesktopVendorPalette.resolve(input)
            assertEquals(strings(item.getValue("clean")),DesktopVendorPalette.clean(input))
            item.getValue("colors").jsonObject.forEach {(id,value) -> assertEquals(hexColor(value.jsonPrimitive.content).toArgb(),toolColor(id,palette).toArgb())}
            item.getValue("models").jsonArray.forEach {row -> val model=row.jsonObject
                assertEquals(model.getValue("vendor").jsonPrimitive.contentOrNull,modelVendor(model.getValue("name").jsonPrimitive.content))
                assertEquals(hexColor(model.getValue("color").jsonPrimitive.content).toArgb(),modelColor(model.getValue("name").jsonPrimitive.content,palette).toArgb())
            }
            item.getValue("providers").jsonArray.forEach {row -> val provider=row.jsonObject
                assertEquals(hexColor(provider.getValue("color").jsonPrimitive.content).toArgb(),providerColor(provider.getValue("id").jsonPrimitive.content,palette).toArgb())
            }
        }
    }
    @Test fun localVendorAndVisibilityChangesPreservePaletteFontsMoneyToolsAndSourceDefaults() {
        val original=DisplayPreferences(currency="CNY",currencyRates=mapOf("CNY" to "7"),hiddenTools=setOf("claude"),lastPeriod="last30",monthMode="last30",appearance=AppearancePreferences(mapOf("chart" to "#abcdef"),"largest","serif",""))
        val colors=original.appearance.colors
        val red=DisplayChange.VendorColor("codex"," #FF0000 ").apply(original)
        assertEquals(mapOf("codex" to "#ff0000"),red.appearance.vendorColors)
        assertEquals(colors,red.appearance.colors);assertEquals(original,red.copy(appearance=red.appearance.copy(vendorColors=emptyMap())))
        for(invalid in listOf(DisplayChange.VendorColor("bad","#123456"),DisplayChange.VendorColor("codex","#123"))) assertEquals(red,invalid.apply(red))
        val hidden=DisplayChange.LiveDot(false).apply(DisplayChange.ToolIcons(false).apply(red))
        assertFalse(hidden.appearance.showToolIcons);assertFalse(hidden.appearance.showLiveDot)
        val reset=DisplayChange.ResetVendorColors.apply(hidden);assertTrue(reset.appearance.vendorColors.isEmpty());assertFalse(reset.appearance.showToolIcons)
        assertEquals(hidden.appearance.vendorColors,DisplayChange.ThemePreset("porcelain").apply(hidden).appearance.vendorColors)
        assertEquals(red.copy(appearance=red.appearance.copy(vendorColors=emptyMap())),DisplayChange.VendorColor("codex",null).apply(red))
        assertTrue(AppearancePreferences().showToolIcons);assertTrue(AppearancePreferences().showLiveDot)
    }
    @Test fun unknownNamesKeepStableModelColorsAndMalformedStoredOverridesAreRejected() {
        val initial=modelColor("unknown-model")
        assertEquals(initial,modelColor("unknown-model",DesktopVendorPalette.resolve(mapOf("default" to "#ffffff","codex" to "#ff0000"))))
        assertEquals(hexColor("#abcdef"),toolColor("unknown-client",DesktopVendorPalette.resolve(mapOf("default" to "#abcdef"))))
        assertFalse(DesktopAppearance.valid(AppearancePreferences(vendorColors=mapOf("bad" to "#123456"))))
        assertFalse(DesktopAppearance.valid(AppearancePreferences(vendorColors=mapOf("codex" to "#123"))))
        assertFalse(DesktopAppearance.valid(AppearancePreferences(vendorColors=mapOf("codex" to "#ABCDEF"))))
        assertEquals(desktopVendorColors,DesktopVendorPalette.resolve(emptyMap()))
    }
}
