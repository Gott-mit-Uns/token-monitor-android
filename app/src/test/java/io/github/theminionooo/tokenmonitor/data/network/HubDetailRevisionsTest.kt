package io.github.theminionooo.tokenmonitor.data.network

import org.junit.Assert.*
import org.junit.Test

class HubDetailRevisionsTest {
    private val raw = """{"historyRevision":"h1","deviceHistoryRevision":"d1","subscriptionsUpdatedAt":"s1"}"""
    private fun wire(stats: String) = WireHubSnapshot("{}", stats, "{}", "{}", "{}", 0)
    @Test fun `only successful detail revisions are reused`() {
        val snapshot = wire(HubDetailRevisions.markLoaded(raw))
        assertTrue(HubDetailRevisions.canReuse(snapshot, raw, "historyRevision", snapshot.history))
        assertFalse(HubDetailRevisions.canReuse(snapshot, raw.replace("h1", "h2"), "historyRevision", snapshot.history))
        assertFalse(HubDetailRevisions.canReuse(snapshot, raw, "historyRevision", null))
    }
    @Test fun `old hubs and old caches reread details`() {
        assertFalse(HubDetailRevisions.canReuse(wire(raw), raw, "historyRevision", "{}"))
        assertFalse(HubDetailRevisions.canReuse(wire("{}"), "{}", "historyRevision", "{}"))
    }
    @Test fun `streamed revision cannot claim old history is current`() {
        val snapshot = wire(HubDetailRevisions.markLoaded(raw))
        val streamed = HubDetailRevisions.retainMarkers(snapshot.stats, raw.replace("h1", "h2"))
        assertTrue(HubDetailRevisions.historyChanged(snapshot.copy(stats = streamed), streamed))
        assertFalse(HubDetailRevisions.canReuse(snapshot.copy(stats = streamed), streamed, "historyRevision", "{}"))
        val loaded = HubDetailRevisions.markLoaded(streamed)
        assertFalse(HubDetailRevisions.historyChanged(snapshot.copy(stats = loaded), loaded))
    }
    @Test fun `HTML cannot be accepted as healthy SSE`() {
        assertFalse(isEventStream("text/html"))
        assertFalse(isEventStream(null))
        assertTrue(isEventStream("text/event-stream; charset=utf-8"))
    }
}
