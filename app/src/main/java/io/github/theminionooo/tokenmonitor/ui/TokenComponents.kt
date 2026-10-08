package io.github.theminionooo.tokenmonitor.ui

internal data class TokenComponents(val cacheRead: Long, val cacheMiss: Long, val cacheWrite: Long, val output: Long, val unclassified: Long) {
    val input: Long get() = cacheRead + cacheMiss + cacheWrite
    val hitPercent: Double get() = if (input > 0) cacheRead.toDouble() / input * 100 else 0.0
    val missPercent: Double get() = if (input > 0) cacheMiss.toDouble() / input * 100 else 0.0
}
internal fun tokenComponents(total: Long, reads: Long, writes: Long, outputs: Long, unknown: Long): TokenComponents {
    val safeTotal = total.coerceAtLeast(0L)
    val unclassified = unknown.coerceIn(0L, safeTotal)
    val classified = safeTotal - unclassified
    val read = reads.coerceIn(0L, classified)
    val output = outputs.coerceIn(0L, classified - read)
    val write = writes.coerceIn(0L, classified - read - output)
    return TokenComponents(read, classified - read - output - write, write, output, unclassified)
}
