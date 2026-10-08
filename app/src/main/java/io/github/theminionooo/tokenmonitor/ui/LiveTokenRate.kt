package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.UsagePeriod

/** Counters are Hub performance measurements, not wall-clock estimates or lifetime averages. */
internal data class LiveRateSample(val speed: Double, val burn: Double, val sampledAt: Long)
internal class LiveTokenRateTracker {
    private var baseline: UsagePeriod? = null
    private var sample: LiveRateSample? = null
    fun reset() { baseline = null; sample = null }
    fun observe(period: UsagePeriod?, now: Long): LiveRateSample? {
        if (period == null || !period.throughputAvailable) { reset(); return null }
        val previous = baseline
        baseline = period
        if (previous == null) return null
        val tokens = period.timedTokens - previous.timedTokens
        val output = period.timedOutputTokens - previous.timedOutputTokens
        val duration = period.timedDurationMs - previous.timedDurationMs
        if (tokens < 0 || output < 0 || duration < 0) { sample = null; return null }
        if (duration > 0) sample = LiveRateSample(output * 1000.0 / duration, tokens * 60000.0 / duration, now)
        return sample
    }
}
