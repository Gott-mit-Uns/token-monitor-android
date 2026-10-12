package dev.bing.tokenmonitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** The independent history view keeps its source end date and never patches live usage. */
@Composable internal fun DesktopActivityPreview(history: HubHistory, metricCost: Boolean = false) {
    val projection = remember(history) { HomeCharts.project(history, null,
        history.daily.maxOfOrNull { it.date }?.let(LocalDate::parse) ?: LocalDate.now(), "year") }
    DesktopActivityPreview(projection, metricCost)
}

/** Desktop geometry and month axis, with day selection and 48dp accessible controls. */
@Composable internal fun DesktopActivityPreview(projection: HomeChartProjection, metricCost: Boolean = true,compactHome:Boolean=false) {
    val strings = LocalResources.current
    val cells = projection.cells
    if (cells.isEmpty()) { Text(strings.getString(R.string.desktop_chart_unknown)); return }
    val scroll = rememberScrollState()
    val entry = if (compactHome) LocalHomeHistoryEntryMotion.current else null
    val entryObserver by rememberUpdatedState(LocalHomeHistoryEntryObserver.current)
    val entryClock = LocalDesktopAnimationClock.current
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    val latestProjection by rememberUpdatedState(projection)
    val densityValue = LocalDensity.current.density
    // Keep the existing right-edge policy; do not restart scrolling on normal data changes.
    LaunchedEffect(projection.today, scroll.maxValue, viewportWidthPx, densityValue, entry) {
        scroll.scrollTo(scroll.maxValue)
        if (entry != null && viewportWidthPx > 0) {
            val visible = HomeHistoryEntryMotion.visibleColumns(scroll.value, viewportWidthPx,
                12.dp.value * densityValue, 9.dp.value * densityValue, latestProjection.columns)
            entryObserver.homeEntryObserve { HomeHistoryEntryObservation(HomeHistoryEntryObservationPhase.VIEWPORT,
                entryClock.nowNanos(), entry.identity, entry.entrySequence, System.identityHashCode(latestProjection),
                viewportWidthPx = viewportWidthPx, scrollPx = scroll.value, pitchPx = 12f * densityValue,
                cellPx = 9f * densityValue, totalColumns = latestProjection.columns,
                visibleFirst = visible?.first ?: -1, visibleLast = visible?.last ?: -1) }
            if (visible != null) entry.heatViewport(latestProjection, visible)
        }
    }
    val colors = MaterialTheme.colorScheme
    val levels = listOf(colors.onSurface.copy(alpha = 0.05f)) + LocalDesktopHeatColors.current.mapIndexed { index,color -> color.copy(alpha=listOf(0.18f,0.45f,0.8f,1f)[index]) }
    val numbers = NumberFormat.getIntegerInstance(strings.configuration.locales[0])
    val density = LocalDensity.current
    val monthFormatter = remember(strings.configuration.locales[0]) { DateTimeFormatter.ofPattern("MMM", strings.configuration.locales[0]) }
    val months = projection.months.map { it to YearMonth.parse(it.month).format(monthFormatter) }
    val canvasWidth = maxOf((projection.columns * 12 - 3).toFloat(), months.lastOrNull()?.let { (month,label) -> month.column * 12 + label.length * 7 * density.fontScale } ?: 0f).dp
    var selectedDate by remember { mutableStateOf<String?>(null) }
    val latest by rememberUpdatedState(projection)
    LaunchedEffect(cells.first().date, cells.last().date) {
        if (selectedDate != null && cells.none { it.date == selectedDate }) selectedDate = null
    }
    val todayRow = cells.last().value
    val description = strings.getString(R.string.activity_date_description, projection.today.toString(), todayRow?.tokens?.let { "${numbers.format(it)} Token" } ?: strings.getString(R.string.usage_unknown))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if(!compactHome) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), itemVerticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("${strings.getString(R.string.activity_title)} · ${numbers.format(projection.activeDays)}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            DesktopTextButton(onClick = { selectedDate = projection.today.toString() }, modifier = Modifier.heightIn(min = 48.dp).testTag("home-activity-day-open"), contentPadding = PaddingValues(horizontal = 8.dp)) { Text(strings.getString(R.string.home_activity_day), style = MaterialTheme.typography.labelSmall) }
        }
        Row(Modifier.fillMaxWidth().testTag("home-activity-viewport").then(if (entry != null) Modifier.onSizeChanged { viewportWidthPx = it.width } else Modifier).horizontalScroll(scroll)) {
        Column(Modifier.width(canvasWidth)) {
        Canvas(Modifier.fillMaxWidth().height(81.dp).testTag("home-activity-chart")
            .pointerInput(projection.today, cells.first().date, density.density) {
                detectTapGestures { position -> HomeCharts.hitCell(latest, (position.x / density.density).toDouble(), (position.y / density.density).toDouble())?.let { selectedDate = it.date } }
            }.semantics {
                contentDescription = strings.getString(R.string.desktop_activity) + ", " + description
                role = Role.Button
                onClick(strings.getString(R.string.home_activity_day)) { selectedDate = projection.today.toString(); true }
            }) {
            val pitch = 12.dp.toPx(); val cell = 9.dp.toPx()
            val frame = entry?.frame?.value
            cells.forEach { point ->
                val base = levels[if (metricCost) point.costLevel else point.tokenLevel]
                drawRoundRect(base.copy(alpha = base.alpha * (frame?.heatOpacity(point.column) ?: 1f)), Offset(point.column * pitch, point.weekday * pitch), Size(cell, cell), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            }
            if (entry != null) entryObserver.homeEntryObserve {
                val visible = HomeHistoryEntryMotion.visibleColumns(scroll.value, viewportWidthPx, pitch, cell, projection.columns)
                val outside = visible?.let { if (it.first > 0) it.first - 1 else it.last + 1 } ?: -1
                HomeHistoryEntryObservation(HomeHistoryEntryObservationPhase.HEAT_DRAW, entryClock.nowNanos(),
                    entry.identity, entry.entrySequence, System.identityHashCode(projection), frame ?: HomeHistoryEntryFrame(),
                    viewportWidthPx, scroll.value, pitch, cell, projection.columns, visible?.first ?: -1, visible?.last ?: -1,
                    frame?.heatOpacity(visible?.first ?: -1) ?: 1f, frame?.heatOpacity(visible?.last ?: -1) ?: 1f,
                    frame?.heatOpacity(outside) ?: 1f, chartWidthPx = size.width, chartHeightPx = size.height)
            }
        }
        Box(Modifier.fillMaxWidth().height((16 * density.fontScale).dp)) {
            months.forEach { (month,label) -> Text(label, Modifier.offset(x = (month.column * 12).dp).testTag("home-activity-month-${month.month}"), fontSize = (9*LocalDesktopTextScale.current).sp, color = colors.onSurfaceVariant) }
        }
        }
        }
        if(compactHome) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
            DesktopTextButton(onClick={selectedDate=projection.today.toString()},modifier=Modifier.heightIn(min=48.dp).testTag("home-activity-day-open"),contentPadding=PaddingValues(horizontal=8.dp)) {Text(strings.getString(R.string.home_activity_day),style=quotaTextStyle(10))}
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(cells.first().date, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant); Text(cells.last().date, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant) }
    }
    selectedDate?.let { date ->
        val index = cells.indexOfFirst { it.date == date }
        if (index >= 0) {
            val row = cells[index].value
            Dialog(onDismissRequest = { selectedDate = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(Modifier.widthIn(max = 360.dp).fillMaxWidth(0.88f).testTag("home-activity-day"), shape = RoundedCornerShape(8.dp),
                    color = colors.surfaceContainer, border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f))) {
                Column(Modifier.padding(horizontal = 12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(date, Modifier.weight(1f).testTag("home-activity-selected-date"), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        DesktopTextButton(onClick = { selectedDate = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text(strings.getString(R.string.close), style = MaterialTheme.typography.labelSmall) }
                    }
                    val numberColor=LocalDesktopNumberColor.current
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(color=numberColor,fontWeight=FontWeight.Bold,fontFeatureSettings="tnum")) {append(row?.tokens?.let(numbers::format) ?: "—")}
                        withStyle(SpanStyle(color=colors.onSurfaceVariant,fontWeight=FontWeight.Medium)) {append(" Token")}
                    }, Modifier.testTag("home-activity-day-tokens"), style = MaterialTheme.typography.titleMedium)
                    Text(strings.getString(R.string.known_cost_formatted, usageCostText(row?.cost, row?.unpricedTokens)), Modifier.testTag("home-activity-day-cost"), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    if (row == null) Text(strings.getString(R.string.home_activity_no_record), style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DesktopTextButton(onClick = { selectedDate = cells[index-1].date }, enabled = index > 0, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("home-activity-day-previous")) { Text(strings.getString(R.string.home_activity_previous_day)) }
                        DesktopTextButton(onClick = { selectedDate = cells[index+1].date }, enabled = index < cells.lastIndex, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("home-activity-day-next")) { Text(strings.getString(R.string.home_activity_next_day)) }
                    }
                }
                }
            }
        }
    }
}

