package io.github.theminionooo.tokenmonitor

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.storage.*
import io.github.theminionooo.tokenmonitor.localization.LanguagePreferences
import io.github.theminionooo.tokenmonitor.ui.formatMoney
import org.junit.Assert.*
import org.junit.Test

class RollbackPreferencesTest {
    @Test fun oldDesktopOptionsStayInactiveAndDisplayPreferencesSurvive() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".preview"))
        val old=context.getSharedPreferences("desktop_presentation",Context.MODE_PRIVATE)
        val existing=old.getString("options",null)
        val display=DisplayPreferences(context);val previous=display.options.value
        val language=context.getSharedPreferences("language_preferences",0).getString("language","system")!!
        try {
            val unused="""{"currency":"CNY","rate":7,"aliases":{"gpt-test":"merged"},"tools":["codex"],"interfaceFont":"system","pinnedTools":["codex"]}"""
            assertTrue(old.edit().putString("options",unused).commit())
            display.setIconScale(IconScale.Large);display.setTextScale(TextScale.Compact);display.setHomeChineseUnits(false);display.setCompactTokenTotal(true)
            LanguagePreferences.set(context,"zh-CN")
            val reloaded=DisplayPreferences(context).options.value
            assertEquals(IconScale.Large,reloaded.iconScale);assertEquals(TextScale.Compact,reloaded.textScale);assertFalse(reloaded.homeChineseUnits);assertTrue(reloaded.compactTokenTotal)
            assertEquals("$1.25",formatMoney(1.25))
            assertEquals(unused,old.getString("options",null))
            assertEquals("zh-CN",context.getSharedPreferences("language_preferences",0).getString("language",null))
            assertTrue(BuildConfig.VERSION_CODE >= 680104)
        } finally {
            old.edit().putString("options",existing).commit()
            display.setIconScale(previous.iconScale);display.setTextScale(previous.textScale);display.setHomeChineseUnits(previous.homeChineseUnits);display.setCompactTokenTotal(previous.compactTokenTotal)
            LanguagePreferences.set(context,language)
        }
    }
}
