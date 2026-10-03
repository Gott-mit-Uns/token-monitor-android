package io.github.theminionooo.tokenmonitor

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.storage.DeviceAliasStore
import org.junit.Assert.*
import org.junit.Test

class DeviceAliasStoreTest {
    @Test fun aliasesPersistAndRemainIsolatedAndCanBeRemoved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".preview"))
        val prefs = context.getSharedPreferences("device_aliases", Context.MODE_PRIVATE)
        val old = prefs.all.mapValues { it.value as String }
        val first = DeviceAliasStore(context)
        val second = DeviceAliasStore(context)
        try {
            assertNull(first.save("https://synthetic.invalid", "one", " 家中 NAS "))
            assertTrue(prefs.all.values.none { it.toString().contains("https://") })
            assertTrue(prefs.all.keys.none { it.contains("synthetic.invalid") || it == "one" })
            assertNull(first.save("https://synthetic.invalid", "two", "家中 NAS"))
            assertEquals("家中 NAS", second.alias("https://synthetic.invalid:443/", "one"))
            assertEquals("家中 NAS", second.alias("https://synthetic.invalid", "two"))
            assertNull(second.alias("https://other.invalid", "one"))
            // Missing devices do not delete preferences; the same ID recovers its alias.
            assertEquals("家中 NAS", DeviceAliasStore(context).also { it.close() }.alias("https://synthetic.invalid", "one"))
            assertNull(first.save("https://synthetic.invalid", "one", ""))
            assertNull(second.alias("https://synthetic.invalid", "one"))
            assertEquals("家中 NAS", second.alias("https://synthetic.invalid", "two"))
        } finally {
            first.close(); second.close()
            val edit = prefs.edit().clear()
            old.forEach { (key, value) -> edit.putString(key, value) }
            check(edit.commit())
        }
    }
}
