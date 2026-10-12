package dev.bing.tokenmonitor

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.UUID

class AppearancePreferenceTest {
    @Test fun actualV7ReadsWithoutRewriteAndV8RetainsAppearanceWithAllPreviousChoices() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test-appearance-${UUID.randomUUID()}.bin";val file=File(context.noBackupFilesDir,name)
        val store=DisplayPreferenceStore(context,name);val buffer=ByteArrayOutputStream()
        DataOutputStream(buffer).use { data ->
            data.writeByte(7);data.writeByte(2);data.writeBoolean(true);data.writeByte(1)
            data.writeByte(3);listOf(5,3,1).forEach(data::writeByte)
            data.writeByte(1);data.writeUTF("claude");data.writeByte(1);data.writeUTF("hermes");data.writeByte(0)
            data.writeUTF("last30");data.writeUTF("last30");data.writeUTF("tokens");data.writeUTF("year")
            data.writeUTF("CNY");data.writeByte(1);data.writeUTF("CNY");data.writeUTF("7")
        }
        val original=buffer.toByteArray()
        try {
            file.writeBytes(original);val old=store.load()
            assertTrue(original.contentEquals(file.readBytes()));assertEquals(AppearancePreferences(),old.appearance)
            assertEquals("CNY",old.currency);assertEquals("7",old.currencyRates["CNY"]);assertEquals("last30",old.lastPeriod)
            val selected=old.copy(appearance=AppearancePreferences(DesktopAppearance.presets.getValue("porcelain")+("chart" to "#123456"),"largest","serif",""))
            store.save(selected);assertEquals(16,file.readBytes()[0].toInt());assertEquals(selected,DisplayPreferenceStore(context,name).load())
            val good=file.readBytes()
            listOf(selected.copy(appearance=selected.appearance.copy(textSize="invalid")),selected.copy(appearance=selected.appearance.copy(colors=mapOf("bg" to "invalid"))),selected.copy(appearance=selected.appearance.copy(interfaceFont="{bad}"))).forEach { invalid ->
                assertTrue(runCatching { store.save(invalid) }.isFailure);assertTrue(good.contentEquals(file.readBytes()))
            }
            file.writeBytes(good+0.toByte());assertTrue(runCatching { store.load() }.isFailure)
        } finally { store.clear() }
    }
}
