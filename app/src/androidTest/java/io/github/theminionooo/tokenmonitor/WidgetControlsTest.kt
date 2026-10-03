package io.github.theminionooo.tokenmonitor

import kotlin.math.roundToInt
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.HubRepositoryPool
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.storage.SecureConnectionStore
import io.github.theminionooo.tokenmonitor.data.storage.SnapshotCache
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import io.github.theminionooo.tokenmonitor.ui.InterfaceTheme
import io.github.theminionooo.tokenmonitor.widget.*
import org.junit.Assert.*
import org.junit.Test
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class WidgetControlsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun asset(name: String) = instrumentation.context.assets.open("protocol/v0.54.0/$name").bufferedReader().use { it.readText() }

    @Test fun fullDigitsRollOnChangeAndReducedMotionStaysStill() {
        val snapshot = HubProtocolParser.decodeSnapshot(asset("health.json"), asset("stats.json"), asset("devices.json"), asset("history.json"), asset("subscriptions.json"), System.currentTimeMillis(), false)
        instrumentation.runOnMainSync {
            val first = CounterFrame(snapshot.today.totalTokens)
            val initial = UsageWidgetProvider.render(context, snapshot, InterfaceTheme.Default, WidgetLayout.Large, first, true)
            val view = initial.apply(context, FrameLayout(context))
            val next = snapshot.copy(stats = snapshot.stats.copy(periods = snapshot.stats.periods + ("today" to snapshot.today.copy(totalTokens = 3_474_544))))
            val changed = counterFrame(first, next.today.totalTokens)
            UsageWidgetProvider.render(context, next, InterfaceTheme.Default, WidgetLayout.Large, changed, true).reapply(context, view)
            val counter = view.findViewById<ViewFlipper>(R.id.widget_counter)
            assertEquals(1, counter.displayedChild)
            assertEquals("3,474,544", (counter.currentView as TextView).text.toString())
            assertNotNull(counter.inAnimation)
            assertEquals(280, counter.inAnimation.duration)
            val steady = counterFrame(changed, next.today.totalTokens)
            assertFalse(steady.changed)
            UsageWidgetProvider.render(context, next, InterfaceTheme.Default, WidgetLayout.Large, steady, false).reapply(context, view)
            assertEquals(View.GONE, counter.visibility)
            assertEquals("3,474,544", view.findViewById<TextView>(R.id.widget_tokens).text.toString())
            assertEquals("9,223,372,036,854,775,807", widgetTokens(Long.MAX_VALUE))
            val density = context.resources.displayMetrics.density
            val width = (390 * density).toInt()
            val height = (440 * density).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            drawWidget(view, bitmap)
            context.openFileOutput("widget-render.png", 0).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun controlsFitAtMinimumSizesWithLargeTextAndLongCounts() {
        val source = HubProtocolParser.decodeSnapshot(asset("health.json"), asset("stats.json"), asset("devices.json"), asset("history.json"), asset("subscriptions.json"), System.currentTimeMillis(), false)
        val account = source.stats.limits.providers.first()
        val snapshot = source.copy(stats = source.stats.copy(
            periods = source.stats.periods + ("today" to source.today.copy(totalTokens = Long.MAX_VALUE)),
            limits = source.stats.limits.copy(providers = listOf(account, account.copy(provider = "claude"))),
        ))
        instrumentation.runOnMainSync {
            for (scale in listOf(1f, 1.3f)) {
                val configuration = android.content.res.Configuration(context.resources.configuration).apply { fontScale = scale }
                val sizedContext = context.createConfigurationContext(configuration)
                val density = sizedContext.resources.displayMetrics.density
                for (layout in WidgetLayout.entries) {
                    val view = UsageWidgetProvider.render(sizedContext, snapshot, InterfaceTheme.Default, layout).apply(sizedContext, FrameLayout(sizedContext))
                    val width = (layout.width * density).toInt()
                    val height = (layout.height * density * if (layout in listOf(WidgetLayout.Compact, WidgetLayout.Wide)) 1f else scale).toInt()
                    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, width, height)
                    val time = view.findViewById<TextView>(R.id.widget_time)
                    if (time != null) assertTrue("$layout at $scale must keep its footer inside the widget: ${time.bottom} vs ${height - view.findViewById<View>(R.id.widget_root).paddingBottom}", time.bottom <= height - view.findViewById<View>(R.id.widget_root).paddingBottom)
                    for (id in listOf(R.id.widget_refresh, R.id.widget_live)) {
                        // The narrow layouts keep Refresh in a footer that only fits from 150 dp up.
                        if (id == R.id.widget_refresh && view.findViewById<View>(R.id.widget_footer)?.visibility == View.GONE) continue
                        val button = view.findViewById<View>(id)
                        val bounds = android.graphics.Rect()
                        button.getDrawingRect(bounds)
                        (view as android.view.ViewGroup).offsetDescendantRectToMyCoords(button, bounds)
                        assertTrue("$layout at $scale control $id must fit: $bounds in ${width}x$height with padding ${view.findViewById<View>(R.id.widget_root).paddingRight},${view.findViewById<View>(R.id.widget_root).paddingBottom}", bounds.bottom <= height - view.findViewById<View>(R.id.widget_root).paddingBottom && bounds.right <= width - view.findViewById<View>(R.id.widget_root).paddingRight)
                        assertTrue("$layout needs usable control targets", button.width >= 48 * density - 1 && button.height >= 48 * density - 1)
                        if (button is TextView) assertTrue("$layout control label must fit", button.paint.measureText(button.text.toString()) <= button.width)
                    }
                    val total = view.findViewById<TextView>(R.id.widget_tokens)
                    assertTrue("$layout at $scale: full digits ${total.paint.measureText(total.text.toString())} must fit ${total.width} at ${total.textSize}", total.paint.measureText(total.text.toString()) <= total.width)
                    run {
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        drawWidget(view, bitmap)
                        context.openFileOutput("widget-minimum-${layout.name.lowercase()}-$scale.png", 0).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                }
            }
        }
    }

    @Test fun pickerAdvertisesSmallDefaultAndPopulatedPreview() {
        val info = android.appwidget.AppWidgetManager.getInstance(context).installedProviders.single {
            it.provider == android.content.ComponentName(context, UsageWidgetProvider::class.java)
        }
        assertEquals(2, info.targetCellWidth)
        assertEquals(2, info.targetCellHeight)
        val density = context.resources.displayMetrics.density
        assertEquals((110 * density).roundToInt(), info.minResizeWidth)
        assertEquals((110 * density).roundToInt(), info.minResizeHeight)
        assertEquals(R.layout.usage_widget_compact, info.initialLayout)
        assertEquals(R.layout.usage_widget_preview, info.previewLayout)
        instrumentation.runOnMainSync {
            val preview = android.widget.RemoteViews(context.packageName, info.previewLayout).apply(context, FrameLayout(context))
            val side = (160 * density).toInt()
            preview.measure(View.MeasureSpec.makeMeasureSpec(side, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(side, View.MeasureSpec.AT_MOST))
            preview.layout(0, 0, preview.measuredWidth, preview.measuredHeight)
            assertEquals(preview.width, preview.height)
            assertEquals("3,474,544", preview.findViewById<TextView>(R.id.widget_tokens).text.toString())
            val bitmap = Bitmap.createBitmap(preview.width, preview.height, Bitmap.Config.ARGB_8888)
            drawWidget(preview, bitmap)
            context.openFileOutput("widget-picker-preview.png", 0).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun livePollsWithoutDashboardSwitchesToOneStreamThenStops() {
        check(context.packageName.endsWith(".preview"))
        val address = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>().first { it.isSiteLocalAddress }.hostAddress
        val streams = AtomicInteger()
        val opened = AtomicInteger()
        val statsReads = AtomicInteger()
        val requestedStreamV2 = AtomicBoolean()
        val server = ServerSocket(0)
        val serverThread = thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    socket.use {
                        runCatching {
                            val reader = socket.getInputStream().bufferedReader()
                            val path = reader.readLine().split(' ')[1]
                            while (true) {
                                val header = reader.readLine() ?: break
                                if (header.isEmpty()) break
                                if (header.equals("x-token-monitor-stream: 2", ignoreCase = true)) requestedStreamV2.set(true)
                            }
                            val output = socket.getOutputStream()
                            if (path == "/api/stats/stream") {
                                streams.incrementAndGet(); opened.incrementAndGet()
                                try {
                                    output.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n\r\n".toByteArray()); output.flush()
                                    var total = 125430L
                                    while (true) {
                                        val raw = kotlinx.serialization.json.Json.parseToJsonElement(asset("stats.json").replace("125430", (++total).toString())).toString()
                                        output.write("event: snapshot\ndata: {\"stats\":$raw}\n\n".toByteArray()); output.flush()
                                        Thread.sleep(200)
                                    }
                                } finally { streams.decrementAndGet() }
                            } else {
                                if (path == "/api/stats") statsReads.incrementAndGet()
                                val body = asset(path.substringAfterLast('/') + ".json").toByteArray()
                                output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()); output.write(body); output.flush()
                            }
                        }
                    }
                }
            }
        }
        try {
            SecureConnectionStore(context).save(HubConnection("http://$address:${server.localPort}", "synthetic-test", true))
            fun start(action: String) = context.startForegroundService(Intent(context, WidgetLiveService::class.java).setAction(action))
            start(WidgetLiveService.ACTION_LIVE)
            await { WidgetRuntime.session.connected && WidgetRuntime.session.snapshot != null }
            assertTrue(WidgetRuntime.session.enabled)
            assertEquals("Widget Live should not open SSE while the dashboard is closed", 0, opened.get())
            assertEquals("The initial snapshot should read stats once", 1, statsReads.get())
            Thread.sleep(1_000)
            assertEquals("Widget Live must not poll aggressively", 1, statsReads.get())
            lateinit var hub: io.github.theminionooo.tokenmonitor.data.HubRepository
            instrumentation.runOnMainSync {
                hub = HubRepositoryPool.acquire(context)
                hub.setDashboardVisible(true)
            }
            await { opened.get() == 1 && streams.get() == 1 }
            assertTrue("Dashboard streams must negotiate protocol v2", requestedStreamV2.get())
            instrumentation.runOnMainSync {
                hub.setDashboardVisible(false)
                HubRepositoryPool.release(hub)
            }
            await { streams.get() == 0 && WidgetRuntime.session.connected }
            assertEquals("Opening the dashboard should create exactly one SSE stream", 1, opened.get())
            instrumentation.runOnMainSync { WidgetLiveService.stop(context) }
            await { !WidgetRuntime.session.enabled && streams.get() == 0 }
            val readsBeforeRefresh = statsReads.get()
            start(WidgetLiveService.ACTION_REFRESH)
            await { WidgetRuntime.session.snapshot?.fromCache == false }
            await { !WidgetRuntime.session.enabled && !WidgetRuntime.session.refreshing && streams.get() == 0 }
            assertTrue("Manual refresh should perform another stats read", statsReads.get() > readsBeforeRefresh)
        } finally {
            instrumentation.runOnMainSync { WidgetLiveService.stop(context) }
            SecureConnectionStore(context).clear()
            SnapshotCache(context).clear()
            server.close()
            serverThread.join(1000)
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(30)
        assertTrue("Widget condition did not become true within ten seconds", condition())
    }
}
