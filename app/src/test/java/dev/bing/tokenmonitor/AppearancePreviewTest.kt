package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AppearancePreviewTest {
    private fun JsonElement.map()=jsonObject.mapValues {it.value.jsonPrimitive.content}
    @Test fun actualSourcePreviewsNormalizeAndMergeWithoutChangingSavedAppearance() {
        val cases=Json.parseToJsonElement(javaClass.getResource("/appearance-preview-parity.json")!!.readText()).jsonObject.getValue("cases").jsonArray
        assertEquals(126,cases.size)
        cases.forEachIndexed {index,item->val row=item.jsonObject;val base=row.getValue("base").jsonObject
            val saved=AppearancePreferences(colors=base.getValue("colors").map(),vendorColors=base.getValue("vendors").map(),textSize="largest",interfaceFont="serif",reduceMotion="off")
            val controller=AppearancePreviewState();val vendor=row.getValue("vendor").jsonPrimitive.boolean
            controller.show((if(vendor) "vendor-color-" else "appearance-color-")+row.getValue("key").jsonPrimitive.content,row.getValue("input").jsonPrimitive.content,Any())
            val rendered=controller.rendered(saved);val expected=row.getValue("expected")
            if(expected==JsonNull) assertEquals("Source case $index",saved,rendered)
            else if(vendor) assertEquals("Source case $index",saved.copy(vendorColors=expected.map()),rendered)
            else assertEquals("Source case $index",saved.copy(colors=expected.map()),rendered)
            assertEquals(base.getValue("colors").map(),saved.colors);assertEquals(base.getValue("vendors").map(),saved.vendorColors)
            controller.clear();assertEquals(saved,controller.rendered(saved))
        }
    }
    @Test fun invalidDraftAndDisposedOldSessionDoNotEraseTheActivePreview() {
        val saved=AppearancePreferences(colors=mapOf("bg" to "#303438"));val controller=AppearancePreviewState();val first=Any();val second=Any()
        controller.show("appearance-color-bg","#ffffff",first)
        controller.show("appearance-color-bg","#invalid",first);assertEquals("#ffffff",controller.rendered(saved).colors["bg"])
        controller.show("vendor-color-codex","#12ab34",second);controller.clear(first)
        assertEquals("#12ab34",controller.rendered(saved).vendorColors["codex"]);assertEquals(saved.colors,controller.rendered(saved).colors)
        controller.show("vendor-color-no-such-vendor","#112233",first);controller.show("unknown","#112233",first)
        assertEquals("#12ab34",controller.rendered(saved).vendorColors["codex"])
        controller.clear(second);assertEquals(saved,controller.rendered(saved));assertNull(controller.value)
    }
}
