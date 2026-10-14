package dev.bing.tokenmonitor

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant

/** Real system host + synthetic Hub data. These captures are emulator evidence, not Xiaomi evidence. */
class WidgetAlignmentHostTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private val snapshot = HubParser.parse("""{"updatedAt":"2026-10-14T11:59:00Z","periods":{
      "today":{"totalTokens":27800000,"costUsd":14.86,"clients":{"codex":22300000,"claude":5500000},"models":{"GPT-5.6":20900000,"MiMo":2900000}},
      "month":{"totalTokens":61200000,"costUsd":237.42},"allTime":{"totalTokens":180000000,"costUsd":620.15}},
      "limits":{"providers":[{"provider":"codex","status":"ok","updatedAt":"2026-10-14T11:59:00Z","windows":[{"kind":"session","remainingPercent":70},{"kind":"weekly","remainingPercent":68}]},
      {"provider":"deepseek","status":"ok","updatedAt":"2026-10-14T11:59:00Z","balance":{"amount":27.70,"currency":"CNY"},"windows":[]}]}}""")
    private val history = HistoryParser.parse("""{"daily":[{"date":"2026-10-08","tokens":1000000},{"date":"2026-10-09","tokens":2000000},{"date":"2026-10-10","tokens":4000000},{"date":"2026-10-11","tokens":3000000},{"date":"2026-10-12","tokens":0},{"date":"2026-10-13","tokens":5000000},{"date":"2026-10-14","tokens":27800000}]}""")
    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun texts(view: View): List<String> = buildList {
        if (view is TextView && view.visibility == View.VISIBLE) add(view.text.toString())
        if (view is ViewGroup && view.visibility == View.VISIBLE) for (index in 0 until view.childCount) addAll(texts(view.getChildAt(index)))
    }
    @Test fun actualHostAllModesCompactDashboardLargeFontThemesAndResize() {
        val manager = AppWidgetManager.getInstance(context)
        lateinit var host: AppWidgetHost
        onMain { host = AppWidgetHost(context, 71905) }
        val id = host.allocateAppWidgetId()
        try {
            instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
            assertTrue(manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, HubWidgetProvider::class.java)))
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            val info = requireNotNull(manager.getAppWidgetInfo(id))
            assertEquals(1_800_000, info.updatePeriodMillis)
            assertEquals(ComponentName(context, WidgetConfigurationActivity::class.java), info.configure)
            val cache = WidgetCache.Ready(widgetUsage(snapshot, now.minusSeconds(60).toEpochMilli()), snapshot, history)
            val cases = listOf(Triple("summary", 180, 280), Triple("dashboard", 320, 360), Triple("dashboard", 360, 400), Triple("tools", 280, 360), Triple("models", 280, 360), Triple("activity", 280, 360))
            for (dark in listOf(false, true)) for ((mode, width, height) in cases) for (scale in listOf(1f, 2f)) {
                val localized = HubWidget.localizedContext(context, LanguageChoice.CHINESE).let { base -> base.createConfigurationContext(Configuration(base.resources.configuration).apply { fontScale = scale }) }
                val preferences = DisplayPreferences(theme = if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT, language = LanguageChoice.CHINESE, main = MainDisplayPreferences(compactUnits = "localized"))
                val options = Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width); putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height) }
                onMain {
                    val view = host.createView(localized, id, info)
                    view.updateAppWidget(HubWidget.render(localized, preferences, cache, id, options, WidgetConfiguration(mode = mode), now))
                    val density = localized.resources.displayMetrics.density
                    val w = (width * density).toInt(); val h = (height * density).toInt()
                    view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)); view.layout(0, 0, w, h)
                    fun check(child: View) {
                        if (child.visibility != View.VISIBLE) return
                        if (child is TextView && child.text.isNotEmpty()) {
                            val details = "mode=$mode font=$scale $width x $height text=${child.text} size=${child.width}x${child.height} natural=${child.layout?.height} bottom=${child.bottom} parent=${(child.parent as View).height}"
                            assertTrue("Natural text must fit: $details", child.layout?.height?.let { it <= child.height - child.totalPaddingTop - child.totalPaddingBottom } != false)
                            assertTrue("Text stays inside its parent: $details", child.bottom <= (child.parent as View).height)
                        }
                        if (child is ViewGroup) for (i in 0 until child.childCount) check(child.getChildAt(i))
                    }
                    check(view)
                    val edit = view.findViewById<View>(R.id.widget_configure)
                    assertTrue(edit.height >= (48 * density).toInt()); assertTrue(edit.width >= (48 * density).toInt())
                    if (mode == "dashboard" && scale == 1f) {
                        assertNotNull(view.findViewById<View>(R.id.widget_activity_section))
                        assertTrue(texts(view).any { it.contains("70%") })
                        assertTrue(texts(view).any { it.contains("68%") })
                        assertTrue(texts(view).any { it.contains("GPT-5.6") })
                        val sections = listOf(R.id.widget_metric_section, R.id.widget_quota_section, R.id.widget_activity_section, R.id.widget_breakdown_section)
                        sections.forEach { section -> assertTrue("Section click target retains 48dp", view.findViewById<View>(section).height >= (48 * density).toInt()) }
                    }
                    if (mode in listOf("summary", "dashboard")) assertEquals("Token：27,800,000", view.findViewById<TextView>(R.id.widget_tokens).contentDescription)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); view.draw(Canvas(bitmap))
                    val suffix = InstrumentationRegistry.getArguments().getString("visualSuffix") ?: "emulator"
                    File(context.getExternalFilesDir(null), "visual-widget-$mode-${if(dark) "dark" else "light"}-font$scale-$width-$height-$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                }
            }
        } finally { instrumentation.uiAutomation.dropShellPermissionIdentity(); onMain { host.deleteHost() } }
    }
    @Test fun instanceMetadataCancellationDeletionRestoreAndPendingIntentTargetsStayIsolated() {
        val manager = AppWidgetManager.getInstance(context)
        lateinit var host: AppWidgetHost
        onMain { host = AppWidgetHost(context, 71906) }
        val first = host.allocateAppWidgetId(); val second = host.allocateAppWidgetId()
        val store = WidgetConfigurationStore(context)
        try {
            assertEquals(WidgetConfiguration(), store.load(first))
            val old = WidgetConfiguration(mode = "tools", period = "month")
            store.save(first, old); store.save(second, WidgetConfiguration(mode = "activity"))
            assertEquals(old, store.load(first)); assertEquals("activity", store.load(second).mode)
            assertFalse(HubWidget.openIntent(context, first, old).filterEquals(HubWidget.openIntent(context, second, old)))
            assertNotEquals(HubWidget.openPending(context, first, old), HubWidget.openPending(context, second, old))
            assertEquals("month", HubWidget.openIntent(context, first, old).getStringExtra(HubWidget.PERIOD))
            // Configuration cancellation is read-only; merely loading the draft cannot persist it.
            val draft = store.load(first).copy(period = "allTime")
            assertNotEquals(draft, store.load(first))
            store.restore(intArrayOf(first), intArrayOf(second), false)
            assertEquals(old, store.load(second)); assertEquals(WidgetConfiguration(), store.load(first))
            HubWidgetProvider().onDeleted(context, intArrayOf(second))
            assertEquals(WidgetConfiguration(), store.load(second))
            assertEquals("quota", store.load(second, true).mode)
        } finally { store.delete(first); store.delete(second); onMain { host.deleteHost() } }
    }
}
