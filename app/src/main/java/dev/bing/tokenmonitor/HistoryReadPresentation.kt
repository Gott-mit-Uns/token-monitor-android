package dev.bing.tokenmonitor

/** History-owned status. Shared detail requests must not reclassify accepted history gaps. */
internal data class HistoryReadPresentation(val loading: Boolean = false, val error: Int? = null) {
    companion object {
        fun project(key: HistoryReadKey, receipt: HistoryReadReceipt?): HistoryReadPresentation {
            if (receipt?.key != key) return HistoryReadPresentation()
            return when (receipt.phase) {
                HistoryReadPhase.STARTED -> HistoryReadPresentation(loading = true)
                HistoryReadPhase.FAILED -> HistoryReadPresentation(error = R.string.record_read_failed)
                HistoryReadPhase.AUTH_BLOCKED -> HistoryReadPresentation(error = R.string.error_stream_auth)
                // Queued has not started a read; cancellation/stale replies are not read failures.
                else -> HistoryReadPresentation()
            }
        }
    }
}
