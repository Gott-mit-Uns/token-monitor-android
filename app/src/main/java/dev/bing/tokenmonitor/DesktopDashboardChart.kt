package dev.bing.tokenmonitor

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Measured native text bounds also exposed for axis accessibility/layout diagnostics. */
internal val DashboardAxisBounds = SemanticsPropertyKey<List<Rect>>("DashboardAxisBounds")
private data class DashboardAxisPlacement(val text:String,val layout:TextLayoutResult,val bounds:Rect)

private fun dateAxisPlacements(labels:List<Pair<DashboardTrendAxisLabel,TextLayoutResult>>,
    model:DashboardTrendModel,size:IntSize,gap:Float):List<DashboardAxisPlacement> {
    if(size.width<=0 || size.height<=0) return emptyList()
    val sx=size.width/model.width.toFloat();val sy=size.height/model.height.toFloat()
    val candidates=labels.map {(label,layout)->
        val x=(label.x.toFloat()*sx-layout.size.width/2f).coerceIn(0f,(size.width-layout.size.width).coerceAtLeast(0).toFloat())
        val y=(label.y.toFloat()*sy-layout.firstBaseline).coerceIn(0f,(size.height-layout.size.height).coerceAtLeast(0).toFloat())
        DashboardAxisPlacement(label.label,layout,Rect(x,y,x+layout.size.width,y+layout.size.height))
    }
    if(candidates.size<2) return candidates
    val first=candidates.first();val last=candidates.last()
    val selected=mutableListOf(first)
    candidates.drop(1).dropLast(1).forEach {candidate->
        if(candidate.bounds.left>=selected.last().bounds.right+gap && candidate.bounds.right+gap<=last.bounds.left) selected+=candidate
    }
    if(last.bounds.left>=selected.last().bounds.right+gap) selected+=last
    return selected
}

internal data class DashboardRasterStrip(val sourceX:Int,val left:Int,val width:Int,val top:Int,val height:Int)
internal class DashboardBarAtlas(
    val image:ImageBitmap,val height:Int,val strips:Map<String,DashboardRasterStrip>,barLabels:List<String>
) {
    // Keep source order (including duplicate labels). Transparent independently padded
    // rectangles become two triangles each, not 366 Canvas image/display-list calls.
    private val ordered=barLabels.mapNotNull {key->strips[key]?.let {key to it}}
    private val positions=FloatArray(ordered.size*12)
    private val texture=FloatArray(positions.size).apply {
        ordered.forEachIndexed {index,(_,strip)->
            val left=strip.sourceX.toFloat();val right=left+strip.width
            val top=strip.top.toFloat();val bottom=top+strip.height;val offset=index*12
            this[offset]=left;this[offset+1]=top
            this[offset+2]=right;this[offset+3]=top
            this[offset+4]=right;this[offset+5]=bottom
            this[offset+6]=left;this[offset+7]=top
            this[offset+8]=right;this[offset+9]=bottom
            this[offset+10]=left;this[offset+11]=bottom
        }
    }
    private val paint=android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
        color=android.graphics.Color.WHITE
        shader=android.graphics.BitmapShader(image.asAndroidBitmap(),android.graphics.Shader.TileMode.CLAMP,
            android.graphics.Shader.TileMode.CLAMP)
    }
    fun drawBatch(canvas:android.graphics.Canvas,frame:DashboardChartFrame?,sx:Float,sy:Float):Boolean {
        // Hardware drawVertices starts at API29. API26–28 retain supported image sprites.
        if(Build.VERSION.SDK_INT<29) return false
        ordered.forEachIndexed {index,(key,strip)->
            val transform=frame?.bars?.get(key)
            val ox=(transform?.originX?.toFloat() ?: 0f)*sx
            val oy=(transform?.originY?.toFloat() ?: 0f)*sy
            val kx=transform?.scaleX?.toFloat() ?: 1f;val ky=transform?.scaleY?.toFloat() ?: 1f
            val tx=((transform?.translateX ?: 0.0)*sx).toFloat()
            val ty=((transform?.translateY ?: 0.0)*sy).toFloat()
            // Explicitly apply the original translate * scale-around-origin FLIP
            // to the same destination pixel lattice used by the sprite fallback.
            val left=ox+(strip.left-ox)*kx+tx;val right=ox+(strip.left+strip.width-ox)*kx+tx
            val top=oy+(strip.top-oy)*ky+ty;val bottom=oy+(strip.top+strip.height-oy)*ky+ty
            val offset=index*12
            positions[offset]=left;positions[offset+1]=top
            positions[offset+2]=right;positions[offset+3]=top
            positions[offset+4]=right;positions[offset+5]=bottom
            positions[offset+6]=left;positions[offset+7]=top
            positions[offset+8]=right;positions[offset+9]=bottom
            positions[offset+10]=left;positions[offset+11]=bottom
        }
        // Native Canvas copies these array values into its recorded vertices; the
        // reused buffers belong only to this Canvas leaf, never to Compose state.
        if(positions.isNotEmpty()) canvas.drawVertices(android.graphics.Canvas.VertexMode.TRIANGLES,positions.size,positions,0,
            texture,0,null,0,null,0,0,paint)
        return true
    }
}

