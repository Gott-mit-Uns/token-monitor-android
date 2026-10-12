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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** Actual Dashboard renderer pixels. Root HubScreen journeys separately verify navigation and Hub reads. */
class DashboardChartMotionScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val barColor = Color(0xFF2060A0)
    private lateinit var observed: State<DashboardChartFrame>
    private var ownerCommits = 0
    private var chartCommits = 0
    private var density = 1f
    private fun barModel(first: Long = 100) = DashboardTrendProjection.project(HubHistory(listOf(
        HistoryRow("2026-10-08", first, null, perClient = mapOf("codex" to HistoryAttribution(first, null))),
        HistoryRow("2026-10-09", 100, null, perClient = mapOf("codex" to HistoryAttribution(100, null)))), emptyList()),
        range = "all", width = 320.0, height = 200.0)
    private fun candleModel() = DashboardTrendProjection.project(HubHistory(listOf(
        HistoryRow("2026-10-01", 4, null), HistoryRow("2026-10-02", 10, null),
        HistoryRow("2026-10-03", 2, null), HistoryRow("2026-10-12", 7, null)), emptyList()),
        range = "all", mode = DashboardTrendMode.KLINE, width = 320.0, height = 200.0)

    private fun host(model: State<DashboardTrendModel>, event: State<Int>, revision: State<Int>,
        reason: State<DashboardMotionReason>, preference: State<String>, shown: State<Boolean>,
        palette: State<Map<String, Long>>? = null, chartWidth: State<Dp>? = null, chartHeight: State<Dp>? = null) {
        val animationClock = DesktopAnimationClock { compose.mainClock.currentTime * 1_000_000L }
        compose.mainClock.autoAdvance = false
        compose.activity.runOnUiThread { compose.activity.setContent {
            TokenTheme(true, LanguageChoice.ENGLISH, AppearancePreferences(colors = mapOf("bg" to "#0b0f14"), reduceMotion = preference.value)) {
                CompositionLocalProvider(LocalDesktopAnimationClock provides animationClock,
                    LocalDesktopSystemMotionScale provides 1f,
                    LocalDesktopVendorPalette provides (palette?.value ?: (desktopVendorColors + ("codex" to 0xFF2060A0L)))) {
                    density = LocalDensity.current.density
                    val motion = rememberDashboardChartMotion(model.value, event.value, revision.value,
                        reason.value, shown.value, preference.value)
                    SideEffect { observed = motion; ownerCommits++ }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize()) { FixtureChart(model.value, motion,
                            chartWidth?.value ?: 320.dp, chartHeight?.value ?: 200.dp) }
                    }
                }
            }
        } }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("dashboard-chart").assertIsDisplayed()
    }

    @Composable private fun FixtureChart(model: DashboardTrendModel, motion: State<DashboardChartFrame>,
        width: Dp = 320.dp, height: Dp = 200.dp) {
        SideEffect { chartCommits++ }
        DesktopDashboardChart(model, Modifier.width(width).height(height), onBar = { _, _ -> }, onCandle = {}, motion = motion)
    }
    private fun frame(): DashboardChartFrame {
        lateinit var result: DashboardChartFrame
        compose.runOnIdle { result = observed.value }
        return result
    }
    private fun image() = compose.onNodeWithTag("dashboard-chart").captureToImage().toPixelMap()
    private fun rows(image: PixelMap, model: DashboardTrendModel, x: Double, color: Color): Int {
        val column = (x / model.width * image.width).roundToInt().coerceIn(0, image.width - 1)
        return (0 until image.height).count { y ->
            val actual = image[column, y]
            listOf(actual.red - color.red, actual.green - color.green, actual.blue - color.blue).all { abs(it * 255) <= 2 }
        }
    }
    private fun barRows(image: PixelMap, model: DashboardTrendModel, index: Int = 0): Int =
        rows(image, model, model.bars[index].x + model.bars[index].width / 2, barColor)

    // Public 64-day / 80-distinct-colour fixture: 5,120 positive paths exercises the
    // dense renderer, while one unchanged column is its ordinary vector reference.
    private fun denseModel(firstPercent: Long = 100): DashboardTrendModel {
        val end = LocalDate.of(2026, 10, 11)
        val daily = (0 until 64).map { day ->
            val groups = (0 until 80).associate { key ->
                val value = (4L + key % 5) * 1_000 * (if (day == 0) firstPercent else 100) / 100
                "public-client-$key" to HistoryAttribution(value, null)
            }
            HistoryRow(end.minusDays(63L - day).toString(), groups.values.sumOf { it.tokens!! }, null, perClient = groups)
        }
        return DashboardTrendProjection.project(HubHistory(daily, emptyList()), range = "all", width = 320.0, height = 200.0)
    }
    private fun densePalette(rotation: Float = 0f): Map<String, Long> = desktopVendorColors +
        (0 until 80).associate { key -> "public-client-$key" to
            (android.graphics.Color.HSVToColor(if (key == 0) 184 else 255,
                floatArrayOf((key * 4.5f + rotation) % 360f, .78f, .85f)).toLong() and 0xffffffffL) }
    private fun at(image: PixelMap, model: DashboardTrendModel, x: Double, y: Double): Color =
        image[(x / model.width * image.width).roundToInt().coerceIn(0, image.width - 1),
            (y / model.height * image.height).roundToInt().coerceIn(0, image.height - 1)]
    private fun assertColumnMatchesVector(reference: PixelMap, dense: PixelMap, model: DashboardTrendModel) {
        assertEquals(reference.width, dense.width); assertEquals(reference.height, dense.height)
        val bar = model.bars.first()
        bar.segments.forEach { segment ->
            val expected = at(reference, model, segment.x + segment.width / 2, segment.y + segment.height / 2)
            val actual = at(dense, model, segment.x + segment.width / 2, segment.y + segment.height / 2)
            listOf(actual.red - expected.red, actual.green - expected.green, actual.blue - expected.blue).forEach {
                assertEquals("Dense ${segment.key} retains vector colour, alpha and source pixel position", 0.0, it.toDouble(), 3.0 / 255)
            }
        }
        // An independently padded strip must not paint across the actual source gap.
        val next = model.bars[1]
        val gapX = (bar.x + bar.width + next.x) / 2
        val expectedGap = at(reference, model, gapX, model.plot.y + model.plot.height * .5)
        val actualGap = at(dense, model, gapX, model.plot.y + model.plot.height * .5)
        assertEquals(expectedGap.red.toDouble(), actualGap.red.toDouble(), 2.0 / 255)
        assertEquals(expectedGap.green.toDouble(), actualGap.green.toDouble(), 2.0 / 255)
        assertEquals(expectedGap.blue.toDouble(), actualGap.blue.toDouble(), 2.0 / 255)
    }
    private fun colouredExtent(image: PixelMap, model: DashboardTrendModel): Int {
        val bar = model.bars.first()
        val x = ((bar.x + bar.width / 2) / model.width * image.width).roundToInt().coerceIn(0, image.width - 1)
        val first = (model.plot.y / model.height * image.height).toInt().coerceIn(0, image.height - 1)
        val last = ((model.plot.y + model.plot.height) / model.height * image.height).toInt().coerceIn(first, image.height - 1)
        val coloured = (first..last).filter { y ->
            val color = image[x, y]
            maxOf(color.red, color.green, color.blue) - minOf(color.red, color.green, color.blue) > .18f
        }
        return if (coloured.isEmpty()) 0 else coloured.last() - coloured.first() + 1
    }

    @Test fun denseMulticolourPixelsMatchVectorAndInvalidateForPaletteSizeAndInterruptedMotion() {
        val fullModel = denseModel()
        assertEquals(5_120, fullModel.bars.sumOf { it.segments.size })
        val model = mutableStateOf(fullModel.copy(bars = listOf(fullModel.bars.first())))
        val event = mutableStateOf(1); val revision = mutableStateOf(1)
        val reason = mutableStateOf(DashboardMotionReason.ENTRY)
        val preference = mutableStateOf("on"); val shown = mutableStateOf(true)
        val palette = mutableStateOf(densePalette())
        val width = mutableStateOf(320.dp); val height = mutableStateOf(200.dp)
        host(model, event, revision, reason, preference, shown, palette, width, height)
        try {
            val vector = image()
            compose.runOnIdle { model.value = fullModel; event.value++ }
            compose.mainClock.advanceTimeBy(32)
            val original = image()
            assertColumnMatchesVector(vector, original, fullModel)
            val sampled = fullModel.bars.first().segments.map { segment ->
                val color = at(original, fullModel, segment.x + segment.width / 2, segment.y + segment.height / 2)
                Triple((color.red * 255).roundToInt(), (color.green * 255).roundToInt(), (color.blue * 255).roundToInt())
            }.toSet()
            assertTrue("The real dense image contains many different colours, not a merged fallback colour", sampled.size >= 60)

            compose.runOnIdle { palette.value = densePalette(45f) }
            compose.mainClock.advanceTimeBy(32)
            val changed = image()
            val segment = fullModel.bars.first().segments[30]
            val oldColor = at(original, fullModel, segment.x + segment.width / 2, segment.y + segment.height / 2)
            val newColor = at(changed, fullModel, segment.x + segment.width / 2, segment.y + segment.height / 2)
            assertTrue("Changing the palette changes already rasterised pixels", abs(oldColor.red - newColor.red) +
                abs(oldColor.green - newColor.green) + abs(oldColor.blue - newColor.blue) > .25f)
            compose.runOnIdle { model.value = fullModel.copy(bars = listOf(fullModel.bars.first())); event.value++ }
            compose.mainClock.advanceTimeBy(32)
            assertColumnMatchesVector(image(), changed, fullModel)

            compose.runOnIdle { width.value = 288.dp; height.value = 240.dp }
            compose.mainClock.advanceTimeBy(32)
            val resizedVector = image()
            assertTrue(resizedVector.width < original.width && resizedVector.height > original.height)
            compose.runOnIdle { model.value = fullModel; event.value++ }
            compose.mainClock.advanceTimeBy(32)
            val resizedDense = image()
            assertColumnMatchesVector(resizedVector, resizedDense, fullModel)
            val full = colouredExtent(resizedDense, fullModel)
            assertTrue(full > resizedDense.height / 2)

            compose.runOnIdle { preference.value = "off"; revision.value++ }
            compose.mainClock.advanceTimeBy(64)
            assertTrue("Dense source entry draws intermediate pixels", colouredExtent(image(), fullModel) in 1 until full)
            val ownerBefore = ownerCommits; val chartBefore = chartCommits
            compose.mainClock.advanceTimeBy(1000)
            assertFalse(frame().running)
            assertEquals(full, colouredExtent(image(), fullModel))
            assertEquals(ownerBefore, ownerCommits)
            assertEquals("Dense per-frame transforms remain in Canvas drawing", chartBefore, chartCommits)

            compose.runOnIdle { model.value = denseModel(50); event.value++ }
            compose.mainClock.advanceTimeBy(160)
            val shrinking = colouredExtent(image(), model.value)
            assertTrue(shrinking > full / 2 && shrinking < full)
            compose.runOnIdle { model.value = denseModel(80); event.value++ }
            compose.mainClock.advanceTimeByFrame()
            val continued = colouredExtent(image(), model.value)
            assertTrue(continued > full / 2)
            assertTrue("A new dense atlas continues the displayed FLIP geometry", abs(continued - shrinking) < 12 * density)
            compose.runOnIdle { preference.value = "on" }
            compose.mainClock.advanceTimeByFrame()
            assertFalse("Reduced motion settles the dense atlas to the exact target", frame().running)
            val settled = image()
            assertEquals(full * .8, colouredExtent(settled, model.value).toDouble(), 3 * density.toDouble())
            val finalModel = model.value
            compose.runOnIdle { model.value = finalModel.copy(bars = listOf(finalModel.bars.first())); event.value++ }
            compose.mainClock.advanceTimeBy(32)
            assertColumnMatchesVector(image(), settled, finalModel)
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun barsEnterAndRetargetActualStackPixelsWithoutPerFrameOwnerOrChartComposition() {
        val model = mutableStateOf(barModel())
        val event = mutableStateOf(1)
        val revision = mutableStateOf(1)
        val reason = mutableStateOf(DashboardMotionReason.ENTRY)
        val preference = mutableStateOf("on")
        val shown = mutableStateOf(true)
        host(model, event, revision, reason, preference, shown)
        try {
            val full = barRows(image(), model.value)
            assertTrue("Actual source-coloured bar fills the plot", full > 100)
            compose.runOnIdle { preference.value = "off"; revision.value++ }
            compose.mainClock.advanceTimeBy(64)
            val entry = barRows(image(), model.value)
            assertTrue("800ms source entry changes real Canvas pixels", entry in 1 until full)
            val ownerBefore = ownerCommits
            val chartBefore = chartCommits
            compose.mainClock.advanceTimeBy(1000)
            assertEquals(full, barRows(image(), model.value))
            assertFalse(frame().running)
            assertEquals(ownerBefore, ownerCommits)
            assertEquals("Frame state is read only in Canvas draw", chartBefore, chartCommits)

            compose.runOnIdle { model.value = barModel(50); event.value++ }
            compose.mainClock.advanceTimeBy(160)
            val shrinking = barRows(image(), model.value)
            assertTrue("Existing stack shrinks from captured displayed bounds", shrinking > full / 2 && shrinking < full)
            compose.runOnIdle { model.value = barModel(80); event.value++ }
            compose.mainClock.advanceTimeByFrame()
            val interrupted = barRows(image(), model.value)
            assertTrue("New data does not collapse an existing stack", interrupted > full / 2)
            assertTrue("Interrupted geometry continues near the displayed height", abs(interrupted - shrinking) < 12 * density)
            compose.mainClock.advanceTimeBy(900)
            assertEquals(full * .8, barRows(image(), model.value).toDouble(), 3 * density.toDouble())
            assertFalse(frame().running)

            compose.runOnIdle { revision.value++; reason.value = DashboardMotionReason.ENTRY }
            compose.mainClock.advanceTimeBy(64)
            assertTrue(frame().running)
            // Same data/revision, changed geometry: the source resize branch settles and does not replay.
            compose.runOnIdle { model.value = model.value.copy(width = 321.0); reason.value = DashboardMotionReason.NONE }
            compose.mainClock.advanceTimeByFrame()
            assertFalse(frame().running)
            assertEquals(1.0, frame().bars.getValue("2026-10-08").scaleY, 0.0)
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun candleBodyAndTwoWicksRenderProgressReplayOnUpdateAndSettleForReductionAndStop() {
        val model = mutableStateOf(candleModel())
        val event = mutableStateOf(1)
        val revision = mutableStateOf(1)
        val reason = mutableStateOf(DashboardMotionReason.ENTRY)
        val preference = mutableStateOf("on")
        val shown = mutableStateOf(true)
        host(model, event, revision, reason, preference, shown)
        try {
            val candle = model.value.candles.first()
            val color = Color(candle.colorArgb!!)
            val bodyX = candle.x + candle.width * .25
            val fullImage = image()
            val fullBody = rows(fullImage, model.value, bodyX, color)
            val fullWick = rows(fullImage, model.value, candle.wickX, color)
            assertTrue(fullBody > 20)
            assertTrue("Fixture has a high wick outside the body", fullWick > fullBody * 2)
            compose.runOnIdle { preference.value = "off"; revision.value++ }
            compose.mainClock.advanceTimeBy(64)
            val entering = image()
            assertTrue(rows(entering, model.value, bodyX, color) in 1 until fullBody)
            assertTrue(rows(entering, model.value, candle.wickX, color) in 1 until fullWick)
            val ownerBefore = ownerCommits
            val chartBefore = chartCommits
            compose.mainClock.advanceTimeBy(800)
            assertEquals(fullBody, rows(image(), model.value, bodyX, color))
            assertFalse(frame().running)
            assertEquals(ownerBefore, ownerCommits)
            assertEquals(chartBefore, chartCommits)

            compose.runOnIdle { event.value++ }
            compose.mainClock.advanceTimeBy(64)
            assertTrue("K-line source update replays even with the same endpoints", frame().running)
            assertTrue(rows(image(), model.value, bodyX, color) < fullBody)
            compose.runOnIdle { preference.value = "on" }
            compose.mainClock.advanceTimeByFrame()
            assertFalse(frame().running)
            assertEquals(fullBody, rows(image(), model.value, bodyX, color))

            compose.runOnIdle { preference.value = "off"; event.value++ }
            compose.mainClock.advanceTimeBy(64)
            assertTrue(frame().running)
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.mainClock.advanceTimeBy(100)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.mainClock.advanceTimeBy(32)
            assertFalse("Android STOP settles; return does not replay a consumed entry", frame().running)
            assertEquals(fullBody, rows(image(), model.value, bodyX, color))
        } finally { compose.mainClock.autoAdvance = true }
    }
}
