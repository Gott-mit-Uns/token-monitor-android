package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
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
import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.roundToInt

/** Real Canvas pixels and commit scopes; root HubScreen tests cover navigation and read counts. */
class TrendsBarMotionScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val chart = Color(0xFF2060A0)
    private val background = Color(0xFF0B0F14)
    private val stats = TrendsMainStats(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO)
    private lateinit var observed: State<TrendsBarFrame>
    private var ownerCommits = 0
    private var chartCommits = 0
    private var density = 1f

    private fun scene(vararg points: Pair<String, Long>) = TrendsMainModel(
        points.map { TrendsMainPoint(it.first, it.second) }, TrendsMainRange.WEEK, stats, true)

    private fun host(
        model: State<TrendsMainModel>,
        event: State<Int>,
        preference: State<String>,
        view: State<String> = mutableStateOf("TRENDS"),
        shown: State<Boolean> = mutableStateOf(true),
        height: State<Dp> = mutableStateOf(360.dp),
        textSize: State<String> = mutableStateOf("standard")
    ) {
        val animationClock = DesktopAnimationClock { compose.mainClock.currentTime * 1_000_000L }
        compose.mainClock.autoAdvance = false
        compose.activity.runOnUiThread { compose.activity.setContent {
            TokenTheme(true, LanguageChoice.ENGLISH, AppearancePreferences(
                colors = mapOf("bg" to "#0b0f14", "chart" to "#2060a0"), reduceMotion = preference.value, textSize=textSize.value)) {
                CompositionLocalProvider(LocalDesktopAnimationClock provides animationClock,
                    LocalDesktopSystemMotionScale provides 1f) {
                    density = LocalDensity.current.density
                    val motion = rememberTrendsBarMotion(model.value.points, event.value, view.value, shown.value, preference.value)
                    SideEffect { observed = motion; ownerCommits++ }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                                FixtureChart(model.value, height.value, motion)
                            }
                        }
                    }
                }
            }
        } }
        // Manual clocks do not run the first composition while waitForIdle waits.
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
    }

    @Composable private fun FixtureChart(model: TrendsMainModel, height: Dp, motion: State<TrendsBarFrame>) {
        SideEffect { chartCommits++ }
        DesktopTrendsMain(model, {}, availableHeight = height, barMotion = motion)
    }

    private fun frame(): TrendsBarFrame {
        lateinit var result: TrendsBarFrame
        compose.runOnIdle { result = observed.value }
        return result
    }

    private fun image(): PixelMap = compose.onNodeWithTag("trends-main-chart").performScrollTo()
        .assertIsDisplayed().captureToImage().toPixelMap()

    private fun near(actual: Color, expected: Color) = listOf(
        actual.red - expected.red, actual.green - expected.green, actual.blue - expected.blue
    ).all { abs(it * 255) <= 2 }

    private fun rows(image: PixelMap, index: Int, count: Int, alpha: Float): Int {
        val inset = (4 * density).roundToInt()
        val x = (inset + (image.width - inset * 2) * (index + .5f) / count)
            .roundToInt().coerceIn(0, image.width - 1)
        val colour = chart.copy(alpha = alpha).compositeOver(background)
        return (0 until image.height).count { y -> near(image[x, y], colour) }
    }

    private fun column(image: PixelMap, index: Int, count: Int): List<Int> {
        val inset = (4 * density).roundToInt()
        val x = (inset + (image.width - inset * 2) * (index + .5f) / count)
            .roundToInt().coerceIn(0, image.width - 1)
        return (0 until image.height).map { y -> image[x, y].toArgb() }
    }

    @Test fun actualEntryAndInterruptedDataPixelsMoveWithoutPerFrameComposition() {
        val model = mutableStateOf(scene("zero" to 0, "day" to 100, "last" to 100))
        val event = mutableStateOf(1)
        val preference = mutableStateOf("on")
        val view = mutableStateOf("TRENDS")
        val shown = mutableStateOf(true)
        host(model, event, preference, view, shown)
        val static = image()
        val full = rows(static, 1, 3, .5f)
        assertTrue("Static fixture has a full-height source-coloured bar", full > 30)
        val zero = column(static, 0, 3)

        compose.runOnIdle { preference.value = "off"; view.value = "TOOLS"; shown.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { view.value = "TRENDS"; shown.value = true }
        compose.mainClock.advanceTimeBy(64)
        val entry = image()
        assertTrue("Real entry is between zero and final height", rows(entry, 1, 3, .5f) in 1 until full)
        assertEquals("Zero marker stays static during source entry", zero, column(entry, 0, 3))
        val ownerBefore = ownerCommits
        val chartBefore = chartCommits
        compose.mainClock.advanceTimeBy(600)
        val settled = image()
        assertEquals(full, rows(settled, 1, 3, .5f))
        assertFalse(frame().running)
        assertEquals("Frame state must not recompose its owner", ownerBefore, ownerCommits)
        assertEquals("Frame state is read in Canvas draw, not component composition", chartBefore, chartCommits)

        compose.runOnIdle { model.value = scene("zero" to 0, "day" to 50, "last" to 100); event.value++ }
        compose.mainClock.advanceTimeBy(80)
        val lowering = image()
        val middle = rows(lowering, 1, 3, .5f)
        assertTrue("Existing bar shrinks from its previous displayed height", middle < full && middle > full * .5)
        assertEquals(zero, column(lowering, 0, 3))
        compose.runOnIdle { model.value = scene("zero" to 0, "day" to 80, "last" to 100); event.value++ }
        compose.mainClock.advanceTimeByFrame()
        val retargeted = rows(image(), 1, 3, .5f)
        assertTrue("Interrupting neither collapses nor jumps to the final endpoint",
            retargeted > full * .5 && retargeted < full * .8 - 3)
        compose.mainClock.advanceTimeBy(500)
        val end = rows(image(), 1, 3, .5f)
        assertEquals("Source normalized endpoint is 80% of the fixture peak", full * .8, end.toDouble(), 3.0)
        assertFalse(frame().running)
        compose.mainClock.autoAdvance = true
    }

    @Test fun plotResizeScalesCurrentGeometryWithoutReenteringOrRestarting() {
        val model = mutableStateOf(scene("day" to 100, "last" to 100))
        val height = mutableStateOf(300.dp)
        val textSize = mutableStateOf("standard")
        host(model, mutableStateOf(1), mutableStateOf("off"), height = height, textSize=textSize)
        compose.mainClock.advanceTimeBy(64)
        val before = frame()
        assertTrue(before.running && before.scales.first() in 0f..1f)
        val small = image()
        compose.runOnIdle { height.value = 420.dp }
        compose.mainClock.advanceTimeByFrame()
        assertEquals("A taller viewport preserves the compact plot",small.height,image().height)
        compose.runOnIdle {textSize.value="largest"}
        compose.mainClock.advanceTimeByFrame()
        val after = frame()
        val large = image()
        assertTrue("Real font-size adjustment adds axis room to the plot", large.height > small.height)
        assertTrue("Resize continues current progress and never reenters from zero", after.scales.first() >= before.scales.first())
        assertTrue(after.scales.first() < 1f)
        val innerHeight = large.height - 12 * density
        assertEquals("The same normalized transform scales with the actual resized plot",
            innerHeight * after.scales.first(), rows(large, 0, 2, .5f).toFloat(), 3f)
        // Resize must not reset the original 420ms/14ms endpoint.
        compose.mainClock.advanceTimeBy(400)
        assertFalse(frame().running)
        assertTrue(frame().scales.all { it == 1f })
        compose.mainClock.autoAdvance = true
    }

    @Test fun reducedAndStopSettleAndInvisibleZeroTweensDoNotPublishFrames() {
        val model = mutableStateOf(scene("day" to 10, "last" to 20))
        val event = mutableStateOf(1)
        val preference = mutableStateOf("on")
        host(model, event, preference)
        assertFalse(frame().running)
        compose.runOnIdle { preference.value = "off" }
        compose.mainClock.advanceTimeByFrame()
        assertFalse("Disabling reduction alone does not replay entry", frame().running)
        compose.runOnIdle { model.value = scene("day" to 20, "last" to 20); event.value++ }
        compose.mainClock.advanceTimeBy(64)
        assertTrue(frame().running)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.mainClock.advanceTimeBy(200)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.mainClock.advanceTimeByFrame()
        assertFalse("Foreground return does not replay settled data", frame().running)
        assertTrue(frame().scales.all { it == 1f })

        compose.runOnIdle { model.value = scene("day" to 10, "last" to 20); event.value++ }
        compose.mainClock.advanceTimeBy(64)
        assertTrue(frame().running)
        compose.runOnIdle { preference.value = "on" }
        compose.mainClock.advanceTimeByFrame()
        assertFalse("Live reduced preference immediately settles", frame().running)
        assertTrue(frame().scales.all { it == 1f })

        compose.runOnIdle {
            preference.value = "off"
            model.value = scene("new-zero-a" to 0, "new-zero-b" to 0)
            event.value++
        }
        compose.mainClock.advanceTimeByFrame()
        val invisible = frame()
        assertTrue("New zero keys retain the original invisible animation state", invisible.running)
        val zeroPixels = column(image(), 0, 2)
        val ownerBefore = ownerCommits
        val chartBefore = chartCommits
        compose.mainClock.advanceTimeBy(1000)
        assertSame("No zero-only draw state is published on each frame", invisible, frame())
        assertEquals(zeroPixels, column(image(), 0, 2))
        assertEquals(ownerBefore, ownerCommits)
        assertEquals(chartBefore, chartCommits)
        compose.runOnIdle { event.value++ }
        compose.mainClock.advanceTimeByFrame()
        assertFalse("Next data event lazily advances the invisible timeline then keeps existing zero keys", frame().running)
        compose.mainClock.autoAdvance = true
    }
}
