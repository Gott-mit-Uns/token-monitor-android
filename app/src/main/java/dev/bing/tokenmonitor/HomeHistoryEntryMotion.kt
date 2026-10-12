package dev.bing.tokenmonitor

import androidx.compose.animation.core.CubicBezierEasing
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

internal data class HomeHeatColumns(val first: Int, val last: Int) {
    init { require(first >= 0 && last >= first) }
}

/** No history rows, paths or business values travel through the display frame. */
internal data class HomeHistoryEntryFrame(
    val columns: HomeHeatColumns? = null,
    val heatElapsedMs: Float = 0f,
    val trendReveal: Float = 1f,
    val preparing: Boolean = false,
    val running: Boolean = false
) {
    fun heatOpacity(column: Int): Float {
        if (preparing) return 0f
        val visible = columns ?: return 1f
        if (column < visible.first || column > visible.last) return 1f
        val delay = (column - visible.first) * 400f / (visible.last - visible.first).coerceAtLeast(1)
        val progress = ((heatElapsedMs - delay) / 240f).coerceIn(0f, 1f)
        return if (progress >= 1f) 1f else homeHistoryEase.transform(progress)
    }
}

private val homeHistoryEase = CubicBezierEasing(.22f, 1f, .36f, 1f)

/**
 * app.js animateHomeHistoryVisuals: one pending entry, consumed after real layout.
 * Ordinary data replacement destroys the old DOM animations and paints the new chart
 * completely. It does not manufacture a second entrance for the same main view.
 */
internal class HomeHistoryEntryMotion {
    private var mainViewKey: String? = null
    private var contentIdentity: Any? = null
    private var hasContent = false
    private var eventKey: Any? = null
    private var available = false
    private var visible = false
    private var homeEntryConsumed = false
    private var hasTrendStroke = false
    private var startedAt = 0L
    private var durationMs = 0f
    private var durationScale = 1f
    var entryPending = true
        private set
    var frame = HomeHistoryEntryFrame()
        private set
    val running get() = frame.running

    /** Settings and its subpages must pass the preserved main-view key. */
    fun mainView(key: String): Boolean {
        val next = key.lowercase(Locale.ROOT)
        if (mainViewKey == next) return false
        mainViewKey = next
        entryPending = next == "home"
        homeEntryConsumed = false
        settle()
        return true
    }

    /** Called on real content/layout inputs, never on each draw frame. */
    fun render(content: Any?, available: Boolean, visible: Boolean, hasTrendStroke: Boolean, scale: Float, renderEventKey: Any? = content): Boolean {
        val replaced = !hasContent || contentIdentity !== content || this.available != available || eventKey != renderEventKey
        val hiddenAfterEntry = !visible && homeEntryConsumed
        if (replaced || hiddenAfterEntry) settle()
        contentIdentity = content
        eventKey = renderEventKey
        hasContent = true
        this.available = available
        this.visible = visible && mainViewKey == "home"
        this.hasTrendStroke = hasTrendStroke
        if (hiddenAfterEntry) entryPending = false
        if (!this.available || !this.visible || scale <= 0f || !scale.isFinite()) settle()
        else if (entryPending && !running) frame = HomeHistoryEntryFrame(trendReveal = 0f, preparing = true)
        return replaced || hiddenAfterEntry
    }

    /** Initial Settings without data does not consume Home's first real layout. */
    fun heatViewport(content: Any, columns: HomeHeatColumns, nowNanos: Long, scale: Float): Boolean {
        if (!entryPending || contentIdentity !== content || !available || !visible || mainViewKey != "home") return false
        entryPending = false
        // Showing Home's empty/preparing state is not entry consumption. Settings
        // may hide it before data/layout; only the real ready callback consumes it.
        homeEntryConsumed = true
        if (scale <= 0f || !scale.isFinite()) {
            settle()
            return true
        }
        startedAt = nowNanos
        durationScale = scale
        val heatDuration = if (columns.first == columns.last) 240f else 640f
        durationMs = if (hasTrendStroke) 920f else heatDuration
        frame = HomeHistoryEntryFrame(columns = columns, trendReveal = if (hasTrendStroke) 0f else 1f, running = true)
        return true
    }

    fun advance(nowNanos: Long, scale: Float) {
        if (!running) return
        if (scale <= 0f || !scale.isFinite()) {
            settle()
            return
        }
        val elapsedMs = (nowNanos - startedAt).coerceAtLeast(0L).toDouble() / 1_000_000.0 / durationScale
        if (scale != durationScale) {
            // A live system scale change changes future speed without rewinding the visible pose.
            durationScale = scale
            startedAt = nowNanos - (elapsedMs * 1_000_000.0 * scale).roundToLong()
        }
        if (elapsedMs >= durationMs) {
            settle()
            return
        }
        val reveal = if (!hasTrendStroke) 1f else homeHistoryEase.transform((elapsedMs / 920.0).toFloat().coerceIn(0f, 1f))
        frame = frame.copy(heatElapsedMs = elapsedMs.toFloat(), trendReveal = reveal)
    }

    fun settle() { frame = HomeHistoryEntryFrame() }

    /** STOP is cancellation, including a not-yet-laid-out entry; resume cannot replay it. */
    fun stop() {
        entryPending = false
        settle()
    }

    companion object {
        /** The source uses strict horizontal rectangle intersection, including partial cells. */
        fun visibleColumns(scrollPx: Int, viewportWidthPx: Int, pitchPx: Float, cellPx: Float, columns: Int): HomeHeatColumns? {
            if (viewportWidthPx <= 0 || columns <= 0 || !pitchPx.isFinite() || !cellPx.isFinite() || pitchPx <= 0f || cellPx <= 0f) return null
            val left = scrollPx.coerceAtLeast(0).toDouble()
            val right = left + viewportWidthPx
            val first = (floor((left - cellPx) / pitchPx).toInt() + 1).coerceAtLeast(0)
            val last = (ceil(right / pitchPx).toInt() - 1).coerceAtMost(columns - 1)
            return if (first <= last) HomeHeatColumns(first, last) else null
        }
    }
}