/** Ordinary charts and a once-only lazy fallback retain the original vector paths. */
private fun dashboardBarVectorPaths(bars:List<DashboardTrendBar>,colors:Map<String,Color>,checkActive:()->Unit={}):Map<String,List<Pair<Color,Path>>> =
    bars.associate {bar->
        checkActive()
        bar.label to bar.segments.mapIndexedNotNull {index,segment->
        if(segment.height<=0.0) null else {
            fun round(value:Double)=DashboardTrendProjection.svgRound(value).toFloat()
            val path=Path()
            val x=segment.x;val y=segment.y;val w=segment.width;val h=segment.height
            if(index==bar.segments.lastIndex) {
                val radius=min(3.0,min(w/2,h))
                path.moveTo(round(x),round(y+h))
                path.lineTo(round(x),round(y+radius))
                path.quadraticBezierTo(round(x),round(y),round(x+radius),round(y))
                path.lineTo(round(x+w-radius),round(y))
                path.quadraticBezierTo(round(x+w),round(y),round(x+w),round(y+radius))
                path.lineTo(round(x+w),round(y+h));path.close()
            } else path.addRect(androidx.compose.ui.geometry.Rect(round(x),round(y),
                round(x)+round(w),round(y)+round(h)))
            colors.getValue(segment.key) to path
        }
    }}

