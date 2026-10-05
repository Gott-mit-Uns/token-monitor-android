package io.github.theminionooo.tokenmonitor.widget

/** The elapsed deadline includes device sleep; the wall time is only for display. */
internal data class WidgetSessionDeadline(
    val elapsedRealtime: Long,
    val displayExpiresAt: Long,
) {
    fun remainingMillis(nowElapsedRealtime: Long): Long = (elapsedRealtime - nowElapsedRealtime).coerceAtLeast(0)

    fun hasExpired(nowElapsedRealtime: Long): Boolean = nowElapsedRealtime >= elapsedRealtime
}

internal fun widgetSessionDeadline(
    previous: WidgetSessionDeadline?,
    alreadyLive: Boolean,
    live: Boolean,
    nowElapsedRealtime: Long,
    nowWallTime: Long,
): WidgetSessionDeadline {
    // Refresh during Live must not buy another hour, including after device sleep.
    if (alreadyLive && previous != null) return previous
    val duration = if (live) WidgetLiveService.SESSION_MS else WidgetLiveService.REFRESH_MS
    return WidgetSessionDeadline(nowElapsedRealtime + duration, if (live) nowWallTime + duration else 0)
}
