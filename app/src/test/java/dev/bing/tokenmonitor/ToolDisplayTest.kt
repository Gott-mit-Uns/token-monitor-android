package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

/** These examples are the desktop's observable rules, not a UI implementation mirror. */
class ToolDisplayTest {
    private val usage = listOf("claude" to 300L, "codex" to 100L, "opencode" to 60L, "hermes" to 20L, "gemini" to 10L)
    @Test fun pinAndHideKeepUsageOrderForOtherRowsAndKeepSourceCounters() {
        val preferences = DisplayPreferences(hiddenTools = setOf("claude"), pinnedTools = listOf("hermes", "codex"))
        assertEquals(listOf("hermes", "codex", "opencode", "gemini"), ToolDisplay.apply(usage, preferences).map { it.first })
        assertEquals(490L, usage.sumOf { it.second })
        assertEquals(5, usage.size)
        assertEquals(listOf("claude", "codex", "opencode", "hermes", "gemini"), ToolDisplay.apply(usage, DisplayPreferences()).map { it.first })
    }
    @Test fun manualOrderOverridesPinsAndKeepsUncataloguedRowsAtTheEnd() {
        val preferences = DisplayPreferences(toolOrder = listOf("opencode", "claude", "hermes", "codex"), pinnedTools = listOf("hermes"))
        assertEquals(listOf("opencode", "claude", "hermes", "codex", "gemini"), ToolDisplay.apply(usage, preferences).map { it.first })
        val pinned = ToolDisplay.togglePin(preferences, "codex")
        assertTrue(pinned.toolOrder.isEmpty())
        assertEquals(listOf("hermes", "codex", "claude", "opencode", "gemini"), ToolDisplay.apply(usage, pinned).map { it.first })
    }
    @Test fun movesInsidePinsKeepUsageModeButMovingAnUnpinnedToolSavesManualOrder() {
        val preferences = DisplayPreferences(pinnedTools = listOf("hermes", "codex"))
        val reordered = ToolDisplay.move(preferences, "codex", -1)
        assertEquals(listOf("codex", "hermes"), reordered.pinnedTools)
        assertTrue(reordered.toolOrder.isEmpty())
        val manual = ToolDisplay.move(reordered, "opencode", -1)
        assertTrue(manual.pinnedTools.isEmpty())
        assertEquals(listOf("claude", "opencode", "codex"), manual.toolOrder.take(3))
        assertEquals(listOf("claude", "opencode", "codex", "hermes", "gemini"), ToolDisplay.apply(usage, manual).map { it.first })
    }
    @Test fun unsupportedIdsDuplicatesAndEdgeMovesDoNotCorruptPreferences() {
        assertEquals(listOf("hermes", "codex"), ToolDisplay.selected(listOf(" Hermes ", "unknown", "hermes", "CODEX")))
        val preferences = DisplayPreferences()
        assertEquals(preferences, ToolDisplay.togglePin(preferences, "unknown"))
        assertEquals(preferences, ToolDisplay.toggleHidden(preferences, "gemini"))
        assertEquals(preferences, ToolDisplay.move(preferences, "claude", -1))
        val partial = ToolDisplay.normalizedOrder(listOf("hermes", "codex", "hermes", "unknown"))
        assertEquals(desktopManagedToolIds.size, partial.size)
        assertEquals(listOf("hermes", "codex", "claude", "opencode"), partial.take(4))
    }
    @Test fun costOnlyRowsStayVisibleWithoutInventingTokens() {
        val period = HubParser.parse("""{"periods":{"today":{"clientCosts":{"codex":1.25,"custom-source":0.5}}}}""").periods.getValue("today")
        val visible = ToolDisplay.usageRows(period, DisplayPreferences(hiddenTools = setOf("codex")))
        assertEquals(listOf("custom-source"), visible.map { it.first })
        assertNull(visible.single().second)
        assertNull(period.tokens)
        assertEquals("0.5", period.toolDetails.getValue("custom-source").cost)
    }
}
