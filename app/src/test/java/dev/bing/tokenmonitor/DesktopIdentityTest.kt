package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class DesktopIdentityTest {
    @Test fun desktopClientCatalogAndModelFamiliesKeepTheirIdentity() {
        assertEquals("Hermes Agent", toolLabel("hermes-agent"))
        assertEquals("Factory Droid", toolLabel("droid"))
        assertEquals("Kilo", toolLabel("kilo"))
        assertEquals("Gemini", toolLabel("gemini"))
        assertEquals("unknown-client", toolLabel("unknown-client"))
        assertEquals("Z.ai / GLM", providerLabel("zai"))
        assertEquals("Factory Droid", providerLabel("factory"))
        assertEquals("Claude Code", providerLabel("claude"))
        assertNotNull(brandResource("hermes"))
        assertNotNull(brandResource("kilo"))
        assertNull(brandResource("unknown-client"))
        assertEquals("codex", modelVendor("openai/gpt-5.5"))
        assertEquals("claude", modelVendor("anthropic/claude-sonnet-4.6"))
        assertEquals("kimi", modelVendor("K2D6-agent"))
        assertEquals("zai", modelVendor("vendor/glm-5"))
        assertEquals("meta", modelVendor("muse-spark"))
        assertNull(modelVendor("unknown-model"))
    }
}
