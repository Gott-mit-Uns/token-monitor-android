package dev.bing.tokenmonitor

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class SettingsManagementPreferenceTest {
    @Test fun v15ReadsWithoutRewriteAndV16AddsOnlyIndependentHighlightChoice() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="quota-v16-${UUID.randomUUID()}.bin";val store=DisplayPreferenceStore(context,name);val file=File(context.noBackupFilesDir,name)
        try {
            val old=DisplayPreferences(sessionTitlesEnabled=false,main=MainDisplayPreferences(sessionContextMetric="remaining"),appearance=AppearancePreferences(reduceMotion="off"),limits=LimitDisplayPreferences(homeBars=true,homeAccountCount=5,hiddenHomeProviders=setOf("codex")))
            store.save(old)
            val v15=file.readBytes().dropLast(1).toByteArray().also {it[0]=15};file.writeBytes(v15)
            assertEquals(old,store.load());assertTrue(v15.contentEquals(file.readBytes()))
            val selected=DisplayChange.HighlightLowHomeLimits(true).apply(store.load());store.save(selected)
            assertEquals(16,file.readBytes()[0].toInt());assertEquals(selected,DisplayPreferenceStore(context,name).load())
            assertEquals(old,selected.copy(limits=selected.limits.copy(highlightLowHomeLimits=false)))
            assertTrue(selected.limits.homeBars);assertFalse(selected.sessionTitlesEnabled);assertEquals("off",selected.appearance.reduceMotion)
        } finally {store.clear()}
    }
    @Test fun v16MalformedBooleanTruncationAndExtraBytesCannotOverwriteSavedChoice() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="quota-tail-${UUID.randomUUID()}.bin";val store=DisplayPreferenceStore(context,name);val file=File(context.noBackupFilesDir,name)
        try {
            val selected=DisplayPreferences(limits=LimitDisplayPreferences(highlightLowHomeLimits=true));store.save(selected);val good=file.readBytes()
            for(bad in listOf(good.copyOf().also {it[it.lastIndex]=2},good.dropLast(1).toByteArray(),good+0.toByte())) {
                file.writeBytes(bad);assertTrue(runCatching {store.load()}.isFailure);assertTrue(bad.contentEquals(file.readBytes()))
            }
            file.writeBytes(good);assertEquals(selected,store.load())
        } finally {store.clear()}
    }
}