/** Immutable target pixels. Never includes axis/text or reads per-frame motion State. */
internal class DashboardDenseBarCache(
    barList:List<DashboardTrendBar>,private val colors:Map<String,Color>,
    private val sourceWidth:Double,private val sourceHeight:Double
) {
    private val bars=barList.associateBy {it.label}
    private val barLabels=barList.map {it.label}
    private val argb=colors.mapValues {it.value.toArgb()}
    private val rasterPath=android.graphics.Path()
    private val rasterPaint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private var vectorFallback:Map<String,List<Pair<Color,Path>>>?=null
    private var attemptedSize:Size?=null
    private var atlas:DashboardBarAtlas?=null
    fun fallbackPaths(checkActive:()->Unit={}):Map<String,List<Pair<Color,Path>>> = vectorFallback ?:
        dashboardBarVectorPaths(bars.values.toList(),colors,checkActive).also {vectorFallback=it}
    fun forSize(size:Size,checkActive:()->Unit={}):DashboardBarAtlas? {
        checkActive()
        if(attemptedSize==size) return atlas
        attemptedSize=size;atlas=null
        if(!size.width.isFinite() || !size.height.isFinite() || size.width<=0 || size.height<=0 ||
            size.width>8192 || size.height>8192 || sourceWidth<=0 || sourceHeight<=0 ||
            !sourceWidth.isFinite() || !sourceHeight.isFinite()) return null
        val height=ceil(size.height.toDouble()).toInt()
        val sx=size.width/sourceWidth.toFloat();val sy=size.height/sourceHeight.toFloat()
        val strips=linkedMapOf<String,DashboardRasterStrip>()
        var width=0L
        fun round(value:Double)=DashboardTrendProjection.svgRound(value).toFloat()
        bars.forEach {(key,bar)->
            checkActive()
            var minX=Float.POSITIVE_INFINITY;var minY=Float.POSITIVE_INFINITY
            var maxX=Float.NEGATIVE_INFINITY;var maxY=Float.NEGATIVE_INFINITY
            bar.segments.forEachIndexed {index,segment->
                if(segment.height>0.0) {
                    val x=round(segment.x);val y=round(segment.y)
                    // Rect dimensions and top-cap endpoints have distinct source rounding.
                    val right=if(index==bar.segments.lastIndex) round(segment.x+segment.width) else x+round(segment.width)
                    val bottom=if(index==bar.segments.lastIndex) round(segment.y+segment.height) else y+round(segment.height)
                    minX=minOf(minX,x);minY=minOf(minY,y)
                    maxX=maxOf(maxX,right);maxY=maxOf(maxY,bottom)
                }
            }
            if(minX.isFinite() && minY.isFinite() && maxX.isFinite() && maxY.isFinite()) {
                // Each independent column gets its own antialias padding. Cropping
                // the complete chart instead could pull a neighbouring narrow bar.
                val left=floor(minX.toDouble()*sx).toInt()-1
                val right=ceil(maxX.toDouble()*sx).toInt()+1
                // The storage may be full-height; sampling/drawing is not. Retain
                // one transparent antialias pixel at both ends of the actual column.
                val top=(floor(minY.toDouble()*sy).toInt()-1).coerceIn(0,height)
                val bottom=(ceil(maxY.toDouble()*sy).toInt()+1).coerceIn(0,height)
                if(bottom<=top) return@forEach
                val stripWidth=(right.toLong()-left).coerceAtLeast(1)
                if(stripWidth>8192 || width+stripWidth>8192 || (width+stripWidth)*height>8_000_000L) return null
                strips[key]=DashboardRasterStrip(width.toInt(),left,stripWidth.toInt(),top,bottom-top)
                width+=stripWidth
            }
        }
        if(width<=0) return null
        checkActive()
        val bitmap=try {Bitmap.createBitmap(width.toInt(),height,Bitmap.Config.ARGB_8888)}
            catch(_:IllegalArgumentException) {return null} catch(_:OutOfMemoryError) {return null}
        val canvas=android.graphics.Canvas(bitmap)
        bars.forEach {(key,bar)->strips[key]?.let {strip->
            checkActive()
            canvas.save()
            canvas.clipRect(strip.sourceX,0,strip.sourceX+strip.width,height)
            canvas.translate((strip.sourceX-strip.left).toFloat(),0f)
            canvas.scale(sx,sy)
            bar.segments.forEachIndexed {index,segment->
                if(segment.height>0.0) {
                    rasterPaint.color=argb.getValue(segment.key)
                    val x=segment.x;val y=segment.y;val w=segment.width;val h=segment.height
                    rasterPath.rewind()
                    if(index==bar.segments.lastIndex) {
                        val radius=min(3.0,min(w/2,h))
                        rasterPath.moveTo(round(x),round(y+h))
                        rasterPath.lineTo(round(x),round(y+radius))
                        rasterPath.quadTo(round(x),round(y),round(x+radius),round(y))
                        rasterPath.lineTo(round(x+w-radius),round(y))
                        rasterPath.quadTo(round(x+w),round(y),round(x+w),round(y+radius))
                        rasterPath.lineTo(round(x+w),round(y+h));rasterPath.close()
                    } else {
                        // Match Compose AndroidPath.addRect's CCW path rasterisation;
                        // direct drawRect has different AA coverage at fractional gaps.
                        rasterPath.addRect(round(x),round(y),round(x)+round(w),round(y)+round(h),android.graphics.Path.Direction.CCW)
                    }
                    canvas.drawPath(rasterPath,rasterPaint)
                }
            }
            canvas.restore()
        }}
        checkActive()
        bitmap.prepareToDraw()
        // No recycle: an older atlas can still belong to the render thread's display list.
        return DashboardBarAtlas(bitmap.asImageBitmap(),height,strips,barLabels).also {atlas=it}
    }
}

/** One immutable model/palette/pixel-size target, published only after preparation. */
internal class DashboardPreparedChart internal constructor(
    val model:DashboardTrendModel,
    val pixelSize:IntSize,
    val colors:Map<String,Color>,
    internal val denseCache:DashboardDenseBarCache?,
    internal val atlas:DashboardBarAtlas?
) {
    fun matches(target:DashboardTrendModel,palette:Map<String,Long>,size:IntSize)=
        pixelSize==size && model==target && colors.all {(key,color)->color==dashboardColor(key,target.byModel,palette)}
}

