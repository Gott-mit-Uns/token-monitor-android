package dev.bing.tokenmonitor

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A small projection only. Private names, connection addresses and credentials never enter RemoteViews. */
data class WidgetUsage(val tokens: Long?, val cost: String?, val unpriced: Boolean, val updated: String?, val savedAt: Long)

fun widgetUsage(snapshot: Snapshot, savedAt: Long): WidgetUsage {
    val today = snapshot.periods["today"]
    val cost = today?.cost?.takeIf { it.length <= 128 }?.let { text -> runCatching {
        BigDecimal(text).takeIf { it.signum() >= 0 && it.precision() <= 100 && it.scale() in -100..100 }?.toPlainString()
    }.getOrNull() }
    return WidgetUsage(today?.tokens, cost, (today?.unpriced ?: 0) > 0, snapshot.updated, savedAt)
}

sealed interface WidgetCache {
    data object Empty : WidgetCache
    data object Unreadable : WidgetCache
    data class Ready(val usage: WidgetUsage) : WidgetCache
}

/** Reads local encrypted data only. No client, service, alarm, worker or connection is created. */
object HubWidget {
    const val OPEN_AND_UPDATE = "dev.bing.tokenmonitor.widget.OPEN_AND_UPDATE"
    private val updateLock = Any()
    fun load(store: HubStore): WidgetCache = runCatching {
        store.load()?.let { WidgetCache.Ready(widgetUsage(it.parsedSnapshot ?: HubParser.parse(it.snapshot, it.observedModelIds), it.savedAt)) } ?: WidgetCache.Empty
    }.getOrDefault(WidgetCache.Unreadable)

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        update(context, manager, manager.getAppWidgetIds(ComponentName(context, HubWidgetProvider::class.java)))
    }

    fun update(context: Context, manager: AppWidgetManager, ids: IntArray) = synchronized(updateLock) {
        if (ids.isEmpty()) return@synchronized
        val cache = load(HubStore(context))
        val preferences = runCatching { DisplayPreferenceStore(context).load() }.getOrDefault(DisplayPreferences())
        val localized = localizedContext(context, preferences.language)
        ids.forEach { id -> manager.updateAppWidget(id, render(localized, preferences, cache, id)) }
    }

    fun localizedContext(context: Context, language: LanguageChoice): Context {
        val tag = when (language) {
            LanguageChoice.CHINESE -> "zh"
            LanguageChoice.ENGLISH -> "en"
            LanguageChoice.SYSTEM -> if (context.resources.configuration.locales[0].language == "zh") "zh" else "en"
        }
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) })
    }

    fun render(context: Context, preferences: DisplayPreferences, cache: WidgetCache, id: Int): RemoteViews {
        val dark = when (preferences.theme) {
            ThemeChoice.DARK -> true
            ThemeChoice.LIGHT -> false
            ThemeChoice.SYSTEM -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        val views = RemoteViews(context.packageName, R.layout.hub_widget)
        views.setInt(R.id.widget_root, "setBackgroundResource", if (dark) R.drawable.widget_dark_background else R.drawable.widget_light_background)
        val text = context.getColor(if (dark) R.color.widget_dark_text else R.color.widget_light_text)
        val secondary = context.getColor(if (dark) R.color.widget_dark_secondary else R.color.widget_light_secondary)
        views.setTextColor(R.id.widget_title, text); views.setTextColor(R.id.widget_tokens, text)
        listOf(R.id.widget_period, R.id.widget_cost, R.id.widget_time).forEach { views.setTextColor(it, secondary) }
        views.setTextColor(R.id.widget_open, context.getColor(if (dark) R.color.widget_dark_accent else R.color.widget_light_accent))
        views.setTextViewText(R.id.widget_period, context.getString(R.string.widget_period))
        views.setTextViewText(R.id.widget_open, context.getString(R.string.widget_open))
        val empty = context.getString(if (cache == WidgetCache.Unreadable) R.string.widget_unreadable else R.string.widget_empty)
        views.setTextViewText(R.id.widget_tokens, "—")
        views.setTextViewText(R.id.widget_cost, empty)
        views.setTextViewText(R.id.widget_time, "")
        if (cache is WidgetCache.Ready) {
            val usage = cache.usage
            val numbers = NumberFormat.getIntegerInstance(context.resources.configuration.locales[0])
            val tokenText = usage.tokens?.let { numbers.format(it) } ?: context.getString(R.string.unknown)
            // Let narrow hosts wrap at thousands separators rather than inside a three-digit group.
            views.setTextViewText(R.id.widget_tokens, tokenText.replace(",", ",\u200B"))
            views.setContentDescription(R.id.widget_tokens, context.getString(R.string.widget_tokens_accessible, tokenText))
            views.setTextViewText(R.id.widget_cost, usage.cost?.let {
                context.getString(if (usage.unpriced) R.string.widget_cost_partial_formatted else R.string.widget_cost_formatted,
                    UsageCurrency(preferences.currency, preferences.currencyRates).headline(it, if (usage.unpriced) 1 else null))
            } ?: context.getString(R.string.widget_cost_unknown))
            fun time(instant: Instant) = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'", context.resources.configuration.locales[0]).withZone(ZoneOffset.UTC).format(instant)
            val updated = usage.updated?.takeIf { it.length <= 80 }?.let { runCatching { time(Instant.parse(it)) }.getOrNull() } ?: context.getString(R.string.unknown)
            val saved = runCatching { time(Instant.ofEpochMilli(usage.savedAt)) }.getOrDefault(context.getString(R.string.unknown))
            views.setTextViewText(R.id.widget_time, context.getString(R.string.widget_times, updated, saved))
        }
        val intent = Intent(context, MainActivity::class.java).putExtra(OPEN_AND_UPDATE, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_root, open)
        views.setOnClickPendingIntent(R.id.widget_open, open)
        return views
    }
}

class HubWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = updateAsync(context, manager, ids)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = updateAsync(context, manager, intArrayOf(id))
    private fun updateAsync(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { runCatching { HubWidget.update(context.applicationContext, manager, ids) } }
            finally { pending.finish() }
        }
    }
}
