package dev.bing.tokenmonitor

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.abs
import kotlin.math.min

internal data class DashboardChartRect(val x: Double, val y: Double, val width: Double, val height: Double)
internal data class DashboardBarTarget(val key: String, val rect: DashboardChartRect)
internal data class DashboardCandleTarget(
    val key: String, val body: DashboardChartRect?, val wickX: Double,
    val wickHighEndY: Double?, val wickLowEndY: Double?, val wickMiddleY: Double? = null
)
internal data class DashboardChartTargets(
    val mode: DashboardTrendMode, val width: Double, val height: Double,
    val bars: List<DashboardBarTarget>, val candles: List<DashboardCandleTarget>
)
internal data class DashboardBarTransform(
    val originX: Double = 0.0, val originY: Double = 0.0,
    val scaleX: Double = 1.0, val scaleY: Double = 1.0,
    val translateX: Double = 0.0, val translateY: Double = 0.0
)
internal data class DashboardCandleProgress(
    val bodyScaleY: Double = 1.0, val highWickProgress: Double = 1.0, val lowWickProgress: Double = 1.0
)
internal data class DashboardChartFrame(
    val targets: DashboardChartTargets? = null,
    val bars: Map<String, DashboardBarTransform> = emptyMap(),
    val candles: Map<String, DashboardCandleProgress> = emptyMap(),
    val running: Boolean = false
) {
    val mode: DashboardTrendMode get() = targets?.mode ?: DashboardTrendMode.BARS
}

/** Original Dashboard FLIP bars and candle entry. Main-window Trends has a different 420ms timeline. */
internal class DashboardChartMotion {
    private data class BarTween(val target: DashboardBarTarget, val from: DashboardBarTransform,
        val startedNanos: Long, val delayMs: Int, val scale: Float)
    private data class CandleTween(val target: DashboardCandleTarget, val startedNanos: Long,
        val delayMs: Int, val scale: Float)
    private var barTweens = emptyMap<String, BarTween>()
    private var candleTweens = emptyMap<String, CandleTween>()
    private var eventSeen = false
    private var previousEvent: Any? = null
    private var consumedRevision: Int? = null
    var frame = DashboardChartFrame()
        private set
    val running: Boolean get() = barTweens.isNotEmpty() || candleTweens.isNotEmpty()
    val hasVisibleMotion: Boolean get() = barTweens.values.any { it.target.rect.width > 0 && it.target.rect.height > 0 } ||
        candleTweens.values.any { it.target.body?.let { body -> body.width > 0 && body.height > 0 } == true }

    /** reason belongs to an explicit UI revision, not every later history render using the same sticky reason. */
    fun render(
        targets: DashboardChartTargets?, renderEventKey: Any?, motionRevision: Int,
        reason: DashboardMotionReason, nowNanos: Long, animate: Boolean,
        consumeReason: Boolean = true, durationScale: Float = 1f
    ): Boolean {
        advance(nowNanos, durationScale)
        val dataChanged = !eventSeen || previousEvent != renderEventKey
        val targetsChanged = frame.targets != targets
        val requested = consumedRevision != motionRevision && consumeReason
        if (!dataChanged && !targetsChanged && !requested) {
            if (!animate || !validScale(durationScale)) settle()
            return false
        }
        val previous = frame
        val effective = if (requested) reason else if (dataChanged) DashboardMotionReason.UPDATE else DashboardMotionReason.NONE
        eventSeen = true
        previousEvent = renderEventKey
        if (consumeReason) consumedRevision = motionRevision
        if (targets == null) {
            barTweens = emptyMap(); candleTweens = emptyMap(); frame = DashboardChartFrame()
            return true
        }
        val identityBars = targets.bars.associate { it.key to identity(it.rect) }
        val identityCandles = targets.candles.associate { it.key to DashboardCandleProgress() }
        frame = DashboardChartFrame(targets, identityBars, identityCandles)
        barTweens = emptyMap(); candleTweens = emptyMap()
        if (!animate || !validScale(durationScale) || effective == DashboardMotionReason.NONE) return true
        if (targets.mode == DashboardTrendMode.KLINE) {
            // Original animateCandles ignores previous geometry and fromZero: every allowed update replays.
            candleTweens = targets.candles.mapIndexedNotNull { index, target ->
                if (target.body == null) null else target.key to CandleTween(target, nowNanos, min(index, 18) * 10, durationScale)
            }.toMap()
        } else {
            val fromZero = effective == DashboardMotionReason.ENTRY || effective == DashboardMotionReason.SERIES ||
                previous.mode != DashboardTrendMode.BARS
            val oldRects = if (fromZero) emptyMap() else displayedBars(previous)
            barTweens = targets.bars.mapIndexedNotNull { index, target ->
                val rect = target.rect
                val old = oldRects[target.key]
                val first = if (old != null && rect.width > 0 && rect.height > 0) {
                    val sx = old.width / rect.width; val sy = old.height / rect.height
                    val dx = old.x - rect.x; val dy = old.y - rect.y
                    if (abs(dx) < .5 && abs(dy) < .5 && abs(sx - 1) < .01 && abs(sy - 1) < .01) return@mapIndexedNotNull null
                    DashboardBarTransform(rect.x, rect.y, sx, sy, dx, dy)
                } else DashboardBarTransform(rect.x + rect.width / 2, rect.y + rect.height, scaleY = 0.0)
                target.key to BarTween(target, first, nowNanos, if (old != null) 0 else min(index, 18) * 12, durationScale)
            }.toMap()
        }
        publish(nowNanos)
        return true
    }

    fun advance(nowNanos: Long, durationScale: Float = 1f) {
        if (!validScale(durationScale)) { settle(); return }
        if (running) publish(nowNanos)
    }

