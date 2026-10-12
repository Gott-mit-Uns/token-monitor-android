package dev.bing.tokenmonitor

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.UUID

/** Explicit default V13 tail; old V12/V11 records are verified without writing them back. */
internal fun legacyV12DefaultSettingsPrefix(current:ByteArray):ByteArray {
    val v15=if(current[0].toInt()==16) current.dropLast(1).toByteArray().also {it[0]=15} else current
    val v14=if(v15[0].toInt()==15) v15.dropLast(1).toByteArray().also {it[0]=14} else v15
    val v13=if(v14[0].toInt()==14) {
        require(v14.last()==0.toByte())
        v14.dropLast(1).toByteArray().also {it[0]=13}
    } else v14
    require(v13[0].toInt()==13)
    val suffix=ByteArrayOutputStream().also {buffer->DataOutputStream(buffer).use {it.writeUTF("system");it.writeUTF("tokens")}}.toByteArray()
    require(v13.takeLast(suffix.size).toByteArray().contentEquals(suffix))
    return v13.copyOf(v13.size-suffix.size).also {it[0]=12}
}
internal fun legacyV11DefaultMainPrefix(current:ByteArray):ByteArray {
    val v12=if(current[0].toInt() in listOf(13,14,15,16)) legacyV12DefaultSettingsPrefix(current) else current
    require(v12[0].toInt()==12)
    return v12.copyOf(v12.size-30).also {it[0]=11}
}

class MainDisplayPreferenceTest {
    @Test fun explicitV11RetainsEveryChoiceWithoutRewritingAndV12IncludesMainAndSessionOptions() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val name="main-v11-${UUID.randomUUID()}.bin"
        val file=File(context.noBackupFilesDir,name);val store=DisplayPreferenceStore(context,name)
        val oldBytes=ByteArrayOutputStream().also {buffer->DataOutputStream(buffer).use {d->
            d.writeByte(11);d.writeByte(2);d.writeBoolean(true);d.writeByte(1)
            d.writeByte(3);listOf(5,3,1).forEach(d::writeByte)
            d.writeByte(1);d.writeUTF("claude");d.writeByte(1);d.writeUTF("hermes");d.writeByte(0)
            d.writeUTF("last30");d.writeUTF("last30");d.writeUTF("tokens");d.writeUTF("year")
            d.writeUTF("CNY");d.writeByte(1);d.writeUTF("CNY");d.writeUTF("7")
            d.writeUTF("largest");d.writeUTF("serif");d.writeUTF("");d.writeByte(1);d.writeUTF("bg");d.writeUTF("#0b0c0e")
            d.writeByte(1);d.writeUTF("codex");d.writeUTF("#ff0000");d.writeBoolean(false);d.writeBoolean(false)
            repeat(3) {d.writeBoolean(true)}
            d.writeByte(2);d.writeUTF("mimo");d.writeUTF("claude")
            d.writeByte(1);d.writeUTF("grok")
            d.writeByte(2);d.writeUTF("codex");d.writeUTF("openrouter")
            d.writeByte(1);d.writeUTF("claude");d.writeByte(1);d.writeUTF("resets")
            d.writeByte(5);d.writeBoolean(true);d.writeBoolean(true)
        }}.toByteArray()
        try {
            file.writeBytes(oldBytes);val old=store.load();assertTrue(oldBytes.contentEquals(file.readBytes()))
            assertEquals(listOf("trends","devices","tools"),old.homeModules);assertEquals("CNY",old.currency);assertEquals("serif",old.appearance.interfaceFont)
            assertEquals(MainDisplayPreferences(),old.main);assertEquals(mapOf("claude" to listOf("resets")),old.limits.hiddenItems)
            val main=MainDisplayPreferences(MainDisplay.ids.reversed(),setOf("home","projects"),false,true,"localized",true,"burn","public-device",true,"remaining")
            val selected=old.copy(homeModules=listOf("sessions","limits","tools"),main=main)
            store.save(selected);assertEquals(16,file.readBytes()[0].toInt());assertEquals(selected,DisplayPreferenceStore(context,name).load())
            val v12=legacyV12DefaultSettingsPrefix(file.readBytes());file.writeBytes(v12)
            assertEquals(selected,store.load());assertTrue(v12.contentEquals(file.readBytes()))
            assertEquals(old,selected.copy(homeModules=old.homeModules,main=MainDisplayPreferences()))
            val orderReset=DisplayChange.ResetMainViewOrder.apply(selected);assertEquals(emptyList<String>(),orderReset.main.viewOrder);assertEquals(main.hiddenViews,orderReset.main.hiddenViews)
            val shown=DisplayChange.ShowAllMainViews.apply(selected);assertTrue(shown.main.hiddenViews.isEmpty());assertEquals(main.viewOrder,shown.main.viewOrder)
        } finally {store.clear()}
    }
    @Test fun invalidMainEnumsIdsCountsFlagsAndAtomicWritesNeverReplaceTheValidRecord() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val name="main-bounds-${UUID.randomUUID()}.bin"
        val file=File(context.noBackupFilesDir,name);val store=DisplayPreferenceStore(context,name)
        val prefs=DisplayPreferences(main=MainDisplayPreferences(compactTotal=true,showLiveRate=true))
        try {
            store.save(prefs);val good=file.readBytes();assertEquals(prefs,store.load())
            listOf(prefs.main.copy(viewOrder=listOf("home","home")),prefs.main.copy(hiddenViews=MainDisplay.storedIds.toSet()),
                prefs.main.copy(compactUnits="unsafe"),prefs.main.copy(rateMode="average"),prefs.main.copy(rateDevice="bad\ncontrol"),
                prefs.main.copy(sessionContextMetric="unknown")).forEach {invalid->
                assertTrue(runCatching {store.save(prefs.copy(main=invalid))}.isFailure);assertTrue(good.contentEquals(file.readBytes()))
            }
            val start=good.size-30-17
            for(offset in listOf(0,1,2,3,13,23)) {
                val malformed=good.copyOf().also {it[start+offset]=127};file.writeBytes(malformed)
                assertTrue("V12 offset $offset",runCatching {store.load()}.isFailure)
            }
            file.writeBytes(good.copyOf(good.size-1));assertTrue(runCatching {store.load()}.isFailure)
            file.writeBytes(good+0.toByte());assertTrue(runCatching {store.load()}.isFailure)
            file.writeBytes(good);assertEquals(prefs,store.load())
        } finally {store.clear()}
    }
}
