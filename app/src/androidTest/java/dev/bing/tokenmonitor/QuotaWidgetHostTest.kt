package dev.bing.tokenmonitor

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import java.net.Inet4Address
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
import java.net.ServerSocket
import java.time.Instant
import java.util.UUID

/** Synthetic encrypted cache and actual system widget host. No physical-device claim. */
class QuotaWidgetHostTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun texts(view: View): List<String> = buildList {
        if (view is TextView) add(view.text.toString())
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(texts(view.getChildAt(index)))
    }
    private fun render(context: Context, cache: QuotaWidgetCache, prefs: DisplayPreferences = DisplayPreferences(language = LanguageChoice.ENGLISH)): View {
        lateinit var view: View
        onMain { view = HubQuotaWidget.render(context, prefs, cache, 701, now = Instant.parse("2026-10-13T12:00:00Z")).apply(context, null) }
        return view
    }
    @Test fun encryptedMissingCorruptStaleAndDualAccountCachesNeverContactNetwork() {
        val store = HubStore(context, "quota-widget-test.bin")
        val english = HubWidget.localizedContext(context, LanguageChoice.ENGLISH).let { base -> base.createConfigurationContext(Configuration(base.resources.configuration).apply { fontScale = 1f }) }
        val address = context.getSystemService(ConnectivityManager::class.java).let { connectivity ->
            connectivity.getLinkProperties(connectivity.activeNetwork)?.linkAddresses?.firstOrNull { it.address is Inet4Address }?.address?.hostAddress
        } ?: error("Widget test emulator requires a local IPv4 network")
        val server = ServerSocket(0).apply { soTimeout = 300 }
        try {
            assertTrue(texts(render(english, HubQuotaWidget.load(store))).any { it.contains("No saved quota cache") })
            val body = """{"periods":{},"limits":{"providers":[
                {"provider":"codex","status":"ok","updatedAt":"2026-10-13T11:00:00Z","accountEmail":"one@example.invalid","windows":[{"kind":"session","remainingPercent":0},{"kind":"weekly","remainingPercent":70}]},
                {"provider":"codex","status":"ok","updatedAt":"2026-10-13T11:00:00Z","accountEmail":"two@example.invalid","windows":[{"kind":"session"},{"kind":"weekly","remainingPercent":10}]}]}}"""
            store.save(SavedHub("http://$address:${server.localPort}", UUID.randomUUID().toString(), body, Instant.parse("2026-10-13T11:00:00Z").toEpochMilli()))
            assertNotNull("Saved encrypted fixture must round-trip through HubStore", store.load())
            val cache = HubQuotaWidget.load(store)
            val view = render(english, cache)
            val text = texts(view).joinToString("\n")
            assertTrue("Expected stale saved cache: $text", text.contains("Stale cache")); assertTrue("Expected 60 minute age: $text", text.contains("60 min old"))
            assertTrue(text.contains("0% left")); assertTrue(text.contains("70% left")); assertTrue(text.contains("Quota unavailable"))
            assertTrue(text.contains("o***e@example.invalid")); assertTrue(text.contains("t***o@example.invalid"))
            assertFalse(text.contains("one@")); assertFalse(text.contains(address))
            assertTrue(runCatching { server.accept().close() }.exceptionOrNull() is java.net.SocketTimeoutException)
            val path = File(context.noBackupFilesDir, "quota-widget-test.bin")
            val bytes = path.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); path.writeBytes(bytes)
            assertTrue(texts(render(english, HubQuotaWidget.load(store))).any { it.contains("could not be read") })
        } finally { store.clear(); server.close() }
    }
    @Test fun actualHostThemeSizeAndLargeFontEvidence() {
        val manager = AppWidgetManager.getInstance(context)
        lateinit var host: AppWidgetHost
        onMain { host = AppWidgetHost(context, 71904) }
        val id = host.allocateAppWidgetId()
        try {
            instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
            assertTrue(manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, HubQuotaWidgetProvider::class.java)))
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            val info = manager.getAppWidgetInfo(id)!!
            assertEquals(0, info.updatePeriodMillis)
            assertFalse(context.packageManager.getReceiverInfo(ComponentName(context, HubQuotaWidgetProvider::class.java), 0).exported)
            val snapshot = HubParser.parse("""{"periods":{},"limits":{"providers":[
                {"provider":"codex","status":"ok","accountEmail":"very.long.personal.account@long.organization.example.invalid","updatedAt":"2026-10-13T11:59:00Z","windows":[{"kind":"session","remainingPercent":15},{"kind":"weekly","remainingPercent":70}]},
                {"provider":"codex","status":"ok","accountEmail":"second.long.personal.account@long.organization.example.invalid","updatedAt":"2026-10-13T11:59:00Z","windows":[{"kind":"session","remainingPercent":0},{"kind":"weekly","remainingPercent":10}]}]}}""")
            val cache = QuotaWidgetCache.Ready(snapshot, Instant.parse("2026-10-13T11:59:00Z").toEpochMilli())
            for ((theme, scale, width, height) in listOf(
                listOf(0, 1, 280, 360), listOf(1, 1, 400, 440), listOf(1, 2, 280, 480), listOf(0, 2, 280, 360))) {
                val localized = HubWidget.localizedContext(context, LanguageChoice.ENGLISH).let { base -> base.createConfigurationContext(Configuration(base.resources.configuration).apply { fontScale = scale.toFloat() }) }
                val prefs = DisplayPreferences(theme = if(theme == 0) ThemeChoice.LIGHT else ThemeChoice.DARK, language = LanguageChoice.ENGLISH)
                onMain {
                    val view = host.createView(localized, id, info)
                    view.updateAppWidget(HubQuotaWidget.render(localized, prefs, cache, id, Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height) }, Instant.parse("2026-10-13T12:00:00Z")))
                    val density = localized.resources.displayMetrics.density
                    val w = (width * density).toInt(); val h = (height * density).toInt()
                    view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)); view.layout(0, 0, w, h)
                    val bitmap = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); view.draw(Canvas(bitmap))
                    val suffix = InstrumentationRegistry.getArguments().getString("visualSuffix") ?: "phone"
                    File(context.getExternalFilesDir(null), "visual-quota-widget-theme$theme-font$scale-$width-$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
                    val dimensions = "host=${width}x${height}dp font=$scale actualFont=${localized.resources.configuration.fontScale} px=${w}x$h density=$density"
                    val action = view.findViewById<TextView>(R.id.quota_widget_open)
                    assertTrue("Quota action retains 48dp: $dimensions actionHeight=${action.height}", action.height >= (48 * density).toInt())
                    fun check(child: View) {
                        if (child.visibility != View.VISIBLE) return
                        if (child is TextView && child.text.isNotEmpty()) {
                            val diagnostic = "$dimensions id=${child.resources.getResourceEntryName(child.id)} view=${child.width}x${child.height} layout=${child.layout.width}x${child.layout.height} bottom=${child.bottom} parentHeight=${(child.parent as View).height} padding=${child.totalPaddingTop}+${child.totalPaddingBottom} text=${child.text}"
                            assertTrue("Quota text must not clip: $diagnostic", child.layout.height <= child.height - child.totalPaddingTop - child.totalPaddingBottom)
                            assertTrue("Quota text must fit parent: $diagnostic", child.bottom <= (child.parent as View).height)
                        }
                        if(child is ViewGroup) for(i in 0 until child.childCount) check(child.getChildAt(i))
                    }
                    check(view)
                    assertTrue(texts(view).any { it.contains("15% left") && it.contains("70% left") })
                }
            }
        } finally { instrumentation.uiAutomation.dropShellPermissionIdentity(); onMain { host.deleteHost() } }
    }
}
