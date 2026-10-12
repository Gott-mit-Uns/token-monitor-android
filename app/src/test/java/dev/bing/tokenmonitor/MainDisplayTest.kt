package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class MainDisplayTest {
    private fun data()=javaClass.classLoader!!.getResourceAsStream("main-display-parity.json")!!.bufferedReader().use {Json.parseToJsonElement(it.readText()).jsonObject}
    @Test fun catalogueComesFromDesktopAndLegacyShortcutsDoNotEnterNavigation() {
        val data=data()
        assertEquals(listOf("home","limits","tool","model","project","session","device","trends","status"),data.getValue("sourceIds").jsonArray.map {it.jsonPrimitive.content})
        assertEquals(MainDisplay.ids,data.getValue("supportedIds").jsonArray.map {it.jsonPrimitive.content})
        val legacy=MainDisplayPreferences(viewOrder=listOf("pricing","sessions","subscriptions","home"),hiddenViews=setOf("pricing","subscriptions","home"))
        assertTrue(MainDisplay.valid(legacy))
        assertEquals("sessions",MainDisplay.preferred(legacy,"pricing"))
        assertEquals(MainDisplay.ids.toSet()-"home",MainDisplay.visible(legacy).toSet())
        val allHidden=DisplayPreferences(main=legacy.copy(hiddenViews=MainDisplay.ids.toSet()))
        assertEquals(MainDisplay.ids.toSet(),MainDisplay.visible(allHidden.main).toSet())
        val hidden=DisplayChange.MainViewVisible("home",false).apply(allHidden)
        assertEquals(setOf("home"),hidden.main.hiddenViews)
        assertEquals(allHidden,DisplayChange.MainViewVisible("pricing",true).apply(allHidden))
    }
    @Test fun originalCompactAndLiveRateFormattingMatchesBothUnitSystemsAndLanguageThresholds() {
        val data=data();val errors=mutableListOf<String>()
        data.getValue("formats").jsonArray.forEachIndexed {i,raw->val c=raw.jsonObject;val locale=c.getValue("locale").jsonPrimitive.content;val units=c.getValue("units").jsonPrimitive.content
            try {
                assertEquals("compact $i",c.getValue("expected").jsonPrimitive.content,DesktopCompactTokens.format(c.getValue("value").jsonPrimitive.double,units,locale))
                assertEquals(c.getValue("threshold").jsonPrimitive.double,DesktopCompactTokens.threshold(units,locale),0.0)
            } catch(e:AssertionError) {errors+=e.message.orEmpty()}
        }
        data.getValue("liveFormats").jsonArray.forEachIndexed {i,raw->val c=raw.jsonObject
            try {assertEquals("live $i",c.getValue("expected").jsonPrimitive.content,DesktopCompactTokens.liveRate(c.getValue("value").jsonPrimitive.double,c.getValue("units").jsonPrimitive.content,Locale.forLanguageTag(c.getValue("locale").jsonPrimitive.content)))} catch(e:AssertionError) {errors+=e.message.orEmpty()}
        }
        assertTrue(errors.joinToString("\n"),errors.isEmpty())
    }
    @Test fun originalViewOrderHiddenGuardFallbackAndMovesMatchReadOnlyCatalogue() {
        data().getValue("viewCases").jsonArray.forEachIndexed {i,raw->val c=raw.jsonObject
            fun strings(key:String)=c.getValue(key).jsonArray.map {it.jsonPrimitive.content}
            val value=MainDisplayPreferences(viewOrder=strings("order"),hiddenViews=strings("hidden").toSet())
            assertEquals("order $i",strings("normalized"),MainDisplay.order(value.viewOrder))
            assertEquals("hide $i",c.getValue("hiddenNormalized").jsonPrimitive.content.split(',').filter {it.isNotEmpty()}.toSet(),MainDisplay.hidden(value.hiddenViews))
            assertEquals("visible $i",strings("visible"),MainDisplay.visible(value))
            assertEquals("preferred $i",c.getValue("preferred").jsonPrimitive.content,MainDisplay.preferred(value,"home"))
            assertEquals("move $i",c.getValue("move").jsonPrimitive.content.split(','),MainDisplay.move(value,"models",-1).viewOrder.ifEmpty {MainDisplay.order(value.viewOrder)})
        }
    }
    @Test fun originalDeviceMatchedCounterStreamsResetExpiryAndMissingModelAttributionMatch() {
        data().getValue("scenarios").jsonArray.forEachIndexed {scenario,raw->var now=0L;val tracker=LiveTokenRateTracker {now}
            raw.jsonObject.getValue("events").jsonArray.forEachIndexed {i,event->val c=event.jsonObject;now=c.getValue("at").jsonPrimitive.long
                fun entries(key:String)=(c[key] as? JsonArray)?.map {r->val row=r.jsonObject
                    RateEntry(row.getValue("id").jsonPrimitive.content,row.getValue("name").jsonPrimitive.content,(row["period"] as? JsonObject)?.let(ThroughputParser::parse))}
                entries("reset")?.let(tracker::reset) ?: entries("entries")?.let(tracker::observe)
                val expected=c["expected"] as? JsonObject;val actual=tracker.sample();val message="stream $scenario event $i"
                if(expected==null) assertNull(message,actual) else {
                    assertNotNull(message,actual);actual!!
                    assertEquals(message,expected.getValue("speed").jsonPrimitive.double,actual.speed,0.0)
                    assertEquals(message,expected.getValue("burn").jsonPrimitive.double,actual.burn,0.0)
                    assertEquals(message,expected.getValue("sampledAt").jsonPrimitive.long,actual.sampledAt)
                    assertEquals(message,expected.getValue("revision").jsonPrimitive.long,actual.revision)
                    assertEquals(message,expected.getValue("expiresAt").jsonPrimitive.long,actual.expiresAt)
                    assertEquals(message,expected.getValue("deviceCount").jsonPrimitive.int,actual.deviceCount)
                    assertEquals(message,expected.getValue("idle").jsonPrimitive.boolean,actual.idle)
                    val devices=expected.getValue("devices").jsonArray.map {element->val device=element.jsonObject
                        DeviceRate(device.getValue("id").jsonPrimitive.content,device.getValue("name").jsonPrimitive.content,device.getValue("models").jsonArray.map {modelRaw->val model=modelRaw.jsonObject;RateModel(model.getValue("model").jsonPrimitive.content,model.getValue("speed").jsonPrimitive.double,model.getValue("burn").jsonPrimitive.double)})}
                    assertEquals(message,devices,actual.devices)
                }
            }
        }
    }
    @Test fun hubPerformanceFieldsRemainIndependentOfTotalsAndOnlyPresentDeviceTodayCountersCanBeSelected() {
        val source="""{"periods":{"today":{"totalTokens":500,"timedTokens":200,"timedOutputTokens":80,"timedDurationMs":1000}},"devices":[{"deviceId":"a","hostname":"Public A","periods":{"today":{"totalTokens":100,"timedTokens":60,"timedOutputTokens":20,"timedDurationMs":500,"modelThroughput":{"public-model":{"timedTokens":60,"timedOutputTokens":20,"timedDurationMs":500}}}}},{"deviceId":"b","hostname":"Public B","stale":true,"periods":{"today":{"timedTokens":140,"timedOutputTokens":60,"timedDurationMs":500}}}]}"""
        val data=HubParser.parse(source);assertEquals(500L,data.periods.getValue("today").tokens)
        assertEquals(TimedUsage(200.0,80.0,1000.0),data.periods.getValue("today").throughput!!.counters)
        assertEquals(listOf("device:a"),LiveTokenRateTracker.entries(data).map {it.id})
        assertEquals(listOf("device:b"),LiveTokenRateTracker.entries(data,"b").map {it.id})
        assertTrue(LiveTokenRateTracker.entries(data,"missing").isEmpty())
        assertTrue(LiveTokenRateTracker.entries(data.copy(devices=emptyList())).isEmpty())
        assertNull(ThroughputParser.parse(Json.parseToJsonElement("""{"timedTokens":0,"timedOutputTokens":0,"timedDurationMs":1,"capabilities":{"throughput":false}}""").jsonObject))
        assertNull(ThroughputParser.parse(Json.parseToJsonElement("""{"timedTokens":0,"timedDurationMs":1}""").jsonObject))
    }
}
