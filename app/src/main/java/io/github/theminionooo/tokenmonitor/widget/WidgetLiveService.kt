package io.github.theminionooo.tokenmonitor.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.data.HubRepository
import io.github.theminionooo.tokenmonitor.data.HubRepositoryPool
import io.github.theminionooo.tokenmonitor.MainActivity
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.domain.snapshotDate
import io.github.theminionooo.tokenmonitor.ui.formatCompactTokens
import io.github.theminionooo.tokenmonitor.ui.formatMoney
import io.github.theminionooo.tokenmonitor.ui.formatBoundary
import io.github.theminionooo.tokenmonitor.ui.formatDuration
import io.github.theminionooo.tokenmonitor.ui.providerLabel
import io.github.theminionooo.tokenmonitor.ui.windowTitle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

internal data class WidgetSession(
    val enabled: Boolean = false,
    val refreshing: Boolean = false,
    val connected: Boolean = false,
    val expiresAt: Long = 0,
    val snapshot: HubSnapshot? = null,
    val note: String? = null,
)

/** Deliberately process-local: a killed process never advertises a surviving Live session. */
internal object WidgetRuntime {
    @Volatile var session = WidgetSession()
}

internal data class WidgetNotificationContent(
    val title: String,
    val headline: String,
    val detail: String,
    val subtext: String?,
    val shortText: String,
)

/** Notification identity is based on visible content, never a fetch timestamp. */
internal fun widgetNotificationContent(
    session: WidgetSession,
    live: Boolean,
    now: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): WidgetNotificationContent {
    val snapshot = session.snapshot
    val connected = session.connected && snapshot?.fromCache != true && session.note == null
    val clock = DateTimeFormatter.ofPattern("HH:mm", locale).withZone(zoneId)
    val headline = snapshot?.let {
        val totals = "${widgetTokens(it.today.totalTokens)} tokens · ${formatMoney(it.today.costUsd)}"
        val day = snapshotDate(it, zoneId)
        when {
            !connected -> "Saved · $totals"
            day == Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate() -> "$totals today"
            day != null -> "$totals for $day"
            else -> totals
        }
    }
        ?: if (live) "Waiting for the Hub" else "Fetching a fresh Hub snapshot"
    val lastUpdated = snapshot?.takeUnless { connected }?.let {
        if (it.capturedAt <= 0) "Last update time unavailable" else {
            val age = (now - it.capturedAt).coerceAtLeast(0L)
            "Last updated " + if (age < 60_000) "less than a minute ago" else "${formatDuration(age / 60_000 * 60_000)} ago"
        }
    }
    val windows = snapshot?.let(::quotaRows).orEmpty().joinToString("\n") { row ->
        val reset = formatBoundary(row.window.resetsAt, row.window.boundaryKind, now)
        "${row.provider.providerLabel()} ${windowTitle(row.window, row.siblings)} ${row.remainingPercent.toInt()}% left" +
            if (reset.isNotBlank()) " · $reset" else ""
    }
    val until = when {
        live && session.expiresAt > 0 -> "${if (connected) "Live until" else "Session ends"} ${clock.format(Instant.ofEpochMilli(session.expiresAt))}"
        live -> "Widget updates for one hour"
        else -> null
    }
    return WidgetNotificationContent(
        title = when {
            !live -> "Refreshing Token Monitor"
            connected -> "Token Monitor · Live"
            snapshot != null -> "Token Monitor · Reconnecting"
            else -> "Token Monitor · Connecting"
        },
        headline = headline,
        detail = listOfNotNull(headline, lastUpdated, windows.ifBlank { null }, until).joinToString("\n"),
        subtext = until,
        shortText = when {
            connected && snapshot != null -> formatCompactTokens(snapshot.today.totalTokens)
            snapshot != null -> "Offline"
            else -> "Waiting"
        },
    )
}

