package dev.bing.tokenmonitor

import androidx.compose.animation.core.CubicBezierEasing
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor

internal data class DashboardRankingRow(val key:String,val tokens:BigInteger,val percent:String,val barFraction:Float)
internal data class DashboardHeatGeometry(val cell:Double,val gap:Double,val weeks:Int) {
    val pitch get()=cell+gap
    val width get()=if(weeks>0) weeks*pitch-gap else 0.0
    val height get()=7*pitch-gap
}

internal data class DashboardRankKey(val byModel:Boolean,val name:String)
internal data class DashboardOverviewFrame(
    val targets:Map<DashboardRankKey,Double> = emptyMap(),
    val previousWidths:Map<DashboardRankKey,Double> = emptyMap(),
    val rankProgress:Double = 1.0,
    val entryPrepared:Boolean = false,
    val heatPending:Boolean = false,
    val heatElapsedMs:Double? = null,
    val heatScale:Float = 1f,
    val running:Boolean = false
) {
    fun rankingFraction(key:DashboardRankKey,trackWidth:Double):Float {
        val target=targets[key] ?: return 0f
        if(entryPrepared) return 0f
        if(rankProgress>=1 || trackWidth<=0 || !trackWidth.isFinite()) return target.toFloat()
        val from=(previousWidths[key] ?: 0.0)/trackWidth
        return (from+(target-from)*rankProgress).coerceAtLeast(0.0).toFloat()
    }
    fun heatAlpha(column:Int,lastColumn:Int):Float {
        if(heatPending || entryPrepared) return 0f
        val elapsed=heatElapsedMs ?: return 1f
        val position=if(lastColumn>0) column.toDouble()/lastColumn else 0.0
        val progress=((elapsed/heatScale-position*440.0)/280.0).coerceIn(0.0,1.0)
        return if(progress>=1.0) 1f else heatEase.transform(progress.toFloat())
    }
    companion object {private val heatEase=CubicBezierEasing(.25f,.1f,.25f,1f)}
}

/** Original Dashboard overview motion, with geometry captured from the last Canvas paint. */
internal class DashboardOverviewMotion {
    private var hasEvent=false
    private var eventKey:Any?=null
    private var revision=0
    private var width=0.0
    private var metricCost=true
    private var rankStarted=0L
    private var rankScale=1f
    private var heatStarted:Long?=null
    private var heatScale=1f
    private var rankActive=false
    private var heatActive=false
    private var barrierFrames=0
    private var deferredEntry=false
    private val paintedWidths=mutableMapOf<DashboardRankKey,Double>()
    private val trackWidths=mutableMapOf<DashboardRankKey,Double>()
    var frame=DashboardOverviewFrame()
        private set
    val running get()=rankActive || heatActive || frame.heatPending
    fun needsFrame(refreshBusy:Boolean)=rankActive || heatActive || (frame.heatPending && !refreshBusy)

    fun recordPaintedWidth(key:DashboardRankKey,trackWidth:Double,fraction:Float) {
        if(trackWidth.isFinite() && trackWidth>=0 && fraction.isFinite() && fraction>=0) {
            paintedWidths[key]=trackWidth*fraction
            trackWidths[key]=trackWidth
        }
    }