    fun settle() {
        barTweens = emptyMap(); candleTweens = emptyMap()
        val targets = frame.targets
        frame = DashboardChartFrame(targets, targets?.bars?.associate { it.key to identity(it.rect) }.orEmpty(),
            targets?.candles?.associate { it.key to DashboardCandleProgress() }.orEmpty())
    }

    /** Captures displayed transformed bounds, not the previous endpoint, just like getBoundingClientRect. */
    fun displayedBars(current: DashboardChartFrame = frame): Map<String, DashboardChartRect> =
        current.targets?.bars.orEmpty().mapNotNull { target ->
            val rect = target.rect; val transform = current.bars[target.key] ?: identity(rect)
            val displayed = DashboardChartRect(
                transform.originX + (rect.x - transform.originX) * transform.scaleX + transform.translateX,
                transform.originY + (rect.y - transform.originY) * transform.scaleY + transform.translateY,
                rect.width * transform.scaleX, rect.height * transform.scaleY)
            if (displayed.width > 0 && displayed.height > 0) target.key to displayed else null
        }.toMap()

    private fun publish(nowNanos: Long) {
        val targets = frame.targets ?: return
        val bars = targets.bars.associate { target ->
            val tween = barTweens[target.key]
            val value = if (tween == null) identity(target.rect) else {
                val progress = progress(nowNanos, tween.startedNanos, tween.delayMs, 800, tween.scale)
                val eased = easing.transform(progress.toFloat()).toDouble()
                tween.from.copy(scaleX = mix(tween.from.scaleX, 1.0, eased), scaleY = mix(tween.from.scaleY, 1.0, eased),
                    translateX = mix(tween.from.translateX, 0.0, eased), translateY = mix(tween.from.translateY, 0.0, eased))
            }
            target.key to value
        }
        val candles = targets.candles.associate { target ->
            val tween = candleTweens[target.key]
            val value = if (tween == null) DashboardCandleProgress() else {
                val eased = easing.transform(progress(nowNanos, tween.startedNanos, tween.delayMs, 560, tween.scale).toFloat()).toDouble()
                // SVG rounds the raw wick midpoint independently from the rect's rounded y/height.
                val middle = target.wickMiddleY ?: (target.body!!.y + target.body.height / 2)
                DashboardCandleProgress(eased,
                    if (target.wickHighEndY != null && abs(target.wickHighEndY - middle) > 0) eased else 1.0,
                    if (target.wickLowEndY != null && abs(target.wickLowEndY - middle) > 0) eased else 1.0)
            }
            target.key to value
        }
        barTweens = barTweens.filterValues { progress(nowNanos, it.startedNanos, it.delayMs, 800, it.scale) < 1 }
        candleTweens = candleTweens.filterValues { progress(nowNanos, it.startedNanos, it.delayMs, 560, it.scale) < 1 }
        frame = DashboardChartFrame(targets, bars, candles, running)
    }

    // Capture a positive Android duration ratio at start, avoiding progress moving backward on a 1→2 settings change.
    // A later zero/invalid scale still settles immediately; source browser preferences are binary, not positive ratios.
    private fun progress(now: Long, start: Long, delayMs: Int, durationMs: Int, scale: Float): Double =
        ((now - start).coerceAtLeast(0).toDouble() / (1_000_000.0 * scale) - delayMs)
            .div(durationMs).coerceIn(0.0, 1.0)
    private fun validScale(scale: Float) = scale.isFinite() && scale > 0f
    private fun identity(rect: DashboardChartRect) = DashboardBarTransform(rect.x, rect.y)
    private fun mix(from: Double, to: Double, progress: Double) = from + (to - from) * progress

    companion object {
        private val easing = CubicBezierEasing(.22f, 1f, .36f, 1f)
        fun targets(model: DashboardTrendModel): DashboardChartTargets {
            fun round(value: Double) = DashboardTrendProjection.svgRound(value)
            val bars = model.bars.map { bar ->
                var left = Double.POSITIVE_INFINITY
                var top = Double.POSITIVE_INFINITY
                var right = Double.NEGATIVE_INFINITY
                var bottom = Double.NEGATIVE_INFINITY
                for (index in bar.segments.indices) {
                    val segment = bar.segments[index]
                    if (segment.height > 0) {
                        val x = round(segment.x); val y = round(segment.y)
                        // The source path rounds each raw endpoint, unlike rect's independently rounded x/y/w/h.
                        val edgeX = if (index == bar.segments.lastIndex) round(segment.x + segment.width) else x + round(segment.width)
                        val edgeY = if (index == bar.segments.lastIndex) round(segment.y + segment.height) else y + round(segment.height)
                        left = minOf(left, x); top = minOf(top, y)
                        right = maxOf(right, edgeX); bottom = maxOf(bottom, edgeY)
                    }
                }
                if (!left.isFinite()) {
                    left = round(bar.x); top = model.plot.y + model.plot.height
                    right = left + round(bar.width); bottom = top
                }
                DashboardBarTarget(bar.label, DashboardChartRect(left, top, right - left, bottom - top))
            }
            val candles = model.candles.map { candle ->
                val body = if (candle.bodyY != null && candle.bodyHeight != null) DashboardChartRect(
                    round(candle.x), round(candle.bodyY), round(candle.width), round(candle.bodyHeight)) else null
                val wickMiddle = if (candle.bodyY != null && candle.bodyHeight != null)
                    round(candle.bodyY + candle.bodyHeight / 2) else null
                DashboardCandleTarget(candle.key, body, round(candle.wickX), candle.yHigh?.let(::round),
                    candle.yLow?.let(::round), wickMiddle)
            }
            return DashboardChartTargets(model.mode, model.width, model.height, bars, candles)
        }
    }
}
