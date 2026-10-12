package dev.bing.tokenmonitor

import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Assert.*

/** A real popup layout gate, evaluated before any scroll or selection action. */
internal fun assertNavigationMenuLayout(
    compose: ComposeTestRule,
    pages: List<HubPage>,
    requireNoScroll: Boolean,
    density: Float = 1f
) {
    compose.waitForIdle()
    val menu = compose.onNodeWithTag("hub-view-menu", useUnmergedTree = true)
    menu.assertIsDisplayed()
    val viewport = menu.getUnclippedBoundsInRoot()
    val scroll = menu.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
    assertTrue("The navigation popup has a nonempty viewport", viewport.width.value > 0f && viewport.height.value > 0f)
    if (requireNoScroll) {
        assertEquals("All visible navigation entries must fit together without scrolling", 0f, scroll.maxValue(), 0f)
        assertEquals("Opening the menu must not scroll to conceal earlier entries", 0f, scroll.value(), 0f)
    }
    val toleranceDp = 1f / density.coerceAtLeast(1f)
    val rows = pages.map { page ->
        val id = page.name.lowercase()
        val row = compose.onNodeWithTag("view-$id", useUnmergedTree = true)
        row.assertExists()
        val bounds = row.getUnclippedBoundsInRoot()
        assertTrue("$id keeps its 36 dp compact navigation height", bounds.height.value + toleranceDp >= 36f)
        assertTrue("$id keeps its 48 dp touch width", bounds.width.value + toleranceDp >= 48f)
        assertTrue("$id stays inside the menu horizontally", bounds.left.value + toleranceDp >= viewport.left.value && bounds.right.value <= viewport.right.value + toleranceDp)
        if (requireNoScroll) {
            row.assertIsDisplayed()
            assertTrue("The complete $id row is visible together with every other entry", bounds.top.value + toleranceDp >= viewport.top.value && bounds.bottom.value <= viewport.bottom.value + toleranceDp)
        }
        val label = compose.onNodeWithTag("view-$id-label", useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("$id exposes its actual text layout", 1, layouts.size)
        val layout = layouts.single()
        assertFalse("$id text must not overflow its measured bounds", layout.hasVisualOverflow)
        assertFalse("$id text must not use an ellipsis", (0 until layout.lineCount).any(layout::isLineEllipsized))
        val textBounds = label.getUnclippedBoundsInRoot()
        assertTrue("$id label is completely inside its touch row", textBounds.left.value + toleranceDp >= bounds.left.value && textBounds.right.value <= bounds.right.value + toleranceDp && textBounds.top.value + toleranceDp >= bounds.top.value && textBounds.bottom.value <= bounds.bottom.value + toleranceDp)
        bounds
    }
    rows.zipWithNext().forEach { (before, after) ->
        assertTrue("Visible entries preserve their configured order without overlapping touch targets", before.bottom.value <= after.top.value + toleranceDp)
    }
}

/** Ordinary portrait phones have room for the default eight compact navigation rows. */
internal fun navigationWindowCanFitDefaultRows(activity: MainActivity): Boolean {
    val configuration = activity.resources.configuration
    return configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
        configuration.screenWidthDp >= 320 && configuration.screenHeightDp >= 600 && configuration.fontScale <= 2f
}

/** Only genuinely constrained windows may scroll; the fit gate runs first. */
internal fun selectNavigationOptionWithoutConcealingOverflow(compose: ComposeTestRule, id: String, requireNoScroll: Boolean) {
    val row = compose.onNodeWithTag("view-$id")
    if (requireNoScroll) {
        val scroll = compose.onNodeWithTag("hub-view-menu").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals("A navigation click cannot hide overflow by scrolling", 0f, scroll.maxValue(), 0f)
        row.assertIsDisplayed().performClick()
    } else row.performScrollTo().performClick()
}
