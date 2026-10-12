package dev.bing.tokenmonitor

import java.math.BigDecimal
import java.math.RoundingMode

/** Desktop app.js animateNumber: one interruptible easeOutQuart tween, with exact Long endpoints. */
internal class HeadlineNumberMotion(initial: Long?) {
    var target: Long? = initial
        private set
    // currentTotal is the source's static baseline. An unavailable period resets
    // it without treating the previous private animation fraction as a new start.
    private var currentTotal: Long = initial ?: 0L
    private var value: BigDecimal = currentTotal.toBigDecimal()
    private var from: BigDecimal = value
    var widest: Long? = initial
        private set
    var running = false
        private set
    val displayed: Long? get() = if (target == null) null else if (running)
        value.setScale(0, RoundingMode.HALF_UP).longValueExact() else currentTotal

    /** Returns true only when a fresh timeline is needed. */
    fun retarget(next: Long?, animate: Boolean): Boolean {
        if (next == null) { unavailable(); return false }
        if (!animate) {
            target = next
            settle()
            return false
        }
        if (next == target && running) return false
        val previous = if (running) value else currentTotal.toBigDecimal()
        target = next
        currentTotal = next
        if (previous.compareTo(next.toBigDecimal()) == 0) {
            settle()
            return false
        }
        from = previous
        value = previous
        // Counts are nonnegative. Grouped decimal width grows monotonically with magnitude.
        widest = maxOf(previous.setScale(0, RoundingMode.HALF_UP).longValueExact(), next)
        running = true
        return true
    }

    /** Source early return: unknown fixed total resets to zero; known detail stays static. */
    fun unavailable(knownTotal: Long? = null) {
        target = knownTotal
        currentTotal = knownTotal ?: 0L
        widest = knownTotal
        running = false
    }

    fun frame(progress: Double) {
        if (!running) return
        val p = progress.coerceIn(0.0, 1.0)
        if (p >= 1.0) { settle(); return }
        val remaining = BigDecimal.ONE.subtract(BigDecimal.valueOf(p))
        val eased = BigDecimal.ONE.subtract(remaining.pow(4))
        val start = from
        // Bound the fractional tail across repeated interruptions. Whole Long digits stay exact;
        // carrying every decimal from successive quartic tweens would grow without limit.
        value = start.add(target!!.toBigDecimal().subtract(start).multiply(eased)).setScale(12,RoundingMode.HALF_UP)
    }

    fun settle() {
        currentTotal = target ?: 0L
        if (target != null) value = currentTotal.toBigDecimal()
        from = value
        widest = target
        running = false
    }
}

/** Start is sampled before requesting a frame, in the frame clock's monotonic domain. */
internal class HeadlineNumberTimeline {
    private var startedAtNanos = 0L
    private var durationNanos = 1_000_000_000L
    fun start(nowNanos: Long, durationMs: Int) {
        startedAtNanos = nowNanos
        durationNanos = durationMs * 1_000_000L
    }
    fun progress(nowNanos: Long, scale: Float): Double = if (scale <= 0f) 1.0 else
        ((nowNanos - startedAtNanos).toDouble() / (durationNanos.toDouble() * scale)).coerceIn(0.0, 1.0)
}