private fun dashboardHasDenseBars(model:DashboardTrendModel)=
    model.mode==DashboardTrendMode.BARS && model.bars.associateBy {it.label}.values
        .sumOf {bar->bar.segments.count {it.height>0.0}.toLong()}>=4096

/** Dense raster work never runs in a UI draw; cancellation is checked for every bar. */
internal suspend fun prepareDashboardChart(
    model:DashboardTrendModel,palette:Map<String,Long>,pixelSize:IntSize
):DashboardPreparedChart {
    currentCoroutineContext().ensureActive()
    val colors=model.keys.associateWith {dashboardColor(it,model.byModel,palette)}
    if(!dashboardHasDenseBars(model)) return DashboardPreparedChart(model,pixelSize,colors,null,null)
    return withContext(Dispatchers.Default) {
        val context=currentCoroutineContext()
        context.ensureActive()
        val cache=DashboardDenseBarCache(model.bars,colors,model.width,model.height)
        val atlas=cache.forSize(Size(pixelSize.width.toFloat(),pixelSize.height.toFloat())) {context.ensureActive()}
        // Oversize/allocation-failed targets retain the original vector renderer,
        // but their one-time path preparation also stays off the UI thread.
        if(atlas==null) cache.fallbackPaths {context.ensureActive()}
        context.ensureActive()
        DashboardPreparedChart(model,pixelSize,colors,cache,atlas)
    }
}

