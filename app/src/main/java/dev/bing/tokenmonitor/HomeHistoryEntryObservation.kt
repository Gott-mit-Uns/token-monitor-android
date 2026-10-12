package dev.bing.tokenmonitor

import androidx.compose.runtime.staticCompositionLocalOf

internal enum class HomeHistoryEntryObservationPhase { ENTRY_START, STATE, STOP, VIEWPORT, HEAT_DRAW, TREND_CACHE_BUILD, TREND_DRAW }

/** Fixed phases and numeric geometry only; never history, labels, dates, URLs or payload. */
internal data class HomeHistoryEntryObservation(
    val phase: HomeHistoryEntryObservationPhase,
    val atNanos: Long,
    val ownerIdentity: Int,
    val entrySequence: Int,
    val contentIdentity: Int = 0,
    val frame: HomeHistoryEntryFrame = HomeHistoryEntryFrame(),
    val viewportWidthPx: Int = 0,
    val scrollPx: Int = 0,
    val pitchPx: Float = 0f,
    val cellPx: Float = 0f,
    val totalColumns: Int = 0,
    val visibleFirst: Int = -1,
    val visibleLast: Int = -1,
    val firstOpacity: Float = 1f,
    val lastOpacity: Float = 1f,
    val outsideOpacity: Float = 1f,
    val cacheIdentity: Int = 0,
    val cacheGeneration: Int = 0,
    val chartWidthPx: Float = 0f,
    val chartHeightPx: Float = 0f,
    val sourceStrokeLength: Float = 0f,
    val drawnStrokeLength: Float = 0f,
    val areaClipRightPx: Float = 0f,
    val visible: Boolean = true
)

// Null in production. Test storage must be bounded; observing never drives rendering.
internal val LocalHomeHistoryEntryObserver = staticCompositionLocalOf<((HomeHistoryEntryObservation) -> Unit)?> { null }
internal inline fun ((HomeHistoryEntryObservation) -> Unit)?.homeEntryObserve(sample: () -> HomeHistoryEntryObservation) {
    this?.let { callback -> runCatching { callback(sample()) } }
}
internal class HomeChartCacheCounter {
    val identity = System.identityHashCode(this)
    var generation = 0
        private set
    fun rebuilt(): Int = ++generation
}
