package io.github.theminionooo.tokenmonitor

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.storage.DesktopPreferences
import io.github.theminionooo.tokenmonitor.data.storage.DesktopOptions
import org.junit.Assert.*
import org.junit.Test

class MaintenanceBoundaryTest {
    @Test fun hub2StoragePersistsThroughMalformedFieldRecovery() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".preview"))
        val prefs=context.getSharedPreferences("desktop_presentation",Context.MODE_PRIVATE)
        val previous=prefs.getString("options",null)
        try {
            val store=DesktopPreferences(context)
            val options=DesktopOptions(currency="CNY",usdRate=7.0,interfaceFont="system",displayFont="mono",pinnedTools=setOf("codex"),modelAliases=mapOf("m" to "n"),currencyRates=mapOf("CNY" to 7.0))
            assertTrue(store.save(options));assertEquals(options,DesktopPreferences(context).options.value)
            val encoded=prefs.getString("options","")!!.replace("\"interfaceFont\":\"system\"","\"interfaceFont\":[]")
            assertTrue(prefs.edit().putString("options",encoded).commit())
            val recovered=DesktopPreferences(context).options.value
            assertEquals(options.copy(interfaceFont="mono"),recovered)
            assertTrue(DesktopPreferences(context).save(recovered));assertEquals(recovered,DesktopPreferences(context).options.value)
        } finally { prefs.edit().putString("options",previous).commit();DesktopPreferences(context) }
    }
    @Test fun sourceAndProtocolVersionsRemainSeparate() {
        assertEquals("android-v0.68.0-r1",BuildConfig.ANDROID_BASE_TAG)
        assertEquals("v0.68.0",BuildConfig.UPSTREAM_TAG)
        assertEquals("0.68.0",BuildConfig.UPSTREAM_VERSION)
    }
}
