package dev.bing.tokenmonitor

/** A logical history target. Transport changes between the same Hub's two URLs do not change it. */
data class HistoryReadKey(val connectionRevision: Long, val signature: String)

enum class HistoryReadPhase { CACHED, QUEUED, STARTED, RESOLVED, FAILED, CANCELLED, STALE, AUTH_BLOCKED }

/** Only an actual history read produces STARTED/terminal receipts; other API loading is unrelated. */
data class HistoryReadReceipt(
    val key: HistoryReadKey,
    val cycleId: Long,
    val requestId: Long?,
    val attempt: Int,
    val phase: HistoryReadPhase,
    val previewHadDays: Boolean = false,
    val fetchedDays: Int? = null,
    val bindingRequested: Boolean = false
) {
    val accepted: Boolean get() = phase == HistoryReadPhase.CACHED ||
        (phase == HistoryReadPhase.RESOLVED && fetchedDays != null && (fetchedDays > 0 || !previewHadDays))

    /** Eligibility only; the visible-screen policy supplies the four-second retry wait. */
    val allowsAutomaticRead: Boolean get() = !accepted && phase != HistoryReadPhase.STARTED &&
        phase != HistoryReadPhase.AUTH_BLOCKED && attempt < 4 &&
        !(key.signature.isEmpty() && bindingRequested) &&
        (attempt == 0 || previewHadDays || phase == HistoryReadPhase.CANCELLED || phase == HistoryReadPhase.STALE)
}

/** Owned by the ViewModel so rebuilding a screen cannot reset accepted request counts. */
internal class HistoryReadCoordinator {
    var receipt: HistoryReadReceipt? = null
        private set
    private var cycleSequence = 0L
    private var bindingRevision: Long? = null
    private var bindingRequested = false

    fun target(key: HistoryReadKey, trustedCachedDays: Int? = null): HistoryReadReceipt {
        receipt?.takeIf { it.key == key }?.let { return it }
        if (bindingRevision != key.connectionRevision) {
            bindingRevision = key.connectionRevision
            bindingRequested = false
        }
        return HistoryReadReceipt(key, ++cycleSequence, null, 0,
            if (trustedCachedDays != null) HistoryReadPhase.CACHED else HistoryReadPhase.QUEUED,
            fetchedDays = trustedCachedDays, bindingRequested = bindingRequested).also { receipt = it }
    }

    /** A successful Home stats refresh opens a new cycle but is not a history network start. */
    fun queue(key: HistoryReadKey): HistoryReadReceipt {
        if (receipt?.key != key) return target(key)
        return HistoryReadReceipt(key, ++cycleSequence, null, 0, HistoryReadPhase.QUEUED,
            bindingRequested = bindingRequested).also { receipt = it }
    }

    fun canStart(key: HistoryReadKey, manual: Boolean): Boolean {
        val current = receipt?.takeIf { it.key == key } ?: return false
        return current.phase != HistoryReadPhase.STARTED && (manual || current.allowsAutomaticRead)
    }

    /** Call only after the transport's foreground/loading/authentication gates accept the start. */
    fun begin(key: HistoryReadKey, requestId: Long, manual: Boolean, previewHadDays: Boolean): HistoryReadReceipt? {
        if (!canStart(key, manual)) return null
        val previous = receipt!!
        bindingRequested = true
        return HistoryReadReceipt(key, if (manual) ++cycleSequence else previous.cycleId,
            requestId, if (manual) 1 else previous.attempt + 1, HistoryReadPhase.STARTED,
            previewHadDays, bindingRequested = true).also { receipt = it }
    }

    fun matches(start: HistoryReadReceipt): Boolean = receipt?.let {
        it.phase == HistoryReadPhase.STARTED && it.key == start.key &&
            it.cycleId == start.cycleId && it.requestId == start.requestId
    } == true

    fun finish(start: HistoryReadReceipt, phase: HistoryReadPhase, fetchedDays: Int? = null): HistoryReadReceipt? {
        require(phase in setOf(HistoryReadPhase.RESOLVED, HistoryReadPhase.FAILED, HistoryReadPhase.CANCELLED,
            HistoryReadPhase.STALE, HistoryReadPhase.AUTH_BLOCKED))
        require(if (phase == HistoryReadPhase.RESOLVED) fetchedDays != null && fetchedDays >= 0 else fetchedDays == null)
        if (!matches(start)) return null
        return start.copy(phase = phase, fetchedDays = fetchedDays).also { receipt = it }
    }

    fun cancelActive(phase: HistoryReadPhase = HistoryReadPhase.CANCELLED): HistoryReadReceipt? {
        val current = receipt?.takeIf { it.phase == HistoryReadPhase.STARTED } ?: return null
        return finish(current, phase)
    }

    /** Successful connection validation unblocks the same cycle without refunding its attempts. */
    fun revalidated(): HistoryReadReceipt? {
        val current = receipt?.takeIf { it.phase == HistoryReadPhase.AUTH_BLOCKED } ?: return receipt
        return current.copy(phase = HistoryReadPhase.CANCELLED).also { receipt = it }
    }

    fun reset() { receipt = null; bindingRevision = null; bindingRequested = false }
}
