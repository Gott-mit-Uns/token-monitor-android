package dev.bing.tokenmonitor

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver

internal enum class DashboardTab { OVERVIEW, TRENDS, RECORDS }
internal enum class DashboardMotionReason { ENTRY, UPDATE, SERIES, NONE }

private data class DashboardChartCommit(
    val model:DashboardTrendModel,val event:Any?,val revision:Int,val reason:DashboardMotionReason,
    val prepared:DashboardPreparedChart?
)

/** A separate window's choices; closing/reopening resets them, opening settings does not. */
@Stable internal class DashboardUiState {
    var tab by mutableStateOf(DashboardTab.OVERVIEW)
        private set
    var range by mutableStateOf("30")
        private set
    var byModel by mutableStateOf(false)
        private set
    var metricCost by mutableStateOf(true)
        private set
    var mode by mutableStateOf(DashboardTrendMode.BARS)
        private set
    var motionRevision by mutableIntStateOf(0)
        private set
    var motionReason by mutableStateOf(DashboardMotionReason.ENTRY)
        private set
    private var previousTab by mutableStateOf(DashboardTab.OVERVIEW)
    var chartWidth by mutableDoubleStateOf(0.0)
        private set
    var chartHeight by mutableDoubleStateOf(0.0)
        private set
    var overviewWidth by mutableDoubleStateOf(0.0)
        private set
    // A single snapshot publishes pixels and the event that starts their source motion.
    // No worker result or raster object is persisted by the window's Saver.
    private var committedChartTarget by mutableStateOf<DashboardChartCommit?>(null)
    val committedChart get()=committedChartTarget?.model
    val committedChartEvent get()=committedChartTarget?.event
    val committedChartRevision get()=committedChartTarget?.revision ?: -1
    val committedChartReason get()=committedChartTarget?.reason ?: DashboardMotionReason.NONE
    val committedChartPreparation get()=committedChartTarget?.prepared

    fun resizeChart(width:Double,height:Double) {
        if(width>0 && height>0) {chartWidth=width;chartHeight=height}
    }
    fun resizeOverview(width:Double) {if(width>0) overviewWidth=width}
    fun discardChart() {
        committedChartTarget=null
    }
    fun commitChart(model:DashboardTrendModel,event:Any?,revision:Int,reason:DashboardMotionReason,
        prepared:DashboardPreparedChart?=null) {
        if(revision!=motionRevision) return
        require(prepared==null || prepared.model===model)
        committedChartTarget=DashboardChartCommit(model,event,revision,reason,prepared)
    }

    fun reset(costMetric:Boolean=true) {
        tab=DashboardTab.OVERVIEW;previousTab=tab;range="30";byModel=false;mode=DashboardTrendMode.BARS
        metricCost=costMetric
        chartWidth=0.0;chartHeight=0.0;overviewWidth=0.0
        committedChartTarget=null
        motionReason=DashboardMotionReason.ENTRY;motionRevision++
    }
    fun selectTab(next:DashboardTab) {
        if(next==tab) return
        if(next==DashboardTab.RECORDS) previousTab=tab
        tab=next;motionReason=DashboardMotionReason.ENTRY;motionRevision++
    }
    fun closeRecords()=selectTab(previousTab)
    fun selectRange(next:String) {
        require(next in DashboardTrendProjection.ranges)
        if(next==range) return
        range=next;motionReason=DashboardMotionReason.UPDATE;motionRevision++
    }
    fun selectDimension(next:Boolean) {
        if(next==byModel) return
        byModel=next;motionReason=DashboardMotionReason.SERIES;motionRevision++
    }
    fun selectMode(next:DashboardTrendMode) {
        if(next==mode) return
        mode=next;motionReason=DashboardMotionReason.UPDATE;motionRevision++
    }
    fun selectMetric(next:Boolean) {
        if(next==metricCost) return
        metricCost=next;motionReason=DashboardMotionReason.NONE;motionRevision++
    }

    companion object {
        val Saver=listSaver<DashboardUiState,Any>(save={listOf(it.tab.name,it.range,it.byModel,it.mode.name,
            it.previousTab.name,it.motionRevision,it.motionReason.name,it.metricCost)},restore={values->DashboardUiState().apply {
            tab=DashboardTab.valueOf(values[0] as String);range=values[1] as String
            byModel=values[2] as Boolean;mode=DashboardTrendMode.valueOf(values[3] as String)
            previousTab=DashboardTab.valueOf(values[4] as String);motionRevision=values[5] as Int
            motionReason=DashboardMotionReason.valueOf(values[6] as String)
            metricCost=values[7] as Boolean
        }})
    }
}
