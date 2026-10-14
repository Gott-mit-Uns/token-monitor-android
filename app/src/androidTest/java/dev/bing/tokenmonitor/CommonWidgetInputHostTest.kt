package dev.bing.tokenmonitor

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** Same authorized, redacted Mac snapshot input as the source-derived native reference. */
class CommonWidgetInputHostTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val fixture = instrumentation.context.assets.open("common-widget-input.json").bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
    @Test fun sameDataAllThreePeriodsSummaryAndDashboard() {
        val now = Instant.parse(fixture.getValue("now").jsonPrimitive.content)
        val snapshot = HubParser.parse(fixture.getValue("stats").toString())
        val history = HistoryParser.parse(fixture.getValue("history").toString())
        val localized = HubWidget.localizedContext(context, LanguageChoice.CHINESE).createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply { fontScale = 1f; setLocales(android.os.LocaleList.forLanguageTags("zh")) })
        val preferences = DisplayPreferences(theme = ThemeChoice.DARK, language = LanguageChoice.CHINESE, main = MainDisplayPreferences(compactUnits = "localized"), currency = "USD")
        val manager = AppWidgetManager.getInstance(context)
        lateinit var host: AppWidgetHost
        instrumentation.runOnMainSync { host = AppWidgetHost(context, 71907) }
        val id = host.allocateAppWidgetId()
        try {
            instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
            assertTrue(manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, HubWidgetProvider::class.java)))
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            val info = requireNotNull(manager.getAppWidgetInfo(id))
            val cases = listOf(Triple("day", "today", "1.58亿"), Triple("month", "month", "33.8亿"), Triple("total", "allTime", "74.3亿"))
            cases.forEach { (source, period, expectedDisplay) ->
                val expected = fixture.getValue("expected").jsonObject.getValue(source).jsonObject
                val expectedTokens = expected.getValue("overview").jsonObject.getValue("totalTokens").jsonPrimitive.long
                assertEquals(expectedTokens, widgetUsage(snapshot, now.toEpochMilli(), period).tokens)
                val expectedTrend = expected.getValue("trend").jsonObject.getValue("points").jsonArray.associate { raw -> raw.jsonObject.let { it.getValue("date").jsonPrimitive.content to it.getValue("totalTokens").jsonPrimitive.long } }
                widgetTrend(snapshot, history, period, now, ZoneId.of("Asia/Shanghai")).forEach { point ->
                    // Missing recorded days stay unknown; every known same-input observation must match.
                    point.tokens?.let { assertEquals("Same-input $period trend ${point.date}", expectedTrend[point.date], it) }
                }
                for (mode in listOf("summary", "dashboard")) instrumentation.runOnMainSync {
                    val width = if (mode == "dashboard") 360 else 180
                    val height = if (mode == "dashboard") 360 else 280
                    val options = Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width); putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height) }
                    val view = host.createView(localized, id, info)
                    view.updateAppWidget(HubWidget.render(localized, preferences, WidgetCache.Ready(widgetUsage(snapshot, now.toEpochMilli()), snapshot, history), id, options, WidgetConfiguration(mode = mode, period = period), now))
                    val density = localized.resources.displayMetrics.density
                    val w = (width * density).toInt(); val h = (height * density).toInt()
                    view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)); view.layout(0, 0, w, h)
                    assertEquals(expectedDisplay, view.findViewById<TextView>(R.id.widget_tokens).text.toString())
                    assertEquals(when (period) { "today" -> "$29.53"; "month" -> "$1077.32"; else -> "$2739.64" }, view.findViewById<TextView>(R.id.widget_cost).text.toString())
                    if (mode == "dashboard") {
                        assertTrue(view.findViewById<TextView>(R.id.widget_quota_1).text.contains("81%"))
                        assertTrue(view.findViewById<TextView>(R.id.widget_quota_2).text.contains("¥21.17"))
                        assertTrue(view.findViewById<TextView>(R.id.widget_quota_2).text.contains("¥7.93"))
                        if (period == "today") assertEquals("+36%", view.findViewById<TextView>(R.id.widget_trend_change).text.toString())
                    }
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); view.draw(Canvas(bitmap))
                    File(context.getExternalFilesDir(null), "visual-common-widget-$mode-$source.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                }
            }
        } finally { instrumentation.uiAutomation.dropShellPermissionIdentity(); instrumentation.runOnMainSync { host.deleteHost() } }
    }
}
