package io.github.theminionooo.tokenmonitor

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetHost
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.ui.InterfaceTheme
import io.github.theminionooo.tokenmonitor.widget.SwipeWidgetProvider
import io.github.theminionooo.tokenmonitor.widget.WidgetDeckPage
import io.github.theminionooo.tokenmonitor.widget.WidgetDeckPageState
import io.github.theminionooo.tokenmonitor.widget.WidgetDeckRenderer
import io.github.theminionooo.tokenmonitor.widget.WidgetSession
import io.github.theminionooo.tokenmonitor.widget.prepareWidgetDeck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class WidgetDeckDesignTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun asset(version: String, name: String): String =
        instrumentation.context.assets.open("protocol/$version/$name").bufferedReader().use { it.readText() }

    /** The dense showcase fixture: three tools, four models, four quota windows and 65 days of history. */
    private fun showcaseSnapshot() = HubProtocolParser.decodeSnapshot(
        showcase("health"),
        showcase("stats"),
        showcase("devices"),
        showcase("history"),
        showcase("subscriptions"),
        showcaseNow,
        false,
    )

    private val showcaseNow = java.time.Instant.parse("2026-09-07T12:00:00Z").toEpochMilli()

    private fun showcase(name: String): String =
        instrumentation.context.assets.open("showcase/$name.json").bufferedReader().use { it.readText() }

    private fun snapshot(version: String = "v0.55.0") = HubProtocolParser.decodeSnapshot(
        asset("v0.54.0", "health.json"),
        asset(version, "stats.json"),
        asset("v0.54.0", "devices.json"),
        if (version == "v0.55.0") null else asset("v0.54.0", "history.json"),
        asset("v0.54.0", "subscriptions.json"),
        java.time.Instant.parse("2026-09-10T12:00:00Z").toEpochMilli(),
        false,
    )

    @Test fun providerAdvertisesASeparateManualFourPageDeck() {
        val info = AppWidgetManager.getInstance(context).installedProviders.single {
            it.provider == ComponentName(context, SwipeWidgetProvider::class.java)
        }
        assertEquals(4, info.targetCellWidth)
        assertEquals(2, info.targetCellHeight)
        assertEquals(250f, info.minWidth / context.resources.displayMetrics.density, 1f)
        assertEquals(110f, info.minHeight / context.resources.displayMetrics.density, 1f)
        assertEquals(0, info.updatePeriodMillis)
        assertEquals(R.layout.usage_widget_deck, info.initialLayout)
        assertEquals(R.layout.usage_widget_deck_preview, info.previewLayout)
        assertEquals(4, WidgetDeckPage.entries.size)
        assertEquals(listOf("Overview", "Limits", "Breakdown", "Activity"), WidgetDeckPage.entries.map { it.name })

        instrumentation.runOnMainSync {
            val preview = android.widget.RemoteViews(context.packageName, info.previewLayout).apply(context, FrameLayout(context))
            val previewSize = SizeF(250f, 110f)
            layoutAtMost(preview, previewSize)
            val density = context.resources.displayMetrics.density
            assertEquals((previewSize.width * density).toInt(), preview.width)
            assertEquals((previewSize.height * density).toInt(), preview.height)
            assertEquals("3,474,544", preview.findViewById<TextView>(R.id.swipe_preview_tokens).text.toString())
            capture(preview, "widget-deck-picker-preview.png")
        }
    }

    @Test fun allPagesUseOneExactCanvasSize() {
        val state = io.github.theminionooo.tokenmonitor.widget.loadWidgetDeck(context, AppWidgetManager.INVALID_APPWIDGET_ID)
        instrumentation.runOnMainSync {
            val size = SizeF(320f, 180f)
            val sizes = WidgetDeckPage.entries.map { page ->
                val view = WidgetDeckRenderer.render(context, page, state.data, state.theme, size).apply(context, FrameLayout(context))
                layoutAtMost(view, size)
                val image = view.findViewById<ImageView>(R.id.swipe_page_bitmap)
                val bitmap = (image.drawable as BitmapDrawable).bitmap
                bitmap.width to bitmap.height
            }
            assertEquals(1, sizes.distinct().size)
            assertEquals(WidgetDeckRenderer.CARD_ASPECT, sizes.first().first / sizes.first().second.toFloat(), 0.01f)
            val density = context.resources.displayMetrics.density
            assertEquals("bitmap must render at launcher density", (size.width * density).roundToInt(), sizes.first().first)
        }
    }

    @Test fun pageStateCyclesInBothDirectionsAndWraps() {
        val widgetId = 8042
        WidgetDeckPageState.remove(context, intArrayOf(widgetId))
        assertEquals(WidgetDeckPage.Overview, WidgetDeckPageState.read(context, widgetId))
        assertEquals(WidgetDeckPage.Limits, WidgetDeckPageState.move(context, widgetId, 1))
        assertEquals(WidgetDeckPage.Overview, WidgetDeckPageState.move(context, widgetId, -1))
        assertEquals(WidgetDeckPage.Activity, WidgetDeckPageState.move(context, widgetId, -1))
        assertEquals(WidgetDeckPage.Overview, WidgetDeckPageState.move(context, widgetId, 1))
        WidgetDeckPageState.remove(context, intArrayOf(widgetId))
    }

    @Test fun hostedEdgeControlsCycleTheSingleCard() {
        check(context.packageName.endsWith(".preview"))
        fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command),
        ).use { it.readBytes() }
        val user = String(shell("am get-current-user")).trim().toInt()
        val host = AppWidgetHost(context, 5414)
        val manager = AppWidgetManager.getInstance(context)
        try {
            shell("appwidget grantbind --package ${context.packageName} --user $user")
            val widgetId = host.allocateAppWidgetId()
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 360)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 360)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 220)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 220)
            }
            assertTrue(manager.bindAppWidgetIdIfAllowed(widgetId, ComponentName(context, SwipeWidgetProvider::class.java), options))
            val provider = SwipeWidgetProvider()
            lateinit var view: android.appwidget.AppWidgetHostView
            instrumentation.runOnMainSync {
                host.startListening()
                provider.onUpdate(context, manager, intArrayOf(widgetId))
                view = host.createView(context, widgetId, manager.getAppWidgetInfo(widgetId))
            }

            fun description(): String {
                var value = ""
                instrumentation.runOnMainSync {
                    value = view.findViewById<ImageView>(R.id.swipe_page_bitmap).contentDescription.toString()
                }
                return value
            }
            fun awaitPage(name: String) {
                val deadline = System.currentTimeMillis() + 3_000
                while (!description().contains(name, ignoreCase = true) && System.currentTimeMillis() < deadline) Thread.sleep(25)
                assertTrue("Hosted card did not show $name: ${description()}", description().contains(name, ignoreCase = true))
            }

            awaitPage("Overview")
            instrumentation.runOnMainSync { view.findViewById<View>(R.id.swipe_page_next).performClick() }
            awaitPage("Limits")
            instrumentation.runOnMainSync { view.findViewById<View>(R.id.swipe_page_previous).performClick() }
            awaitPage("Overview")
        } finally {
            instrumentation.runOnMainSync { host.stopListening(); host.deleteHost() }
            shell("appwidget revokebind --package ${context.packageName} --user $user")
        }
    }


    @Test fun sparseBreakdownRowsStayAtTheTopOfTheCard() {
        val now = showcaseNow
        val full = prepareWidgetDeck(
            showcaseSnapshot(),
            WidgetSession(enabled = true, connected = true, expiresAt = now + 3_600_000L),
            now = now,
        )
        val sparse = full.copy(tools = full.tools.take(1), models = full.models.take(1))
        instrumentation.runOnMainSync {
            val views = WidgetDeckRenderer.render(context, WidgetDeckPage.Breakdown, sparse, InterfaceTheme.Default, SizeF(320f, 180f))
            val view = views.apply(context, FrameLayout(context))
            layoutAtMost(view, SizeF(320f, 180f))
            val bitmap = (view.findViewById<ImageView>(R.id.swipe_page_bitmap).drawable as BitmapDrawable).bitmap
            assertEquals(WidgetDeckRenderer.CARD_ASPECT, bitmap.width / bitmap.height.toFloat(), 0.01f)
            assertTrue(view.findViewById<ImageView>(R.id.swipe_page_bitmap).contentDescription.contains("Breakdown", ignoreCase = true))
            capture(view, "widget-deck-breakdown-sparse.png")
        }
    }

    @Test fun allPagesRenderAtMediumAndLargeSizesInBothThemes() {
        val now = showcaseNow
        val data = prepareWidgetDeck(
            showcaseSnapshot(),
            WidgetSession(enabled = true, connected = true, expiresAt = now + 3_600_000L),
            now = now,
        )
        assertEquals("the gallery fixture must be dense", 3, data.tools.size)
        assertEquals(4, data.models.size)
        assertEquals(2, data.limitGroups.size)
        assertEquals(7, data.week.size)
        instrumentation.runOnMainSync {
            for (theme in listOf(InterfaceTheme.Default, InterfaceTheme.Porcelain,
                InterfaceTheme.fromCode("TM2-B7EAD4-303438-EEF5FB-A3ADBB-FF6688")!!)) {
                for (size in listOf(SizeF(250f, 110f), SizeF(320f, 180f), SizeF(360f, 220f))) {
                    val renderedSizes = mutableListOf<Pair<Int, Int>>()
                    for (page in WidgetDeckPage.entries) {
                        val view = WidgetDeckRenderer.render(context, page, data, theme, size).apply(context, FrameLayout(context))
                        layout(view, size)
                        val image = view.findViewById<ImageView>(R.id.swipe_page_bitmap)
                        val bitmap = (image.drawable as BitmapDrawable).bitmap
                        assertTrue(image.contentDescription.contains(context.getString(page.subtitle)))
                        assertEquals(WidgetDeckRenderer.CARD_ASPECT, bitmap.width / bitmap.height.toFloat(), 0.01f)
                        assertEquals("rounded card corners must remain transparent", 0, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
                        assertInside(view, R.id.widget_refresh, size)
                        assertInside(view, R.id.widget_live, size)
                        assertInside(view, R.id.swipe_page_previous, size)
                        assertInside(view, R.id.swipe_page_next, size)
                        val density = context.resources.displayMetrics.density
                        val card = view.findViewById<View>(R.id.swipe_card)
                        assertEquals(view.width, card.width)
                        assertEquals(view.height, card.height)
                        renderedSizes += view.width to view.height
                        for (id in listOf(R.id.widget_refresh, R.id.widget_live, R.id.swipe_page_previous, R.id.swipe_page_next)) {
                            val control = view.findViewById<View>(id)
                            assertTrue("$page $size control $id is narrower than 48dp", control.width >= 48 * density - 1)
                            assertTrue("$page $size control $id is shorter than 48dp", control.height >= 48 * density - 1)
                            assertTrue("$page $size control $id needs an accessibility label", !control.contentDescription.isNullOrBlank())
                        }
                        capture(view, "widget-deck-${page.name.lowercase()}-${InterfaceTheme.idOf(theme)}-${size.width.toInt()}x${size.height.toInt()}.png")
                    }
                    assertEquals("pages changed canvas size for $theme at $size: $renderedSizes", 1, renderedSizes.distinct().size)
                }
            }
        }
    }

    @Test fun v054AndSavedEmptyOfflineAndStaleStatesRemainTruthful() {
        val old = snapshot("v0.54.0")
        assertTrue(!old.today.throughputAvailable)
        val saved = prepareWidgetDeck(old, WidgetSession(), now = old.capturedAt)
        val stale = prepareWidgetDeck(old, WidgetSession(), now = old.capturedAt + (old.stats.staleAfterMs ?: 300_000L) + 1)
        val offline = prepareWidgetDeck(old, WidgetSession(note = "Connection refused"), now = old.capturedAt)
        val empty = prepareWidgetDeck(null, WidgetSession(), now = old.capturedAt)
        assertEquals("SAVED", saved.status)
        assertEquals("STALE", stale.status)
        assertEquals("OFFLINE", offline.status)
        assertEquals("NO DATA", empty.status)
        instrumentation.runOnMainSync {
            listOf(saved, stale, offline).forEach { data ->
                val view = WidgetDeckRenderer.render(context, WidgetDeckPage.Overview, data, InterfaceTheme.Default, SizeF(320f, 220f)).apply(context, FrameLayout(context))
                layout(view, SizeF(320f, 220f))
                assertTrue(view.findViewById<ImageView>(R.id.swipe_page_bitmap).contentDescription.contains(data.status, ignoreCase = true))
                capture(view, "widget-deck-overview-${data.status.lowercase()}.png")
            }
            val emptyView = WidgetDeckRenderer.render(context, WidgetDeckPage.Overview, empty, InterfaceTheme.Default, SizeF(320f, 220f)).apply(context, FrameLayout(context))
            layout(emptyView, SizeF(320f, 220f))
            assertTrue(emptyView.findViewById<ImageView>(R.id.swipe_page_bitmap).contentDescription.contains("No data", ignoreCase = true))
            capture(emptyView, "widget-deck-overview-empty.png")
        }
    }

    private fun layout(view: View, size: SizeF) {
        val density = context.resources.displayMetrics.density
        val width = (size.width * density).toInt()
        val height = (size.height * density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }

    private fun layoutAtMost(view: View, size: SizeF) {
        val density = context.resources.displayMetrics.density
        val width = (size.width * density).toInt()
        val height = (size.height * density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun assertInside(root: View, id: Int, size: SizeF) {
        val density = context.resources.displayMetrics.density
        val child = root.findViewById<View>(id)
        assertTrue("control $id is missing", child != null)
        val bounds = android.graphics.Rect()
        child.getDrawingRect(bounds)
        (root as android.view.ViewGroup).offsetDescendantRectToMyCoords(child, bounds)
        assertTrue("control $id is clipped: $bounds", bounds.left >= 0 && bounds.top >= 0)
        assertTrue("control $id is clipped: $bounds", bounds.right <= size.width * density && bounds.bottom <= size.height * density)
    }

    private fun capture(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        context.openFileOutput(name, 0).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
