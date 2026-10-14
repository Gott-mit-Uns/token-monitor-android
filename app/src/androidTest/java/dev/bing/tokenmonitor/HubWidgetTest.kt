package dev.bing.tokenmonitor

import android.Manifest
import android.app.Application
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.net.ConnectivityManager
import java.net.Inet4Address
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelStore
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Actual system host binding and RemoteViews delivery, with synthetic cache only. */
@SdkSuppress(minSdkVersion = 29)
class HubWidgetTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = AppWidgetManager.getInstance(context)
    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST") return result as T
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(30)
        assertTrue("Widget condition was not reached", condition())
    }
    private fun texts(view: View): List<String> = buildList {
        if (view is TextView) add(view.text.toString().replace("\u200B", ""))
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(texts(view.getChildAt(index)))
    }
    private fun withHost(block: (AppWidgetHostView, Int, AppWidgetProviderInfo) -> Unit) {
        val host = onMain { AppWidgetHost(context, 71903) }
        val id = host.allocateAppWidgetId()
        try {
            instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
            val bound = manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, HubWidgetProvider::class.java))
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            assertTrue("System refused test widget host binding", bound)
            val info = manager.getAppWidgetInfo(id)!!
            val view = onMain { host.createView(context, id, info).also { host.startListening() } }
            block(view, id, info)
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            onMain { host.stopListening(); host.deleteHost() }
        }
    }
    private fun capture(view: AppWidgetHostView, name: String, widthDp: Int, heightDp: Int) = onMain {
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).toInt(); val height = (heightDp * density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val open = view.findViewById<TextView>(R.id.widget_open)
        val target = if (open.visibility == View.VISIBLE) open else view.findViewById<View>(R.id.widget_root)
        assertTrue("Widget open target must be at least 48dp", target.height >= (48 * density).toInt())
        fun checkVisible(child: View) {
            if (child.visibility != View.VISIBLE) return
            if (child is TextView && child.text.isNotEmpty()) {
                val diagnostic = "id=${child.resources.getResourceEntryName(child.id)} text=${child.text} size=${child.width}x${child.height} natural=${child.layout?.height} padding=${child.totalPaddingTop}+${child.totalPaddingBottom} font=${context.resources.configuration.fontScale}"
                assertTrue("Widget text was clipped vertically: $diagnostic", child.layout?.height?.let { it <= child.height - child.totalPaddingTop - child.totalPaddingBottom } != false)
                assertTrue("Widget content extends beyond host height: $diagnostic", child.bottom <= view.height)
                if (child.id == R.id.widget_tokens) {
                    assertEquals("Compact token value must remain on one line: $diagnostic", 1, child.layout.lineCount)
                    val available = child.width-child.totalPaddingLeft-child.totalPaddingRight
                    assertTrue("Compact token value fits actual TextView width: $diagnostic available=$available line=${child.layout.getLineWidth(0)}", child.layout.getLineWidth(0) <= available)
                    assertFalse("Compact token value must not be ellipsized: $diagnostic", child.layout.getEllipsisCount(0) > 0)
                }
            }
            if (child is ViewGroup) for (index in 0 until child.childCount) checkVisible(child.getChildAt(index))
        }
        checkVisible(view)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun actualHostReadsEncryptedCacheWithoutNetworkAndClearsImmediately() {
        val store = HubStore(context)
        val displayStore = DisplayPreferenceStore(context)
        assertNull("Widget test requires an empty app profile", store.load())
        val original = displayStore.load()
        val address = context.getSystemService(ConnectivityManager::class.java).let { connectivity ->
            connectivity.getLinkProperties(connectivity.activeNetwork)?.linkAddresses?.firstOrNull { it.address is Inet4Address }?.address?.hostAddress
        } ?: error("Test emulator requires a local IPv4 network")
        val server = ServerSocket(0).apply { soTimeout = 1200 }
        try {
            displayStore.save(DisplayPreferences(theme = ThemeChoice.LIGHT, language = LanguageChoice.ENGLISH))
            withHost { view, id, info ->
                assertEquals(1_800_000, info.updatePeriodMillis)
                assertTrue(info.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0)
                if (android.os.Build.VERSION.SDK_INT >= 36) assertTrue(info.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_NOT_KEYGUARD != 0)
                assertFalse(context.packageManager.getReceiverInfo(ComponentName(context, HubWidgetProvider::class.java), 0).exported)
                HubWidget.updateAll(context)
                await { onMain { texts(view).any { it.contains("Connect in the app") } } }
                val generated = UUID.randomUUID().toString()
                val body = """{"updatedAt":"2026-10-08T00:00:00Z","periods":{"today":{"totalTokens":9223372036854775807,"costUsd":"0.1234567890123456789","unpricedTokens":12,"projects":{"secret-project":{"label":"private fixture project"}}}},"devices":[{"deviceId":"a","hostname":"private fixture device"}]}"""
                store.save(SavedHub("http://$address:${server.localPort}", generated, body, 1791507600000))
                HubWidget.updateAll(context)
                await { onMain { texts(view).contains("9223372036.9B") } }
                assertEquals("Tokens: 9,223,372,036,854,775,807", onMain { view.findViewById<TextView>(R.id.widget_tokens).contentDescription.toString() })
                val rendered = onMain { texts(view).joinToString("\n") }
                assertTrue(rendered.contains("$0.12 + ?"))
                assertTrue(onMain { view.findViewById<TextView>(R.id.widget_cost).contentDescription.toString() }.contains("Known cost only"))
                assertTrue(rendered.contains("10-09"))
                assertFalse(rendered.contains("private fixture"))
                assertFalse(rendered.contains(address))
                assertFalse(rendered.contains(generated))
                val suffix = InstrumentationRegistry.getArguments().getString("visualSuffix") ?: "phone"
                capture(view, "visual-widget-english-$suffix.png", 280, 360)
                // Changing options uses the system broadcast and the provider's asynchronous local read.
                displayStore.save(DisplayPreferences(theme = ThemeChoice.DARK, language = LanguageChoice.CHINESE))
                manager.updateAppWidgetOptions(id, Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280); putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 360) })
                await { onMain { texts(view).contains("今日") } }
                capture(view, "visual-widget-chinese-$suffix.png", 280, 360)
                store.clear(); HubWidget.updateAll(context)
                await { onMain { texts(view).any { it.contains("先在应用中连接") } } }
                assertFalse(onMain { texts(view).contains("9223372036.9B") })
                assertTrue("Widget update unexpectedly made a network connection", runCatching { server.accept().close() }.exceptionOrNull() is java.net.SocketTimeoutException)
            }
        } finally { store.clear(); displayStore.save(original); server.close() }
    }
    @Test fun corruptCacheAndMissingTodayRemainUnknownWhileZeroIsPreserved() {
        val store = HubStore(context, "widget-fixture.bin")
        val localized = HubWidget.localizedContext(context, LanguageChoice.ENGLISH)
        val prefs = DisplayPreferences(language = LanguageChoice.ENGLISH)
        fun rendered(cache: WidgetCache) = onMain { texts(HubWidget.render(localized, prefs, cache, 0).apply(context, null)) }
        try {
            val generated = UUID.randomUUID().toString()
            fun save(body: String) = store.save(SavedHub("https://example.invalid", generated, body, 0))
            save("""{"periods":{"allTime":{"totalTokens":123}}}""")
            assertTrue(rendered(HubWidget.load(store)).contains("—"))
            assertFalse(rendered(HubWidget.load(store)).contains("123"))
            save("""{"periods":{"today":{"totalTokens":0,"costUsd":0}}}""")
            assertTrue(rendered(HubWidget.load(store)).contains("0"))
            assertTrue(rendered(HubWidget.load(store)).contains("$0.00"))
            val file = File(context.noBackupFilesDir, "widget-fixture.bin")
            val bytes = file.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); file.writeBytes(bytes)
            assertTrue(rendered(HubWidget.load(store)).any { it.contains("could not be read") })
        } finally { store.clear() }
    }
    @Test fun separateStoreInstancesSerializeAtomicReadsAndWrites() {
        val name = "widget-concurrent-fixture.bin"
        val writer = HubStore(context, name)
        val reader = HubStore(context, name)
        val generated = UUID.randomUUID().toString()
        val failures = java.util.concurrent.atomic.AtomicInteger()
        try {
            writer.save(SavedHub("https://example.invalid", generated, """{"periods":{}}""", 0))
            val writes = thread { repeat(20) { number ->
                if (runCatching { writer.save(SavedHub("https://example.invalid", generated, """{"periods":{"today":{"totalTokens":$number}}}""", number.toLong())) }.isFailure) failures.incrementAndGet()
            } }
            val reads = thread { repeat(30) { if (runCatching { requireNotNull(reader.load()) }.isFailure) failures.incrementAndGet() } }
            writes.join(10000); reads.join(10000)
            assertFalse(writes.isAlive); assertFalse(reads.isAlive)
            assertEquals(0, failures.get())
            assertEquals(19L, reader.load()?.savedAt)
        } finally { writer.clear() }
    }

    @Test fun appSaveLanguageChangeAndForgetPublishToActualHostWithoutBackgroundRequests() {
        val store = HubStore(context)
        val displayStore = DisplayPreferenceStore(context)
        assertNull("Widget integration requires an empty app profile", store.load())
        val original = displayStore.load()
        val owner = ViewModelStore()
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val body = """{"periods":{"today":{"totalTokens":1234567,"costUsd":0}}}"""
        val client = HubViewModel.defaultClient().newBuilder().addInterceptor { chain ->
            calls.incrementAndGet()
            val route = chain.request().url.encodedPath
            val stream = route.endsWith("/stream")
            val text = if (route.endsWith("/health")) """{"ok":true,"role":"hub"}""" else body
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if (stream) 403 else 200)
                .message("Synthetic response").header("Content-Type", "application/json").body(text.toResponseBody()).build()
        }.build()
        lateinit var model: HubViewModel
        try {
            displayStore.save(DisplayPreferences(language = LanguageChoice.CHINESE))
            withHost { view, _, _ ->
                onMain { model = HubViewModel(context.applicationContext as Application, store, client); owner.put("widget", model) }
                await { !model.state.value.loading }
                onMain { model.updateNetworkScope(fixtureLocalScope()); model.setForeground(true); model.refresh("http://192.168.1.1:17321", UUID.randomUUID().toString()) }
                // No direct widget update here: saving via the real ViewModel must publish it.
                await { onMain { texts(view).contains("1.2M") } }
                onMain { model.setForeground(false) }
                val backgroundCalls = calls.get()
                onMain { model.changeDisplay(DisplayChange.Language(LanguageChoice.ENGLISH)) }
                await { onMain { texts(view).contains("Today") } }
                assertEquals(backgroundCalls, calls.get())
                onMain { model.forget() }
                await { onMain { texts(view).any { it.contains("Connect in the app") } } }
                assertFalse(onMain { texts(view).contains("1.2M") })
                assertEquals(backgroundCalls, calls.get())
            }
        } finally {
            onMain { owner.clear() }
            client.dispatcher.cancelAll(); client.connectionPool.evictAll()
            store.clear(); displayStore.save(original)
        }
    }
}
