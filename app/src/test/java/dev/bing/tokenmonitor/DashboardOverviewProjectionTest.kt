package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.time.LocalDate

/** Concrete samples are independently executed by overview-original.cjs. */
class DashboardOverviewProjectionTest {
    private val today=LocalDate.of(2026,10,11)
    private fun value(tokens:Long)=HistoryAttribution(tokens,"0")
    private fun history(vararg rows:HistoryRow)=HubHistory(rows.toList(),emptyList())

    @Test fun dashboardOverviewAllRetainedTopFiveAndTwoDenominators() {
        val first=HistoryRow("2026-09-01",1000,"0",perClient=linkedMapOf("antigravity-cli" to value(300),"openai" to value(180),"omp" to value(60),"kilocode" to value(40),"unknown tool" to value(20),"zero" to value(0)),
            perModel=linkedMapOf("Z" to value(400),"A" to value(200),"B" to value(150),"E" to value(110),"F" to value(10)))
        val last=HistoryRow("2026-10-11",1000,"0",perClient=mapOf("antigravity" to value(200)),perModel=mapOf("G" to value(200)))
        val summary=HistorySummary(12_345_678,"2500",400,9,10,3_570_000,1_200_000,"favorite<&\"",12_345)
        val model=DashboardOverviewProjection.project(HubHistory(listOf(last,first),emptyList(),summary),today)
        assertSame(summary,model.summary)
        assertEquals(BigInteger.valueOf(2000),model.retainedTokens)
        assertEquals(listOf("Z","A","G","B","E"),model.models.map {it.key})
        assertEquals(listOf("20.0","10.0","10.0","7.5","5.5"),model.models.map {it.percent})
        assertEquals(listOf(1f,.5f,.5f,.375f,.275f),model.models.map {it.barFraction})
        assertEquals(listOf("antigravity","openai","omp","kilocode","unknown tool"),model.tools.map {it.key})
        assertEquals(listOf("25.0","9.0","3.0","2.0","1.0"),model.tools.map {it.percent})
        assertEquals(listOf(1f,.36f,.12f,.08f,.04f),model.tools.map {it.barFraction})
        assertEquals("12.3M",DashboardOverviewProjection.count(summary.totalTokens,"western","en"))
        assertEquals("$2.5K",DashboardOverviewProjection.compactCost(summary.totalCost,null,UsageCurrency(),"western","en"))
    }
    @Test fun stableSourceEnumerationHandlesNumericKeysAndOnlySingleToolAlias() {
        val keys=linkedMapOf("z" to value(100),"alpha" to value(100),"10" to value(100),"2" to value(100),"beta" to value(100),"sixth" to value(100))
        val model=DashboardOverviewProjection.project(history(HistoryRow(today.toString(),1000,"0",perModel=keys,
            perClient=linkedMapOf("antigravity-cli" to value(20),"antigravity" to value(30),"omp" to value(60),"kilocode" to value(40)))),today)
        assertEquals(listOf("2","10","z","alpha","beta"),model.models.map {it.key})
        assertEquals(listOf("omp","antigravity","kilocode"),model.tools.map {it.key})
        assertEquals(BigInteger.valueOf(50),model.tools.single {it.key=="antigravity"}.tokens)
    }
    @Test fun dashboardOverviewLocalTodaySundayYearWithoutLiveOverlay() {
        val model=DashboardOverviewProjection.project(history(HistoryRow("2026-09-01",1000,"1")),today)
        assertEquals("2025-10-26",model.cells.first().date)
        assertEquals("2026-10-11",model.cells.last().date)
        assertEquals(351,model.cells.size);assertEquals(51,model.weeks)
        assertEquals(0,model.cells.first().weekday);assertEquals(0,model.cells.last().weekday)
        assertEquals(12,model.months.size)
        assertEquals(HomeMonthLabel("2025-11",0),model.months.first())
        assertEquals(HomeMonthLabel("2026-10",48),model.months.last())
        assertNull(model.cells.last().value)
        assertEquals(0,model.cells.last().costLevel)
    }
    @Test fun emptyMonthlyOnlyUnknownAndDuplicateHeatDaysRemainDistinct() {
        val monthly=HubHistory(emptyList(),listOf(HistoryRow("2026-10",10,"1")))
        val monthModel=DashboardOverviewProjection.project(monthly,today)
        assertEquals(351,monthModel.cells.size);assertTrue(monthModel.cells.all {it.value==null})
        assertTrue(monthModel.models.isEmpty());assertTrue(monthModel.tools.isEmpty());assertNull(monthModel.summary)
        val rows=listOf(HistoryRow(today.toString(),0,"0"),HistoryRow("2026-10-10",null,null),
            HistoryRow("2026-10-09",10,"1",perModel=mapOf("model" to value(10))),HistoryRow("2026-10-09",20,"2",perModel=mapOf("model" to value(20))))
        val model=DashboardOverviewProjection.project(HubHistory(rows,emptyList()),today)
        assertEquals(0L,model.cells.last().value?.tokens)
        assertNotNull(model.cells.single {it.date=="2026-10-10"}.value)
        assertNull(model.cells.single {it.date=="2026-10-10"}.value?.tokens)
        assertNull(model.cells.single {it.date=="2026-10-09"}.value)
        assertEquals(BigInteger.valueOf(30),model.models.single().tokens)
        // Native unknown protection for ambiguous heat days is explicit; raw source
        // ranking still includes all records, as documented by the original oracle.
    }
    @Test fun maximaUseRetainedDaysOutsideVisibleYearAndDoNotUseWireIntensities() {
        val model=DashboardOverviewProjection.project(history(HistoryRow("2024-01-01",1000,"100"),
            HistoryRow(today.toString(),250,"75")),today)
        assertEquals(2,model.cells.last().tokenLevel)
        assertEquals(4,model.cells.last().costLevel)
        assertFalse(model.cells.any {it.date=="2024-01-01"})
    }
    @Test fun dashboardOverviewHeatFitsSourceCellBoundsAndSelectsRawTinyCost() {
        val row=HistoryRow(today.toString(),1,"0.000125")
        val model=DashboardOverviewProjection.project(history(row),today)
        val unset=DashboardOverviewProjection.heatGeometry(51,0.0)
        assertEquals(14.0,unset.cell,0.0);assertEquals(914.0,unset.width,0.0)
        val small=DashboardOverviewProjection.heatGeometry(51,320.0)
        assertEquals(9.0,small.cell,0.0);assertEquals(659.0,small.width,0.0)
        val fit=DashboardOverviewProjection.heatGeometry(51,800.0)
        assertEquals(11.686274509803921,fit.cell,.0000001);assertEquals(796.0,fit.width,.0000001)
        val wide=DashboardOverviewProjection.heatGeometry(51,2000.0)
        assertEquals(22.0,wide.cell,0.0);assertEquals(1322.0,wide.width,0.0)
        val last=model.cells.last()
        val hit=DashboardOverviewProjection.hitCell(model,fit,last.column*fit.pitch+1,last.weekday*fit.pitch+1)
        assertSame(row,hit?.value);assertEquals("0.000125",hit?.value?.cost)
        assertNull(DashboardOverviewProjection.hitCell(model,fit,fit.cell+1,1.0))
        assertNull(DashboardOverviewProjection.hitCell(model,fit,Double.NaN,1.0))
        assertNull(DashboardOverviewProjection.hitCell(model,fit,last.column*fit.pitch+1,fit.pitch+1))
    }
    @Test fun summaryIsNeverReconciledToDailyAndUnknownCostIsNeverInvented() {
        val summary=HistorySummary(1,"0.125",null,null,null,null,null,null,null,12)
        val model=DashboardOverviewProjection.project(HubHistory(listOf(HistoryRow(today.toString(),10000,"10")),emptyList(),summary),today)
        assertSame(summary,model.summary);assertEquals(BigInteger.valueOf(10000),model.retainedTokens)
        assertEquals("—",DashboardOverviewProjection.count(null,"western","en"))
        assertEquals("—",DashboardOverviewProjection.duration(null))
        assertEquals("—",DashboardOverviewProjection.compactCost(null,null,UsageCurrency(),"western","en"))
        assertEquals("—",DashboardOverviewProjection.compactCost("0",12,UsageCurrency(),"western","en"))
        assertEquals("$0.0000",DashboardOverviewProjection.compactCost("0",0,UsageCurrency(),"western","en"))
        assertEquals("$0.1250",DashboardOverviewProjection.compactCost("0.125",12,UsageCurrency(),"western","en"))
    }
    @Test fun dashboardOverviewDurationHalfUpAndCompactMoneyConfiguredRates() {
        assertEquals("0m",DashboardOverviewProjection.duration(29_999))
        assertEquals("1m",DashboardOverviewProjection.duration(30_000))
        assertEquals("1h 0m",DashboardOverviewProjection.duration(3_570_000))
        assertEquals("$0.0001",DashboardOverviewProjection.compactCost("0.000125",null,UsageCurrency(),"western","en"))
        assertEquals("$1.2K",DashboardOverviewProjection.compactCost("1200",null,UsageCurrency(),"western","en"))
        assertEquals("$1200.00",DashboardOverviewProjection.compactCost("1200",null,UsageCurrency(),"localized","zh-CN"))
        assertEquals("¥1.36万",DashboardOverviewProjection.compactCost("2000",null,UsageCurrency("CNY"),"localized","zh-CN"))
        val custom=UsageCurrency("CNY",mapOf("CNY" to "7.25"))
        assertEquals("¥0.9063",DashboardOverviewProjection.compactCost("0.125",null,custom,"localized","zh-CN"))
        assertEquals("¥1.45万",DashboardOverviewProjection.compactCost("2000",null,custom,"localized","zh-CN"))
    }
    @Test fun dashboardOverviewCardWidthsTwoFourEightAndFractionalTotals() {
        assertEquals(listOf(159.9,159.8),DashboardOverviewProjection.statWidths(listOf(30.1,80.2),319.7))
        assertEquals(listOf(146.3,200.7,146.3,148.0),DashboardOverviewProjection.statWidths(listOf(40.2,190.7,70.1,90.9),641.3))
        assertEquals(listOf(90.4,197.2,90.4,99.1,90.4,132.9,90.4,206.5),DashboardOverviewProjection.statWidths(listOf(40.2,190.7,70.1,90.9,50.1,125.3,40.1,200.2),997.3))
        assertEquals(emptyList<Double>(),DashboardOverviewProjection.statWidths(emptyList(),100.0))
    }
    @Test fun preciseRankSumsExceedLongWithoutLosingSourceFields() {
        val row=HistoryRow(today.toString(),Long.MAX_VALUE,"0",perModel=mapOf("model" to value(Long.MAX_VALUE)))
        val model=DashboardOverviewProjection.project(history(row,row.copy(date="2026-10-10")),today)
        val exact=BigInteger.valueOf(Long.MAX_VALUE)*BigInteger.TWO
        assertEquals(exact,model.retainedTokens);assertEquals(exact,model.models.single().tokens)
        assertEquals("100.0",model.models.single().percent)
    }
}
