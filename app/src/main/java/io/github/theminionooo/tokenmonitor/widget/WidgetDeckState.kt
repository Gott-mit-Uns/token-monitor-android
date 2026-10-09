package io.github.theminionooo.tokenmonitor.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.util.SizeF
import android.os.Build
import android.os.Bundle
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.storage.DisplayPreferences
import io.github.theminionooo.tokenmonitor.data.storage.SnapshotCache
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.ui.InterfaceTheme
import io.github.theminionooo.tokenmonitor.ui.resolveInterfaceTheme

internal data class WidgetDeckRenderState(
    val data: WidgetDeckData,
    val theme: InterfaceTheme,
    val size: SizeF,
)

internal fun loadWidgetDeck(context: Context, widgetId: Int): WidgetDeckRenderState {
    val session = WidgetRuntime.session
    val cached = SnapshotCache(context).read()?.let { wire ->
        runCatching {
            HubProtocolParser.decodeSnapshot(
                wire.health,
                wire.stats,
                wire.devices,
                wire.history,
                wire.subscriptions,
                wire.capturedAt,
                fromCache = true,
            )
        }.getOrNull()
    }
    val snapshot: HubSnapshot? = listOfNotNull(session.snapshot, cached).maxByOrNull { it.capturedAt }?.let { io.github.theminionooo.tokenmonitor.fork.hubNamedSnapshot(it) }
    val display = DisplayPreferences(context).options.value
    val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val theme = resolveInterfaceTheme(display.themeCode, display.followSystemTheme, systemDark)
    return WidgetDeckRenderState(
        data = prepareWidgetDeck(snapshot, session),
        theme = theme,
        size = widgetDeckSize(context, widgetId),
    )
}

internal fun widgetDeckSize(context: Context, widgetId: Int): SizeF {
    if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return SizeF(320f, 180f)
    val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
    val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
    val width = options.getInt(
        if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
        320,
    ).coerceAtLeast(1)
    val height = options.getInt(
        if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
        180,
    ).coerceAtLeast(1)
    return SizeF(width.toFloat(), height.toFloat())
}

internal object WidgetDeckPageState {
    private const val STORE = "widget_deck_pages"

    fun read(context: Context, widgetId: Int): WidgetDeckPage {
        val index = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getInt(widgetId.toString(), 0)
        return WidgetDeckPage.entries.getOrElse(index) { WidgetDeckPage.Overview }
    }

    fun move(context: Context, widgetId: Int, delta: Int): WidgetDeckPage {
        val current = read(context, widgetId).ordinal
        val next = Math.floorMod(current + delta, WidgetDeckPage.entries.size)
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putInt(widgetId.toString(), next).apply()
        return WidgetDeckPage.entries[next]
    }

    fun remove(context: Context, widgetIds: IntArray) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().apply {
            widgetIds.forEach { remove(it.toString()) }
        }.apply()
    }
}

/** Use launcher allocations, including orientation variants; never enlarge an undersized host. */
internal fun widgetHostSizes(options: Bundle): List<SizeF> {
    val advertised = if (Build.VERSION.SDK_INT >= 33) options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
        else if (Build.VERSION.SDK_INT >= 31) { @Suppress("DEPRECATION") options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES) } else null
    fun valid(size: SizeF) = size.width.isFinite() && size.height.isFinite() && size.width > 0 && size.height > 0
    val sizes = advertised.orEmpty().filter(::valid).distinct().take(16)
    if (sizes.isNotEmpty()) return sizes
    return listOf(
        SizeF(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat(), options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat()),
        SizeF(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).toFloat(), options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).toFloat()),
    ).filter(::valid).distinct()
}
