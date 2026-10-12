package dev.bing.tokenmonitor

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.UUID

class HomeChartPreferenceTest {
    @Test fun v5CycleAndToolsReadWithoutRewriteAndCurrentFormatKeepsAllChoices() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test-home-preferences-${UUID.randomUUID()}.bin"
        val file=File(context.noBackupFilesDir,name); val store=DisplayPreferenceStore(context,name)
        val buffer=ByteArrayOutputStream()
        DataOutputStream(buffer).use { data ->
            data.writeByte(5); data.writeByte(2); data.writeBoolean(true); data.writeByte(1)
            data.writeByte(3); listOf(5,3,1).forEach(data::writeByte)
            data.writeByte(1); data.writeUTF("claude")
            data.writeByte(1); data.writeUTF("hermes")
            data.writeByte(0); data.writeUTF("last30"); data.writeUTF("last30")
        }
        val original=buffer.toByteArray()
        try {
            file.writeBytes(original)
            val old=store.load()
            assertEquals("last30",old.monthMode); assertEquals("last30",old.lastPeriod)
            assertEquals("cost",old.heatmapMetric); assertEquals("all",old.homeActiveDaysWindow)
            assertTrue(original.contentEquals(file.readBytes()))
            val next=DisplayChange.HomeActiveDaysWindow("year").apply(DisplayChange.HeatmapMetric("tokens").apply(old))
            store.save(next)
            assertEquals(16,file.readBytes()[0].toInt())
            assertEquals(next,DisplayPreferenceStore(context,name).load())
            val good=file.readBytes()
            assertTrue(runCatching { store.save(next.copy(heatmapMetric="invalid")) }.isFailure)
            assertTrue(runCatching { store.save(next.copy(homeActiveDaysWindow="invalid")) }.isFailure)
            assertTrue(good.contentEquals(file.readBytes()))
            file.writeBytes(good+0.toByte())
            assertTrue(runCatching { store.load() }.isFailure)
        } finally { store.clear() }
    }
}
