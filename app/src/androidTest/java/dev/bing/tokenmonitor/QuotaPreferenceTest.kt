package dev.bing.tokenmonitor

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.UUID

class QuotaPreferenceTest {
    private fun v10():ByteArray=ByteArrayOutputStream().also {buffer->DataOutputStream(buffer).use {d->
        d.writeByte(10);d.writeByte(2);d.writeBoolean(true);d.writeByte(1)
        d.writeByte(3);listOf(5,3,1).forEach(d::writeByte)
        d.writeByte(1);d.writeUTF("claude");d.writeByte(1);d.writeUTF("hermes");d.writeByte(0)
        d.writeUTF("last30");d.writeUTF("last30");d.writeUTF("tokens");d.writeUTF("year")
        d.writeUTF("CNY");d.writeByte(1);d.writeUTF("CNY");d.writeUTF("7")
        d.writeUTF("largest");d.writeUTF("serif");d.writeUTF("");d.writeByte(0)
        d.writeByte(1);d.writeUTF("codex");d.writeUTF("#ff0000");d.writeBoolean(false);d.writeBoolean(false)
        repeat(3) {d.writeBoolean(true)}
    }}.toByteArray()
    @Test fun installedV10ChoicesRemainUnchangedUntilExplicitSaveAndV11RetainsEveryQuotaChoice() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val name="quota-v10-${UUID.randomUUID()}.bin"
        val file=File(context.noBackupFilesDir,name);val store=DisplayPreferenceStore(context,name)
        try {
            val bytes=v10();file.writeBytes(bytes);val old=store.load();assertTrue(bytes.contentEquals(file.readBytes()))
            assertEquals(LimitDisplayPreferences(true,true,true),old.limits)
            assertEquals("last30",old.lastPeriod);assertEquals("CNY",old.currency);assertEquals("serif",old.appearance.interfaceFont);assertFalse(old.appearance.showToolIcons)
            val selected=old.copy(limits=old.limits.copy(providerOrder=listOf("mimo","claude"),homeProviderOrder=listOf("openrouter","codex"),hiddenHomeProviders=setOf("grok","mimo"),hiddenItems=mapOf("claude" to listOf("resets","spend","[\"weekly\",\"Fable only\",\"\",false]")),homeAccountCount=12,homeBars=true,showHomeProviderNames=true))
            store.save(selected);assertEquals(16,file.readBytes()[0].toInt());assertEquals(selected,DisplayPreferenceStore(context,name).load())
            assertEquals(old,selected.copy(limits=old.limits));val good=file.readBytes()
            val invalid=listOf(selected.limits.copy(homeAccountCount=0),selected.limits.copy(homeAccountCount=13),selected.limits.copy(providerOrder=listOf("codex","codex")),selected.limits.copy(homeProviderOrder=listOf("future")),selected.limits.copy(hiddenHomeProviders=setOf("Future")),selected.limits.copy(hiddenItems=mapOf("codex" to listOf("bad"))),selected.limits.copy(hiddenItems=mapOf("claude" to listOf("resets","resets"))))
            invalid.forEach {value->assertTrue(runCatching {store.save(selected.copy(limits=value))}.isFailure);assertTrue(good.contentEquals(file.readBytes()))}
            file.writeBytes(good.copyOf(good.size-1));assertTrue(runCatching {store.load()}.isFailure)
            file.writeBytes(good+0.toByte());assertTrue(runCatching {store.load()}.isFailure)
        } finally {store.clear()}
    }
    @Test fun maximalUnicodeItemsRoundTripAndMalformedProviderItemCountsAndTypesAreRejected() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val name="quota-max-${UUID.randomUUID()}.bin"
        val file=File(context.noBackupFilesDir,name);val store=DisplayPreferenceStore(context,name)
        val keys=(0 until 64).map {index->buildJsonArray {add("billing");add("中".repeat(350)+index);add("");add(false)}.toString()}
        val prefs=DisplayPreferences(limits=LimitDisplayPreferences(providerOrder=LimitPreferences.providers,homeProviderOrder=LimitPreferences.providers.reversed(),hiddenHomeProviders=LimitPreferences.providers.toSet(),hiddenItems=LimitPreferences.providers.associateWith {keys},homeAccountCount=12,homeBars=true,showHomeProviderNames=true))
        try {
            store.save(prefs);val good=file.readBytes();assertTrue(good.size>4096);assertTrue(good.size<=DisplayPreferenceStore.maxBytes);assertEquals(prefs,DisplayPreferenceStore(context,name).load())
            file.writeBytes(good.copyOf().also {it[0]=10});assertTrue(runCatching {store.load()}.isFailure)
            file.writeBytes(good);assertTrue(runCatching {store.save(prefs.copy(limits=prefs.limits.copy(hiddenItems=mapOf("codex" to keys+"resets"))))}.isFailure);assertTrue(good.contentEquals(file.readBytes()))
            val base=v10().also {it[0]=11}
            fun tail(block:DataOutputStream.()->Unit)=ByteArrayOutputStream().also {DataOutputStream(it).use(block)}.toByteArray()
            val malformed=listOf(
                tail {writeByte(30)},
                tail {writeByte(2);writeUTF("codex");writeUTF("codex")},
                tail {writeByte(1);writeUTF("unknown")},
                tail {repeat(3) {writeByte(0)};writeByte(1);writeUTF("codex");writeByte(65)},
                tail {repeat(3) {writeByte(0)};writeByte(1);writeUTF("codex");writeByte(1);writeUTF("[\"session\",\"\",\"\",\"false\"]")},
                tail {repeat(4) {writeByte(0)};writeByte(3);writeByte(2);writeByte(0)},
                tail {repeat(4) {writeByte(0)};writeByte(3);writeByte(0);writeByte(2)}
            )
            malformed.forEach {file.writeBytes(base+it);assertTrue(runCatching {store.load()}.isFailure)}
        } finally {store.clear()}
    }
}
