package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class SessionTitleTest {
    @Test fun usesAdmittedHubTitleAndFallsBackWhenAbsentOrInvalid() {
        val period = HubParser.parse("""{"periods":{"today":{"sessions":{"one":{"sessionId":"source-id","title":"Hub shared title","totalTokens":12},"two":{"sessionId":"fallback-id"},"three":{"sessionId":"safe-id","title":"bad\ncontrol"}},"projects":{"project":{"label":"Project label","title":"not a project title"}}}}}""").periods.getValue("today")
        assertEquals(listOf("Hub shared title", "fallback-id", "safe-id"), period.sessions.map { it.label })
        assertEquals("Project label", period.projects.single().label)
        assertEquals(12L, period.sessions.first().tokens)
    }
}
