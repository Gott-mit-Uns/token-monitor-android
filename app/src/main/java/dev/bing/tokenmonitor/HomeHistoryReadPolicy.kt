package dev.bing.tokenmonitor

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import java.security.MessageDigest

/** Request counts and outcomes belong to the ViewModel; this object owns only visible wait time. */
internal class HomeHistoryReadSchedule(private val key: HistoryReadKey) {
    val attempts: Int get() = receipt?.attempt ?: 0
    private var active = false
    private var loading = false
    private var receipt: HistoryReadReceipt? = null
    private var dispatchPending = false
    private var retryAt: Long? = null

    fun observe(nowMs: Long, active: Boolean, loading: Boolean, receipt: HistoryReadReceipt?) {
        this.active = active
        this.loading = loading
        if (receipt != null && receipt.key == key && accepts(receipt)) {
            if (this.receipt != receipt) {
                this.receipt = receipt
                dispatchPending = false
                retryAt = null
            }
        }
        if (!active) {
            // Returning to the foreground or the history page always starts a fresh full wait.
            retryAt = null
            return
        }
        val current = this.receipt
        if (!dispatchPending && current?.allowsAutomaticRead == true && current.attempt > 0 && retryAt == null) {
            retryAt = nowMs + RETRY_DELAY_MS
        }
    }

    private fun accepts(next: HistoryReadReceipt): Boolean {
        val previous = receipt ?: return true
        if (next.cycleId != previous.cycleId) return next.cycleId > previous.cycleId
        if (next.attempt != previous.attempt) return next.attempt > previous.attempt
        if (next.requestId != previous.requestId) return false
        return previous == next || previous.phase == HistoryReadPhase.STARTED ||
            (previous.phase == HistoryReadPhase.AUTH_BLOCKED && next.phase == HistoryReadPhase.CANCELLED)
    }

    fun ready(nowMs: Long): Boolean {
        val current = receipt ?: return false
        return active && !loading && !dispatchPending && current.allowsAutomaticRead &&
            (current.attempt == 0 || retryAt?.let { nowMs >= it } == true)
    }

    /** Called only after the action accepts a real start. The STARTED receipt owns its attempt. */
    fun requested(nowMs: Long): Boolean {
        if (!ready(nowMs)) return false
        dispatchPending = true
        retryAt = null
        return true
    }

    fun waitMs(nowMs: Long): Long? = if (active && !loading && !dispatchPending && receipt?.allowsAutomaticRead == true)
        retryAt?.let { (it - nowMs).coerceAtLeast(0) } else null

    fun pause() { active = false; retryAt = null }

    companion object { const val RETRY_DELAY_MS = 4000L }
}

internal interface HomeHistoryReadClock {
    fun nowMs(): Long
    suspend fun waitMs(durationMs: Long)
}
private object AndroidHomeHistoryReadClock : HomeHistoryReadClock {
    override fun nowMs() = SystemClock.elapsedRealtime()
    override suspend fun waitMs(durationMs: Long) { delay(durationMs) }
}
/** Test clock injection affects only this policy, never the app's data or network clocks. */
internal val LocalHomeHistoryReadClock = staticCompositionLocalOf<HomeHistoryReadClock> { AndroidHomeHistoryReadClock }

@Stable
internal class HomeHistoryReadPolicy internal constructor(
    private val readyState: State<Boolean>,
    private val active: () -> Boolean,
    private val onRequested: () -> Unit
) {
    val ready: Boolean get() = readyState.value && active()
    fun requested() = onRequested()
}

private data class HomeHistoryReadInputs(val receipt: HistoryReadReceipt?, val visible: Boolean, val live: Boolean, val loading: Boolean)

/** Produces eligibility only. The caller dispatches reads through its existing serial queue. */
@Composable
internal fun rememberHomeHistoryReadPolicy(key: HistoryReadKey, receipt: HistoryReadReceipt?, visible: Boolean, live: Boolean, loading: Boolean): HomeHistoryReadPolicy {
    val schedule = remember(key) { HomeHistoryReadSchedule(key) }
    val ready = remember(schedule) { mutableStateOf(false) }
    val attemptSignal = remember(schedule) { mutableIntStateOf(0) }
    val clock = LocalHomeHistoryReadClock.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val inputs = rememberUpdatedState(HomeHistoryReadInputs(receipt, visible, live, loading))
    fun activeNow(): Boolean = inputs.value.visible && inputs.value.live && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    DisposableEffect(lifecycle, schedule) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (!foreground) { schedule.pause(); ready.value = false }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); schedule.pause(); ready.value = false }
    }

    LaunchedEffect(schedule, clock, foreground, receipt, visible, live, loading, attemptSignal.intValue) {
        fun publish() {
            val current = inputs.value
            val now = clock.nowMs()
            schedule.observe(now, activeNow(), current.loading, current.receipt)
            ready.value = schedule.ready(now)
        }
        publish()
        schedule.waitMs(clock.nowMs())?.let { remaining ->
            if (remaining > 0) clock.waitMs(remaining)
            publish()
        }
    }
    return remember(schedule, clock, lifecycle) {
        HomeHistoryReadPolicy(ready, ::activeNow) {
            val current = inputs.value
            val now = clock.nowMs()
            schedule.observe(now, activeNow(), current.loading, current.receipt)
            if (schedule.requested(now)) { ready.value = false; attemptSignal.intValue++ }
        }
    }
}

/** Legacy Hub fallback: only fields emitted by historyPreview, excluding freshness/unknown fields. */
internal fun homeHistoryPreviewSignature(preview: HubHistory?): String {
    if (preview?.daily.isNullOrEmpty()) return ""
    fun row(value: HistoryRow) = buildJsonArray {
        add(value.date); add(value.tokens?.let(::JsonPrimitive) ?: JsonNull)
        add(value.cost?.let(::JsonPrimitive) ?: JsonNull); add(value.activeTimeMs?.let(::JsonPrimitive) ?: JsonNull)
        add(value.unpricedTokens?.let(::JsonPrimitive) ?: JsonNull)
    }
    val source = buildJsonObject {
        put("daily", JsonArray(preview!!.daily.sortedBy { it.date }.map(::row)))
        put("monthly", JsonArray(preview.monthly.sortedBy { it.date }.map(::row)))
        put("summary", preview.summary?.let { s -> buildJsonArray {
            add(s.totalTokens?.let(::JsonPrimitive) ?: JsonNull); add(s.totalCost?.let(::JsonPrimitive) ?: JsonNull)
            add(s.activeDays?.let(::JsonPrimitive) ?: JsonNull); add(s.currentStreak?.let(::JsonPrimitive) ?: JsonNull)
            add(s.longestStreak?.let(::JsonPrimitive) ?: JsonNull); add(s.activeTimeMs?.let(::JsonPrimitive) ?: JsonNull)
            add(s.peakDayTokens?.let(::JsonPrimitive) ?: JsonNull); add(s.favoriteModel?.let(::JsonPrimitive) ?: JsonNull)
            add(s.messages?.let(::JsonPrimitive) ?: JsonNull); add(s.unpricedTokens?.let(::JsonPrimitive) ?: JsonNull)
        } } ?: JsonNull)
    }
    return MessageDigest.getInstance("SHA-256").digest(source.toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

internal fun homeHistorySignature(snapshot: Snapshot?): String = snapshot?.historyRevision?.trim()?.takeIf { it.isNotEmpty() }
    ?: homeHistoryPreviewSignature(snapshot?.historyPreview)
