package dev.bing.tokenmonitor

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.UUID

class LimitDisplayPreferenceTest {
    @Test fun explicitV9RecordKeepsEveryOldChoiceAndDefaultsWithoutRewritingBeforeV12RoundTrip() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test-limit-${UUID.randomUUID()}.bin";val file=File(context.noBackupFilesDir,name);val store=DisplayPreferenceStore(context,name)
        val buffer=ByteArrayOutputStream()
        DataOutputStream(buffer).use {d ->
            d.writeByte(9);d.writeByte(2);d.writeBoolean(true);d.writeByte(1)
            d.writeByte(3);listOf(5,3,1).forEach(d::writeByte)
            d.writeByte(1);d.writeUTF("claude");d.writeByte(1);d.writeUTF("hermes");d.writeByte(0)
            d.writeUTF("last30");d.writeUTF("last30");d.writeUTF("tokens");d.writeUTF("year")
            d.writeUTF("CNY");d.writeByte(1);d.writeUTF("CNY");d.writeUTF("7")
            d.writeUTF("largest");d.writeUTF("serif");d.writeUTF("");d.writeByte(1);d.writeUTF("bg");d.writeUTF("#0b0c0e")
            d.writeByte(1);d.writeUTF("codex");d.writeUTF("#ff0000");d.writeBoolean(false);d.writeBoolean(false)
        }
        try {
            val oldBytes=buffer.toByteArray();file.writeBytes(oldBytes);val old=store.load();assertTrue(oldBytes.contentEquals(file.readBytes()))
            assertEquals(LimitDisplayPreferences(),old.limits);assertEquals("#ff0000",old.appearance.vendorColors["codex"]);assertFalse(old.appearance.showToolIcons);assertFalse(old.appearance.showLiveDot)
            assertEquals("CNY",old.currency);assertEquals("7",old.currencyRates["CNY"]);assertEquals("last30",old.lastPeriod);assertEquals("largest",old.appearance.textSize)
            val selected=old.copy(limits=LimitDisplayPreferences(true,true,true));store.save(selected)
            assertEquals(16,file.readBytes()[0].toInt());assertEquals(selected,DisplayPreferenceStore(context,name).load());assertEquals(old,selected.copy(limits=LimitDisplayPreferences()))
        } finally {store.clear()}
    }
    @Test fun quotaBooleansTruncationAndTrailingBytesAreStrictAndAtomicChoicesStaySeparateFromEncryptedHubData() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test-limit-bounds-${UUID.randomUUID()}.bin";val file=File(context.noBackupFilesDir,name);val store=DisplayPreferenceStore(context,name)
        val prefs=DisplayPreferences(limits=LimitDisplayPreferences(true,false,true))
        try {
            store.save(prefs);val current=file.readBytes();val good=legacyV11DefaultMainPrefix(current)
            for(index in (good.size-10 until good.size-7)+(good.size-2 until good.size)) {file.writeBytes(good.copyOf().also {it[index]=2});assertTrue(runCatching {store.load()}.isFailure)}
            for(count in listOf(0,13)) {file.writeBytes(good.copyOf().also {it[it.size-3]=count.toByte()});assertTrue(runCatching {store.load()}.isFailure)}
            file.writeBytes(good.copyOf(good.size-1));assertTrue(runCatching {store.load()}.isFailure)
            file.writeBytes(good+0.toByte());assertTrue(runCatching {store.load()}.isFailure)
            file.writeBytes(good);assertEquals(prefs,store.load());assertTrue(good.size<=4096)
            assertFalse(good.toString(Charsets.ISO_8859_1).contains("example.com"))
        } finally {store.clear()}
    }
}
