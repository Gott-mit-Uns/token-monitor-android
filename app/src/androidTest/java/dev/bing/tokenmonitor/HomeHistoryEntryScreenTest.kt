package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt

/** Deterministic component/pixel assertions. These advances are not real Window frame evidence. */
class HomeHistoryEntryScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val clock = DesktopAnimationClock { compose.mainClock.currentTime * 1_000_000L }
    private val today = LocalDate.of(2026, 10, 11)
    private val fixtureSavedAt = today.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val samples = Collections.synchronizedList(mutableListOf<HomeHistoryEntryObservation>())
    private var ownerCommits = 0
    private var chartCommits = 0
    private val overflow = AtomicInteger()
    private fun rows(tokens: Long = 100) = HubHistory(List(365) { index ->
        HistoryRow(today.minusDays(364L - index).toString(), tokens, "1")
    }, emptyList())
    private fun projection(tokens: Long = 100) = HomeCharts.project(rows(tokens), null, today)
    private fun observed() = synchronized(samples) { samples.toList() }
    // Advancing a virtual frame runs composition/controller work, but raw observer
    // storage is not a Compose idling API and can still contain the preceding draw.
    private fun last(phase: HomeHistoryEntryObservationPhase): HomeHistoryEntryObservation {
        compose.waitForIdle()
        return observed().last { it.phase == phase }
    }
    private fun entries(): Int {
        compose.waitForIdle()
        return observed().count { it.phase == HomeHistoryEntryObservationPhase.ENTRY_START }
    }
    private fun visibleTrend(): HomeHistoryEntryObservation {
        compose.onNodeWithTag("home-trend-chart").assertIsDisplayed()
        val draw = last(HomeHistoryEntryObservationPhase.TREND_DRAW)
        val state = last(HomeHistoryEntryObservationPhase.STATE)
        assertEquals("An actual visible draw must contain the latest controller pose", state.frame, draw.frame)
        if (state.frame.running) assertEquals("A live draw belongs to the current entrance", state.entrySequence, draw.entrySequence)
        return draw
    }
    private fun trendBitmap(): Pair<PixelMap, HomeHistoryEntryObservation> {
        val pixels = image("home-trend-chart")
        // Bind coordinates to the draw that actually supplied this capture, not
        // a pre-capture observer sample that could predate Android draw sync.
        val draw = visibleTrend()
        assertEquals(pixels.width.toDouble(), draw.chartWidthPx.toDouble(), 1.0)
        assertEquals(pixels.height.toDouble(), draw.chartHeightPx.toDouble(), 1.0)
        return pixels to draw
    }
    private fun image(tag: String) = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
    private fun different(a: Color, b: Color) = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue)) > 3f / 255f
    private fun collect(sample: HomeHistoryEntryObservation) {
        synchronized(samples) { if (samples.size < 8192) samples.add(sample) else overflow.incrementAndGet() }
    }
    @After fun restoreClockAndCheckBoundedObserver() { compose.mainClock.autoAdvance = true; assertEquals(0, overflow.get()) }
    private fun mount(model: State<HomeChartProjection?>, main: State<String> = mutableStateOf("HOME"),
        shown: State<Boolean> = mutableStateOf(true), event: State<Int> = mutableStateOf(0),
        preference: State<String> = mutableStateOf("off"), system: State<Float> = mutableStateOf(1f)) {
        compose.mainClock.autoAdvance = false
        compose.activity.runOnUiThread { compose.activity.setContent {
            TokenTheme(true, LanguageChoice.ENGLISH, AppearancePreferences(reduceMotion = preference.value)) {
                CompositionLocalProvider(LocalDesktopAnimationClock provides clock,
                    LocalDesktopSystemMotionScale provides system.value, LocalHomeHistoryEntryObserver provides ::collect) {
                    val entry = rememberHomeHistoryEntryMotion(model.value, main.value, shown.value, event.value, preference.value)
                    SideEffect { ownerCommits++ }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        if (shown.value && main.value == "HOME") model.value?.let { content ->
                            CompositionLocalProvider(LocalHomeHistoryEntryMotion provides entry) { Charts(content) }
                        }
                    }
                }
            }
        } }
        compose.mainClock.advanceTimeBy(48)
    }
    @Composable private fun Charts(content: HomeChartProjection) {
        SideEffect { chartCommits++ }
        Column(Modifier.width(320.dp).padding(8.dp)) {
            DesktopActivityPreview(content, metricCost = false, compactHome = true)
            DesktopTrendPreview(content)
        }
    }
    private fun expectedVisible(sample: HomeHistoryEntryObservation): List<Int> = (0 until sample.totalColumns).filter { column ->
        val left = column * sample.pitchPx - sample.scrollPx
        left + sample.cellPx > 0f && left < sample.viewportWidthPx
    }

    @Test fun realHeatViewportStaggersOnlyIntersectingColumnsAndActualLineAreaPixelsRevealWithoutRebuildingCache() {
        mount(mutableStateOf(projection()))
        compose.onNodeWithTag("home-activity-viewport").assertIsDisplayed()
        compose.onNodeWithTag("home-trend-chart").assertIsDisplayed()
        val start = last(HomeHistoryEntryObservationPhase.ENTRY_START)
        val heat = last(HomeHistoryEntryObservationPhase.HEAT_DRAW)
        val expected = expectedVisible(heat)
        assertTrue("The narrow real viewport clips the year heatmap", expected.first() > 0)
        assertEquals(expected.first(), start.frame.columns!!.first)
        assertEquals(expected.last(), start.frame.columns!!.last)
        assertEquals(compose.onNodeWithTag("home-activity-viewport").fetchSemanticsNode().boundsInRoot.width.toDouble(),
            heat.viewportWidthPx.toDouble(), 1.0)
        assertEquals(1f, heat.outsideOpacity, 0f)
        val ownerAtStart = ownerCommits; val chartAtStart = chartCommits
        val cacheAtStart = last(HomeHistoryEntryObservationPhase.TREND_CACHE_BUILD)
        compose.mainClock.advanceTimeBy(128)
        val (mid, midTrend) = trendBitmap()
        val midHeatPixels = image("home-activity-viewport")
        val midHeat = last(HomeHistoryEntryObservationPhase.HEAT_DRAW)
        assertTrue(midHeat.firstOpacity > 0f && midHeat.firstOpacity < 1f)
        assertEquals("Last visible column waits through its real stagger", 0f, midHeat.lastOpacity, 0f)
        assertEquals("Offscreen source cells stay fully painted", 1f, midHeat.outsideOpacity, 0f)
        assertTrue(midTrend.frame.trendReveal > 0f && midTrend.frame.trendReveal < 1f)
        assertEquals(midTrend.sourceStrokeLength * midTrend.frame.trendReveal, midTrend.drawnStrokeLength, .001f)
        assertEquals(midTrend.chartWidthPx * midTrend.frame.trendReveal, midTrend.areaClipRightPx, .001f)
        repeat(12) { compose.mainClock.advanceTimeBy(32); image("home-trend-chart") }
        assertEquals("Owner composition does not subscribe to display frames", ownerAtStart, ownerCommits)
        assertEquals("Chart composition does not subscribe to display frames", chartAtStart, chartCommits)
        assertTrue(observed().filter { it.phase == HomeHistoryEntryObservationPhase.TREND_DRAW &&
            it.cacheIdentity == cacheAtStart.cacheIdentity }.all { it.cacheGeneration == cacheAtStart.cacheGeneration })
        compose.mainClock.advanceTimeBy(640)
        val (full, finalTrend) = trendBitmap()
        val fullHeatPixels = image("home-activity-viewport")
        val yArea = (full.height * .5f).roundToInt().coerceIn(0, full.height - 1)
        val xEarly = (midTrend.areaClipRightPx * .5f).roundToInt().coerceIn(1, full.width - 2)
        val xLate = ((midTrend.areaClipRightPx + full.width) * .5f).roundToInt().coerceIn(1, full.width - 2)
        val background = full[full.width - 1, yArea] // Source path ends at x=297 of its 300-unit viewport.
        assertTrue("The complete public fixture really paints filled area at this sample", different(full[xLate, yArea], background))
        assertFalse("The captured intermediate right side is actual unpainted background", different(mid[xLate, yArea], background))
        assertFalse("Already revealed area has its final pixels", different(mid[xEarly, yArea], full[xEarly, yArea]))
        assertTrue("The right-side filled area is absent in the actual intermediate bitmap", different(mid[xLate, yArea], full[xLate, yArea]))
        val yLine = (4f / 70f * full.height).roundToInt().coerceIn(0, full.height - 1)
        assertTrue("The right-side stroke is absent in the actual intermediate bitmap", different(mid[xLate, yLine], full[xLate, yLine]))
        val fullyInsideLast = expected.last { column -> column < heat.totalColumns - 1 &&
            column * heat.pitchPx - heat.scrollPx + heat.cellPx * .5f in 0f..(heat.viewportWidthPx - 1f) }
        val xCell = (fullyInsideLast * heat.pitchPx - heat.scrollPx + heat.cellPx * .5f).roundToInt()
        val yCell = (3f * heat.pitchPx + heat.cellPx * .5f).roundToInt()
        assertTrue("A delayed heat cell changes the actual viewport pixels", different(midHeatPixels[xCell, yCell], fullHeatPixels[xCell, yCell]))
        assertFalse(last(HomeHistoryEntryObservationPhase.STATE).frame.running)
        assertEquals(1f, finalTrend.frame.trendReveal, 0f)
    }

    @Test fun actualComponentsWaitForDataThenCancelForSettingsContentPeriodAndStopWithoutReplay() {
        val model = mutableStateOf<HomeChartProjection?>(null)
        val main = mutableStateOf("HOME"); val shown = mutableStateOf(true); val event = mutableStateOf(0)
        mount(model, main, shown, event)
        assertEquals(0, entries())
        // Regression for the separate production bug: an empty Home must not
        // consume pending simply by being shown before Settings receives data.
        compose.runOnIdle { shown.value = false }; compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { model.value = projection() }; compose.mainClock.advanceTimeBy(32)
        assertEquals("First data hidden by Settings has no real viewport ready callback", 0, entries())
        compose.onNodeWithTag("home-trend-chart").assertDoesNotExist()
        compose.runOnIdle { shown.value = true }; compose.mainClock.advanceTimeBy(48)
        assertEquals(1, entries())
        assertTrue("The original pending entrance begins at the first actual visible chart", visibleTrend().frame.running)
        compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle { shown.value = false }; compose.mainClock.advanceTimeBy(32)
        val hiddenState = last(HomeHistoryEntryObservationPhase.STATE)
        assertFalse(hiddenState.visible); assertFalse(hiddenState.frame.running)
        assertEquals("Hidden cancellation settles the controller, not a nonexistent chart draw", 1f, hiddenState.frame.trendReveal, 0f)
        compose.onNodeWithTag("home-trend-chart").assertDoesNotExist()
        compose.runOnIdle { shown.value = true }; compose.mainClock.advanceTimeBy(48)
        assertEquals(1, entries()); assertEquals(1f, visibleTrend().frame.trendReveal, 0f)
        fun roundTrip() {
            compose.runOnIdle { main.value = "TOOLS" }; compose.mainClock.advanceTimeBy(32)
            compose.runOnIdle { main.value = "HOME" }; compose.mainClock.advanceTimeBy(48)
        }
        roundTrip(); assertEquals(2, entries()); compose.mainClock.advanceTimeBy(128)
        val oldCache = last(HomeHistoryEntryObservationPhase.TREND_CACHE_BUILD)
        compose.runOnIdle { model.value = projection(200) }; compose.mainClock.advanceTimeBy(32)
        assertEquals(2, entries()); assertFalse(last(HomeHistoryEntryObservationPhase.STATE).frame.running)
        assertTrue("Replacing content invalidates cached paths", last(HomeHistoryEntryObservationPhase.TREND_CACHE_BUILD).cacheGeneration > oldCache.cacheGeneration)
        roundTrip(); assertEquals(3, entries()); compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle { event.value++ }; compose.mainClock.advanceTimeBy(32)
        assertEquals(3, entries()); assertFalse(last(HomeHistoryEntryObservationPhase.STATE).frame.running)
        roundTrip(); assertEquals(4, entries()); compose.mainClock.advanceTimeBy(128)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertTrue(observed().any { it.phase == HomeHistoryEntryObservationPhase.STOP && !it.frame.running })
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED); compose.mainClock.advanceTimeBy(48)
        assertEquals(4, entries()); assertEquals(1f, visibleTrend().frame.trendReveal, 0f)
    }

    @Test fun componentsRespectAllThreeReductionChoicesAndDoNotReplayWhenReductionChanges() {
        val preference = mutableStateOf("on"); val system = mutableStateOf(1f); val main = mutableStateOf("HOME")
        mount(mutableStateOf(projection()), main = main, preference = preference, system = system)
        assertEquals(1, entries()); assertFalse(last(HomeHistoryEntryObservationPhase.STATE).frame.running)
        assertEquals(1f, visibleTrend().frame.trendReveal, 0f)
        assertTrue(observed().filter { it.phase == HomeHistoryEntryObservationPhase.TREND_DRAW }.all { it.frame.trendReveal == 1f })
        compose.runOnIdle { preference.value = "off"; system.value = 0f }; compose.mainClock.advanceTimeBy(32)
        assertEquals(1, entries())
        compose.runOnIdle { main.value = "TOOLS" }; compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { main.value = "HOME" }; compose.mainClock.advanceTimeBy(48)
        assertEquals(2, entries()); assertTrue(visibleTrend().frame.trendReveal < 1f)
        compose.runOnIdle { preference.value = "system" }; compose.mainClock.advanceTimeBy(32)
        assertFalse(last(HomeHistoryEntryObservationPhase.STATE).frame.running)
        assertEquals(1f, visibleTrend().frame.trendReveal, 0f)
    }

    private class Actions : HubUiActions {
        var calls = 0
        override fun connect(address: String, credential: String, alternate: String) { calls++ }
        override fun refreshSaved() { calls++ }
        override fun switchEndpoint() { calls++ }
        override fun hasAlternate() = false
        override fun endpointType() = "https"
        override fun loadHistory() { calls++ }
        override fun loadDeviceHistory() { calls++ }
        override fun loadModelNames() { calls++ }
        override fun loadSubscriptions() { calls++ }
        override fun forget() { calls++ }
        override fun renameDevice(id: String, input: String) { calls++ }
    }
    @Test fun actualHubViewSelectionReplaysOnlyRealMainViewChangesAndUnchangedFreshnessKeepsEntry() {
        val history = rows()
        val snapshot = HubParser.parse("""{"periods":{"today":{"totalTokens":100,"costUsd":1},"month":{"totalTokens":100,"costUsd":1},"allTime":{"totalTokens":100,"costUsd":1}}}""")
        val state = mutableStateOf(HubState(snapshot = snapshot, savedAt = fixtureSavedAt, history = history, modelNames = ModelNames(emptyMap(), "off"), subscriptions = emptyList()))
        val actions = Actions()
        val hubCommits = AtomicInteger()
        val prefs = DisplayPreferences(language = LanguageChoice.ENGLISH, homeModules = listOf("activity"), heatmapMetric = "tokens", appearance = AppearancePreferences(reduceMotion = "off"))
        compose.mainClock.autoAdvance = false
        compose.activity.runOnUiThread { compose.activity.setContent {
            CompositionLocalProvider(LocalHubCalendarClock provides Clock.fixed(Instant.parse("2026-10-11T08:00:00Z"), ZoneId.of("Asia/Shanghai")),
                LocalDesktopAnimationClock provides clock, LocalHomeHistoryEntryObserver provides ::collect,
                LocalHeadlineRenderObserver provides { part -> if (part == "hub") hubCommits.incrementAndGet() }) {
                HubScreen(state.value, actions, preferences = prefs)
            }
        } }
        compose.mainClock.advanceTimeBy(48); compose.onNodeWithTag("home-trend-chart").assertIsDisplayed()
        val initial = entries(); assertEquals(1, initial)
        compose.mainClock.advanceTimeBy(128)
        val before = visibleTrend()
        compose.runOnIdle { state.value = state.value.copy(savedAt = fixtureSavedAt + 1_000L, live = true) }
        compose.mainClock.advanceTimeBy(32)
        assertEquals(initial, entries())
        val after = visibleTrend()
        assertEquals("Unchanged business content retains its projection", before.contentIdentity, after.contentIdentity)
        assertTrue("Header freshness does not truncate the 920ms entry", after.frame.running && after.frame.trendReveal > before.frame.trendReveal)
        val committed = hubCommits.get()
        repeat(4) { compose.mainClock.advanceTimeBy(32); image("home-trend-chart") }
        assertEquals("Home entry draw frames do not recompose the actual Hub owner", committed, hubCommits.get())
        compose.mainClock.advanceTimeBy(1000)
        fun choose(view: String) {
            compose.mainClock.autoAdvance = true
            compose.onNodeWithTag("hub-view-selector").performClick()
            val item = compose.onNodeWithTag("view-$view").assertIsDisplayed()
            compose.mainClock.autoAdvance = false
            item.performClick(); compose.mainClock.advanceTimeBy(48)
        }
        choose("home"); assertEquals(initial, entries())
        choose("tools"); choose("home"); assertEquals(initial + 1, entries())
        assertTrue("The actual new Home chart paints a live entrance", visibleTrend().frame.running)
        compose.onNodeWithTag("hub-settings-toggle").performClick(); compose.mainClock.advanceTimeBy(240)
        assertFalse(last(HomeHistoryEntryObservationPhase.STATE).frame.running)
        compose.onNodeWithTag("hub-settings-toggle").performClick(); compose.mainClock.advanceTimeBy(240)
        assertEquals(initial + 1, entries()); assertEquals(1f, visibleTrend().frame.trendReveal, 0f)
        assertEquals(0, actions.calls)
        assertEquals(100L, state.value.snapshot!!.periods.getValue("today").tokens)
    }
}
