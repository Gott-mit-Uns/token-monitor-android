package dev.bing.tokenmonitor

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.abs
import kotlin.math.floor

/** Source SVG target height in the original 120-unit coordinate system. Zero keys remain present. */
internal data class TrendsBarSpec(val key: String, val height: Double)
internal data class TrendsBarFrame(
    val targets: List<TrendsBarSpec> = emptyList(),
    val scales: List<Float> = emptyList(),
    val running: Boolean = false
)

/** Desktop app.js captureTrendBarMotion/animateTrendBarsFrom, independent of rendering and clocks. */
internal class TrendsBarMotion {
    private data class Tween(val from: Double, val delayNanos: Long)
    private var viewKey: String? = null
    private var hasEvent = false
    private var eventKey: Any? = null
    private var startedAt = 0L
    private var tweens: List<Tween?> = emptyList()
    var frame = TrendsBarFrame()
        private set
    var entryPending = true
        private set
    val running get() = frame.running
    // Invisible zero-height animations retain source state, but need no continuous Canvas redraws.
    val hasVisibleMotion get() = tweens.indices.any { tweens[it] != null && frame.targets[it].height > 0.0 }

    fun mainView(key: String) {
        if (key != viewKey) {
            viewKey = key
            entryPending = true
        }
    }

    /**
     * A data render event is distinct from an ordinary Compose commit. Hidden updates settle
     * without consuming entry; null means fixed data is unavailable and retains old geometry.
     */
    fun render(
        targets: List<TrendsBarSpec>?,
        eventKey: Any?,
        nowNanos: Long,
        animate: Boolean,
        consumeEntry: Boolean = true,
        durationScale: Float = 1f
    ): Boolean {
        if (targets == null) {
            settle()
            return false
        }
        val entering = entryPending && consumeEntry && targets.isNotEmpty()
        if (hasEvent && this.eventKey == eventKey && frame.targets == targets && !entering) return false
        advance(nowNanos, durationScale)
        val previous = frame.targets.indices.associate { index ->
            frame.targets[index].key to frame.targets[index].height * frame.scales[index]
        }
        val enabled = animate && durationScale > 0f
        startedAt = nowNanos
        tweens = targets.mapIndexed { index, target ->
            val oldHeight = previous[target.key]
            val from = when {
                entering || oldHeight == null -> 0.0
                target.height > 0.0 -> oldHeight / target.height
                else -> 1.0
            }
            if (!enabled || abs(from - 1.0) < .001) null
            else Tween(from, if (oldHeight != null && !entering) 0L else minOf(index, 14) * 14_000_000L)
        }
        this.eventKey = eventKey
        hasEvent = true
        if (targets.isNotEmpty() && consumeEntry) entryPending = false
        frame = TrendsBarFrame(targets.toList(), tweens.map { it?.from?.toFloat() ?: 1f }, tweens.any { it != null })
        return true
    }

    fun advance(nowNanos: Long, durationScale: Float = 1f) {
        if (!running) return
        if (durationScale <= 0f || !durationScale.isFinite()) {
            settle()
            return
        }
        val elapsed = (nowNanos - startedAt).coerceAtLeast(0L).toDouble() / durationScale
        val scales = tweens.map { tween ->
            if (tween == null) 1f else {
                val progress = ((elapsed - tween.delayNanos) / durationNanos).coerceIn(0.0, 1.0)
                if (progress >= 1.0) 1f
                else (tween.from + (1.0 - tween.from) * easing.transform(progress.toFloat())).toFloat()
            }
        }
        tweens = tweens.map { tween ->
            tween?.takeIf { elapsed < it.delayNanos + durationNanos }
        }
        frame = TrendsBarFrame(frame.targets, scales, tweens.any { it != null })
    }

    fun settle() {
        tweens = frame.targets.map { null }
        frame = TrendsBarFrame(frame.targets, frame.targets.map { 1f }, false)
    }

    companion object {
        private const val durationNanos = 420_000_000L
        private val easing = CubicBezierEasing(.22f, 1f, .36f, 1f)

        fun targets(points: List<TrendsMainPoint>): List<TrendsBarSpec> {
            val max = points.maxOfOrNull { it.tokens }?.coerceAtLeast(1)?.toDouble() ?: 1.0
            return points.mapIndexed { index, point ->
                TrendsBarSpec(point.label.ifEmpty { index.toString() },
                    (floor(120.0 * point.tokens / max * 100 + .5) / 100).coerceAtLeast(0.0))
            }
        }
    }
}
