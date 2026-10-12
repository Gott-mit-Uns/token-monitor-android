package dev.bing.tokenmonitor

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal data class HeadlineFrame(val value: Long?, val widest: Long?, val running: Boolean)
// Tests can exercise the same renderer with a deterministic Android motion preference.
internal val LocalHeadlineMotionScale = staticCompositionLocalOf<Float?> { null }
// Opt-in public workload diagnostics. Null in the application; never records usage data.
internal val LocalHeadlineRenderObserver = staticCompositionLocalOf<((String) -> Unit)?> { null }
internal val LocalDesktopSystemMotionScale = staticCompositionLocalOf { 1f }
internal fun interface DesktopAnimationClock { fun nowNanos(): Long }
// Production frame timestamps and System.nanoTime share a monotonic domain.
// Virtual Compose tests must provide their own mainClock-based timestamp here.
internal val LocalDesktopAnimationClock = staticCompositionLocalOf<DesktopAnimationClock> {
    DesktopAnimationClock { System.nanoTime() }
}

@Composable internal fun rememberAndroidMotionScale(): Float {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f).coerceAtLeast(0f)
    var scale by remember(resolver) { mutableFloatStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object: ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange:Boolean) { scale = read() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        scale = read()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return scale
}

/** Only display frames while this screen is foreground. No timers, reads or background work. */
@Composable internal fun rememberHeadlineNumber(target: Long?, period: String, visible: Boolean,preference:String="system",
    suppressInitial:Boolean=false,detailUnavailable:Boolean=false): State<HeadlineFrame> {
    val motion = remember { HeadlineNumberMotion(null) }
    // The owner keeps the timeline across pages, but must not read the per-frame value.
    // Reading the State only in DesktopTotal confines frame invalidation to that region.
    val frame = remember { mutableStateOf(HeadlineFrame(target, target, false)) }
    var suppressionPending by remember { mutableStateOf(suppressInitial) }
    var previousPeriod by remember { mutableStateOf(period) }
    val timeline = remember { HeadlineNumberTimeline() }
    val clock = LocalDesktopAnimationClock.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val scaleOverride = LocalHeadlineMotionScale.current
    val androidScale = LocalDesktopSystemMotionScale.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (!foreground) {
                motion.settle()
                frame.value = HeadlineFrame(motion.displayed,motion.widest,false)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); motion.settle() }
    }
    LaunchedEffect(target, period, visible, foreground, scaleOverride, androidScale,preference,detailUnavailable,clock) {
        val duration = if (period != previousPeriod) 800 else 1000
        previousPeriod = period
        fun scale() = scaleOverride ?: DesktopMotionPreference.scale(preference,androidScale)
        fun publish(prefit:Boolean=false) { frame.value = HeadlineFrame(if(prefit) motion.widest else motion.displayed, motion.widest, motion.running) }
        // Both source early-return branches cancel without consuming suppression.
        if (target == null || detailUnavailable) {
            motion.unavailable(if(detailUnavailable) target else null)
            publish()
            return@LaunchedEffect
        }
        val started = motion.retarget(target, !suppressionPending && visible && foreground && scale() > 0f)
        suppressionPending = false
        if (started) timeline.start(clock.nowNanos(),duration)
        if (!visible || !foreground || scale() <= 0f) motion.settle()
        // The source writes the wider endpoint for font fitting before its first RAF.
        publish(prefit = started && motion.running)
        if (motion.running) {
            while (motion.running) {
                withFrameNanos { now ->
                    motion.frame(timeline.progress(now,scale()))
                    publish()
                }
            }
        }
    }
    return frame
}
