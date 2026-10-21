package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class Desktop077Test {
    private val reference=Json.parseToJsonElement(javaClass.getResource("/desktop-077.json")!!.readText()).jsonObject
    @Test fun exactWholeRegistryMatchesImmutableDesktop077() {
        val expected=reference.getValue("registry").jsonObject.getValue("components").jsonObject.mapValues {(_,rows)->rows.jsonArray.associate {it.jsonObject.getValue("revision").jsonPrimitive.long to it.jsonObject.getValue("buildId").jsonPrimitive.content}}
        assertEquals(expected,HubBuildRegistry.entries)
    }
    @Test fun newApiAccountPeriodsMatchActualDesktopDomWithoutChangingOtherAdapters() {
        assertEquals(DesktopParityMetadata.sourceCommit,reference.getValue("sourceCommit").jsonPrimitive.content)
        reference.getValue("cases").jsonArray.forEachIndexed {index,raw->
            val case=raw.jsonObject;val provider=case.getValue("provider").jsonObject
            val actual=LimitProviderLayout.project(provider,locale=Locale.forLanguageTag(case.getValue("locale").jsonPrimitive.content))
            val expected=case.getValue("rows").jsonArray
            if(provider.getValue("provider").jsonPrimitive.content=="cursor") {
                // Android retains its established no-data explanation; neither surface invents a quota.
                assertTrue(expected.isEmpty());assertEquals(1,actual.size);assertNull(actual.single().percent)
                assertEquals(if(case.getValue("locale").jsonPrimitive.content=="en") "Allowance unavailable" else "额度数据未提供",actual.single().value)
                return@forEachIndexed
            }
            assertEquals("case $index count",expected.size,actual.size)
            expected.forEachIndexed {i,row->val r=row.jsonObject
                assertEquals("case $index label",r.getValue("label").jsonPrimitive.content,actual[i].label)
                assertEquals("case $index value",r.getValue("value").jsonPrimitive.content,actual[i].value)
                assertEquals("case $index details",r.getValue("details").jsonArray.map {cells->cells.jsonArray.map {it.jsonPrimitive.content}},actual[i].details)
            }
        }
    }
}
