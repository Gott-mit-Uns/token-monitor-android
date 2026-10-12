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
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** Real component pixels; independent source math and Hub navigation have their own tests. */
class TrendsMainScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val dark get() = InstrumentationRegistry.getArguments().getString("visualDark") != "false"
    private val today = LocalDate.of(2026, 10, 9)
    private val clock = Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"), ZoneId.of("Asia/Shanghai"))
    private val stats = TrendsMainStats(BigInteger.valueOf(106), BigInteger.valueOf(14), BigInteger.ZERO, BigInteger.valueOf(10_010_000))

    // Daily rows and lifetime values match tools/capture-full-renderer.cjs. Public
    // monthly rows additionally exercise the source's nonempty TOTAL/fixed context.
    private fun preview() = HubHistory(
        List(84) { n -> HistoryRow(today.minusDays((83 - n).toLong()).toString(), (n % 11 + 1) * 1_430_000L, "1") },
        listOf(HistoryRow("2026-07", 20_000_000, "20"), HistoryRow("2026-08", 40_000_000, "40"),
            HistoryRow("2026-09", 50_000_000, "50"), HistoryRow("2026-10", 60_000_000, "60")),
        HistorySummary(165_164_772, "136.08", 106, 14, 40, 198_000_000, 22_000_000, "gpt-5.5", 1600)
    )

    private fun host(
        model: State<TrendsMainModel>,
        appearance: State<AppearancePreferences>,
        availableHeight: State<Dp?>? = null,
        viewportHeight: State<Dp?>? = null,
        opened: () -> Unit = {}
    ) {
        compose.activity.runOnUiThread { compose.activity.setContent {
            CompositionLocalProvider(LocalHubCalendarClock provides clock) {
                TokenTheme(dark, LanguageChoice.CHINESE, appearance.value) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        // Surface propagates its full-size minimum constraints. A
                        // non-propagating Box lets the explicit short viewport take effect.
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxWidth().then(viewportHeight?.value?.let { Modifier.height(it) } ?: Modifier.fillMaxHeight())
                                .testTag("trends-main-test-viewport")) {
                                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("trends-main-test-host")) {
                                    DesktopTrendsMain(model.value, opened, availableHeight = availableHeight?.value)
                                }
                            }
                        }
                    }
                }
            }
        } }
    }

    private fun layout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun pixels(tag: String) = compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().captureToImage().toPixelMap()

    private fun near(a: Color, b: Color, tolerance: Int = 2) = listOf(a.red - b.red, a.green - b.green, a.blue - b.blue)
        .all { abs(it * 255) <= tolerance }

    private fun assertPainted(tag: String, expected: Color, minimum: Int = 3) {
        val image = pixels(tag)
        var matches = 0
        for (y in 0 until image.height) for (x in 0 until image.width) if (near(image[x, y], expected)) matches++
        assertTrue("$tag must paint the expected composited colour", matches >= minimum)
    }

    private fun assertArrowPainted(text: Color, background: Color) {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("↗", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val ink = results.single().layoutInput.style.color
        // Source .6 caption opacity × .75 arrow opacity. A 13sp diagonal glyph
        // need not contain fully covered pixels at every density. Check the
        // actual text colour independently, then check its rasterised coverage.
        assertEquals(.45f, ink.alpha, .001f)
        assertTrue("Arrow uses the source body colour", near(ink.copy(alpha = 1f), text.copy(alpha = 1f)))
        val expected = text.copy(alpha = .45f).compositeOver(background)
        val image = pixels("trends-main-open-history")
        val start = floatArrayOf(background.red, background.green, background.blue)
        val delta = floatArrayOf(expected.red-background.red, expected.green-background.green, expected.blue-background.blue)
        val channel = delta.indices.maxBy { abs(delta[it]) }
        var visible = 0
        var strongest = 0f
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val pixel = image[x, y]
            val components = floatArrayOf(pixel.red, pixel.green, pixel.blue)
            val coverage = (components[channel]-start[channel])/delta[channel]
            strongest = maxOf(strongest, coverage)
            if (coverage > .12f) {
                visible++
                assertTrue("Arrow antialiasing stays on the expected colour blend", coverage <= 1.025f &&
                    components.indices.all { abs((components[it]-start[it]-delta[it]*coverage)*255) <= 2.5f })
            }
        }
        assertTrue("Arrow has visible source-coloured glyph pixels: count=$visible, strongest=$strongest", visible >= 3 && strongest >= .6f)
    }

    @Test fun sourceShapedDayMonthTotalFixedZeroAndEmptyScenesRemainReadable() {
        val history = preview()
        val model = mutableStateOf(TrendsMainProjection.project(history, "today", 165_164_772, today)!!)
        val appearance = mutableStateOf(AppearancePreferences(reduceMotion = "on"))
        host(model, appearance)
        fun save(name: String) {
            compose.waitForIdle()
            saveVisualEvidence(compose.activity, compose.onRoot(), "trends-main-$name-${if (dark) "dark" else "light"}.png")
        }
        fun cardValues(days: String, streak: String, time: String, peak: String) {
            compose.onNodeWithTag("trends-main-active-days").assertTextEquals(days)
            compose.onNodeWithTag("trends-main-streak").assertTextEquals(streak)
            compose.onNodeWithTag("trends-main-time").assertTextEquals(time)
            compose.onNodeWithTag("trends-main-peak").assertTextEquals(peak)
            listOf("active-days", "streak", "time", "peak").forEach { id ->
                val value = layout("trends-main-$id")
                assertFalse("$id value must not be clipped: size=${value.size}, paragraphHeight=${value.multiParagraph.height}, widthOverflow=${value.didOverflowWidth}, heightOverflow=${value.didOverflowHeight}", value.didOverflowWidth || value.didOverflowHeight)
                val caption = layout("trends-main-$id-label")
                assertFalse("$id caption must not be clipped: size=${caption.size}, paragraphHeight=${caption.multiParagraph.height}, widthOverflow=${caption.didOverflowWidth}, heightOverflow=${caption.didOverflowHeight}", caption.didOverflowWidth || caption.didOverflowHeight)
                val valueBounds = compose.onNodeWithTag("trends-main-$id").getUnclippedBoundsInRoot()
                val labelBounds = compose.onNodeWithTag("trends-main-$id-label").getUnclippedBoundsInRoot()
                assertTrue("Source card puts its value above the caption", valueBounds.bottom <= labelBounds.top)
            }
        }
        compose.onNodeWithTag("trends-main-range").assertTextEquals("近 7 天")
        compose.onNodeWithTag("trends-main-axis-start").assertTextEquals("10/3")
        compose.onNodeWithTag("trends-main-axis-end").assertTextEquals("10/9")
        compose.onNodeWithTag("trends-main-active-days-label").assertTextEquals("活跃天数")
        compose.onNodeWithTag("trends-main-streak-label").assertTextEquals("连续天数")
        compose.onNodeWithTag("trends-main-time-label").assertTextEquals("活跃时间")
        compose.onNodeWithTag("trends-main-peak-label").assertTextEquals("峰值单日")
        cardValues("106", "14", "0m", "10M")
        save("day")

        compose.runOnIdle { model.value = TrendsMainProjection.project(history, "month", 165_164_772, today)!! }
        compose.onNodeWithTag("trends-main-range").assertTextEquals("本月每日")
        compose.onNodeWithTag("trends-main-axis-start").assertTextEquals("10/1")
        cardValues("106", "14", "0m", "15.7M")
        save("month")

        compose.runOnIdle { model.value = TrendsMainProjection.project(history, "allTime", 165_164_772, today)!! }
        compose.onNodeWithTag("trends-main-range").assertTextEquals("历年每月")
        compose.onNodeWithTag("trends-main-axis-start").assertTextEquals("2026-07")
        compose.onNodeWithTag("trends-main-axis-end").assertTextEquals("2026-10")
        cardValues("106", "14", "55h 0m", "22M")
        save("total")

        val fixedPeriod = HubParser.parse("""{"periods":{"fixed":{"totalTokens":5000000,"costUsd":5}}}""").periods.getValue("fixed")
        val fixed = FixedPeriodView(period = fixedPeriod, activity = FixedActivity(2, 1, 90_000, 5_000_000))
        compose.runOnIdle { model.value = TrendsMainProjection.project(history, "last7", 165_164_772, today, fixed)!! }
        compose.onNodeWithTag("trends-main-range").assertTextEquals("历年每月")
        cardValues("106", "14", "2m", "5M")
        save("fixed")

        val zero = HubHistory(List(7) { n -> HistoryRow(today.minusDays((6 - n).toLong()).toString(), 0, "0") }, emptyList())
        compose.runOnIdle { model.value = TrendsMainProjection.project(zero, "today", 0, today)!! }
        compose.onNodeWithTag("trends-main-chart").assertExists()
        cardValues("0", "0", "0m", "0")
        save("zero")

        compose.runOnIdle { model.value = TrendsMainProjection.project(HubHistory(emptyList(), emptyList()), "today", 0, today)!! }
        compose.onNodeWithText("暂无使用历史").assertIsDisplayed()
        compose.onNodeWithTag("trends-main-chart").assertDoesNotExist()
        compose.onNodeWithTag("trends-main-open-history").assertDoesNotExist()
        listOf("active-days", "streak", "time", "peak").forEach { compose.onNodeWithTag("trends-main-$it").assertDoesNotExist() }
        save("empty")
        assertEquals(10_010_000L, history.daily.last().tokens)
        assertEquals(106L, history.summary!!.activeDays)
    }

    @Test fun actualChartCardAndTextPixelsUseSourceRolesOpacityGeometryAndAccessibleActions() {
        val model = mutableStateOf(TrendsMainModel(listOf(TrendsMainPoint("2026-10-08", 10), TrendsMainPoint("2026-10-09", 10)), TrendsMainRange.WEEK, stats, true))
        val appearance = mutableStateOf(AppearancePreferences(colors = mapOf("accent" to "#ff0000", "chart" to "#2060a0"), reduceMotion = "on"))
        var opens = 0
        host(model, appearance) { opens++ }
        val palette = DesktopAppearance.palette(appearance.value.colors, dark)
        val bg = hexColor(palette.getValue("bg"))
        val text = hexColor(palette.getValue("text"))
        val chart = Color(0xFF2060A0)
        val overlay = if (DesktopAppearance.light(palette.getValue("bg"))) Color(0xFF0F1218) else Color.White
        val card = overlay.copy(alpha = .04f).compositeOver(bg)
        val density = compose.activity.resources.displayMetrics.density
        compose.onNodeWithTag("trends-main-chart").performScrollTo().assertHasClickAction()
        val chartBounds = compose.onNodeWithTag("trends-main-chart").fetchSemanticsNode().boundsInRoot
        assertTrue("Chart has a 48dp accessible target", chartBounds.height / density >= 48f)
        val image = pixels("trends-main-chart")
        // Equal source bars make interior colour/slot checks independent of the
        // projection helper: two 150-unit slots, with a 70% bar width in each.
        val inset = (4 * density).roundToInt()
        val innerWidth = image.width - inset * 2
        val middleY = image.height / 2
        assertTrue("Normal bar uses chart colour at .5", near(image[inset + innerWidth / 4, middleY], chart.copy(alpha = .5f).compositeOver(bg)))
        assertTrue("Last bar uses chart colour at 1", near(image[inset + innerWidth * 3 / 4, middleY], chart))
        assertTrue("Gap is unpainted", near(image[inset + innerWidth / 2, middleY], bg))
        val firstPaint = (inset until image.width / 2).first { !near(image[it, middleY], bg) }
        assertEquals("gap=.3 centers a bar 15% into its slot", innerWidth * .075f, (firstPaint - inset).toFloat(), 2f)

        val arrow = compose.onNodeWithTag("trends-main-open-history").performScrollTo().assertHasClickAction()
        val arrowBounds = arrow.fetchSemanticsNode().boundsInRoot
        assertTrue("Arrow has a 48dp accessible height", arrowBounds.height / density >= 48f)
        assertTrue("Arrow has a 48dp accessible width", arrowBounds.width / density >= 48f)
        arrow.performClick()
        compose.onNodeWithTag("trends-main-chart").performScrollTo().performClick()
        assertEquals(2, opens)
        assertEquals(FontWeight.SemiBold, layout("trends-main-active-days").layoutInput.style.fontWeight)
        assertEquals("tnum", layout("trends-main-active-days").layoutInput.style.fontFeatureSettings)
        assertEquals(16.sp, layout("trends-main-active-days").layoutInput.style.fontSize)
        assertEquals(10.sp, layout("trends-main-active-days-label").layoutInput.style.fontSize)
        assertEquals(11.sp, layout("trends-main-range").layoutInput.style.fontSize)
        assertEquals(10.sp, layout("trends-main-axis-start").layoutInput.style.fontSize)
        assertPainted("trends-main-range", text.copy(alpha = .6f).compositeOver(bg))
        assertArrowPainted(text, bg)
        assertPainted("trends-main-axis-start", text.copy(alpha = .45f).compositeOver(bg))
        assertPainted("trends-main-active-days", text.compositeOver(card))
        assertPainted("trends-main-active-days-label", text.copy(alpha = .55f).compositeOver(card))
        val cardImage = pixels("trends-main-active-days-card")
        assertTrue("Card has the source .04 overlay background", near(cardImage[cardImage.width / 2, cardImage.height - 2], card))
        assertTrue("Card corner preserves the source rounded background", near(cardImage[0, 0], bg))
        val left = compose.onNodeWithTag("trends-main-active-days-card").getUnclippedBoundsInRoot()
        val right = compose.onNodeWithTag("trends-main-streak-card").getUnclippedBoundsInRoot()
        assertEquals("Two card columns keep an 8dp gap", 8f, right.left.value - left.right.value, .6f)

        compose.runOnIdle { model.value = model.value.copy(points = listOf(TrendsMainPoint("2026-10-08", 0), TrendsMainPoint("2026-10-09", 0))) }
        val zeros = pixels("trends-main-chart")
        fun strongestMarker(pixels: PixelMap, x: Int): Color = (pixels.height - (8 * density).roundToInt() until pixels.height)
            .map { pixels[x, it] }.maxBy { abs(it.red - bg.red) + abs(it.green - bg.green) + abs(it.blue - bg.blue) }
        assertTrue("Normal DAY zero marker uses .24", near(strongestMarker(zeros, inset + innerWidth / 4), chart.copy(alpha = .24f).compositeOver(bg), 3))
        assertTrue("Last DAY zero marker uses .32", near(strongestMarker(zeros, inset + innerWidth * 3 / 4), chart.copy(alpha = .32f).compositeOver(bg), 3))
        compose.runOnIdle { model.value = model.value.copy(showZeroMarkers = false) }
        val withoutMarkers = pixels("trends-main-chart")
        assertTrue("Other periods do not paint DAY zero markers", near(strongestMarker(withoutMarkers, inset + innerWidth / 4), bg))

        compose.runOnIdle {
            appearance.value=appearance.value.copy(colors=mapOf("accent" to "#ff0000","chart" to "#73bdf5"))
            model.value=model.value.copy(points=listOf(TrendsMainPoint("2026-10-08",10),TrendsMainPoint("2026-10-09",10)))
        }
        val defaultBars=pixels("trends-main-chart")
        val defaultBar=Color(0xFF6AB4F0) // original styles.css:44, separate from --chart-color
        assertTrue("Restoring the default chart colour restores the source bar colour",near(defaultBars[inset+innerWidth*3/4,defaultBars.height/2],defaultBar))
        assertTrue("Ordinary default bar preserves .5 opacity",near(defaultBars[inset+innerWidth/4,defaultBars.height/2],defaultBar.copy(alpha=.5f).compositeOver(bg)))

        compose.runOnIdle { model.value = model.value.copy(stats = stats.copy(activeDays = BigInteger("123456789012345678901234567890"))) }
        val longValue = layout("trends-main-active-days")
        assertFalse("Long values can wrap instead of clipping", longValue.didOverflowWidth || longValue.didOverflowHeight)
        assertTrue("Long values remain above their caption", compose.onNodeWithTag("trends-main-active-days").getUnclippedBoundsInRoot().bottom <= compose.onNodeWithTag("trends-main-active-days-label").getUnclippedBoundsInRoot().top)
    }

    @Test fun compactSparkIsBoundedAndShortLargeTextContentKeepsItsNaturalScrollableHeight() {
        val model = mutableStateOf(TrendsMainProjection.project(preview(), "today", 165_164_772, today)!!)
        val appearance = mutableStateOf(AppearancePreferences(reduceMotion = "on"))
        val target = mutableStateOf<Dp?>(600.dp)
        val viewport = mutableStateOf<Dp?>(320.dp)
        host(model, appearance, availableHeight = target, viewportHeight = viewport)
        val large = compose.onNodeWithTag("trends-main").getUnclippedBoundsInRoot()
        val largeChart = compose.onNodeWithTag("trends-main-chart").getUnclippedBoundsInRoot()
        assertTrue("Compact content no longer fills unused viewport", (large.bottom-large.top).value < 600f)
        assertEquals("Preferred plot height includes its padding", 160f, (largeChart.bottom-largeChart.top).value, 1f)

        compose.runOnIdle {
            target.value = 160.dp
            viewport.value = 160.dp
            appearance.value = appearance.value.copy(textSize = "largest")
        }
        val short = compose.onNodeWithTag("trends-main").getUnclippedBoundsInRoot()
        val shortChart = compose.onNodeWithTag("trends-main-chart").getUnclippedBoundsInRoot()
        val actualViewport = compose.onNodeWithTag("trends-main-test-viewport").getUnclippedBoundsInRoot()
        assertEquals("Fixture must use an actual 160dp viewport", 160f, (actualViewport.bottom - actualViewport.top).value, 1f)
        assertTrue("Natural content must exceed a short viewport instead of clipping cards", (short.bottom-short.top).value > 160f)
        assertTrue("Short screens retain at least a 48dp plot plus its source padding", (shortChart.bottom-shortChart.top).value >= 59.5f)
        val scroll = compose.onNodeWithTag("trends-main-test-host").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("The full natural item must be included in the actual scroll range", scroll.maxValue() > 0f)
        listOf("active-days", "streak", "time", "peak").forEach { id ->
            compose.onNodeWithTag("trends-main-$id").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("trends-main-$id-label").performScrollTo().assertIsDisplayed()
            val value = layout("trends-main-$id")
            val label = layout("trends-main-$id-label")
            assertFalse("$id large value is naturally measured", value.didOverflowHeight || value.didOverflowWidth)
            assertFalse("$id large caption is naturally measured", label.didOverflowHeight || label.didOverflowWidth)
        }
    }
}