@Composable internal fun DesktopTrendPreview(projection: HomeChartProjection) {
    val rows = projection.trend
    val strings = LocalResources.current; val colors = MaterialTheme.colorScheme; val chart = LocalDesktopChartColor.current
    val numbers = NumberFormat.getIntegerInstance(strings.configuration.locales[0])
    val segments = remember(rows) { HomeCharts.lineSegments(rows) }
    val entry = LocalHomeHistoryEntryMotion.current
    val entryObserver by rememberUpdatedState(LocalHomeHistoryEntryObserver.current)
    val entryClock = LocalDesktopAnimationClock.current
    val cacheCounter = remember { HomeChartCacheCounter() }
    if (rows.isEmpty()) { Text(strings.getString(R.string.desktop_chart_unknown)); return }
    val main=LocalMainDisplay.current
    val peak=projection.peakTokens?.let {DesktopCompactTokens.format(it.toDouble(),main.compactUnits,DesktopCompactTokens.localeTag(strings.configuration.locales[0]))} ?: "—"
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().padding(top=4.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Bottom) {
            Text(strings.getString(R.string.desktop_trends).uppercase(java.util.Locale.ROOT),style=quotaTextStyle(11,true))
            Text(strings.getString(R.string.home_peak_caption,peak),Modifier.semantics {contentDescription=strings.getString(R.string.history_peak,projection.peakTokens?.let(numbers::format) ?: "—")},style=quotaTextStyle(10),color=colors.onSurfaceVariant)
        }
        Spacer(Modifier.fillMaxWidth().height(70.dp).testTag("home-trend-chart")
            .semantics { contentDescription = strings.getString(R.string.history_chart_description, rows.first().date, rows.last().date) }
            .drawWithCache {
                // Build the source's 300x70 path and all target geometry only on content/size changes.
                // SVG getTotalLength measures the unscaled source path, even with preserveAspectRatio=none.
                val scale = android.graphics.Matrix().apply { setScale(size.width / 300f, size.height / 70f) }
                val fullSource = android.graphics.Path()
                val areaSource = android.graphics.Path()
                val measures = segments.map { commands ->
                    val path = android.graphics.Path()
                    commands.forEachIndexed { index, command ->
                        val point = command.point
                        if (index == 0) path.moveTo(point.x.toFloat(), point.y.toFloat())
                        else if (command.control1 != null && command.control2 != null) path.cubicTo(
                            command.control1.x.toFloat(), command.control1.y.toFloat(),
                            command.control2.x.toFloat(), command.control2.y.toFloat(), point.x.toFloat(), point.y.toFloat())
                        else path.lineTo(point.x.toFloat(), point.y.toFloat())
                    }
                    fullSource.addPath(path)
                    areaSource.addPath(android.graphics.Path(path).apply {
                        lineTo(commands.last().point.x.toFloat(), 66f)
                        lineTo(commands.first().point.x.toFloat(), 66f)
                        close()
                    })
                    android.graphics.PathMeasure(path, false)
                }
                val lengths = measures.map { it.length }
                val totalLength = lengths.sum()
                val fullLine = Path().also { fullSource.transform(scale, it.asAndroidPath()) }
                val fullArea = Path().also { areaSource.transform(scale, it.asAndroidPath()) }
                val partialSource = android.graphics.Path()
                val partialLine = Path()
                val gradient = Brush.verticalGradient(listOf(chart.copy(alpha = 0.22f), chart.copy(alpha = 0f)))
                val stroke = Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                val cacheGeneration = cacheCounter.rebuilt()
                entryObserver.homeEntryObserve { HomeHistoryEntryObservation(HomeHistoryEntryObservationPhase.TREND_CACHE_BUILD,
                    entryClock.nowNanos(), entry?.identity ?: 0, entry?.entrySequence ?: 0, System.identityHashCode(projection),
                    cacheIdentity = cacheCounter.identity, cacheGeneration = cacheGeneration, chartWidthPx = size.width,
                    chartHeightPx = size.height, sourceStrokeLength = totalLength) }
                onDrawBehind {
                    // This is the sole per-frame State read. Static paths and projections stay cached.
                    val frame = entry?.frame?.value
                    val reveal = frame?.trendReveal ?: 1f
                    clipRect(right = size.width * reveal) { drawPath(fullArea, gradient) }
                    if (reveal >= 1f) drawPath(fullLine, chart, style = stroke)
                    else if (reveal > 0f && totalLength > 0f) {
                        partialSource.reset()
                        var remaining = totalLength * reveal
                        measures.forEachIndexed { index, measure ->
                            val length = lengths[index]
                            if (remaining > 0f && length > 0f) measure.getSegment(0f, remaining.coerceAtMost(length), partialSource, true)
                            remaining -= length
                        }
                        partialSource.transform(scale, partialLine.asAndroidPath())
                        drawPath(partialLine, chart, style = stroke)
                    }
                    entryObserver.homeEntryObserve { HomeHistoryEntryObservation(HomeHistoryEntryObservationPhase.TREND_DRAW,
                        entryClock.nowNanos(), entry?.identity ?: 0, entry?.entrySequence ?: 0, System.identityHashCode(projection),
                        frame ?: HomeHistoryEntryFrame(), cacheIdentity = cacheCounter.identity, cacheGeneration = cacheGeneration,
                        chartWidthPx = size.width, chartHeightPx = size.height, sourceStrokeLength = totalLength,
                        drawnStrokeLength = totalLength * reveal, areaClipRightPx = size.width * reveal) }
                }
            })
        Row(Modifier.fillMaxWidth().offset(y=(-1).dp)) {
            listOf(rows.first(),rows[(rows.size-1)/2],rows.last()).forEachIndexed {index,row->
                val date=runCatching {java.time.LocalDate.parse(row.date).let {"${it.monthValue}/${it.dayOfMonth}"}}.getOrDefault(row.date)
                Text(date,Modifier.weight(1f),style=quotaTextStyle(9).copy(lineHeight=(9*LocalDesktopTextScale.current).sp),color=colors.onSurfaceVariant,textAlign=when(index){0->androidx.compose.ui.text.style.TextAlign.Start;1->androidx.compose.ui.text.style.TextAlign.Center;else->androidx.compose.ui.text.style.TextAlign.End})
            }
        }
    }
}
