package io.github.theminionooo.tokenmonitor.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTokenFormattingTest {
    @Test fun chineseUnitsUseDesktopPrecisionAndTrimZeros() {
        assertEquals("0", formatHomeTokens(0, true))
        assertEquals("9,999", formatHomeTokens(9_999, true))
        assertEquals("1万", formatHomeTokens(10_000, true))
        assertEquals("1.23万", formatHomeTokens(12_345, true))
        assertEquals("12.3万", formatHomeTokens(123_456, true))
        assertEquals("1亿", formatHomeTokens(100_000_000, true))
        assertEquals("1.23亿", formatHomeTokens(123_456_789, true))
        assertEquals("45.6亿", formatHomeTokens(4_555_528_936, true))
    }

    @Test fun roundedChineseValuesPromoteFromWanToYi() {
        assertEquals("1亿", formatHomeTokens(99_999_999, true))
        assertEquals("1亿", formatHomeTokens(99_999_500, true))
        assertEquals("9999.9万", formatHomeTokens(99_999_499, true))
        assertEquals("10万", formatHomeTokens(99_999, true))
    }

    @Test fun internationalUnitsRemainOriginal() {
        assertEquals("4.6B", formatHomeTokens(4_555_528_936, false))
        assertEquals("1.2M", formatHomeTokens(1_234_567, false))
        assertEquals("12.3K", formatHomeTokens(12_345, false))
    }

    @Test fun fullTotalsIgnoreHomeUnitWhenCompactIsOffOrPageIsSecondary() {
        for (chinese in listOf(false, true)) {
            assertEquals("4,555,528,936", formatTokenTotal(4_555_528_936, true, false, chinese))
            for (compact in listOf(false, true)) {
                assertEquals("4,555,528,936", formatTokenTotal(4_555_528_936, false, compact, chinese))
            }
        }
        assertEquals("45.6亿", formatTokenTotal(4_555_528_936, true, true, true))
        assertEquals("4.6B", formatTokenTotal(4_555_528_936, true, true, false))
    }

    @Test fun chineseFormattingHandlesLongMaximumWithoutOverflow() {
        assertEquals("92233720368.5亿", formatHomeTokens(Long.MAX_VALUE, true))
    }
}
