package io.github.theminionooo.tokenmonitor.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Formatters are built once; they are used on every frame while a total rolls.
private val wholeNumberFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.US)
private val currencyFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)
private val dateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
private val clockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val shortDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")
internal val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

internal fun formatTokens(tokens: Long): String = wholeNumberFormat.format(tokens)

internal fun formatCompactTokens(tokens: Long): String = when {
    tokens >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", tokens / 1_000_000_000.0)
    tokens >= 1_000_000L -> String.format(Locale.US, "%.1fM", tokens / 1_000_000.0)
    tokens >= 10_000L -> String.format(Locale.US, "%.1fK", tokens / 1_000.0)
    else -> formatTokens(tokens)
}

internal fun formatMoney(value: Double): String = currencyFormat.format(value)

internal fun formatSubscriptionAmount(amountMinor: Long, currency: String): String = if (currency == "USD") {
    formatMoney(amountMinor / 100.0)
} else {
    "$currency ${wholeNumberFormat.format(amountMinor / 100.0)}"
}

internal fun formatWindowAmount(value: Double, currency: String): String = when {
    currency.equals("USD", ignoreCase = true) -> formatMoney(value)
    currency.isNotBlank() -> "$currency ${wholeNumberFormat.format(value)}"
    else -> wholeNumberFormat.format(value)
}

