package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HomeChartsTest {
    @Test fun desktopOracleMatchesLiveRowsCalendarLevelsRangesPeakAndCurve() {
        val fixture = javaClass.classLoader!!.getResourceAsStream("home-chart-parity.json")!!.bufferedReader().use { it.readText() }
        for (raw in Json.parseToJsonElement(fixture).jsonArray) {
            val row = raw.jsonObject; val name = row.getValue("name").jsonPrimitive.content
            fun history(key: String) = row[key]?.takeUnless { it == JsonNull }?.let { HistoryParser.parse(it.toString()) }
            val full = history("full"); val preview = history("preview")
            val source = HomeCharts.pickHistory(full, preview)!!
            val live = HubParser.parse(buildJsonObject { put("periods", buildJsonObject { put("today",row.getValue("live")) }) }.toString()).periods.getValue("today")
            val today = Instant.parse(row.getValue("now").jsonPrimitive.content).atZone(ZoneId.of(row.getValue("zone").jsonPrimitive.content)).toLocalDate()
            assertEquals(name, row.getValue("today").jsonPrimitive.content, today.toString())
            val result = HomeCharts.project(source, live, today, row.getValue("window").jsonPrimitive.content, preview?.summary?.activeDays)
            val expected = row.getValue("expected").jsonObject
            fun rows(key: String) = HistoryParser.parse(buildJsonObject { put("daily",expected.getValue(key)) }.toString()).daily.sortedBy { it.date }
            assertEquals(name,rows("daily"),result.history.daily.sortedBy { it.date })
            assertEquals(name,rows("trend"),result.trend)
            assertEquals(name,expected.getValue("peak").jsonPrimitive.longOrNull,result.peakTokens)
            assertEquals(name,expected.getValue("activeDays").jsonPrimitive.long,result.activeDays)
            assertEquals(name,expected.getValue("months").jsonArray.map { it.jsonObject.let { month ->
                HomeMonthLabel(month.getValue("label").jsonPrimitive.content,month.getValue("col").jsonPrimitive.int)
            } },result.months)
            val wantedCells=expected.getValue("cells").jsonArray
            assertEquals(name,wantedCells.size,result.cells.size)
            wantedCells.zip(result.cells).forEach { (wanted,cell) ->
                val wanted=wanted.jsonObject
                assertEquals(name,wanted.getValue("date").jsonPrimitive.content,cell.date)
                assertEquals(name,wanted.getValue("col").jsonPrimitive.int,cell.column)
                assertEquals(name,wanted.getValue("row").jsonPrimitive.int,cell.weekday)
                assertEquals(name,wanted.getValue("tokenLevel").jsonPrimitive.int,cell.tokenLevel)
                assertEquals(name,wanted.getValue("costLevel").jsonPrimitive.int,cell.costLevel)
                assertEquals(name,wanted.getValue("tokens").jsonPrimitive.long,cell.value?.tokens ?: 0)
                assertEquals(name,wanted.getValue("cost").jsonPrimitive.double,cell.value?.cost?.toDouble() ?: 0.0,0.0000001)
            }
            val commands=HomeCharts.lineSegments(result.trend).flatten()
            val nativeNumbers=commands.flatMap { command ->
                listOfNotNull(command.control1,command.control2,command.point).flatMap { listOf(it.x,it.y) }
            }
            val sourceNumbers=Regex("-?\\d+(?:\\.\\d+)?").findAll(expected.getValue("line").jsonPrimitive.content).map { it.value.toDouble() }.toList()
            assertEquals(name,sourceNumbers.size,nativeNumbers.size)
            sourceNumbers.zip(nativeNumbers).forEach { (wanted,actual) -> assertEquals(name,wanted,actual,0.00001) }
        }
    }
    @Test fun dayTargetsUseExactDesktopGeometryAndKeepUnknownDistinctFromZero() {
        val history=HubHistory(listOf(HistoryRow("2026-10-01",0,"0"),HistoryRow("2026-10-02",null,null)),emptyList())
        val result=HomeCharts.project(history,null,LocalDate.of(2026,10,2))
        result.cells.forEach { cell -> assertEquals(cell,HomeCharts.hitCell(result,cell.column*12.0+4,cell.weekday*12.0+4)) }
        assertNull(HomeCharts.hitCell(result,9.0,4.0))
        assertNull(HomeCharts.hitCell(result,4.0,9.0))
        assertNull(HomeCharts.hitCell(result,4.0,81.0))
        assertNull(HomeCharts.hitCell(result,-1.0,4.0))
        assertNull(HomeCharts.hitCell(result,Double.NaN,4.0))
        assertNull(HomeCharts.hitCell(result,Double.POSITIVE_INFINITY,4.0))
        assertNull(HomeCharts.hitCell(result,(result.columns-1)*12.0+4,76.0)) // Saturday after Friday end.
        assertEquals(0L,result.cells.first { it.date=="2026-10-01" }.value!!.tokens)
        assertNull(result.cells.first { it.date=="2026-10-02" }.value!!.tokens)
        assertNull(result.cells.first().value)
        val live=HubParser.parse("""{"periods":{"today":{"totalTokens":10,"costUsd":0.2,"unpricedTokens":3}}}""").periods.getValue("today")
        val patched=HomeCharts.project(history,live,LocalDate.of(2026,10,2))
        assertEquals(3L,patched.cells.last().value!!.unpricedTokens)
        assertNull(history.daily.last().unpricedTokens)
    }
    @Test fun presentationCopyNeverMutatesCachedHistoryAndMissingValuesStayUnknown() {
        val history=HistoryParser.parse("""{"daily":[{"date":"2026-10-09","tokens":100,"cost":0.1,"perClient":{"codex":{"tokens":100,"cost":0.1}}}],"monthly":[]}""")
        val live=HubParser.parse("""{"periods":{"today":{"totalTokens":200}}}""").periods.getValue("today")
        val result=HomeCharts.project(history,live,LocalDate.of(2026,10,9))
        assertEquals(100L,history.daily.single().tokens)
        assertEquals("0.1",history.daily.single().cost)
        assertEquals(200L,result.history.daily.single().tokens)
        assertNull(result.history.daily.single().cost)
        assertEquals(history.daily.single().perClient,result.history.daily.single().perClient)
        assertNull(result.cells.first().value)
        assertEquals(0,result.cells.first().costLevel)
        assertEquals(1,HomeCharts.lineSegments(listOf(HistoryRow("2026-10-08",1,null),HistoryRow("2026-10-09",null,null))).size)
    }
    @Test fun statsPreviewIsParsedAndFreshnessDoesNotEraseIt() {
        val body="""{"periods":{},"historyPreview":{"daily":[{"date":"2026-10-09","tokens":100}],"monthly":[],"summary":{"activeDays":106}}}"""
        val snapshot=HubParser.parse(body)
        assertEquals(106L,snapshot.historyPreview?.summary?.activeDays)
        val merged=StreamMerge.apply(Json.parseToJsonElement(body).jsonObject,"freshness","""{"stats":{"updatedAt":"2026-10-09T10:00:00Z"}}""")!!
        assertEquals(snapshot.historyPreview,HubParser.parse(merged.toString()).historyPreview)
        assertSame(snapshot.historyPreview,HomeCharts.pickHistory(HubHistory(emptyList(),emptyList()),snapshot.historyPreview))
        val full=HubHistory(listOf(HistoryRow("2026-10-08",200,null)),emptyList())
        assertSame(full,HomeCharts.pickHistory(full,snapshot.historyPreview))
    }
}
