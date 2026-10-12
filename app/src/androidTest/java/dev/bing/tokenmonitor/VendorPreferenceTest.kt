package dev.bing.tokenmonitor

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.UUID

class VendorPreferenceTest {
    @Test fun explicitV8FileLoadsWithoutRewriteAndV9StoresAllVendorsAndVisibilityWithOldChoices() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test-vendor-${UUID.randomUUID()}.bin";val file=File(context.noBackupFilesDir,name);val store=DisplayPreferenceStore(context,name)
        val buffer=ByteArrayOutputStream()
        DataOutputStream(buffer).use {data ->
            data.writeByte(8);data.writeByte(2);data.writeBoolean(true);data.writeByte(1)
            data.writeByte(3);listOf(5,3,1).forEach(data::writeByte)
            data.writeByte(1);data.writeUTF("claude");data.writeByte(1);data.writeUTF("hermes");data.writeByte(0)
            data.writeUTF("last30");data.writeUTF("last30");data.writeUTF("tokens");data.writeUTF("year")
            data.writeUTF("CNY");data.writeByte(1);data.writeUTF("CNY");data.writeUTF("7")
            data.writeUTF("largest");data.writeUTF("serif");data.writeUTF("")
            data.writeByte(2);data.writeUTF("bg");data.writeUTF("#0b0c0e");data.writeUTF("chart");data.writeUTF("#ff0000")
        }
        try {
            val oldBytes=buffer.toByteArray();file.writeBytes(oldBytes);val old=store.load();assertTrue(oldBytes.contentEquals(file.readBytes()))
            assertEquals("#0b0c0e",old.appearance.colors["bg"]);assertEquals("serif",old.appearance.interfaceFont);assertEquals("largest",old.appearance.textSize)
            assertTrue(old.appearance.vendorColors.isEmpty());assertTrue(old.appearance.showToolIcons);assertTrue(old.appearance.showLiveDot)
            val selected=old.copy(appearance=old.appearance.copy(vendorColors=DesktopVendorPalette.ids.associateWith {"#123456"},showToolIcons=false,showLiveDot=false))
            store.save(selected);assertEquals(16,file.readBytes()[0].toInt());assertEquals(selected,DisplayPreferenceStore(context,name).load())
            assertEquals(old,selected.copy(appearance=selected.appearance.copy(vendorColors=emptyMap(),showToolIcons=true,showLiveDot=true)))
            val good=file.readBytes()
            listOf(mapOf("bad" to "#123456"),mapOf("codex" to "#123"),mapOf("codex" to "#ABCDEF")).forEach {invalid ->
                assertTrue(runCatching {store.save(selected.copy(appearance=selected.appearance.copy(vendorColors=invalid)))}.isFailure);assertTrue(good.contentEquals(file.readBytes()))
            }
            file.writeBytes(legacyV11DefaultMainPrefix(good).also {it[it.lastIndex]=2});assertTrue(runCatching {store.load()}.isFailure)
            file.writeBytes(good+0.toByte());assertTrue(runCatching {store.load()}.isFailure)
        } finally {store.clear()}
    }
    @Test fun maximalSupportedVendorAndToolSettingsFitAtomicRecordAndRoundTrip() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test-vendor-max-${UUID.randomUUID()}.bin";val store=DisplayPreferenceStore(context,name)
        val prefs=DisplayPreferences(homeModules=homeModuleIds,hiddenTools=desktopManagedToolIds.toSet(),pinnedTools=desktopManagedToolIds,toolOrder=desktopManagedToolIds,currency="CNY",currencyRates=mapOf("CNY" to "7","TWD" to "30","HKD" to "7"),appearance=AppearancePreferences(DesktopAppearance.presets.getValue("porcelain"),"largest","x".repeat(160),"y".repeat(160),DesktopVendorPalette.ids.associateWith {"#123456"},false,false))
        try {store.save(prefs);assertTrue(File(context.noBackupFilesDir,name).length()<=4096);assertEquals(prefs,DisplayPreferenceStore(context,name).load())} finally {store.clear()}
    }
}
