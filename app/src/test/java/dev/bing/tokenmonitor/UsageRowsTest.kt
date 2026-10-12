package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class UsageRowsTest {
    @Test fun partialComponentsNeverInventCacheMissAndExactLargeCountersDoNotOverflow() {
        val snapshot = HubParser.parse("""{"periods":{"today":{"totalTokens":100,"cacheReadTokens":10,"outputTokens":20},"month":{"totalTokens":5000000123,"cacheReadTokens":5000000000,"cacheWriteTokens":0,"outputTokens":100,"unclassifiedTokens":3,"capabilities":{"tokenComponents":true}}}}""")
        assertNull(snapshot.periods.getValue("today").components.inputMiss)
        assertEquals(20L, snapshot.periods.getValue("month").components.inputMiss)
        assertEquals(3L, snapshot.periods.getValue("month").components.unclassified)
        val missingClassification = HubParser.parse("""{"periods":{"today":{"totalTokens":100,"cacheReadTokens":10,"cacheWriteTokens":0,"outputTokens":20,"capabilities":{"tokenComponents":true}}}}""")
        assertNull(missingClassification.periods.getValue("today").components.inputMiss)
    }
    @Test fun zeroCostDoesNotInventMissingPriceButExplicitUnpricedIsKept() {
        val snapshot = HubParser.parse("""{"periods":{"today":{"totalTokens":100,"costUsd":0},"month":{"totalTokens":100,"costUsd":0,"unpricedTokens":100}}}""")
        assertNull(snapshot.periods.getValue("today").unpriced)
        assertEquals(100L, snapshot.periods.getValue("month").unpriced)
    }
    @Test fun readsProjectRollupAndKeepsSessionBoundaryThreeStates() {
        val snapshot = HubParser.parse("""{"periods":{"today":{"projects":{"p":{"label":"Example","tokens":42}},"sessions":{"a":{"sessionId":"a","totalTokens":12,"turnEnded":false},"b":{"sessionId":"b","totalTokens":30}}}}}""")
        val period = snapshot.periods.getValue("today")
        assertEquals(42L, period.projects.single().tokens)
        assertEquals("Example", period.projects.single().label)
        assertEquals(false, period.sessions.first { it.id == "a" }.turnEnded)
        assertNull(period.sessions.first { it.id == "b" }.turnEnded)
        assertNull(period.tokens)
    }
}
