package io.github.theminionooo.tokenmonitor

import android.app.Application
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.storage.DisplayPreferences
import io.github.theminionooo.tokenmonitor.ui.DashboardViewModel
import io.github.theminionooo.tokenmonitor.ui.InterfaceTheme
import io.github.theminionooo.tokenmonitor.ui.Palette
import io.github.theminionooo.tokenmonitor.widget.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class WidgetThemeTest {
    @Test fun allShapesUseAppPaletteIncludingLightAndCustomThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".preview"))
        fun asset(name: String) = instrumentation.context.assets.open("showcase/$name.json").bufferedReader().use { it.readText() }
        val snapshot = HubProtocolParser.decodeSnapshot(asset("health"), asset("stats"), asset("devices"), asset("history"), asset("subscriptions"), Instant.parse("2026-09-07T18:00:00Z").toEpochMilli(), false)
        val themes = InterfaceTheme.presets + ("custom" to InterfaceTheme("#c4b5fd", "#241e30", "#f5f0ff", "#b8adc9")) +
            ("chart" to InterfaceTheme.fromCode("TM2-B7EAD4-303438-EEF5FB-A3ADBB-FF6688")!!)
        instrumentation.runOnMainSync {
            for ((name, theme) in themes) for (layout in WidgetLayout.entries) for (empty in listOf(false, true)) {
                val palette = Palette.from(theme)
                val view = UsageWidgetProvider.render(context, snapshot.takeUnless { empty }, theme, layout,
                    session = WidgetSession(enabled = true, connected = !empty, expiresAt = System.currentTimeMillis() + 3_600_000),
                    size = SizeF(if (layout in listOf(WidgetLayout.Compact, WidgetLayout.Portrait)) 160f else 360f, when (layout) { WidgetLayout.Compact -> 160f; WidgetLayout.Wide -> 120f; WidgetLayout.Portrait -> 260f; WidgetLayout.Overview -> 280f; WidgetLayout.Large -> 400f })).apply(context, FrameLayout(context))
                val density = context.resources.displayMetrics.density
                val width = ((if (layout in listOf(WidgetLayout.Compact, WidgetLayout.Portrait)) 160 else 360) * density).toInt()
                val height = ((when (layout) { WidgetLayout.Compact -> 160; WidgetLayout.Wide -> 120; WidgetLayout.Portrait -> 260; WidgetLayout.Overview -> 280; WidgetLayout.Large -> 400 }) * density).toInt()
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, width, height)
                assertEquals(palette.ink.toArgb(), view.findViewById<TextView>(if (empty) R.id.widget_empty_title else R.id.widget_tokens).currentTextColor)
                assertEquals((palette.strongLine.alpha * 255).toInt(), view.findViewById<ImageView>(R.id.widget_surface_border).imageAlpha)
                val gradient = (view.findViewById<ImageView>(R.id.widget_surface_color).drawable as BitmapDrawable).bitmap
                val top = gradient.getPixel(0, 0)
                val expected = palette.gradientTop.toArgb()
                for (shift in listOf(0, 8, 16)) assertTrue(kotlin.math.abs(((top shr shift) and 255) - ((expected shr shift) and 255)) <= 2)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                drawWidget(view, bitmap)
                assertEquals("Native rounded outline must be preserved", 0, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
                val live = view.findViewById<TextView>(R.id.widget_live)
                val bounds = Rect()
                live.getDrawingRect(bounds)
                (view as android.view.ViewGroup).offsetDescendantRectToMyCoords(live, bounds)
                assertEquals("$name $layout Live text must use app accent", palette.accent.toArgb(), live.currentTextColor)
                context.openFileOutput("theme-$name-${layout.name.lowercase()}-${if (empty) "empty" else "live"}.png", 0).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }

    @Test fun changingAppThemeRecolorsSavedHostWithoutStartingLive() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".preview"))
        fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_USE_ACCESSIBILITY).executeShellCommand(command)).use { it.readBytes() }
        val user = String(shell("am get-current-user")).trim().toInt()
        val previous = DisplayPreferences(context).options.value.themeCode
        val host = AppWidgetHost(context, 5412)
        val manager = AppWidgetManager.getInstance(context)
        val store = ViewModelStore()
        try {
            WidgetLiveService.stop(context)
            shell("appwidget grantbind --package ${context.packageName} --user ${user}")
            val id = host.allocateAppWidgetId()
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 360)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 360)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 400)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 400)
                putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(360f, 400f)))
            }
            assertTrue(manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, UsageWidgetProvider::class.java), options))
            var hostView: android.appwidget.AppWidgetHostView? = null
            instrumentation.runOnMainSync {
                host.startListening()
                hostView = host.createView(context, id, manager.getAppWidgetInfo(id))
                val model = DashboardViewModel(context.applicationContext as Application)
                store.put("theme-test", model)
                model.setThemeCode(InterfaceTheme.Porcelain.code)
            }
            var changed = false
            val deadline = System.currentTimeMillis() + 5_000
            while (!changed && System.currentTimeMillis() < deadline) {
                instrumentation.runOnMainSync {
                    changed = hostView?.findViewById<TextView>(R.id.widget_title)?.currentTextColor == Palette.from(InterfaceTheme.Porcelain).ink.toArgb()
                }
                if (!changed) Thread.sleep(25)
            }
            assertTrue("Saved host must receive the new theme without a data update", changed)
            assertFalse(WidgetRuntime.session.enabled)
        } finally {
            instrumentation.runOnMainSync { store.clear(); host.stopListening(); host.deleteHost() }
            DisplayPreferences(context).setThemeCode(previous)
            shell("appwidget revokebind --package ${context.packageName} --user ${user}")
        }
    }
}
