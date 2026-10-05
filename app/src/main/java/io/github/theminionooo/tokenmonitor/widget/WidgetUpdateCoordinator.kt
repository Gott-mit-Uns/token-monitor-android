package io.github.theminionooo.tokenmonitor.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

internal object WidgetUpdateCoordinator {
    fun refresh(context: Context) {
        UsageWidgetProvider.refresh(context)
        SwipeWidgetProvider.refresh(context)
    }

    fun stopLiveIfUnused(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val hasResponsive = usageWidgetProviders.any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
        val hasDeck = manager.getAppWidgetIds(ComponentName(context, SwipeWidgetProvider::class.java)).isNotEmpty()
        if (!hasResponsive && !hasDeck) WidgetLiveService.stop(context)
    }
}
