package io.github.theminionooo.tokenmonitor.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.util.SizeF
import android.widget.RemoteViews
import io.github.theminionooo.tokenmonitor.R

class SwipeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { configure(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        configure(context, manager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action !in PAGE_ACTIONS) return
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        WidgetDeckPageState.move(context, widgetId, if (intent.action == ACTION_PREVIOUS) -1 else 1)
        configure(context, AppWidgetManager.getInstance(context), widgetId)
    }

    override fun onDeleted(context: Context, ids: IntArray) = WidgetDeckPageState.remove(context, ids)

    override fun onDisabled(context: Context) = WidgetUpdateCoordinator.stopLiveIfUnused(context)

    private fun configure(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val state = loadWidgetDeck(context, widgetId)
        val page = WidgetDeckPageState.read(context, widgetId)
        fun render(size: SizeF) = WidgetDeckRenderer.render(context, page, state.data, state.theme, size).apply {
            setOnClickPendingIntent(R.id.swipe_page_open, activityIntent(context, widgetId, 0, WidgetControlActivity.ACTION_OPEN))
            setOnClickPendingIntent(R.id.swipe_page_previous, pageIntent(context, widgetId, 1, ACTION_PREVIOUS))
            setOnClickPendingIntent(R.id.swipe_page_next, pageIntent(context, widgetId, 2, ACTION_NEXT))
            setOnClickPendingIntent(R.id.widget_refresh, activityIntent(context, widgetId, 3, WidgetLiveService.ACTION_REFRESH))
            setOnClickPendingIntent(R.id.widget_live, activityIntent(context, widgetId, 4, WidgetLiveService.ACTION_LIVE))
        }
        val sizes = widgetHostSizes(manager.getAppWidgetOptions(widgetId))
        val views = if (Build.VERSION.SDK_INT >= 31 && sizes.isNotEmpty()) RemoteViews(sizes.associateWith(::render)) else render(state.size)
        manager.updateAppWidget(widgetId, views)
    }

    companion object {
        internal const val ACTION_PREVIOUS = "widget.DECK_PREVIOUS"
        internal const val ACTION_NEXT = "widget.DECK_NEXT"
        private val PAGE_ACTIONS = setOf(ACTION_PREVIOUS, ACTION_NEXT)

        internal fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SwipeWidgetProvider::class.java))
            val provider = SwipeWidgetProvider()
            ids.forEach { provider.configure(context, manager, it) }
        }

        private fun activityIntent(context: Context, widgetId: Int, offset: Int, action: String): PendingIntent {
            val intent = Intent(context, WidgetControlActivity::class.java).apply {
                this.action = action
                data = Uri.parse("token-monitor://widget/deck/$widgetId/action/$offset")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            return PendingIntent.getActivity(
                context,
                widgetId * 10 + offset,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun pageIntent(context: Context, widgetId: Int, offset: Int, action: String): PendingIntent {
            val intent = Intent(context, SwipeWidgetProvider::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse("token-monitor://widget/deck/$widgetId/page/$offset")
            }
            return PendingIntent.getBroadcast(
                context,
                widgetId * 10 + offset,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
