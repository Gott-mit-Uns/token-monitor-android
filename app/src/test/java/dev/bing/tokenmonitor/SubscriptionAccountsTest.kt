package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SubscriptionAccountsTest {
    @Test fun originalDesktopKeyFamiliesWorkspaceDeduplicationBindingLadderAndAmbiguityMatch() {
        val data=javaClass.classLoader!!.getResourceAsStream("subscription-accounts-parity.json")!!.bufferedReader().use {Json.parseToJsonElement(it.readText()).jsonObject}
        data.getValue("cases").jsonArray.forEachIndexed {caseIndex,raw->
            val c=raw.jsonObject;val input=c.getValue("input").jsonArray.map {it.jsonObject};val deduped=SubscriptionAccounts.dedupe(input)
            assertEquals("case $caseIndex dedupe",c.getValue("deduped").jsonArray.map {it.jsonObject},deduped)
            c.getValue("pairs").jsonArray.forEach {pair->val p=pair.jsonObject;assertEquals("case $caseIndex pair",p.getValue("same").jsonPrimitive.boolean,SubscriptionAccounts.same(input[p.getValue("i").jsonPrimitive.int],input[p.getValue("j").jsonPrimitive.int]))}
            c.getValue("matches").jsonArray.forEach {row->
                val r=row.jsonObject;val record=r.getValue("record").jsonObject
                val parsed=SubscriptionParser.parse(buildJsonObject {put("subscriptions",buildJsonArray {add(record)})}.toString()).single()
                val found=SubscriptionAccounts.match(parsed,deduped)
                assertEquals("case $caseIndex binding",r.getValue("index").jsonPrimitive.intOrNull,found?.get("fixtureIndex")?.jsonPrimitive?.int)
                assertEquals("case $caseIndex warning",r.getValue("ambiguous").jsonPrimitive.boolean,SubscriptionAccounts.ambiguous(parsed,deduped))
                listOf(false,true).forEachIndexed {index,mask->assertEquals("case $caseIndex label",r.getValue("labels").jsonArray[index].jsonPrimitive.content,SubscriptionAccounts.label(parsed,found,mask))}
            }
        }
    }
    @Test fun parserRetainsOnlyTypedBindingAndAliasFieldsAndPresentationDoesNotRewriteRawRecords() {
        val body="""{"subscriptions":[{"provider":"codex","binding":{"profileName":" Public ","accountKey":" public-a ","accountEmail":"FIRST@example.test","ignored":{"value":"discard"}}}]}"""
        val parsed=SubscriptionParser.parse(body).single();assertEquals(SubscriptionBinding("Public","public-a","first@example.test"),parsed.binding)
        val snapshot=HubParser.parse("""{"periods":{},"limits":{"providers":[{"provider":"codex","accountKey":"public-new","webAccountKey":"public-web","accountKeyAliases":["public-a",{"value":"discard"}],"windows":[]}]}}""")
        val record=snapshot.limits.single().presentation.providerRecord!!;assertEquals(listOf("public-a"),record.getValue("accountKeyAliases").jsonArray.map {it.jsonPrimitive.content})
        assertEquals("public-web",record.getValue("webAccountKey").jsonPrimitive.content)
        assertNotNull(SubscriptionAccounts.match(parsed,SubscriptionAccounts.records(snapshot.limits)))
        assertEquals(snapshot.limits.single().presentation.providerRecord,record)
    }
}
