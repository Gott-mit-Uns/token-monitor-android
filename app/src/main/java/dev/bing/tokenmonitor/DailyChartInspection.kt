package dev.bing.tokenmonitor

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.text.NumberFormat

/** Date identity survives in-place snapshot updates; missing dates are never coerced to zero. */
internal fun dailyChartIndex(x: Float, left: Float, width: Float, count: Int, clamp: Boolean): Int? {
    if (count <= 0 || width <= 0 || !x.isFinite()) return null
    if (!clamp && (x < left || x > left + width)) return null
    return (((x - left) / width * count).toInt()).coerceIn(0, count - 1)
}

internal class DailyChartInspection {
    var date by mutableStateOf<String?>(null)
    var bounds: Rect? = null
    var cardBounds: Rect? = null
    fun dismiss() { date = null }
}
private val LocalInspectionRegistry = staticCompositionLocalOf<MutableSet<DailyChartInspection>?> { null }

/** Observes outside touches without consuming them: tabs, scrolling and toolbar actions remain usable. */
@Composable internal fun ChartInspectionHost(content: @Composable () -> Unit) {
    val registry = remember { mutableSetOf<DailyChartInspection>() }
    val origin = remember { arrayOf(Offset.Zero) }
    Box(Modifier.onGloballyPositioned { origin[0] = it.localToRoot(Offset.Zero) }.pointerInput(registry) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.firstOrNull { it.changedToDownIgnoreConsumed() }?.let { change ->
                    val point = change.position + origin[0]
                    registry.forEach { state ->
                        if (state.date != null && state.bounds?.contains(point) != true && state.cardBounds?.contains(point) != true) state.dismiss()
                    }
                }
            }
        }
    }) { CompositionLocalProvider(LocalInspectionRegistry provides registry, content = content) }
}

@Composable internal fun rememberDailyChartInspection(dates: List<String>, resetKey: Any): DailyChartInspection {
    val state = remember(resetKey) { DailyChartInspection() }
    val registry = LocalInspectionRegistry.current
    DisposableEffect(state, registry) {
        registry?.add(state)
        onDispose { registry?.remove(state); state.dismiss() }
    }
    SideEffect { if (state.date != null && state.date !in dates) state.dismiss() }
    return state
}

/** No event is consumed before a successful long press. A short tap retains its existing callback.
 * Inputs are remembered-updated by callers; snapshots do not cancel an active drag. */
internal fun Modifier.dailyChartGestures(
    state: DailyChartInspection,
    hit: (Offset, Int, Int, Boolean) -> String?,
    tap: (Offset, Int, Int) -> Unit
): Modifier = composed {
    val latestHit by rememberUpdatedState(hit)
    val latestTap by rememberUpdatedState(tap)
    onGloballyPositioned { state.bounds = it.boundsInRoot() }.pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown()
            var released: PointerInputChange? = null
            var canceled = false
            var last = down
            val completed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null) { canceled = true; break }
                    last = change
                    if (change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        canceled = true
                        break
                    }
                    if (!change.pressed) { released = change; break }
                }
                true
            }
            if (completed != null) {
                if (!canceled && released != null) {
                    val point = released!!.position
                    val date = latestHit(point, size.width, size.height, false)
                    if (state.date != null && date == state.date) state.dismiss()
                    else { state.dismiss(); latestTap(point, size.width, size.height) }
                    released!!.consume()
                }
            } else {
                val date = latestHit(last.position, size.width, size.height, false)
                if (date != null) {
                    state.date = date
                    last.consume()
                    drag(last.id) { change ->
                        state.date = latestHit(change.position, size.width, size.height, true)
                        change.consume()
                    }
                    currentEvent.changes.forEach { if (!it.pressed) it.consume() }
                }
            }
        }
    }
}

/** A bounded, scrollable surface leaves the chart and toolbar available at large font scales. */
@Composable internal fun DailyChartInspectionCard(
    state: DailyChartInspection, row: HistoryRow, byModel: Boolean, tag: String,
    attributed: DashboardTrendBar? = null
) {
    val strings = LocalResources.current
    val numbers = NumberFormat.getIntegerInstance(strings.configuration.locales[0])
    Surface(Modifier.widthIn(max = 240.dp).heightIn(max = 140.dp).testTag(tag)
        .onGloballyPositioned { state.cardBounds = it.boundsInRoot() },
        shape = MaterialTheme.shapes.small, tonalElevation = 3.dp, shadowElevation = 2.dp) {
        Column(Modifier.padding(8.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(row.date, Modifier.weight(1f), style = quotaDataTextStyle(12))
                DesktopTextButton(state::dismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(strings.getString(R.string.close)) }
            }
            Text(strings.getString(R.string.dashboard_recorded_tokens) + ": " + (row.tokens?.let(numbers::format) ?: "—"), style = quotaDataTextStyle(12))
            Text(strings.getString(R.string.known_cost_formatted, usageCostText(row.cost, row.unpricedTokens)), style = quotaTextStyle(12))
            row.unpricedTokens?.takeIf { it > 0 }?.let { Text(strings.getString(R.string.unpriced_tokens, numbers.format(it)), style = quotaTextStyle(11)) }
            if (attributed != null) {
                Text(strings.getString(R.string.dashboard_attributed_tokens) + ": " + if (attributed.duplicate || row.tokens == null) "—" else numbers.format(attributed.total), style = quotaDataTextStyle(12))
                attributed.segments.sortedByDescending { it.value }.forEach { Text(it.key + ": " + if (it.known) numbers.format(it.value) else "—", style = quotaTextStyle(12)) }
                if (!attributed.attributionComplete || attributed.duplicate) Text(strings.getString(R.string.dashboard_chart_unknown), style = quotaTextStyle(11))
            } else {
                (if (byModel) row.perModel else row.perClient).toList().sortedByDescending { it.second.tokens }.forEach { (name, value) ->
                    Text((if (byModel) name else toolLabel(name)) + ": " + (value.tokens?.let(numbers::format) ?: "—"), style = quotaTextStyle(12))
                }
            }
            if (row.tokens == null) Text(strings.getString(R.string.history_gap_note), style = quotaTextStyle(11))
        }
    }
}
