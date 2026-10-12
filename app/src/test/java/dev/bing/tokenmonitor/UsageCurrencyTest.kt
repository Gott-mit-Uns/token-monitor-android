package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale

class UsageCurrencyTest {
    private fun fixture() = javaClass.classLoader!!.getResourceAsStream("usage-display-parity.json")!!.bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
    @Test fun allDesktopAmountsAndLabelsMatchIncludingBinaryRoundingAndScientificValues() {
        val fixture = fixture()
        fixture.getValue("money").jsonArray.forEach { raw ->
            val row = raw.jsonObject
            val rates = (row["rates"] as? JsonObject).orEmpty().mapValues { it.value.jsonPrimitive.content }
            val currency = UsageCurrency(row.getValue("code").jsonPrimitive.content, rates)
            val amount = row.getValue("amount").jsonPrimitive.content
            assertEquals(row.toString(), row.getValue("expected").jsonPrimitive.content, currency.format(amount))
            assertEquals(row.getValue("converted").jsonPrimitive.double, currency.converted(amount)!!, 0.0)
        }
        val numbers = NumberFormat.getIntegerInstance(Locale.CHINESE)
        fixture.getValue("costLabels").jsonArray.forEach { raw ->
            val row = raw.jsonObject; val currency = UsageCurrency(row.getValue("code").jsonPrimitive.content)
            val amount = row.getValue("amount").jsonPrimitive.content
            val unpriced = BigInteger(row.getValue("unpriced").jsonPrimitive.content)
            assertEquals(row.getValue("full").jsonPrimitive.content, currency.label(amount, unpriced, formatTokens = { numbers.format(it) }))
            assertEquals(row.getValue("compact").jsonPrimitive.content, currency.label(amount, unpriced, compact = true))
        }
    }
    @Test fun unknownCostsAndInvalidRatesNeverBecomeFreeUsage() {
        for (value in listOf(null,"NaN","Infinity","-1","1e999")) assertEquals("—",UsageCurrency().format(value))
        assertEquals("— (100 unpriced tokens)", UsageCurrency().label(null, BigInteger.valueOf(100)))
        assertEquals("—",UsageCurrency().headline("0",100))
        assertEquals("$0.8000",UsageCurrency().headline("0.8",100))
        assertEquals("CNY", UsageCurrency.normalize(" cny "))
        assertEquals("USD", UsageCurrency.normalize("invalid"))
        assertTrue(UsageCurrency.validRate("7.123456"))
        assertFalse(UsageCurrency.validRate("0"));assertFalse(UsageCurrency.validRate("1e999"))
    }
}
