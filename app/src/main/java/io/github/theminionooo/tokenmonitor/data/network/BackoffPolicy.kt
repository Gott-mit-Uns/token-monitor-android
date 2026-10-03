package io.github.theminionooo.tokenmonitor.data.network

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

class BackoffPolicy(
    private val random: Random = Random.Default,
    private val initialDelayMs: Long = 1_000,
    private val maxDelayMs: Long = 60_000,
) {
    fun delayForAttempt(attempt: Int): Long {
        val exponent = min(attempt.coerceAtLeast(0), 16)
        val capped = min(initialDelayMs * 2.0.pow(exponent.toDouble()), maxDelayMs.toDouble())
        // Keep reconnects from synchronizing after a Hub or network restart.
        val jitter = 0.8 + (random.nextDouble() * 0.4)
        return (capped * jitter).toLong().coerceIn(1L, maxDelayMs)
    }
}
