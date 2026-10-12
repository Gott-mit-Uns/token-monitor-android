package dev.bing.tokenmonitor

import android.os.Build
import android.os.Looper
import android.os.Trace
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Fixed names only: callers must never pass payloads, connection details, or model IDs.
 * Units depend on the stage: decoded UTF-8 bytes, input UTF-16 characters, or row counts.
 */
internal enum class HubWorkStage {
    HTTP_READ, JSON_PROJECT, JSON_TREE, JSON_MERGE, JSON_STRINGIFY, SNAPSHOT_PARSE,
    MODEL_IDS, SSE_READ_FRAME, SSE_FRAME_WAIT,
    MAIN_ACCEPT, CACHE_QUEUE_WAIT, CACHE_SAVE, CACHE_LOAD, HOME_SOURCE, HOME_RECENT, SESSION_RAW,
    SESSION_GROUP, SESSION_FILTER, SESSION_PAGE, MODEL_ALIAS_PLAN, PROJECT_ROWS, DEVICE_ROWS,
    SNAPSHOT_FRESHNESS
}

/** A monotonic completed span containing numeric measurements and fixed enum names only. */
internal data class HubWorkSpan(
    val id: Long,
    val stage: HubWorkStage,
    val units: Long,
    val startNanos: Long,
    val endNanos: Long,
    val success: Boolean,
    val onMain: Boolean,
    val endedOnMain: Boolean
) {
    val durationNanos: Long get() = (endNanos - startNanos).coerceAtLeast(0L)
}

/**
 * Opt-in coarse diagnostics. With no observer installed, trace() runs its block directly
 * and begin() returns a shared inert span. Neither path reads a clock or Android API.
 * Observers must retain only bounded records; exceptions in observers never affect Hub work.
 */
internal object HubWorkTrace {
    fun interface Observer { fun completed(span: HubWorkSpan) }

    internal class Subscription internal constructor(val observer: Observer)
    private val current = AtomicReference<Subscription?>(null)
    private val nextId = AtomicLong()
    val isActive: Boolean get() = current.get() != null

    fun observe(observer: Observer): AutoCloseable {
        val subscription = Subscription(observer)
        check(current.compareAndSet(null, subscription)) { "A Hub work observer is already active" }
        return AutoCloseable { current.compareAndSet(subscription, null) }
    }

    inline fun <T> trace(stage: HubWorkStage, units: Long = 0L, block: () -> T): T {
        if (!isActive) return block()
        val span = begin(stage, units)
        var success = true
        try {
            return block()
        } catch (failure: Throwable) {
            success = false
            throw failure
        } finally {
            span.end(success)
        }
    }

    fun begin(stage: HubWorkStage, units: Long = 0L): Span {
        val subscription = current.get() ?: return INERT
        val id = nextId.incrementAndGet()
        val start = System.nanoTime()
        val onMain = mainThread()
        // Async sections also represent spans whose completion occurs on another thread.
        // Tracing support is optional and failures must not alter the business operation.
        val cookie = (id and Int.MAX_VALUE.toLong()).toInt()
        val androidTrace = runCatching {
            if (Build.VERSION.SDK_INT >= 29 && current.get() === subscription) {
                Trace.beginAsyncSection("Hub/${stage.name}", cookie)
                true
            } else false
        }.getOrDefault(false)
        return Span(stage, units.coerceAtLeast(0L), id, start, onMain, subscription, cookie, androidTrace)
    }

    class Span internal constructor(
        private val stage: HubWorkStage?,
        private val initialUnits: Long,
        private val id: Long,
        private val start: Long,
        private val onMain: Boolean,
        private val subscription: Subscription?,
        private val cookie: Int,
        private val androidTrace: Boolean
    ) {
        private val ended = AtomicBoolean()

        fun end(success: Boolean = true, units: Long? = null) {
            val observer = subscription ?: return
            if (!ended.compareAndSet(false, true)) return
            val end = System.nanoTime()
            if (androidTrace) runCatching {
                if (Build.VERSION.SDK_INT >= 29) Trace.endAsyncSection("Hub/${stage!!.name}", cookie)
            }
            // A span that outlives its diagnostic lease must not enter a later run.
            if (current.get() !== observer) return
            val value = HubWorkSpan(id, stage!!, (units ?: initialUnits).coerceAtLeast(0L),
                start, end, success, onMain, mainThread())
            runCatching { observer.observer.completed(value) }
        }

        companion object {
            internal fun inert() = Span(null, 0L, 0L, 0L, false, null, 0, false)
        }
    }

    private fun mainThread(): Boolean = runCatching {
        val main = Looper.getMainLooper()
        main != null && Looper.myLooper() === main
    }.getOrDefault(false)
    private val INERT = Span.inert()
}
