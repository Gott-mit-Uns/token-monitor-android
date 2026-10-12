package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class BackgroundImageRulesTest {
    @Test fun desktopLimitsRetainAspectAndOpacityEndpoints() {
        assertEquals(2000 to 1000, BackgroundImageRules.size(4000, 2000))
        assertEquals(500 to 2000, BackgroundImageRules.size(1000, 4000))
        assertEquals(101 to 67, BackgroundImageRules.size(101, 67))
        assertEquals(0, BackgroundImageRules.opacity(-1)); assertEquals(100, BackgroundImageRules.opacity(101))
        assertEquals(28, BackgroundImageRules.defaultOpacity)
    }
    @Test fun invalidDimensionsCannotAllocateUnboundedBitmap() {
        for ((width, height) in listOf(0 to 10, -1 to 10, 32769 to 1, 20000 to 20000)) {
            assertThrows(IllegalArgumentException::class.java) { BackgroundImageRules.size(width, height) }
        }
        assertEquals(12 * 1024 * 1024, BackgroundImageRules.sourceBytes)
        assertEquals(8 * 1024 * 1024, BackgroundImageRules.savedBytes)
    }
}
