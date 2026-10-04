package io.github.theminionooo.tokenmonitor.data.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayPreferencesTest {
    @Test fun iconSizeAndTextSizeAreIndependentPresentationChoices() {
        assertEquals(listOf(16, 20, 24), IconScale.entries.map { it.dp })
        val defaults = DisplayOptions()
        assertEquals(IconScale.Comfortable, defaults.iconScale)
        assertEquals(true, defaults.homeChineseUnits)
        val changed = defaults.copy(iconScale = IconScale.Large)
        assertEquals(defaults.textScale, changed.textScale)
        assertEquals(IconScale.Large, changed.copy(textScale = TextScale.Compact).iconScale)
        assertEquals(defaults.compactTokenTotal, changed.compactTokenTotal)
    }

    @Test
    fun togglingAnItemPreservesTheCurrentOrder() {
        val allowed = listOf("Home", "Tools", "Status", "Devices")
        val custom = listOf("Home", "Status", "Tools", "Devices")

        val hidden = toggleOrderedValue(custom, "Tools", enabled = false, allowed)
        val restored = toggleOrderedValue(hidden, "Tools", enabled = true, allowed)

        assertEquals(listOf("Home", "Status", "Devices"), hidden)
        assertEquals(listOf("Home", "Status", "Devices", "Tools"), restored)
    }

    @Test
    fun unknownStoredItemsAreDiscarded() {
        val result = toggleOrderedValue(
            current = listOf("Home", "Removed view", "Tools"),
            value = "Home",
            enabled = true,
            allowed = listOf("Home", "Tools"),
        )

        assertEquals(listOf("Home", "Tools"), result)
    }
}
