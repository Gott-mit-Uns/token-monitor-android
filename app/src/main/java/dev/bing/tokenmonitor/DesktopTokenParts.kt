package dev.bing.tokenmonitor

data class DesktopTokenParts(val cacheRead: Long, val cacheMiss: Long, val output: Long, val unclassified: Long, val hitPct: Int, val missPct: Int) {
    companion object {
        /** Exact desktop tokenComponentBreakdown contract. Cache writes are part of cache miss. */
        fun source(totalTokens: Long, cacheReadTokens: Long = 0, outputTokens: Long = 0, unclassifiedTokens: Long = 0): DesktopTokenParts {
            val total = totalTokens.coerceAtLeast(0)
            val unknown = unclassifiedTokens.coerceIn(0, total)
            val classified = total - unknown
            val read = cacheReadTokens.coerceIn(0, classified)
            val output = outputTokens.coerceIn(0, classified - read)
            val miss = classified - read - output
            val input = read + miss
            val hit = if (input > 0) Math.round(read.toDouble() / input * 100).toInt() else 0
            return DesktopTokenParts(read, miss, output, unknown, hit, if (input > 0) 100 - hit else 0)
        }
        fun known(total: Long?, components: TokenComponents): TokenComponents {
            if (total == null || components.cacheRead == null || components.output == null || components.unclassified == null) return components.copy(inputMiss = null)
            val parts = source(total, components.cacheRead, components.output, components.unclassified)
            return TokenComponents(parts.cacheRead, components.cacheWrite, parts.output, parts.unclassified, parts.cacheMiss)
        }
    }
}