/** Source Dashboard geometry, with a chart-wide touch target and accessible record navigation. */
@Composable internal fun DesktopDashboardChart(
    model:DashboardTrendModel,
    modifier:Modifier=Modifier,
    onBar:(DashboardTrendBar,HistoryRow)->Unit,
    onCandle:(DashboardTrendCandle)->Unit,
    motion:State<DashboardChartFrame>?=null,
    prepared:DashboardPreparedChart?=null
) {
    val strings=LocalResources.current
    val locale=strings.configuration.locales[0]
    val display=LocalMainDisplay.current
    val palette=LocalDesktopVendorPalette.current
    val line=LocalDesktopLineColor.current
    val textMeasurer=rememberTextMeasurer()
    val textStyle=quotaTextStyle(10).copy(color=line.copy(alpha=.5f))
    fun compact(value:Double)=DesktopCompactTokens.format(value,display.compactUnits,DesktopCompactTokens.localeTag(locale))
    val ticks=remember(model.axisTicks,textStyle,textMeasurer,display.compactUnits,locale) {
        model.axisTicks.map {tick->tick to textMeasurer.measure(compact(tick.value.toDouble()),textStyle)}
    }
    val labels=remember(model.axisLabels,model.daily,model.bars,model.candles,textStyle,textMeasurer) {
        val last=model.daily.lastOrNull()?.let {row->
            val x=if(model.mode==DashboardTrendMode.BARS) model.bars.lastOrNull()?.let {it.x+it.width/2} else model.candles.lastOrNull()?.wickX
            x?.let {DashboardTrendAxisLabel(DashboardTrendProjection.shortDate(row.date),it,model.height-4)}
        }
        val complete=if(last==null) model.axisLabels else model.axisLabels.filter {it.x<last.x} + last
        complete.map {label->label to textMeasurer.measure(label.label,textStyle)}
    }
    var axisPixelSize by remember {mutableStateOf(IntSize.Zero)}
    val nativeDensity=LocalDensity.current
    val dates=remember(labels,model.width,model.height,axisPixelSize,nativeDensity) {
        dateAxisPlacements(labels,model,axisPixelSize,with(nativeDensity) {4.dp.toPx()})
    }
    val tickPlacements=remember(ticks,model.width,model.height,axisPixelSize,dates,nativeDensity) {
        if(axisPixelSize==IntSize.Zero) emptyList() else ticks.map {(tick,layout)->
            val sx=axisPixelSize.width/model.width.toFloat();val sy=axisPixelSize.height/model.height.toFloat()
            val x=(tick.labelX.toFloat()*sx-layout.size.width).coerceIn(0f,(axisPixelSize.width-layout.size.width).coerceAtLeast(0).toFloat())
            val lastTextTop=dates.minOfOrNull {it.bounds.top} ?: axisPixelSize.height.toFloat()
            val maximumTop=minOf(axisPixelSize.height-layout.size.height.toFloat(),lastTextTop-layout.size.height-with(nativeDensity) {2.dp.toPx()}).coerceAtLeast(0f)
            val y=(tick.labelY.toFloat()*sy-layout.firstBaseline).coerceIn(0f,maximumTop)
            DashboardAxisPlacement(layout.layoutInput.text.text,layout,Rect(x,y,x+layout.size.width,y+layout.size.height))
        }
    }
    // A pending palette change must not recolour the previous committed model.
    // Direct component callers without a prepared target retain the original path.
    val preparedTarget=prepared?.takeIf {it.model===model}
    val barColors=remember(model.keys,model.byModel,palette,preparedTarget) {
        preparedTarget?.colors ?: model.keys.associateWith {dashboardColor(it,model.byModel,palette)}
    }
    val dense=remember(model.bars,model.mode,preparedTarget) {
        preparedTarget?.let {it.denseCache!=null} ?: dashboardHasDenseBars(model)
    }
    // Dense targets avoid thousands of Path/Pair/native mask allocations entirely.
    val barPaths=remember(model.bars,barColors,model.mode,dense) {
        if(model.mode!=DashboardTrendMode.BARS || dense) emptyMap() else dashboardBarVectorPaths(model.bars,barColors)
    }
    val density=LocalDensity.current.density
    val denseCache=remember(model.bars,barColors,model.width,model.height,model.mode,density,dense,preparedTarget) {
        preparedTarget?.denseCache ?: if(dense) DashboardDenseBarCache(model.bars,barColors,model.width,model.height) else null
    }
    var accessibleIndex by remember(model.daily,model.mode) {mutableIntStateOf(0)}
    val count=if(model.mode==DashboardTrendMode.BARS) model.bars.size else model.candles.size
    fun choose(index:Int) {
        if(count==0) return
        accessibleIndex=index.coerceIn(0,count-1)
        if(model.mode==DashboardTrendMode.BARS) {
            val bar=model.bars[accessibleIndex];onBar(bar,model.daily[accessibleIndex])
        } else onCandle(model.candles[accessibleIndex])
    }
    Canvas(modifier.fillMaxWidth().onSizeChanged {axisPixelSize=it}.testTag("dashboard-chart").semantics {
        this[DashboardAxisBounds]=(tickPlacements+dates).map {it.bounds}
        stateDescription=dates.joinToString(" – ") {it.text}
        contentDescription=strings.getString(R.string.dashboard_chart_description,count)
        if(count>0) {
            onClick(strings.getString(R.string.chart_tap_detail)) {choose(accessibleIndex);true}
            customActions=listOf(
                CustomAccessibilityAction(strings.getString(R.string.dashboard_previous_record)) {choose(accessibleIndex-1);true},
                CustomAccessibilityAction(strings.getString(R.string.dashboard_next_record)) {choose(accessibleIndex+1);true})
        }
    }.pointerInput(model,onBar,onCandle) {
        detectTapGestures {point->
            val x=point.x/size.width*model.width
            val y=point.y/size.height*model.height
            if(count>0 && x>=model.plot.x && x<=model.plot.x+model.plot.width &&
                y>=model.plot.y && y<=model.plot.y+model.plot.height) {
                choose(((x-model.plot.x)/model.plot.width*count).toInt().coerceIn(0,count-1))
            }
        }
    }) {
        val sx=size.width/model.width.toFloat();val sy=size.height/model.height.toFloat()
        fun point(x:Double,y:Double)=Offset(x.toFloat()*sx,y.toFloat()*sy)
        fun roundedPoint(x:Double,y:Double)=point(DashboardTrendProjection.svgRound(x),DashboardTrendProjection.svgRound(y))
        val frame=motion?.value
        ticks.forEach { (tick,_)->
            drawLine(line.copy(alpha=.07f),point(model.plot.x,tick.lineY),
                point(model.plot.x+model.plot.width,tick.lineY),sx)
        }
        tickPlacements.forEach {drawText(it.layout,topLeft=it.bounds.topLeft)}
        if(model.mode==DashboardTrendMode.BARS) {
            val atlas=if(preparedTarget!=null) preparedTarget.atlas else denseCache?.forSize(size)
            drawLine(line.copy(alpha=.18f),point(model.plot.x,model.plot.y+model.plot.height),
                point(model.plot.x+model.plot.width,model.plot.y+model.plot.height),sx)
            // While a resized target prepares, scale the already committed atlas.
            // It keeps its own pixel lattice and never rasterises again in this draw.
            val atlasScaleX=if(atlas!=null && preparedTarget!=null) size.width/preparedTarget.pixelSize.width else 1f
            val atlasScaleY=if(atlas!=null && preparedTarget!=null) size.height/preparedTarget.pixelSize.height else 1f
            val atlasSx=sx/atlasScaleX;val atlasSy=sy/atlasScaleY
            var batched=false
            if(atlas!=null) withTransform({scale(atlasScaleX,atlasScaleY,Offset.Zero)}) {
                batched=atlas.drawBatch(drawContext.canvas.nativeCanvas,frame,atlasSx,atlasSy)
            }
            val paths=if(!batched && atlas==null && denseCache!=null) denseCache.fallbackPaths() else barPaths
            if(!batched) model.bars.forEach {bar->
                val transform=frame?.bars?.get(bar.label) ?: DashboardBarTransform()
                val strip=atlas?.strips?.get(bar.label)
                if(atlas!=null && strip!=null) withTransform({
                    scale(atlasScaleX,atlasScaleY,Offset.Zero)
                    translate((transform.translateX*atlasSx).toFloat(),(transform.translateY*atlasSy).toFloat())
                    scale(transform.scaleX.toFloat(),transform.scaleY.toFloat(),
                        Offset(transform.originX.toFloat()*atlasSx,transform.originY.toFloat()*atlasSy))
                }) {
                    drawImage(atlas.image,srcOffset=IntOffset(strip.sourceX,strip.top),srcSize=IntSize(strip.width,strip.height),
                        dstOffset=IntOffset(strip.left,strip.top),dstSize=IntSize(strip.width,strip.height),filterQuality=FilterQuality.Low)
                } else withTransform({
                    translate((transform.translateX*sx).toFloat(),(transform.translateY*sy).toFloat())
                    scale(transform.scaleX.toFloat(),transform.scaleY.toFloat(),point(transform.originX,transform.originY))
                    scale(sx,sy,Offset.Zero)
                }) {
                        paths[bar.label].orEmpty().forEach {(color,path)->drawPath(path,color)}
                }
            }
        } else model.candles.forEach {candle->
            val y=candle.bodyY;val h=candle.bodyHeight;val high=candle.yHigh;val low=candle.yLow
            if(y!=null && h!=null && high!=null && low!=null && candle.colorArgb!=null) {
                val color=Color(candle.colorArgb)
                val bodyY=DashboardTrendProjection.svgRound(y);val bodyHeight=DashboardTrendProjection.svgRound(h)
                val middle=bodyY+bodyHeight/2
                val wickMiddle=DashboardTrendProjection.svgRound(y+h/2)
                val wickX=DashboardTrendProjection.svgRound(candle.wickX)
                val progress=frame?.candles?.get(candle.key) ?: DashboardCandleProgress()
                drawLine(color,point(wickX,wickMiddle),point(wickX,wickMiddle+(DashboardTrendProjection.svgRound(high)-wickMiddle)*progress.highWickProgress),1.5f*sx)
                drawLine(color,point(wickX,wickMiddle),point(wickX,wickMiddle+(DashboardTrendProjection.svgRound(low)-wickMiddle)*progress.lowWickProgress),1.5f*sx)
                withTransform({scale(1f,progress.bodyScaleY.toFloat(),point(DashboardTrendProjection.svgRound(candle.x)+DashboardTrendProjection.svgRound(candle.width)/2,middle))}) {
                    withTransform({scale(sx,sy,Offset.Zero)}) {
                        val position=Offset(DashboardTrendProjection.svgRound(candle.x).toFloat(),bodyY.toFloat())
                        val bodySize=Size(DashboardTrendProjection.svgRound(candle.width).toFloat(),bodyHeight.toFloat())
                        val radius=androidx.compose.ui.geometry.CornerRadius(1f)
                        drawRoundRect(color,position,bodySize,radius)
                        // Later source candle-up/down CSS overrides candle-body stroke:none.
                        drawRoundRect(color,position,bodySize,radius,style=androidx.compose.ui.graphics.drawscope.Stroke(1f))
                    }
                }
            }
        }
        dates.forEach {drawText(it.layout,topLeft=it.bounds.topLeft)}
    }
}
