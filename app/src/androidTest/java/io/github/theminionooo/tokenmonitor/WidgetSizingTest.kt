package io.github.theminionooo.tokenmonitor

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.util.SizeF
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.widget.*
import org.junit.Assert.*
import org.junit.Test

class WidgetSizingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun allNamedPresetsAreRegisteredAndDistinct() {
        val manager = AppWidgetManager.getInstance(context)
        val installed = manager.installedProviders.filter { it.provider.packageName == context.packageName }
        assertEquals(7, installed.size)
        usageWidgetProviders.forEach { provider ->
            assertTrue(installed.any { it.provider == ComponentName(context, provider) })
        }
        assertEquals(WidgetLayout.Compact, widgetPreset(CompactWidgetProvider::class.java.name))
        assertEquals(WidgetLayout.Wide, widgetPreset(WideWidgetProvider::class.java.name))
        assertEquals(WidgetLayout.Portrait, widgetPreset(PortraitWidgetProvider::class.java.name))
        assertEquals(WidgetLayout.Overview, widgetPreset(OverviewWidgetProvider::class.java.name))
        assertEquals(WidgetLayout.Large, widgetPreset(LargeWidgetProvider::class.java.name))
        assertNull(widgetPreset(UsageWidgetProvider::class.java.name))
    }

    @Test fun advertisedSizesPreserveSmallHostsAndOrientation() {
        val small = SizeF(180f, 90f)
        val wide = SizeF(320f, 176f)
        val options = Bundle().apply {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(small, wide, small, SizeF(0f, 20f)))
        }
        assertEquals(listOf(small, wide), widgetHostSizes(options))
    }

    @Test fun olderLauncherBoundsProduceExactPortraitAndLandscapeAllocations() {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 320)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 90)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 176)
        }
        assertEquals(listOf(SizeF(180f,176f), SizeF(320f,90f)), widgetHostSizes(options))
        assertTrue(widgetHostSizes(Bundle()).isEmpty())
    }
}
