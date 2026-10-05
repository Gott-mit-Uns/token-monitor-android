package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.ProjectUsage
import io.github.theminionooo.tokenmonitor.domain.SessionUsage
import org.junit.Assert.*
import org.junit.Test

class UsageSearchTest {
    private val session = SessionUsage("session-42", "Private planning title", "mcode", "Example project", 10, 0.0, listOf("example-model"), 1, "", "")

    @Test fun `titles are searchable only while visible and public labels remain searchable`() {
        assertTrue(session.matchesSearch(" planning ", true))
        assertFalse(session.matchesSearch("planning", false))
        listOf("session-42", "example project", "mcode", "MiniMax Code", "example-model", " ").forEach {
            assertTrue(it, session.matchesSearch(it, false))
        }
        assertFalse(session.matchesSearch("unrelated", true))
    }

    @Test fun `project search includes its visible name fallback and tool label`() {
        val project = ProjectUsage("project-42", "", 10, 0.0, 1, mapOf("mcode" to 10))
        assertTrue(project.matchesSearch("project-42"))
        assertTrue(project.matchesSearch("MiniMax Code"))
    }

    @Test fun `empty month and search misses are distinct from unsupported rolling periods`() {
        assertEquals("No session detail is available for month.", usageListEmptyMessage(true, DashboardPeriod.Month, false))
        assertEquals("No projects match this search.", usageListEmptyMessage(false, DashboardPeriod.Month, true))
        assertEquals("The Hub reports sessions for Day, Month, and Total only.", usageListEmptyMessage(true, DashboardPeriod.Last7, true))
    }
}