    fun render(model:DashboardOverviewModel?,renderEventKey:Any?,motionRevision:Int,reason:DashboardMotionReason,
        width:Double,metricCost:Boolean,nowNanos:Long,animate:Boolean,visible:Boolean,durationScale:Float=1f):Boolean {
        if(model==null) {
            hasEvent=false;paintedWidths.clear();trackWidths.clear();deferredEntry=false
            rankActive=false;heatActive=false;heatStarted=null;barrierFrames=0
            frame=DashboardOverviewFrame()
            return true
        }
        val targets=linkedMapOf<DashboardRankKey,Double>().apply {
            model.models.forEach {put(DashboardRankKey(true,it.key),it.barFraction.toDouble())}
            model.tools.forEach {put(DashboardRankKey(false,it.key),it.barFraction.toDouble())}
        }
        val eventChanged=!hasEvent || eventKey!=renderEventKey || revision!=motionRevision || frame.targets!=targets
        val geometryChanged=this.width!=width || this.metricCost!=metricCost
        val entering=(if(!hasEvent || revision!=motionRevision) reason==DashboardMotionReason.ENTRY else false) || deferredEntry
        val enabled=animate && visible && durationScale.isFinite() && durationScale>0
        if(!eventChanged && !geometryChanged && !(deferredEntry && visible)) return false
        val continuing=frame.heatPending
        val effectiveReason=if(!hasEvent || revision!=motionRevision) reason else if(eventChanged) DashboardMotionReason.UPDATE else DashboardMotionReason.NONE
        rankStarted=nowNanos
        rankScale=durationScale.takeIf {it.isFinite() && it>0} ?: 1f
        rankActive=enabled && (eventChanged || entering) && (entering || effectiveReason!=DashboardMotionReason.NONE) && targets.isNotEmpty()
        val previous=if(entering) emptyMap() else paintedWidths.toMap()
        val startHeat=enabled && (entering || continuing)
        heatStarted=null;heatActive=false;barrierFrames=if(startHeat) 2 else 0
        deferredEntry=entering && !visible && durationScale.isFinite() && durationScale>0
        if(visible) deferredEntry=false
        this.eventKey=renderEventKey;revision=motionRevision;this.width=width;this.metricCost=metricCost;hasEvent=true
        paintedWidths.keys.retainAll(targets.keys)
        trackWidths.keys.retainAll(targets.keys)
        frame=DashboardOverviewFrame(targets=targets,previousWidths=previous,rankProgress=if(rankActive) 0.0 else 1.0,
            entryPrepared=deferredEntry,heatPending=startHeat,heatScale=durationScale,running=rankActive || startHeat)
        return true
    }

    /** Two source RAF barriers precede cell animations; refresh completion gates that schedule. */
    fun advance(nowNanos:Long,durationScale:Float=1f,refreshBusy:Boolean=false) {
        if(!running) return
        if(!durationScale.isFinite() || durationScale<=0) {settle();return}
        var pending=frame.heatPending
        if(pending && !refreshBusy) {
            barrierFrames--
            if(barrierFrames<=0) {pending=false;heatStarted=nowNanos;heatScale=durationScale;heatActive=true}
        }
        // A positive system-scale change affects the next segment. Existing
        // timelines retain their start scale instead of moving progress backwards.
        val rankT=((nowNanos-rankStarted).coerceAtLeast(0L).toDouble()/rankScale/800_000_000.0).coerceIn(0.0,1.0)
        val rankProgress=if(!rankActive || rankT>=1) 1.0 else rankEase.transform(rankT.toFloat()).toDouble()
        if(rankT>=1) rankActive=false
        val heatElapsed=heatStarted?.let {(nowNanos-it).coerceAtLeast(0L).toDouble()/1_000_000.0}
        if(heatElapsed!=null && heatElapsed/heatScale>=720) {heatActive=false;heatStarted=null}
        frame=frame.copy(rankProgress=rankProgress,heatPending=pending,heatElapsedMs=if(heatActive) heatElapsed else null,
            heatScale=heatScale,running=rankActive || heatActive || pending)
    }
    fun settle() {
        rankActive=false;heatActive=false;heatStarted=null;barrierFrames=0
        frame=frame.copy(rankProgress=1.0,heatPending=false,heatElapsedMs=null,running=false)
        frame.targets.forEach {(key,target)->trackWidths[key]?.let {paintedWidths[key]=it*target}}
    }
    companion object {private val rankEase=CubicBezierEasing(.22f,1f,.36f,1f)}
}
internal data class DashboardOverviewModel(
    val summary:HistorySummary?,val today:LocalDate,val cells:List<HomeHeatCell>,val months:List<HomeMonthLabel>,
    val models:List<DashboardRankingRow>,val tools:List<DashboardRankingRow>,val retainedTokens:BigInteger
) {
    val weeks get()=cells.lastOrNull()?.column?.plus(1) ?: 0
}

