package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SessionSettingsTest {
    @Test fun originalSessionTitleDefaultsAndContextFallbackMatchLocalPreferences() {
        val cases=Json.parseToJsonElement(javaClass.getResource("/session-settings-parity.json")!!.readText()).jsonObject.getValue("cases").jsonArray
        assertEquals(24,cases.size)
        cases.forEach {entry->val row=entry.jsonObject
            val enabled=row.getValue("titleValue")!=JsonPrimitive(false)
            val metric=row.getValue("metric").jsonPrimitive.content
            val expected=if(metric=="remaining") "remaining" else "used"
            assertEquals(enabled,row.getValue("enabled").jsonPrimitive.boolean)
            assertEquals(expected,row.getValue("selected").jsonPrimitive.content)
            assertTrue(row.getValue("unchanged").jsonPrimitive.boolean)
            val changed=DisplayChange.SessionTitles(!enabled).apply(DisplayPreferences())
            assertEquals(!enabled,changed.sessionTitlesEnabled)
            assertEquals(expected,DisplayChange.SessionContextMetric(expected).apply(changed).main.sessionContextMetric)
        }
    }
    @Test fun sessionVisibilityIsLocalAndExistingGlobalPrivacyAlwaysHidesTitles() {
        val original=DisplayPreferences(currency="CNY",main=MainDisplayPreferences(sessionContextMetric="remaining"))
        val hidden=DisplayChange.SessionTitles(false).apply(original)
        assertTrue(hidden.hideSessionTitles);assertFalse(hidden.hideTitles)
        assertEquals(original,DisplayChange.SessionTitles(true).apply(hidden))
        val global=DisplayChange.HideTitles(true).apply(original)
        assertTrue(global.hideSessionTitles)
        assertTrue(DisplayChange.SessionTitles(true).apply(global).hideSessionTitles)
        assertTrue(DisplayChange.HideTitles(false).apply(hidden).hideSessionTitles)
        assertEquals(original.copy(sessionTitlesEnabled=false),hidden)
    }
    @Test fun originalVisibleCountProtectsEachLastViewWithoutResettingHiddenChoices() {
        val cases=Json.parseToJsonElement(javaClass.getResource("/session-settings-parity.json")!!.readText()).jsonObject.getValue("guards").jsonArray
        assertEquals(MainDisplay.ids.size,cases.size)
        cases.forEach {entry->val row=entry.jsonObject;val id=row.getValue("id").jsonPrimitive.content
            val hidden=row.getValue("hidden").jsonArray.map {it.jsonPrimitive.content}.toSet()
            val prefs=DisplayPreferences(main=MainDisplayPreferences(hiddenViews=hidden))
            assertEquals(row.getValue("count").jsonPrimitive.int,MainDisplay.visible(prefs.main).size)
            assertEquals(prefs,DisplayChange.MainViewVisible(id,false).apply(prefs))
            val other=hidden.first();val shown=DisplayChange.MainViewVisible(other,true).apply(prefs)
            assertEquals(listOf(id,other).toSet(),MainDisplay.visible(shown.main).toSet())
            val closed=DisplayChange.MainViewVisible(id,false).apply(shown)
            assertEquals(listOf(other),MainDisplay.visible(closed.main))
            assertEquals(hidden-other+id,closed.main.hiddenViews)
        }
    }

}