/** The desktop `formatDuration`: `2d 21h`, `1h 59m`, `5m`, or `<1m`, rounded to the minute. */
internal fun formatDuration(milliseconds: Long): String {
    val totalMinutes = Math.round(milliseconds.coerceAtLeast(0L) / 60_000.0)
    val days = totalMinutes / 1_440
    val hours = (totalMinutes % 1_440) / 60
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

/** The desktop `formatActiveDuration`: total hours and minutes, such as `542h 28m`. */
internal fun formatActiveDuration(milliseconds: Long): String {
    val totalMinutes = Math.round(milliseconds.coerceAtLeast(0L) / 60_000.0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/** The desktop `formatReset`: a countdown such as `Reset 1h 59m`, `Reset now` once due, or nothing without a timestamp. */
internal fun formatReset(resetsAt: String, now: Long): String {
    val target = runCatching { Instant.parse(resetsAt).toEpochMilli() }.getOrNull() ?: return ""
    val remaining = target - now
    return if (remaining <= 0) "Reset now" else "Reset ${formatDuration(remaining)}"
}

/** v0.56 keeps one timestamp but tells readers whether it resets, expires, or does both. */
internal fun formatBoundary(resetsAt: String, boundaryKind: String, now: Long): String {
    val target = runCatching { Instant.parse(resetsAt).toEpochMilli() }.getOrNull() ?: return ""
    val remaining = target - now
    val suffix = if (remaining <= 0) "now" else formatDuration(remaining)
    return when (boundaryKind.lowercase()) {
        "expiry" -> "Expires $suffix"
        "mixed" -> "Changes${if (remaining <= 0) "" else " in"} $suffix"
        else -> "Reset $suffix"
    }
}

/** Desktop-style age such as `38s ago`, `5m ago`, `2h 10m ago`, or `3d 4h ago`. */
internal fun formatRelativeAge(timestamp: Long, now: Long): String {
    if (timestamp <= 0) return ""
    val age = (now - timestamp).coerceAtLeast(0L)
    return when {
        age < 5_000 -> "just now"
        age < 60_000 -> "${age / 1_000}s ago"
        else -> "${formatDuration(age)} ago"
    }
}

internal fun String.relativeAge(now: Long): String =
    runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()?.let { formatRelativeAge(it, now) }.orEmpty()

internal fun formatPercent(value: Double): String = "${value.toInt()}%"

internal fun formatHubBuild(value: String): String {
    if (value.isBlank()) return "Not reported"
    val normalized = value.removePrefix("sha256:")
    return if (normalized.length > 14) "${normalized.take(14)}…" else normalized
}

internal fun formatCapturedAt(timestamp: Long): String = if (timestamp <= 0) "an unknown time" else runCatching {
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(dateTimeFormat)
}.getOrDefault("recently")

internal fun String.shortTime(): String = runCatching {
    Instant.parse(this).atZone(ZoneId.systemDefault()).format(dateTimeFormat)
}.getOrDefault("")

internal fun String.shortClockTime(): String = runCatching {
    Instant.parse(this).atZone(ZoneId.systemDefault()).format(clockFormat)
}.getOrDefault("")

internal fun shortDate(value: String): String = runCatching {
    LocalDate.parse(value.take(10)).format(shortDateFormat)
}.getOrDefault(value.take(5))

/** The desktop renderer's tool labels, keyed by the Hub's client id. */
private val toolLabels = mapOf(
    "claude" to "Claude Code", "codex" to "Codex", "hermes" to "Hermes Agent", "gemini" to "Gemini", "cursor" to "Cursor",
    "opencode" to "OpenCode", "openclaw" to "OpenClaw", "antigravity" to "Antigravity", "cline" to "Cline", "kimi" to "Kimi",
    "amp" to "Amp", "droid" to "Factory Droid", "qwen" to "Qwen", "grok" to "Grok Build", "copilot" to "GitHub Copilot",
    "pi" to "Pi", "omp" to "Oh My Pi", "zed" to "Zed", "kilo" to "Kilo", "kilocode" to "Kilo Code", "commandcode" to "Command Code",
    "mimo" to "Xiaomi MiMo", "micode" to "Xiaomi MiMo", "zcode" to "ZCode", "kiro" to "Kiro", "codebuddy" to "CodeBuddy",
    "workbuddy" to "WorkBuddy", "proma" to "Proma", "qodercn" to "Qoder CN", "reasonix" to "Reasonix", "dsh" to "DeepSeek Harness",
    "cherrystudio" to "Cherry Studio", "lmstudio" to "LM Studio", "unsloth" to "Unsloth", "devin" to "Devin", "openrouter" to "OpenRouter",
    "deepseek" to "DeepSeek", "ollama" to "Ollama", "thirdparty" to "Third-party",
    "factory" to "Factory", "nvidia" to "NVIDIA", "stepfun" to "StepFun", "muse" to "Muse Code",
)

/**
 * Tool ids get the desktop's label, model ids stay exactly as the Hub reports them
 * (the desktop prints `gpt-5.6-sol`, not `Gpt-5.6-sol`), and anything else is title-cased.
 */
internal fun String.displayName(): String {
    val key = trim().lowercase(Locale.US)
    toolLabels[key]?.let { return it }
    if (any { it.isDigit() } || contains('/')) return trim()
    return providerName()
}

/** The desktop's period words for a limit window kind, used when two windows share a name. */
internal fun windowPeriodLabel(kind: String): String = when (kind.trim().lowercase(Locale.US)) {
    "session" -> "5-hour"
    "daily" -> "Daily"
    "weekly" -> "Weekly"
    "monthly" -> "Monthly"
    "billing" -> "Billing"
    else -> kind.providerName()
}

/**
 * A window's row title. Codex reports separately metered quotas such as GPT-5.3-Codex-Spark
 * as two windows with the same label and different kinds, so when a label repeats within an
 * account the period is appended: `GPT-5.3-Codex-Spark · 5-hour` and `GPT-5.3-Codex-Spark · Weekly`.
 */
internal fun windowTitle(window: io.github.theminionooo.tokenmonitor.domain.LimitWindow, siblings: List<io.github.theminionooo.tokenmonitor.domain.LimitWindow>): String {
    val base = window.label.ifBlank { window.kind.ifBlank { "Limit" } }.displayName()
    val shared = siblings.count { it !== window && it.label.ifBlank { it.kind.ifBlank { "Limit" } }.displayName() == base } > 0
    return if (shared && window.kind.isNotBlank()) "$base · ${windowPeriodLabel(window.kind)}" else base
}

/** Plain title case for provider and platform names, where the desktop shows `Claude` rather than `Claude Code`. */
internal fun String.providerName(): String = replace('_', ' ').split(' ').joinToString(" ") { part ->
    part.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
}

internal val Int.absoluteValue: Int get() = if (this == Int.MIN_VALUE) 0 else kotlin.math.abs(this)

/** Limit and subscription providers: the desktop shows `Claude`, not `Claude Code`, but keeps labels such as `OpenCode`. */
private val providerLabels = mapOf(
    "claude" to "Claude",
    "factory" to "Factory Droid",
    "grok" to "Grok",
    "mimo" to "Xiaomi MiMo",
    "zai" to "GLM",
    "zaiteam" to "GLM Team",
    "qoder" to "Qoder",
    "devin" to "Devin",
    "typesafe" to "TypeSafe",
    "volcengine" to "Volcengine",
    "trae" to "Trae CN",
    "alibaba" to "Alibaba Cloud",
    "thirdparty" to "Third-party APIs",
)

internal fun String.providerLabel(): String {
    val key = trim().lowercase(Locale.US)
    return providerLabels[key] ?: displayName()
}
