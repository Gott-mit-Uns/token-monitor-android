package io.github.theminionooo.tokenmonitor.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import io.github.theminionooo.tokenmonitor.R
import java.util.Locale

private val modelVendorRules = listOf(
    Regex("^(cursor-)?auto$") to "cursor",
    Regex("claude|anthropic|sonnet|opus|haiku") to "claude",
    Regex("gpt|openai|codex|^o[134](?:-|$)|o[134]-(mini|pro|preview)|chatgpt") to "openai",
    Regex("gemini|gemma|google|antigravity") to "gemini",
    Regex("grok|xai") to "xai",
    Regex("deepseek") to "deepseek",
    Regex("llama|meta") to "meta",
    Regex("mistral|mixtral|codestral") to "mistral",
    Regex("qwen|qwq|qvq|qmodel") to "qwen",
    Regex("nemotron|nvidia") to "nvidia",
    Regex("stepfun|step-") to "stepfun",
    Regex("kimi|moonshot|k2d6-agent|k3-agent") to "kimi",
    Regex("chatglm|\\bglm-|\\bzai\\b|z\\.ai|zhipu") to "zai",
    Regex("cohere|command-r") to "cohere",
    Regex("mimo|xiaomi") to "xiaomi",
    Regex("^cline$") to "cline",
    Regex("^swe[-_]|devin|cognition") to "devin",
    Regex("minimax|\\babab") to "minimax",
    Regex("doubao|\\bseed(?:-|$)") to "doubao",
    Regex("hy3|hunyuan") to "hunyuan",
    Regex("^big-pickle$") to "opencode",
)

/** Returns the desktop vendor family represented by a tool or model name. */
internal fun vendorOf(name: String): String? {
    val key = name.trim().lowercase(Locale.US)
    if (key.isEmpty()) return null
    if (key == "cursor") return "cursor"
    if (key == "muse" || key == "muse code") return "meta"
    if (key == "openrouter" || key.startsWith("openrouter/")) return "openrouter"
    if (key == "zed" || key.startsWith("zed-")) return "zed"
    if (key == "opencode") return "opencode"
    return modelVendorRules.firstOrNull { (pattern, _) -> pattern.containsMatchIn(key) }?.second
}

internal fun originalToolColor(name: String, fallback: Color): Color = when (vendorOf(name)) {
    "openai" -> Color(0xFF49A3B0)
    "claude" -> Color(0xFFCC7C5E)
    "gemini" -> Color(0xFF4285F4)
    "xai" -> fallback
    "deepseek" -> Color(0xFF4D6BFE)
    "meta" -> if (name.trim().lowercase(Locale.US) in setOf("muse", "muse code")) Color(0xFF0866FF) else Color(0xFF1D65C1)
    "mistral" -> Color(0xFFFA520F)
    "qwen" -> Color(0xFF615CED)
    "kimi", "zai", "opencode" -> fallback
    "cohere" -> Color(0xFF39594D)
    "xiaomi" -> Color(0xFFFF6700)
    "cline" -> Color(0xFF9D4EDD)
    "devin" -> fallback
    "minimax" -> Color(0xFFF23F5D)
    "doubao" -> Color(0xFF1E37FC)
    "hunyuan" -> Color(0xFF0053E0)
    "nvidia" -> Color(0xFF74B71B)
    "stepfun" -> fallback
    "openrouter" -> Color(0xFF6566F1)
    "zed" -> Color(0xFF4173E7)
    else -> fallback
}

internal fun upstreamToolAsset(name: String): Int? = when (vendorOf(name)) {
    "openai" -> R.drawable.upstream_logo_codex
    "claude" -> R.drawable.upstream_logo_claude
    "cursor" -> R.drawable.upstream_logo_cursor
    "deepseek" -> R.drawable.upstream_logo_deepseek
    "gemini" -> R.drawable.upstream_logo_gemini
    "xai" -> R.drawable.upstream_logo_xai
    "meta" -> R.drawable.upstream_logo_meta
    "mistral" -> R.drawable.upstream_logo_mistral
    "qwen" -> R.drawable.upstream_logo_qwen
    "kimi" -> R.drawable.upstream_logo_kimi
    "zai" -> R.drawable.upstream_logo_zai
    "cohere" -> R.drawable.upstream_logo_cohere
    "xiaomi" -> R.drawable.upstream_logo_xiaomi
    "cline" -> R.drawable.upstream_logo_cline
    "devin" -> R.drawable.upstream_logo_devin
    "minimax" -> R.drawable.upstream_logo_minimax
    "doubao" -> R.drawable.upstream_logo_doubao
    "hunyuan" -> R.drawable.upstream_logo_hunyuan
    "nvidia" -> R.drawable.upstream_logo_nvidia
    "stepfun" -> R.drawable.upstream_logo_stepfun
    "opencode" -> R.drawable.upstream_logo_opencode
    "openrouter" -> R.drawable.upstream_logo_openrouter
    "zed" -> R.drawable.upstream_logo_zed
    else -> null
}

@Composable
internal fun StatusDot(color: Color, size: Dp) {
    Surface(color = color, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.size(size)) {}
}

@Composable
internal fun UpstreamToolMark(name: String, color: Color, size: Dp) {
    val sourceAsset = if (LocalToolIcons.current) upstreamToolAsset(name) else null
    val resolvedColor = if (LocalColorfulToolMarks.current) originalToolColor(name, color) else Ink
    if (sourceAsset == null) {
        StatusDot(resolvedColor, size)
    } else {
        Icon(
            painter = painterResource(sourceAsset),
            contentDescription = null,
            tint = resolvedColor,
            modifier = Modifier.size(size),
        )
    }
}

@Composable
internal fun ModelMark(name: String, color: Color, size: Dp) {
    val sourceAsset = if (LocalToolIcons.current) upstreamToolAsset(name) ?: R.drawable.view_model else null
    val resolvedColor = if (LocalColorfulToolMarks.current) originalToolColor(name, color) else Ink
    if (sourceAsset == null) {
        StatusDot(resolvedColor, size)
    } else {
        Icon(
            painter = painterResource(sourceAsset),
            contentDescription = null,
            tint = resolvedColor,
            modifier = Modifier.size(size),
        )
    }
}

@Composable
internal fun DevicePlatformMark(platform: String, color: Color, size: Dp) {
    val icon = when (platform.lowercase(Locale.US).substringBefore('-')) {
        "win32", "windows" -> R.drawable.upstream_os_windows
        "darwin", "macos", "mac" -> R.drawable.upstream_os_apple
        else -> R.drawable.view_device
    }
    Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(size))
}

internal fun DashboardDestination.iconRes(): Int = when (this) {
    DashboardDestination.Home -> R.drawable.view_home
    DashboardDestination.Tools -> R.drawable.view_tool
    DashboardDestination.Status -> R.drawable.view_status
    DashboardDestination.Devices -> R.drawable.view_device
    DashboardDestination.Models -> R.drawable.view_model
    DashboardDestination.Projects -> R.drawable.view_project
    DashboardDestination.Sessions -> R.drawable.view_session
    DashboardDestination.Limits -> R.drawable.view_limits
    DashboardDestination.Trends -> R.drawable.view_trends
    DashboardDestination.Settings -> R.drawable.action_settings
}
