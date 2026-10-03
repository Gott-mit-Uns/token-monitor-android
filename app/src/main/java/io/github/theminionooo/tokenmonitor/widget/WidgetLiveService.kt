package io.github.theminionooo.tokenmonitor.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.data.HubRepository
import io.github.theminionooo.tokenmonitor.data.HubRepositoryPool
import io.github.theminionooo.tokenmonitor.MainActivity
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.ui.formatCompactTokens
import io.github.theminionooo.tokenmonitor.ui.formatMoney
import io.github.theminionooo.tokenmonitor.ui.formatBoundary
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
    val clock = DateTimeFormatter.ofPattern("HH:mm", locale).withZone(zoneId)
    val headline = snapshot?.let { "${widgetTokens(it.today.totalTokens)} tokens · ${formatMoney(it.today.costUsd)} today" }
        ?: if (live) "Waiting for the Hub" else "Fetching a fresh Hub snapshot"
    val windows = snapshot?.let(::quotaRows).orEmpty().joinToString("\n") { row ->
        val reset = formatBoundary(row.window.resetsAt, row.window.boundaryKind, now)
        "${row.provider.providerLabel()} ${windowTitle(row.window, row.siblings)} ${row.remainingPercent.toInt()}% left" +
            if (reset.isNotBlank()) " · $reset" else ""
    }
    val until = when {
        live && session.expiresAt > 0 -> "Live until ${clock.format(Instant.ofEpochMilli(session.expiresAt))}"
        live -> "Widget updates for one hour"
        else -> null
    }
    return WidgetNotificationContent(
        title = if (live) "Token Monitor · Live" else "Refreshing Token Monitor",
        headline = headline,
        detail = listOfNotNull(headline, windows.ifBlank { null }, until).joinToString("\n"),
        subtext = until,
    )
}

/** Started only by a visible, explicit widget action. No restart, boot receiver, or wake lock. */
class WidgetLiveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var repository: HubRepository? = null
    private var collection: Job? = null
    private var expiry: Job? = null
    private var live = false
    private var finishing = false
    private var shownNotification: WidgetNotificationContent? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action !in listOf(ACTION_LIVE, ACTION_REFRESH)) {
            stopSelf()
            return START_NOT_STICKY
        }
        current = this
        val alreadyLive = live
        live = live || intent?.action == ACTION_LIVE
        val until = if (alreadyLive) WidgetRuntime.session.expiresAt else if (live) System.currentTimeMillis() + SESSION_MS else 0L
        WidgetRuntime.session = WidgetRuntime.session.copy(enabled = live, refreshing = true, expiresAt = until, note = null)
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
        hub.setWidgetActive(true)
        if (intent?.action == ACTION_REFRESH) hub.refreshNow()
        collection = scope.launch {
            hub.state.collectLatest { state ->
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
        if (!alreadyLive || expiry?.isActive != true) {
            expiry?.cancel()
            expiry = scope.launch {
                // Preserve this monotonic deadline when Refresh is tapped during Live.
                delay(if (live) SESSION_MS else REFRESH_MS)
                finishSession(if (live) "Live session ended" else "Refresh timed out")
            }
        }
        return START_NOT_STICKY
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
        val session = WidgetRuntime.session
        val snapshot = session.snapshot
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
        if (Build.VERSION.SDK_INT >= 36) snapshot?.let { builder.setShortCriticalText(formatCompactTokens(it.today.totalTokens)) }
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
