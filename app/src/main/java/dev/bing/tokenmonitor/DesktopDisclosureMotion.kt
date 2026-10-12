package dev.bing.tokenmonitor

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import kotlin.math.abs

/** Values are the pinned renderer's accordion, settings-panel and disclosure CSS. */
internal enum class DesktopDisclosureStyle {
    ACCORDION, SETTINGS_PANEL
}

internal enum class DesktopDisclosureKind {
    SETTINGS_PANEL, SETTINGS_SECTION, MAIN_SETTINGS, MAIN_HOME, MAIN_SESSIONS,
    PROJECT_DETAILS, DEVICE_DETAILS, BREAKDOWN_DETAILS, GENERIC
}

internal data class DesktopDisclosurePose(
    val heightFraction: Float,
    val opacity: Float,
    val chevronFraction: Float,
    val running: Boolean
)

internal object DesktopDisclosureMotionTokens {
    // CSS ease and the original grid-template-rows curve; no spring or page slide.
    val cssEase: Easing = CubicBezierEasing(.25f, .1f, .25f, 1f)
    val accordionHeightEase: Easing = CubicBezierEasing(.4f, 0f, .2f, 1f)
    const val accordionDurationMs = 250
    const val panelHeightDurationMs = 140
    const val panelOpacityDurationMs = 120
    const val chevronDurationMs = 140
    const val settingsAnchorDurationMs = 360
}

/**
 * A CSS-like scalar transition, including CSS reversal shortening. Start time is
 * captured at the request, before requesting the first Android frame. A new target
 * samples the current timeline rather than restarting from a stale last-drawn value.
 */
internal class DisclosureTransitionChannel(
    initial: Float,
    private val baseDurationMs: Int,
    private val easing: Easing
) {
    var value = initial; private set
    var running = false; private set
    private var from = initial
    private var target = initial
    private var reversingStart = initial
    private var shortening = 1f
    private var eased = 1f
    private var startedAt = 0L
    private var durationNanos = 0.0

    fun retarget(next: Float, animate: Boolean, nowNanos: Long, scale: Float) {
        sample(nowNanos)
        if (!animate || scale <= 0f || !scale.isFinite()) {
            settle(next)
            return
        }
        if (next == target) return
        val reversing = running && next == reversingStart
        val factor = if (reversing) abs(eased * shortening + 1f - shortening).coerceIn(0f, 1f) else 1f
        reversingStart = if (reversing) target else value
        from = value
        target = next
        shortening = factor
        eased = 0f
        startedAt = nowNanos
        durationNanos = baseDurationMs * 1_000_000.0 * scale * factor
        running = from != target && durationNanos > 0.0
        if (!running) settle(next)
    }

    fun sample(nowNanos: Long) {
        if (!running) return
        val elapsed = (nowNanos - startedAt).coerceAtLeast(0L)
        val progress = (elapsed / durationNanos).coerceIn(0.0, 1.0).toFloat()
        eased = easing.transform(progress)
        value = from + (target - from) * eased
        if (progress >= 1f) {
            value = target
            running = false
        }
    }

    fun settle(next: Float) {
        value = next
        from = next
        target = next
        reversingStart = next
        shortening = 1f
        eased = 1f
        running = false
        durationNanos = 0.0
    }
}

/** No frame loop, lifecycle, state persistence or network work lives in this controller. */
internal class DesktopDisclosureMotion(initiallyOpen: Boolean, style: DesktopDisclosureStyle) {
    private val initial = if (initiallyOpen) 1f else 0f
    private val height = DisclosureTransitionChannel(initial,
        if (style == DesktopDisclosureStyle.ACCORDION) DesktopDisclosureMotionTokens.accordionDurationMs
        else DesktopDisclosureMotionTokens.panelHeightDurationMs,
        if (style == DesktopDisclosureStyle.ACCORDION) DesktopDisclosureMotionTokens.accordionHeightEase
        else DesktopDisclosureMotionTokens.cssEase)
    private val opacity = DisclosureTransitionChannel(initial,
        if (style == DesktopDisclosureStyle.ACCORDION) DesktopDisclosureMotionTokens.accordionDurationMs
        else DesktopDisclosureMotionTokens.panelOpacityDurationMs,
        DesktopDisclosureMotionTokens.cssEase)
    private val chevron = DisclosureTransitionChannel(initial,
        DesktopDisclosureMotionTokens.chevronDurationMs, DesktopDisclosureMotionTokens.cssEase)
    var targetOpen = initiallyOpen; private set
    val pose get() = DesktopDisclosurePose(height.value, opacity.value, chevron.value,
        height.running || opacity.running || chevron.running)
    val retained get() = targetOpen || height.value > 0f || opacity.value > 0f || pose.running

    fun retarget(open: Boolean, animate: Boolean, nowNanos: Long, scale: Float = 1f) {
        targetOpen = open
        val next = if (open) 1f else 0f
        height.retarget(next, animate, nowNanos, scale)
        opacity.retarget(next, animate, nowNanos, scale)
        chevron.retarget(next, animate, nowNanos, scale)
    }

    fun frame(nowNanos: Long): DesktopDisclosurePose {
        height.sample(nowNanos)
        opacity.sample(nowNanos)
        chevron.sample(nowNanos)
        return pose
    }

    fun settle(open: Boolean = targetOpen) {
        targetOpen = open
        val next = if (open) 1f else 0f
        height.settle(next)
        opacity.settle(next)
        chevron.settle(next)
    }
}
