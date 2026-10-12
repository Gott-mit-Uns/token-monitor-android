package dev.bing.tokenmonitor

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.UUID

class CurrencyPreferenceTest {
    @Test fun actualV6ReadsWithoutRewriteAndCurrentFormatRetainsCurrencyRatesAndAllPreviousChoices() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test-currency-${UUID.randomUUID()}.bin";val file=File(context.noBackupFilesDir,name)
        val store=DisplayPreferenceStore(context,name);val buffer=ByteArrayOutputStream()
        DataOutputStream(buffer).use { data ->
            data.writeByte(6);data.writeByte(2);data.writeBoolean(true);data.writeByte(1)
            data.writeByte(3);listOf(5,3,1).forEach(data::writeByte)
            data.writeByte(1);data.writeUTF("claude");data.writeByte(1);data.writeUTF("hermes");data.writeByte(0)
            data.writeUTF("last30");data.writeUTF("last30");data.writeUTF("tokens");data.writeUTF("year")
        }
        val original=buffer.toByteArray()
        try {
            file.writeBytes(original);val old=store.load()
            assertEquals("USD",old.currency);assertTrue(old.currencyRates.isEmpty())
            assertEquals("tokens",old.heatmapMetric);assertEquals("year",old.homeActiveDaysWindow)
            assertEquals("last30",old.lastPeriod);assertEquals(setOf("claude"),old.hiddenTools)
            assertTrue(original.contentEquals(file.readBytes()))
            val selected=DisplayChange.CurrencyRate("CNY","7.123456").apply(DisplayChange.Currency("CNY").apply(old))
            store.save(selected);assertEquals(16,file.readBytes()[0].toInt())
            assertEquals(selected,DisplayPreferenceStore(context,name).load())
            val good=file.readBytes()
            for (invalid in listOf(selected.copy(currency="invalid"),selected.copy(currencyRates=mapOf("CNY" to "0")),selected.copy(currencyRates=mapOf("USD" to "2")))) {
                assertTrue(runCatching { store.save(invalid) }.isFailure)
                assertTrue(good.contentEquals(file.readBytes()))
            }
            assertEquals(selected,DisplayChange.CurrencyRate("CNY","NaN").apply(selected))
            assertTrue(DisplayChange.CurrencyRate("CNY",null).apply(selected).currencyRates.isEmpty())
            file.writeBytes(good+0.toByte());assertTrue(runCatching { store.load() }.isFailure)
        } finally { store.clear() }
    }
}
