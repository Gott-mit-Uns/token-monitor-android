package dev.bing.tokenmonitor

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class LauncherLabelTest {
    @Test fun installedApplicationAndEveryLauncherEntryUseTokenMonitor() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val pm=context.packageManager
        assertEquals("Token Monitor",context.applicationInfo.loadLabel(pm).toString())
        val launchers=pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName),0)
        assertTrue("Installed package must retain its launcher",launchers.isNotEmpty())
        launchers.forEach {assertEquals("Token Monitor",it.activityInfo.loadLabel(pm).toString())}
    }
}
