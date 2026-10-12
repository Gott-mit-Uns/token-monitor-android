package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DesktopMenuNavigationTest {
    @Test fun originalDisabledNavigationAndTypeaheadCasesMatchAndMainMenuKeepsWrapping() {
        val reference=Json.parseToJsonElement(javaClass.getResource("/menu-control-parity.json")!!.readText()).jsonObject
        val navigation=reference.getValue("navigation").jsonArray;assertEquals(132,navigation.size)
        navigation.forEachIndexed {index,row->val r=row.jsonObject
            val direction=when(r.getValue("direction").jsonPrimitive.content) {"-10"->"page-previous";"-1"->"previous";"1"->"next";"10"->"page-next";else->r.getValue("direction").jsonPrimitive.content}
            assertEquals("Original navigate $index",r.getValue("expected").jsonPrimitive.int,DesktopMenuNavigation.navigate(r.getValue("options").jsonArray.map {!it.jsonObject.getValue("disabled").jsonPrimitive.boolean},r.getValue("current").jsonPrimitive.int,direction))
        }
        val typing=reference.getValue("typing").jsonArray;assertEquals(154,typing.size)
        typing.forEachIndexed {index,row->val r=row.jsonObject;val options=r.getValue("options").jsonArray
            assertEquals("Original typeahead $index",r.getValue("expected").jsonPrimitive.int,DesktopMenuNavigation.typeahead(options.map {it.jsonObject.getValue("label").jsonPrimitive.content},options.map {!it.jsonObject.getValue("disabled").jsonPrimitive.boolean},r.getValue("current").jsonPrimitive.int,r.getValue("text").jsonPrimitive.content))
        }
        val periods=reference.getValue("period").jsonArray;assertEquals(24,periods.size)
        periods.forEachIndexed {index,row->val r=row.jsonObject
            val direction=when(r.getValue("key").jsonPrimitive.content) {"ArrowUp"->"previous";"ArrowDown"->"next";"Home"->"first";else->"last"}
            assertEquals("Original period $index",r.getValue("expected").jsonPrimitive.int,DesktopMenuNavigation.navigate(List(4){true},r.getValue("current").jsonPrimitive.int.coerceIn(0,3),direction,true))
        }
        assertEquals(0,DesktopMenuNavigation.navigate(listOf(true,false,true),2,"next",true))
        assertEquals(2,DesktopMenuNavigation.navigate(listOf(true,false,true),0,"previous",true))
    }
}
