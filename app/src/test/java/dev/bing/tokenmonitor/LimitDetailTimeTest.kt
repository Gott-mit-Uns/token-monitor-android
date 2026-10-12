package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class LimitDetailTimeTest {
    @Test fun millisecondsAndUtcSuffixAreFormattedInLocalZone() {
        assertEquals("2026-10-12 16:16:41",LimitDetailTime.format("2026-10-12T08:16:41.764Z",ZoneId.of("Asia/Shanghai")))
        assertEquals("2026-10-19 09:17:35",LimitDetailTime.format("2026-10-19T01:17:35.000Z",ZoneId.of("Asia/Shanghai")))
    }
    @Test fun offsetsAndDstAreNotTreatedAsLocalUtc() {
        assertEquals("2026-10-12 08:16:41",LimitDetailTime.format("2026-10-12T10:16:41+02:00",ZoneId.of("UTC")))
        assertEquals("2026-03-08 03:30:00",LimitDetailTime.format("2026-03-08T07:30:00Z",ZoneId.of("America/New_York")))
    }
    @Test fun missingAndMalformedDatesDoNotLeakWireText() {
        for(value in listOf(null,"","invalid","2026-10-12")) assertNull(LimitDetailTime.format(value))
    }
}
