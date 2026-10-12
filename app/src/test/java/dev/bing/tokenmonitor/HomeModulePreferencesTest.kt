package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class HomeModulePreferencesTest {
    private val reference=Json.parseToJsonElement(javaClass.getResource("/home-order-parity.json")!!.readText()).jsonObject
    private fun JsonElement.strings()=jsonArray.map {it.jsonPrimitive.content}
    @Test fun originalDefaultsAnd1440OrderVisibilityActionsMatch() {
        val defaults=reference.getValue("defaults").jsonObject
        assertEquals(defaults.getValue("order").strings(),HomeModulePreferences.order(DisplayPreferences()))
        assertEquals(defaults.getValue("visible").strings(),DisplayPreferences().homeModules)
        val cases=reference.getValue("cases").jsonArray;assertEquals(1440,cases.size)
        cases.forEachIndexed {index,item->val r=item.jsonObject;val input=r.getValue("input").jsonObject
            val p=DisplayPreferences(homeModules=input.getValue("visible").strings(),homeModuleOrder=input.getValue("order").strings())
            val change=when(r.getValue("action").jsonPrimitive.content) {
                "toggle"->DisplayChange.HomeModuleVisibility(r.getValue("id").jsonPrimitive.content)
                "move"->DisplayChange.HomeModuleMove(r.getValue("id").jsonPrimitive.content,r.getValue("direction").jsonPrimitive.int)
                "reset"->DisplayChange.ResetHomeModuleOrder
                else->DisplayChange.ShowAllHomeModules
            }
            val changed=change.apply(p)
            assertEquals("Original full order case $index",r.getValue("expectedOrder").strings(),HomeModulePreferences.order(changed))
            assertEquals("Original visible membership case $index",r.getValue("expectedVisible").strings(),HomeModulePreferences.visible(changed))
        }
    }
    @Test fun legacyTrendSlotProjectsIntoCombinedActivityAndEditsPreserveUnrelatedPreferences() {
        val p=DisplayPreferences(homeModules=listOf("trends","tools","devices"),currency="CNY",appearance=AppearancePreferences(reduceMotion="off"),modelRanking="cost",main=MainDisplayPreferences(compactTotal=true))
        val order=HomeModulePreferences.order(p);assertEquals(listOf("activity","tools","devices","limits","models","sessions"),order)
        val hidden=DisplayChange.HomeModuleVisibility("activity").apply(p)
        assertEquals(order,hidden.homeModuleOrder);assertFalse("activity" in hidden.homeModules)
        val restored=DisplayChange.HomeModuleVisibility("activity").apply(hidden)
        assertEquals(listOf("activity","tools","devices"),restored.homeModules)
        assertEquals(p,restored.copy(homeModules=p.homeModules,homeModuleOrder=emptyList()))
        assertEquals(p,DisplayChange.HomeModuleMove("tools",0).apply(p));assertEquals(p,DisplayChange.HomeModuleVisibility("unknown").apply(p))
        assertFalse(HomeModulePreferences.valid(p.copy(homeModuleOrder=listOf("tools","tools"))))
    }
}
