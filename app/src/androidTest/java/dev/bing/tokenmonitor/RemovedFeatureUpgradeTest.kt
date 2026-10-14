package dev.bing.tokenmonitor

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** A real system binding spans signed replacement; no removed implementation is linked. */
class RemovedFeatureUpgradeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val manager get() = AppWidgetManager.getInstance(context)
    private val receipt get() = File(context.noBackupFilesDir, "test-removed-feature-widget-ids.txt")
    private val components = listOf("dev.bing.tokenmonitor.HubWidgetProvider", "dev.bing.tokenmonitor.HubQuotaWidgetProvider")
    private val hostId = 20640064

    @Test fun seedLegacyWidgetBindingsBeforeUpgrade() {
        assertEquals("0.2.63", context.packageManager.getPackageInfo(context.packageName, 0).versionName)
        assertFalse("Do not overwrite an earlier upgrade receipt", receipt.exists())
        val host = AppWidgetHost(context, hostId)
        val ids = mutableListOf<Int>()
        try {
            instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
            components.forEach { className ->
                val id = host.allocateAppWidgetId()
                ids += id
                val component = ComponentName(context.packageName, className)
                assertTrue("Old provider must be genuinely bound by the system", manager.bindAppWidgetIdIfAllowed(id, component))
                assertEquals(component, manager.getAppWidgetInfo(id)?.provider)
            }
            receipt.writeText(ids.joinToString("\n"))
            println("REMOVED_FEATURE_UPGRADE_SEEDED ${ids.joinToString(",")}")
        } catch (failure: Throwable) {
            ids.forEach(host::deleteAppWidgetId)
            throw failure
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test fun replacementWithdrawsBothOldProvidersFromExistingBindings() {
        assertEquals("0.2.64", context.packageManager.getPackageInfo(context.packageName, 0).versionName)
        assertTrue("Upgrade must retain the prior process receipt", receipt.isFile)
        val ids = receipt.readLines().map(String::toInt)
        assertEquals(2, ids.size)
        val deadline = SystemClock.elapsedRealtime() + 8000
        while (SystemClock.elapsedRealtime() < deadline && ids.any { manager.getAppWidgetInfo(it) != null }) SystemClock.sleep(100)
        assertFalse("Installed provider inventory must withdraw this app", manager.installedProviders.any { it.provider.packageName == context.packageName })
        ids.forEach { assertNull("Old binding must have no executable provider after replacement: $it", manager.getAppWidgetInfo(it)) }
        println("REMOVED_FEATURE_UPGRADE_WITHDRAWN ${ids.joinToString(",")}")
        AppWidgetHost(context, hostId).deleteHost()
        receipt.delete()
    }
}