/** Started only by a visible, explicit widget action. No restart, boot receiver, or wake lock. */
class WidgetLiveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var repository: HubRepository? = null
    private var collection: Job? = null
    private var expiry: Job? = null
    private var deadline: WidgetSessionDeadline? = null
    private var observingWake = false
    private var live = false
    private var finishing = false
    private var shownNotification: WidgetNotificationContent? = null
    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON && !expireIfNeeded()) showNotification()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action !in listOf(ACTION_LIVE, ACTION_REFRESH)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (finishing || expireIfNeeded()) return START_NOT_STICKY
        current = this
        val alreadyLive = live
        live = live || intent?.action == ACTION_LIVE
        val activeDeadline = widgetSessionDeadline(deadline, alreadyLive, live, SystemClock.elapsedRealtime(), System.currentTimeMillis())
        deadline = activeDeadline
        WidgetRuntime.session = WidgetRuntime.session.copy(enabled = live, refreshing = true, expiresAt = activeDeadline.displayExpiresAt, note = null)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, widgetText(this, "Widget live updates"), NotificationManager.IMPORTANCE_LOW))
        try {
            val content = widgetNotificationContent(WidgetRuntime.session, live)
            shownNotification = content
            val notification = notification(content)
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(NOTIFICATION_ID, notification)
        } catch (_: RuntimeException) {
            finishSession("Live unavailable. Tap to retry.")
            return START_NOT_STICKY
        }
        val hub = repository ?: HubRepositoryPool.acquire(this).also { repository = it }
        if (!hub.state.value.hasConnection) {
            finishSession("Open the app to connect your Hub")
            return START_NOT_STICKY
        }
        collection?.cancel()
        val before = hub.state.value.snapshot
        hub.setWidgetActive(true, activeDeadline.elapsedRealtime)
        if (intent?.action == ACTION_REFRESH) hub.refreshNow()
        collection = scope.launch {
            hub.state.collectLatest { state ->
                if (expireIfNeeded()) return@collectLatest
                if (!state.hasConnection) {
                    finishSession("Connect your Hub in the app")
                    return@collectLatest
                }
                WidgetRuntime.session = WidgetRuntime.session.copy(
                    connected = state.streamActive || state.widgetLiveActive, refreshing = state.refreshing,
                    snapshot = state.snapshot, note = if (state.message != null) "Hub offline · retrying" else null,
                )
                withContext(Dispatchers.IO) { WidgetUpdateCoordinator.refresh(this@WidgetLiveService) }
                showNotification()
                if (!live && ((state.snapshot !== before && state.snapshot?.fromCache == false) || state.message != null)) {
                    finishSession(if (state.message != null) "Refresh failed · showing saved data" else null)
                }
            }
        }
        if (!observingWake && !finishing) {
            ContextCompat.registerReceiver(this, wakeReceiver, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
            observingWake = true
        }
        expiry?.cancel()
        expiry = scope.launch {
            while (!finishing && !expireIfNeeded()) {
                // Handler delays pause in deep sleep. Recheck elapsed time on each callback;
                // the repository independently checks this same deadline before network work.
                showNotification()
                delay(minOf(activeDeadline.remainingMillis(SystemClock.elapsedRealtime()), EXPIRY_CHECK_MS))
            }
        }
        return START_NOT_STICKY
    }

    private fun expireIfNeeded(): Boolean {
        if (deadline?.hasExpired(SystemClock.elapsedRealtime()) != true) return false
        finishSession(if (live) "Live session ended" else "Refresh timed out")
        return true
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        finishSession("Android paused Live. Tap to retry.")
    }

    /**
     * The notification that keeps the service alive doubles as the lock-screen and status-bar view of
     * the session: the figure and the tightest windows, refreshed whenever the snapshot changes. On
     * Android 16 it asks to be promoted, which is what puts it on the lock screen and in the status
     * bar chip for the hour it runs.
     */
    private fun notification(content: WidgetNotificationContent = widgetNotificationContent(WidgetRuntime.session, live)): Notification {
        val stop = PendingIntent.getBroadcast(this, 21, Intent(this, WidgetStopReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 22, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_widget_notification)
            .setContentTitle(widgetText(this, content.title))
            .setContentText(widgetText(this, content.headline))
            .setStyle(Notification.BigTextStyle().bigText(widgetText(this, content.detail)))
            .setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, widgetText(this, "Stop"), stop).build())
        content.subtext?.let { builder.setSubText(widgetText(this, it)) }
        // Promotion (lock screen, status bar chip) arrived in the Android 16 minor release, so check the full version.
        if (Build.VERSION.SDK_INT >= 36) builder.setShortCriticalText(content.shortText)
        if (Build.VERSION.SDK_INT >= 36 && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) builder.setRequestPromotedOngoing(true)
        return builder.build()
    }

    /** Re-posts the notification only when its text would change, so the shade never flickers. */
    private fun showNotification() {
        val content = widgetNotificationContent(WidgetRuntime.session, live)
        if (content == shownNotification || finishing) return
        shownNotification = content
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(content)) }
    }

    private fun finishSession(note: String? = null) {
        if (finishing) return
        finishing = true
        collection?.cancel()
        expiry?.cancel()
        if (observingWake) {
            unregisterReceiver(wakeReceiver)
            observingWake = false
        }
        repository?.setWidgetActive(false)
        WidgetRuntime.session = WidgetRuntime.session.copy(enabled = false, refreshing = false, connected = false, expiresAt = 0, note = note)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        refreshAsync(applicationContext)
    }

    override fun onDestroy() {
        finishSession()
        scope.cancel()
        repository?.let(HubRepositoryPool::release)
        repository = null
        if (current === this) current = null
        super.onDestroy()
    }

    companion object {
        internal const val ACTION_LIVE = "widget.LIVE"
        internal const val ACTION_REFRESH = "widget.REFRESH"
        internal const val SESSION_MS = 60 * 60 * 1000L
        internal const val REFRESH_MS = 45_000L
        private const val EXPIRY_CHECK_MS = 30_000L
        private const val CHANNEL = "widget_live"
        private const val NOTIFICATION_ID = 71
        private var current: WidgetLiveService? = null

        internal fun stop(context: Context) {
            current?.finishSession()
            context.stopService(Intent(context, WidgetLiveService::class.java))
            WidgetRuntime.session = WidgetSession()
            refreshAsync(context.applicationContext)
        }

        private fun refreshAsync(context: Context) {
            kotlin.concurrent.thread(name = "widget-status") { WidgetUpdateCoordinator.refresh(context) }
        }
    }
}
