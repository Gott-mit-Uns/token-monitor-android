package dev.bing.tokenmonitor

import android.accessibilityservice.AccessibilityServiceInfo
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Real default HOME host only. No BIND_APPWIDGET, test AppWidgetHost or synthetic host options. */
@SdkSuppress(minSdkVersion = 29)
class LauncherWidgetLifecycleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation = instrumentation.uiAutomation
    private val manager = AppWidgetManager.getInstance(context)
    private val usageProvider = ComponentName(context, HubWidgetProvider::class.java)
    private val quotaProvider = ComponentName(context, HubQuotaWidgetProvider::class.java)
    private val events = mutableListOf<String>()
    private val owned = mutableMapOf<Int, Boolean>()
    private lateinit var launcherPackage: String
    private val cache = HubStore(context)
    private val configurations = WidgetConfigurationStore(context)
    private val output get() = requireNotNull(context.getExternalFilesDir(null))
    private fun record(message: String) { events += message }
    private fun await(label: String, seconds: Long = 15, condition: () -> Boolean) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        while (System.nanoTime() < until) {
            if (condition()) return
            Thread.sleep(100)
        }
        saveScreen("launcher-failure")
        throw AssertionError("Real launcher condition unavailable: $label")
    }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val root = automation.rootInActiveWindow ?: return emptyList()
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            if (result.size >= 400) return
            result += node
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        visit(root)
        return result
    }
    private fun exactText(node: AccessibilityNodeInfo, labels: Set<String>) =
        node.text?.toString()?.trim()?.lowercase() in labels || node.contentDescription?.toString()?.trim()?.lowercase() in labels
    private fun click(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(8) {
            val item = current ?: return false
            if (item.isEnabled && item.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK } && item.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            current = item.parent
        }
        return false
    }
    private fun saveScreen(name: String) {
        automation.takeScreenshot()?.let { bitmap ->
            File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun home() {
        assertTrue("System HOME action unavailable", automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME))
        await("default HOME package $launcherPackage") { automation.rootInActiveWindow?.packageName?.toString() == launcherPackage }
    }
    private fun widgetNode(quota: Boolean): AccessibilityNodeInfo? {
        val entry = if (quota) "quota_widget_title" else "widget_title"
        return nodes().firstOrNull { it.viewIdResourceName == "${context.packageName}:id/$entry" && it.isVisibleToUser }
    }
    private fun scrollWorkspace(forward: Boolean): Boolean {
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return nodes().firstOrNull { it.viewIdResourceName?.endsWith(":id/workspace") == true && it.actionList.any { item -> item.id == action } }
            ?.performAction(action) == true
    }
    private fun showWidget(quota: Boolean): AccessibilityNodeInfo {
        home()
        widgetNode(quota)?.let { return it }
        // Navigate actual accessible workspace pages; never use a guessed swipe coordinate.
        repeat(8) { if (!scrollWorkspace(false)) return@repeat; Thread.sleep(200); widgetNode(quota)?.let { return it } }
        repeat(12) { if (!scrollWorkspace(true)) return@repeat; Thread.sleep(200); widgetNode(quota)?.let { return it } }
        saveScreen("launcher-widget-not-visible")
        throw AssertionError("Pinned widget is not exposed on real HOME pages; workspace scrolling or RemoteViews accessibility may be unsupported")
    }
    private fun options(id: Int): String {
        val value = manager.getAppWidgetOptions(id)
        return listOf(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
            AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
            AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY).joinToString { "$it=${value.getInt(it, -1)}" }
    }
    private fun assertRealHostOptions(id: Int) {
        val value = manager.getAppWidgetOptions(id)
        assertTrue("Launcher must provide its actual widget width", value.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) > 0)
        assertTrue("Launcher must provide its actual widget height", value.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) > 0)
        assertEquals(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, value.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN))
        record("host-options id=$id ${options(id)}")
    }
    private fun pin(scenario: ActivityScenario<MainActivity>, quota: Boolean): Int {
        val provider = if (quota) quotaProvider else usageProvider
        val before = manager.getAppWidgetIds(provider).toSet()
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        await("foreground app before system pin request") { automation.rootInActiveWindow?.packageName?.toString() == context.packageName }
        scenario.onActivity { activity -> assertTrue("Current HOME host refused requestPinAppWidget", AppWidgetManager.getInstance(activity).requestPinAppWidget(provider, null, null)) }
        val addLabels = setOf("add", "add automatically", "add widget", "添加", "自动添加", "添加小组件", "添加到主屏幕")
        await("Launcher Add confirmation") { nodes().any { exactText(it, addLabels) && it.isVisibleToUser } }
        saveScreen(if (quota) "launcher-quota-add-confirmation" else "launcher-usage-add-confirmation")
        val add = nodes().first { exactText(it, addLabels) && it.isVisibleToUser }
        assertTrue("Launcher Add confirmation is not actionable", click(add))
        await("system-owned provider instance after Add") { manager.getAppWidgetIds(provider).toSet().minus(before).isNotEmpty() }
        val added = manager.getAppWidgetIds(provider).toSet().minus(before)
        assertEquals("One pin request creates exactly one instance", 1, added.size)
        val id = added.single()
        owned[id] = quota
        assertEquals(provider, manager.getAppWidgetInfo(id)?.provider)
        showWidget(quota)
        await("Launcher laid out its own pinned instance") {
            val value = manager.getAppWidgetOptions(id)
            value.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) > 0 && value.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) > 0
        }
        assertRealHostOptions(id)
        record("system-pin accepted provider=${provider.className} id=$id actual-home=$launcherPackage")
        saveScreen(if (quota) "launcher-quota-pinned" else "launcher-usage-pinned")
        return id
    }
    private fun launcherAction(node: AccessibilityNodeInfo, labels: Set<String>): Pair<AccessibilityNodeInfo, Int>? {
        var current: AccessibilityNodeInfo? = node
        repeat(10) {
            val item = current ?: return null
            item.actionList.firstOrNull { it.label?.toString()?.trim()?.lowercase() in labels }?.let { return item to it.id }
            current = item.parent
        }
        return null
    }
    private fun tryRealResize(id: Int) {
        val node = showWidget(false)
        val action = launcherAction(node, setOf("resize", "调整大小", "调整尺寸"))
        if (action == null) {
            record("resize NOT VERIFIED: real HOME exposes no accessible Resize action; actual options are recorded, no synthetic updateAppWidgetOptions used")
            return
        }
        val before = options(id)
        assertTrue("Launcher Resize action failed", action.first.performAction(action.second))
        val changes = setOf("increase width", "decrease width", "increase height", "decrease height", "增加宽度", "减少宽度", "增加高度", "减少高度")
        var resizeControl: AccessibilityNodeInfo? = null
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (System.nanoTime() < until && resizeControl == null) {
            resizeControl = nodes().firstOrNull { it.isVisibleToUser && exactText(it, changes) }
            if (resizeControl == null) Thread.sleep(100)
        }
        if (resizeControl == null) {
            saveScreen("launcher-resize-frame-unverified")
            record("resize NOT VERIFIED: Resize entered but no accessible size-change control; no guessed handle coordinates used")
            automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            return
        }
        assertTrue("Real resize control failed", click(resizeControl))
        await("real host changed its options") { options(id) != before }
        record("resize VERIFIED id=$id before=$before after=${options(id)}")
        showWidget(false)
        saveScreen("launcher-usage-resized")
    }
    private fun clickWidgetAndAssertPage(quota: Boolean) {
        showWidget(quota)
        val name = if (quota) "quota_widget_open" else "widget_open"
        val open = nodes().firstOrNull { it.viewIdResourceName == "${context.packageName}:id/$name" && it.isVisibleToUser }
            ?: throw AssertionError("Real HOME does not expose the widget open control")
        assertTrue("Actual widget click failed", click(open))
        val title = if (quota) "limits," else "models,"
        await("widget opens its correct selected app page") { nodes().any { it.isSelected && it.contentDescription?.toString()?.lowercase()?.startsWith(title) == true } }
        if (!quota) assertTrue("Configured month stays the natural month", nodes().any { it.isSelected && it.contentDescription?.toString() == "MONTH" })
        record("actual-launcher-click page=${if(quota) "limits" else "models"} period=${if(quota) "account-owned" else "month"}")
        saveScreen(if (quota) "launcher-quota-opened-limits" else "launcher-usage-opened-models-month")
    }
    private fun removeOwned(id: Int, quota: Boolean) {
        if (id !in owned || manager.getAppWidgetInfo(id) == null) return
        val node = showWidget(quota)
        val action = launcherAction(node, setOf("remove", "remove widget", "移除", "移除小组件", "删除"))
            ?: throw AssertionError("Real HOME has no accessible Remove action for the owned widget; refusing to clear an unrelated host or guess drag coordinates")
        assertTrue("Launcher Remove action failed", action.first.performAction(action.second))
        await("Launcher removed only requested provider id") { manager.getAppWidgetInfo(id) == null }
        val metadata = context.getSharedPreferences("widget-instance-v1", Context.MODE_PRIVATE)
        await("provider onDeleted removed only this instance metadata") { metadata.all.keys.none { it.startsWith("$id.") } }
        record("actual-launcher-remove id=$id metadata-cleared=true")
        owned.remove(id)
    }
    private fun writeReceipt() {
        fun hash(path: String) = File(path).inputStream().use { stream ->
            val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(8192)
            while (true) { val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        @Suppress("DEPRECATION") val info = context.packageManager.getPackageInfo(context.packageName, 0)
        File(output, "launcher-lifecycle-receipt.txt").writeText("version=${info.versionName}/${info.longVersionCode}\napkSHA256=${hash(context.packageCodePath)}\ntestAPKsha256=${hash(instrumentation.context.packageCodePath)}\n" + events.joinToString("\n") + "\nremaining-owned=${owned.keys.joinToString()}\n")
    }
    @Test fun actualLauncherPinsRendersOpensAndDeletesOwnInstances() {
        assertTrue("Launcher lifecycle test is restricted to the task emulator", Build.HARDWARE in setOf("ranchu", "goldfish") || Build.MODEL.startsWith("sdk_"))
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        launcherPackage = requireNotNull(context.packageManager.resolveActivity(homeIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName) { "No resolvable HOME launcher" }
        record("HOME=$launcherPackage emulator=${Build.MODEL}")
        assertTrue("Default HOME does not support requestPinAppWidget; actual pin acceptance is blocked", manager.isRequestPinAppWidgetSupported)
        assertTrue("Dedicated launcher test found preexisting usage instances; refusing to risk removing unowned widgets", manager.getAppWidgetIds(usageProvider).isEmpty())
        assertTrue("Dedicated launcher test found preexisting quota instances; refusing to risk removing unowned widgets", manager.getAppWidgetIds(quotaProvider).isEmpty())
        assertNull("Dedicated launcher test requires empty Hub profile; no existing private cache is overwritten", cache.load())
        val preferences = DisplayPreferenceStore(context)
        val original = preferences.load()
        val previousFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS }
        val now = Instant.now()
        val body = """{"updatedAt":"$now","periods":{"today":{"totalTokens":27800000,"costUsd":14.86,"models":{"GPT-5.6":20900000}},"month":{"totalTokens":61200000,"costUsd":237.42,"models":{"GPT-5.6":50000000}},"allTime":{"totalTokens":180000000,"costUsd":620.15}},"devices":[],"limits":{"providers":[{"provider":"codex","status":"ok","updatedAt":"$now","windows":[{"kind":"session","remainingPercent":70},{"kind":"weekly","remainingPercent":68}]}]}}"""
        val keepQuota = InstrumentationRegistry.getArguments().getString("launcherKeepQuota") == "true"
        var succeeded = false
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            cache.save(SavedHub("https://launcher-fixture.example.invalid", UUID.randomUUID().toString(), body, now.toEpochMilli()))
            preferences.save(DisplayPreferences(theme = ThemeChoice.LIGHT, language = LanguageChoice.ENGLISH, appearance = AppearancePreferences(reduceMotion = "on"), monthMode = "last30"))
            val activeScenario = ActivityScenario.launch(MainActivity::class.java)
            scenario = activeScenario
            val usageId = pin(activeScenario, false)
            configurations.save(usageId, WidgetConfiguration(mode = "models", period = "month"))
            HubWidget.update(context, manager, intArrayOf(usageId))
            await("real HOME rendered per-instance models configuration") { widgetNode(false)?.text?.toString() == "Models" }
            assertTrue(nodes().any { it.text?.toString() == "This month" })
            saveScreen("launcher-usage-configured-models-month")
            tryRealResize(usageId)
            clickWidgetAndAssertPage(false)
            val quotaId = pin(activeScenario, true)
            val account = widgetQuotaAccountId(LimitDisplay.groups(HubParser.parse(body).limits).single())
            val quotaConfiguration = WidgetConfiguration(mode = "quota", quotaAutomatic = false, quotaAccounts = listOf(account))
            configurations.save(quotaId, quotaConfiguration)
            HubQuotaWidget.update(context, manager, intArrayOf(quotaId))
            showWidget(true)
            await("real HOME rendered quota values") { nodes().any { it.text?.toString()?.contains("70%") == true || it.contentDescription?.toString()?.contains("70%") == true } }
            assertEquals(2, manager.getAppWidgetIds(usageProvider).size + manager.getAppWidgetIds(quotaProvider).size)
            saveScreen("launcher-two-instances-quota-rendered")
            clickWidgetAndAssertPage(true)
            removeOwned(usageId, false)
            assertEquals(quotaProvider, manager.getAppWidgetInfo(quotaId)?.provider)
            assertEquals(quotaConfiguration, configurations.load(quotaId, true))
            showWidget(true)
            saveScreen("launcher-quota-survives-usage-delete")
            if (keepQuota) record("retained quota id=$quotaId and synthetic cache/preferences for root process-kill/reboot acceptance; cleanup must remove this owned widget in HOME")
            else removeOwned(quotaId, true)
            succeeded = true
        } catch (failure: Throwable) {
            record("FIRST FAILURE ${failure.javaClass.simpleName}: ${failure.message}")
            saveScreen("launcher-first-failure")
            throw failure
        } finally {
            if (!(succeeded && keepQuota)) {
                owned.toMap().forEach { (id, quota) -> runCatching { removeOwned(id, quota) }.onFailure { record("cleanup INCOMPLETE owned-id=$id reason=${it.message}") } }
                cache.clear(); preferences.save(original)
            }
            scenario?.close()
            automation.serviceInfo = automation.serviceInfo.apply { flags = previousFlags }
            writeReceipt()
        }
    }
}
