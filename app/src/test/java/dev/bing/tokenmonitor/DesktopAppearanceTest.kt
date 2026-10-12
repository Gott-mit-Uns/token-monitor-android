package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DesktopAppearanceTest {
    private fun fixture()=javaClass.classLoader!!.getResourceAsStream("appearance-parity.json")!!.bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
    private fun JsonElement.map()=jsonObject.mapValues { it.value.jsonPrimitive.content }
    @Test fun presetsColoursCodesAndBrightnessMatchFixedDesktop() {
        val fixture=fixture()
        fixture.getValue("presets").jsonArray.forEach { raw -> val item=raw.jsonObject;assertEquals(item.getValue("colors").map(),DesktopAppearance.presets.getValue(item.getValue("id").jsonPrimitive.content)) }
        fixture.getValue("colors").jsonArray.forEach { raw ->
            val item=raw.jsonObject;val input=item.getValue("input").map();val palette=DesktopAppearance.palette(input)
            assertEquals(item.getValue("clean").map(),DesktopAppearance.cleanColors(input));assertEquals(item.getValue("palette").map(),palette)
            assertEquals(item.getValue("code").jsonPrimitive.content,DesktopAppearance.encode(input));assertEquals(item.getValue("light").jsonPrimitive.boolean,DesktopAppearance.light(palette.getValue("bg")))
            item.getValue("heat").jsonArray.forEachIndexed { index, entry ->
                val expected=entry.jsonObject.getValue("value").jsonPrimitive.contentOrNull
                if(expected!=null) assertEquals(expected,DesktopAppearance.heatColors(palette.getValue("chart"))[index].drop(1).chunked(2).joinToString(", ") { it.toInt(16).toString() })
            }
        }
        assertEquals(listOf("#5aaaff","#78beff","#96d2ff","#b4e6ff"),DesktopAppearance.heatColors("#73bdf5"))
        // Source styles.css keeps its default --chart-bar distinct; a custom
        // chart colour overrides it. Resetting to default clears that override.
        assertEquals("#6ab4f0",DesktopAppearance.chartBarColor("#73bdf5"))
        assertEquals("#6ab4f0",DesktopAppearance.chartBarColor("#73BDF5"))
        assertEquals("#6ab4f0",DesktopAppearance.chartBarColor("invalid"))
        assertEquals("#2060a0",DesktopAppearance.chartBarColor("#2060a0"))
    }
    @Test fun themeCodeVersionsAndInvalidCodesMatchWithoutChangingExistingChoices() {
        fixture().getValue("codes").jsonArray.forEach { raw ->
            val item=raw.jsonObject;val expected=item.getValue("expected").jsonObject;val actual=DesktopAppearance.decode(item.getValue("input").jsonPrimitive.content)
            if(expected.getValue("ok").jsonPrimitive.boolean) {
                assertEquals(expected.getValue("colors").map(),actual.colors)
                assertEquals(expected.getValue("code").jsonPrimitive.content,DesktopAppearance.encode(actual.colors!!))
            } else assertEquals(expected.getValue("reason").jsonPrimitive.content,actual.reason)
        }
        val good=DisplayPreferences(appearance=AppearancePreferences(colors=mapOf("chart" to "#123456")))
        assertEquals(good,DisplayChange.ThemeCode("TM3-AAAAAA").apply(good));assertEquals(good,DisplayChange.ThemeColor("bg","broken").apply(good))
        assertEquals(good,DisplayChange.ThemeColor("illegal","#000000").apply(good));assertEquals(good,DisplayChange.Font(false,"{bad}").apply(good))
        assertEquals(good,DisplayChange.TextSize("invalid").apply(good))
    }
    @Test fun fontNormalizationPresetsAndTextScaleMatchDesktop() {
        fixture().getValue("fonts").jsonArray.forEach { raw ->
            val item=raw.jsonObject;val input=item.getValue("input").jsonPrimitive.content
            assertEquals(item.getValue("normalized").jsonPrimitive.content,DesktopAppearance.font(input))
            assertEquals(item.getValue("interface").jsonPrimitive.content,DesktopAppearance.fontPreset(input))
            assertEquals(item.getValue("display").jsonPrimitive.content,DesktopAppearance.fontPreset(input,true))
        }
        fixture().getValue("sizes").jsonArray.forEach { raw -> val item=raw.jsonObject;assertEquals(item.getValue("scale").jsonPrimitive.float,DesktopAppearance.scale(item.getValue("input").jsonPrimitive.content),0f) }
        fixture().getValue("fontPresets").jsonArray.forEach { raw -> val item=raw.jsonObject;assertEquals(item.getValue("value").jsonPrimitive.content,DesktopAppearance.fontFor(item.getValue("preset").jsonPrimitive.content," Noto Sans ",item.getValue("role").jsonPrimitive.content=="display")) }
    }
    @Test fun themeChangesPreserveFontsCurrencyAndToolPreferences() {
        val original=DisplayPreferences(currency="CNY",currencyRates=mapOf("CNY" to "7"),hiddenTools=setOf("claude"),monthMode="last30",lastPeriod="last30",appearance=AppearancePreferences(textSize="largest",interfaceFont="serif",displayFont=""))
        val paper=DisplayChange.ThemePreset("porcelain").apply(original)
        assertEquals(ThemeChoice.LIGHT,paper.theme);assertEquals(DesktopAppearance.presets.getValue("porcelain"),DesktopAppearance.palette(paper.appearance.colors))
        assertEquals(original.appearance.copy(colors=paper.appearance.colors),paper.appearance)
        val cleared=DisplayChange.Theme(ThemeChoice.SYSTEM).apply(paper)
        assertEquals(original.copy(theme=ThemeChoice.SYSTEM),cleared)
        val imported=DisplayChange.ThemeCode("TM1-2563EB-F6F7F9-1C1F26-5B626D").apply(original)
        assertEquals("#73bdf5",DesktopAppearance.palette(imported.appearance.colors).getValue("chart"))
        assertEquals("CNY",imported.currency);assertEquals(setOf("claude"),imported.hiddenTools)
    }
}