/** Independent Dashboard overview. Never patches live-today or derives the lifetime summary. */
internal object DashboardOverviewProjection {
    fun project(history:HubHistory,today:LocalDate):DashboardOverviewModel {
        // Parsed rows are descending; original Hub history is chronological. Preserve
        // each map's insertion order so equal Top 5 totals retain original source order.
        val daily=history.daily.sortedBy {it.date}
        val grand=daily.fold(BigInteger.ZERO) {sum,row->sum+integer(row.tokens)}
        fun ranking(byModel:Boolean):List<DashboardRankingRow> {
            val totals=linkedMapOf<String,BigInteger>()
            daily.forEach {row->(if(byModel) row.perModel else row.perClient).forEach {(raw,value)->
                val key=if(!byModel && raw=="antigravity-cli") "antigravity" else raw
                totals[key]=(totals[key] ?: BigInteger.ZERO)+(value.presentationTokens() ?: BigInteger.ZERO)
            }}
            // JS Object.entries puts array-index keys first, before other insertion keys.
            val ordered=totals.entries.sortedWith(compareBy<Map.Entry<String,BigInteger>> {jsIndex(it.key)==null}
                .thenBy {jsIndex(it.key) ?: 0L})
            val rows=ordered.filter {it.value.signum()>0}.sortedByDescending {it.value}.take(5)
            val max=rows.firstOrNull()?.value ?: BigInteger.ZERO
            return rows.map {(key,tokens)->DashboardRankingRow(key,tokens,
                percent(tokens,grand),percent(tokens,max).toFloat()/100f)}
        }
        val maxTokens=daily.mapNotNull {it.tokens?.takeIf {n->n>=0}}.maxOrNull()?.toBigDecimal() ?: BigDecimal.ZERO
        val maxCost=daily.mapNotNull {decimal(it.cost)}.maxOrNull() ?: BigDecimal.ZERO
        val grouped=daily.groupBy {it.date}
        val start=today.withDayOfMonth(1).minusMonths(11).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val cells=mutableListOf<HomeHeatCell>()
        var date=start
        while(date<=today) {
            val index=cells.size
            // Source accepts last duplicate; Android keeps ambiguous days unknown, as
            // in the existing history viewer. Rankings still sum all retained records.
            val row=grouped[date.toString()]?.singleOrNull()
            cells+=HomeHeatCell(date.toString(),index/7,index%7,row,
                intensity(row?.tokens?.toBigDecimal(),maxTokens),intensity(decimal(row?.cost),maxCost))
            date=date.plusDays(1)
        }
        return DashboardOverviewModel(history.summary,today,cells,
            cells.filter {it.date.endsWith("-01")}.map {HomeMonthLabel(it.date.take(7),it.column)},
            ranking(true),ranking(false),grand)
    }

