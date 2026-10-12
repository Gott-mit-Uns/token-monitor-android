package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class PricingTest {
    private fun response(value: String, revision: Int = 3) = """{"ok":true,"version":1,"revision":$revision,"updatedAt":"2026-10-09T00:00:00Z","value":$value}"""
    @Test fun zeroUnknownAndAllFiveRatesRemainDistinct() {
        val price = PricingParser.parse(response("""[{"modelId":" demo/model ","inputPerM":0,"outputPerM":1.23456789,"cacheReadPerM":null,"cacheWritePerM":"","cacheWrite1hPerM":2.5}]"""))
        assertTrue(price.initialized)
        assertEquals("demo/model", price.models.single().model)
        assertEquals("0", price.models.single().rates.getValue("inputPerM").toPlainString())
        assertEquals("1.23456789", price.models.single().rates.getValue("outputPerM").toPlainString())
        assertNull(price.models.single().rates["cacheReadPerM"])
        assertNull(price.models.single().rates["cacheWritePerM"])
        assertEquals("2.5", price.models.single().rates.getValue("cacheWrite1hPerM").toPlainString())
    }
    @Test fun uninitializedAndWrittenEmptyAreDifferent() {
        assertFalse(PricingParser.parse(response("null", 0)).initialized)
        assertTrue(PricingParser.parse(response("[]")).initialized)
        assertTrue(PricingParser.parse(response("[]")).models.isEmpty())
    }
    @Test fun malformedReplacementFailsRatherThanClearingValidData() {
        for (value in listOf("""[{"modelId":"a","inputPerM":"1"}]""", """[{"modelId":"a","inputPerM":-1}]""",
            """[{"modelId":"a","cacheReadPerM":1}]""", """[{"modelId":"a","inputPerM":1},{"modelId":"a","outputPerM":2}]""")) {
            assertTrue(runCatching { PricingParser.parse(response(value)) }.isFailure)
        }
        assertTrue(runCatching { PricingParser.parse("""{"ok":true,"version":2,"revision":1,"value":[]}""") }.isFailure)
    }
}
