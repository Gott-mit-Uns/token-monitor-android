package dev.bing.tokenmonitor

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.UUID

class FixedPeriodPreferenceTest {
    @Test fun actualV4ToolsAreReadWithoutRewriteThenCurrentFormatPersistsSelectionAndKeepsAllOldChoices() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-fixed-preferences-${UUID.randomUUID()}.bin"
        val file = File(context.noBackupFilesDir, name)
        val store = DisplayPreferenceStore(context, name)
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { data ->
            data.writeByte(4); data.writeByte(2); data.writeBoolean(true); data.writeByte(1)
            data.writeByte(3); listOf(5,3,1).forEach(data::writeByte)
            data.writeByte(1); data.writeUTF("claude")
            data.writeByte(1); data.writeUTF("hermes")
            data.writeByte(0)
        }
        val original = buffer.toByteArray()
        try {
            file.writeBytes(original)
            val old = store.load()
            assertEquals(DisplayPreferences(ThemeChoice.DARK, true, LanguageChoice.ENGLISH, listOf("trends","devices","tools"), setOf("claude"), listOf("hermes")), old)
            assertTrue(original.contentEquals(file.readBytes()))
            val selected = DisplayChange.PeriodSelection("last30").apply(old)
            store.save(selected)
            assertEquals(16, file.readBytes()[0].toInt())
            assertEquals(selected, DisplayPreferenceStore(context, name).load())
            assertEquals("last30", selected.monthMode); assertEquals("last30", selected.lastPeriod)
            val today = DisplayChange.PeriodSelection("today").apply(selected)
            assertEquals("last30", today.monthMode); assertEquals("today", today.lastPeriod)
            val good = file.readBytes()
            assertTrue(runCatching { store.save(selected.copy(monthMode = "unknown")) }.isFailure)
            assertTrue(runCatching { store.save(selected.copy(lastPeriod = "unknown")) }.isFailure)
            assertTrue(good.contentEquals(file.readBytes()))
        } finally { store.clear() }
    }
}