    /** Original renderActivity: fractional cells, gap4, min9/max22, no trailing gap. */
    fun heatGeometry(weeks:Int,availableWidth:Double):DashboardHeatGeometry {
        require(weeks>=0)
        val cell=if(weeks>0 && availableWidth.isFinite() && availableWidth>0)
            ((availableWidth-weeks*4.0)/weeks).coerceIn(9.0,22.0) else 14.0
        return DashboardHeatGeometry(cell,4.0,weeks)
    }
    fun hitCell(model:DashboardOverviewModel,geometry:DashboardHeatGeometry,x:Double,y:Double):HomeHeatCell? {
        if(!x.isFinite() || !y.isFinite() || x<0 || y<0 || x>=geometry.width || y>=geometry.height) return null
        if(x%geometry.pitch>=geometry.cell || y%geometry.pitch>=geometry.cell) return null
        val column=floor(x/geometry.pitch).toInt()
        val weekday=floor(y/geometry.pitch).toInt()
        return model.cells.getOrNull(column*7+weekday)
    }
    fun count(value:Long?,units:String,locale:String):String = value?.let {DesktopCompactTokens.format(it.toDouble(),units,locale)} ?: "—"
    fun duration(value:Long?):String = value?.let {TrendsMainProjection.activeDuration(BigInteger.valueOf(it))} ?: "—"
    /** Original usageCharts.statCardColumnWidths, reusable for the native 2/4/8-column rows. */
    fun statWidths(contentWidths:List<Double>,totalWidth:Double,minWidth:Double=92.0,safety:Double=10.0):List<Double> {
        val widths=contentWidths.map {if(it.isFinite()) it.coerceAtLeast(0.0) else 0.0}
        if(widths.isEmpty()) return emptyList()
        if(!totalWidth.isFinite() || totalWidth<=0) return widths.map {0.0}
        val equal=totalWidth/widths.size
        val minimum=if(minWidth.isFinite() && minWidth>0) minWidth else equal*.72
        val required=widths.map {it+(if(safety.isFinite()) safety.coerceAtLeast(0.0) else 0.0)}
        val columns=required.map {maxOf(equal,it)}.toMutableList()
        val overflow=columns.sum()-totalWidth
        if(overflow>0) {
            val capacities=required.mapIndexed {index,value->(columns[index]-maxOf(value,minimum)).coerceAtLeast(0.0)}
            val capacity=capacities.sum()
            if(capacity>0) columns.indices.forEach {index->columns[index]-=minOf(capacities[index],overflow*capacities[index]/capacity)}
        }
        val sum=columns.sum()
        if(sum>totalWidth) columns.indices.forEach {columns[it]*=totalWidth/sum}
        val rounded=columns.map {floor(it*10+.5)/10}.toMutableList()
        val delta=floor((totalWidth-rounded.sum())*10+.5)/10
        rounded[rounded.lastIndex]=(floor((rounded.last()+delta)*10+.5)/10).coerceAtLeast(0.0)
        return rounded
    }

    /** Source compactMoney path: retain full small-cost precision, compact only above its unit threshold. */
    fun compactCost(value:String?,unpriced:Long?,currency:UsageCurrency,units:String,locale:String):String {
        val amount=currency.converted(value) ?: return "—"
        if((unpriced ?: 0)>0 && amount<=0.0) return "—"
        if(amount<DesktopCompactTokens.threshold(units,locale)) return currency.format(value)
        val symbol=mapOf("USD" to "$","TWD" to "NT$","HKD" to "HK$","CNY" to "¥").getValue(currency.code)
        return symbol+DesktopCompactTokens.format(amount,units,locale,roundTokens=false)
    }
    private fun integer(value:Long?)=BigInteger.valueOf((value ?: 0).coerceAtLeast(0))
    private fun decimal(value:String?)=value?.takeIf {it.length<=128}?.let {runCatching {BigDecimal(it)}.getOrNull()}
        ?.takeIf {it.signum()>=0 && it.precision()<=100 && it.scale() in -100..100}
    private fun intensity(value:BigDecimal?,max:BigDecimal):Int {
        if(value==null || value.signum()<=0 || max.signum()<=0) return 0
        return when {
            value>=max.multiply(BigDecimal("0.75"))->4
            value>=max.multiply(BigDecimal("0.5"))->3
            value>=max.multiply(BigDecimal("0.25"))->2
            else->1
        }
    }
    private fun percent(value:BigInteger,total:BigInteger):String = if(total.signum()<=0) "0.0" else
        BigDecimal(value.toDouble()/total.toDouble()*100).setScale(1,RoundingMode.HALF_UP).toPlainString()
    private fun jsIndex(key:String):Long?=key.toLongOrNull()?.takeIf {it in 0..4_294_967_294L && it.toString()==key}
}
