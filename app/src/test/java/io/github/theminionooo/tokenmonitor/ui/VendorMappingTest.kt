package io.github.theminionooo.tokenmonitor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VendorMappingTest {
    @Test fun confirmedMonochromeBrandsFollowActualTheme() {
        assertEquals(io.github.theminionooo.tokenmonitor.R.drawable.brand_openai_dark, monochromeBrandAsset("openai", true))
        assertEquals(io.github.theminionooo.tokenmonitor.R.drawable.brand_openai_light, monochromeBrandAsset("openai", false))
        assertEquals(io.github.theminionooo.tokenmonitor.R.drawable.brand_deepseek_dark, monochromeBrandAsset("deepseek", true))
        assertEquals(io.github.theminionooo.tokenmonitor.R.drawable.brand_deepseek_light, monochromeBrandAsset("deepseek", false))
        assertEquals(io.github.theminionooo.tokenmonitor.R.drawable.brand_hermes_dark, monochromeBrandAsset("hermes", true))
        assertEquals(io.github.theminionooo.tokenmonitor.R.drawable.brand_hermes_light, monochromeBrandAsset("hermes", false))
        assertNull(monochromeBrandAsset("claude", true))
        assertNull(monochromeBrandAsset(vendorOf("hermes-3-llama"), false))
    }

    @Test
    fun miniMaxCodeUsesTheVendorMarkWithoutRenamingTheProviderOrModels() {
        assertEquals("MiniMax Code", "mcode".displayName())
        assertEquals("minimax", vendorOf("mcode"))
        assertEquals("minimax", vendorOf("MiniMax Code"))
        assertEquals(io.github.theminionooo.tokenmonitor.R.drawable.upstream_logo_minimax, upstreamToolAsset("mcode"))
        assertEquals(originalToolColor("minimax", androidx.compose.ui.graphics.Color.White), originalToolColor("mcode", androidx.compose.ui.graphics.Color.White))
        assertEquals("Minimax", "minimax".providerLabel())
        assertEquals("minimax-m2.5", "minimax-m2.5".displayName())
        assertNull(vendorOf("mcode-helper"))
    }

    @Test
    fun modelsMapToTheVendorBehindThem() {
        assertEquals("openai", vendorOf("gpt-5.6-sol"))
        assertEquals("openai", vendorOf("GPT-5.5"))
        assertEquals("openai", vendorOf("o3-mini"))
        assertEquals("openai", vendorOf("codex-auto-review"))
        assertEquals("openai", vendorOf("codex"))
        assertEquals("claude", vendorOf("claude-fable-5-1"))
        assertEquals("claude", vendorOf("Claude Code"))
        assertEquals("deepseek", vendorOf("deepseek-v4-pro"))
        assertEquals("gemini", vendorOf("gemini-3-pro"))
        assertEquals("gemini", vendorOf("gemma-3"))
        assertEquals("xai", vendorOf("grok-4"))
        assertEquals("meta", vendorOf("llama-4-maverick"))
        assertEquals("mistral", vendorOf("codestral-latest"))
        assertEquals("qwen", vendorOf("qwen3-coder"))
        assertEquals("qwen", vendorOf("qmodel_latest"))
        assertEquals("nvidia", vendorOf("nemotron-3-super"))
        assertEquals("stepfun", vendorOf("step-3.5-flash"))
        assertEquals("kimi", vendorOf("moonshot-kimi-k2"))
        assertEquals("zai", vendorOf("glm-5.2"))
        assertEquals("cohere", vendorOf("command-r-plus"))
        assertEquals("xiaomi", vendorOf("mimo-v2"))
        assertEquals("cline", vendorOf("cline"))
        assertEquals("devin", vendorOf("devin"))
        assertEquals("devin", vendorOf("swe-agent"))
        assertEquals("devin", vendorOf("cognition-labs"))
        assertEquals("minimax", vendorOf("abab-7"))
        assertEquals("doubao", vendorOf("doubao-seed"))
        assertEquals("hunyuan", vendorOf("hunyuan-t1"))
        assertEquals("opencode", vendorOf("big-pickle"))
        assertEquals("opencode", vendorOf("opencode"))
        assertEquals("openrouter", vendorOf("openrouter/auto"))
        assertEquals("zed", vendorOf("zed-agent"))
    }

    @Test
    fun labelsFollowTheDesktop() {
        assertEquals("Muse Code", "muse".displayName())
        assertEquals("meta", vendorOf("muse"))
        assertEquals("meta", vendorOf("Muse Code"))
        assertEquals(io.github.theminionooo.tokenmonitor.R.drawable.upstream_logo_meta, upstreamToolAsset("muse"))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF0866FF), originalToolColor("muse", androidx.compose.ui.graphics.Color.White))
        assertEquals("Claude Code", "claude".displayName())
        assertEquals("OpenCode", "opencode".displayName())
        assertEquals("LM Studio", "lmstudio".displayName())
        assertEquals("NVIDIA", "nvidia".displayName())
        assertEquals("StepFun", "stepfun".displayName())
        assertEquals("Xiaomi MiMo", "mimo".displayName())
        assertEquals("Xiaomi MiMo", "micode".displayName())
        assertEquals("Pi", "pi".displayName())
        assertEquals("Oh My Pi", "omp".displayName())
        assertEquals("Devin", "devin".displayName())
        assertEquals("TypeSafe", "typesafe".providerLabel())
        assertEquals("Factory Droid", "droid".displayName())
        assertEquals("Factory Droid", "factory".providerLabel())
        assertEquals("Third-party APIs", "thirdparty".providerLabel())
        assertEquals("gpt-5.6-sol", "gpt-5.6-sol".displayName())
        assertEquals("claude-fable-5-1", "claude-fable-5-1".displayName())
        assertEquals("stealth/ox-alpha", "stealth/ox-alpha".displayName())
        assertEquals("Studio Workstation", "studio_workstation".displayName())
        assertEquals("Claude", "claude".providerName())
    }

    @Test
    fun unknownNamesStayNeutral() {
        assertNull(vendorOf("stealth/ox-alpha"))
        assertNull(vendorOf("local-model"))
        assertNull(vendorOf(""))
    }
    @Test fun harnessAliasesUseTheOfficialWhale() {
        listOf("dsh", "DeepSeek Harness", "deepseek-harness", "deepseek_harness", " DSH ").forEach {
            assertEquals("deepseek", toolVendorOf(it))
            assertEquals(io.github.theminionooo.tokenmonitor.R.drawable.upstream_logo_deepseek, upstreamToolAsset(it))
        }
    }

    @Test fun hermesToolAliasesDoNotBecomeModelVendors() {
        listOf("hermes", "Hermes Agent", "hermes-agent", "hermes_agent").forEach {
            assertEquals(io.github.theminionooo.tokenmonitor.R.drawable.upstream_logo_hermes, upstreamToolAsset(it))
            assertNull(upstreamModelAsset(it))
        }
        assertNull(upstreamModelAsset("Nous-Hermes-3"))
        assertNull(upstreamModelAsset("dsh"))
        assertNull(upstreamToolAsset("unknown-tool"))
    }

    @Test fun platformsAreTrimmedAndDoNotUseDeviceNames() {
        assertEquals(io.github.theminionooo.tokenmonitor.R.drawable.device_windows_light, devicePlatformAsset("WINDOWS-x64"))
        assertEquals(io.github.theminionooo.tokenmonitor.R.drawable.device_apple_light, devicePlatformAsset(" darwin "))
        listOf("linux", "Linux-arm64", " linux-x64 ").forEach {
            assertEquals(io.github.theminionooo.tokenmonitor.R.drawable.device_ugreen_light, devicePlatformAsset(it))
        }
        assertEquals(io.github.theminionooo.tokenmonitor.R.drawable.view_device, devicePlatformAsset("unknown"))
        assertEquals(io.github.theminionooo.tokenmonitor.R.drawable.view_device, devicePlatformAsset(""))
    }

}
