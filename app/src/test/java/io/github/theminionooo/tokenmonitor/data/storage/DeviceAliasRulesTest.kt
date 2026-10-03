package io.github.theminionooo.tokenmonitor.data.storage

import org.junit.Assert.*
import org.junit.Test

class DeviceAliasRulesTest {
    @Test fun trimsAndRestores() {
        assertEquals("客厅 NAS", DeviceAliasRules.normalize("  客厅 NAS  "))
        assertEquals("", DeviceAliasRules.normalize("  "))
    }
    @Test fun countsUnicodeCodePoints() {
        assertEquals(64, DeviceAliasRules.normalize("😀".repeat(32)).length)
        assertThrows(IllegalArgumentException::class.java) { DeviceAliasRules.normalize("😀".repeat(33)) }
    }
    @Test fun rejectsControlAndLineBreaks() {
        listOf("a\nb", "a\rb", "a\tb", "a\u0000b", "a\u2028b").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) { DeviceAliasRules.normalize(name) }
        }
    }
    @Test fun normalizedKeyUsesNoCredentialsAndHidesAddressAndId() {
        val key = DeviceAliasRules.key(" https://EXAMPLE.com/ ", "private-device-id")
        assertEquals(key, DeviceAliasRules.key("https://example.com:443", "private-device-id"))
        assertTrue(key.matches(Regex("[a-f0-9]{64}\\.[a-f0-9]{64}")))
        assertFalse(key.contains("example"))
        assertFalse(key.contains("private-device-id"))
    }
    @Test fun isolatesHubAndDeviceButNormalizesAddress() {
        assertEquals(DeviceAliasRules.key("https://EXAMPLE.com/", "device"), DeviceAliasRules.key("https://example.com:443", "device"))
        assertNotEquals(DeviceAliasRules.key("https://example.com", "device"), DeviceAliasRules.key("https://other.example.com", "device"))
        assertNotEquals(DeviceAliasRules.key("https://example.com", "device"), DeviceAliasRules.key("https://example.com", "other"))
    }
}
